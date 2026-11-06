package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class AssetApiTests extends ApiTestSupport {
    private static String today() {
        return LocalDate.ofInstant(Instant.now().plusSeconds(3 * 3600), ZoneOffset.UTC).toString();
    }

    private JsonNode first(String token, String url) {
        return get(url, token).body().path("data").get(0);
    }

    private JsonNode ok(Res res) {
        assertThat(res.status()).as(res.status() + ": " + res.body()).isEqualTo(200);
        return res.body();
    }

    private static boolean any(JsonNode array, java.util.function.Predicate<JsonNode> test) {
        return StreamSupport.stream(array.spliterator(), false).anyMatch(test);
    }

    @Test
    void assignAcknowledgeTransferReturnAndMaintenance() {
        String admin = admin(), employee = employee();
        String kerem = get("/api/hr/employees/me", employee).body().path("id").asText();
        String id = first(admin, "/api/r/assets?status=IN_STOCK&search=Aeron").path("id").asText();
        Map<String, Object> toKerem = Map.of("assigneeType", "EMPLOYEE", "employeeId", kerem, "assignedAt", today());

        assertThat(post("/api/assets/" + id + "/assign", toKerem, employee).status()).isEqualTo(403);
        JsonNode assignment = ok(post("/api/assets/" + id + "/assign",
                Map.of("assigneeType", "EMPLOYEE", "employeeId", kerem, "assignedAt", today(), "notes", "test"), admin));
        assertThat(assignment.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(post("/api/assets/" + id + "/assign", toKerem, admin).status()).isEqualTo(409);

        JsonNode detail = get("/api/assets/" + id, admin).body();
        assertThat(detail.path("status").asText()).isEqualTo("ASSIGNED");
        assertThat(detail.path("holder").path("name").asText()).isEqualTo("Kerem Polat");
        assertThat(detail.path("depreciation").isObject()).isTrue();

        String assignmentId = assignment.path("id").asText();
        assertThat(any(get("/api/assets/assignments/mine", employee).body(),
                a -> a.path("id").asText().equals(assignmentId) && a.path("acknowledgedAt").isNull())).isTrue();
        assertThat(get("/api/assets/assignments/" + assignmentId, employee).body().path("canAcknowledge").asBoolean()).isTrue();
        JsonNode signed = ok(post("/api/assets/assignments/" + assignmentId + "/acknowledge", null, employee));
        assertThat(signed.path("assignment").path("acknowledgedAt").isNull()).isFalse();
        assertThat(signed.path("canAcknowledge").asBoolean()).isFalse();

        String finance = first(admin, "/api/r/departments?code=FIN").path("id").asText();
        JsonNode transferred = ok(post("/api/assets/" + id + "/transfer", Map.of("assigneeType", "DEPARTMENT", "departmentId", finance, "assignedAt", today()), admin));
        assertThat(transferred.path("assigneeType").asText()).isEqualTo("DEPARTMENT");
        JsonNode history = get("/api/assets/" + id + "/assignments", admin).body();
        assertThat(history.size()).isEqualTo(2);
        assertThat(any(history, a -> a.path("status").asText().equals("RETURNED") && a.path("returnNotes").asText().startsWith("Devir"))).isTrue();

        String depot = first(admin, "/api/r/asset-locations?code=DEPO").path("id").asText();
        ok(post("/api/assets/" + id + "/return", Map.of("returnedAt", today(), "conditionIn", "FAIR", "locationId", depot), admin));
        detail = get("/api/assets/" + id, admin).body();
        assertThat(detail.path("status").asText()).isEqualTo("IN_STOCK");
        assertThat(detail.path("condition").asText()).isEqualTo("FAIR");
        assertThat(detail.path("holder").isNull()).isTrue();

        JsonNode maintenance = ok(post("/api/assets/" + id + "/maintenances", Map.of("type", "REPAIR", "title", "Amortisör", "startedAt", today()), admin));
        assertThat(get("/api/assets/" + id, admin).body().path("status").asText()).isEqualTo("IN_MAINTENANCE");
        assertThat(post("/api/assets/" + id + "/assign", toKerem, admin).status()).isEqualTo(409);
        ok(post("/api/assets/" + id + "/maintenances/" + maintenance.path("id").asText() + "/complete", Map.of("completedAt", today(), "cost", 750), admin));
        assertThat(get("/api/assets/" + id, admin).body().path("status").asText()).isEqualTo("IN_STOCK");

        Res label = get("/api/assets/" + id + "/label", admin);
        assertThat(label.status()).isEqualTo(200);
        assertThat(label.raw()[1]).isEqualTo((byte) 'P');
    }

    @Test
    void genericWritesCannotBypassAssetRules() {
        String admin = admin();
        assertThat(post("/api/r/asset-assignments", Map.of(), admin).status()).isEqualTo(409);
        String retired = first(admin, "/api/r/assets?status=RETIRED").path("id").asText();
        assertThat(call("PATCH", "/api/r/assets/" + retired, Map.of("name", "x"), admin, null).status()).isEqualTo(409);
        String assigned = first(admin, "/api/r/assets?status=ASSIGNED").path("id").asText();
        assertThat(call("DELETE", "/api/r/assets/" + assigned, null, admin, null).status()).isEqualTo(409);
    }

    @Test
    void countScanAndComplete() {
        String admin = admin();
        JsonNode tablet = first(admin, "/api/r/assets?search=iPad");
        String category = tablet.path("categoryId").asText(), tabletId = tablet.path("id").asText(), tag = tablet.path("assetTag").asText();
        JsonNode count = ok(post("/api/assets/counts", Map.of("countDate", today(), "categoryId", category), admin));
        assertThat(count.path("status").asText()).isEqualTo("DRAFT");
        assertThat(count.path("expectedCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(post("/api/assets/counts", Map.of("countDate", today(), "categoryId", category), admin).status()).isEqualTo(409);

        String id = count.path("id").asText();
        assertThat(ok(post("/api/assets/counts/" + id + "/scan", Map.of("code", tag.toLowerCase()), admin)).path("result").asText()).isEqualTo("FOUND");
        assertThat(ok(post("/api/assets/counts/" + id + "/scan", Map.of("code", "http://localhost:8181/r/assets/" + tabletId), admin)).path("result").asText())
                .isEqualTo("ALREADY_FOUND");
        assertThat(post("/api/assets/counts/" + id + "/scan", Map.of("code", "YOK-123"), admin).status()).isEqualTo(404);

        JsonNode laptop = first(admin, "/api/r/assets?search=Latitude");
        JsonNode scan = ok(post("/api/assets/counts/" + id + "/scan", Map.of("code", laptop.path("serialNumber").asText()), admin));
        assertThat(scan.path("result").asText()).isEqualTo("UNEXPECTED");
        assertThat(call("DELETE", "/api/assets/counts/" + id + "/lines/" + scan.path("line").path("id").asText(), null, admin, null).status()).isEqualTo(204);

        JsonNode line = StreamSupport.stream(get("/api/assets/counts/" + id, admin).body().path("lines").spliterator(), false)
                .filter(l -> l.path("assetId").asText().equals(tabletId)).findFirst().orElseThrow();
        ok(call("PATCH", "/api/assets/counts/" + id + "/lines/" + line.path("id").asText(), Map.of("condition", "DAMAGED"), admin, null));
        JsonNode done = ok(post("/api/assets/counts/" + id + "/complete", Map.of("applyConditions", true), admin));
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.path("unexpectedCount").asInt()).isZero();
        assertThat(get("/api/assets/" + tabletId, admin).body().path("condition").asText()).isEqualTo("DAMAGED");
        assertThat(post("/api/assets/counts/" + id + "/scan", Map.of("code", tag), admin).status()).isEqualTo(409);
    }
}
