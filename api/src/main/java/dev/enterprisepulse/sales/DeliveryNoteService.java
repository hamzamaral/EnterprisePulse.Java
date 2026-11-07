package dev.enterprisepulse.sales;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.DeliveryNote;
import dev.enterprisepulse.data.generated.DeliveryNoteLine;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.SalesOrder;
import dev.enterprisepulse.data.generated.SalesOrderLine;
import dev.enterprisepulse.data.generated.SalesOrderStatus;
import dev.enterprisepulse.data.generated.SerialNumber;
import dev.enterprisepulse.data.generated.StockMovement;
import dev.enterprisepulse.data.generated.StockMovementType;
import dev.enterprisepulse.data.generated.StockReservation;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.inventory.StockMovementService;
import dev.enterprisepulse.platform.NumberingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeliveryNoteService {
    public record LineInput(UUID orderLineId, BigDecimal quantity, String lotNo, List<UUID> serialIds) {}

    public record Create(UUID orderId, LocalDate deliveryDate, String shippingAddress, String carrier, String notes, List<LineInput> lines) {}

    public record LineDto(UUID id, UUID orderLineId, String sku, String description, BigDecimal quantity, String lotNo, String serialNos) {}

    public record Detail(UUID id, String number, UUID orderId, String orderNumber, UUID partnerId, String partnerTitle, UUID warehouseId, String warehouseName,
                          LocalDate deliveryDate, String shippingAddress, String carrier, String notes, UUID stockMovementId, String stockMovementNumber, List<LineDto> lines) {}

    private final Db db;
    private final NumberingService numbering;
    private final StockMovementService stock;
    private final SalesOrderService orders;

    public DeliveryNoteService(Db db, NumberingService numbering, StockMovementService stock, SalesOrderService orders) {
        this.db = db;
        this.numbering = numbering;
        this.stock = stock;
        this.orders = orders;
    }

    public Detail create(Create body) {
        Guard.that(body.lines() != null && !body.lines().isEmpty(), "İrsaliye satırı gerekli");
        db.nativeRows("SELECT id FROM sales_orders WHERE id = ?1 FOR UPDATE", body.orderId());
        SalesOrder order = Guard.found(db.first("select o from SalesOrder o where o.id = ?1", SalesOrder.class, body.orderId()), "Satış siparişi bulunamadı");
        Guard.state(order.status == SalesOrderStatus.APPROVED || order.status == SalesOrderStatus.PARTIALLY_DELIVERED, "Yalnızca onaylı sipariş sevk edilir");
        List<SalesOrderLine> orderLines = db.list("select l from SalesOrderLine l where l.orderId = ?1", SalesOrderLine.class, order.id);
        List<StockMovementService.LineInput> movement = new ArrayList<>();
        List<Chosen> chosen = new ArrayList<>();
        for (LineInput input : body.lines()) {
            SalesOrderLine line = orderLines.stream().filter(l -> l.id.equals(input.orderLineId())).findFirst().orElse(null);
            Guard.that(line != null, "Satır bu siparişte yok");
            ProductVariant variant = db.get(ProductVariant.class, line.variantId);
            Product product = db.get(Product.class, variant.productId);
            Guard.that(product.type == ProductType.GOODS, variant.sku + " hizmet satırı irsaliyeye yazılmaz");
            BigDecimal open = line.quantity.subtract(line.deliveredQty);
            Guard.that(input.quantity() != null && input.quantity().signum() > 0 && input.quantity().compareTo(open.add(new BigDecimal("0.0005"))) <= 0,
                    variant.sku + " için en fazla " + StockMovementService.qty(open) + " sevk edilebilir");
            movement.add(new StockMovementService.LineInput(line.variantId, input.quantity(), null, null, input.lotNo(), null, null, input.serialIds()));
            chosen.add(new Chosen(line, input, variant.sku));
        }
        var posted = stock.post(new StockMovementService.CreateRequest(StockMovementType.ISSUE, body.deliveryDate(), order.warehouseId, null, null, body.notes(), movement), order.id);
        DeliveryNote note = new DeliveryNote();
        note.number = numbering.next("DELIVERY_NOTE");
        note.orderId = order.id;
        note.partnerId = order.partnerId;
        note.warehouseId = order.warehouseId;
        note.deliveryDate = body.deliveryDate();
        note.shippingAddress = blank(body.shippingAddress());
        note.carrier = blank(body.carrier());
        note.notes = blank(body.notes());
        note.stockMovementId = posted.movementId();
        note.createdById = CurrentUser.get().requireUserId();
        db.add(note);
        for (Chosen row : chosen) {
            DeliveryNoteLine line = new DeliveryNoteLine();
            line.deliveryId = note.id;
            line.orderLineId = row.line().id;
            line.variantId = row.line().variantId;
            line.quantity = row.input().quantity();
            line.lotNo = blank(row.input().lotNo());
            line.serialNos = serialText(row.input().serialIds());
            db.add(line);
            row.line().deliveredQty = row.line().deliveredQty.add(row.input().quantity());
            StockReservation reservation = db.first("select r from StockReservation r where r.orderLineId = ?1", StockReservation.class, row.line().id).orElse(null);
            if (reservation != null) {
                reservation.quantity = reservation.quantity.subtract(row.input().quantity());
                if (reservation.quantity.compareTo(new BigDecimal("0.0005")) <= 0) db.remove(reservation);
            }
        }
        orders.refreshStatus(order.id);
        stock.notifyLowStock(posted.lowStock());
        return get(note.id);
    }

    public Detail get(UUID id) {
        DeliveryNote note = Guard.found(db.first("select d from DeliveryNote d where d.id = ?1", DeliveryNote.class, id), "İrsaliye bulunamadı");
        SalesOrder order = db.get(SalesOrder.class, note.orderId);
        BusinessPartner partner = db.get(BusinessPartner.class, note.partnerId);
        Warehouse warehouse = db.get(Warehouse.class, note.warehouseId);
        StockMovement movement = note.stockMovementId == null ? null : db.find(StockMovement.class, note.stockMovementId);
        List<LineDto> lines = new ArrayList<>();
        for (DeliveryNoteLine line : db.list("select l from DeliveryNoteLine l where l.deliveryId = ?1", DeliveryNoteLine.class, id)) {
            ProductVariant variant = db.get(ProductVariant.class, line.variantId);
            SalesOrderLine orderLine = db.get(SalesOrderLine.class, line.orderLineId);
            lines.add(new LineDto(line.id, line.orderLineId, variant.sku, orderLine.description, line.quantity, line.lotNo, line.serialNos));
        }
        return new Detail(note.id, note.number, note.orderId, order.number, note.partnerId, partner.title, note.warehouseId, warehouse.name, note.deliveryDate,
                note.shippingAddress, note.carrier, note.notes, note.stockMovementId, movement == null ? null : movement.number, lines);
    }

    private String serialText(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "";
        List<String> nos = new ArrayList<>();
        for (SerialNumber serial : db.list("select s from SerialNumber s where s.id in ?1", SerialNumber.class, ids)) nos.add(serial.serialNo);
        return String.join(", ", nos);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Chosen(SalesOrderLine line, LineInput input, String sku) {}
}
