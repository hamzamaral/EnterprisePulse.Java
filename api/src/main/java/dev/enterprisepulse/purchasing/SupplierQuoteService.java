package dev.enterprisepulse.purchasing;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.PurchaseOrder;
import dev.enterprisepulse.data.generated.PurchaseOrderStatus;
import dev.enterprisepulse.data.generated.PurchaseRequest;
import dev.enterprisepulse.data.generated.PurchaseRequestLine;
import dev.enterprisepulse.data.generated.PurchaseRequestStatus;
import dev.enterprisepulse.data.generated.SupplierQuote;
import dev.enterprisepulse.data.generated.SupplierQuoteLine;
import dev.enterprisepulse.data.generated.SupplierQuoteStatus;
import dev.enterprisepulse.data.generated.Unit;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.partners.PartnerLedgerService;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import dev.enterprisepulse.trade.TradeService;
import dev.enterprisepulse.trade.TradeService.LineInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SupplierQuoteService {
    public record CreateQuote(UUID partnerId, UUID requestId, LocalDate quoteDate, LocalDate validUntil, Integer deliveryDays,
                               String currencyCode, BigDecimal exchangeRate, String notes, List<LineInput> lines) {}

    public record Accept(UUID warehouseId, LocalDate expectedDate) {}

    public record Detail(UUID id, String number, UUID requestId, String requestNumber, UUID partnerId, String partnerCode, String partnerTitle,
                          LocalDate quoteDate, LocalDate validUntil, Integer deliveryDays, String currencyCode, BigDecimal exchangeRate,
                          SupplierQuoteStatus status, String notes, UUID orderId, String orderNumber, TradeService.TotalsDto totals, List<TradeService.LineDto> lines) {}

    public record PriceCell(UUID requestLineId, BigDecimal netUnitPriceBase) {}

    public record Compared(UUID id, String number, UUID partnerId, String partnerTitle, SupplierQuoteStatus status, BigDecimal grandTotalBase, boolean complete, List<PriceCell> prices) {}

    public record Comparison(UUID requestId, String baseCurrency, UUID bestQuoteId, List<PurchaseRequestService.LineDto> lines, List<Compared> quotes) {}

    private final Db db;
    private final NumberingService numbering;
    private final TradeService trade;
    private final PartnerLedgerService partners;
    private final PurchaseOrderService orders;

    public SupplierQuoteService(Db db, NumberingService numbering, TradeService trade, PartnerLedgerService partners, @Lazy PurchaseOrderService orders) {
        this.db = db;
        this.numbering = numbering;
        this.trade = trade;
        this.partners = partners;
        this.orders = orders;
    }

    public Detail create(CreateQuote body) {
        BusinessPartner partner = partners.forDocument(body.partnerId(), PartnerLedgerService.SUPPLIERS);
        PurchaseRequest request = null;
        if (body.requestId() != null) {
            request = Guard.found(db.first("select r from PurchaseRequest r where r.id = ?1", PurchaseRequest.class, body.requestId()), "Talep bulunamadı");
            Guard.state(request.status == PurchaseRequestStatus.APPROVED, "Yalnızca onaylı talebe teklif girilir");
        }
        LocalDate date = body.quoteDate() == null ? LocalDate.now() : body.quoteDate();
        var currency = trade.resolveCurrency(body.currencyCode(), body.exchangeRate(), date);
        Map<UUID, ProductVariant> variants = trade.loadVariants(body.lines());
        if (request != null) {
            for (LineInput line : body.lines()) {
                if (line.requestLineId() == null) continue;
                Guard.that(db.exists("select l.id from PurchaseRequestLine l where l.id = ?1 and l.requestId = ?2", line.requestLineId(), request.id), "Talep satırı bu talebe ait değil");
            }
        }
        var priced = trade.priceLines(body.lines(), variants, false);
        SupplierQuote quote = new SupplierQuote();
        quote.number = numbering.next("SUPPLIER_QUOTE");
        quote.requestId = request == null ? null : request.id;
        quote.partnerId = partner.id;
        quote.quoteDate = date;
        quote.validUntil = body.validUntil();
        quote.deliveryDays = body.deliveryDays();
        quote.currencyCode = currency.currencyCode();
        quote.exchangeRate = currency.exchangeRate();
        quote.notes = body.notes();
        quote.createdById = CurrentUser.get().requireUserId();
        quote.subtotal = priced.totals().subtotal();
        quote.discountTotal = priced.totals().discountTotal();
        quote.vatTotal = priced.totals().vatTotal();
        quote.grandTotal = priced.totals().grandTotal();
        db.add(quote);
        for (var line : priced.lines()) {
            SupplierQuoteLine row = new SupplierQuoteLine();
            row.quoteId = quote.id;
            row.lineNo = line.lineNo();
            row.requestLineId = line.input().requestLineId();
            row.variantId = line.variantId();
            row.description = line.description();
            row.quantity = line.quantity();
            row.unitPrice = line.unitPrice();
            row.discountRate = line.discountRate();
            row.vatRate = line.vatRate();
            row.lineTotal = line.lineTotal();
            db.add(row);
        }
        db.flush();
        return detail(quote.id);
    }

    public Detail reject(UUID id) {
        SupplierQuote quote = open(id);
        quote.status = SupplierQuoteStatus.REJECTED;
        db.flush();
        return detail(id);
    }

    public UUID accept(UUID id, Accept body) {
        SupplierQuote quote = open(id);
        Warehouse warehouse = db.get(Warehouse.class, body.warehouseId());
        Guard.that(warehouse.isActive, warehouse.name + " deposu pasif");
        List<SupplierQuoteLine> lines = db.list("select l from SupplierQuoteLine l where l.quoteId = ?1 order by l.lineNo", SupplierQuoteLine.class, quote.id);
        Guard.that(lines.stream().allMatch(l -> l.variantId != null), "Sipariş için her satırda ürün olmalı");
        List<LineInput> inputs = lines.stream().map(l -> new LineInput(l.variantId, l.description, l.quantity, l.unitPrice, l.discountRate, l.vatRate, l.requestLineId, null)).toList();
        PurchaseOrder order = orders.createEntity(new PurchaseOrderService.Save(quote.partnerId, warehouse.id, LocalDate.now(), body.expectedDate(),
                quote.currencyCode, quote.exchangeRate, quote.notes, inputs), quote.requestId, quote.id);
        quote.status = SupplierQuoteStatus.ACCEPTED;
        if (quote.requestId != null) {
            for (SupplierQuote other : db.list("select q from SupplierQuote q where q.requestId = ?1 and q.status = ?2 and q.id <> ?3",
                    SupplierQuote.class, quote.requestId, SupplierQuoteStatus.OPEN, quote.id)) other.status = SupplierQuoteStatus.REJECTED;
            PurchaseRequest request = db.get(PurchaseRequest.class, quote.requestId);
            request.status = PurchaseRequestStatus.ORDERED;
        }
        db.flush();
        return order.id;
    }

    public Detail detail(UUID id) {
        SupplierQuote q = Guard.found(db.first("select q from SupplierQuote q where q.id = ?1", SupplierQuote.class, id), "Teklif bulunamadı");
        BusinessPartner partner = db.get(BusinessPartner.class, q.partnerId);
        PurchaseRequest request = q.requestId == null ? null : db.find(PurchaseRequest.class, q.requestId);
        PurchaseOrder order = db.first("select o from PurchaseOrder o where o.quoteId = ?1", PurchaseOrder.class, q.id).orElse(null);
        List<TradeService.LineDto> lines = new ArrayList<>();
        for (SupplierQuoteLine l : db.list("select l from SupplierQuoteLine l where l.quoteId = ?1 order by l.lineNo", SupplierQuoteLine.class, q.id)) {
            lines.add(trade.lineDto(l.id, l.lineNo, l.variantId == null ? null : db.find(ProductVariant.class, l.variantId), l.description, l.quantity, l.unitPrice, l.discountRate, l.vatRate, l.lineTotal));
        }
        var totals = new TradeService.TotalsDto(q.subtotal, q.discountTotal, q.vatTotal, q.grandTotal, DocumentMath.toBase(q.grandTotal, q.exchangeRate));
        return new Detail(q.id, q.number, q.requestId, request == null ? null : request.number, partner.id, partner.code, partner.title, q.quoteDate, q.validUntil,
                q.deliveryDays, q.currencyCode, q.exchangeRate, q.status, q.notes, order == null ? null : order.id, order == null ? null : order.number, totals, lines);
    }

    public Comparison compare(UUID requestId) {
        PurchaseRequest request = Guard.found(db.first("select r from PurchaseRequest r where r.id = ?1", PurchaseRequest.class, requestId), "Talep bulunamadı");
        var requestLines = db.list("select l from PurchaseRequestLine l where l.requestId = ?1 order by l.lineNo", PurchaseRequestLine.class, requestId);
        String base = db.one("select c.baseCurrency from Company c where c.id = ?1", String.class, request.companyId);
        List<Compared> quotes = new ArrayList<>();
        UUID best = null;
        BigDecimal bestTotal = null;
        for (SupplierQuote q : db.list("select q from SupplierQuote q where q.requestId = ?1", SupplierQuote.class, requestId)) {
            BusinessPartner partner = db.get(BusinessPartner.class, q.partnerId);
            List<SupplierQuoteLine> lines = db.list("select l from SupplierQuoteLine l where l.quoteId = ?1", SupplierQuoteLine.class, q.id);
            boolean complete = requestLines.stream().allMatch(r -> lines.stream().anyMatch(l -> r.id.equals(l.requestLineId) && l.variantId != null));
            List<PriceCell> prices = new ArrayList<>();
            for (SupplierQuoteLine l : lines) {
                if (l.requestLineId == null) continue;
                BigDecimal net = l.unitPrice.multiply(new BigDecimal("100").subtract(l.discountRate)).movePointLeft(2).multiply(q.exchangeRate).setScale(4, RoundingMode.HALF_UP);
                prices.add(new PriceCell(l.requestLineId, net));
            }
            BigDecimal totalBase = DocumentMath.toBase(q.grandTotal, q.exchangeRate);
            quotes.add(new Compared(q.id, q.number, partner.id, partner.title, q.status, totalBase, complete, prices));
            if (q.status == SupplierQuoteStatus.OPEN && complete && (bestTotal == null || totalBase.compareTo(bestTotal) < 0)) {
                best = q.id;
                bestTotal = totalBase;
            }
        }
        quotes.sort(Comparator.comparing(Compared::grandTotalBase));
        List<PurchaseRequestService.LineDto> lines = new ArrayList<>();
        for (PurchaseRequestLine l : requestLines) {
            ProductVariant v = l.variantId == null ? null : db.find(ProductVariant.class, l.variantId);
            Product p = v == null ? null : db.find(Product.class, v.productId);
            Unit unit = p == null || p.unitId == null ? null : db.find(Unit.class, p.unitId);
            lines.add(new PurchaseRequestService.LineDto(l.id, l.lineNo, l.variantId, p == null ? null : p.id, v == null ? null : v.sku, unit == null ? null : unit.code, l.description, l.quantity, l.estimatedPrice));
        }
        return new Comparison(request.id, base, best, lines, quotes);
    }

    private SupplierQuote open(UUID id) {
        SupplierQuote quote = Guard.found(db.first("select q from SupplierQuote q where q.id = ?1", SupplierQuote.class, id), "Teklif bulunamadı");
        Guard.state(quote.status == SupplierQuoteStatus.OPEN, "Teklif artık açık değil");
        return quote;
    }
}
