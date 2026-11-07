package dev.enterprisepulse.inventory;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.LotBalance;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.SerialNumber;
import dev.enterprisepulse.data.generated.SerialStatus;
import dev.enterprisepulse.data.generated.StockBalance;
import dev.enterprisepulse.data.generated.StockLot;
import dev.enterprisepulse.data.generated.StockMovement;
import dev.enterprisepulse.data.generated.StockMovementLine;
import dev.enterprisepulse.data.generated.StockMovementSerial;
import dev.enterprisepulse.data.generated.StockMovementType;
import dev.enterprisepulse.data.generated.StockTransaction;
import dev.enterprisepulse.data.generated.TrackingType;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NotificationService.NotifyRequest;
import dev.enterprisepulse.platform.NumberingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Posts stock documents. A movement is applied immediately and never edited. */
@Service
@Transactional
public class StockMovementService {
    public record LineInput(UUID variantId, BigDecimal quantity, BigDecimal unitCost, UUID lotId, String lotNo, LocalDate expiryDate,
                             List<String> serialNos, List<UUID> serialIds) {
        public LineInput(UUID variantId, BigDecimal quantity) {
            this(variantId, quantity, null, null, null, null, null, null);
        }
    }

    public record CreateRequest(StockMovementType type, LocalDate movementDate, UUID sourceWarehouseId, UUID targetWarehouseId,
                                 String reference, String notes, List<LineInput> lines) {}

    public record LowStockItem(UUID variantId, UUID productId, String sku, String name, BigDecimal onHand, BigDecimal minStock) {}

    public record PostResult(UUID movementId, String number, List<LowStockItem> lowStock) {}

    public record LineDto(UUID id, UUID variantId, UUID productId, String sku, String productName, String variantName, String unitCode,
                           BigDecimal quantity, BigDecimal unitCost, BigDecimal lineValue, String lotNo, LocalDate expiryDate, List<String> serialNos) {}

    public record DetailDto(UUID id, String number, StockMovementType type, LocalDate movementDate, UUID sourceWarehouseId, String sourceWarehouseName,
                             UUID targetWarehouseId, String targetWarehouseName, String reference, String notes, int lineCount, BigDecimal totalValue,
                             String createdByName, Instant createdAt, UUID stockCountId, String stockCountNumber, UUID goodsReceiptId, String goodsReceiptNumber,
                             List<LineDto> lines) {}

    private final Db db;
    private final NumberingService numbering;
    private final NotificationService notifications;

    public StockMovementService(Db db, NumberingService numbering, NotificationService notifications) {
        this.db = db;
        this.numbering = numbering;
        this.notifications = notifications;
    }

    public DetailDto create(CreateRequest request) {
        PostResult result = post(request, null);
        notifyLowStock(result.lowStock());
        return detail(result.movementId());
    }

