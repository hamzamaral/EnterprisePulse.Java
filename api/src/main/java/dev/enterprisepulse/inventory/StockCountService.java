package dev.enterprisepulse.inventory;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.LotBalance;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.StockBalance;
import dev.enterprisepulse.data.generated.StockCount;
import dev.enterprisepulse.data.generated.StockCountLine;
import dev.enterprisepulse.data.generated.StockCountStatus;
import dev.enterprisepulse.data.generated.StockLot;
import dev.enterprisepulse.data.generated.StockMovementType;
import dev.enterprisepulse.data.generated.TrackingType;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.inventory.StockMovementService.LineInput;
import dev.enterprisepulse.inventory.StockMovementService.LowStockItem;
import dev.enterprisepulse.platform.NumberingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StockCountService {
    public record CreateRequest(UUID warehouseId, UUID categoryId, LocalDate countDate, String notes) {}

    public record LineUpdate(UUID id, BigDecimal countedQty) {}

    public record AddLine(UUID variantId, String lotNo, BigDecimal countedQty) {}

    public record LineDto(UUID id, UUID variantId, String sku, String productName, String variantName, String lotNo, BigDecimal expectedQty,
                           BigDecimal countedQty, BigDecimal difference) {}

    public record Detail(UUID id, String number, UUID warehouseId, String warehouseName, StockCountStatus status, LocalDate countDate, String notes,
                          String createdByName, Instant completedAt, UUID adjustmentId, String adjustmentNumber, int lineCount, int countedCount,
                          List<LineDto> lines) {}

    private final Db db;
    private final NumberingService numbering;
    private final StockMovementService movements;

    public StockCountService(Db db, NumberingService numbering, StockMovementService movements) {
        this.db = db;
        this.numbering = numbering;
        this.movements = movements;
    }

    public Detail create(CreateRequest request) {
        Warehouse warehouse = db.get(Warehouse.class, request.warehouseId());
        Guard.that(warehouse.isActive, warehouse.name + " deposu pasif");
        db.nativeRows("SELECT id FROM warehouses WHERE id = ?1 FOR UPDATE", warehouse.id);
        Guard.state(!db.exists("select c.id from StockCount c where c.warehouseId = ?1 and c.status = ?2", warehouse.id, StockCountStatus.DRAFT),
                "Bu depoda açık bir sayım var");
        StockCount count = new StockCount();
        count.number = numbering.next("STOCK_COUNT");
        count.warehouseId = warehouse.id;
        count.countDate = request.countDate() == null ? LocalDate.now() : request.countDate();
        count.notes = request.notes();
        count.createdById = CurrentUser.get().requireUserId();
        db.add(count);
        db.flush();
        for (ProductVariant v : db.list("select v from ProductVariant v order by v.sku", ProductVariant.class)) {
            Product p = db.find(Product.class, v.productId);
            if (p == null || p.deletedAt != null || p.type != ProductType.GOODS) continue;
            if (request.categoryId() != null && !request.categoryId().equals(p.categoryId)) continue;
            if (p.trackingType == TrackingType.SERIAL) continue;
            if (p.trackingType == TrackingType.LOT) {
                for (LotBalance b : db.list("select b from LotBalance b where b.warehouseId = ?1 and b.quantity > 0", LotBalance.class, warehouse.id)) {
                    StockLot lot = db.get(StockLot.class, b.lotId);
                    if (!lot.variantId.equals(v.id)) continue;
                    addLine(count.id, v.id, lot.id, b.quantity);
                }
            } else {
                BigDecimal qty = db.first("select b.quantity from StockBalance b where b.warehouseId = ?1 and b.variantId = ?2", BigDecimal.class, warehouse.id, v.id).orElse(BigDecimal.ZERO);
                if (qty.signum() > 0) addLine(count.id, v.id, null, qty);
            }
        }
        db.flush();
        return detail(count.id);
    }

    public Detail updateLines(UUID id, List<LineUpdate> updates) {
        StockCount count = draft(id);
        for (LineUpdate update : updates) {
            StockCountLine line = db.first("select l from StockCountLine l where l.id = ?1 and l.countId = ?2", StockCountLine.class, update.id(), count.id).orElse(null);
            Guard.that(line != null, "Sayım satırı bulunamadı");
            if (update.countedQty() != null) Guard.that(update.countedQty().signum() >= 0, "Sayılan miktar negatif olamaz");
            line.countedQty = update.countedQty();
        }
        db.flush();
        return detail(id);
    }

    public Detail addLine(UUID id, AddLine body) {
        StockCount count = draft(id);
        ProductVariant v = db.get(ProductVariant.class, body.variantId());
        Product p = db.get(Product.class, v.productId);
        Guard.that(p.type == ProductType.GOODS && p.trackingType != TrackingType.SERIAL, "Seri takipli ürünler sayıma satır olarak eklenmez");
        UUID lotId = null;
        BigDecimal expected = db.first("select b.quantity from StockBalance b where b.warehouseId = ?1 and b.variantId = ?2", BigDecimal.class, count.warehouseId, v.id).orElse(BigDecimal.ZERO);
        if (p.trackingType == TrackingType.LOT) {
            Guard.that(body.lotNo() != null && !body.lotNo().isBlank(), "Lot numarası gerekli");
            StockLot lot = db.first("select l from StockLot l where l.variantId = ?1 and l.lotNo = ?2", StockLot.class, v.id, body.lotNo().trim()).orElse(null);
            if (lot == null) {
                lot = new StockLot();
                lot.variantId = v.id;
                lot.lotNo = body.lotNo().trim();
                db.add(lot);
                db.flush();
            }
            lotId = lot.id;
            expected = db.first("select b.quantity from LotBalance b where b.warehouseId = ?1 and b.lotId = ?2", BigDecimal.class, count.warehouseId, lotId).orElse(BigDecimal.ZERO);
        }
        Guard.state(!db.exists("select l.id from StockCountLine l where l.countId = ?1 and l.variantId = ?2 and ((?3 is null and l.lotId is null) or l.lotId = ?3)",
                count.id, v.id, lotId), "Bu satır sayımda zaten var");
        StockCountLine line = addLine(count.id, v.id, lotId, expected);
        line.countedQty = body.countedQty();
        db.flush();
        return detail(id);
    }

    public Detail complete(UUID id) {
        StockCount count = draft(id);
        List<StockCountLine> lines = db.list("select l from StockCountLine l where l.countId = ?1 and l.countedQty is not null", StockCountLine.class, id);
        Guard.that(!lines.isEmpty(), "Henüz hiçbir satır sayılmadı");
        List<LineInput> diffs = new ArrayList<>();
        for (StockCountLine line : lines) {
            BigDecimal diff = line.countedQty.subtract(line.expectedQty);
            if (diff.signum() != 0) diffs.add(new LineInput(line.variantId, diff, null, line.lotId, null, null, null, null));
        }
        List<LowStockItem> low = List.of();
        if (!diffs.isEmpty()) {
            var result = movements.post(new StockMovementService.CreateRequest(StockMovementType.ADJUSTMENT, count.countDate, null, count.warehouseId,
                    count.number, count.notes, diffs), null);
            count.adjustmentId = result.movementId();
            low = result.lowStock();
        }
        count.status = StockCountStatus.COMPLETED;
        count.completedAt = Instant.now();
        db.flush();
        movements.notifyLowStock(low);
        return detail(id);
    }

    public Detail cancel(UUID id) {
        StockCount count = draft(id);
        count.status = StockCountStatus.CANCELLED;
        db.flush();
        return detail(id);
    }

    public Detail detail(UUID id) {
        StockCount count = Guard.found(db.first("select c from StockCount c where c.id = ?1", StockCount.class, id), "Sayım bulunamadı");
        Warehouse warehouse = db.get(Warehouse.class, count.warehouseId);
        User user = db.find(User.class, count.createdById);
        var adjustment = count.adjustmentId == null ? null : db.find(dev.enterprisepulse.data.generated.StockMovement.class, count.adjustmentId);
        List<LineDto> lines = new ArrayList<>();
        for (StockCountLine line : db.list("select l from StockCountLine l where l.countId = ?1", StockCountLine.class, count.id)) {
            ProductVariant v = db.get(ProductVariant.class, line.variantId);
            Product p = db.get(Product.class, v.productId);
            StockLot lot = line.lotId == null ? null : db.find(StockLot.class, line.lotId);
            lines.add(new LineDto(line.id, v.id, v.sku, p.name, v.name, lot == null ? null : lot.lotNo, line.expectedQty, line.countedQty,
                    line.countedQty == null ? null : line.countedQty.subtract(line.expectedQty)));
        }
        lines.sort(Comparator.comparing(LineDto::sku).thenComparing(l -> l.lotNo() == null ? "" : l.lotNo()));
        int counted = (int) lines.stream().filter(l -> l.countedQty() != null).count();
        return new Detail(count.id, count.number, warehouse.id, warehouse.name, count.status, count.countDate, count.notes,
                user == null ? "" : user.firstName + " " + user.lastName, count.completedAt, count.adjustmentId, adjustment == null ? null : adjustment.number,
                lines.size(), counted, lines);
    }

    private StockCount draft(UUID id) {
        db.nativeRows("SELECT id FROM stock_counts WHERE id = ?1 FOR UPDATE", id);
        StockCount count = Guard.found(db.first("select c from StockCount c where c.id = ?1", StockCount.class, id), "Sayım bulunamadı");
        Guard.state(count.status == StockCountStatus.DRAFT, "Sayım artık açık değil");
        return count;
    }

    private StockCountLine addLine(UUID countId, UUID variantId, UUID lotId, BigDecimal expected) {
        StockCountLine line = new StockCountLine();
        line.countId = countId;
        line.variantId = variantId;
        line.lotId = lotId;
        line.expectedQty = expected == null ? BigDecimal.ZERO : expected;
        db.add(line);
        return line;
    }
}
