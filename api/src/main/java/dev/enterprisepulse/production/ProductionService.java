package dev.enterprisepulse.production;

import dev.enterprisepulse.accounting.AccountingClock;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BillOfMaterials;
import dev.enterprisepulse.data.generated.BomLine;
import dev.enterprisepulse.data.generated.LotBalance;
import dev.enterprisepulse.data.generated.NumberSequence;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.ProductionRecord;
import dev.enterprisepulse.data.generated.PurchaseOrderLine;
import dev.enterprisepulse.data.generated.PurchaseOrderStatus;
import dev.enterprisepulse.data.generated.SalesOrderLine;
import dev.enterprisepulse.data.generated.SalesOrderStatus;
import dev.enterprisepulse.data.generated.StockLot;
import dev.enterprisepulse.data.generated.StockMovement;
import dev.enterprisepulse.data.generated.StockMovementLine;
import dev.enterprisepulse.data.generated.StockMovementType;
import dev.enterprisepulse.data.generated.TrackingType;
import dev.enterprisepulse.data.generated.Unit;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.data.generated.WorkOrder;
import dev.enterprisepulse.data.generated.WorkOrderComponent;
import dev.enterprisepulse.data.generated.WorkOrderStatus;
import dev.enterprisepulse.inventory.StockMovementService;
import dev.enterprisepulse.inventory.StockMovementService.CreateRequest;
import dev.enterprisepulse.inventory.StockMovementService.LineInput;
import dev.enterprisepulse.inventory.StockMovementService.LowStockItem;
import dev.enterprisepulse.inventory.StockMovementService.PostResult;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.production.ProductionMath.BomLineShape;
import dev.enterprisepulse.production.ProductionMath.BomShape;
import dev.enterprisepulse.production.ProductionMath.BuildLine;
import dev.enterprisepulse.production.ProductionMath.ExplodedLine;
import dev.enterprisepulse.production.ProductionMath.RolledCost;
import dev.enterprisepulse.purchasing.PurchaseRequestService;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Üretim: BOMs, work orders, production records and MRP. */
@Service
@Transactional
public class ProductionService {
    public static final String RECORD_SEQUENCE = "PRODUCTION";

    public record VariantInfo(UUID variantId, UUID productId, String sku, String productName, String variantName, String unitCode) {}
    public record BomLineInput(UUID variantId, BigDecimal quantity, BigDecimal scrapRate, String notes) {}
    public record CreateBom(UUID variantId, String code, String name, BigDecimal quantity, BigDecimal laborCost, BigDecimal overheadCost,
                            Boolean isDefault, String notes, List<BomLineInput> lines) {}
    public record UpdateBom(String code, String name, BigDecimal quantity, BigDecimal laborCost, BigDecimal overheadCost, Boolean isDefault,
                            Boolean isActive, String notes, List<BomLineInput> lines) {}
    public record BomPlanLineDto(int level, UUID variantId, UUID parentId, String sku, String name, String unitCode, BigDecimal required,
                                 BigDecimal onHand, BigDecimal shortage, boolean made, String availability) {}
    public record BomPlanDto(BigDecimal quantity, BigDecimal materialCost, BigDecimal laborCost, BigDecimal overheadCost, BigDecimal totalCost,
                             List<BomPlanLineDto> lines) {}
    public record BomLineDto(UUID id, int lineNo, VariantInfo variant, BigDecimal quantity, BigDecimal scrapRate, BigDecimal quantityPer,
                             BigDecimal unitCost, BigDecimal lineCost, BigDecimal onHand, boolean made, UUID madeBomId, String notes) {}
    public record BomDetailDto(UUID id, String code, String name, VariantInfo variant, BigDecimal quantity, BigDecimal laborCost,
                               BigDecimal overheadCost, boolean isDefault, boolean isActive, int lineCount, BigDecimal unitCost, int workOrderCount,
                               Instant updatedAt, String notes, BigDecimal materialCost, String createdByName, Instant createdAt,
                               List<BomLineDto> lines, BigDecimal buildable, boolean canDelete) {}
    public record CreateWorkOrder(UUID bomId, BigDecimal quantity, LocalDate plannedStart, LocalDate plannedEnd, UUID sourceWarehouseId,
                                  UUID targetWarehouseId, String reference, String notes) {}
    public record UpdateWorkOrder(BigDecimal quantity, LocalDate plannedStart, LocalDate plannedEnd, UUID sourceWarehouseId,
                                  UUID targetWarehouseId, String reference, String notes) {}
    public record ComponentUse(UUID variantId, BigDecimal quantity) {}
    public record RecordProduction(BigDecimal quantity, LocalDate recordDate, String lotNo, LocalDate expiryDate, Boolean finish, String notes,
                                   List<ComponentUse> components) {}
    public record WorkOrderDto(UUID id, String number, WorkOrderStatus status, UUID bomId, String bomCode, VariantInfo variant,
                               BigDecimal quantity, BigDecimal producedQty, int progress, LocalDate plannedStart, LocalDate plannedEnd,
                               boolean late, UUID sourceWarehouseId, String sourceWarehouseName, UUID targetWarehouseId, String targetWarehouseName,
                               String reference, Instant createdAt) {}
    public record WorkOrderComponentDto(UUID id, int lineNo, VariantInfo variant, BigDecimal quantityPer, BigDecimal requiredQty,
                                        BigDecimal consumedQty, BigDecimal remainingQty, BigDecimal onHand, String availability, boolean lotTracked) {}
    public record ProductionRecordDto(UUID id, String number, LocalDate recordDate, BigDecimal quantity, BigDecimal materialCost, BigDecimal laborCost,
                                      BigDecimal overheadCost, BigDecimal totalCost, BigDecimal unitCost, UUID issueMovementId, String issueMovementNumber,
                                      UUID receiptMovementId, String receiptMovementNumber, String lotNo, String notes, String createdByName,
                                      Instant createdAt) {}
    public record WorkOrderDetailDto(WorkOrderDto order, String notes, BigDecimal laborPerUnit, BigDecimal overheadPerUnit, BigDecimal standardUnitCost,
                                     BigDecimal actualUnitCost, boolean lotTracked, String createdByName, Instant releasedAt, Instant startedAt,
                                     Instant completedAt, Instant cancelledAt, List<WorkOrderComponentDto> components, List<ProductionRecordDto> records,
                                     BigDecimal buildable, boolean canEdit, boolean canRelease, boolean canRecord, boolean canCancel, boolean canComplete) {}
    public record RequirementOrderDto(UUID id, String number, BigDecimal quantity, LocalDate date) {}
    public record RequirementDto(VariantInfo variant, BigDecimal productionDemand, BigDecimal salesDemand, BigDecimal gross, BigDecimal onHand,
                                 BigDecimal purchaseIncoming, BigDecimal productionIncoming, BigDecimal shortage, LocalDate neededBy, boolean made,
                                 UUID defaultBomId, BigDecimal purchasePrice, List<RequirementOrderDto> workOrders, List<RequirementOrderDto> salesOrders) {}
    public record RequirementLineInput(UUID variantId, BigDecimal quantity) {}
    public record CreateRequirementRequest(LocalDate neededBy, List<RequirementLineInput> lines) {}
    public record CreatedDocumentDto(UUID id, String number) {}
    public record SummaryDto(int activeBoms, int draft, int released, int inProgress, int late, int completedThisMonth,
                             BigDecimal producedValueThisMonth, int shortages, List<WorkOrderDto> upcoming) {}

