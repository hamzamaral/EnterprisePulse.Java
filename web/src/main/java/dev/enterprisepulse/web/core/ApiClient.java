package dev.enterprisepulse.web.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/** REST client for the API: bearer token, tenant headers and a single-flight refresh on 401. */
public final class ApiClient {
    public static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static String baseUrl = "http://localhost:8180";

    private final Session session;
    private final ReentrantLock refreshLock = new ReentrantLock();

    ApiClient(Session session) {
        this.session = session;
    }

    public static void configure(String url) {
        baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static String baseUrl() {
        return baseUrl;
    }

    public record Download(byte[] bytes, String fileName, String contentType) {}

    /** Builds a JSON object from key/value pairs; unlike Map.of it accepts nulls. */
    public static ObjectNode obj(Object... kv) {
        ObjectNode node = JSON.createObjectNode();
        for (int i = 0; i < kv.length; i += 2) node.set((String) kv[i], JSON.valueToTree(kv[i + 1]));
        return node;
    }

    public static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private HttpResponse<byte[]> send(String method, String path, byte[] body, String contentType, boolean retry) {
        String token = session.accessToken;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(60));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (session.companyId != null) b.header("x-company-id", session.companyId);
        if (session.branchId != null) b.header("x-branch-id", session.branchId);
        b.header("Accept-Language", session.locale);
        if (body != null) b.header("Content-Type", contentType);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        HttpResponse<byte[]> res;
        try {
            res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException ex) {
            throw new ApiException(0, "en".equals(session.locale) ? "Cannot reach the API" : "API'ye ulaşılamıyor");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Interrupted");
        }
        if (res.statusCode() == 401 && retry && token != null) {
            if (tryRefresh(token)) return send(method, path, body, contentType, false);
            UI ui = UI.getCurrent();
            if (ui != null) ui.navigate("login");
        }
        if (res.statusCode() >= 400) {
            String message = "HTTP " + res.statusCode();
            try {
                JsonNode err = JSON.readTree(res.body());
                if (err != null && err.hasNonNull("message")) message = err.get("message").asText();
            } catch (IOException ignored) {
            }
            if (res.statusCode() == 429) message = "en".equals(session.locale) ? "Too many requests, please wait" : "Çok fazla istek, biraz bekleyin";
            throw new ApiException(res.statusCode(), message);
        }
        return res;
    }

    private HttpResponse<byte[]> sendJson(String method, String path, Object body) {
        try {
            byte[] bytes = JSON.writeValueAsBytes(body == null ? JSON.createObjectNode() : body);
            return send(method, path, bytes, "application/json", true);
        } catch (JsonProcessingException ex) {
            throw new ApiException(400, ex.getMessage());
        }
    }

    private static JsonNode parse(HttpResponse<byte[]> res) {
        if (res.statusCode() == 204 || res.body().length == 0) return null;
        try {
            return JSON.readTree(res.body());
        } catch (IOException ex) {
            throw new ApiException(500, ex.getMessage());
        }
    }

    public JsonNode get(String path) {
        return parse(send("GET", path, null, null, true));
    }

    public ObjectNode getObject(String path) {
        return (ObjectNode) get(path);
    }

    public ArrayNode getArray(String path) {
        return (ArrayNode) get(path);
    }

    public JsonNode post(String path, Object body) {
        return parse(sendJson("POST", path, body));
    }

    public JsonNode post(String path) {
        return post(path, null);
    }

    public JsonNode patch(String path, Object body) {
        return parse(sendJson("PATCH", path, body));
    }

    public JsonNode put(String path, Object body) {
        return parse(sendJson("PUT", path, body));
    }

    public void delete(String path) {
        send("DELETE", path, null, null, true);
    }

    public Download download(String path) {
        HttpResponse<byte[]> res = send("GET", path, null, null, true);
        String name = "download";
        String disposition = res.headers().firstValue("content-disposition").orElse("");
        int star = disposition.indexOf("filename*=UTF-8''");
        int plain = disposition.indexOf("filename=");
        if (star >= 0) {
            name = java.net.URLDecoder.decode(disposition.substring(star + 17).split(";")[0], StandardCharsets.UTF_8);
        } else if (plain >= 0) {
            name = disposition.substring(plain + 9).split(";")[0].replace("\"", "").trim();
        }
        return new Download(res.body(), name, res.headers().firstValue("content-type").orElse("application/octet-stream"));
    }

    public JsonNode upload(String path, String fileName, String contentType, byte[] data, Map<String, String> fields) {
        String boundary = "----ep" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (var f : fields.entrySet()) {
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey() + "\"\r\n\r\n" + f.getValue() + "\r\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName.replace("\"", "") + "\"\r\n"
                    + "Content-Type: " + (contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType) + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(data);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new ApiException(400, ex.getMessage());
        }
        return parse(send("POST", path, out.toByteArray(), "multipart/form-data; boundary=" + boundary, true));
    }

    /** Parallel 401s rotate the refresh token only once; a stale caller just reuses the new access token. */
    public boolean tryRefresh(String failedAccessToken) {
        refreshLock.lock();
        try {
            if (failedAccessToken != null && !failedAccessToken.equals(session.accessToken)) return session.accessToken != null;
            if (session.refreshToken == null) return false;
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/refresh"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(obj("refreshToken", session.refreshToken))))
                    .build();
            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                session.clear();
                return false;
            }
            JsonNode pair = JSON.readTree(res.body());
            session.accessToken = pair.path("accessToken").asText();
            session.refreshToken = pair.path("refreshToken").asText();
            return true;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            refreshLock.unlock();
        }
    }
}
