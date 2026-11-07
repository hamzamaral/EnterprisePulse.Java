package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryApiTests extends ApiTestSupport {
    private static String today() {
        return LocalDate.now().toString();
    }

    private JsonNode first(String token, String url) {
        return get(url, token).body().path("data").get(0);
    }

    private JsonNode ok(Res res) {
        assertThat(res.status()).as(res.status() + ": " + res.body()).isBetween(200, 299);
        return res.body();
    }

    @Test
    void movementCountRequestReceiptAndInvoiceStayConsistent() {
        String admin = admin();
        String employee = employee();
        String me = get("/api/auth/me", employee).body().path("user").path("id").asText();
        String code = "TST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String unit = first(admin, "/api/r/units?code=ADET").path("id").asText();
        String warehouse = first(admin, "/api/r/warehouses?code=MRK").path("id").asText();
        String supplier = first(admin, "/api/r/business-partners?code=TED-001").path("id").asText();

        JsonNode product = ok(post("/api/r/products", Map.of("code", code, "name", "Test kalemi", "unitId", unit, "purchasePrice", 10), admin)).path("data");
        JsonNode stock = get("/api/stock/products/" + product.path("id").asText(), admin).body();
        String variantId = stock.path("variants").get(0).path("id").asText();
        ok(call("PATCH", "/api/stock/products/" + product.path("id").asText() + "/variants/" + variantId, Map.of("minStock", 5), admin, null));

        assertThat(post("/api/stock/movements", Map.of("type", "RECEIPT", "movementDate", today(), "targetWarehouseId", warehouse,
                "lines", List.of(Map.of("variantId", variantId, "quantity", 1))), employee).status()).isEqualTo(403);

        JsonNode receipt = ok(post("/api/stock/movements", Map.of("type", "RECEIPT", "movementDate", today(), "targetWarehouseId", warehouse,
                "lines", List.of(Map.of("variantId", variantId, "quantity", 10, "unitCost", 12.5))), admin));
        assertThat(receipt.path("number").asText()).startsWith("STH");
        JsonNode level = first(admin, "/api/stock/levels?search=" + code);
        assertThat(level.path("quantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(level.path("lowStock").asBoolean()).isFalse();

        ok(post("/api/stock/movements", Map.of("type", "ISSUE", "movementDate", today(), "sourceWarehouseId", warehouse,
                "lines", List.of(Map.of("variantId", variantId, "quantity", 6))), admin));
        level = first(admin, "/api/stock/levels?search=" + code + "&lowStock=true");
        assertThat(level.path("quantity").decimalValue()).isEqualByComparingTo("4");
        assertThat(level.path("lowStock").asBoolean()).isTrue();
        assertThat(post("/api/stock/movements", Map.of("type", "ISSUE", "movementDate", today(), "sourceWarehouseId", warehouse,
                "lines", List.of(Map.of("variantId", variantId, "quantity", 100))), admin).status()).isEqualTo(400);
        assertThat(post("/api/r/stock-movements", Map.of(), admin).status()).isEqualTo(409);

        JsonNode count = ok(post("/api/stock/counts", Map.of("warehouseId", warehouse, "countDate", today()), admin));
        JsonNode line = null;
        for (JsonNode l : count.path("lines")) {
            if (variantId.equals(l.path("variantId").asText())) line = l;
        }
        assertThat(line).isNotNull();
        ok(call("PUT", "/api/stock/counts/" + count.path("id").asText() + "/lines",
                List.of(Map.of("id", line.path("id").asText(), "countedQty", 2)), admin, null));
        JsonNode done = ok(post("/api/stock/counts/" + count.path("id").asText() + "/complete", null, admin));
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(first(admin, "/api/stock/levels?search=" + code).path("quantity").decimalValue()).isEqualByComparingTo("2");

        JsonNode request = ok(post("/api/purchasing/requests", Map.of("title", "Test talebi " + code, "neededBy", today(),
                "lines", List.of(Map.of("variantId", variantId, "quantity", 2, "estimatedPrice", 100))), employee));
        assertThat(request.path("status").asText()).isEqualTo("PENDING");
        for (JsonNode row : get("/api/r/purchase-requests?pageSize=100", employee).body().path("data")) {
            assertThat(row.path("requestedById").asText()).isEqualTo(me);
        }
        String instance = request.path("workflowInstanceId").asText();
        ok(post("/api/workflow/instances/" + instance + "/approve", Map.of("comment", "uygun"), admin));
        ok(post("/api/workflow/instances/" + instance + "/approve", Map.of("comment", "uygun"), admin));
        request = get("/api/purchasing/requests/" + request.path("id").asText(), admin).body();
        assertThat(request.path("status").asText()).isEqualTo("APPROVED");

        String requestLine = request.path("lines").get(0).path("id").asText();
        JsonNode quote = ok(post("/api/purchasing/quotes", Map.of("partnerId", supplier, "requestId", request.path("id").asText(), "quoteDate", today(),
                "lines", List.of(Map.of("variantId", variantId, "quantity", 2, "unitPrice", 100, "discountRate", 10, "vatRate", 20, "requestLineId", requestLine))), admin));
        assertThat(quote.path("totals").path("grandTotal").decimalValue()).isEqualByComparingTo("216");
        Res accepted = post("/api/purchasing/quotes/" + quote.path("id").asText() + "/accept", Map.of("warehouseId", warehouse, "expectedDate", today()), admin);
        assertThat(accepted.status()).isBetween(200, 299);
        String orderId = accepted.body().asText();
        ok(post("/api/purchasing/orders/" + orderId + "/approve", null, admin));
        JsonNode order = get("/api/purchasing/orders/" + orderId, admin).body();
        String orderLine = order.path("lines").get(0).path("id").asText();
        ok(post("/api/purchasing/receipts", Map.of("orderId", orderId, "warehouseId", warehouse, "receiptDate", today(),
                "lines", List.of(Map.of("orderLineId", orderLine, "quantity", 2))), admin));
        assertThat(first(admin, "/api/stock/levels?search=" + code).path("quantity").decimalValue()).isEqualByComparingTo("4");

        BigDecimal before = get("/api/partners/" + supplier + "/balance", admin).body().decimalValue();
        JsonNode invoice = ok(post("/api/purchasing/invoices", Map.of("partnerId", supplier, "orderId", orderId, "supplierInvoiceNo", code, "invoiceDate", today(),
                "lines", List.of(Map.of("variantId", variantId, "quantity", 2, "unitPrice", 100, "discountRate", 10, "vatRate", 20, "orderLineId", orderLine))), admin));
        assertThat(invoice.path("totals").path("grandTotal").decimalValue()).isEqualByComparingTo("216");
        BigDecimal after = get("/api/partners/" + supplier + "/balance", admin).body().decimalValue();
        assertThat(after).isEqualByComparingTo(before.subtract(new BigDecimal("216")));
        JsonNode cancelled = ok(post("/api/purchasing/invoices/" + invoice.path("id").asText() + "/cancel", Map.of("reason", "test"), admin));
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(get("/api/partners/" + supplier + "/balance", admin).body().decimalValue()).isEqualByComparingTo(before);
    }
}