    private static final class CostBook {
        private final Map<UUID, VariantCost> variants;
        private final Map<UUID, BomShape> boms;
        private final Map<UUID, BigDecimal> memo = new HashMap<>();

        private record VariantCost(BigDecimal averageCost, BigDecimal purchasePrice) {}

        CostBook(Map<UUID, VariantCost> variants, Map<UUID, BomShape> boms) {
            this.variants = variants;
            this.boms = boms;
        }

        Map<UUID, BomShape> boms() { return boms; }

        BigDecimal cost(UUID variantId) { return cost(variantId, new HashSet<>()); }

        private BigDecimal cost(UUID variantId, Set<UUID> trail) {
            if (memo.containsKey(variantId)) return memo.get(variantId);
            VariantCost v = variants.get(variantId);
            BigDecimal value = v == null ? BigDecimal.ZERO : v.averageCost();
            BomShape bom = boms.get(variantId);
            if (value.signum() == 0 && bom != null && !trail.contains(variantId)) {
                trail.add(variantId);
                value = ProductionMath.rollUpCost(bom, id -> cost(id, trail)).unitCost();
                trail.remove(variantId);
            }
            if (value.signum() == 0 && v != null && v.purchasePrice() != null) value = v.purchasePrice();
            memo.put(variantId, value);
            return value;
        }
    }

    private final Db db;
    private final NumberingService numbering;
    private final StockMovementService stock;
    private final PurchaseRequestService requests;

    public ProductionService(Db db, NumberingService numbering, StockMovementService stock, PurchaseRequestService requests) {
        this.db = db;
        this.numbering = numbering;
        this.stock = stock;
        this.requests = requests;
    }

    private static CurrentUser ctx() { return CurrentUser.get(); }

    private static VariantInfo info(ProductVariant v, Product p, Unit unit) {
        return new VariantInfo(v.id, v.productId, v.sku, p.name, v.name, unit.code);
    }

    private static String label(ProductVariant v, Product p) {
        return v.name == null || v.name.isBlank() ? p.name : p.name + " (" + v.name + ")";
    }

    private static String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<UUID, ProductVariant> loadVariants(Iterable<UUID> ids) {
        List<UUID> list = new ArrayList<>();
        ids.forEach(list::add);
        if (list.isEmpty()) return Map.of();
        return db.list("select v from ProductVariant v where v.id in ?1", ProductVariant.class, list).stream()
                .collect(Collectors.toMap(v -> v.id, Function.identity()));
    }

    private Map<UUID, BigDecimal> onHand(Iterable<UUID> variantIds, UUID warehouseId) {
        List<UUID> ids = new ArrayList<>();
        variantIds.forEach(ids::add);
        Map<UUID, BigDecimal> result = ids.stream().distinct().collect(Collectors.toMap(id -> id, id -> BigDecimal.ZERO, (a, b) -> a, LinkedHashMap::new));
        if (result.isEmpty()) return result;
        List<Object[]> rows = warehouseId == null
                ? db.list("select b.variantId, sum(b.quantity) from StockBalance b where b.variantId in ?1 group by b.variantId", Object[].class, result.keySet())
                : db.list("select b.variantId, sum(b.quantity) from StockBalance b where b.variantId in ?1 and b.warehouseId = ?2 group by b.variantId",
                        Object[].class, result.keySet(), warehouseId);
        for (Object[] r : rows) result.put((UUID) r[0], (BigDecimal) r[1]);
        return result;
    }

    private static BomShape shapeOf(BillOfMaterials row, List<BomLine> lines) {
        return new BomShape(row.quantity, row.laborCost, row.overheadCost,
                lines.stream().sorted(Comparator.comparingInt(l -> l.lineNo))
                        .map(l -> new BomLineShape(l.variantId, l.quantity, l.scrapRate)).toList(),
                row.id, row.variantId);
    }

    private Map<UUID, BomShape> defaultBoms() {
        List<BillOfMaterials> rows = db.list("select b from BillOfMaterials b where b.isDefault = true and b.isActive = true", BillOfMaterials.class);
        Map<UUID, BomShape> result = new HashMap<>();
        for (BillOfMaterials row : rows) {
            List<BomLine> lines = db.list("select l from BomLine l where l.bomId = ?1", BomLine.class, row.id);
            result.put(row.variantId, shapeOf(row, lines));
        }
        return result;
    }

    private CostBook loadCostBook(Iterable<UUID> extra) {
        Map<UUID, BomShape> boms = defaultBoms();
        Set<UUID> ids = new HashSet<>();
        if (extra != null) extra.forEach(ids::add);
        for (BomShape bom : boms.values()) {
            ids.add(bom.variantId());
            for (BomLineShape line : bom.lines()) ids.add(line.variantId());
        }
        Map<UUID, CostBook.VariantCost> costs = new HashMap<>();
        if (!ids.isEmpty()) {
            for (ProductVariant v : db.list("select v from ProductVariant v where v.id in ?1", ProductVariant.class, ids)) {
                Product p = db.get(Product.class, v.productId);
                costs.put(v.id, new CostBook.VariantCost(v.averageCost, p.purchasePrice));
            }
        }
        return new CostBook(costs, boms);
    }

    private static void assertProducible(ProductVariant variant, Product product, String label) {
        Guard.that(product.type == ProductType.GOODS, label + ": hizmetler üretilemez veya sarf edilemez");
        Guard.that(product.trackingType != TrackingType.SERIAL, label + ": seri takipli ürünler üretimde desteklenmez");
        Guard.that(variant.isActive && product.isActive && product.deletedAt == null, label + ": ürün pasif");
    }

    // ---------- Bills of materials ----------

