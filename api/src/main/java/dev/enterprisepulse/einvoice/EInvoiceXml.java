package dev.enterprisepulse.einvoice;

import static dev.enterprisepulse.einvoice.EInvoiceRules.EInvoiceLine;
import static dev.enterprisepulse.einvoice.EInvoiceRules.EInvoiceTotals;
import static dev.enterprisepulse.einvoice.EInvoiceRules.VatBreakdown;

import dev.enterprisepulse.data.generated.EInvoiceProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** UBL-TR 2.1 invoice (TR1.2 customisation); the digital signature is left to the integrator. */
public final class EInvoiceXml {
    private EInvoiceXml() {}

    public record UblParty(String taxId, String name, String taxOffice, String street, String city, String country, String email) {}

    public record OrderReference(String number, String date) {}

    public record UblInvoice(EInvoiceProfile profile, String number, UUID uuid, String issueDate, String issueTime, String currencyCode, String exchangeRate,
                             List<String> notes, UblParty supplier, UblParty customer, List<EInvoiceLine> lines, EInvoiceTotals totals, OrderReference orderReference) {}

    private static final Map<String, String> COUNTRIES = Map.of(
            "TR", "Türkiye", "DE", "Almanya", "SE", "İsveç", "US", "Amerika Birleşik Devletleri", "GB", "Birleşik Krallık");

    private static final String[][] NAMESPACES = {
            {"xmlns", "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"},
            {"xmlns:cac", "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"},
            {"xmlns:cbc", "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"},
            {"xmlns:ext", "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2"},
    };

