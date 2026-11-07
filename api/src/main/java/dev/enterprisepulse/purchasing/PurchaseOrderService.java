package dev.enterprisepulse.purchasing;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.PurchaseOrder;
import dev.enterprisepulse.data.generated.PurchaseOrderLine;
import dev.enterprisepulse.data.generated.PurchaseOrderStatus;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.partners.PartnerLedgerService;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import dev.enterprisepulse.trade.TradeService;
import dev.enterprisepulse.trade.TradeService.LineInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PurchaseOrderService {
    public record Save(UUID partnerId, UUID warehouseId, LocalDate orderDate, LocalDate expectedDate, String currencyCode, BigDecimal exchangeRate,
                        String notes, List<LineInput> lines) {}

    public record LineDto(UUID id, int lineNo, UUID variantId, UUID productId, String sku, String productName, String variantName, String unitCode,
                           boolean stocked, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal vatRate,
                           BigDecimal lineTotal, BigDecimal receivedQty, BigDecimal invoicedQty, BigDecimal remainingQty, BigDecimal invoiceableQty) {}

    public record Detail(UUID id, String number, UUID partnerId, String partnerCode, String partnerTitle, UUID warehouseId, String warehouseName,
                          LocalDate orderDate, LocalDate expectedDate, String currencyCode, BigDecimal exchangeRate, PurchaseOrderStatus status,
                          String notes, UUID requestId, UUID quoteId, UUID createdById, UUID approvedById, Instant approvedAt, int receivedPercent,
                          boolean canReceive, boolean canInvoice, boolean canCancel, TradeService.TotalsDto totals, List<LineDto> lines) {}

    private final Db db;
    private final NumberingService numbering;
    private final TradeService trade;
    private final PartnerLedgerService partners;

    public PurchaseOrderService(Db db, NumberingService numbering, TradeService trade, PartnerLedgerService partners) {
        this.db = db;
        this.numbering = numbering;
        this.trade = trade;
        this.partners = partners;
    }

    public Detail create(Save body) {
        return detail(createEntity(body, null, null).id);
    }

    public PurchaseOrder createEntity(Save body, UUID requestId, UUID quoteId) {
        var prepared = prepare(body);
        PurchaseOrder order = new PurchaseOrder();
        order.number = numbering.next("PURCHASE_ORDER");
        order.requestId = requestId;
        order.quoteId = quoteId;
        order.createdById = CurrentUser.get().requireUserId();
        apply(order, prepared);
        db.add(order);
        writeLines(order, prepared);
        db.flush();
        return order;
    }

    public Detail update(UUID id, Save body) {
        PurchaseOrder order = draft(id);
        var prepared = prepare(body);
        apply(order, prepared);
        db.execute("delete from PurchaseOrderLine l where l.orderId = ?1", order.id);
        db.flush();
        writeLines(order, prepared);
        db.flush();
        return detail(id);
    }

    public Detail approve(UUID id) {
        PurchaseOrder order = draft(id);
        order.status = PurchaseOrderStatus.APPROVED;
        order.approvedById = CurrentUser.get().requireUserId();
        order.approvedAt = Instant.now();
        db.flush();
        return detail(id);
    }

    public Detail cancel(UUID id) {
        PurchaseOrder order = Guard.found(db.first("select o from PurchaseOrder o where o.id = ?1", PurchaseOrder.class, id), "Sipariş bulunamadı");
        Guard.state(order.status == PurchaseOrderStatus.DRAFT || order.status == PurchaseOrderStatus.APPROVED, "Sipariş bu durumda iptal edilemez");
        Guard.state(!db.exists("select r.id from GoodsReceipt r where r.orderId = ?1", id), "Mal kabulü olan sipariş iptal edilemez");
        Guard.state(!db.exists("select i.id from PurchaseInvoice i where i.orderId = ?1 and i.status <> dev.enterprisepulse.data.generated.InvoiceStatus.CANCELLED", id),
                "Faturası olan sipariş iptal edilemez");
        order.status = PurchaseOrderStatus.CANCELLED;
        db.flush();
        return detail(id);
    }

    public List<Detail> open() {
        List<Detail> result = new ArrayList<>();
        for (PurchaseOrder order : db.list("select o from PurchaseOrder o where o.status in (dev.enterprisepulse.data.generated.PurchaseOrderStatus.APPROVED, dev.enterprisepulse.data.generated.PurchaseOrderStatus.PARTIALLY_RECEIVED, dev.enterprisepulse.data.generated.PurchaseOrderStatus.RECEIVED)", PurchaseOrder.class)) {
            Detail d = detail(order.id);
            if (d.lines().stream().anyMatch(l -> l.receivedQty().compareTo(l.quantity()) < 0 || l.invoicedQty().compareTo(l.quantity()) < 0)) result.add(d);
        }
        return result;
    }

    public Detail detail(UUID id) {
        PurchaseOrder o = Guard.found(db.first("select o from PurchaseOrder o where o.id = ?1", PurchaseOrder.class, id), "Sipariş bulunamadı");
        BusinessPartner partner = db.get(BusinessPartner.class, o.partnerId);
        Warehouse warehouse = db.get(Warehouse.class, o.warehouseId);
        List<LineDto> lines = new ArrayList<>();
        BigDecimal qty = BigDecimal.ZERO, received = BigDecimal.ZERO;
        for (PurchaseOrderLine l : db.list("select l from PurchaseOrderLine l where l.orderId = ?1 order by l.lineNo", PurchaseOrderLine.class, o.id)) {
            ProductVariant v = db.get(ProductVariant.class, l.variantId);
            var product = db.get(dev.enterprisepulse.data.generated.Product.class, v.productId);
            boolean stocked = trade.isStocked(product);
            BigDecimal remaining = l.quantity.subtract(l.receivedQty).max(BigDecimal.ZERO);
            BigDecimal cap = stocked ? l.receivedQty : l.quantity;
            lines.add(new LineDto(l.id, l.lineNo, v.id, product.id, v.sku, product.name, v.name, null, stocked, l.description, l.quantity, l.unitPrice,
                    l.discountRate, l.vatRate, l.lineTotal, l.receivedQty, l.invoicedQty, remaining, cap.subtract(l.invoicedQty).max(BigDecimal.ZERO)));
            qty = qty.add(l.quantity);
            received = received.add(l.receivedQty.min(l.quantity));
        }
        int percent = qty.signum() == 0 ? 0 : received.multiply(new BigDecimal("100")).divide(qty, 0, RoundingMode.DOWN).intValue();
        boolean live = o.status == PurchaseOrderStatus.APPROVED || o.status == PurchaseOrderStatus.PARTIALLY_RECEIVED;
        var totals = new TradeService.TotalsDto(o.subtotal, o.discountTotal, o.vatTotal, o.grandTotal, DocumentMath.toBase(o.grandTotal, o.exchangeRate));
        return new Detail(o.id, o.number, partner.id, partner.code, partner.title, warehouse.id, warehouse.name, o.orderDate, o.expectedDate, o.currencyCode,
                o.exchangeRate, o.status, o.notes, o.requestId, o.quoteId, o.createdById, o.approvedById, o.approvedAt, percent,
                live && lines.stream().anyMatch(l -> l.stocked() && l.remainingQty().signum() > 0),
                (live || o.status == PurchaseOrderStatus.RECEIVED) && lines.stream().anyMatch(l -> l.invoiceableQty().signum() > 0),
                o.status == PurchaseOrderStatus.DRAFT || o.status == PurchaseOrderStatus.APPROVED, totals, lines);
    }

    public void refreshStatus(UUID id) {
        PurchaseOrder order = db.get(PurchaseOrder.class, id);
        if (order.status == PurchaseOrderStatus.CANCELLED || order.status == PurchaseOrderStatus.DRAFT) return;
        List<PurchaseOrderLine> lines = db.list("select l from PurchaseOrderLine l where l.orderId = ?1", PurchaseOrderLine.class, id);
        boolean all = lines.stream().allMatch(l -> l.receivedQty.compareTo(l.quantity) >= 0);
        boolean any = lines.stream().anyMatch(l -> l.receivedQty.signum() > 0);
        order.status = all ? PurchaseOrderStatus.RECEIVED : any ? PurchaseOrderStatus.PARTIALLY_RECEIVED : PurchaseOrderStatus.APPROVED;
    }

    private record Prepared(BusinessPartner partner, Warehouse warehouse, TradeService.CurrencyChoice currency, LocalDate date, LocalDate expected,
                             String notes, TradeService.PricedDocument priced) {}

    private Prepared prepare(Save body) {
        BusinessPartner partner = partners.forDocument(body.partnerId(), PartnerLedgerService.SUPPLIERS);
        Warehouse warehouse = db.get(Warehouse.class, body.warehouseId());
        Guard.that(warehouse.isActive, warehouse.name + " deposu pasif");
        LocalDate date = body.orderDate() == null ? LocalDate.now() : body.orderDate();
        var currency = trade.resolveCurrency(body.currencyCode(), body.exchangeRate(), date);
        Map<UUID, ProductVariant> variants = trade.loadVariants(body.lines());
        return new Prepared(partner, warehouse, currency, date, body.expectedDate(), body.notes(), trade.priceLines(body.lines(), variants, true));
    }

    private void apply(PurchaseOrder order, Prepared p) {
        order.partnerId = p.partner.id;
        order.warehouseId = p.warehouse.id;
        order.orderDate = p.date;
        order.expectedDate = p.expected;
        order.currencyCode = p.currency.currencyCode();
        order.exchangeRate = p.currency.exchangeRate();
        order.notes = p.notes;
        order.subtotal = p.priced.totals().subtotal();
        order.discountTotal = p.priced.totals().discountTotal();
        order.vatTotal = p.priced.totals().vatTotal();
        order.grandTotal = p.priced.totals().grandTotal();
    }

    private void writeLines(PurchaseOrder order, Prepared p) {
        for (var line : p.priced.lines()) {
            PurchaseOrderLine row = new PurchaseOrderLine();
            row.orderId = order.id;
            row.lineNo = line.lineNo();
            row.variantId = line.variantId();
            row.description = line.description();
            row.quantity = line.quantity();
            row.unitPrice = line.unitPrice();
            row.discountRate = line.discountRate();
            row.vatRate = line.vatRate();
            row.lineTotal = line.lineTotal();
            db.add(row);
        }
    }

    private PurchaseOrder draft(UUID id) {
        PurchaseOrder order = Guard.found(db.first("select o from PurchaseOrder o where o.id = ?1", PurchaseOrder.class, id), "Sipariş bulunamadı");
        Guard.state(order.status == PurchaseOrderStatus.DRAFT, "Yalnızca taslak sipariş düzenlenir");
        return order;
    }
}