    public BomDetailDto getBom(UUID id) {
        BillOfMaterials row = Guard.found(db.first("select b from BillOfMaterials b where b.id = ?1", BillOfMaterials.class, id), "Ürün ağacı bulunamadı");
        List<BomLine> bomLines = db.list("select l from BomLine l where l.bomId = ?1 order by l.lineNo", BomLine.class, id);
        List<UUID> componentIds = bomLines.stream().map(l -> l.variantId).toList();
        CostBook book = loadCostBook(componentIds);
        Map<UUID, BigDecimal> onHand = onHand(componentIds, null);
        Set<UUID> allIds = new HashSet<>(componentIds);
        allIds.add(row.variantId);
        Map<UUID, ProductVariant> variants = loadVariants(allIds);
        RolledCost cost = ProductionMath.rollUpCost(shapeOf(row, bomLines), book::cost);
        int workOrders = (int) db.count("select count(w) from WorkOrder w where w.bomId = ?1", id);
        User createdBy = db.get(User.class, row.createdById);
        List<BomLineDto> lines = new ArrayList<>();
        for (BomLine line : bomLines) {
            BigDecimal per = ProductionMath.perUnit(line.quantity, line.scrapRate, row.quantity);
            BigDecimal unit = book.cost(line.variantId);
            BomShape made = book.boms().get(line.variantId);
            ProductVariant v = variants.get(line.variantId);
            Product p = db.get(Product.class, v.productId);
            Unit u = db.get(Unit.class, p.unitId);
            lines.add(new BomLineDto(line.id, line.lineNo, info(v, p, u), line.quantity, line.scrapRate, per, unit,
                    DocumentMath.round2(line.quantity.multiply(BigDecimal.ONE.add(line.scrapRate.movePointLeft(2))).multiply(unit)),
                    onHand.getOrDefault(line.variantId, BigDecimal.ZERO), made != null, made == null ? null : made.id(), line.notes));
        }
        BigDecimal buildable = ProductionMath.buildable(lines.stream().map(l -> new BuildLine(l.quantityPer(), onHand.getOrDefault(l.variant().variantId(), BigDecimal.ZERO))).toList());
        ProductVariant output = variants.get(row.variantId);
        Product outputProduct = db.get(Product.class, output.productId);
        Unit outputUnit = db.get(Unit.class, outputProduct.unitId);
        return new BomDetailDto(row.id, row.code, row.name, info(output, outputProduct, outputUnit), row.quantity, row.laborCost, row.overheadCost,
                row.isDefault, row.isActive, bomLines.size(), cost.unitCost(), workOrders, row.updatedAt, row.notes, cost.material(),
                createdBy.firstName + " " + createdBy.lastName, row.createdAt, lines, buildable, workOrders == 0);
    }

    public BomPlanDto plan(UUID id, BigDecimal quantity, UUID warehouseId) {
        Guard.that(quantity != null && quantity.signum() > 0, "Miktar sıfırdan büyük olmalı");
        BillOfMaterials row = Guard.found(db.first("select b from BillOfMaterials b where b.id = ?1", BillOfMaterials.class, id), "Ürün ağacı bulunamadı");
        if (warehouseId != null) Guard.that(db.exists("select w.id from Warehouse w where w.id = ?1", warehouseId), "Depo bulunamadı");
        List<BomLine> bomLines = db.list("select l from BomLine l where l.bomId = ?1", BomLine.class, id);
        CostBook book = loadCostBook(bomLines.stream().map(l -> l.variantId).toList());
        BomShape root = shapeOf(row, bomLines);
        Function<UUID, BomShape> bomOf = variantId -> Objects.equals(variantId, row.variantId) ? root : book.boms().get(variantId);
        Set<UUID> ids = new HashSet<>();
        collect(row.variantId, bomOf, ids, new HashSet<>());
        Map<UUID, BigDecimal> stockMap = onHand(ids, warehouseId);
        Map<UUID, ProductVariant> variants = loadVariants(ids);
        List<ExplodedLine> lines = ProductionMath.explode(row.variantId, quantity, bomOf, stockMap);
        BigDecimal batches = quantity.divide(row.quantity, 12, java.math.RoundingMode.HALF_UP);
        BigDecimal material = ProductionMath.rollUpCost(root, book::cost).material().multiply(batches);
        BigDecimal labor = row.laborCost.multiply(batches);
        BigDecimal overhead = row.overheadCost.multiply(batches);
        return new BomPlanDto(quantity, DocumentMath.round2(material), DocumentMath.round2(labor), DocumentMath.round2(overhead),
                DocumentMath.round2(material.add(labor).add(overhead)),
                lines.stream().map(l -> {
                    ProductVariant v = variants.get(l.variantId());
                    Product p = db.get(Product.class, v.productId);
                    Unit u = db.get(Unit.class, p.unitId);
                    return new BomPlanLineDto(l.level(), l.variantId(), l.parentId(), v.sku, label(v, p), u.code, l.quantity(),
                            stockMap.getOrDefault(l.variantId(), BigDecimal.ZERO), l.shortage(), l.made(),
                            ProductionMath.availability(l.quantity(), l.allocated()));
                }).toList());
    }

    private static void collect(UUID variantId, Function<UUID, BomShape> bomOf, Set<UUID> ids, Set<UUID> trail) {
        BomShape bom = bomOf.apply(variantId);
        if (bom == null || trail.contains(variantId)) return;
        trail.add(variantId);
        for (BomLineShape line : bom.lines()) {
            ids.add(line.variantId());
            collect(line.variantId(), bomOf, ids, trail);
        }
        trail.remove(variantId);
    }

    public BomDetailDto createBom(CreateBom dto) {
        UUID companyId = ctx().requireCompanyId();
        Guard.that(!Guard.blank(dto.name()), "Reçete adı gerekli");
        Guard.that(dto.lines() != null && !dto.lines().isEmpty(), "En az bir bileşen gerekli");
        BigDecimal qty = dto.quantity() == null ? BigDecimal.ONE : dto.quantity();
        Guard.that(qty.signum() > 0, "Parti miktarı sıfırdan büyük olmalı");
        ProductVariant output = loadOutput(dto.variantId());
        validateLines(output.id, dto.lines(), null);
        String code = trimOrNull(dto.code());
        code = code == null ? nextCode(output) : code.toUpperCase();
        assertCodeFree(code, null);
        boolean hasDefault = db.exists("select b.id from BillOfMaterials b where b.variantId = ?1 and b.isDefault = true and b.isActive = true", output.id);
        boolean isDefault = !hasDefault || Boolean.TRUE.equals(dto.isDefault());
        if (isDefault) clearDefault(output.id);
        BillOfMaterials bom = new BillOfMaterials();
        bom.companyId = companyId;
        bom.code = code;
        bom.name = dto.name().trim();
        bom.variantId = output.id;
        bom.quantity = qty;
        bom.laborCost = dto.laborCost() == null ? BigDecimal.ZERO : dto.laborCost();
        bom.overheadCost = dto.overheadCost() == null ? BigDecimal.ZERO : dto.overheadCost();
        bom.isDefault = isDefault;
        bom.notes = trimOrNull(dto.notes());
        bom.createdById = ctx().requireUserId();
        db.add(bom);
        db.flush();
        for (BomLine line : lineData(dto.lines(), bom.id)) db.add(line);
        db.flush();
        return getBom(bom.id);
    }

