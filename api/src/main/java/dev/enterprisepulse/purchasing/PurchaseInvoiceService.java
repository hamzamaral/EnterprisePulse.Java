package dev.enterprisepulse.purchasing;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.LedgerDocumentType;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.PurchaseInvoice;
import dev.enterprisepulse.data.generated.PurchaseInvoiceLine;
import dev.enterprisepulse.data.generated.PurchaseOrder;
import dev.enterprisepulse.data.generated.PurchaseOrderLine;
import dev.enterprisepulse.data.generated.PurchaseOrderStatus;
import dev.enterprisepulse.inventory.StockMovementService;
import dev.enterprisepulse.partners.PartnerLedgerService;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import dev.enterprisepulse.trade.TradeService;
import dev.enterprisepulse.trade.TradeService.LineInput;
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
public class PurchaseInvoiceService {
    public record Create(UUID partnerId, UUID orderId, String supplierInvoiceNo, LocalDate invoiceDate, LocalDate dueDate, String currencyCode,
                          BigDecimal exchangeRate, String notes, List<LineInput> lines) {}

    public record Detail(UUID id, String number, String supplierInvoiceNo, UUID partnerId, String partnerCode, String partnerTitle, UUID orderId,
                          String orderNumber, LocalDate invoiceDate, LocalDate dueDate, String currencyCode, BigDecimal exchangeRate, InvoiceStatus status,
                          dev.enterprisepulse.data.generated.PaymentStatus paymentStatus, BigDecimal paidAmount, boolean overdue, String notes,
                          Instant cancelledAt, boolean canCancel, TradeService.TotalsDto totals, List<TradeService.LineDto> lines) {}

    private final Db db;
    private final NumberingService numbering;
    private final TradeService trade;
    private final PartnerLedgerService ledger;

    public PurchaseInvoiceService(Db db, NumberingService numbering, TradeService trade, PartnerLedgerService ledger) {
        this.db = db;
        this.numbering = numbering;
        this.trade = trade;
        this.ledger = ledger;
    }

