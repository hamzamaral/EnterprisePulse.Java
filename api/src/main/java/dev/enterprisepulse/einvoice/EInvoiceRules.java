package dev.enterprisepulse.einvoice;

import dev.enterprisepulse.data.generated.EInvoiceProfile;
import dev.enterprisepulse.data.generated.EInvoiceStatus;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** e-Fatura / e-Arşiv rules: identifiers, scenario, simulated GİB validation, response window and status machine. */
public final class EInvoiceRules {
    private EInvoiceRules() {}

    public static final Pattern ALIAS_PATTERN = Pattern.compile("^urn:mail:[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    public static final Pattern GIB_NUMBER = Pattern.compile("^[A-Z0-9]{3}\\d{4}\\d{9}$");
    public static final int RESPONSE_DAYS = 8;
    public static final String ANONYMOUS_TCKN = "11111111111";

    public static final class Codes {
        private Codes() {}
        public static final String QUEUED = "1000";
        public static final String SCHEMA_ERROR = "1150";
        public static final String PROCESSED = "1200";
        public static final String COMPLETED = "1300";
        public static final String ACCEPTED = "KABUL";
        public static final String REJECTED = "RED";
        public static final String CANCELLED = "IPTAL";
    }

    public record EInvoiceLineInput(String description, String sellerCode, UUID variantId, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal vatRate) {}

    public record EInvoiceLine(int lineNo, String description, String sellerCode, UUID variantId, String quantity, String unitCode, String unitPrice, String discountRate, String gross, String discount, String net, String vatRate, String vat) {}

    public record VatBreakdown(String rate, String base, String vat) {}

    public record EInvoiceTotals(String lineExtension, String allowance, String taxExclusive, String taxInclusive, String payable, List<VatBreakdown> taxes) {}

    public record ValidationInput(EInvoiceProfile profile, String number, String supplierTaxId, String customerTaxId, String receiverAlias, String currencyCode, BigDecimal exchangeRate, List<EInvoiceLine> lines, BigDecimal payable) {}

    public record CancelCheck(boolean allowed, boolean cancelEInvoice, String reason) {}

    public record OutgoingStep(EInvoiceStatus status, String code, String message) {}

    public record BuiltLines(List<EInvoiceLine> lines, EInvoiceTotals totals) {}

    public static boolean isValidVkn(String value) {
        if (value == null || value.length() != 10 || !value.chars().allMatch(Character::isDigit)) return false;
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int t = (value.charAt(i) - '0' + 9 - i) % 10;
            int v = (int) ((t * Math.pow(2, 9 - i)) % 9);
            if (t != 0 && v == 0) v = 9;
            sum += v;
        }
        return (10 - sum % 10) % 10 == value.charAt(9) - '0';
    }

    public static String withVknCheckDigit(String value) {
        String head = value.substring(0, 9);
        for (int digit = 0; digit <= 9; digit++) {
            if (isValidVkn(head + digit)) return head + digit;
        }
        return value;
    }

    public static boolean isValidTckn(String value) {
        if (value == null || value.length() != 11 || !value.chars().allMatch(Character::isDigit) || value.charAt(0) == '0') return false;
        int[] d = value.chars().map(c -> c - '0').toArray();
        int odd = d[0] + d[2] + d[4] + d[6] + d[8];
        int even = d[1] + d[3] + d[5] + d[7];
        int tenth = (((odd * 7 - even) % 10) + 10) % 10;
        int eleventh = 0;
        for (int i = 0; i < 10; i++) eleventh += d[i];
        eleventh %= 10;
        return d[9] == tenth && d[10] == eleventh;
    }

    public static boolean isValidTaxId(String value) {
        return value != null && !value.isEmpty() && (value.length() == 11 ? isValidTckn(value) : isValidVkn(value));
    }

    public static String taxIdScheme(String value) {
        return value.length() == 11 ? "TCKN" : "VKN";
    }

