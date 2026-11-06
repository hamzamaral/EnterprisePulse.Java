package dev.enterprisepulse.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.identity.AuthService;
import dev.enterprisepulse.identity.PermissionResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fills {@link CurrentUser} from the JWT and the x-company-id / x-branch-id headers. */
public class RequestContextFilter extends OncePerRequestFilter {
    private final Db db;
    private final AuthService auth;
    private final PermissionResolver permissions;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public RequestContextFilter(Db db, AuthService auth, PermissionResolver permissions, TransactionTemplate tx, ObjectMapper json) {
        this.db = db;
        this.auth = auth;
        this.permissions = permissions;
        this.tx = tx;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        CurrentUser ctx = CurrentUser.reset();
        try {
            String forwarded = request.getHeader("X-Forwarded-For");
            ctx.ip = forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
            ctx.userAgent = request.getHeader("User-Agent");
            String requestId = request.getHeader("X-Request-Id");
            if (requestId != null && !requestId.isBlank()) ctx.requestId = requestId;
            String lang = request.getHeader("Accept-Language");
            ctx.locale = lang != null && lang.toLowerCase().startsWith("en") ? "en" : "tr";

            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
                try {
                    UUID userId = UUID.fromString(token.getToken().getSubject());
                    try (var scope = ctx.withoutTenant()) {
                        tx.executeWithoutResult(s -> {
                            User user = db.find(User.class, userId);
                            if (user == null || !user.isActive) throw AppException.unauthorized("Hesap devre dışı");
                            ctx.userId = user.id;
                            ctx.email = user.email;
                            ctx.fullName = user.firstName + " " + user.lastName;
                            ctx.superAdmin = user.isSuperAdmin;
                            var company = auth.resolveCompany(user.id, user.isSuperAdmin, header(request, "x-company-id"), header(request, "x-branch-id"));
                            ctx.companyId = company.companyId();
                            ctx.branchId = company.branchId();
                            ctx.permissions = new HashSet<>(permissions.resolve(user.id, company.companyId()));
                        });
                    }
                } catch (AppException e) {
                    ErrorHandler.write(response, json, e.status(), e.getMessage(), ctx.requestId);
                    return;
                }
            }
            response.setHeader("X-Request-Id", ctx.requestId);
            chain.doFilter(request, response);
        } finally {
            CurrentUser.reset();
        }
    }

    private static UUID header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