    public BomDetailDto updateBom(UUID id, UpdateBom dto) {
        BillOfMaterials bom = Guard.found(db.first("select b from BillOfMaterials b where b.id = ?1", BillOfMaterials.class, id), "Ürün ağacı bulunamadı");
        if (dto.lines() != null) {
            Guard.that(!dto.lines().isEmpty(), "En az bir bileşen gerekli");
            validateLines(bom.variantId, dto.lines(), bom.id);
        }
        String code = trimOrNull(dto.code());
        if (code != null) {
            code = code.toUpperCase();
            if (!code.equals(bom.code)) assertCodeFree(code, bom.id);
        }
        Guard.that(dto.quantity() == null || dto.quantity().signum() > 0, "Parti miktarı sıfırdan büyük olmalı");
        boolean wasDefault = bom.isDefault;
        boolean isActive = dto.isActive() == null ? bom.isActive : dto.isActive();
        Guard.that(!(Boolean.TRUE.equals(dto.isDefault()) && !isActive), "Pasif ürün ağacı varsayılan olamaz");
        boolean isDefault = isActive && (dto.isDefault() == null ? bom.isDefault : dto.isDefault());
        if (isDefault && !bom.isDefault) clearDefault(bom.variantId);
        if (code != null) bom.code = code;
        if (!Guard.blank(dto.name())) bom.name = dto.name().trim();
        if (dto.quantity() != null) bom.quantity = dto.quantity();
        if (dto.laborCost() != null) bom.laborCost = dto.laborCost();
        if (dto.overheadCost() != null) bom.overheadCost = dto.overheadCost();
        if (dto.notes() != null) bom.notes = trimOrNull(dto.notes());
        bom.isActive = isActive;
        bom.isDefault = isDefault;
        if (dto.lines() != null) {
            db.execute("delete from BomLine l where l.bomId = ?1", id);
            db.flush();
            for (BomLine line : lineData(dto.lines(), bom.id)) db.add(line);
        }
        db.flush();
        if (wasDefault && !isDefault) promoteDefault(bom.variantId, id);
        return getBom(id);
    }

    public void removeBom(UUID id) {
        BillOfMaterials bom = Guard.found(db.first("select b from BillOfMaterials b where b.id = ?1", BillOfMaterials.class, id), "Ürün ağacı bulunamadı");
        Guard.state(!db.exists("select w.id from WorkOrder w where w.bomId = ?1", id), "İş emirleri bu ürün ağacını kullanıyor; pasifleştirin");
        boolean wasDefault = bom.isDefault;
        UUID variantId = bom.variantId;
        db.remove(bom);
        db.flush();
        if (wasDefault) promoteDefault(variantId, id);
    }