    public Detail create(Create body) {
        String supplierNo = body.supplierInvoiceNo() == null ? "" : body.supplierInvoiceNo().trim();
        Guard.that(!supplierNo.isEmpty(), "Tedarikçi fatura numarası gerekli");
        BusinessPartner partner = ledger.forDocument(body.partnerId(), PartnerLedgerService.SUPPLIERS);
        Guard.state(!db.exists("select i.id from PurchaseInvoice i where i.partnerId = ?1 and i.supplierInvoiceNo = ?2 and i.status <> ?3",
                partner.id, supplierNo, InvoiceStatus.CANCELLED), supplierNo + " numaralı fatura zaten kayıtlı");
        PurchaseOrder order = null;
        Map<UUID, PurchaseOrderLine> orderLines = new HashMap<>();
        if (body.orderId() != null) {
            db.nativeRows("SELECT id FROM purchase_orders WHERE id = ?1 FOR UPDATE", body.orderId());
            order = Guard.found(db.first("select o from PurchaseOrder o where o.id = ?1", PurchaseOrder.class, body.orderId()), "Satın alma siparişi bulunamadı");
            Guard.that(order.partnerId.equals(partner.id), "Sipariş başka bir tedarikçiye ait");
            Guard.state(order.status == PurchaseOrderStatus.APPROVED || order.status == PurchaseOrderStatus.PARTIALLY_RECEIVED || order.status == PurchaseOrderStatus.RECEIVED,
                    "Yalnızca onaylı sipariş faturalanabilir");
            for (PurchaseOrderLine line : db.list("select l from PurchaseOrderLine l where l.orderId = ?1", PurchaseOrderLine.class, order.id)) orderLines.put(line.id, line);
        }
        Map<UUID, BigDecimal> invoicing = new HashMap<>();
        List<LineInput> inputs = new ArrayList<>();
        List<LineInput> source = body.lines() == null ? List.of() : body.lines();
        for (int i = 0; i < source.size(); i++) {
            LineInput input = source.get(i);
            if (input.orderLineId() == null) {
                inputs.add(input);
                continue;
            }
            PurchaseOrderLine line = orderLines.get(input.orderLineId());
            Guard.that(line != null, "Satır " + (i + 1) + ": sipariş satırı bu siparişte yok");
            BigDecimal total = invoicing.getOrDefault(line.id, BigDecimal.ZERO).add(input.quantity());
            Product product = db.get(Product.class, db.get(ProductVariant.class, line.variantId).productId);
            BigDecimal limit = product.type == ProductType.GOODS ? line.receivedQty : line.quantity;
            BigDecimal open = limit.subtract(line.invoicedQty).max(BigDecimal.ZERO);
            Guard.that(total.compareTo(open.add(new BigDecimal("0.0005"))) <= 0,
                    "Satır " + (i + 1) + ": en fazla " + StockMovementService.qty(open) + " faturalanabilir");
            invoicing.put(line.id, total);
            inputs.add(new LineInput(line.variantId, input.description(), input.quantity(), input.unitPrice(), input.discountRate(), input.vatRate(), input.requestLineId(), line.id));
        }
        LocalDate date = body.invoiceDate() == null ? LocalDate.now() : body.invoiceDate();
        LocalDate due = body.dueDate() == null ? date.plusDays(partner.paymentTermDays) : body.dueDate();
        Guard.that(!due.isBefore(date), "Vade fatura tarihinden önce olamaz");
        String currencyCode = body.currencyCode() != null ? body.currencyCode() : order != null ? order.currencyCode : partner.currencyCode;
        var currency = trade.resolveCurrency(currencyCode, body.exchangeRate(), date);
        var priced = trade.priceLines(inputs, trade.loadVariants(inputs), false);
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.number = numbering.next("PURCHASE_INVOICE");
        invoice.supplierInvoiceNo = supplierNo;
        invoice.partnerId = partner.id;
        invoice.orderId = order == null ? null : order.id;
        invoice.invoiceDate = date;
        invoice.dueDate = due;
        invoice.currencyCode = currency.currencyCode();
        invoice.exchangeRate = currency.exchangeRate();
        invoice.status = InvoiceStatus.ISSUED;
        invoice.notes = body.notes() == null || body.notes().isBlank() ? null : body.notes().trim();
        invoice.subtotal = priced.totals().subtotal();
        invoice.discountTotal = priced.totals().discountTotal();
        invoice.vatTotal = priced.totals().vatTotal();
        invoice.grandTotal = priced.totals().grandTotal();
        invoice.createdById = CurrentUser.get().requireUserId();
        db.add(invoice);
        for (var line : priced.lines()) {
            PurchaseInvoiceLine row = new PurchaseInvoiceLine();
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
        for (var entry : invoicing.entrySet()) orderLines.get(entry.getKey()).invoicedQty = orderLines.get(entry.getKey()).invoicedQty.add(entry.getValue());
        db.flush();
        if (priced.totals().grandTotal().signum() > 0) {
            ledger.post(new PartnerLedgerService.Posting(partner.id, date, due, LedgerDocumentType.PURCHASE_INVOICE, invoice.id, invoice.number,
                    "Alış faturası " + supplierNo, BigDecimal.ZERO, priced.totals().grandTotal(), currency.currencyCode(), currency.exchangeRate()));
        }
        return detail(invoice.id);
    }

    public Detail cancel(UUID id, String reason) {
        db.nativeRows("SELECT id FROM purchase_invoices WHERE id = ?1 FOR UPDATE", id);
        PurchaseInvoice invoice = Guard.found(db.first("select i from PurchaseInvoice i where i.id = ?1", PurchaseInvoice.class, id), "Alış faturası bulunamadı");
        Guard.state(invoice.status == InvoiceStatus.ISSUED, "Fatura zaten iptal edilmiş");
        Guard.state(invoice.paidAmount.signum() == 0, "Faturanın ödemesi var; önce ödemeyi kaldırın");
        for (PurchaseInvoiceLine line : db.list("select l from PurchaseInvoiceLine l where l.invoiceId = ?1 and l.orderLineId is not null", PurchaseInvoiceLine.class, id)) {
            PurchaseOrderLine orderLine = db.get(PurchaseOrderLine.class, line.orderLineId);
            orderLine.invoicedQty = orderLine.invoicedQty.subtract(line.quantity);
        }
        String text = reason == null || reason.isBlank() ? null : reason.trim();
        ledger.reverse(LedgerDocumentType.PURCHASE_INVOICE, invoice.id, LocalDate.now(), "İptal: " + invoice.number + (text == null ? "" : " — " + text));
        invoice.status = InvoiceStatus.CANCELLED;
        invoice.cancelledAt = Instant.now();
        if (text != null) invoice.notes = invoice.notes == null || invoice.notes.isBlank() ? "[İptal] " + text : invoice.notes + "\n[İptal] " + text;
        db.flush();
        return detail(id);
    }

    public Detail detail(UUID id) {
        PurchaseInvoice i = Guard.found(db.first("select i from PurchaseInvoice i where i.id = ?1", PurchaseInvoice.class, id), "Alış faturası bulunamadı");
        BusinessPartner partner = db.get(BusinessPartner.class, i.partnerId);
        PurchaseOrder order = i.orderId == null ? null : db.find(PurchaseOrder.class, i.orderId);
        List<TradeService.LineDto> lines = new ArrayList<>();
        for (PurchaseInvoiceLine l : db.list("select l from PurchaseInvoiceLine l where l.invoiceId = ?1 order by l.lineNo", PurchaseInvoiceLine.class, i.id)) {
            lines.add(trade.lineDto(l.id, l.lineNo, l.variantId == null ? null : db.find(ProductVariant.class, l.variantId), l.description, l.quantity, l.unitPrice, l.discountRate, l.vatRate, l.lineTotal));
        }
        var totals = new TradeService.TotalsDto(i.subtotal, i.discountTotal, i.vatTotal, i.grandTotal, DocumentMath.toBase(i.grandTotal, i.exchangeRate));
        return new Detail(i.id, i.number, i.supplierInvoiceNo, partner.id, partner.code, partner.title, i.orderId, order == null ? null : order.number,
                i.invoiceDate, i.dueDate, i.currencyCode, i.exchangeRate, i.status, i.paymentStatus, i.paidAmount, TradeService.isOverdue(i.status, i.paymentStatus, i.dueDate),
                i.notes, i.cancelledAt, i.status == InvoiceStatus.ISSUED && i.paidAmount.signum() == 0, totals, lines);
    }
}
