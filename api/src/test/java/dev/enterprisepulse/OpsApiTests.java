package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpsApiTests extends ApiTestSupport {
    @Test
    void supportContractBudgetAndFleetAreSeeded() {
        String admin = admin();
        JsonNode tickets = get("/api/r/tickets", admin).body().path("data");
        assertThat(tickets.size()).isGreaterThan(0);
        JsonNode ticket = get("/api/support/tickets/" + tickets.get(0).path("id").asText(), admin).body();
        assertThat(ticket.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(post("/api/r/tickets", Map.of(), admin).status()).isEqualTo(409);

        JsonNode contracts = get("/api/r/contracts", admin).body().path("data");
        JsonNode service = null;
        for (JsonNode contract : contracts) if (contract.path("title").asText().contains("bakım")) { service = contract; break; }
        assertThat(service).isNotNull();
        JsonNode detail = get("/api/contracts/" + service.path("id").asText(), admin).body();
        assertThat(detail.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(detail.path("state").asText()).isIn("ACTIVE", "EXPIRING");

        JsonNode budgets = get("/api/r/budgets", admin).body().path("data");
        JsonNode budget = get("/api/budgets/" + budgets.get(0).path("id").asText(), admin).body();
        assertThat(budget.path("status").asText()).isEqualTo("APPROVED");
        assertThat(budget.path("rows").size()).isGreaterThan(0);

        JsonNode vehicles = get("/api/r/vehicles", admin).body().path("data");
        JsonNode vehicle = get("/api/fleet/vehicles/" + vehicles.get(0).path("id").asText(), admin).body();
        assertThat(vehicle.path("plate").asText()).isEqualTo("34 EP 001");
        assertThat(vehicle.path("driverName").asText()).isEqualTo("Kerem Polat");
    }
}
