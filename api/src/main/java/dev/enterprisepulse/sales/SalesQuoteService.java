package dev.enterprisepulse.sales;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.SalesOrder;
import dev.enterprisepulse.data.generated.SalesQuote;
import dev.enterprisepulse.data.generated.SalesQuoteLine;
import dev.enterprisepulse.data.generated.SalesQuoteStatus;
import dev.enterprisepulse.partners.PartnerLedgerService;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import dev.enterprisepulse.trade.TradeService;
import dev.enterprisepulse.trade.TradeService.LineInput;
import dev.enterprisepulse.trade.TradeService.PricedDocument;
import dev.enterprisepulse.trade.TradeService.PricedLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SalesQuoteService {
    public record Create(UUID partnerId, LocalDate quoteDate, LocalDate validUntil, String currencyCode, BigDecimal exchangeRate, String notes, List<LineInput> lines) {}

    public record Detail(UUID id, String number, UUID partnerId, String partnerCode, String partnerTitle, LocalDate quoteDate, LocalDate validUntil,
                          String currencyCode, BigDecimal exchangeRate, SalesQuoteStatus status, String notes, UUID orderId, String orderNumber,
                          TradeService.TotalsDto totals, List<TradeService.LineDto> lines) {}

    private final Db db;
    private final NumberingService numbering;
    private final TradeService trade;
    private final PartnerLedgerService partners;
    private final SalesOrderService orders;

    public SalesQuoteService(Db db, NumberingService numbering, TradeService trade, PartnerLedgerService partners, SalesOrderService orders) {
        this.db = db;
        this.numbering = numbering;
        this.trade = trade;
        this.partners = partners;
        this.orders = orders;
    }

    public Detail create(Create body) {
        BusinessPartner partner = partners.forDocument(body.partnerId(), PartnerLedgerService.CUSTOMERS);
        LocalDate date = body.quoteDate() == null ? LocalDate.now() : body.quoteDate();
        var currency = trade.resolveCurrency(body.currencyCode() == null ? partner.currencyCode : body.currencyCode(), body.exchangeRate(), date);
        List<LineInput> inputs = body.lines() == null ? List.of() : body.lines();
        PricedDocument priced = trade.priceLines(inputs, trade.loadVariants(inputs), false);
        SalesQuote quote = new SalesQuote();
        quote.number = numbering.next("SALES_QUOTE");
        quote.partnerId = partner.id;
        quote.quoteDate = date;
        quote.validUntil = body.validUntil();
        quote.currencyCode = currency.currencyCode();
        quote.exchangeRate = currency.exchangeRate();
        quote.notes = blank(body.notes());
        quote.subtotal = priced.totals().subtotal();
        quote.discountTotal = priced.totals().discountTotal();
        quote.vatTotal = priced.totals().vatTotal();
        quote.grandTotal = priced.totals().grandTotal();
        quote.createdById = CurrentUser.get().requireUserId();
        db.add(quote);
        for (PricedLine line : priced.lines()) {
            SalesQuoteLine row = new SalesQuoteLine();
            row.quoteId = quote.id;
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
        return get(quote.id);
    }

    public Detail reject(UUID id) {
        SalesQuote quote = open(id);
        quote.status = SalesQuoteStatus.REJECTED;
        return get(id);
    }

    public UUID accept(UUID id, UUID warehouseId, LocalDate deliveryDate) {
        SalesQuote quote = open(id);
        List<SalesQuoteLine> lines = db.list("select l from SalesQuoteLine l where l.quoteId = ?1 order by l.lineNo", SalesQuoteLine.class, id);
        Guard.that(lines.stream().allMatch(l -> l.variantId != null), "Sipariş için her satırda ürün olmalı");
        List<LineInput> inputs = new ArrayList<>();
        for (SalesQuoteLine line : lines) inputs.add(new LineInput(line.variantId, line.description, line.quantity, line.unitPrice, line.discountRate, line.vatRate, null, null));
        SalesOrder order = orders.createEntity(new SalesOrderService.Save(quote.partnerId, warehouseId, LocalDate.now(), deliveryDate, null, quote.currencyCode,
                quote.exchangeRate, quote.notes, inputs), quote.id);
        quote.status = SalesQuoteStatus.ACCEPTED;
        return order.id;
    }

    public Detail get(UUID id) {
        SalesQuote quote = Guard.found(db.first("select q from SalesQuote q where q.id = ?1", SalesQuote.class, id), "Satış teklifi bulunamadı");
        BusinessPartner partner = db.get(BusinessPartner.class, quote.partnerId);
        SalesOrder order = quote.status == SalesQuoteStatus.ACCEPTED
                ? db.first("select o from SalesOrder o where o.quoteId = ?1", SalesOrder.class, quote.id).orElse(null) : null;
        List<TradeService.LineDto> lines = new ArrayList<>();
        for (SalesQuoteLine line : db.list("select l from SalesQuoteLine l where l.quoteId = ?1 order by l.lineNo", SalesQuoteLine.class, id)) {
            ProductVariant variant = line.variantId == null ? null : db.get(ProductVariant.class, line.variantId);
            lines.add(trade.lineDto(line.id, line.lineNo, variant, line.description, line.quantity, line.unitPrice, line.discountRate, line.vatRate, line.lineTotal));
        }
        return new Detail(quote.id, quote.number, quote.partnerId, partner.code, partner.title, quote.quoteDate, quote.validUntil, quote.currencyCode,
                quote.exchangeRate, quote.status, quote.notes, order == null ? null : order.id, order == null ? null : order.number,
                TradeService.totals(new DocumentMath.DocumentTotals(quote.subtotal, quote.discountTotal, quote.vatTotal, quote.grandTotal), quote.exchangeRate), lines);
    }

    private SalesQuote open(UUID id) {
        SalesQuote quote = Guard.found(db.first("select q from SalesQuote q where q.id = ?1", SalesQuote.class, id), "Satış teklifi bulunamadı");
        Guard.state(quote.status == SalesQuoteStatus.OPEN, "Teklif artık açık değil");
        return quote;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
