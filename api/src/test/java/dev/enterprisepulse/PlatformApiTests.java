package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformApiTests extends ApiTestSupport {

    @Test
    void loginAndMe() {
        assertThat(post("/api/auth/login", Map.of("email", "admin@enterprisepulse.dev", "password", "wrong"), null).status()).isEqualTo(401);
        Res me = get("/api/auth/me", admin());
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().path("companies").size()).isEqualTo(2);
        assertThat(me.body().path("permissions").size()).isGreaterThan(100);
    }

    @Test
    void employeeCannotReadRoles() {
        assertThat(get("/api/r/roles", employee()).status()).isEqualTo(403);
    }

    @Test
    void crudWritesAuditAndSoftDeletes() {
        String a = admin();
        String code = "T" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        Res missing = post("/api/r/units", Map.of("code", code), a);
        assertThat(missing.status()).isEqualTo(400);
        Res created = post("/api/r/units", Map.of("code", code, "name", "Test"), a);
        assertThat(created.status()).isEqualTo(200);
        String id = created.body().path("data").path("id").asText();
        assertThat(post("/api/r/units", Map.of("code", code, "name", "Dup"), a).status()).isEqualTo(409);
        Res updated = call("PATCH", "/api/r/units/" + id, Map.of("name", "Test 2"), a, null);
        assertThat(updated.body().path("data").path("name").asText()).isEqualTo("Test 2");
        Res audit = get("/api/r/audit-logs?entityType=Unit&entityId=" + id, a);
        assertThat(audit.body().path("meta").path("total").asInt()).isEqualTo(2);
        assertThat(call("DELETE", "/api/r/units/" + id, null, a, null).status()).isEqualTo(204);
        assertThat(get("/api/r/units/" + id, a).status()).isEqualTo(404);
    }

    @Test
    void tenantIsolation() {
        String a = admin();
        JsonNode me = get("/api/auth/me", a).body();
        String logistics = null;
        for (JsonNode c : me.path("companies")) if (c.path("code").asText().equals("PULSE-LOJ")) logistics = c.path("id").asText();
        String hq = null;
        for (JsonNode b : get("/api/r/branches", a).body().path("data")) if (b.path("code").asText().equals("MERKEZ")) hq = b.path("id").asText();
        assertThat(call("GET", "/api/r/branches/" + hq, null, a, Map.of("x-company-id", logistics)).status()).isEqualTo(404);
        Res other = call("GET", "/api/r/branches", null, a, Map.of("x-company-id", logistics));
        assertThat(other.body().path("meta").path("total").asInt()).isEqualTo(1);
    }

    @Test
    void refreshTokenReuseRevokesFamily() {
        String first = login("manager@enterprisepulse.dev", "Manager123!").path("refreshToken").asText();
        Res rotated = post("/api/auth/refresh", Map.of("refreshToken", first), null);
        assertThat(rotated.status()).isEqualTo(200);
        String second = rotated.body().path("refreshToken").asText();
        assertThat(post("/api/auth/refresh", Map.of("refreshToken", first), null).status()).isEqualTo(401);
        assertThat(post("/api/auth/refresh", Map.of("refreshToken", second), null).status()).isEqualTo(401);
    }

    @Test
    void twoStepApproval() {
        String a = admin();
        String m = manager();
        String e = employee();
        Res started = post("/api/workflow/start", Map.of("definitionCode", "PURCHASE_REQUEST", "entityId", UUID.randomUUID().toString(), "title", "Test"), a);
        assertThat(started.status()).isEqualTo(200);
        String id = started.body().path("id").asText();
        assertThat(post("/api/workflow/instances/" + id + "/approve", null, e).status()).isEqualTo(403);
        Res step1 = post("/api/workflow/instances/" + id + "/approve", Map.of("comment", "ok"), m);
        assertThat(step1.body().path("currentStepOrder").asInt()).isEqualTo(2);
        assertThat(post("/api/workflow/instances/" + id + "/reject", Map.of("comment", "x"), m).status()).isEqualTo(403);
        Res done = post("/api/workflow/instances/" + id + "/approve", null, a);
        assertThat(done.body().path("status").asText()).isEqualTo("APPROVED");
        assertThat(post("/api/workflow/instances/" + id + "/approve", null, a).status()).isEqualTo(409);
        assertThat(get("/api/notifications/unread-count", a).body().path("count").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void exportsPdfAndExcel() {
        String a = admin();
        Res pdf = get("/api/r/branches/export?format=pdf", a);
        assertThat(new String(pdf.raw(), 0, 4)).isEqualTo("%PDF");
        Res xlsx = get("/api/r/branches/export?format=xlsx", a);
        assertThat(new String(xlsx.raw(), 0, 2)).isEqualTo("PK");
    }
}
