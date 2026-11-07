package dev.enterprisepulse.purchasing;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.GoodsReceipt;
import dev.enterprisepulse.data.generated.GoodsReceiptLine;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.PurchaseOrder;
import dev.enterprisepulse.data.generated.PurchaseOrderLine;
import dev.enterprisepulse.data.generated.PurchaseOrderStatus;
import dev.enterprisepulse.data.generated.StockMovement;
import dev.enterprisepulse.data.generated.StockMovementType;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.inventory.StockMovementService;
import dev.enterprisepulse.inventory.StockMovementService.LowStockItem;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoodsReceiptService {
    public record LineInput(UUID orderLineId, BigDecimal quantity, String lotNo, LocalDate expiryDate, List<String> serialNos) {}

    public record Create(UUID orderId, UUID warehouseId, LocalDate receiptDate, String deliveryNoteNo, String notes, List<LineInput> lines) {}

    public record LineDto(UUID id, int orderLineNo, UUID orderLineId, UUID variantId, String sku, String description, BigDecimal quantity,
                           String lotNo, LocalDate expiryDate, String serialNos) {}

    public record Detail(UUID id, String number, UUID orderId, String orderNumber, UUID partnerId, String partnerTitle, UUID warehouseId, String warehouseName,
                          LocalDate receiptDate, String deliveryNoteNo, String notes, UUID stockMovementId, String stockMovementNumber, String createdByName,
                          Instant createdAt, List<LineDto> lines) {}

    private final Db db;
    private final NumberingService numbering;
    private final StockMovementService stock;

    public GoodsReceiptService(Db db, NumberingService numbering, StockMovementService stock) {
        this.db = db;
        this.numbering = numbering;
        this.stock = stock;
    }

    public Detail create(Create body) {
        Guard.that(body.lines() != null && !body.lines().isEmpty(), "En az bir satır gerekli");
        db.nativeRows("SELECT id FROM purchase_orders WHERE id = ?1 FOR UPDATE", body.orderId());
        PurchaseOrder order = Guard.found(db.first("select o from PurchaseOrder o where o.id = ?1", PurchaseOrder.class, body.orderId()), "Satın alma siparişi bulunamadı");
        Guard.state(order.status == PurchaseOrderStatus.APPROVED || order.status == PurchaseOrderStatus.PARTIALLY_RECEIVED, "Yalnızca onaylı sipariş teslim alınabilir");
        UUID warehouseId = body.warehouseId() == null ? order.warehouseId : body.warehouseId();
        Warehouse warehouse = db.get(Warehouse.class, warehouseId);
        Guard.that(warehouse.isActive, "Depo bulunamadı");
        Map<UUID, PurchaseOrderLine> orderLines = new HashMap<>();
        for (PurchaseOrderLine line : db.list("select l from PurchaseOrderLine l where l.orderId = ?1", PurchaseOrderLine.class, order.id)) orderLines.put(line.id, line);
        Map<UUID, BigDecimal> receiving = new HashMap<>();
        List<StockMovementService.LineInput> stockLines = new ArrayList<>();
        for (LineInput input : body.lines()) {
            PurchaseOrderLine line = orderLines.get(input.orderLineId());
            Guard.that(line != null, "Mal kabul satırı başka bir siparişe ait");
            Guard.that(input.quantity() != null && input.quantity().signum() > 0, "Satır " + line.lineNo + ": miktar sıfırdan büyük olmalı");
            BigDecimal total = receiving.getOrDefault(line.id, BigDecimal.ZERO).add(input.quantity());
            BigDecimal remaining = line.quantity.subtract(line.receivedQty);
            Guard.that(total.compareTo(remaining.add(new BigDecimal("0.0005"))) <= 0, "Satır " + line.lineNo + ": en fazla " + qty(remaining) + " teslim alınabilir");
            receiving.put(line.id, total);
            ProductVariant variant = db.get(ProductVariant.class, line.variantId);
            Product product = db.get(Product.class, variant.productId);
            if (product.type == ProductType.GOODS) {
                stockLines.add(new StockMovementService.LineInput(line.variantId, input.quantity(),
                        DocumentMath.netUnitCostBase(line.unitPrice, line.discountRate, order.exchangeRate), null, input.lotNo(), input.expiryDate(),
                        input.serialNos(), null));
            }
        }
        List<LowStockItem> low = List.of();
        UUID movementId = null;
        String note = body.deliveryNoteNo() == null || body.deliveryNoteNo().isBlank() ? null : body.deliveryNoteNo().trim();
        if (!stockLines.isEmpty()) {
            var posted = stock.post(new StockMovementService.CreateRequest(StockMovementType.RECEIPT, body.receiptDate(), null, warehouseId,
                    note == null ? order.number : order.number + " / " + note, body.notes(), stockLines), null);
            movementId = posted.movementId();
            low = posted.lowStock();
        }
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.number = numbering.next("GOODS_RECEIPT");
        receipt.orderId = order.id;
        receipt.partnerId = order.partnerId;
        receipt.warehouseId = warehouseId;
        receipt.receiptDate = body.receiptDate() == null ? LocalDate.now() : body.receiptDate();
        receipt.deliveryNoteNo = note;
        receipt.notes = body.notes() == null || body.notes().isBlank() ? null : body.notes().trim();
        receipt.stockMovementId = movementId;
        receipt.createdById = CurrentUser.get().requireUserId();
        db.add(receipt);
        for (LineInput input : body.lines()) {
            PurchaseOrderLine line = orderLines.get(input.orderLineId());
            GoodsReceiptLine row = new GoodsReceiptLine();
            row.receiptId = receipt.id;
            row.orderLineId = line.id;
            row.variantId = line.variantId;
            row.quantity = input.quantity().setScale(3, RoundingMode.HALF_UP);
            row.lotNo = input.lotNo() == null || input.lotNo().isBlank() ? null : input.lotNo().trim();
            row.expiryDate = input.expiryDate();
            row.serialNos = input.serialNos() == null || input.serialNos().isEmpty() ? "" : String.join(", ", input.serialNos().stream().map(String::trim).toList());
            db.add(row);
        }
        for (var entry : receiving.entrySet()) orderLines.get(entry.getKey()).receivedQty = orderLines.get(entry.getKey()).receivedQty.add(entry.getValue());
        boolean complete = orderLines.values().stream().allMatch(l -> l.receivedQty.compareTo(l.quantity) >= 0);
        order.status = complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
        db.flush();
        stock.notifyLowStock(low);
        return detail(receipt.id);
    }

    public Detail detail(UUID id) {
        GoodsReceipt r = Guard.found(db.first("select r from GoodsReceipt r where r.id = ?1", GoodsReceipt.class, id), "Mal kabul bulunamadı");
        PurchaseOrder order = db.get(PurchaseOrder.class, r.orderId);
        BusinessPartner partner = db.get(BusinessPartner.class, r.partnerId);
        Warehouse warehouse = db.get(Warehouse.class, r.warehouseId);
        User user = db.find(User.class, r.createdById);
        StockMovement movement = r.stockMovementId == null ? null : db.find(StockMovement.class, r.stockMovementId);
        List<LineDto> lines = new ArrayList<>();
        for (GoodsReceiptLine l : db.list("select l from GoodsReceiptLine l where l.receiptId = ?1", GoodsReceiptLine.class, r.id)) {
            PurchaseOrderLine orderLine = db.get(PurchaseOrderLine.class, l.orderLineId);
            ProductVariant variant = db.get(ProductVariant.class, l.variantId);
            lines.add(new LineDto(l.id, orderLine.lineNo, l.orderLineId, l.variantId, variant.sku, orderLine.description, l.quantity, l.lotNo, l.expiryDate,
                    l.serialNos == null || l.serialNos.isBlank() ? null : l.serialNos));
        }
        lines.sort(java.util.Comparator.comparingInt(LineDto::orderLineNo));
        return new Detail(r.id, r.number, order.id, order.number, partner.id, partner.title, warehouse.id, warehouse.name, r.receiptDate, r.deliveryNoteNo,
                r.notes, r.stockMovementId, movement == null ? null : movement.number, user == null ? "" : user.firstName + " " + user.lastName, r.createdAt, lines);
    }

    private static String qty(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
