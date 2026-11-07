package dev.enterprisepulse.sales;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.LedgerDocumentType;
import dev.enterprisepulse.data.generated.PaymentStatus;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.SalesInvoice;
import dev.enterprisepulse.data.generated.SalesInvoiceLine;
import dev.enterprisepulse.data.generated.SalesOrder;
import dev.enterprisepulse.data.generated.SalesOrderLine;
import dev.enterprisepulse.data.generated.SalesOrderStatus;
import dev.enterprisepulse.inventory.StockMovementService;
import dev.enterprisepulse.partners.PartnerLedgerService;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import dev.enterprisepulse.trade.TradeService;
import dev.enterprisepulse.trade.TradeService.LineInput;
import dev.enterprisepulse.trade.TradeService.PricedDocument;
import java.math.BigDecimal;
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
public class SalesInvoiceService {
    public record Create(UUID partnerId, UUID orderId, LocalDate invoiceDate, LocalDate dueDate, String currencyCode, BigDecimal exchangeRate, String notes,
                          List<LineInput> lines) {}

    public record LineDto(UUID id, int lineNo, UUID variantId, String sku, String description, BigDecimal quantity, BigDecimal returnedQty, BigDecimal unitPrice,
                           BigDecimal discountRate, BigDecimal vatRate, BigDecimal lineTotal) {}

    public record Detail(UUID id, String number, UUID partnerId, String partnerCode, String partnerTitle, UUID orderId, String orderNumber, LocalDate invoiceDate,
                          LocalDate dueDate, String currencyCode, BigDecimal exchangeRate, InvoiceStatus status, PaymentStatus paymentStatus, BigDecimal paidAmount,
                          BigDecimal openAmount, boolean isOverdue, String notes, boolean canCancel, TradeService.TotalsDto totals, List<LineDto> lines) {}

    private final Db db;
    private final NumberingService numbering;
    private final TradeService trade;
    private final PartnerLedgerService ledger;

    public SalesInvoiceService(Db db, NumberingService numbering, TradeService trade, PartnerLedgerService ledger) {
        this.db = db;
        this.numbering = numbering;
        this.trade = trade;
        this.ledger = ledger;
    }

    public Detail create(Create body) {
        Guard.that(body.lines() != null && !body.lines().isEmpty(), "Fatura satırı gerekli");
        SalesOrder order = null;
        Map<UUID, SalesOrderLine> orderLines = new HashMap<>();
        if (body.orderId() != null) {
            db.nativeRows("SELECT id FROM sales_orders WHERE id = ?1 FOR UPDATE", body.orderId());
            order = Guard.found(db.first("select o from SalesOrder o where o.id = ?1", SalesOrder.class, body.orderId()), "Satış siparişi bulunamadı");
            Guard.state(order.status == SalesOrderStatus.APPROVED || order.status == SalesOrderStatus.PARTIALLY_DELIVERED || order.status == SalesOrderStatus.DELIVERED, "Sipariş faturalanamaz");
            Guard.that(order.partnerId.equals(body.partnerId()), "Fatura carisi sipariş carisiyle aynı olmalı");
            for (SalesOrderLine line : db.list("select l from SalesOrderLine l where l.orderId = ?1", SalesOrderLine.class, order.id)) orderLines.put(line.id, line);
        }
        List<LineInput> inputs = new ArrayList<>();
        Map<UUID, BigDecimal> invoicing = new HashMap<>();
        for (int i = 0; i < body.lines().size(); i++) {
            LineInput input = body.lines().get(i);
            if (input.orderLineId() == null) {
                inputs.add(input);
                continue;
            }
            SalesOrderLine line = orderLines.get(input.orderLineId());
            Guard.that(line != null, "Satır " + (i + 1) + ": sipariş satırı bu siparişte yok");
            Product product = db.get(Product.class, db.get(ProductVariant.class, line.variantId).productId);
            BigDecimal cap = product.type == ProductType.GOODS ? line.deliveredQty : line.quantity;
            BigDecimal open = cap.subtract(line.invoicedQty).max(BigDecimal.ZERO);
            BigDecimal total = invoicing.getOrDefault(line.id, BigDecimal.ZERO).add(input.quantity());
            Guard.that(total.compareTo(open.add(new BigDecimal("0.0005"))) <= 0, "Satır " + (i + 1) + ": en fazla " + StockMovementService.qty(open) + " faturalanabilir");
            invoicing.put(line.id, total);
            inputs.add(new LineInput(line.variantId, input.description(), input.quantity(), input.unitPrice(), input.discountRate(), input.vatRate(), null, line.id));
        }
        BusinessPartner partner = ledger.forDocument(body.partnerId(), PartnerLedgerService.CUSTOMERS);
        LocalDate date = body.invoiceDate() == null ? LocalDate.now() : body.invoiceDate();
        LocalDate due = body.dueDate() == null ? date.plusDays(partner.paymentTermDays) : body.dueDate();
        Guard.that(!due.isBefore(date), "Vade fatura tarihinden önce olamaz");
        var currency = trade.resolveCurrency(body.currencyCode() != null ? body.currencyCode() : order != null ? order.currencyCode : partner.currencyCode, body.exchangeRate(), date);
        PricedDocument priced = trade.priceLines(inputs, trade.loadVariants(inputs), false);
        SalesInvoice invoice = new SalesInvoice();
        invoice.number = numbering.next("SALES_INVOICE");
        invoice.partnerId = partner.id;
        invoice.orderId = order == null ? null : order.id;
        invoice.invoiceDate = date;
        invoice.dueDate = due;
        invoice.currencyCode = currency.currencyCode();
        invoice.exchangeRate = currency.exchangeRate();
        invoice.notes = blank(body.notes());
        invoice.subtotal = priced.totals().subtotal();
        invoice.discountTotal = priced.totals().discountTotal();
        invoice.vatTotal = priced.totals().vatTotal();
        invoice.grandTotal = priced.totals().grandTotal();
        invoice.createdById = CurrentUser.get().requireUserId();
        db.add(invoice);
        for (var line : priced.lines()) {
            SalesInvoiceLine row = new SalesInvoiceLine();
            row.invoiceId = invoice.id;
            row.lineNo = line.lineNo();
            row.orderLineId = line.input().orderLineId();
            row.variantId = line.variantId();
            row.description = line.description();
            row.quantity = line.quantity();
            row.unitPrice = line.unitPrice();
            row.discountRate = line.discountRate();
            row.vatRate = line.vatRate();
            row.lineTotal = line.lineTotal();
            db.add(row);
        }
        invoicing.forEach((lineId, qty) -> orderLines.get(lineId).invoicedQty = orderLines.get(lineId).invoicedQty.add(qty));
        db.flush();
        if (priced.totals().grandTotal().signum() > 0) {
            ledger.post(new PartnerLedgerService.Posting(partner.id, date, due, LedgerDocumentType.SALES_INVOICE, invoice.id, invoice.number,
                    "Satış faturası " + invoice.number, priced.totals().grandTotal(), BigDecimal.ZERO, currency.currencyCode(), currency.exchangeRate()));
        }
        return get(invoice.id);
    }

