package dev.enterprisepulse.sales;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.LedgerDocumentType;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.SalesInvoice;
import dev.enterprisepulse.data.generated.SalesInvoiceLine;
import dev.enterprisepulse.data.generated.SalesReturn;
import dev.enterprisepulse.data.generated.SalesReturnLine;
import dev.enterprisepulse.data.generated.StockMovement;
import dev.enterprisepulse.data.generated.StockMovementType;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.finance.InvoicePayments;
import dev.enterprisepulse.inventory.StockMovementService;
import dev.enterprisepulse.partners.PartnerLedgerService;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import dev.enterprisepulse.trade.TradeService;
import dev.enterprisepulse.trade.TradeService.LineInput;
import dev.enterprisepulse.trade.TradeService.PricedDocument;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SalesReturnService {
    public record LineIn(UUID invoiceLineId, BigDecimal quantity, String lotNo, List<String> serialNos) {}

    public record Create(UUID invoiceId, UUID warehouseId, LocalDate returnDate, String reason, List<LineIn> lines) {}

    public record Detail(UUID id, String number, UUID invoiceId, String invoiceNumber, UUID partnerId, String partnerTitle, UUID warehouseId, String warehouseName,
                          LocalDate returnDate, String reason, String currencyCode, BigDecimal exchangeRate, BigDecimal grandTotal, UUID stockMovementId, String stockMovementNumber) {}

    private final Db db;
    private final NumberingService numbering;
    private final StockMovementService stock;
    private final PartnerLedgerService ledger;
    private final TradeService trade;

    public SalesReturnService(Db db, NumberingService numbering, StockMovementService stock, PartnerLedgerService ledger, TradeService trade) {
        this.db = db;
        this.numbering = numbering;
        this.stock = stock;
        this.ledger = ledger;
        this.trade = trade;
    }

    public Detail create(Create body) {
        Guard.that(body.lines() != null && !body.lines().isEmpty(), "İade satırı gerekli");
        Guard.that(body.reason() != null && !body.reason().isBlank(), "İade gerekçesi gerekli");
        db.nativeRows("SELECT id FROM sales_invoices WHERE id = ?1 FOR UPDATE", body.invoiceId());
        SalesInvoice invoice = Guard.found(db.first("select i from SalesInvoice i where i.id = ?1", SalesInvoice.class, body.invoiceId()), "Satış faturası bulunamadı");
        Guard.state(invoice.status == InvoiceStatus.ISSUED, "Yalnızca kesilmiş fatura iade edilir");
        Warehouse warehouse = Guard.found(db.first("select w from Warehouse w where w.id = ?1 and w.isActive = true", Warehouse.class, body.warehouseId()), "Depo bulunamadı");
        List<SalesInvoiceLine> invoiceLines = db.list("select l from SalesInvoiceLine l where l.invoiceId = ?1", SalesInvoiceLine.class, invoice.id);
        List<LineInput> inputs = new ArrayList<>();
        List<SalesInvoiceLine> chosen = new ArrayList<>();
        for (LineIn input : body.lines()) {
            SalesInvoiceLine line = invoiceLines.stream().filter(l -> l.id.equals(input.invoiceLineId())).findFirst().orElse(null);
            Guard.that(line != null, "Satır bu faturada yok");
            BigDecimal open = line.quantity.subtract(line.returnedQty);
            Guard.that(input.quantity() != null && input.quantity().signum() > 0 && input.quantity().compareTo(open.add(new BigDecimal("0.0005"))) <= 0,
                    line.description + " için en fazla " + StockMovementService.qty(open) + " iade edilebilir");
            inputs.add(new LineInput(line.variantId, line.description, input.quantity(), line.unitPrice, line.discountRate, line.vatRate, null, null));
            chosen.add(line);
        }
        PricedDocument priced = trade.priceLines(inputs, trade.loadVariants(inputs), false);
        List<StockMovementService.LineInput> stockLines = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            SalesInvoiceLine line = chosen.get(i);
            if (line.variantId == null) continue;
            Product product = db.get(Product.class, db.get(ProductVariant.class, line.variantId).productId);
            if (product.type != ProductType.GOODS) continue;
            LineIn input = body.lines().get(i);
            stockLines.add(new StockMovementService.LineInput(line.variantId, input.quantity(),
                    DocumentMath.netUnitCostBase(line.unitPrice, line.discountRate, invoice.exchangeRate), null, input.lotNo(), null, input.serialNos(), null));
        }
        UUID movementId = null;
        if (!stockLines.isEmpty()) {
            var posted = stock.post(new StockMovementService.CreateRequest(StockMovementType.RECEIPT, body.returnDate(), null, warehouse.id, null, body.reason().trim(), stockLines), null);
            movementId = posted.movementId();
            stock.notifyLowStock(posted.lowStock());
        }
        SalesReturn doc = new SalesReturn();
        doc.number = numbering.next("SALES_RETURN");
        doc.invoiceId = invoice.id;
        doc.partnerId = invoice.partnerId;
        doc.warehouseId = warehouse.id;
        doc.returnDate = body.returnDate();
        doc.reason = body.reason().trim();
        doc.currencyCode = invoice.currencyCode;
        doc.exchangeRate = invoice.exchangeRate;
        doc.stockMovementId = movementId;
        doc.createdById = CurrentUser.get().requireUserId();
        doc.subtotal = priced.totals().subtotal();
        doc.discountTotal = priced.totals().discountTotal();
        doc.vatTotal = priced.totals().vatTotal();
        doc.grandTotal = priced.totals().grandTotal();
        db.add(doc);
        for (int i = 0; i < chosen.size(); i++) {
            SalesInvoiceLine line = chosen.get(i);
            LineIn input = body.lines().get(i);
            SalesReturnLine row = new SalesReturnLine();
            row.returnId = doc.id;
            row.lineNo = i + 1;
            row.invoiceLineId = line.id;
            row.variantId = line.variantId;
            row.description = line.description;
            row.quantity = input.quantity();
            row.unitPrice = line.unitPrice;
            row.discountRate = line.discountRate;
            row.vatRate = line.vatRate;
            row.lineTotal = priced.lines().get(i).lineTotal();
            row.lotNo = input.lotNo() == null || input.lotNo().isBlank() ? null : input.lotNo().trim();
            row.serialNos = input.serialNos() == null || input.serialNos().isEmpty() ? "" : String.join(", ", input.serialNos());
            db.add(row);
            line.returnedQty = line.returnedQty.add(input.quantity());
        }
        InvoicePayments.apply(invoice, priced.totals().grandTotal());
        if (priced.totals().grandTotal().signum() > 0) {
            ledger.post(new PartnerLedgerService.Posting(invoice.partnerId, body.returnDate(), null, LedgerDocumentType.SALES_RETURN, doc.id, doc.number,
                    "Satış iadesi " + doc.number, BigDecimal.ZERO, priced.totals().grandTotal(), invoice.currencyCode, invoice.exchangeRate));
        }
        return get(doc.id);
    }

    public Detail get(UUID id) {
        SalesReturn row = Guard.found(db.first("select r from SalesReturn r where r.id = ?1", SalesReturn.class, id), "Satış iadesi bulunamadı");
        SalesInvoice invoice = db.get(SalesInvoice.class, row.invoiceId);
        BusinessPartner partner = db.get(BusinessPartner.class, row.partnerId);
        Warehouse warehouse = db.get(Warehouse.class, row.warehouseId);
        StockMovement movement = row.stockMovementId == null ? null : db.find(StockMovement.class, row.stockMovementId);
        return new Detail(row.id, row.number, row.invoiceId, invoice.number, row.partnerId, partner.title, row.warehouseId, warehouse.name, row.returnDate,
                row.reason, row.currencyCode, row.exchangeRate, row.grandTotal, row.stockMovementId, movement == null ? null : movement.number);
    }
}
