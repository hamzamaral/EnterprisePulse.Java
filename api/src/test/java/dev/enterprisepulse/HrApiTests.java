package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HrApiTests extends ApiTestSupport {

    private static LocalDate firstWednesdayOfJune() {
        LocalDate d = LocalDate.of(LocalDate.now(ZoneOffset.UTC).getYear(), 6, 1);
        while (d.getDayOfWeek() != DayOfWeek.WEDNESDAY) d = d.plusDays(1);
        return d;
    }

    private JsonNode balance(String token, String type) {
        for (JsonNode b : get("/api/hr/leave-balances", token).body()) if (b.path("leaveTypeName").asText().equals(type)) return b;
        throw new AssertionError("balance not found: " + type);
    }

    @Test
    void leaveRequestApprovalUpdatesBalance() {
        String employee = employee(), manager = manager(), admin = admin();
        String excuse = null;
        for (JsonNode t : get("/api/hr/leave-types", employee).body()) if (t.path("code").asText().equals("MAZERET")) excuse = t.path("id").asText();
        String day = firstWednesdayOfJune().toString();
        int usedBefore = balance(employee, "Mazeret İzni").path("usedDays").asInt();

        Res created = post("/api/hr/leave-requests", Map.of("leaveTypeId", excuse, "startDate", day, "endDate", day, "reason", "test"), employee);
        assertThat(created.status()).isEqualTo(200);
        assertThat(created.body().path("status").asText()).isEqualTo("PENDING");
        assertThat(created.body().path("days").asInt()).isEqualTo(1);
        assertThat(post("/api/hr/leave-requests", Map.of("leaveTypeId", excuse, "startDate", day, "endDate", day), employee).status()).isEqualTo(409);

        String id = created.body().path("id").asText();
        String instance = created.body().path("workflowInstanceId").asText();
        assertThat(post("/api/workflow/instances/" + instance + "/approve", null, employee).status()).isEqualTo(403);
        assertThat(post("/api/workflow/instances/" + instance + "/approve", Map.of("comment", "ok"), manager).status()).isEqualTo(200);
        assertThat(get("/api/hr/leave-requests/" + id, employee).body().path("status").asText()).isEqualTo("APPROVED");
        assertThat(balance(employee, "Mazeret İzni").path("usedDays").asInt()).isEqualTo(usedBefore + 1);

        boolean future = LocalDate.parse(day).isAfter(LocalDate.now(ZoneOffset.UTC));
        if (future) {
            assertThat(post("/api/hr/leave-requests/" + id + "/cancel", null, employee).status()).isEqualTo(403);
            assertThat(post("/api/hr/leave-requests/" + id + "/cancel", Map.of("comment", "test"), admin).status()).isEqualTo(200);
            assertThat(balance(employee, "Mazeret İzni").path("usedDays").asInt()).isEqualTo(usedBefore);
        } else {
            assertThat(post("/api/hr/leave-requests/" + id + "/cancel", Map.of("comment", "test"), admin).status()).isEqualTo(409);
        }
    }

    @Test
    void genericLeaveRequestCreateIsBlocked() {
        assertThat(post("/api/r/leave-requests", Map.of(), admin()).status()).isEqualTo(409);
    }

    @Test
    void employeeSeesOnlyOwnHrData() {
        String employee = employee();
        assertThat(get("/api/hr/attendance/timesheet?month=" + YearMonth.now(), employee).status()).isEqualTo(403);
        JsonNode me = get("/api/hr/employees/me", employee).body();
        assertThat(me.path("fullName").asText()).isEqualTo("Kerem Polat");
        for (JsonNode r : get("/api/hr/leave-requests", employee).body().path("data")) {
            assertThat(r.path("employeeId").asText()).isEqualTo(me.path("id").asText());
        }
    }

    @Test
    void timesheetAndOrgChartForHr() {
        String admin = admin();
        JsonNode sheet = get("/api/hr/attendance/timesheet?month=" + YearMonth.now().minusMonths(1), admin).body();
        assertThat(sheet.path("rows").size()).isEqualTo(15);
        assertThat(get("/api/hr/org-chart", admin).body().size()).isGreaterThan(0);
    }

    @Test
    void employeeStatusIsNotEditableGenerically() {
        String admin = admin();
        JsonNode first = get("/api/r/employees?pageSize=1", admin).body().path("data").get(0);
        Res updated = call("PATCH", "/api/r/employees/" + first.path("id").asText(), Map.of("status", "TERMINATED", "phone", "+90 555 000 0000"), admin, null);
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().path("data").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(updated.body().path("data").path("phone").asText()).isEqualTo("+90 555 000 0000");
    }
}
