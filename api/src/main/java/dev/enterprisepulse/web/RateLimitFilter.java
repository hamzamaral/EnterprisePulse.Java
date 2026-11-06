package dev.enterprisepulse.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fixed one-minute windows per client IP: 300 requests overall, 20 for credential endpoints. */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH = Set.of("/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-password");
    private static final int GLOBAL_LIMIT = 300;
    private static final int AUTH_LIMIT = 20;

    private record Window(long minute, AtomicInteger count) {}

    private final AppProperties props;
    private final ObjectMapper json;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!props.rateLimit || !request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        boolean auth = AUTH.contains(request.getRequestURI());
        if (!allow("all:" + ip, GLOBAL_LIMIT) || (auth && !allow("auth:" + ip, AUTH_LIMIT))) {
            response.setHeader("Retry-After", "60");
            ErrorHandler.write(response, json, 429, "Çok fazla istek; lütfen biraz sonra tekrar deneyin", null);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        long minute = System.currentTimeMillis() / 60_000;
        if (windows.size() > 10_000) windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        Window w = windows.compute(key, (k, old) -> old == null || old.minute() != minute ? new Window(minute, new AtomicInteger()) : old);
        return w.count().incrementAndGet() <= limit;
    }
}
