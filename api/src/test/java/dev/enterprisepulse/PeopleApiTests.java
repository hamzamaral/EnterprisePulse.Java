package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PeopleApiTests extends ApiTestSupport {
    private JsonNode ok(Res res) {
        assertThat(res.status()).as(res.status() + ": " + res.body()).isBetween(200, 299);
        return res.body();
    }

    @Test
    void seededPayrollHireReviewAndAugustPay() {
        String admin = admin();
        JsonNode october = null;
        for (JsonNode period : get("/api/r/payroll-periods", admin).body().path("data")) {
            if (period.path("month").asInt() == 10) october = period;
        }
        assertThat(october).isNotNull();
        assertThat(october.path("status").asText()).isEqualTo("PAID");
        assertThat(october.path("netTotal").decimalValue()).isGreaterThan(BigDecimal.ZERO);

        JsonNode hired = null;
        for (JsonNode application : get("/api/r/job-applications?pageSize=100", admin).body().path("data")) {
            if ("HIRED".equals(application.path("stage").asText())) hired = application;
        }
        assertThat(hired).isNotNull();
        JsonNode detail = ok(get("/api/recruitment/applications/" + hired.path("id").asText(), admin));
        assertThat(detail.path("stage").asText()).isEqualTo("HIRED");
        assertThat(detail.path("offerSalary").decimalValue()).isEqualByComparingTo("72000");
        assertThat(post("/api/r/job-postings", Map.of(), admin).status()).isEqualTo(409);

        JsonNode kerem = null;
        for (JsonNode review : get("/api/performance/mine", admin).body().path("items")) {
            if ("Kerem Polat".equals(review.path("employeeName").asText()) && "COMPLETED".equals(review.path("status").asText())) kerem = review;
        }
        assertThat(kerem).isNotNull();
        assertThat(kerem.path("overallScore").decimalValue()).isEqualByComparingTo("4.36");
        for (JsonNode gap : get("/api/training/compliance", admin).body().path("items")) {
            assertThat(gap.path("employeeName").asText()).isNotEqualTo("Kerem Polat");
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("year", 2026);
        parameters.put("minimumWage", new BigDecimal("26005.50"));
        parameters.put("sgkCeiling", new BigDecimal("195041.40"));
        parameters.put("sgkEmployeeRate", new BigDecimal("0.14"));
        parameters.put("unemploymentEmployeeRate", new BigDecimal("0.01"));
        parameters.put("sgkEmployerRate", new BigDecimal("0.1675"));
        parameters.put("unemploymentEmployerRate", new BigDecimal("0.02"));
        parameters.put("stampTaxRate", new BigDecimal("0.00759"));
        parameters.put("overtimeRate", new BigDecimal("1.5"));
        parameters.put("monthlyHours", 225);
        parameters.put("taxBrackets", List.of(Map.of("upTo", 158000, "rate", 0.15), Map.of("rate", 0.20)));
        ok(call("PUT", "/api/payroll/parameters", parameters, admin, null));
        JsonNode deniz = null;
        for (JsonNode person : get("/api/r/employees?pageSize=100", admin).body().path("data")) {
            if ("Deniz".equals(person.path("firstName").asText()) && "Aksoy".equals(person.path("lastName").asText())) deniz = person;
        }
        ok(post("/api/payroll/salaries", Map.of("employeeId", deniz.path("id").asText(), "effectiveFrom", "2026-01-01", "grossSalary", 45000), admin));
        JsonNode august = ok(post("/api/payroll/periods", Map.of("year", 2026, "month", 8), admin));
        JsonNode slip = null;
        for (JsonNode row : august.path("payslips")) if (deniz.path("id").asText().equals(row.path("employeeId").asText())) slip = row;
        assertThat(slip).isNotNull();
        assertThat(slip.path("netPay").decimalValue()).isLessThan(slip.path("grossPay").decimalValue());
        assertThat(slip.path("sgkEmployee").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        JsonNode approved = ok(post("/api/payroll/periods/" + august.path("id").asText() + "/approve", Map.of(), admin));
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        assertThat(post("/api/payroll/payslips/" + slip.path("id").asText(), Map.of("missingDays", 1), admin).status()).isEqualTo(409);
        JsonNode bank = get("/api/r/treasury-accounts?code=BANK-TRY", admin).body().path("data").get(0);
        JsonNode paid = ok(post("/api/payroll/periods/" + august.path("id").asText() + "/pay", Map.of("accountId", bank.path("id").asText()), admin));
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
    }
}
