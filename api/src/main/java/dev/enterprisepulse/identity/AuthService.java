package dev.enterprisepulse.identity;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.AppProperties;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Branch;
import dev.enterprisepulse.data.generated.Company;
import dev.enterprisepulse.data.generated.PasswordResetToken;
import dev.enterprisepulse.data.generated.RefreshToken;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.platform.Messaging;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {
    public record LoginRequest(String email, String password, String code) {}

    public record TokenPair(String accessToken, String refreshToken, Instant refreshExpiresAt) {}

    public record LoginResult(boolean requiresTwoFactor, TokenPair tokens) {}

    public record MeBranch(UUID id, String code, String name) {}

    public record MeCompany(UUID id, String code, String name, String baseCurrency, boolean isDefault, List<MeBranch> branches) {}

    public record MeUser(UUID id, String email, String firstName, String lastName, String phone, String locale,
                         boolean isSuperAdmin, boolean twoFactorEnabled, UUID employeeId) {}

    public record MeResponse(MeUser user, List<MeCompany> companies, UUID activeCompanyId, UUID activeBranchId, List<String> permissions) {}

    public record TwoFactorSetup(String secret, String otpAuthUri) {}

    public record CompanyContext(UUID companyId, UUID branchId) {}

    private final Db db;
    private final TokenService tokens;
    private final PermissionResolver permissions;
    private final Messaging.MailQueue mail;
    private final AppProperties props;

    public AuthService(Db db, TokenService tokens, PermissionResolver permissions, Messaging.MailQueue mail, AppProperties props) {
        this.db = db;
        this.tokens = tokens;
        this.permissions = permissions;
        this.mail = mail;
        this.props = props;
    }

    private static CurrentUser ctx() {
        return CurrentUser.get();
    }

    /** Wrong passwords are counted even though the login itself fails, hence noRollbackFor. */
    @Transactional(noRollbackFor = AppException.class)
    public LoginResult login(LoginRequest request) {
        Guard.that(request.email() != null && request.password() != null, "E-posta ve şifre zorunludur");
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = db.first("select u from User u where u.email = ?1", User.class, email).orElse(null);
        if (user == null || !user.isActive) throw AppException.unauthorized("E-posta veya şifre hatalı");
        if (user.lockedUntil != null && user.lockedUntil.isAfter(Instant.now())) {
            String until = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Istanbul")).format(user.lockedUntil);
            throw AppException.unauthorized("Hesap kilitli. " + until + " sonrasında tekrar deneyin");
        }
        if (!PasswordHasher.verify(request.password(), user.passwordHash)) {
            user.failedLoginCount++;
            if (user.failedLoginCount >= props.maxFailedLogins) {
                user.lockedUntil = Instant.now().plus(Duration.ofMinutes(props.lockMinutes));
                user.failedLoginCount = 0;
            }
            db.flush();
            throw AppException.unauthorized("E-posta veya şifre hatalı");
        }
        if (user.twoFactorEnabled) {
            if (Guard.blank(request.code())) return new LoginResult(true, null);
            if (!verifyTotp(user.twoFactorSecret, request.code())) throw AppException.unauthorized("Doğrulama kodu hatalı");
        }
        user.failedLoginCount = 0;
        user.lockedUntil = null;
        user.lastLoginAt = Instant.now();
        return new LoginResult(false, issue(user, UUID.randomUUID()));
    }

    /** Rotates the refresh token. Reusing an already rotated token revokes the whole family. */
    @Transactional(noRollbackFor = AppException.class)
    public TokenPair refresh(String refreshToken) {
        String hash = TokenService.sha256(refreshToken);
        RefreshToken row = db.first("select t from RefreshToken t where t.tokenHash = ?1", RefreshToken.class, hash)
                .orElseThrow(() -> AppException.unauthorized("Oturum süresi doldu"));
        if (row.revokedAt != null) {
            db.execute("update RefreshToken t set t.revokedAt = ?1 where t.familyId = ?2 and t.revokedAt is null", Instant.now(), row.familyId);
            throw AppException.unauthorized("Oturum güvenlik nedeniyle sonlandırıldı");
        }
        User user = db.find(User.class, row.userId);
        if (row.expiresAt.isBefore(Instant.now()) || user == null || !user.isActive) throw AppException.unauthorized("Oturum süresi doldu");
        row.revokedAt = Instant.now();
        TokenPair pair = issue(user, row.familyId);
        row.replacedById = db.one("select t.id from RefreshToken t where t.tokenHash = ?1", UUID.class, TokenService.sha256(pair.refreshToken()));
        return pair;
    }

    public void logout(String refreshToken) {
        if (Guard.blank(refreshToken)) return;
        db.first("select t from RefreshToken t where t.tokenHash = ?1", RefreshToken.class, TokenService.sha256(refreshToken))
                .ifPresent(row -> db.execute("update RefreshToken t set t.revokedAt = ?1 where t.familyId = ?2 and t.revokedAt is null",
                        Instant.now(), row.familyId));
    }

    private TokenPair issue(User user, UUID familyId) {
        String refresh = TokenService.newOpaqueToken();
        Instant expires = Instant.now().plus(Duration.ofDays(props.refreshTokenDays));
        RefreshToken token = new RefreshToken();
        token.userId = user.id;
        token.tokenHash = TokenService.sha256(refresh);
        token.familyId = familyId;
        token.expiresAt = expires;
        token.ip = ctx().ip;
        token.userAgent = ctx().userAgent;
        db.add(token);
        db.flush();
        return new TokenPair(tokens.createAccessToken(user), refresh, expires);
    }

    /**
     * Picks the active company: the requested one if the user belongs to it (super admins may open any),
     * otherwise the default membership. Runs without the tenant restriction.
     */
    public CompanyContext resolveCompany(UUID userId, boolean superAdmin, UUID requested, UUID requestedBranch) {
        try (var scope = ctx().withoutTenant()) {
            List<Object[]> memberships = db.list("""
                    select m.companyId, m.isDefault, m.defaultBranchId from UserCompany m join Company c on c.id = m.companyId
                     where m.userId = ?1 and c.isActive = true""", Object[].class, userId);
            UUID companyId;
            UUID branchId = null;
            Object[] match = requested == null ? null : memberships.stream().filter(m -> requested.equals(m[0])).findFirst().orElse(null);
            if (match != null || (requested != null && superAdmin
                    && db.exists("select c.id from Company c where c.id = ?1 and c.isActive = true", requested))) {
                companyId = requested;
                branchId = match == null ? null : (UUID) match[2];
            } else {
                Object[] chosen = memberships.stream().max(Comparator.comparing(m -> (Boolean) m[1])).orElse(null);
                if (chosen == null && superAdmin) {
                    companyId = db.one("select c.id from Company c where c.isActive = true order by c.code", UUID.class);
                } else if (chosen == null) {
                    throw AppException.forbidden("Kullanıcı hiçbir şirkete bağlı değil");
                } else {
                    companyId = (UUID) chosen[0];
                    branchId = (UUID) chosen[2];
                }
            }
            if (requestedBranch != null && db.exists("select b.id from Branch b where b.id = ?1 and b.companyId = ?2", requestedBranch, companyId)) {
                branchId = requestedBranch;
            }
            return new CompanyContext(companyId, branchId);
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        UUID userId = ctx().requireUserId();
        UUID companyId = ctx().requireCompanyId();
        User user = db.get(User.class, userId);
        List<MeCompany> companies;
        try (var scope = ctx().withoutTenant()) {
            List<Company> list = user.isSuperAdmin
                    ? db.list("select c from Company c where c.isActive = true order by c.name", Company.class)
                    : db.list("select c from Company c where c.isActive = true and exists (select 1 from UserCompany m where m.companyId = c.id and m.userId = ?1) order by c.name",
                            Company.class, userId);
            UUID defaultId = db.first("select m.companyId from UserCompany m where m.userId = ?1 and m.isDefault = true", UUID.class, userId).orElse(null);
            companies = list.stream().map(c -> new MeCompany(c.id, c.code, c.name, c.baseCurrency, c.id.equals(defaultId),
                    db.list("select b from Branch b where b.companyId = ?1 and b.isActive = true order by b.name", Branch.class, c.id)
                            .stream().map(b -> new MeBranch(b.id, b.code, b.name)).toList())).toList();
        }
        UUID employeeId = db.first("select e.id from Employee e where e.userId = ?1", UUID.class, userId).orElse(null);
        List<String> codes = user.isSuperAdmin
                ? db.list("select p.code from Permission p order by p.code", String.class)
                : permissions.resolve(userId, companyId).stream().sorted().toList();
        return new MeResponse(new MeUser(user.id, user.email, user.firstName, user.lastName, user.phone, user.locale, user.isSuperAdmin,
                user.twoFactorEnabled, employeeId), companies, companyId, ctx().branchId, codes);
    }

    public void updateProfile(String firstName, String lastName, String phone, String locale) {
        User user = db.get(User.class, ctx().requireUserId());
        Guard.that(!Guard.blank(firstName) && !Guard.blank(lastName), "Ad ve soyad zorunludur");
        user.firstName = firstName.trim();
        user.lastName = lastName.trim();
        user.phone = phone;
        user.locale = "en".equals(locale) ? "en" : "tr";
    }

    public void changePassword(String current, String next) {
        User user = db.get(User.class, ctx().requireUserId());
        if (!PasswordHasher.verify(current, user.passwordHash)) throw AppException.badRequest("Mevcut şifre hatalı");
        PasswordHasher.assertStrong(next);
        user.passwordHash = PasswordHasher.hash(next);
    }

    public void forgotPassword(String email) {
        if (Guard.blank(email)) return;
        User user = db.first("select u from User u where u.email = ?1 and u.isActive = true", User.class, email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null) return; // never reveal whether the address exists
        String token = TokenService.newOpaqueToken();
        PasswordResetToken row = new PasswordResetToken();
        row.userId = user.id;
        row.tokenHash = TokenService.sha256(token);
        row.expiresAt = Instant.now().plus(Duration.ofHours(1));
        db.add(row);
        String link = props.webAppUrl + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        mail.send(user.email, "Şifre sıfırlama", "<p>Şifrenizi sıfırlamak için bağlantıyı açın (1 saat geçerli):</p><p><a href=\"" + link + "\">" + link + "</a></p>");
    }

    public void resetPassword(String token, String password) {
        Guard.that(!Guard.blank(token), "Bağlantı geçersiz veya süresi dolmuş");
        PasswordResetToken row = db.first("select t from PasswordResetToken t where t.tokenHash = ?1", PasswordResetToken.class, TokenService.sha256(token)).orElse(null);
        if (row == null || row.usedAt != null || row.expiresAt.isBefore(Instant.now())) throw AppException.badRequest("Bağlantı geçersiz veya süresi dolmuş");
        PasswordHasher.assertStrong(password);
        User user = db.get(User.class, row.userId);
        row.usedAt = Instant.now();
        user.passwordHash = PasswordHasher.hash(password);
        user.failedLoginCount = 0;
        user.lockedUntil = null;
        db.execute("update RefreshToken t set t.revokedAt = ?1 where t.userId = ?2 and t.revokedAt is null", Instant.now(), user.id);
    }

    public TwoFactorSetup setupTwoFactor() {
        User user = db.get(User.class, ctx().requireUserId());
        Guard.state(!user.twoFactorEnabled, "İki adımlı doğrulama zaten açık");
        String secret = new DefaultSecretGenerator(32).generate();
        user.twoFactorSecret = secret;
        String uri = "otpauth://totp/EnterprisePulse:" + URLEncoder.encode(user.email, StandardCharsets.UTF_8).replace("+", "%20")
                + "?secret=" + secret + "&issuer=EnterprisePulse";
        return new TwoFactorSetup(secret, uri);
    }

    public void enableTwoFactor(String code) {
        User user = db.get(User.class, ctx().requireUserId());
        Guard.state(user.twoFactorSecret != null, "Önce kurulum başlatın");
        if (!verifyTotp(user.twoFactorSecret, code)) throw AppException.badRequest("Doğrulama kodu hatalı");
        user.twoFactorEnabled = true;
    }

    public void disableTwoFactor(String password) {
        User user = db.get(User.class, ctx().requireUserId());
        if (!PasswordHasher.verify(password, user.passwordHash)) throw AppException.badRequest("Şifre hatalı");
        user.twoFactorEnabled = false;
        user.twoFactorSecret = null;
    }

    public static boolean verifyTotp(String secret, String code) {
        if (secret == null || code == null) return false;
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        verifier.setAllowedTimePeriodDiscrepancy(1);
        return verifier.isValidCode(secret, code.trim());
    }
}