    /** Posts inside the caller's transaction (counts and receipts use it too). */
    public PostResult post(CreateRequest dto, UUID reservationOrderId) {
        Guard.that(dto.lines() != null && !dto.lines().isEmpty(), "En az bir satır gerekli");
        Guard.that(!dto.movementDate().isAfter(LocalDate.now().plusDays(1)), "Hareket tarihi ileri bir tarih olamaz");
        Warehouse source = null;
        Warehouse target = null;
        boolean needsSource = dto.type() == StockMovementType.ISSUE || dto.type() == StockMovementType.TRANSFER;
        boolean needsTarget = dto.type() != StockMovementType.ISSUE;
        Guard.that(needsSource == (dto.sourceWarehouseId() != null), needsSource ? "Çıkış deposu seçilmeli" : "Bu hareket türünde çıkış deposu olmaz");
        Guard.that(needsTarget == (dto.targetWarehouseId() != null), needsTarget ? "Giriş deposu seçilmeli" : "Bu hareket türünde giriş deposu olmaz");
        Guard.that(dto.sourceWarehouseId() == null || !dto.sourceWarehouseId().equals(dto.targetWarehouseId()), "Çıkış ve giriş depoları farklı olmalı");
        if (dto.sourceWarehouseId() != null) source = activeWarehouse(dto.sourceWarehouseId());
        if (dto.targetWarehouseId() != null) target = activeWarehouse(dto.targetWarehouseId());

        List<UUID> variantIds = dto.lines().stream().map(LineInput::variantId).distinct().sorted().toList();
        for (UUID id : variantIds) db.nativeRows("SELECT id FROM product_variants WHERE id = ?1 FOR UPDATE", id);
        Map<UUID, ProductVariant> variants = new HashMap<>();
        Map<UUID, Product> products = new HashMap<>();
        for (UUID id : variantIds) {
            ProductVariant v = db.find(ProductVariant.class, id);
            Guard.that(v != null, "Bir veya daha fazla ürün bulunamadı");
            variants.put(id, v);
            products.put(v.productId, db.get(Product.class, v.productId));
        }
        validate(dto, variants, products);

        Map<UUID, BigDecimal> onHand = totals(variantIds);
        Map<UUID, BigDecimal> before = new HashMap<>(onHand);
        Map<UUID, BigDecimal> average = new HashMap<>();
        variants.values().forEach(v -> average.put(v.id, v.averageCost == null ? BigDecimal.ZERO : v.averageCost));

        StockMovement movement = new StockMovement();
        movement.number = numbering.next("STOCK_MOVEMENT");
        movement.type = dto.type();
        movement.movementDate = dto.movementDate();
        movement.sourceWarehouseId = source == null ? null : source.id;
        movement.targetWarehouseId = target == null ? null : target.id;
        movement.reference = clean(dto.reference());
        movement.notes = clean(dto.notes());
        movement.createdById = CurrentUser.get().requireUserId();
        db.add(movement);
        db.flush();

        for (LineInput input : dto.lines()) {
            ProductVariant variant = variants.get(input.variantId());
            Product product = products.get(variant.productId);
            boolean incoming = isIncoming(dto.type(), input.quantity());
            UUID lotId = resolveLot(variant, product, input, incoming);
            BigDecimal cost = average.get(variant.id);
            if (dto.type() == StockMovementType.RECEIPT) {
                BigDecimal receiptCost = input.unitCost() == null ? cost : input.unitCost();
                average.put(variant.id, StockMath.weightedAverageCost(onHand.get(variant.id), cost, input.quantity(), receiptCost));
                cost = receiptCost;
            }
            StockMovementLine line = new StockMovementLine();
            line.movementId = movement.id;
            line.variantId = variant.id;
            line.lotId = lotId;
            line.quantity = input.quantity();
            line.unitCost = cost;
            db.add(line);
            db.flush();
            moveSerials(dto.type(), variant, product, input, line.id, source, target);

            List<Entry> entries = new ArrayList<>();
            if (dto.type() == StockMovementType.ISSUE) entries.add(new Entry(source, input.quantity().negate()));
            else if (dto.type() == StockMovementType.TRANSFER) {
                entries.add(new Entry(source, input.quantity().negate()));
                entries.add(new Entry(target, input.quantity()));
            } else entries.add(new Entry(target, input.quantity()));
            boolean guardReservations = dto.type() == StockMovementType.ISSUE || dto.type() == StockMovementType.TRANSFER;
            for (Entry entry : entries) {
                apply(movement, line, entry.warehouse, variant, lotId, entry.delta, cost, guardReservations, reservationOrderId);
                onHand.put(variant.id, onHand.get(variant.id).add(entry.delta));
            }
        }
        for (ProductVariant v : variants.values()) {
            if (v.averageCost.compareTo(average.get(v.id)) != 0) v.averageCost = average.get(v.id);
        }
        db.flush();

        List<LowStockItem> low = new ArrayList<>();
        for (ProductVariant v : variants.values()) {
            if (StockMath.crossedBelowMinimum(before.get(v.id), onHand.get(v.id), v.minStock)) {
                low.add(new LowStockItem(v.id, v.productId, v.sku, label(products.get(v.productId), v), onHand.get(v.id), v.minStock));
            }
        }
        return new PostResult(movement.id, movement.number, low);
    }

    public void notifyLowStock(List<LowStockItem> items) {
        if (items == null || items.isEmpty()) return;
        UUID companyId = CurrentUser.get().requireCompanyId();
        List<UUID> users = notifications.usersWithPermission(companyId, "inventory:update");
        for (LowStockItem item : items) {
            notifications.notify(new NotifyRequest(users, companyId, "inventory.low_stock", "Kritik stok: " + item.sku(),
                    item.name() + " stoğu " + qty(item.onHand()) + " — minimum " + qty(item.minStock()) + ".", "/r/products/" + item.productId()));
        }
    }

