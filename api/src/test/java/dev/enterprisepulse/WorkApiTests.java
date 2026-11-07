package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkApiTests extends ApiTestSupport {
    private static String today() { return LocalDate.now().toString(); }

    private JsonNode ok(Res res) {
        assertThat(res.status()).as(res.status() + ": " + res.body()).isBetween(200, 299);
        return res.body();
    }

    @Test
    void leadConvertsExpenseHitsProjectAndTimeIsCosted() {
        String admin = admin();
        String code = "CRM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String category = ok(post("/api/r/expense-categories", Map.of("code", code, "name", "Test fiş", "kind", "RECEIPT", "requiresReceipt", true, "isActive", true), admin)).path("data").path("id").asText();
        String perDiem = ok(post("/api/r/expense-categories", Map.of("code", code + "-H", "name", "Test harcırah", "kind", "PER_DIEM", "rate", 100, "requiresReceipt", false, "isActive", true), admin)).path("data").path("id").asText();
        JsonNode deniz = null;
        for (JsonNode person : get("/api/r/employees?pageSize=100", admin).body().path("data")) {
            if ("Deniz".equals(person.path("firstName").asText()) && "Aksoy".equals(person.path("lastName").asText())) deniz = person;
        }
        assertThat(deniz).isNotNull();
        String managerId = deniz.path("id").asText();
        JsonNode project = ok(post("/api/projects", Map.of("name", code, "managerId", managerId, "budgetAmount", 1000, "managerHourlyCost", 50), admin));
        ok(post("/api/projects/" + project.path("id").asText() + "/status", Map.of("status", "ACTIVE"), admin));

        JsonNode draft = ok(post("/api/expenses/reports", Map.of("employeeId", managerId, "title", code, "lines",
                List.of(Map.of("categoryId", category, "expenseDate", today(), "description", "Fişsiz", "amount", 10))), admin));
        assertThat(post("/api/expenses/reports/" + draft.path("id").asText() + "/submit", Map.of(), admin).status()).isEqualTo(400);

        JsonNode report = ok(post("/api/expenses/reports", Map.of("employeeId", managerId, "projectId", project.path("id").asText(), "title", code + " harcırah", "lines",
                List.of(Map.of("categoryId", perDiem, "expenseDate", today(), "description", "İki gün", "quantity", 2))), admin));
        assertThat(report.path("totalAmount").decimalValue()).isEqualByComparingTo("200");
        JsonNode submitted = ok(post("/api/expenses/reports/" + report.path("id").asText() + "/submit", Map.of(), admin));
        assertThat(submitted.path("status").asText()).isEqualTo("PENDING");
        ok(post("/api/workflow/instances/" + submitted.path("workflowInstanceId").asText() + "/approve", Map.of("comment", "uygun"), admin));

        String empty = ok(post("/api/r/treasury-accounts", Map.of("code", code, "name", "Test kasa", "type", "CASH", "currencyCode", "TRY"), admin)).path("data").path("id").asText();
        assertThat(post("/api/expenses/reports/" + report.path("id").asText() + "/pay", Map.of("accountId", empty), admin).status()).isEqualTo(400);
        String kasa = get("/api/r/treasury-accounts?code=KASA-MRK", admin).body().path("data").get(0).path("id").asText();
        JsonNode paid = ok(post("/api/expenses/reports/" + report.path("id").asText() + "/pay", Map.of("accountId", kasa), admin));
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        JsonNode costed = ok(get("/api/projects/" + project.path("id").asText(), admin));
        assertThat(costed.path("expenseCost").decimalValue()).isEqualByComparingTo("200");

        JsonNode lead = ok(post("/api/crm/leads", Map.of("companyName", code, "source", "WEBSITE", "estimatedValue", 80, "currencyCode", "TRY"), admin));
        JsonNode converted = ok(post("/api/crm/leads/" + lead.path("id").asText() + "/convert", Map.of("createOpportunity", true, "amount", 80), admin));
        assertThat(converted.path("status").asText()).isEqualTo("CONVERTED");
        assertThat(converted.path("partnerId").asText()).isNotBlank();
        assertThat(post("/api/r/leads", Map.of(), admin).status()).isEqualTo(409);
        assertThat(post("/api/r/projects", Map.of(), admin).status()).isEqualTo(409);

        JsonNode timed = ok(post("/api/projects/" + project.path("id").asText() + "/time", Map.of("employeeId", managerId, "workDate", today(), "hours", 3), admin));
        assertThat(timed.path("time").get(0).path("costAmount").decimalValue()).isEqualByComparingTo("150");
    }
}
