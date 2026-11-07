package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SalesApiTests extends ApiTestSupport {
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
    void quoteReservesStockInvoicesAndCollects() {
        String admin = admin();
        String code = "SAT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String unit = first(admin, "/api/r/units?code=ADET").path("id").asText();
        String warehouse = first(admin, "/api/r/warehouses?code=MRK").path("id").asText();
        JsonNode product = ok(post("/api/r/products", Map.of("code", code, "name", "Satış test kalemi", "unitId", unit, "purchasePrice", 10, "salePrice", 100), admin)).path("data");
        String variantId = get("/api/stock/products/" + product.path("id").asText(), admin).body().path("variants").get(0).path("id").asText();
        ok(post("/api/stock/movements", Map.of("type", "RECEIPT", "movementDate", today(), "targetWarehouseId", warehouse,
                "lines", List.of(Map.of("variantId", variantId, "quantity", 10, "unitCost", 10))), admin));
        String customer = ok(post("/api/r/business-partners", Map.of("code", code, "title", "Satış test müşterisi", "type", "CUSTOMER", "currencyCode", "TRY",
                "paymentTermDays", 15, "creditLimit", 500), admin)).path("data").path("id").asText();
        String account = ok(post("/api/r/treasury-accounts", Map.of("code", code, "name", "Test kasa", "type", "CASH", "currencyCode", "TRY"), admin)).path("data").path("id").asText();

        JsonNode quote = ok(post("/api/sales/quotes", Map.of("partnerId", customer, "quoteDate", today(),
                "lines", List.of(Map.of("variantId", variantId, "quantity", 6, "unitPrice", 100, "vatRate", 0))), admin));
        String orderId = ok(post("/api/sales/quotes/" + quote.path("id").asText() + "/accept", Map.of("warehouseId", warehouse), admin)).asText();
        assertThat(post("/api/sales/orders/" + orderId + "/approve", null, admin).status()).isEqualTo(400);
        ok(call("PUT", "/api/sales/orders/" + orderId, Map.of("partnerId", customer, "warehouseId", warehouse, "orderDate", today(), "currencyCode", "TRY",
                "lines", List.of(Map.of("variantId", variantId, "quantity", 4, "unitPrice", 100, "vatRate", 0))), admin, null));
        JsonNode approved = ok(post("/api/sales/orders/" + orderId + "/approve", null, admin));
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        String lineId = approved.path("lines").get(0).path("id").asText();

        JsonNode extra = ok(post("/api/sales/orders", Map.of("partnerId", customer, "warehouseId", warehouse, "orderDate", today(), "currencyCode", "TRY",
                "lines", List.of(Map.of("variantId", variantId, "quantity", 8, "unitPrice", 10, "vatRate", 0))), admin));
        assertThat(post("/api/sales/orders/" + extra.path("id").asText() + "/approve", null, admin).status()).isEqualTo(400);
        assertThat(post("/api/r/sales-orders", Map.of(), admin).status()).isEqualTo(409);

        ok(post("/api/sales/deliveries", Map.of("orderId", orderId, "deliveryDate", today(), "lines", List.of(Map.of("orderLineId", lineId, "quantity", 2))), admin));
        assertThat(first(admin, "/api/stock/levels?search=" + code).path("quantity").decimalValue()).isEqualByComparingTo("8");
        BigDecimal before = get("/api/partners/" + customer + "/balance", admin).body().decimalValue();
        JsonNode invoice = ok(post("/api/sales/invoices", Map.of("partnerId", customer, "orderId", orderId, "invoiceDate", today(),
                "lines", List.of(Map.of("variantId", variantId, "quantity", 2, "unitPrice", 100, "vatRate", 0, "orderLineId", lineId))), admin));
        assertThat(invoice.path("totals").path("grandTotal").decimalValue()).isEqualByComparingTo("200");
        assertThat(get("/api/partners/" + customer + "/balance", admin).body().decimalValue()).isEqualByComparingTo(before.add(new BigDecimal("200")));
        ok(post("/api/finance/payments", Map.of("direction", "IN", "method", "CASH", "partnerId", customer, "paymentDate", today(), "accountId", account,
                "amount", 200, "currencyCode", "TRY", "allocations", List.of(Map.of("salesInvoiceId", invoice.path("id").asText(), "amount", 200))), admin));
        assertThat(get("/api/partners/" + customer + "/balance", admin).body().decimalValue()).isEqualByComparingTo(before);
        JsonNode accounts = get("/api/finance/accounts", admin).body();
        BigDecimal cash = null;
        for (JsonNode row : accounts) if (account.equals(row.path("id").asText())) cash = row.path("balance").decimalValue();
        assertThat(cash).isEqualByComparingTo("200");
        assertThat(post("/api/sales/invoices/" + invoice.path("id").asText() + "/cancel", Map.of("reason", "tahsil edildi"), admin).status()).isEqualTo(409);

        String invoiceLine = invoice.path("lines").get(0).path("id").asText();
        JsonNode returned = ok(post("/api/sales/returns", Map.of("invoiceId", invoice.path("id").asText(), "warehouseId", warehouse, "returnDate", today(),
                "reason", "test iade", "lines", List.of(Map.of("invoiceLineId", invoiceLine, "quantity", 1))), admin));
        assertThat(returned.path("grandTotal").decimalValue()).isEqualByComparingTo("100");
        assertThat(first(admin, "/api/stock/levels?search=" + code).path("quantity").decimalValue()).isEqualByComparingTo("9");
        assertThat(get("/api/partners/" + customer + "/balance", admin).body().decimalValue()).isEqualByComparingTo(before.subtract(new BigDecimal("100")));
    }
}
