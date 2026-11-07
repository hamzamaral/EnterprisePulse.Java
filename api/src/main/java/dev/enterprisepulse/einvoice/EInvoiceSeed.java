package dev.enterprisepulse.einvoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.enterprisepulse.accounting.AccountingClock;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.Company;
import dev.enterprisepulse.data.generated.NumberSequence;
import dev.enterprisepulse.data.generated.Setting;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardProvider;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import dev.enterprisepulse.sales.SalesHooks;
import dev.enterprisepulse.seed.SeedStep;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

public final class EInvoiceSeed {
    private EInvoiceSeed() {}

    @Component public static class InvoicesHook extends SalesHooks.Managed {
        public InvoicesHook() { super("e-invoices", "e-Faturalar e-Fatura ekranından gönderilir ve yanıtlanır"); }
    }

    @Component public static class EventsHook extends SalesHooks.Managed {
        public EventsHook() { super("e-invoice-events", "e-Fatura olayları sistem tarafından yazılır"); }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Dashboard implements DashboardProvider {
        private final EInvoiceService einvoice;
        public Dashboard(EInvoiceService einvoice) { this.einvoice = einvoice; }
        @Override public int order() { return 65; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("einvoice:read")) return null;
            EInvoiceService.SummaryDto s = einvoice.summary();
            return new DashboardSection("einvoice", "e-Fatura", List.of(
                    new DashboardCard("einvoice-unsent", "Gönderilmemiş fatura", Integer.toString(s.unsent()), "Outbox", null, "/e-invoices", s.unsent() > 0 ? "warning" : "primary"),
                    new DashboardCard("einvoice-errors", "GİB hatası", Integer.toString(s.errors()), "ErrorOutline", null, "/r/e-invoices", s.errors() > 0 ? "error" : "primary"),
                    new DashboardCard("einvoice-respond", "Yanıt bekleyen gelen", Integer.toString(s.toRespond()), "MarkEmailUnread", null, "/e-invoices", s.toRespond() > 0 ? "warning" : "primary")));
        }
    }

    @Component
    public static class Seed implements SeedStep {
        private static final Map<String, String> ALIASES = Map.of(
                "MUS-001", "urn:mail:defaultpk@bogaziciperakende.com.tr",
                "MUS-003", "urn:mail:defaultpk@akdenizotelcilik.com.tr",
                "CAR-001", "urn:mail:defaultpk@karadenizticaret.com.tr",
                "TED-001", "urn:mail:defaultpk@anadolubilisim.com.tr",
                "TED-002", "urn:mail:defaultpk@egekirtasiye.com.tr",
                "TED-004", "urn:mail:defaultpk@marmaraambalaj.com.tr");
        private static final Set<String> KEEP_INVALID = Set.of("MUS-004");

        private final Db db;
        private final ObjectMapper json;

        public Seed(Db db, ObjectMapper json) {
            this.db = db;
            this.json = json;
        }

        @Override public int order() { return 82; }

        @Override
        public void run(Context ctx) {
            int year = AccountingClock.today().getYear();
            for (UUID companyId : List.of(ctx.pulseId, ctx.logisticsId)) {
                try (var scope = CurrentUser.get().actAs(companyId, ctx.adminId, true)) {
                    Company company = db.get(Company.class, companyId);
                    if (company.taxNumber == null || company.taxNumber.isEmpty()) {
                        company.taxNumber = companyId.equals(ctx.pulseId) ? "1234567890" : "9876543210";
                        if (company.taxOffice == null) company.taxOffice = company.city;
                    }
                    if (company.taxNumber != null && company.taxNumber.length() == 10 && !EInvoiceRules.isValidVkn(company.taxNumber)) {
                        company.taxNumber = EInvoiceRules.withVknCheckDigit(company.taxNumber);
                    }
                    for (BusinessPartner partner : db.list("select p from BusinessPartner p", BusinessPartner.class)) {
                        String tax = partner.taxNumber;
                        if (tax != null && tax.length() == 10 && !EInvoiceRules.isValidVkn(tax) && !KEEP_INVALID.contains(partner.code)) {
                            partner.taxNumber = EInvoiceRules.withVknCheckDigit(tax);
                        }
                        if ((partner.eInvoiceAlias == null || partner.eInvoiceAlias.isEmpty()) && ALIASES.containsKey(partner.code)) {
                            partner.eInvoiceAlias = ALIASES.get(partner.code);
                        }
                    }
                    ensureSequence(companyId, EInvoiceService.INVOICE_SEQUENCE, "e-Fatura", "EPF", year);
                    ensureSequence(companyId, EInvoiceService.ARCHIVE_SEQUENCE, "e-Arşiv Fatura", "EPA", year);
                    if (!db.exists("select s.id from Setting s where s.key = ?1", EInvoiceService.SETTINGS_KEY)) {
                        Setting setting = new Setting();
                        setting.companyId = companyId;
                        setting.key = EInvoiceService.SETTINGS_KEY;
                        ObjectNode value = json.createObjectNode();
                        value.put("senderAlias", "urn:mail:defaultgb@" + company.code.toLowerCase() + ".enterprisepulse.dev");
                        value.put("defaultProfile", "TICARIFATURA");
                        value.put("autoSend", false);
                        setting.value = value;
                        db.add(setting);
                    }
                    db.flush();
                }
            }
        }

        private void ensureSequence(UUID companyId, String code, String name, String prefix, int year) {
            NumberSequence row = db.first("select n from NumberSequence n where n.code = ?1", NumberSequence.class, code).orElse(null);
            if (row == null) {
                row = new NumberSequence();
                row.companyId = companyId;
                row.code = code;
                row.name = name;
                row.prefix = prefix;
                row.padding = 9;
                row.currentYear = year;
                db.add(row);
            } else if (row.nextValue == 1) {
                row.padding = 9;
            }
        }
    }
}
