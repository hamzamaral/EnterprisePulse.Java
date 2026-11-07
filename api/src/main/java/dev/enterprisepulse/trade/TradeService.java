package dev.enterprisepulse.trade;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Currency;
import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.PaymentStatus;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.Unit;
import java.math.BigDecimal;
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
public class TradeService {
    public record LineInput(UUID variantId, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal vatRate,
                             UUID requestLineId, UUID orderLineId) {
        public LineInput {
            if (discountRate == null) discountRate = BigDecimal.ZERO;
            if (vatRate == null) vatRate = BigDecimal.ZERO;
        }
    }

    public record PricedLine(int lineNo, UUID variantId, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate,
                              BigDecimal vatRate, BigDecimal lineTotal, LineInput input) {}

    public record PricedDocument(List<PricedLine> lines, DocumentMath.DocumentTotals totals) {}

    public record CurrencyChoice(String currencyCode, BigDecimal exchangeRate, String baseCurrency) {}

    public record LineDto(UUID id, int lineNo, UUID variantId, UUID productId, String sku, ProductType productType, String unitCode,
                           String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal vatRate, BigDecimal lineTotal) {}

    public record TotalsDto(BigDecimal subtotal, BigDecimal discountTotal, BigDecimal vatTotal, BigDecimal grandTotal, BigDecimal grandTotalBase) {}

    private final Db db;

    public TradeService(Db db) {
        this.db = db;
    }

    public CurrencyChoice resolveCurrency(String currencyCode, BigDecimal exchangeRate, LocalDate date) {
        String baseCurrency = db.one("select c.baseCurrency from Company c where c.id = ?1", String.class, CurrentUser.get().companyId);
        String code = currencyCode == null || currencyCode.isBlank() ? baseCurrency : currencyCode.trim().toUpperCase();
        if (code.equals(baseCurrency)) return new CurrencyChoice(code, BigDecimal.ONE, baseCurrency);
        Currency currency = db.first("select c from Currency c where c.code = ?1", Currency.class, code).orElse(null);
        Guard.that(currency != null && currency.isActive, "Bilinmeyen para birimi: " + code);
        if (exchangeRate != null) {
            Guard.that(exchangeRate.signum() > 0, "Kur sıfırdan büyük olmalı");
            return new CurrencyChoice(code, exchangeRate, baseCurrency);
        }
        BigDecimal latest = db.first("select r.selling from ExchangeRate r where r.currencyCode = ?1 and r.rateDate <= ?2 order by r.rateDate desc",
                BigDecimal.class, code, date).orElse(null);
        Guard.that(latest != null, code + " için kur bulunamadı; kuru elle girin");
        return new CurrencyChoice(code, latest, baseCurrency);
    }

    public Map<UUID, ProductVariant> loadVariants(List<LineInput> lines) {
        List<UUID> ids = lines.stream().map(LineInput::variantId).filter(id -> id != null).distinct().toList();
        Map<UUID, ProductVariant> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        List<ProductVariant> rows = db.list("select v from ProductVariant v where v.id in ?1", ProductVariant.class, ids);
        Guard.that(rows.size() == ids.size(), "Bir veya daha fazla ürün bulunamadı");
        for (ProductVariant v : rows) {
            Product p = db.get(Product.class, v.productId);
            Guard.that(v.isActive && p.isActive && p.deletedAt == null, v.sku + " pasif durumda");
            map.put(v.id, v);
        }
        return map;
    }

    public Product product(ProductVariant variant) {
        return variant == null ? null : db.get(Product.class, variant.productId);
    }

    public PricedDocument priceLines(List<LineInput> inputs, Map<UUID, ProductVariant> variants, boolean requireItem) {
        Guard.that(inputs != null && !inputs.isEmpty(), "En az bir satır gerekli");
        List<PricedLine> lines = new ArrayList<>();
        List<DocumentMath.LineInput> math = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            LineInput input = inputs.get(i);
            ProductVariant variant = input.variantId() == null ? null : variants.get(input.variantId());
            Guard.that(!requireItem || variant != null, "Satır " + (i + 1) + ": ürün seçilmeli");
            Guard.that(input.quantity().signum() > 0, "Satır " + (i + 1) + ": miktar sıfırdan büyük olmalı");
            Guard.that(input.unitPrice().signum() >= 0, "Satır " + (i + 1) + ": birim fiyat negatif olamaz");
            Guard.that(input.discountRate().signum() >= 0 && input.discountRate().compareTo(new BigDecimal("100")) <= 0, "Satır " + (i + 1) + ": iskonto %0-100 arasında olmalı");
            Guard.that(input.vatRate().signum() >= 0 && input.vatRate().compareTo(new BigDecimal("100")) <= 0, "Satır " + (i + 1) + ": KDV %0-100 arasında olmalı");
            String description = input.description() != null && !input.description().isBlank() ? input.description().trim() : variant == null ? "" : label(variant);
            Guard.that(!description.isEmpty(), "Satır " + (i + 1) + ": açıklama gerekli");
            DocumentMath.LineInput priced = new DocumentMath.LineInput(input.quantity(), input.unitPrice(), input.discountRate(), input.vatRate());
            math.add(priced);
            lines.add(new PricedLine(i + 1, variant == null ? null : variant.id, description, input.quantity().setScale(3, java.math.RoundingMode.HALF_UP),
                    input.unitPrice(), input.discountRate(), input.vatRate(), DocumentMath.line(priced).total(), input));
        }
        return new PricedDocument(lines, DocumentMath.document(math));
    }

    public String label(ProductVariant variant) {
        Product product = db.get(Product.class, variant.productId);
        return variant.name != null && !variant.name.isBlank() ? product.name + " (" + variant.name + ")" : product.name;
    }

    public static boolean isStocked(Product product) {
        return product != null && product.type == ProductType.GOODS;
    }

    public LineDto lineDto(UUID id, int lineNo, ProductVariant variant, String description, BigDecimal quantity, BigDecimal unitPrice,
                            BigDecimal discountRate, BigDecimal vatRate, BigDecimal lineTotal) {
        Product product = variant == null ? null : db.get(Product.class, variant.productId);
        Unit unit = product == null || product.unitId == null ? null : db.find(Unit.class, product.unitId);
        return new LineDto(id, lineNo, variant == null ? null : variant.id, product == null ? null : product.id, variant == null ? null : variant.sku,
                product == null ? null : product.type, unit == null ? null : unit.code, description, quantity, unitPrice, discountRate, vatRate, lineTotal);
    }

    public static TotalsDto totals(DocumentMath.DocumentTotals t, BigDecimal rate) {
        return new TotalsDto(t.subtotal(), t.discountTotal(), t.vatTotal(), t.grandTotal(), DocumentMath.toBase(t.grandTotal(), rate));
    }

    public static boolean isOverdue(InvoiceStatus status, PaymentStatus payment, LocalDate dueDate) {
        return status == InvoiceStatus.ISSUED && payment != PaymentStatus.PAID && dueDate.isBefore(LocalDate.now());
    }
}
