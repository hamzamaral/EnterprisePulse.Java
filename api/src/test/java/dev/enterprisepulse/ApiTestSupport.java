package dev.enterprisepulse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Boots the API against a throw-away database (created on the dev Postgres, dropped afterwards) and talks to it over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class ApiTestSupport {
    private static final String SERVER = "jdbc:postgresql://localhost:5452/";
    private static final String DB = "ep_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    protected static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    protected int port;

    public record Res(int status, JsonNode body, byte[] raw) {}

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection c = DriverManager.getConnection(SERVER + "postgres", "erp", "erp"); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + DB);
        }
        registry.add("spring.datasource.url", () -> SERVER + DB);
        registry.add("app.rate-limit", () -> "false");
    }

    @AfterAll
    static void dropDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection(SERVER + "postgres", "erp", "erp"); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
        }
    }

    protected Res call(String method, String path, Object body, String token, Map<String, String> headers) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
            if (token != null) b.header("Authorization", "Bearer " + token);
            if (headers != null) headers.forEach(b::header);
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
            HttpResponse<byte[]> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            String type = res.headers().firstValue("content-type").orElse("");
            JsonNode json = res.body().length > 0 && type.contains("json") ? JSON.readTree(res.body()) : null;
            return new Res(res.statusCode(), json, res.body());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    protected Res get(String path, String token) {
        return call("GET", path, null, token, null);
    }

    protected Res post(String path, Object body, String token) {
        return call("POST", path, body == null ? Map.of() : body, token, null);
    }

    protected JsonNode login(String email, String password) {
        Res r = post("/api/auth/login", Map.of("email", email, "password", password), null);
        if (r.status() != 200) throw new IllegalStateException("login failed: " + r.status() + " " + r.body());
        return r.body();
    }

    protected String token(String email, String password) {
        return login(email, password).path("accessToken").asText();
    }

    protected String admin() {
        return token("admin@enterprisepulse.dev", "Admin123!");
    }

    protected String manager() {
        return token("manager@enterprisepulse.dev", "Manager123!");
    }

    protected String employee() {
        return token("employee@enterprisepulse.dev", "Employee123!");
    }
}