    public DetailDto detail(UUID id) {
        StockMovement m = Guard.found(db.first("select m from StockMovement m where m.id = ?1", StockMovement.class, id), "Stok hareketi bulunamadı");
        Warehouse source = m.sourceWarehouseId == null ? null : db.find(Warehouse.class, m.sourceWarehouseId);
        Warehouse target = m.targetWarehouseId == null ? null : db.find(Warehouse.class, m.targetWarehouseId);
        var user = db.find(dev.enterprisepulse.data.generated.User.class, m.createdById);
        List<StockMovementLine> rows = db.list("select l from StockMovementLine l where l.movementId = ?1", StockMovementLine.class, m.id);
        List<LineDto> lines = new ArrayList<>();
        for (StockMovementLine l : rows) {
            ProductVariant v = db.get(ProductVariant.class, l.variantId);
            Product p = db.get(Product.class, v.productId);
            var unit = db.find(dev.enterprisepulse.data.generated.Unit.class, p.unitId);
            StockLot lot = l.lotId == null ? null : db.find(StockLot.class, l.lotId);
            List<String> serials = db.list("""
                    select s.serialNo from SerialNumber s, StockMovementSerial ms
                     where ms.lineId = ?1 and ms.serialId = s.id order by s.serialNo""", String.class, l.id);
            BigDecimal cost = l.unitCost == null ? BigDecimal.ZERO : l.unitCost;
            lines.add(new LineDto(l.id, v.id, p.id, v.sku, p.name, v.name, unit == null ? "" : unit.code, l.quantity, cost,
                    l.quantity.multiply(cost).setScale(2, RoundingMode.HALF_UP), lot == null ? null : lot.lotNo, lot == null ? null : lot.expiryDate, serials));
        }
        lines.sort((a, b) -> a.sku().compareTo(b.sku()));
        BigDecimal total = lines.stream().map(LineDto::lineValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        var count = db.first("select c.id, c.number from StockCount c where c.adjustmentId = ?1", Object[].class, m.id).orElse(null);
        var receipt = db.first("select r.id, r.number from GoodsReceipt r where r.stockMovementId = ?1", Object[].class, m.id).orElse(null);
        return new DetailDto(m.id, m.number, m.type, m.movementDate, m.sourceWarehouseId, source == null ? null : source.name,
                m.targetWarehouseId, target == null ? null : target.name, m.reference, m.notes, lines.size(), total,
                user == null ? "" : user.firstName + " " + user.lastName, m.createdAt,
                count == null ? null : (UUID) count[0], count == null ? null : (String) count[1],
                receipt == null ? null : (UUID) receipt[0], receipt == null ? null : (String) receipt[1], lines);
    }

    private Warehouse activeWarehouse(UUID id) {
        Warehouse w = db.first("select w from Warehouse w where w.id = ?1", Warehouse.class, id).orElse(null);
        Guard.that(w != null, "Depo bulunamadı");
        Guard.that(w.isActive, w.name + " deposu pasif");
        return w;
    }

    private void validate(CreateRequest dto, Map<UUID, ProductVariant> variants, Map<UUID, Product> products) {
        Set<String> seen = new HashSet<>();
        int i = 0;
        for (LineInput line : dto.lines()) {
            i++;
            ProductVariant v = variants.get(line.variantId());
            Product p = products.get(v.productId);
            String label = "Satır " + i + " (" + v.sku + ")";
            boolean incoming = isIncoming(dto.type(), line.quantity());
            Guard.that(p.type == ProductType.GOODS, label + ": hizmetler stokta izlenmez");
            Guard.that(dto.type() == StockMovementType.ADJUSTMENT ? line.quantity().signum() != 0 : line.quantity().signum() > 0, label + ": geçersiz miktar");
            Guard.that(!incoming || (v.isActive && p.isActive && p.deletedAt == null), label + ": ürün pasif");
            Guard.that(line.unitCost() == null || dto.type() == StockMovementType.RECEIPT, label + ": birim maliyet yalnızca girişte girilir");
            Guard.that(line.unitCost() == null || line.unitCost().signum() >= 0, label + ": birim maliyet negatif olamaz");
            boolean hasLot = line.lotId() != null || (line.lotNo() != null && !line.lotNo().isBlank());
            boolean hasSerials = (line.serialNos() != null && !line.serialNos().isEmpty()) || (line.serialIds() != null && !line.serialIds().isEmpty());
            Guard.that(p.trackingType == TrackingType.LOT || (!hasLot && line.expiryDate() == null), label + ": ürün lot takipli değil");
            Guard.that(p.trackingType == TrackingType.SERIAL || !hasSerials, label + ": ürün seri takipli değil");
            Guard.that(p.trackingType != TrackingType.LOT || hasLot, label + ": lot numarası gerekli");
            if (p.trackingType != TrackingType.SERIAL) continue;
            Guard.that(StockMath.isWhole(line.quantity()), label + ": seri takipli miktar tam sayı olmalı");
            Guard.that(!incoming || line.serialIds() == null || line.serialIds().isEmpty(), label + ": girişte yeni seri numaraları yazılır");
            Guard.that(incoming || line.serialNos() == null || line.serialNos().isEmpty(), label + ": çıkışta stoktaki seri numaraları seçilir");
            List<String> serials = incoming
                    ? line.serialNos().stream().map(String::trim).toList()
                    : line.serialIds().stream().map(UUID::toString).toList();
            Guard.that(serials.size() == line.quantity().abs().intValue(), label + ": " + qty(line.quantity().abs()) + " seri numarası gerekli");
            for (String s : serials) Guard.that(!s.isEmpty() && seen.add(v.id + ":" + s), label + ": tekrarlanan seri numarası " + s);
        }
    }

    private UUID resolveLot(ProductVariant v, Product p, LineInput line, boolean incoming) {
        if (p.trackingType != TrackingType.LOT) return null;
        if (line.lotId() != null) {
            Guard.that(db.exists("select l.id from StockLot l where l.id = ?1 and l.variantId = ?2", line.lotId(), v.id), v.sku + " için lot bulunamadı");
            return line.lotId();
        }
        String lotNo = line.lotNo().trim();
        StockLot lot = db.first("select l from StockLot l where l.variantId = ?1 and l.lotNo = ?2", StockLot.class, v.id, lotNo).orElse(null);
        if (!incoming) {
            Guard.that(lot != null, v.sku + " için " + lotNo + " lotu bulunamadı");
            return lot.id;
        }
        if (lot == null) {
            lot = new StockLot();
            lot.variantId = v.id;
            lot.lotNo = lotNo;
            lot.expiryDate = line.expiryDate();
            db.add(lot);
        } else if (line.expiryDate() != null) lot.expiryDate = line.expiryDate();
        db.flush();
        return lot.id;
    }

    private void moveSerials(StockMovementType type, ProductVariant v, Product p, LineInput line, UUID lineId, Warehouse source, Warehouse target) {
        if (p.trackingType != TrackingType.SERIAL) return;
        List<SerialNumber> serials;
        if (isIncoming(type, line.quantity())) {
            List<String> numbers = line.serialNos().stream().map(String::trim).toList();
            serials = new ArrayList<>(db.list("select s from SerialNumber s where s.variantId = ?1 and s.serialNo in ?2", SerialNumber.class, v.id, numbers));
            serials.stream().filter(s -> s.status == SerialStatus.IN_STOCK).findFirst()
                    .ifPresent(s -> { throw AppException.conflict(s.serialNo + " seri numarası zaten stokta"); });
            for (SerialNumber s : serials) {
                s.status = SerialStatus.IN_STOCK;
                s.warehouseId = target.id;
            }
            Set<String> known = new HashSet<>();
            serials.forEach(s -> known.add(s.serialNo));
            for (String n : numbers) {
                if (known.contains(n)) continue;
                SerialNumber created = new SerialNumber();
                created.variantId = v.id;
                created.serialNo = n;
                created.warehouseId = target.id;
                db.add(created);
                serials.add(created);
            }
        } else {
            Warehouse from = type == StockMovementType.ADJUSTMENT ? target : source;
            serials = db.list("select s from SerialNumber s where s.id in ?1 and s.variantId = ?2", SerialNumber.class, line.serialIds(), v.id);
            Guard.that(serials.size() == line.serialIds().size(), v.sku + " için bilinmeyen seri numarası");
            serials.stream().filter(s -> s.status != SerialStatus.IN_STOCK || !from.id.equals(s.warehouseId)).findFirst()
                    .ifPresent(s -> { throw AppException.badRequest(s.serialNo + " seri numarası " + from.name + " deposunda stokta değil"); });
            for (SerialNumber s : serials) {
                if (type == StockMovementType.TRANSFER) s.warehouseId = target.id;
                else {
                    s.status = SerialStatus.ISSUED;
                    s.warehouseId = null;
                }
            }
        }
        for (SerialNumber s : serials) {
            StockMovementSerial link = new StockMovementSerial();
            link.lineId = lineId;
            link.serialId = s.id;
            db.add(link);
        }
        db.flush();
    }

    private void apply(StockMovement movement, StockMovementLine line, Warehouse warehouse, ProductVariant v, UUID lotId,
                        BigDecimal delta, BigDecimal cost, boolean guardReservations, UUID reservationOrderId) {
        StockBalance balance = db.first("select b from StockBalance b where b.warehouseId = ?1 and b.variantId = ?2",
                StockBalance.class, warehouse.id, v.id).orElse(null);
        if (delta.signum() < 0) {
            Guard.that(balance != null && balance.quantity.compareTo(delta.negate()) >= 0, warehouse.name + " deposunda yetersiz " + v.sku + " stoğu");
            balance.quantity = balance.quantity.add(delta);
            if (lotId != null) {
                LotBalance lot = db.first("select b from LotBalance b where b.warehouseId = ?1 and b.lotId = ?2", LotBalance.class, warehouse.id, lotId).orElse(null);
                Guard.that(lot != null && lot.quantity.compareTo(delta.negate()) >= 0, warehouse.name + " deposunda seçilen lotta yetersiz " + v.sku + " stoğu");
                lot.quantity = lot.quantity.add(delta);
            }
        } else {
            if (balance == null) {
                balance = new StockBalance();
                balance.warehouseId = warehouse.id;
                balance.variantId = v.id;
                db.add(balance);
            }
            balance.quantity = balance.quantity.add(delta);
            if (lotId != null) {
                LotBalance lot = db.first("select b from LotBalance b where b.warehouseId = ?1 and b.lotId = ?2", LotBalance.class, warehouse.id, lotId).orElse(null);
                if (lot == null) {
                    lot = new LotBalance();
                    lot.warehouseId = warehouse.id;
                    lot.lotId = lotId;
                    db.add(lot);
                }
                lot.quantity = lot.quantity.add(delta);
            }
        }
        if (delta.signum() < 0 && guardReservations) {
            BigDecimal others = reserved(warehouse.id, v.id, reservationOrderId);
            Guard.that(balance.quantity.compareTo(others) >= 0,
                    warehouse.name + " deposunda kullanılabilir " + v.sku + " stoğu yetersiz: " + qty(others) + " satış siparişlerine ayrılmış");
        }
        StockTransaction tx = new StockTransaction();
        tx.movementId = movement.id;
        tx.lineId = line.id;
        tx.warehouseId = warehouse.id;
        tx.variantId = v.id;
        tx.lotId = lotId;
        tx.quantity = delta;
        tx.unitCost = cost;
        tx.balanceAfter = balance.quantity;
        tx.movementDate = movement.movementDate;
        db.add(tx);
        db.flush();
    }

    private BigDecimal reserved(UUID warehouseId, UUID variantId, UUID reservationOrderId) {
        List<BigDecimal> rows = reservationOrderId == null
                ? db.list("select coalesce(sum(r.quantity), 0) from StockReservation r where r.warehouseId = ?1 and r.variantId = ?2", BigDecimal.class, warehouseId, variantId)
                : db.list("""
                        select coalesce(sum(r.quantity), 0) from StockReservation r
                         where r.warehouseId = ?1 and r.variantId = ?2
                           and r.orderLineId not in (select l.id from SalesOrderLine l where l.orderId = ?3)""",
                        BigDecimal.class, warehouseId, variantId, reservationOrderId);
        return rows.isEmpty() || rows.get(0) == null ? BigDecimal.ZERO : rows.get(0);
    }

    private Map<UUID, BigDecimal> totals(List<UUID> variantIds) {
        Map<UUID, BigDecimal> totals = new HashMap<>();
        variantIds.forEach(id -> totals.put(id, BigDecimal.ZERO));
        for (Object[] row : db.list("select b.variantId, coalesce(sum(b.quantity), 0) from StockBalance b where b.variantId in ?1 group by b.variantId", Object[].class, variantIds)) {
            totals.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return totals;
    }

    public static boolean isIncoming(StockMovementType type, BigDecimal quantity) {
        return type == StockMovementType.RECEIPT || (type == StockMovementType.ADJUSTMENT && quantity.signum() > 0);
    }

    public static String label(Product product, ProductVariant variant) {
        return variant.name != null && !variant.name.isBlank() ? product.name + " (" + variant.name + ")" : product.name;
    }

    public static String qty(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Entry(Warehouse warehouse, BigDecimal delta) {}
}
