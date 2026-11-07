package dev.enterprisepulse.sales;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.SalesOrder;
import dev.enterprisepulse.data.generated.SalesOrderLine;
import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.SalesOrderStatus;
import dev.enterprisepulse.data.generated.StockReservation;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.inventory.StockMovementService;
import dev.enterprisepulse.partners.PartnerLedgerService;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import dev.enterprisepulse.trade.TradeService;
import dev.enterprisepulse.trade.TradeService.LineInput;
import dev.enterprisepulse.trade.TradeService.PricedDocument;
import dev.enterprisepulse.trade.TradeService.PricedLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SalesOrderService {
    public record Save(UUID partnerId, UUID warehouseId, LocalDate orderDate, LocalDate deliveryDate, String customerRef, String currencyCode,
                        BigDecimal exchangeRate, String notes, List<LineInput> lines) {}

    public record LineDto(UUID id, int lineNo, UUID variantId, UUID productId, String sku, ProductType productType, boolean stocked, String description,
                           BigDecimal quantity, BigDecimal deliveredQty, BigDecimal invoicedQty, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal vatRate,
                           BigDecimal lineTotal, BigDecimal remainingQty, BigDecimal invoiceableQty) {}

    public record Detail(UUID id, String number, UUID partnerId, String partnerCode, String partnerTitle, UUID warehouseId, String warehouseName,
                          LocalDate orderDate, LocalDate deliveryDate, String customerRef, String currencyCode, BigDecimal exchangeRate, SalesOrderStatus status,
                          String notes, UUID quoteId, int deliveredPercent, boolean canDeliver, boolean canInvoice, boolean canCancel, boolean canEdit,
                          TradeService.TotalsDto totals, List<LineDto> lines) {}

    private final Db db;
    private final NumberingService numbering;
    private final TradeService trade;
    private final PartnerLedgerService partners;

    public SalesOrderService(Db db, NumberingService numbering, TradeService trade, PartnerLedgerService partners) {
        this.db = db;
        this.numbering = numbering;
        this.trade = trade;
        this.partners = partners;
    }

    public Detail create(Save body) {
        return get(createEntity(body, null).id);
    }

    public SalesOrder createEntity(Save body, UUID quoteId) {
        Prepared prepared = prepare(body);
        SalesOrder order = new SalesOrder();
        order.number = numbering.next("SALES_ORDER");
        order.quoteId = quoteId;
        order.createdById = CurrentUser.get().requireUserId();
        apply(order, prepared);
        db.add(order);
        for (PricedLine line : prepared.priced().lines()) db.add(line(order.id, line));
        db.flush();
        return order;
    }

    public Detail update(UUID id, Save body) {
        SalesOrder order = draft(id);
        Prepared prepared = prepare(body);
        apply(order, prepared);
        db.execute("delete from SalesOrderLine l where l.orderId = ?1", id);
        db.flush();
        for (PricedLine line : prepared.priced().lines()) db.add(line(order.id, line));
        return get(id);
    }

    public Detail approve(UUID id) {
        db.nativeRows("SELECT id FROM sales_orders WHERE id = ?1 FOR UPDATE", id);
        SalesOrder order = draft(id);
        List<SalesOrderLine> lines = db.list("select l from SalesOrderLine l where l.orderId = ?1", SalesOrderLine.class, id);
        for (SalesOrderLine line : lines) {
            ProductVariant variant = db.get(ProductVariant.class, line.variantId);
            Product product = trade.product(variant);
            if (product.type != ProductType.GOODS) continue;
            BigDecimal onHand = db.first("select b.quantity from StockBalance b where b.warehouseId = ?1 and b.variantId = ?2", BigDecimal.class, order.warehouseId, line.variantId).orElse(BigDecimal.ZERO);
            BigDecimal reserved = db.first("select coalesce(sum(r.quantity), 0) from StockReservation r where r.warehouseId = ?1 and r.variantId = ?2", BigDecimal.class, order.warehouseId, line.variantId).orElse(BigDecimal.ZERO);
            BigDecimal free = onHand.subtract(reserved);
            Guard.that(free.add(new BigDecimal("0.0005")).compareTo(line.quantity) >= 0, variant.sku + " için ayrılabilir stok " + StockMovementService.qty(free) + "; sipariş " + line.quantity);
            StockReservation reservation = new StockReservation();
            reservation.warehouseId = order.warehouseId;
            reservation.variantId = line.variantId;
            reservation.orderLineId = line.id;
            reservation.quantity = line.quantity;
            db.add(reservation);
        }
        BusinessPartner partner = db.get(BusinessPartner.class, order.partnerId);
        if (partner.creditLimit != null) {
            BigDecimal exposure = partners.balance(partner.id).add(DocumentMath.toBase(order.grandTotal, order.exchangeRate));
            Guard.that(exposure.compareTo(partner.creditLimit) <= 0, partner.title + " kredi limiti aşılıyor");
        }
        order.status = SalesOrderStatus.APPROVED;
        order.approvedById = CurrentUser.get().requireUserId();
        order.approvedAt = Instant.now();
        return get(id);
    }

    public Detail cancel(UUID id) {
        SalesOrder order = Guard.found(db.first("select o from SalesOrder o where o.id = ?1", SalesOrder.class, id), "Satış siparişi bulunamadı");
        Guard.state(order.status == SalesOrderStatus.DRAFT || order.status == SalesOrderStatus.APPROVED, "Sipariş bu durumda iptal edilemez");
        Guard.state(!db.exists("select d.id from DeliveryNote d where d.orderId = ?1", id), "İrsaliyesi olan sipariş iptal edilemez");
        Guard.state(!db.exists("select i.id from SalesInvoice i where i.orderId = ?1 and i.status <> ?2", id, InvoiceStatus.CANCELLED), "Faturası olan sipariş iptal edilemez");
        db.execute("delete from StockReservation r where r.orderLineId in (select l.id from SalesOrderLine l where l.orderId = ?1)", id);
        order.status = SalesOrderStatus.CANCELLED;
        return get(id);
    }

    public List<Detail> open() {
        List<SalesOrder> rows = db.list("select o from SalesOrder o where o.status in ?1 order by o.orderDate desc", SalesOrder.class,
                List.of(SalesOrderStatus.APPROVED, SalesOrderStatus.PARTIALLY_DELIVERED, SalesOrderStatus.DELIVERED));
        List<Detail> result = new ArrayList<>();
        for (SalesOrder row : rows) {
            Detail detail = get(row.id);
            if (detail.lines().stream().anyMatch(l -> l.deliveredQty().compareTo(l.quantity()) < 0 || l.invoicedQty().compareTo(l.quantity()) < 0)) result.add(detail);
        }
        return result;
    }

    public Detail get(UUID id) {
        SalesOrder order = Guard.found(db.first("select o from SalesOrder o where o.id = ?1", SalesOrder.class, id), "Satış siparişi bulunamadı");
        BusinessPartner partner = db.get(BusinessPartner.class, order.partnerId);
        Warehouse warehouse = db.get(Warehouse.class, order.warehouseId);
        List<LineDto> lines = new ArrayList<>();
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal delivered = BigDecimal.ZERO;
        for (SalesOrderLine line : db.list("select l from SalesOrderLine l where l.orderId = ?1 order by l.lineNo", SalesOrderLine.class, id)) {
            ProductVariant variant = db.get(ProductVariant.class, line.variantId);
            Product product = trade.product(variant);
            boolean stocked = product.type == ProductType.GOODS;
            BigDecimal remaining = line.quantity.subtract(line.deliveredQty).max(BigDecimal.ZERO);
            BigDecimal cap = stocked ? line.deliveredQty : line.quantity;
            lines.add(new LineDto(line.id, line.lineNo, line.variantId, variant.productId, variant.sku, product.type, stocked, line.description,
                    line.quantity, line.deliveredQty, line.invoicedQty, line.unitPrice, line.discountRate, line.vatRate, line.lineTotal, remaining,
                    cap.subtract(line.invoicedQty).max(BigDecimal.ZERO)));
            qty = qty.add(line.quantity);
            delivered = delivered.add(line.deliveredQty.min(line.quantity));
        }
        int percent = qty.signum() == 0 ? 0 : delivered.multiply(new BigDecimal("100")).divide(qty, 0, RoundingMode.DOWN).intValue();
        boolean live = order.status == SalesOrderStatus.APPROVED || order.status == SalesOrderStatus.PARTIALLY_DELIVERED;
        return new Detail(order.id, order.number, order.partnerId, partner.code, partner.title, order.warehouseId, warehouse.name, order.orderDate,
                order.deliveryDate, order.customerRef, order.currencyCode, order.exchangeRate, order.status, order.notes, order.quoteId, percent,
                live && lines.stream().anyMatch(l -> l.stocked() && l.remainingQty().signum() > 0),
                (live || order.status == SalesOrderStatus.DELIVERED) && lines.stream().anyMatch(l -> l.invoiceableQty().signum() > 0),
                order.status == SalesOrderStatus.DRAFT || order.status == SalesOrderStatus.APPROVED, order.status == SalesOrderStatus.DRAFT,
                TradeService.totals(new DocumentMath.DocumentTotals(order.subtotal, order.discountTotal, order.vatTotal, order.grandTotal), order.exchangeRate), lines);
    }

    public void refreshStatus(UUID id) {
        SalesOrder order = db.get(SalesOrder.class, id);
        if (order.status == SalesOrderStatus.CANCELLED || order.status == SalesOrderStatus.DRAFT) return;
        List<SalesOrderLine> lines = db.list("select l from SalesOrderLine l where l.orderId = ?1", SalesOrderLine.class, id);
        boolean all = lines.stream().allMatch(l -> l.deliveredQty.compareTo(l.quantity) >= 0);
        boolean any = lines.stream().anyMatch(l -> l.deliveredQty.signum() > 0);
        order.status = all ? SalesOrderStatus.DELIVERED : any ? SalesOrderStatus.PARTIALLY_DELIVERED : SalesOrderStatus.APPROVED;
    }

    private SalesOrder draft(UUID id) {
        SalesOrder order = Guard.found(db.first("select o from SalesOrder o where o.id = ?1", SalesOrder.class, id), "Satış siparişi bulunamadı");
        Guard.state(order.status == SalesOrderStatus.DRAFT, "Yalnızca taslak sipariş düzenlenir");
        return order;
    }

    private Prepared prepare(Save body) {
        BusinessPartner partner = partners.forDocument(body.partnerId(), PartnerLedgerService.CUSTOMERS);
        Warehouse warehouse = Guard.found(db.first("select w from Warehouse w where w.id = ?1 and w.isActive = true", Warehouse.class, body.warehouseId()), "Depo bulunamadı");
        LocalDate date = body.orderDate() == null ? LocalDate.now() : body.orderDate();
        var currency = trade.resolveCurrency(body.currencyCode() == null ? partner.currencyCode : body.currencyCode(), body.exchangeRate(), date);
        List<LineInput> inputs = body.lines() == null ? List.of() : body.lines();
        return new Prepared(partner, warehouse, currency.currencyCode(), currency.exchangeRate(), date, body, trade.priceLines(inputs, trade.loadVariants(inputs), true));
    }

    private static void apply(SalesOrder order, Prepared p) {
        order.partnerId = p.partner().id;
        order.warehouseId = p.warehouse().id;
        order.orderDate = p.date();
        order.deliveryDate = p.body().deliveryDate();
        order.customerRef = blank(p.body().customerRef());
        order.currencyCode = p.currency();
        order.exchangeRate = p.rate();
        order.notes = blank(p.body().notes());
        order.subtotal = p.priced().totals().subtotal();
        order.discountTotal = p.priced().totals().discountTotal();
        order.vatTotal = p.priced().totals().vatTotal();
        order.grandTotal = p.priced().totals().grandTotal();
    }

    private static SalesOrderLine line(UUID orderId, PricedLine line) {
        SalesOrderLine row = new SalesOrderLine();
        row.orderId = orderId;
        row.lineNo = line.lineNo();
        row.variantId = line.variantId();
        row.description = line.description();
        row.quantity = line.quantity();
        row.unitPrice = line.unitPrice();
        row.discountRate = line.discountRate();
        row.vatRate = line.vatRate();
        row.lineTotal = line.lineTotal();
        return row;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Prepared(BusinessPartner partner, Warehouse warehouse, String currency, BigDecimal rate, LocalDate date, Save body, PricedDocument priced) {}
}
