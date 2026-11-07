package dev.enterprisepulse.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.common.Paged;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.LotBalance;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductCategory;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.SerialNumber;
import dev.enterprisepulse.data.generated.SerialStatus;
import dev.enterprisepulse.data.generated.StockBalance;
import dev.enterprisepulse.data.generated.StockLot;
import dev.enterprisepulse.data.generated.StockMovement;
import dev.enterprisepulse.data.generated.StockTransaction;
import dev.enterprisepulse.data.generated.TaxRate;
import dev.enterprisepulse.data.generated.TrackingType;
import dev.enterprisepulse.data.generated.Unit;
import dev.enterprisepulse.data.generated.Warehouse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {
    public record Level(UUID variantId, UUID productId, String sku, String productName, String variantName, String categoryName, String unitCode,
                         TrackingType trackingType, BigDecimal quantity, BigDecimal minStock, BigDecimal averageCost, BigDecimal stockValue, boolean lowStock) {}

    public record Summary(int itemCount, int lowStockCount, BigDecimal stockValue, int warehouseCount) {}

    public record Item(UUID variantId, UUID productId, String sku, String productName, String variantName, String label, String unitCode,
                        ProductType productType, TrackingType trackingType, BigDecimal averageCost, BigDecimal onHand, BigDecimal reserved, BigDecimal available,
                        BigDecimal purchasePrice, BigDecimal salePrice, String priceCurrency, BigDecimal vatRate) {}

    public record LotStock(UUID lotId, String lotNo, LocalDate expiryDate, UUID warehouseId, String warehouseName, BigDecimal quantity) {}

    public record SerialRow(UUID id, UUID variantId, String sku, String serialNo, SerialStatus status, UUID warehouseId, String warehouseName) {}

    public record LedgerRow(UUID id, UUID movementId, String movementNumber, String movementType, LocalDate movementDate, UUID warehouseId,
                             String warehouseName, String sku, String lotNo, BigDecimal quantity, BigDecimal unitCost, BigDecimal balanceAfter, Instant createdAt) {}

    public record VariantRow(UUID id, String sku, String name, JsonNode attributes, String barcode, BigDecimal minStock, BigDecimal averageCost,
                              boolean isDefault, boolean isActive, BigDecimal onHand, boolean lowStock, boolean hasHistory) {}

    public record WarehouseStock(UUID warehouseId, String warehouseName, UUID variantId, String sku, BigDecimal quantity) {}

    public record ProductStock(UUID id, String code, String name, ProductType type, TrackingType trackingType, String unitCode, BigDecimal onHand,
                                BigDecimal stockValue, boolean lowStock, boolean hasHistory, List<VariantRow> variants, List<WarehouseStock> stock) {}

    public record VariantInput(String sku, String name, Map<String, String> attributes, String barcode, BigDecimal minStock, Boolean isActive) {}

    public record WarehouseSummary(UUID id, int itemCount, BigDecimal stockValue) {}

    private final Db db;
    private final ObjectMapper json;

    public InventoryService(Db db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    public Paged<Level> levels(UUID warehouseId, UUID categoryId, boolean lowStock, String search, String sort, boolean desc, int page, int pageSize) {
        List<Level> rows = new ArrayList<>();
        for (ProductVariant v : db.list("select v from ProductVariant v", ProductVariant.class)) {
            Product p = db.find(Product.class, v.productId);
            if (p == null || p.deletedAt != null || p.type != ProductType.GOODS) continue;
            BigDecimal total = sum("select coalesce(sum(b.quantity), 0) from StockBalance b where b.variantId = ?1", v.id);
            BigDecimal here = warehouseId == null ? BigDecimal.ZERO
                    : sum("select coalesce(sum(b.quantity), 0) from StockBalance b where b.variantId = ?1 and b.warehouseId = ?2", v.id, warehouseId);
            if (warehouseId != null) {
                if (here.signum() == 0) continue;
            } else if (!((v.isActive && p.isActive) || total.signum() != 0)) continue;
            if (categoryId != null && !categoryId.equals(p.categoryId)) continue;
            boolean low = v.minStock.signum() > 0 && total.compareTo(v.minStock) < 0;
            if (lowStock && !low) continue;
            if (search != null && !search.isBlank()) {
                String s = search.trim().toLowerCase(Locale.ROOT);
                boolean hit = contains(v.sku, s) || contains(p.code, s) || contains(p.name, s) || contains(v.name, s) || s.equalsIgnoreCase(v.barcode == null ? "" : v.barcode);
                if (!hit) continue;
            }
            BigDecimal qty = warehouseId == null ? total : here;
            Unit unit = p.unitId == null ? null : db.find(Unit.class, p.unitId);
            ProductCategory cat = p.categoryId == null ? null : db.find(ProductCategory.class, p.categoryId);
            rows.add(new Level(v.id, p.id, v.sku, p.name, v.name, cat == null ? null : cat.name, unit == null ? "" : unit.code, p.trackingType,
                    qty, v.minStock, v.averageCost, qty.multiply(v.averageCost).setScale(2, RoundingMode.HALF_UP), low));
        }
        Comparator<Level> cmp = switch (sort == null ? "sku" : sort) {
            case "productName" -> Comparator.comparing(Level::productName, String.CASE_INSENSITIVE_ORDER);
            case "quantity" -> Comparator.comparing(Level::quantity);
            case "stockValue" -> Comparator.comparing(Level::stockValue);
            default -> Comparator.comparing(Level::sku, String.CASE_INSENSITIVE_ORDER);
        };
        rows.sort(desc ? cmp.reversed() : cmp);
        int size = Math.max(1, Math.min(200, pageSize));
        int pg = Math.max(1, page);
        int from = Math.min(rows.size(), (pg - 1) * size);
        return Paged.of(rows.subList(from, Math.min(rows.size(), from + size)), rows.size(), pg, size);
    }

    public Summary summary() {
        int items = 0;
        int low = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (ProductVariant v : db.list("select v from ProductVariant v where v.isActive = true", ProductVariant.class)) {
            Product p = db.find(Product.class, v.productId);
            if (p == null || p.deletedAt != null || !p.isActive || p.type != ProductType.GOODS) continue;
            BigDecimal total = sum("select coalesce(sum(b.quantity), 0) from StockBalance b where b.variantId = ?1", v.id);
            if (total.signum() > 0) items++;
            if (v.minStock.signum() > 0 && total.compareTo(v.minStock) < 0) low++;
            value = value.add(total.multiply(v.averageCost));
        }
        int warehouses = (int) db.count("select count(w) from Warehouse w where w.isActive = true");
        return new Summary(items, low, value.setScale(2, RoundingMode.HALF_UP), warehouses);
    }

    public List<Item> items(String search, UUID warehouseId, boolean inStock, boolean includeServices, UUID id) {
        List<ProductVariant> variants = id != null
                ? db.list("select v from ProductVariant v where v.id = ?1", ProductVariant.class, id)
                : db.list("select v from ProductVariant v where v.isActive = true order by v.sku", ProductVariant.class);
        List<Item> rows = new ArrayList<>();
        for (ProductVariant v : variants) {
            if (rows.size() >= 20) break;
            Product p = db.find(Product.class, v.productId);
            if (p == null || p.deletedAt != null || !p.isActive || !v.isActive) continue;
            if (!includeServices && id == null && p.type != ProductType.GOODS) continue;
            if (search != null && !search.isBlank() && id == null) {
                String s = search.trim().toLowerCase(Locale.ROOT);
                if (!(contains(v.sku, s) || contains(v.name, s) || contains(p.name, s) || s.equalsIgnoreCase(v.barcode == null ? "" : v.barcode))) continue;
            }
            BigDecimal onHand = warehouseId == null
                    ? sum("select coalesce(sum(b.quantity), 0) from StockBalance b where b.variantId = ?1", v.id)
                    : sum("select coalesce(sum(b.quantity), 0) from StockBalance b where b.variantId = ?1 and b.warehouseId = ?2", v.id, warehouseId);
            if (inStock && warehouseId != null && onHand.signum() <= 0) continue;
            BigDecimal reserved = warehouseId == null
                    ? sum("select coalesce(sum(r.quantity), 0) from StockReservation r where r.variantId = ?1", v.id)
                    : sum("select coalesce(sum(r.quantity), 0) from StockReservation r where r.variantId = ?1 and r.warehouseId = ?2", v.id, warehouseId);
            Unit unit = p.unitId == null ? null : db.find(Unit.class, p.unitId);
            TaxRate vat = p.vatRateId == null ? null : db.find(TaxRate.class, p.vatRateId);
            String label = v.name != null && !v.name.isBlank() ? p.name + " (" + v.name + ")" : p.name;
            rows.add(new Item(v.id, p.id, v.sku, p.name, v.name, label, unit == null ? "" : unit.code, p.type, p.trackingType, v.averageCost,
                    onHand, reserved, onHand.subtract(reserved), p.purchasePrice, p.salePrice, p.currency, vat == null ? null : vat.rate));
        }
        return rows;
    }

    public List<LotStock> lots(UUID variantId, UUID warehouseId) {
        List<LotBalance> balances = db.list("select b from LotBalance b where b.quantity > 0", LotBalance.class);
        List<LotStock> rows = new ArrayList<>();
        for (LotBalance b : balances) {
            if (warehouseId != null && !warehouseId.equals(b.warehouseId)) continue;
            StockLot lot = db.get(StockLot.class, b.lotId);
            if (variantId != null && !variantId.equals(lot.variantId)) continue;
            Warehouse w = db.get(Warehouse.class, b.warehouseId);
            rows.add(new LotStock(lot.id, lot.lotNo, lot.expiryDate, w.id, w.name, b.quantity));
        }
        rows.sort(Comparator.comparing((LotStock r) -> r.expiryDate() == null ? LocalDate.MAX : r.expiryDate()).thenComparing(LotStock::lotNo));
        return rows;
    }

    public List<SerialRow> serials(UUID variantId, UUID warehouseId, SerialStatus status) {
        List<SerialNumber> rows = db.list("select s from SerialNumber s order by s.serialNo", SerialNumber.class);
        List<SerialRow> result = new ArrayList<>();
        for (SerialNumber s : rows) {
            if (variantId != null && !variantId.equals(s.variantId)) continue;
            if (warehouseId != null && !warehouseId.equals(s.warehouseId)) continue;
            if (status != null && s.status != status) continue;
            ProductVariant v = db.get(ProductVariant.class, s.variantId);
            Warehouse w = s.warehouseId == null ? null : db.find(Warehouse.class, s.warehouseId);
            result.add(new SerialRow(s.id, s.variantId, v.sku, s.serialNo, s.status, s.warehouseId, w == null ? null : w.name));
            if (result.size() == 1000) break;
        }
        return result;
    }

    public Paged<LedgerRow> ledger(UUID productId, UUID variantId, UUID warehouseId, int page, int pageSize) {
        List<StockTransaction> rows = db.list("select t from StockTransaction t order by t.seq desc", StockTransaction.class);
        List<LedgerRow> matched = new ArrayList<>();
        for (StockTransaction t : rows) {
            if (variantId != null && !variantId.equals(t.variantId)) continue;
            if (warehouseId != null && !warehouseId.equals(t.warehouseId)) continue;
            ProductVariant v = db.get(ProductVariant.class, t.variantId);
            if (productId != null && !productId.equals(v.productId)) continue;
            StockMovement m = db.get(StockMovement.class, t.movementId);
            Warehouse w = db.get(Warehouse.class, t.warehouseId);
            StockLot lot = t.lotId == null ? null : db.find(StockLot.class, t.lotId);
            matched.add(new LedgerRow(t.id, m.id, m.number, m.type.name(), t.movementDate, w.id, w.name, v.sku, lot == null ? null : lot.lotNo,
                    t.quantity, t.unitCost, t.balanceAfter, t.createdAt));
        }
        int size = Math.max(1, Math.min(200, pageSize));
        int pg = Math.max(1, page);
        int from = Math.min(matched.size(), (pg - 1) * size);
        return Paged.of(matched.subList(from, Math.min(matched.size(), from + size)), matched.size(), pg, size);
    }

    public List<WarehouseSummary> warehouses() {
        List<WarehouseSummary> rows = new ArrayList<>();
        for (Warehouse w : db.list("select w from Warehouse w", Warehouse.class)) {
            List<StockBalance> balances = db.list("select b from StockBalance b where b.warehouseId = ?1 and b.quantity > 0", StockBalance.class, w.id);
            BigDecimal value = BigDecimal.ZERO;
            for (StockBalance b : balances) {
                ProductVariant v = db.get(ProductVariant.class, b.variantId);
                value = value.add(b.quantity.multiply(v.averageCost));
            }
            rows.add(new WarehouseSummary(w.id, balances.size(), value.setScale(2, RoundingMode.HALF_UP)));
        }
        return rows;
    }

    public ProductStock product(UUID productId) {
        Product p = Guard.found(db.first("select p from Product p where p.id = ?1", Product.class, productId), "Ürün bulunamadı");
        Unit unit = db.get(Unit.class, p.unitId);
        List<ProductVariant> variants = db.list("select v from ProductVariant v where v.productId = ?1", ProductVariant.class, p.id);
        variants.sort(Comparator.comparing((ProductVariant v) -> !v.isDefault).thenComparing(v -> v.sku));
        List<VariantRow> variantRows = new ArrayList<>();
        List<WarehouseStock> stock = new ArrayList<>();
        BigDecimal onHand = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        boolean low = false;
        boolean history = false;
        for (ProductVariant v : variants) {
            BigDecimal qty = sum("select coalesce(sum(b.quantity), 0) from StockBalance b where b.variantId = ?1", v.id);
            boolean has = db.exists("select t.id from StockTransaction t where t.variantId = ?1", v.id)
                    || db.exists("select l.id from StockCountLine l where l.variantId = ?1", v.id);
            boolean isLow = v.isActive && v.minStock.signum() > 0 && qty.compareTo(v.minStock) < 0;
            variantRows.add(new VariantRow(v.id, v.sku, v.name, v.attributes, v.barcode, v.minStock, v.averageCost, v.isDefault, v.isActive, qty, isLow, has));
            onHand = onHand.add(qty);
            value = value.add(qty.multiply(v.averageCost));
            low = low || isLow;
            history = history || has;
            for (StockBalance b : db.list("select b from StockBalance b where b.variantId = ?1 and b.quantity <> 0", StockBalance.class, v.id)) {
                Warehouse w = db.get(Warehouse.class, b.warehouseId);
                stock.add(new WarehouseStock(w.id, w.name, v.id, v.sku, b.quantity));
            }
        }
        stock.sort(Comparator.comparing(WarehouseStock::warehouseName).thenComparing(WarehouseStock::sku));
        return new ProductStock(p.id, p.code, p.name, p.type, p.trackingType, unit.code, onHand, value.setScale(2, RoundingMode.HALF_UP), low, history, variantRows, stock);
    }

    public ProductStock addVariant(UUID productId, VariantInput input) {
        Product p = Guard.found(db.first("select p from Product p where p.id = ?1", Product.class, productId), "Ürün bulunamadı");
        Guard.that(p.type != ProductType.SERVICE, "Hizmetlerin varyantı olmaz");
        Guard.that(input.sku() != null && !input.sku().isBlank(), "Stok kodu (SKU) gerekli");
        ProductVariant variant = new ProductVariant();
        variant.productId = p.id;
        variant.sku = input.sku().trim();
        apply(variant, input);
        db.add(variant);
        db.flush();
        return product(productId);
    }

    public ProductStock updateVariant(UUID productId, UUID variantId, VariantInput input) {
        ProductVariant v = Guard.found(db.first("select v from ProductVariant v where v.id = ?1 and v.productId = ?2", ProductVariant.class, variantId, productId), "Varyant bulunamadı");
        if (input.sku() != null && !input.sku().trim().equals(v.sku)) {
            Guard.state(!hasHistory(List.of(variantId)), "Stok hareketi olan varyantın stok kodu değiştirilemez");
            v.sku = input.sku().trim();
        }
        apply(v, input);
        db.flush();
        return product(productId);
    }

    public ProductStock removeVariant(UUID productId, UUID variantId) {
        List<ProductVariant> variants = db.list("select v from ProductVariant v where v.productId = ?1", ProductVariant.class, productId);
        ProductVariant v = variants.stream().filter(x -> x.id.equals(variantId)).findFirst().orElse(null);
        Guard.that(v != null, "Varyant bulunamadı");
        Guard.state(variants.size() > 1, "Bir ürünün en az bir varyantı olmalı");
        Guard.state(!hasHistory(List.of(variantId)), "Varyantın stok geçmişi var; silmek yerine pasifleştirin");
        db.execute("delete from StockBalance b where b.variantId = ?1", variantId);
        db.em().remove(v);
        if (v.isDefault) variants.stream().filter(x -> !x.id.equals(variantId)).findFirst().ifPresent(x -> x.isDefault = true);
        db.flush();
        return product(productId);
    }

    public boolean hasHistory(List<UUID> variantIds) {
        if (variantIds.isEmpty()) return false;
        return db.exists("select t.id from StockTransaction t where t.variantId in ?1", variantIds)
                || db.exists("select l.id from StockCountLine l where l.variantId in ?1", variantIds);
    }

    private void apply(ProductVariant v, VariantInput input) {
        if (input.name() != null) v.name = input.name().isBlank() ? null : input.name().trim();
        if (input.barcode() != null) v.barcode = input.barcode().isBlank() ? null : input.barcode().trim();
        if (input.minStock() != null) {
            Guard.that(input.minStock().signum() >= 0, "Minimum stok negatif olamaz");
            v.minStock = input.minStock();
        }
        if (input.attributes() != null) v.attributes = input.attributes().isEmpty() ? null : json.valueToTree(input.attributes());
        if (input.isActive() != null) v.isActive = input.isActive();
        Guard.that(!db.exists("select v.id from ProductVariant v where v.sku = ?1 and v.id <> ?2", v.sku, v.id), "Bu stok kodu kullanılıyor: " + v.sku);
    }

    private BigDecimal sum(String jpql, Object... params) {
        return db.first(jpql, BigDecimal.class, params).orElse(BigDecimal.ZERO);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