    private static List<BomLine> lineData(List<BomLineInput> lines, UUID bomId) {
        List<BomLine> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            BomLineInput l = lines.get(i);
            BomLine row = new BomLine();
            row.bomId = bomId;
            row.lineNo = i + 1;
            row.variantId = l.variantId();
            row.quantity = l.quantity();
            row.scrapRate = l.scrapRate() == null ? BigDecimal.ZERO : l.scrapRate();
            row.notes = trimOrNull(l.notes());
            result.add(row);
        }
        return result;
    }

    private ProductVariant loadOutput(UUID variantId) {
        ProductVariant variant = db.first("select v from ProductVariant v where v.id = ?1", ProductVariant.class, variantId).orElse(null);
        Guard.that(variant != null, "Ürün bulunamadı");
        Product product = db.get(Product.class, variant.productId);
        assertProducible(variant, product, "Ürün");
        return variant;
    }

    private void validateLines(UUID outputId, List<BomLineInput> lines, UUID bomId) {
        List<UUID> ids = lines.stream().map(BomLineInput::variantId).toList();
        Guard.that(ids.stream().distinct().count() == ids.size(), "Bir bileşen yalnızca bir kez listelenebilir");
        Guard.that(!ids.contains(outputId), "Ürün kendi bileşeni olamaz");
        Guard.that(lines.stream().allMatch(l -> l.quantity() != null && l.quantity().signum() > 0), "Bileşen miktarı sıfırdan büyük olmalı");
        Guard.that(lines.stream().allMatch(l -> l.scrapRate() == null || l.scrapRate().signum() >= 0), "Fire oranı negatif olamaz");
        Map<UUID, ProductVariant> variants = loadVariants(ids);
        Guard.that(variants.size() == ids.size(), "Bileşen bulunamadı");
        for (ProductVariant variant : variants.values()) assertProducible(variant, db.get(Product.class, variant.productId), variant.sku);
        List<BillOfMaterials> others = bomId == null
                ? db.list("select b from BillOfMaterials b where b.isActive = true", BillOfMaterials.class)
                : db.list("select b from BillOfMaterials b where b.isActive = true and b.id <> ?1", BillOfMaterials.class, bomId);
        Map<UUID, List<UUID>> graph = new HashMap<>();
        for (BillOfMaterials other : others) {
            List<BomLine> otherLines = db.list("select l from BomLine l where l.bomId = ?1", BomLine.class, other.id);
            graph.computeIfAbsent(other.variantId, k -> new ArrayList<>()).addAll(otherLines.stream().map(l -> l.variantId).toList());
        }
        Guard.that(!ProductionMath.createsCycle(outputId, ids, v -> graph.getOrDefault(v, List.of())),
                "Bir bileşen bu üründen üretiliyor; ürün ağacı döngü oluşturur");
    }

    private String nextCode(ProductVariant output) {
        for (int n = 1; ; n++) {
            String code = (output.sku + "-R" + n).toUpperCase();
            if (!db.exists("select b.id from BillOfMaterials b where b.code = ?1", code)) return code;
        }
    }

    private void assertCodeFree(String code, UUID exceptId) {
        boolean taken = exceptId == null
                ? db.exists("select b.id from BillOfMaterials b where b.code = ?1", code)
                : db.exists("select b.id from BillOfMaterials b where b.code = ?1 and b.id <> ?2", code, exceptId);
        Guard.state(!taken, code + " ürün ağacı kodu zaten kullanılıyor");
    }

    private void clearDefault(UUID variantId) {
        for (BillOfMaterials other : db.list("select b from BillOfMaterials b where b.variantId = ?1 and b.isDefault = true", BillOfMaterials.class, variantId)) {
            other.isDefault = false;
        }
        db.flush();
    }

    private void promoteDefault(UUID variantId, UUID except) {
        BillOfMaterials next = db.first("""
                select b from BillOfMaterials b where b.variantId = ?1 and b.isActive = true and b.id <> ?2 order by b.updatedAt desc
                """, BillOfMaterials.class, variantId, except).orElse(null);
        if (next == null) return;
        next.isDefault = true;
        db.flush();
    }

    // ---------- Work orders ----------

    private void ensureRecordSequence(UUID companyId) {
        if (db.exists("select n.id from NumberSequence n where n.code = ?1", RECORD_SEQUENCE)) return;
        NumberSequence row = new NumberSequence();
        row.companyId = companyId;
        row.code = RECORD_SEQUENCE;
        row.name = "Üretim Fişi";
        row.prefix = "URT";
        row.padding = 6;
        row.includeYear = true;
        row.resetYearly = true;
        row.currentYear = LocalDate.now(ZoneOffset.UTC).getYear();
        db.add(row);
        db.flush();
    }

    private WorkOrderDto toDto(WorkOrder w, BillOfMaterials bom, ProductVariant variant, Product product, Unit unit,
                               Warehouse source, Warehouse target, LocalDate today) {
        return new WorkOrderDto(w.id, w.number, w.status, w.bomId, bom.code, info(variant, product, unit), w.quantity, w.producedQty,
                ProductionMath.progress(w.producedQty, w.quantity), w.plannedStart, w.plannedEnd,
                ProductionMath.isLate(w.status, w.plannedEnd, today), w.sourceWarehouseId, source.name, w.targetWarehouseId, target.name,
                w.reference, w.createdAt);
    }

    public WorkOrderDetailDto getWorkOrder(UUID id) {
        WorkOrder w = Guard.found(db.first("select w from WorkOrder w where w.id = ?1", WorkOrder.class, id), "İş emri bulunamadı");
        BillOfMaterials bom = db.get(BillOfMaterials.class, w.bomId);
        ProductVariant variant = db.get(ProductVariant.class, w.variantId);
        Product product = db.get(Product.class, variant.productId);
        Unit unit = db.get(Unit.class, product.unitId);
        Warehouse source = db.get(Warehouse.class, w.sourceWarehouseId);
        Warehouse target = db.get(Warehouse.class, w.targetWarehouseId);
        User createdBy = db.get(User.class, w.createdById);
        List<WorkOrderComponent> components = db.list("select c from WorkOrderComponent c where c.workOrderId = ?1 order by c.lineNo", WorkOrderComponent.class, id);
        List<ProductionRecord> records = db.list("select r from ProductionRecord r where r.workOrderId = ?1 order by r.recordDate, r.createdAt", ProductionRecord.class, id);
        List<UUID> ids = components.stream().map(c -> c.variantId).toList();
        Map<UUID, ProductVariant> variants = loadVariants(ids);
        Map<UUID, BigDecimal> onHand = onHand(ids, w.sourceWarehouseId);
        CostBook book = loadCostBook(ids);
        boolean lotTracked = product.trackingType == TrackingType.LOT;
        List<WorkOrderComponentDto> componentDtos = new ArrayList<>();
        for (WorkOrderComponent c : components) {
            BigDecimal remaining = c.requiredQty.subtract(c.consumedQty).max(BigDecimal.ZERO);
            BigDecimal stockHere = onHand.getOrDefault(c.variantId, BigDecimal.ZERO);
            ProductVariant v = variants.get(c.variantId);
            Product p = db.get(Product.class, v.productId);
            Unit u = db.get(Unit.class, p.unitId);
            componentDtos.add(new WorkOrderComponentDto(c.id, c.lineNo, info(v, p, u), c.quantityPer, c.requiredQty, c.consumedQty, remaining,
                    stockHere, ProductionMath.availability(remaining, stockHere), p.trackingType == TrackingType.LOT));
        }
        List<ProductionRecordDto> recordDtos = new ArrayList<>();
        for (ProductionRecord r : records) {
            User author = db.get(User.class, r.createdById);
            StockMovement issue = r.issueMovementId == null ? null : db.find(StockMovement.class, r.issueMovementId);
            StockMovement receipt = db.get(StockMovement.class, r.receiptMovementId);
            recordDtos.add(new ProductionRecordDto(r.id, r.number, r.recordDate, r.quantity, r.materialCost, r.laborCost, r.overheadCost,
                    r.materialCost.add(r.laborCost).add(r.overheadCost), r.unitCost, r.issueMovementId, issue == null ? null : issue.number,
                    r.receiptMovementId, receipt.number, null, r.notes, author.firstName + " " + author.lastName, r.createdAt));
        }
        BigDecimal standard = DocumentMath.round4(componentDtos.stream()
                .map(c -> c.quantityPer().multiply(book.cost(c.variant().variantId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).add(w.laborPerUnit).add(w.overheadPerUnit));
        BigDecimal produced = recordDtos.stream().map(ProductionRecordDto::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = w.producedQty.signum() > 0 ? DocumentMath.round4(produced.divide(w.producedQty, 12, java.math.RoundingMode.HALF_UP)) : null;
        BigDecimal buildable = ProductionMath.buildable(componentDtos.stream().map(c -> new BuildLine(c.quantityPer(), c.onHand())).toList())
                .min(w.quantity.subtract(w.producedQty).max(BigDecimal.ZERO));
        return new WorkOrderDetailDto(toDto(w, bom, variant, product, unit, source, target, AccountingClock.today()), w.notes, w.laborPerUnit,
                w.overheadPerUnit, standard, actual, lotTracked, createdBy.firstName + " " + createdBy.lastName, w.releasedAt, w.startedAt,
                w.completedAt, w.cancelledAt, componentDtos, recordDtos, buildable, ProductionMath.canEdit(w.status), ProductionMath.canRelease(w.status),
                ProductionMath.canRecord(w.status), ProductionMath.canCancel(w.status, w.producedQty), ProductionMath.canComplete(w.status));
    }

    public WorkOrderDetailDto createWorkOrder(CreateWorkOrder dto) {
        UUID companyId = ctx().requireCompanyId();
        Guard.that(dto.quantity() != null && dto.quantity().signum() > 0, "Miktar sıfırdan büyük olmalı");
        Guard.that(!dto.plannedEnd().isBefore(dto.plannedStart()), "Planlanan bitiş, başlangıçtan önce olamaz");
        BillOfMaterials bom = Guard.found(db.first("select b from BillOfMaterials b where b.id = ?1", BillOfMaterials.class, dto.bomId()), "Ürün ağacı bulunamadı");
        Guard.that(bom.isActive, "Pasif ürün ağacıyla iş emri açılamaz");
        assertWarehouses(dto.sourceWarehouseId(), dto.targetWarehouseId());
        List<BomLine> bomLines = db.list("select l from BomLine l where l.bomId = ?1 order by l.lineNo", BomLine.class, bom.id);
        WorkOrder order = new WorkOrder();
        order.companyId = companyId;
        order.number = numbering.next("WORK_ORDER");
        order.bomId = bom.id;
        order.variantId = bom.variantId;
        order.quantity = dto.quantity();
        order.plannedStart = dto.plannedStart();
        order.plannedEnd = dto.plannedEnd();
        order.sourceWarehouseId = dto.sourceWarehouseId();
        order.targetWarehouseId = dto.targetWarehouseId();
        order.laborPerUnit = ProductionMath.costPerUnit(bom.laborCost, bom.quantity);
        order.overheadPerUnit = ProductionMath.costPerUnit(bom.overheadCost, bom.quantity);
        order.reference = trimOrNull(dto.reference());
        order.notes = trimOrNull(dto.notes());
        order.createdById = ctx().requireUserId();
        db.add(order);
        db.flush();
        for (BomLine l : bomLines) {
            BigDecimal per = ProductionMath.perUnit(l.quantity, l.scrapRate, bom.quantity);
            WorkOrderComponent c = new WorkOrderComponent();
            c.workOrderId = order.id;
            c.lineNo = l.lineNo;
            c.variantId = l.variantId;
            c.quantityPer = per;
            c.requiredQty = ProductionMath.required(per, dto.quantity());
            db.add(c);
        }
        db.flush();
        return getWorkOrder(order.id);
    }

    public WorkOrderDetailDto updateWorkOrder(UUID id, UpdateWorkOrder dto) {
        WorkOrder order = lockOrder(id);
        Guard.state(ProductionMath.canEdit(order.status), "Yalnızca taslak iş emirleri düzenlenebilir");
        Guard.that(dto.quantity() == null || dto.quantity().signum() > 0, "Miktar sıfırdan büyük olmalı");
        LocalDate start = dto.plannedStart() == null ? order.plannedStart : dto.plannedStart();
        LocalDate end = dto.plannedEnd() == null ? order.plannedEnd : dto.plannedEnd();
        Guard.that(!end.isBefore(start), "Planlanan bitiş, başlangıçtan önce olamaz");
        UUID source = dto.sourceWarehouseId() == null ? order.sourceWarehouseId : dto.sourceWarehouseId();
        UUID target = dto.targetWarehouseId() == null ? order.targetWarehouseId : dto.targetWarehouseId();
        if (!source.equals(order.sourceWarehouseId) || !target.equals(order.targetWarehouseId)) assertWarehouses(source, target);
        order.plannedStart = start;
        order.plannedEnd = end;
        order.sourceWarehouseId = source;
        order.targetWarehouseId = target;
        if (dto.reference() != null) order.reference = trimOrNull(dto.reference());
        if (dto.notes() != null) order.notes = trimOrNull(dto.notes());
        if (dto.quantity() != null && dto.quantity().compareTo(order.quantity) != 0) {
            order.quantity = dto.quantity();
            for (WorkOrderComponent c : db.list("select c from WorkOrderComponent c where c.workOrderId = ?1", WorkOrderComponent.class, id)) {
                c.requiredQty = ProductionMath.required(c.quantityPer, order.quantity);
            }
        }
        db.flush();
        return getWorkOrder(id);
    }

    public WorkOrderDetailDto release(UUID id) {
        WorkOrder order = lockOrder(id);
        Guard.state(ProductionMath.canRelease(order.status), "Yalnızca taslak iş emirleri serbest bırakılabilir");
        assertWarehouses(order.sourceWarehouseId, order.targetWarehouseId);
        order.status = WorkOrderStatus.RELEASED;
        order.releasedAt = Instant.now();
        db.flush();
        return getWorkOrder(id);
    }

    public WorkOrderDetailDto cancel(UUID id) {
        WorkOrder order = lockOrder(id);
        Guard.state(ProductionMath.canCancel(order.status, order.producedQty), "Üretim kaydı olan iş emri iptal edilemez; tamamlayın");
        order.status = WorkOrderStatus.CANCELLED;
        order.cancelledAt = Instant.now();
        db.flush();
        return getWorkOrder(id);
    }

    public WorkOrderDetailDto complete(UUID id) {
        WorkOrder order = lockOrder(id);
        Guard.state(ProductionMath.canComplete(order.status), "Yalnızca üretimdeki iş emirleri tamamlanabilir");
        order.status = WorkOrderStatus.COMPLETED;
        order.completedAt = Instant.now();
        db.flush();
        return getWorkOrder(id);
    }

    public WorkOrderDetailDto record(UUID id, RecordProduction dto) {
        UUID companyId = ctx().requireCompanyId();
        ensureRecordSequence(companyId);
        WorkOrder order = lockOrder(id);
        Guard.state(ProductionMath.canRecord(order.status), "Yalnızca serbest bırakılmış veya üretimdeki iş emirlerine üretim girilebilir");
        BigDecimal output = dto.quantity();
        Guard.that(output != null && output.signum() > 0, "Miktar sıfırdan büyük olmalı");
        BigDecimal remaining = order.quantity.subtract(order.producedQty);
        Guard.that(output.compareTo(remaining) <= 0, "En fazla " + StockMovementService.qty(remaining) + " üretilebilir");
        boolean finishes = output.compareTo(remaining) == 0 || Boolean.TRUE.equals(dto.finish());
        LocalDate recordDate = dto.recordDate() == null ? AccountingClock.today() : dto.recordDate();
        ProductVariant productVariant = db.get(ProductVariant.class, order.variantId);
        Product product = db.get(Product.class, productVariant.productId);
        boolean lotTracked = product.trackingType == TrackingType.LOT;
        String lotNo = trimOrNull(dto.lotNo()) == null ? order.number : trimOrNull(dto.lotNo());
        List<WorkOrderComponent> components = db.list("select c from WorkOrderComponent c where c.workOrderId = ?1 order by c.lineNo", WorkOrderComponent.class, id);
        Map<UUID, BigDecimal> overrides = new HashMap<>();
        if (dto.components() != null) {
            for (ComponentUse c : dto.components()) overrides.put(c.variantId(), c.quantity());
        }
        Guard.that(overrides.keySet().stream().allMatch(v -> components.stream().anyMatch(c -> c.variantId.equals(v))), "İş emrinde olmayan bir bileşen girildi");
        Guard.that(overrides.values().stream().allMatch(q -> q != null && q.signum() >= 0), "Sarf miktarı negatif olamaz");
        Map<UUID, ProductVariant> variants = loadVariants(components.stream().map(c -> c.variantId).toList());
        List<LineInput> issueLines = new ArrayList<>();
        Map<WorkOrderComponent, BigDecimal> used = new LinkedHashMap<>();
        for (WorkOrderComponent c : components) {
            BigDecimal qty = overrides.containsKey(c.variantId)
                    ? overrides.get(c.variantId)
                    : ProductionMath.standardConsumption(c.quantityPer, c.requiredQty, c.consumedQty, output, finishes);
            if (qty.signum() <= 0) continue;
            used.put(c, qty);
            ProductVariant v = variants.get(c.variantId);
            Product p = db.get(Product.class, v.productId);
            if (p.trackingType == TrackingType.LOT) issueLines.addAll(allocateLots(order.sourceWarehouseId, v, qty));
            else issueLines.add(new LineInput(c.variantId, qty));
        }
        String number = numbering.next(RECORD_SEQUENCE);
        String notes = order.number + " üretimi";
        PostResult issue = null;
        BigDecimal material = BigDecimal.ZERO;
        List<LowStockItem> low = new ArrayList<>();
        if (!issueLines.isEmpty()) {
            issue = stock.post(new CreateRequest(StockMovementType.ISSUE, recordDate, order.sourceWarehouseId, null, order.number, notes + " — malzeme sarfı", issueLines), null);
            low.addAll(issue.lowStock());
            List<StockMovementLine> lines = db.list("select l from StockMovementLine l where l.movementId = ?1", StockMovementLine.class, issue.movementId());
            material = DocumentMath.round2(lines.stream()
                    .map(l -> l.quantity.multiply(l.unitCost == null ? BigDecimal.ZERO : l.unitCost))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        BigDecimal labor = DocumentMath.round2(order.laborPerUnit.multiply(output));
        BigDecimal overhead = DocumentMath.round2(order.overheadPerUnit.multiply(output));
        BigDecimal unitCost = DocumentMath.round4(material.add(labor).add(overhead).divide(output, 12, java.math.RoundingMode.HALF_UP));
        PostResult receipt = stock.post(new CreateRequest(StockMovementType.RECEIPT, recordDate, null, order.targetWarehouseId, order.number, notes + " — mamul girişi",
                List.of(new LineInput(order.variantId, output, unitCost, null, lotTracked ? lotNo : null, lotTracked ? dto.expiryDate() : null, null, null))), null);
        low.addAll(receipt.lowStock());
        ProductionRecord record = new ProductionRecord();
        record.companyId = companyId;
        record.workOrderId = order.id;
        record.number = number;
        record.recordDate = recordDate;
        record.quantity = output;
        record.materialCost = material;
        record.laborCost = labor;
        record.overheadCost = overhead;
        record.unitCost = unitCost;
        record.issueMovementId = issue == null ? null : issue.movementId();
        record.receiptMovementId = receipt.movementId();
        record.notes = trimOrNull(dto.notes());
        record.createdById = ctx().requireUserId();
        db.add(record);
        for (Map.Entry<WorkOrderComponent, BigDecimal> e : used.entrySet()) e.getKey().consumedQty = e.getKey().consumedQty.add(e.getValue());
        order.producedQty = order.producedQty.add(output);
        if (order.startedAt == null) order.startedAt = Instant.now();
        if (finishes) {
            order.status = WorkOrderStatus.COMPLETED;
            order.completedAt = Instant.now();
        } else {
            order.status = WorkOrderStatus.IN_PROGRESS;
        }
        db.flush();
        stock.notifyLowStock(low);
        return getWorkOrder(id);
    }

    private List<LineInput> allocateLots(UUID warehouseId, ProductVariant variant, BigDecimal quantity) {
        List<Object[]> balances = db.list("""
                select b.lotId, b.quantity, l.lotNo, l.expiryDate from LotBalance b, StockLot l
                 where b.lotId = l.id and b.warehouseId = ?1 and b.quantity > 0 and l.variantId = ?2
                """, Object[].class, warehouseId, variant.id);
        balances.sort(Comparator
                .comparing((Object[] b) -> b[3] == null ? LocalDate.MAX : (LocalDate) b[3])
                .thenComparing(b -> (String) b[2]));
        List<LineInput> lines = new ArrayList<>();
        BigDecimal left = quantity;
        for (Object[] lot : balances) {
            if (left.signum() <= 0) break;
            BigDecimal take = ((BigDecimal) lot[1]).min(left);
            lines.add(new LineInput(variant.id, take, null, (UUID) lot[0], null, null, null, null));
            left = left.subtract(take);
        }
        Guard.that(left.signum() <= 0, variant.sku + " için kaynak depoda yeterli lot stoğu yok");
        return lines;
    }

    private WorkOrder lockOrder(UUID id) {
        db.nativeRows("SELECT id FROM work_orders WHERE id = ?1 FOR UPDATE", id);
        return Guard.found(db.first("select w from WorkOrder w where w.id = ?1", WorkOrder.class, id), "İş emri bulunamadı");
    }

    private void assertWarehouses(UUID... ids) {
        Set<UUID> list = new HashSet<>(Arrays.asList(ids));
        List<Warehouse> rows = db.list("select w from Warehouse w where w.id in ?1", Warehouse.class, list);
        Guard.that(rows.size() == list.size(), "Depo bulunamadı");
        Guard.that(rows.stream().allMatch(w -> w.isActive), "Pasif depo seçilemez");
    }

    // ---------- Requirements (MRP) ----------

    private static final class Demand {
        BigDecimal production = BigDecimal.ZERO;
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal productionIncoming = BigDecimal.ZERO;
        LocalDate neededBy;
        List<RequirementOrderDto> workOrders = new ArrayList<>();
        List<RequirementOrderDto> salesOrders = new ArrayList<>();
    }

    public List<RequirementDto> requirements() {
        List<WorkOrder> orders = db.list("""
                select w from WorkOrder w where w.status in ?1 order by w.plannedStart, w.number
                """, WorkOrder.class, ProductionMath.OPEN_STATUSES);
        Map<UUID, BomShape> boms = defaultBoms();
        Map<UUID, Demand> demands = new HashMap<>();
        Map<UUID, BigDecimal> output = new HashMap<>();
        for (WorkOrder order : orders) {
            output.merge(order.variantId, order.quantity.subtract(order.producedQty).max(BigDecimal.ZERO), BigDecimal::add);
            for (WorkOrderComponent c : db.list("select c from WorkOrderComponent c where c.workOrderId = ?1", WorkOrderComponent.class, order.id)) {
                BigDecimal rem = c.requiredQty.subtract(c.consumedQty).max(BigDecimal.ZERO);
                if (rem.signum() == 0) continue;
                Demand d = demands.computeIfAbsent(c.variantId, k -> new Demand());
                d.production = d.production.add(rem);
                d.neededBy = earliest(d.neededBy, order.plannedStart);
                d.workOrders.add(new RequirementOrderDto(order.id, order.number, rem, order.plannedStart));
            }
        }
        List<UUID> madeIds = new ArrayList<>(boms.keySet());
        if (!madeIds.isEmpty()) {
            List<SalesOrderLine> salesLines = db.list("""
                    select l from SalesOrderLine l, SalesOrder o where l.orderId = o.id and l.variantId in ?1
                      and o.status in (?2, ?3) order by o.orderDate
                    """, SalesOrderLine.class, madeIds, SalesOrderStatus.APPROVED, SalesOrderStatus.PARTIALLY_DELIVERED);
            for (SalesOrderLine line : salesLines) {
                BigDecimal rem = line.quantity.subtract(line.deliveredQty).max(BigDecimal.ZERO);
                if (rem.signum() == 0) continue;
                var order = db.get(dev.enterprisepulse.data.generated.SalesOrder.class, line.orderId);
                Demand d = demands.computeIfAbsent(line.variantId, k -> new Demand());
                LocalDate date = order.deliveryDate != null ? order.deliveryDate : order.orderDate;
                d.sales = d.sales.add(rem);
                d.neededBy = earliest(d.neededBy, date);
                d.salesOrders.add(new RequirementOrderDto(order.id, order.number, rem, date));
            }
        }
        for (Map.Entry<UUID, BigDecimal> e : output.entrySet()) {
            Demand d = demands.get(e.getKey());
            if (d != null) d.productionIncoming = e.getValue();
        }
        List<UUID> ids = new ArrayList<>(demands.keySet());
        if (ids.isEmpty()) return List.of();
        Map<UUID, BigDecimal> onHand = onHand(ids, null);
        List<PurchaseOrderLine> purchaseLines = db.list("""
                select l from PurchaseOrderLine l, PurchaseOrder o where l.orderId = o.id and l.variantId in ?1
                  and o.status in (?2, ?3)
                """, PurchaseOrderLine.class, ids, PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.PARTIALLY_RECEIVED);
        Map<UUID, BigDecimal> incoming = new HashMap<>();
        for (PurchaseOrderLine l : purchaseLines) {
            incoming.merge(l.variantId, l.quantity.subtract(l.receivedQty).max(BigDecimal.ZERO), BigDecimal::add);
        }
        Map<UUID, ProductVariant> variants = loadVariants(ids);
        List<RequirementDto> result = new ArrayList<>();
        for (ProductVariant v : variants.values()) {
            Demand d = demands.get(v.id);
            Product p = db.get(Product.class, v.productId);
            Unit u = db.get(Unit.class, p.unitId);
            BigDecimal gross = d.production.add(d.sales);
            BigDecimal stockHere = onHand.getOrDefault(v.id, BigDecimal.ZERO);
            BigDecimal purchase = incoming.getOrDefault(v.id, BigDecimal.ZERO);
            BigDecimal shortage = ProductionMath.netRequirement(gross, stockHere, purchase.add(d.productionIncoming));
            BomShape bom = boms.get(v.id);
            result.add(new RequirementDto(info(v, p, u), d.production, d.sales, gross, stockHere, purchase, d.productionIncoming, shortage,
                    d.neededBy, bom != null, bom == null ? null : bom.id(), p.purchasePrice, d.workOrders, d.salesOrders));
        }
        result.sort(Comparator
                .comparing((RequirementDto r) -> r.shortage().signum() > 0 ? 0 : 1)
                .thenComparing(r -> r.neededBy() == null ? LocalDate.MAX : r.neededBy())
                .thenComparing(r -> r.variant().sku()));
        return result;
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        return a != null && !a.isAfter(b) ? a : b;
    }

    public CreatedDocumentDto createPurchaseRequest(CreateRequirementRequest dto) {
        List<UUID> ids = dto.lines().stream().map(RequirementLineInput::variantId).toList();
        Guard.that(!ids.isEmpty(), "En az bir satır gerekli");
        Guard.that(ids.stream().distinct().count() == ids.size(), "Bir ürün yalnızca bir kez listelenebilir");
        Map<UUID, ProductVariant> variants = loadVariants(ids);
        Guard.that(variants.size() == ids.size(), "Ürün bulunamadı");
        PurchaseRequestService.Detail created = requests.create(new PurchaseRequestService.CreateRequest(
                "Üretim malzeme ihtiyacı (MRP)", dto.neededBy(), null, "Malzeme ihtiyaç planlamasındaki eksiklerden oluşturuldu.",
                dto.lines().stream().map(l -> {
                    Product p = db.get(Product.class, variants.get(l.variantId()).productId);
                    return new PurchaseRequestService.LineInput(l.variantId(), null, l.quantity(), p.purchasePrice);
                }).toList()));
        return new CreatedDocumentDto(created.id(), created.number());
    }

    public SummaryDto summary() {
        LocalDate today = AccountingClock.today();
        LocalDate monthStart = LocalDate.of(today.getYear(), today.getMonth(), 1);
        Instant monthStartUtc = LocalDateTime.of(today.getYear(), today.getMonth(), 1, 0, 0).toInstant(ZoneOffset.UTC);
        Map<WorkOrderStatus, Long> statuses = new HashMap<>();
        for (Object[] row : db.list("""
                select w.status, count(w) from WorkOrder w where w.status in ?1 group by w.status
                """, Object[].class, ProductionMath.OPEN_STATUSES)) {
            statuses.put((WorkOrderStatus) row[0], (Long) row[1]);
        }
        int late = (int) db.count("select count(w) from WorkOrder w where w.status in ?1 and w.plannedEnd < ?2", ProductionMath.OPEN_STATUSES, today);
        int completed = (int) db.count("select count(w) from WorkOrder w where w.status = ?1 and w.completedAt >= ?2", WorkOrderStatus.COMPLETED, monthStartUtc);
        BigDecimal value = db.first("""
                select coalesce(sum(r.materialCost + r.laborCost + r.overheadCost), 0) from ProductionRecord r where r.recordDate >= ?1
                """, BigDecimal.class, monthStart).orElse(BigDecimal.ZERO);
        int activeBoms = (int) db.count("select count(b) from BillOfMaterials b where b.isActive = true");
        List<WorkOrder> upcoming = db.list("""
                select w from WorkOrder w where w.status in ?1 order by w.plannedStart, w.number
                """, WorkOrder.class, ProductionMath.OPEN_STATUSES).stream().limit(5).toList();
        int shortages = (int) requirements().stream().filter(r -> r.shortage().signum() > 0).count();
        List<WorkOrderDto> upcomingDtos = new ArrayList<>();
        for (WorkOrder w : upcoming) {
            BillOfMaterials bom = db.get(BillOfMaterials.class, w.bomId);
            ProductVariant variant = db.get(ProductVariant.class, w.variantId);
            Product product = db.get(Product.class, variant.productId);
            Unit unit = db.get(Unit.class, product.unitId);
            Warehouse source = db.get(Warehouse.class, w.sourceWarehouseId);
            Warehouse target = db.get(Warehouse.class, w.targetWarehouseId);
            upcomingDtos.add(toDto(w, bom, variant, product, unit, source, target, today));
        }
        return new SummaryDto(activeBoms,
                statuses.getOrDefault(WorkOrderStatus.DRAFT, 0L).intValue(),
                statuses.getOrDefault(WorkOrderStatus.RELEASED, 0L).intValue(),
                statuses.getOrDefault(WorkOrderStatus.IN_PROGRESS, 0L).intValue(),
                late, completed, DocumentMath.round2(value), shortages, upcomingDtos);
    }
}