    public static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(switch (c) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                case '\'' -> "&apos;";
                default -> String.valueOf(c);
            });
        }
        return sb.toString();
    }

    private static final class Writer {
        private final List<String> out = new ArrayList<>();
        private int depth;

        Writer open(String tag, String[]... attrs) {
            out.add(indent() + "<" + tag + attrs(attrs) + ">");
            depth++;
            return this;
        }

        Writer close(String tag) {
            depth--;
            out.add(indent() + "</" + tag + ">");
            return this;
        }

        Writer leaf(String tag, String value, String[]... attrs) {
            if (value == null || value.isEmpty()) return this;
            out.add(indent() + "<" + tag + attrs(attrs) + ">" + escape(value) + "</" + tag + ">");
            return this;
        }

        @Override public String toString() { return String.join("\n", out) + "\n"; }

        private String indent() { return " ".repeat(depth * 2); }

        private static String attrs(String[]... attrs) {
            StringBuilder sb = new StringBuilder();
            for (String[] a : attrs) sb.append(' ').append(a[0]).append("=\"").append(escape(a[1])).append('"');
            return sb.toString();
        }
    }

    private static String num(String value) {
        return new BigDecimal(value).stripTrailingZeros().toPlainString();
    }

    private static void party(Writer w, String tag, UblParty p, boolean eArchive) {
        String taxId = p.taxId() != null && !p.taxId().isEmpty() ? p.taxId() : eArchive ? EInvoiceRules.ANONYMOUS_TCKN : "";
        String scheme = EInvoiceRules.taxIdScheme(taxId);
        w.open(tag).open("cac:Party");
        w.open("cac:PartyIdentification").leaf("cbc:ID", taxId, new String[] {"schemeID", scheme}).close("cac:PartyIdentification");
        if ("VKN".equals(scheme)) w.open("cac:PartyName").leaf("cbc:Name", p.name()).close("cac:PartyName");
        w.open("cac:PostalAddress");
        w.leaf("cbc:StreetName", p.street());
        w.leaf("cbc:CitySubdivisionName", p.city());
        w.leaf("cbc:CityName", p.city() == null ? "-" : p.city());
        w.open("cac:Country").leaf("cbc:Name", COUNTRIES.getOrDefault(p.country(), p.country())).close("cac:Country");
        w.close("cac:PostalAddress");
        if (p.taxOffice() != null && !p.taxOffice().isEmpty()) {
            w.open("cac:PartyTaxScheme").open("cac:TaxScheme").leaf("cbc:Name", p.taxOffice()).close("cac:TaxScheme").close("cac:PartyTaxScheme");
        }
        if (p.email() != null && !p.email().isEmpty()) w.open("cac:Contact").leaf("cbc:ElectronicMail", p.email()).close("cac:Contact");
        if ("TCKN".equals(scheme)) {
            String[] parts = p.name().trim().split("\\s+");
            String family = parts[parts.length - 1];
            String first = parts.length > 1 ? String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1)) : parts[0];
            w.open("cac:Person").leaf("cbc:FirstName", first).leaf("cbc:FamilyName", family).close("cac:Person");
        }
        w.close("cac:Party").close(tag);
    }

    private static void vatSubtotal(Writer w, String[] currency, String taxable, String vat, String rate) {
        w.open("cac:TaxSubtotal");
        w.leaf("cbc:TaxableAmount", taxable, currency);
        w.leaf("cbc:TaxAmount", vat, currency);
        w.leaf("cbc:Percent", num(rate));
        w.open("cac:TaxCategory").open("cac:TaxScheme");
        w.leaf("cbc:Name", "KDV").leaf("cbc:TaxTypeCode", "0015");
        w.close("cac:TaxScheme").close("cac:TaxCategory");
        w.close("cac:TaxSubtotal");
    }

    public static String build(UblInvoice doc) {
        boolean eArchive = doc.profile() == EInvoiceProfile.EARSIVFATURA;
        String[] currency = {"currencyID", doc.currencyCode()};
        Writer w = new Writer();
        w.open("Invoice", NAMESPACES);
        w.open("ext:UBLExtensions").open("ext:UBLExtension").leaf("ext:ExtensionContent", "-").close("ext:UBLExtension").close("ext:UBLExtensions");
        w.leaf("cbc:UBLVersionID", "2.1");
        w.leaf("cbc:CustomizationID", "TR1.2");
        w.leaf("cbc:ProfileID", doc.profile().name());
        w.leaf("cbc:ID", doc.number());
        w.leaf("cbc:CopyIndicator", "false");
        w.leaf("cbc:UUID", doc.uuid().toString());
        w.leaf("cbc:IssueDate", doc.issueDate());
        w.leaf("cbc:IssueTime", doc.issueTime());
        w.leaf("cbc:InvoiceTypeCode", "SATIS");
        for (String note : doc.notes()) w.leaf("cbc:Note", note);
        w.leaf("cbc:DocumentCurrencyCode", doc.currencyCode());
        w.leaf("cbc:LineCountNumeric", Integer.toString(doc.lines().size()));
        if (doc.orderReference() != null) {
            w.open("cac:OrderReference");
            w.leaf("cbc:ID", doc.orderReference().number()).leaf("cbc:IssueDate", doc.orderReference().date());
            w.close("cac:OrderReference");
        }
        if (eArchive) {
            w.open("cac:AdditionalDocumentReference");
            w.leaf("cbc:ID", "ELEKTRONIK").leaf("cbc:IssueDate", doc.issueDate());
            w.leaf("cbc:DocumentTypeCode", "EREPSENDT");
            w.close("cac:AdditionalDocumentReference");
        }
        party(w, "cac:AccountingSupplierParty", doc.supplier(), false);
        party(w, "cac:AccountingCustomerParty", doc.customer(), eArchive);
        if (doc.exchangeRate() != null && !doc.exchangeRate().isEmpty() && !"TRY".equals(doc.currencyCode())) {
            w.open("cac:PricingExchangeRate");
            w.leaf("cbc:SourceCurrencyCode", doc.currencyCode()).leaf("cbc:TargetCurrencyCode", "TRY");
            w.leaf("cbc:CalculationRate", doc.exchangeRate()).leaf("cbc:Date", doc.issueDate());
            w.close("cac:PricingExchangeRate");
        }
        BigDecimal taxTotal = doc.totals().taxes().stream().map(t -> new BigDecimal(t.vat())).reduce(BigDecimal.ZERO, BigDecimal::add);
        w.open("cac:TaxTotal").leaf("cbc:TaxAmount", taxTotal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), currency);
        for (VatBreakdown t : doc.totals().taxes()) vatSubtotal(w, currency, t.base(), t.vat(), t.rate());
        w.close("cac:TaxTotal");
        w.open("cac:LegalMonetaryTotal");
        w.leaf("cbc:LineExtensionAmount", doc.totals().lineExtension(), currency);
        w.leaf("cbc:TaxExclusiveAmount", doc.totals().taxExclusive(), currency);
        w.leaf("cbc:TaxInclusiveAmount", doc.totals().taxInclusive(), currency);
        w.leaf("cbc:AllowanceTotalAmount", doc.totals().allowance(), currency);
        w.leaf("cbc:PayableAmount", doc.totals().payable(), currency);
        w.close("cac:LegalMonetaryTotal");
        for (EInvoiceLine line : doc.lines()) {
            w.open("cac:InvoiceLine");
            w.leaf("cbc:ID", Integer.toString(line.lineNo()));
            w.leaf("cbc:InvoicedQuantity", line.quantity(), new String[] {"unitCode", line.unitCode()});
            w.leaf("cbc:LineExtensionAmount", line.net(), currency);
            if (new BigDecimal(line.discount()).signum() > 0) {
                w.open("cac:AllowanceCharge");
                w.leaf("cbc:ChargeIndicator", "false");
                w.leaf("cbc:MultiplierFactorNumeric", new BigDecimal(line.discountRate()).movePointLeft(2).stripTrailingZeros().toPlainString());
                w.leaf("cbc:Amount", line.discount(), currency);
                w.leaf("cbc:BaseAmount", line.gross(), currency);
                w.close("cac:AllowanceCharge");
            }
            w.open("cac:TaxTotal").leaf("cbc:TaxAmount", line.vat(), currency);
            vatSubtotal(w, currency, line.net(), line.vat(), line.vatRate());
            w.close("cac:TaxTotal");
            w.open("cac:Item").leaf("cbc:Name", line.description());
            if (line.sellerCode() != null && !line.sellerCode().isEmpty()) {
                w.open("cac:SellersItemIdentification").leaf("cbc:ID", line.sellerCode()).close("cac:SellersItemIdentification");
            }
            w.close("cac:Item");
            w.open("cac:Price").leaf("cbc:PriceAmount", line.unitPrice(), currency).close("cac:Price");
            w.close("cac:InvoiceLine");
        }
        w.close("Invoice");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + w;
    }
}
