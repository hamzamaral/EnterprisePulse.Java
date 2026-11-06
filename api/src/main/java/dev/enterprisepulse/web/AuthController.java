package dev.enterprisepulse.web;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.identity.AuthService;
import dev.enterprisepulse.identity.AuthService.LoginRequest;
import dev.enterprisepulse.identity.AuthService.TokenPair;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {
    private static final String COOKIE = "refresh_token";

    public record RefreshRequest(String refreshToken) {}

    public record ForgotPasswordRequest(String email) {}

    public record ResetPasswordRequest(String token, String password) {}

    public record ProfileRequest(String firstName, String lastName, String phone, String locale) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record CodeRequest(String code) {}

    public record PasswordRequest(String password) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body, HttpServletResponse res) {
        var result = auth.login(body);
        if (result.tokens() != null) setCookie(res, result.tokens());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requiresTwoFactor", result.requiresTwoFactor());
        out.put("accessToken", result.tokens() == null ? null : result.tokens().accessToken());
        out.put("refreshToken", result.tokens() == null ? null : result.tokens().refreshToken());
        out.put("refreshExpiresAt", result.tokens() == null ? null : result.tokens().refreshExpiresAt());
        return out;
    }

    @PostMapping("/refresh")
    public TokenPair refresh(@RequestBody(required = false) RefreshRequest body, HttpServletRequest req, HttpServletResponse res) {
        String token = body != null && body.refreshToken() != null ? body.refreshToken() : cookie(req);
        if (token == null) throw AppException.unauthorized("Oturum süresi doldu");
        TokenPair pair = auth.refresh(token);
        setCookie(res, pair);
        return pair;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest body, HttpServletRequest req) {
        auth.logout(body != null && body.refreshToken() != null ? body.refreshToken() : cookie(req));
        ResponseCookie clear = ResponseCookie.from(COOKIE, "").path("/api/auth").maxAge(0).httpOnly(true).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clear.toString()).build();
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgot(@RequestBody ForgotPasswordRequest body) {
        auth.forgotPassword(body.email());
        return Map.of("message", "Hesap varsa sıfırlama bağlantısı e-posta ile gönderildi");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@RequestBody ResetPasswordRequest body) {
        auth.resetPassword(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthService.MeResponse me() {
        return auth.me();
    }

    @PatchMapping("/profile")
    public ResponseEntity<Void> profile(@RequestBody ProfileRequest body) {
        auth.updateProfile(body.firstName(), body.lastName(), body.phone(), body.locale());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest body) {
        auth.changePassword(body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/setup")
    public AuthService.TwoFactorSetup setup() {
        return auth.setupTwoFactor();
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<Void> enable(@RequestBody CodeRequest body) {
        auth.enableTwoFactor(body.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> disable(@RequestBody PasswordRequest body) {
        auth.disableTwoFactor(body.password());
        return ResponseEntity.noContent().build();
    }

    private static String cookie(HttpServletRequest req) {
        var c = WebUtils.getCookie(req, COOKIE);
        return c == null ? null : c.getValue();
    }

    private static void setCookie(HttpServletResponse res, TokenPair pair) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, pair.refreshToken())
                .httpOnly(true).sameSite("Strict").secure(false).path("/api/auth")
                .maxAge(Duration.between(Instant.now(), pair.refreshExpiresAt()))
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