    public static String toGibNumber(String formatted) {
        return formatted.replace("-", "");
    }

    public static EInvoiceProfile resolveProfile(String receiverAlias, EInvoiceProfile requested, EInvoiceProfile fallback) {
        if (receiverAlias == null || receiverAlias.isEmpty()) {
            return requested == null || requested == EInvoiceProfile.EARSIVFATURA ? EInvoiceProfile.EARSIVFATURA : null;
        }
        EInvoiceProfile profile = requested == null ? fallback : requested;
        return profile == EInvoiceProfile.EARSIVFATURA ? null : profile;
    }

    private static final Map<String, String> UNIT_CODES = Map.of(
            "ADET", "C62", "KG", "KGM", "LT", "LTR", "M", "MTR", "M2", "MTK", "KOLI", "CT", "SAAT", "HUR");

    public static String unitCode(String unit) {
        return unit != null && UNIT_CODES.containsKey(unit) ? UNIT_CODES.get(unit) : "C62";
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String trimQty(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public static BuiltLines buildLines(List<EInvoiceLineInput> inputs) {
        List<DocumentMath.LineAmounts> amounts = inputs.stream()
                .map(i -> DocumentMath.line(new DocumentMath.LineInput(i.quantity(), i.unitPrice(), i.discountRate(), i.vatRate())))
                .toList();
        List<EInvoiceLine> lines = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            EInvoiceLineInput input = inputs.get(i);
            DocumentMath.LineAmounts a = amounts.get(i);
            lines.add(new EInvoiceLine(i + 1, input.description(), input.sellerCode(), input.variantId(), trimQty(input.quantity()), unitCode(input.unit()),
                    input.unitPrice().setScale(4, java.math.RoundingMode.HALF_UP).toPlainString(), money(input.discountRate()), money(a.gross()), money(a.discount()),
                    money(a.net()), money(input.vatRate()), money(a.vat())));
        }
        BigDecimal net = amounts.stream().map(DocumentMath.LineAmounts::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = amounts.stream().map(DocumentMath.LineAmounts::vat).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<BigDecimal, List<Integer>> byRate = new LinkedHashMap<>();
        for (int i = 0; i < inputs.size(); i++) byRate.computeIfAbsent(inputs.get(i).vatRate(), k -> new ArrayList<>()).add(i);
        List<VatBreakdown> taxes = byRate.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> {
            BigDecimal base = BigDecimal.ZERO;
            BigDecimal tax = BigDecimal.ZERO;
            for (int i : e.getValue()) {
                base = base.add(amounts.get(i).net());
                tax = tax.add(amounts.get(i).vat());
            }
            return new VatBreakdown(money(e.getKey()), money(base), money(tax));
        }).toList();
        BigDecimal allowance = amounts.stream().map(DocumentMath.LineAmounts::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BuiltLines(lines, new EInvoiceTotals(money(net), money(allowance), money(net), money(net.add(vat)), money(net.add(vat)), taxes));
    }

    public static List<String> validateForGib(ValidationInput input) {
        List<String> errors = new ArrayList<>();
        if (!GIB_NUMBER.matcher(input.number()).matches()) errors.add("Fatura numarası GİB formatına uymuyor: " + input.number());
        if (!isValidTaxId(input.supplierTaxId())) errors.add("Gönderici VKN/TCKN geçersiz");
        boolean eArchive = input.profile() == EInvoiceProfile.EARSIVFATURA;
        if (input.customerTaxId() == null || input.customerTaxId().isEmpty()) {
            if (!eArchive) errors.add("Alıcı VKN/TCKN zorunludur");
        } else if (!isValidTaxId(input.customerTaxId())) {
            errors.add("Alıcı VKN/TCKN geçersiz: " + input.customerTaxId());
        }
        if (!eArchive && (input.receiverAlias() == null || !ALIAS_PATTERN.matcher(input.receiverAlias()).matches())) {
            errors.add("Alıcı posta kutusu etiketi (urn:mail) geçersiz");
        }
        if (!"TRY".equals(input.currencyCode()) && input.exchangeRate().signum() <= 0) errors.add("Döviz kuru zorunludur");
        if (input.lines().isEmpty()) errors.add("Fatura en az bir satır içermelidir");
        for (EInvoiceLine line : input.lines()) {
            if (new BigDecimal(line.quantity()).signum() <= 0) errors.add("Satır " + line.lineNo() + ": miktar sıfırdan büyük olmalıdır");
        }
        BigDecimal total = input.lines().stream().map(l -> new BigDecimal(l.net()).add(new BigDecimal(l.vat()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(input.payable()) != 0) errors.add("Ödenecek tutar satır toplamlarıyla uyuşmuyor");
        return errors;
    }

    public static boolean canRespond(EInvoiceProfile profile, EInvoiceStatus status, Instant deliveredAt, Instant now) {
        return profile == EInvoiceProfile.TICARIFATURA && status == EInvoiceStatus.DELIVERED && deliveredAt != null
                && Duration.between(deliveredAt, now).compareTo(Duration.ofDays(RESPONSE_DAYS)) <= 0;
    }

    public static boolean responseExpired(EInvoiceProfile profile, EInvoiceStatus status, Instant deliveredAt, Instant now) {
        return profile == EInvoiceProfile.TICARIFATURA && status == EInvoiceStatus.DELIVERED && deliveredAt != null
                && Duration.between(deliveredAt, now).compareTo(Duration.ofDays(RESPONSE_DAYS)) > 0;
    }

    public static CancelCheck salesCancelCheck(EInvoiceProfile profile, EInvoiceStatus status) {
        if (profile == null || status == null) return new CancelCheck(true, false, null);
        return switch (status) {
            case QUEUED, SENT -> new CancelCheck(false, false, "e-Fatura gönderiliyor; GİB yanıtını bekleyin");
            case DELIVERED, ACCEPTED -> profile == EInvoiceProfile.EARSIVFATURA
                    ? new CancelCheck(true, true, null)
                    : new CancelCheck(false, false, "e-Fatura alıcıya ulaştı; yalnızca alıcı reddederse iptal edilebilir");
            default -> new CancelCheck(true, false, null);
        };
    }

    public static OutgoingStep nextOutgoingStep(EInvoiceProfile profile, EInvoiceStatus status, Instant deliveredAt, Supplier<List<String>> errors, Instant now) {
        if (status == EInvoiceStatus.QUEUED) {
            List<String> problems = errors.get();
            return problems.isEmpty()
                    ? new OutgoingStep(EInvoiceStatus.SENT, Codes.PROCESSED, "Zarf başarıyla işlendi")
                    : new OutgoingStep(EInvoiceStatus.ERROR, Codes.SCHEMA_ERROR, problems.stream().collect(Collectors.joining("; ")));
        }
        if (status == EInvoiceStatus.SENT) {
            return switch (profile) {
                case EARSIVFATURA -> new OutgoingStep(EInvoiceStatus.DELIVERED, Codes.COMPLETED, "e-Arşiv raporuna eklendi; alıcıya e-posta ile iletildi");
                case TICARIFATURA -> new OutgoingStep(EInvoiceStatus.DELIVERED, Codes.COMPLETED, "Alıcıya ulaştı; ticari fatura yanıtı bekleniyor");
                default -> new OutgoingStep(EInvoiceStatus.DELIVERED, Codes.COMPLETED, "Başarıyla tamamlandı");
            };
        }
        if (responseExpired(profile, status, deliveredAt, now)) {
            return new OutgoingStep(EInvoiceStatus.ACCEPTED, Codes.ACCEPTED, RESPONSE_DAYS + " gün içinde yanıt verilmedi; kabul edilmiş sayılır");
        }
        return null;
    }
}
