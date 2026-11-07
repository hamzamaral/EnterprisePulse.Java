package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountingApiTests extends ApiTestSupport {
    private static String today() {
        return LocalDate.now().toString();
    }

    private JsonNode ok(Res res) {
        assertThat(res.status()).as(res.status() + ": " + res.body()).isBetween(200, 299);
        return res.body();
    }

    private JsonNode list(String token, String url) {
        return get(url, token).body().path("data");
    }

    private JsonNode array(String token, String url) {
        return get(url, token).body();
    }

    private static BigDecimal dec(JsonNode node) {
        return node.decimalValue();
    }

    private JsonNode account(JsonNode accounts, String code) {
        for (JsonNode a : accounts) if (code.equals(a.path("code").asText())) return a;
        return null;
    }

    @Test
    void chartOfAccountsAndManualEntriesAreSeeded() {
        String admin = admin();
        JsonNode accounts = array(admin, "/api/ledger-accounts");
        assertThat(accounts.size()).isGreaterThanOrEqualTo(60);
        assertThat(account(accounts, "100").path("isSystem").asBoolean()).isTrue();
        assertThat(account(accounts, "120").path("type").asText()).isEqualTo("ASSET");
        assertThat(account(accounts, "320").path("type").asText()).isEqualTo("LIABILITY");
        assertThat(account(accounts, "600").path("type").asText()).isEqualTo("INCOME");
        assertThat(account(accounts, "770").path("type").asText()).isEqualTo("EXPENSE");

        JsonNode entries = list(admin, "/api/r/journal-entries?pageSize=100");
        long posted = 0, draft = 0;
        for (JsonNode e : entries) {
            if ("POSTED".equals(e.path("status").asText())) posted++;
            if ("DRAFT".equals(e.path("status").asText())) draft++;
        }
        assertThat(posted).isGreaterThanOrEqualTo(3);
        assertThat(draft).isGreaterThanOrEqualTo(1);
        assertThat(post("/api/r/journal-entries", Map.of(), admin).status()).isEqualTo(409);
        assertThat(post("/api/r/ledger-accounts", Map.of(), admin).status()).isEqualTo(409);

        JsonNode created = ok(post("/api/ledger-accounts", Map.of("code", "120.77", "name", "Test müşterileri"), admin));
        assertThat(created.path("type").asText()).isEqualTo("ASSET");
        assertThat(post("/api/ledger-accounts", Map.of("code", "ABC", "name", "Geçersiz"), admin).status()).isEqualTo(400);
        assertThat(post("/api/ledger-accounts", Map.of("code", "120.77", "name", "Yinelenen"), admin).status()).isIn(409, 400);
        assertThat(call("DELETE", "/api/ledger-accounts/" + created.path("id").asText(), null, admin, null).status()).isEqualTo(204);
        assertThat(call("DELETE", "/api/ledger-accounts/" + account(accounts, "100").path("id").asText(), null, admin, null).status()).isEqualTo(409);

        String employee = employee();
        assertThat(get("/api/accounting/summary", employee).status()).isEqualTo(403);
    }

    @Test
    void postingRunCreatesBalancedEntriesAndStatements() {
        String admin = admin();
        JsonNode pending = array(admin, "/api/accounting/pending");
        assertThat(pending.size()).isGreaterThan(0);
        boolean sales = false, production = false;
        for (JsonNode p : pending) {
            if ("SALES_INVOICE".equals(p.path("source").asText())) sales = true;
            if ("PRODUCTION".equals(p.path("source").asText())) production = true;
        }
        assertThat(sales).isTrue();
        assertThat(production).isTrue();

        JsonNode run = ok(post("/api/accounting/run", null, admin));
        assertThat(run.path("posted").asInt()).isGreaterThan(0);
        JsonNode again = ok(post("/api/accounting/run", null, admin));
        assertThat(again.path("posted").asInt()).isEqualTo(0);
        assertThat(again.path("reversed").asInt()).isEqualTo(0);
        assertThat(array(admin, "/api/accounting/pending").size()).isEqualTo(0);

        JsonNode entries = list(admin, "/api/r/journal-entries?pageSize=100");
        JsonNode salesEntry = null, productionEntry = null;
        for (JsonNode e : entries) {
            if ("SALES_INVOICE".equals(e.path("source").asText()) && salesEntry == null) salesEntry = e;
            if ("PRODUCTION".equals(e.path("source").asText()) && productionEntry == null) productionEntry = e;
        }
        assertThat(salesEntry).isNotNull();
        JsonNode detail = get("/api/journal-entries/" + salesEntry.path("id").asText(), admin).body();
        assertThat(detail.path("status").asText()).isEqualTo("POSTED");
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        boolean has120 = false, has600 = false;
        for (JsonNode l : detail.path("lines")) {
            debit = debit.add(dec(l.path("debit")));
            credit = credit.add(dec(l.path("credit")));
            String code = l.path("accountCode").asText();
            if (code.startsWith("120")) has120 = true;
            if (code.startsWith("600")) has600 = true;
        }
        assertThat(debit).isEqualByComparingTo(credit);
        assertThat(has120).isTrue();
        assertThat(has600).isTrue();
        assertThat(detail.path("canEdit").asBoolean()).isFalse();

        assertThat(productionEntry).isNotNull();
        JsonNode productionLines = get("/api/journal-entries/" + productionEntry.path("id").asText(), admin).body().path("lines");
        BigDecimal pDebit = BigDecimal.ZERO, pCredit = BigDecimal.ZERO;
        boolean hasStock = false;
        for (JsonNode l : productionLines) {
            pDebit = pDebit.add(dec(l.path("debit")));
            pCredit = pCredit.add(dec(l.path("credit")));
            String code = l.path("accountCode").asText();
            if (code.startsWith("152") || code.startsWith("153")) hasStock = true;
        }
        assertThat(hasStock).isTrue();
        assertThat(pDebit).isEqualByComparingTo(pCredit);

        JsonNode trial = get("/api/accounting/trial-balance", admin).body();
        JsonNode totals = trial.path("totals");
        assertThat(dec(totals.path("totalDebit"))).isGreaterThan(BigDecimal.ZERO);
        assertThat(dec(totals.path("totalDebit"))).isEqualByComparingTo(dec(totals.path("totalCredit")));
        assertThat(dec(totals.path("periodDebit"))).isEqualByComparingTo(dec(totals.path("periodCredit")));
        assertThat(dec(totals.path("balanceDebit"))).isEqualByComparingTo(dec(totals.path("balanceCredit")));

        JsonNode sheet = get("/api/accounting/balance-sheet", admin).body();
        assertThat(dec(sheet.path("totalAssets"))).isGreaterThan(BigDecimal.ZERO);
        assertThat(dec(sheet.path("totalAssets"))).isEqualByComparingTo(dec(sheet.path("totalLiabilities")));

        JsonNode income = get("/api/accounting/income-statement", admin).body();
        assertThat(dec(income.path("netSales"))).isGreaterThan(BigDecimal.ZERO);
        assertThat(income.path("sections").size()).isGreaterThan(0);

        JsonNode summary = get("/api/accounting/summary", admin).body();
        assertThat(summary.path("chartInstalled").asBoolean()).isTrue();
        assertThat(summary.path("pendingCount").asInt()).isEqualTo(0);
        assertThat(summary.path("months").size()).isEqualTo(12);
    }

    @Test
    void manualEntriesValidatePostReverseAndRespectThePeriodLock() {
        String admin = admin();
        JsonNode accounts = array(admin, "/api/ledger-accounts");
        String bank = account(accounts, "102").path("id").asText();
        String expense = account(accounts, "770").path("id").asText();

        JsonNode unbalanced = ok(post("/api/journal-entries", Map.of(
                "entryDate", today(), "description", "Dengesiz",
                "lines", List.of(
                        Map.of("accountId", expense, "debit", 100, "credit", 0),
                        Map.of("accountId", bank, "debit", 0, "credit", 90))), admin));
        assertThat(unbalanced.path("canPost").asBoolean()).isFalse();
        assertThat(post("/api/journal-entries/" + unbalanced.path("id").asText() + "/post", null, admin).status()).isEqualTo(400);
        assertThat(call("DELETE", "/api/journal-entries/" + unbalanced.path("id").asText(), null, admin, null).status()).isEqualTo(204);

        JsonNode draft = ok(post("/api/journal-entries", Map.of(
                "entryDate", today(), "description", "Test gideri",
                "lines", List.of(
                        Map.of("accountId", expense, "debit", 250, "credit", 0),
                        Map.of("accountId", bank, "debit", 0, "credit", 250))), admin));
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("number").asText()).startsWith("YEV-");
        String id = draft.path("id").asText();

        JsonNode updated = ok(call("PATCH", "/api/journal-entries/" + id, Map.of(
                "entryDate", today(), "description", "Test gideri (düzeltildi)",
                "lines", List.of(
                        Map.of("accountId", expense, "debit", 300, "credit", 0),
                        Map.of("accountId", bank, "debit", 0, "credit", 300))), admin, null));
        assertThat(dec(updated.path("total"))).isEqualByComparingTo("300");

        JsonNode posted = ok(post("/api/journal-entries/" + id + "/post", null, admin));
        assertThat(posted.path("status").asText()).isEqualTo("POSTED");
        assertThat(posted.path("canEdit").asBoolean()).isFalse();
        assertThat(call("DELETE", "/api/journal-entries/" + id, null, admin, null).status()).isEqualTo(409);

        JsonNode reversal = ok(post("/api/journal-entries/" + id + "/reverse", Map.of("date", today(), "note", "Hatalı giriş"), admin));
        assertThat(reversal.path("isReversal").asBoolean()).isTrue();
        assertThat(reversal.path("reverses").path("id").asText()).isEqualTo(id);
        JsonNode original = get("/api/journal-entries/" + id, admin).body();
        assertThat(original.path("isReversed").asBoolean()).isTrue();
        assertThat(post("/api/journal-entries/" + id + "/reverse", Map.of("date", today()), admin).status()).isEqualTo(409);

        JsonNode another = ok(post("/api/journal-entries", Map.of(
                "entryDate", today(), "description", "Silinecek",
                "lines", List.of(
                        Map.of("accountId", expense, "debit", 10, "credit", 0),
                        Map.of("accountId", bank, "debit", 0, "credit", 10))), admin));
        assertThat(call("DELETE", "/api/journal-entries/" + another.path("id").asText(), null, admin, null).status()).isEqualTo(204);

        JsonNode lockedDraft = ok(post("/api/journal-entries", Map.of(
                "entryDate", today(), "description", "Kilitli dönem",
                "lines", List.of(
                        Map.of("accountId", expense, "debit", 5, "credit", 0),
                        Map.of("accountId", bank, "debit", 0, "credit", 5))), admin));
        ok(call("PATCH", "/api/accounting/settings", Map.of("lockedUntil", today()), admin, null));
        try {
            assertThat(post("/api/journal-entries/" + lockedDraft.path("id").asText() + "/post", null, admin).status()).isEqualTo(400);
            assertThat(get("/api/journal-entries/" + lockedDraft.path("id").asText(), admin).body().path("locked").asBoolean()).isTrue();
        } finally {
            Map<String, Object> clear = new HashMap<>();
            clear.put("lockedUntil", null);
            ok(call("PATCH", "/api/accounting/settings", clear, admin, null));
        }
        assertThat(call("DELETE", "/api/journal-entries/" + lockedDraft.path("id").asText(), null, admin, null).status()).isEqualTo(204);
        assertThat(get("/api/accounting/settings", admin).body().path("lockedUntil").isNull()).isTrue();
    }

    @Test
    void outgoingEinvoicesAreQueuedValidatedAndDelivered() {
        String admin = admin();
        JsonNode unsent = array(admin, "/api/einvoices/unsent");
        assertThat(unsent.size()).isGreaterThan(0);
        List<JsonNode> sent = new ArrayList<>();
        int n = 0;
        for (JsonNode invoice : unsent) {
            if (n++ >= 5) break;
            Res res = post("/api/einvoices", Map.of("salesInvoiceId", invoice.path("id").asText()), admin);
            if (res.status() < 200 || res.status() >= 300) continue;
            JsonNode created = res.body();
            assertThat(created.path("status").asText()).isEqualTo("QUEUED");
            assertThat(created.path("number").asText()).matches("^[A-Z0-9]{3}\\d{4}\\d{9}$");
            assertThat(created.path("xml").asText()).contains("<Invoice");
            assertThat(created.path("xml").asText()).contains(created.path("number").asText());
            assertThat(created.path("events").size()).isEqualTo(1);
            sent.add(created);
        }
        assertThat(sent).isNotEmpty();
        assertThat(post("/api/einvoices", Map.of("salesInvoiceId", sent.get(0).path("salesInvoiceId").asText()), admin).status()).isEqualTo(409);

        assertThat(ok(post("/api/einvoices/sync", null, admin)).path("processed").asInt()).isGreaterThanOrEqualTo(sent.size());
        ok(post("/api/einvoices/sync", null, admin));
        List<JsonNode> states = new ArrayList<>();
        for (JsonNode s : sent) states.add(get("/api/einvoices/" + s.path("id").asText(), admin).body());
        assertThat(states.stream().anyMatch(s -> "DELIVERED".equals(s.path("status").asText()))).isTrue();
        for (JsonNode s : states) assertThat(s.path("status").asText()).isIn("DELIVERED", "ERROR");
        JsonNode delivered = states.stream().filter(s -> "DELIVERED".equals(s.path("status").asText())).findFirst().orElseThrow();
        assertThat(delivered.path("events").size()).isGreaterThanOrEqualTo(3);

        if ("TICARIFATURA".equals(delivered.path("profile").asText())) {
            assertThat(post("/api/sales/invoices/" + delivered.path("salesInvoiceId").asText() + "/cancel", Map.of("text", "Hata"), admin).status()).isEqualTo(409);
            assertThat(post("/api/einvoices/" + delivered.path("id").asText() + "/simulate-response", Map.of("action", "REJECT"), admin).status()).isEqualTo(400);
            JsonNode rejected = ok(post("/api/einvoices/" + delivered.path("id").asText() + "/simulate-response",
                    Map.of("action", "REJECT", "note", "Fiyat uyuşmazlığı"), admin));
            assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        } else {
            Res cancelRes = post("/api/sales/invoices/" + delivered.path("salesInvoiceId").asText() + "/cancel", Map.of("text", "Hata"), admin);
            if (cancelRes.status() != 409) {
                JsonNode cancelled = ok(cancelRes);
                assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
                assertThat(get("/api/einvoices/" + delivered.path("id").asText(), admin).body().path("status").asText()).isEqualTo("CANCELLED");
            }
        }

        JsonNode summary = get("/api/einvoices/summary", admin).body();
        assertThat(summary.path("unsent").asInt()).isEqualTo(unsent.size() - sent.size());
        assertThat(post("/api/r/e-invoices", Map.of(), admin).status()).isEqualTo(409);
    }

    @Test
    void incomingEinvoiceIsAnsweredAndRecordedAsPurchaseInvoice() {
        String admin = admin();
        JsonNode incoming = ok(post("/api/einvoices/simulate-incoming", Map.of(), admin));
        String id = incoming.path("id").asText();
        assertThat(incoming.path("direction").asText()).isEqualTo("INCOMING");
        assertThat(incoming.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(incoming.path("canRespond").asBoolean()).isTrue();
        assertThat(incoming.path("number").asText()).matches("^[A-Z0-9]{3}\\d{4}\\d{9}$");

        assertThat(post("/api/einvoices/" + id + "/record", null, admin).status()).isEqualTo(409);
        assertThat(post("/api/einvoices/" + id + "/respond", Map.of("action", "REJECT"), admin).status()).isEqualTo(400);
        JsonNode accepted = ok(post("/api/einvoices/" + id + "/respond", Map.of("action", "ACCEPT"), admin));
        assertThat(accepted.path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(post("/api/einvoices/" + id + "/respond", Map.of("action", "ACCEPT"), admin).status()).isEqualTo(409);

        JsonNode recorded = ok(post("/api/einvoices/" + id + "/record", null, admin));
        assertThat(recorded.path("purchaseInvoiceId").isNull()).isFalse();
        assertThat(recorded.path("canRecord").asBoolean()).isFalse();
        assertThat(post("/api/einvoices/" + id + "/record", null, admin).status()).isEqualTo(409);

        JsonNode second = ok(post("/api/einvoices/simulate-incoming", Map.of(), admin));
        JsonNode rejected = ok(post("/api/einvoices/" + second.path("id").asText() + "/respond",
                Map.of("action", "REJECT", "note", "Sipariş edilmedi"), admin));
        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(post("/api/einvoices/" + second.path("id").asText() + "/record", null, admin).status()).isEqualTo(409);

        JsonNode settings = ok(call("PATCH", "/api/einvoices/settings", Map.of("autoSend", true), admin, null));
        assertThat(settings.path("autoSend").asBoolean()).isTrue();
        ok(call("PATCH", "/api/einvoices/settings", Map.of("autoSend", false), admin, null));
    }

    @Test
    void billsOfMaterialsAndWorkOrdersAreSeeded() {
        String admin = admin();
        JsonNode boms = list(admin, "/api/r/bill-of-materials?pageSize=100");
        assertThat(boms.size()).isGreaterThanOrEqualTo(3);
        JsonNode orders = list(admin, "/api/r/work-orders?pageSize=100");
        for (String status : List.of("DRAFT", "RELEASED", "IN_PROGRESS", "COMPLETED")) {
            assertThat(orders.toString()).contains("\"status\":\"" + status + "\"");
        }
        assertThat(post("/api/r/work-orders", Map.of(), admin).status()).isEqualTo(409);
        assertThat(post("/api/r/bill-of-materials", Map.of(), admin).status()).isEqualTo(409);

        JsonNode kit = null;
        for (JsonNode b : boms) if ("PRD-KIT-R1".equals(b.path("code").asText())) kit = b;
        assertThat(kit).isNotNull();
        JsonNode kitDetail = get("/api/production/boms/" + kit.path("id").asText(), admin).body();
        assertThat(kitDetail.path("isDefault").asBoolean()).isTrue();
        assertThat(kitDetail.path("lines").toString()).contains("\"made\":true");
        assertThat(dec(kitDetail.path("unitCost"))).isGreaterThan(new BigDecimal("160"));

        JsonNode plan = get("/api/production/boms/" + kit.path("id").asText() + "/plan?quantity=12", admin).body();
        JsonNode planLines = plan.path("lines");
        boolean hasStation = false, hasMonitor = false;
        BigDecimal polo = null, monitor = null;
        for (JsonNode l : planLines) {
            if ("PRD-ISTASYON".equals(l.path("sku").asText()) && l.path("level").asInt() == 1) hasStation = true;
            if ("PRD-MONITOR".equals(l.path("sku").asText()) && l.path("level").asInt() == 2) {
                hasMonitor = true;
                monitor = dec(l.path("required"));
            }
            if ("PRD-POLO-LCV-M".equals(l.path("sku").asText())) polo = dec(l.path("required"));
        }
        assertThat(hasStation).isTrue();
        assertThat(hasMonitor).isTrue();
        assertThat(polo).isEqualByComparingTo("12");
        assertThat(monitor).isEqualByComparingTo("7");
        assertThat(dec(plan.path("totalCost"))).isGreaterThan(BigDecimal.ZERO);

        JsonNode catering = null;
        for (JsonNode b : boms) if ("PRD-IKRAM-R1".equals(b.path("code").asText())) catering = b;
        assertThat(catering).isNotNull();
        JsonNode coffee = null;
        for (JsonNode l : get("/api/production/boms/" + catering.path("id").asText(), admin).body().path("lines")) {
            if ("PRD-KAHVE".equals(l.path("variant").path("sku").asText())) coffee = l;
        }
        assertThat(dec(coffee.path("quantityPer"))).isEqualByComparingTo("0.52");

        JsonNode done = null;
        for (JsonNode o : orders) if ("Bilgi İşlem talebi #131".equals(o.path("reference").asText())) done = o;
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
        JsonNode detail = get("/api/production/work-orders/" + done.path("id").asText(), admin).body();
        assertThat(dec(detail.path("order").path("producedQty"))).isEqualByComparingTo("3");
        assertThat(detail.path("records").size()).isEqualTo(1);
        for (JsonNode c : detail.path("components")) {
            assertThat(dec(c.path("requiredQty"))).isEqualByComparingTo(dec(c.path("consumedQty")));
        }
        JsonNode record = detail.path("records").get(0);
        assertThat(dec(record.path("materialCost"))).isGreaterThan(BigDecimal.ZERO);
        assertThat(dec(record.path("overheadCost"))).isEqualByComparingTo("225");
        assertThat(dec(record.path("laborCost"))).isEqualByComparingTo("750");
        BigDecimal expectedUnit = dec(record.path("materialCost")).add(new BigDecimal("750")).add(new BigDecimal("225"))
                .divide(new BigDecimal("3"), 4, java.math.RoundingMode.HALF_UP);
        assertThat(dec(record.path("unitCost"))).isEqualByComparingTo(expectedUnit);
        assertThat(dec(detail.path("actualUnitCost"))).isGreaterThan(BigDecimal.ZERO);
        assertThat(detail.path("canRecord").asBoolean()).isFalse();

        JsonNode summary = get("/api/production/summary", admin).body();
        assertThat(summary.path("activeBoms").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(summary.path("draft").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(summary.path("released").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(summary.path("inProgress").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(summary.path("late").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode requirements = array(admin, "/api/production/requirements");
        assertThat(requirements.size()).isGreaterThan(0);
        assertThat(requirements.toString()).contains("PRD-ISTASYON");
        for (JsonNode r : requirements) {
            assertThat(dec(r.path("productionDemand")).add(dec(r.path("salesDemand")))).isEqualByComparingTo(dec(r.path("gross")));
        }

        String employee = employee();
        assertThat(post("/api/production/work-orders/" + done.path("id").asText() + "/release", Map.of(), employee).status()).isEqualTo(403);
    }

    @Test
    void workOrderLifecycleRecordsOutputAndPostsStock() {
        String admin = admin();
        JsonNode boms = list(admin, "/api/r/bill-of-materials?pageSize=100");
        JsonNode catering = null;
        for (JsonNode b : boms) if ("PRD-IKRAM-R1".equals(b.path("code").asText())) catering = b;
        JsonNode warehouse = list(admin, "/api/r/warehouses?code=MRK").get(0);
        String wh = warehouse.path("id").asText();

        java.util.function.Function<String, BigDecimal> stock = sku -> {
            BigDecimal total = BigDecimal.ZERO;
            for (JsonNode l : list(admin, "/api/stock/levels?search=" + sku)) {
                if (sku.equals(l.path("sku").asText())) total = total.add(dec(l.path("quantity")));
            }
            return total;
        };

        JsonNode order = ok(post("/api/production/work-orders", Map.of(
                "bomId", catering.path("id").asText(), "quantity", 10, "plannedStart", today(), "plannedEnd", today(),
                "sourceWarehouseId", wh, "targetWarehouseId", wh, "reference", "Test"), admin));
        String id = order.path("order").path("id").asText();
        assertThat(order.path("order").path("status").asText()).isEqualTo("DRAFT");
        assertThat(order.path("order").path("number").asText()).startsWith("IEM-");
        assertThat(post("/api/production/work-orders/" + id + "/records", Map.of("quantity", 1), admin).status()).isEqualTo(409);

        JsonNode edited = ok(call("PATCH", "/api/production/work-orders/" + id, Map.of("quantity", 20), admin, null));
        JsonNode water = null;
        for (JsonNode c : edited.path("components")) if ("PRD-SU".equals(c.path("variant").path("sku").asText())) water = c;
        assertThat(dec(water.path("requiredQty"))).isEqualByComparingTo("40");
        ok(call("PATCH", "/api/production/work-orders/" + id, Map.of("quantity", 10), admin, null));

        JsonNode released = ok(post("/api/production/work-orders/" + id + "/release", null, admin));
        assertThat(released.path("order").path("status").asText()).isEqualTo("RELEASED");
        assertThat(call("PATCH", "/api/production/work-orders/" + id, Map.of("quantity", 5), admin, null).status()).isEqualTo(409);

        BigDecimal packagesBefore = stock.apply("PRD-IKRAM");
        BigDecimal waterBefore = stock.apply("PRD-SU");
        BigDecimal coffeeBefore = stock.apply("PRD-KAHVE");
        assertThat(post("/api/production/work-orders/" + id + "/records", Map.of("quantity", 11), admin).status()).isEqualTo(400);

        JsonNode partial = ok(post("/api/production/work-orders/" + id + "/records", Map.of("quantity", 4, "recordDate", today()), admin));
        assertThat(partial.path("order").path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(dec(partial.path("order").path("producedQty"))).isEqualByComparingTo("4");
        assertThat(partial.path("order").path("progress").asInt()).isEqualTo(40);
        assertThat(partial.path("canCancel").asBoolean()).isFalse();
        assertThat(stock.apply("PRD-IKRAM")).isEqualByComparingTo(packagesBefore.add(new BigDecimal("4")));
        assertThat(stock.apply("PRD-SU")).isEqualByComparingTo(waterBefore.subtract(new BigDecimal("8")));
        assertThat(stock.apply("PRD-KAHVE")).isEqualByComparingTo(coffeeBefore.subtract(new BigDecimal("2.08")));
        JsonNode first = partial.path("records").get(0);
        assertThat(dec(first.path("unitCost"))).isGreaterThan(BigDecimal.ZERO);
        assertThat(first.path("number").asText()).startsWith("URT-");
        assertThat(first.path("issueMovementId").isNull()).isFalse();

        JsonNode finished = ok(post("/api/production/work-orders/" + id + "/records", Map.of("quantity", 6), admin));
        assertThat(finished.path("order").path("status").asText()).isEqualTo("COMPLETED");
        for (JsonNode c : finished.path("components")) {
            assertThat(dec(c.path("requiredQty"))).isEqualByComparingTo(dec(c.path("consumedQty")));
        }
        assertThat(finished.path("records").size()).isEqualTo(2);
        assertThat(stock.apply("PRD-IKRAM")).isEqualByComparingTo(packagesBefore.add(new BigDecimal("10")));
        assertThat(post("/api/production/work-orders/" + id + "/records", Map.of("quantity", 1), admin).status()).isEqualTo(409);

        JsonNode spare = ok(post("/api/production/work-orders", Map.of(
                "bomId", catering.path("id").asText(), "quantity", 10, "plannedStart", today(), "plannedEnd", today(),
                "sourceWarehouseId", wh, "targetWarehouseId", wh), admin));
        JsonNode cancelled = ok(post("/api/production/work-orders/" + spare.path("order").path("id").asText() + "/cancel", null, admin));
        assertThat(cancelled.path("order").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(post("/api/production/work-orders/" + spare.path("order").path("id").asText() + "/release", null, admin).status()).isEqualTo(409);

        JsonNode bomDetail = get("/api/production/boms/" + catering.path("id").asText(), admin).body();
        assertThat(bomDetail.path("canDelete").asBoolean()).isFalse();
        assertThat(call("DELETE", "/api/production/boms/" + catering.path("id").asText(), null, admin, null).status()).isEqualTo(409);

        JsonNode station = null;
        for (JsonNode b : boms) if ("PRD-ISTASYON-R1".equals(b.path("code").asText())) station = b;
        JsonNode stationDetail = get("/api/production/boms/" + station.path("id").asText(), admin).body();
        String kitVariant = null;
        for (JsonNode i : array(admin, "/api/stock/items?search=PRD-KIT")) {
            if ("PRD-KIT".equals(i.path("sku").asText())) kitVariant = i.path("variantId").asText();
        }
        assertThat(call("PATCH", "/api/production/boms/" + station.path("id").asText(),
                Map.of("lines", List.of(Map.of("variantId", kitVariant, "quantity", 1))), admin, null).status()).isEqualTo(400);
        assertThat(stationDetail.path("lines").size()).isEqualTo(3);

        String monitor = null;
        for (JsonNode i : array(admin, "/api/stock/items?search=PRD-MONITOR")) {
            if ("PRD-MONITOR".equals(i.path("sku").asText())) monitor = i.path("variantId").asText();
        }
        JsonNode request = ok(post("/api/production/requirements/purchase-request",
                Map.of("lines", List.of(Map.of("variantId", monitor, "quantity", 5))), admin));
        assertThat(request.path("number").asText()).isNotBlank();
    }
}