    public Detail cancel(UUID id, String reason) {
        db.nativeRows("SELECT id FROM sales_invoices WHERE id = ?1 FOR UPDATE", id);
        SalesInvoice invoice = Guard.found(db.first("select i from SalesInvoice i where i.id = ?1", SalesInvoice.class, id), "Satış faturası bulunamadı");
        Guard.state(invoice.status == InvoiceStatus.ISSUED, "Fatura zaten iptal edilmiş");
        Guard.state(invoice.paidAmount.signum() == 0, "Faturanın tahsilatı veya iadesi var; önce onu kaldırın");
        for (SalesInvoiceLine line : db.list("select l from SalesInvoiceLine l where l.invoiceId = ?1", SalesInvoiceLine.class, id)) {
            if (line.orderLineId == null) continue;
            SalesOrderLine orderLine = db.get(SalesOrderLine.class, line.orderLineId);
            orderLine.invoicedQty = orderLine.invoicedQty.subtract(line.quantity);
        }
        String text = reason == null || reason.isBlank() ? null : reason.trim();
        ledger.reverse(LedgerDocumentType.SALES_INVOICE, invoice.id, LocalDate.now(), "İptal: " + invoice.number + (text == null ? "" : " — " + text));
        invoice.status = InvoiceStatus.CANCELLED;
        invoice.cancelledAt = Instant.now();
        if (text != null) invoice.notes = invoice.notes == null || invoice.notes.isEmpty() ? "[İptal] " + text : invoice.notes + "\n[İptal] " + text;
        return get(id);
    }

    public Detail get(UUID id) {
        SalesInvoice invoice = Guard.found(db.first("select i from SalesInvoice i where i.id = ?1", SalesInvoice.class, id), "Satış faturası bulunamadı");
        BusinessPartner partner = db.get(BusinessPartner.class, invoice.partnerId);
        SalesOrder order = invoice.orderId == null ? null : db.find(SalesOrder.class, invoice.orderId);
        List<LineDto> lines = new ArrayList<>();
        for (SalesInvoiceLine line : db.list("select l from SalesInvoiceLine l where l.invoiceId = ?1 order by l.lineNo", SalesInvoiceLine.class, id)) {
            ProductVariant variant = line.variantId == null ? null : db.find(ProductVariant.class, line.variantId);
            lines.add(new LineDto(line.id, line.lineNo, line.variantId, variant == null ? null : variant.sku, line.description, line.quantity, line.returnedQty,
                    line.unitPrice, line.discountRate, line.vatRate, line.lineTotal));
        }
        return new Detail(invoice.id, invoice.number, invoice.partnerId, partner.code, partner.title, invoice.orderId, order == null ? null : order.number,
                invoice.invoiceDate, invoice.dueDate, invoice.currencyCode, invoice.exchangeRate, invoice.status, invoice.paymentStatus, invoice.paidAmount,
                invoice.grandTotal.subtract(invoice.paidAmount).max(BigDecimal.ZERO), TradeService.isOverdue(invoice.status, invoice.paymentStatus, invoice.dueDate),
                invoice.notes, invoice.status == InvoiceStatus.ISSUED && invoice.paidAmount.signum() == 0,
                TradeService.totals(new DocumentMath.DocumentTotals(invoice.subtotal, invoice.discountTotal, invoice.vatTotal, invoice.grandTotal), invoice.exchangeRate), lines);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
