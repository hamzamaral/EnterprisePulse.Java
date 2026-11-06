package dev.enterprisepulse.identity;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.AppProperties;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.PasswordResetToken;
import dev.enterprisepulse.data.generated.Permission;
import dev.enterprisepulse.data.generated.Role;
import dev.enterprisepulse.data.generated.RolePermission;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.data.generated.UserCompany;
import dev.enterprisepulse.data.generated.UserRole;
import dev.enterprisepulse.platform.Messaging;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@Transactional
public class UsersService {
    public record CreateUserRequest(String email, String firstName, String lastName, String phone, String password,
                                    List<UUID> roleIds, UUID branchId, boolean isSuperAdmin) {}

    public record UserRolesRequest(List<UUID> roleIds, UUID branchId) {}

    public record RoleRequest(String code, String name, String description, List<String> permissions) {}

    public record RoleDetail(UUID id, UUID companyId, String code, String name, String description, boolean isSystem,
                             List<String> permissions, long userCount) {}

    private final Db db;
    private final PermissionResolver permissions;
    private final Messaging.MailQueue mail;
    private final AppProperties props;

    public UsersService(Db db, PermissionResolver permissions, Messaging.MailQueue mail, AppProperties props) {
        this.db = db;
        this.permissions = permissions;
        this.mail = mail;
        this.props = props;
    }

    private static CurrentUser ctx() {
        return CurrentUser.get();
    }

    /** Creates the user or, if the e-mail already exists, adds the existing user to this company. */
    public User create(CreateUserRequest request) {
        ctx().require("users:create");
        UUID companyId = ctx().requireCompanyId();
        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        Guard.that(email.contains("@"), "Geçerli bir e-posta girin");
        Guard.that(!Guard.blank(request.firstName()) && !Guard.blank(request.lastName()), "Ad ve soyad zorunludur");
        Guard.that(!request.isSuperAdmin() || ctx().superAdmin, "Süper yönetici yalnızca süper yönetici tarafından atanabilir");

        List<Object[]> existing = db.nativeRows("select id, deleted_at from users where email = ?1", email);
        boolean invite = Guard.blank(request.password());
        User user;
        if (existing.isEmpty()) {
            if (!invite) PasswordHasher.assertStrong(request.password());
            user = new User();
            user.email = email;
            user.firstName = request.firstName().trim();
            user.lastName = request.lastName().trim();
            user.phone = request.phone();
            user.isSuperAdmin = request.isSuperAdmin();
            user.passwordHash = PasswordHasher.hash(invite ? TokenService.newOpaqueToken() : request.password());
            db.add(user);
        } else {
            Guard.state(existing.get(0)[1] == null, "Bu e-posta silinmiş bir kullanıcıya ait");
            user = db.get(User.class, existing.get(0)[0]);
            Guard.state(db.nativeRows("select 1 from user_companies where user_id = ?1 and company_id = ?2", user.id, companyId).isEmpty(),
                    "Kullanıcı bu şirkette zaten tanımlı");
            invite = false;
        }
        boolean hasDefault = !db.nativeRows("select 1 from user_companies where user_id = ?1 and is_default", user.id).isEmpty();
        UserCompany membership = new UserCompany();
        membership.userId = user.id;
        membership.companyId = companyId;
        membership.isDefault = !hasDefault;
        membership.defaultBranchId = request.branchId();
        db.add(membership);
        setRoles(user.id, companyId, request.roleIds() == null ? List.of() : request.roleIds());
        db.flush();

        if (invite) {
            String token = TokenService.newOpaqueToken();
            PasswordResetToken row = new PasswordResetToken();
            row.userId = user.id;
            row.tokenHash = TokenService.sha256(token);
            row.expiresAt = Instant.now().plus(Duration.ofDays(3));
            db.add(row);
            String link = props.webAppUrl + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            mail.send(user.email, "EnterprisePulse davetiyesi", "<p>Merhaba " + HtmlUtils.htmlEscape(user.firstName)
                    + ",</p><p>Hesabınız oluşturuldu. Şifrenizi belirlemek için bağlantıyı açın (3 gün geçerli):</p><p><a href=\"" + link + "\">" + link + "</a></p>");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public List<UUID> roleIds(UUID userId) {
        return db.list("select ur.roleId from UserRole ur where ur.userId = ?1 and ur.companyId = ?2", UUID.class, userId, ctx().requireCompanyId());
    }

    public void updateRoles(UUID userId, UserRolesRequest request) {
        ctx().require("users:update");
        UUID companyId = ctx().requireCompanyId();
        UserCompany membership = db.first("select m from UserCompany m where m.userId = ?1 and m.companyId = ?2", UserCompany.class, userId, companyId)
                .orElseThrow(() -> AppException.notFound("Kullanıcı bu şirkette değil"));
        membership.defaultBranchId = request.branchId();
        setRoles(userId, companyId, request.roleIds() == null ? List.of() : request.roleIds());
        db.flush();
        permissions.invalidate();
    }

    /** Removes the user from the active company; the account itself stays for other companies. */
    public void removeFromCompany(UUID userId) {
        ctx().require("users:delete");
        Guard.state(!userId.equals(ctx().userId), "Kendinizi şirketten çıkaramazsınız");
        UUID companyId = ctx().requireCompanyId();
        db.list("select ur from UserRole ur where ur.userId = ?1 and ur.companyId = ?2", UserRole.class, userId, companyId).forEach(db::remove);
        db.list("select m from UserCompany m where m.userId = ?1 and m.companyId = ?2", UserCompany.class, userId, companyId).forEach(db::remove);
        db.flush();
        permissions.invalidate();
    }

    private void setRoles(UUID userId, UUID companyId, List<UUID> roleIds) {
        Set<UUID> wanted = new HashSet<>(roleIds);
        List<UUID> valid = wanted.isEmpty() ? List.of()
                : db.list("select r.id from Role r where r.id in ?1 and (r.companyId = ?2 or r.companyId is null)", UUID.class, wanted, companyId);
        Guard.that(valid.size() == wanted.size(), "Geçersiz rol seçimi");
        List<UserRole> existing = db.list("select ur from UserRole ur where ur.userId = ?1 and ur.companyId = ?2", UserRole.class, userId, companyId);
        for (UserRole ur : existing) if (!wanted.contains(ur.roleId)) db.remove(ur);
        for (UUID roleId : valid) {
            if (existing.stream().noneMatch(ur -> ur.roleId.equals(roleId))) {
                UserRole ur = new UserRole();
                ur.userId = userId;
                ur.roleId = roleId;
                ur.companyId = companyId;
                db.add(ur);
            }
        }
    }

    // ---------- roles ----------

    @Transactional(readOnly = true)
    public List<RoleDetail> roles() {
        UUID companyId = ctx().requireCompanyId();
        return db.list("select r from Role r where r.companyId = ?1 or r.companyId is null order by r.name", Role.class, companyId)
                .stream().map(r -> detail(r, companyId)).toList();
    }

    private RoleDetail detail(Role r, UUID companyId) {
        List<String> codes = db.list("select p.code from RolePermission rp join Permission p on p.id = rp.permissionId where rp.roleId = ?1 order by p.code",
                String.class, r.id);
        long users = db.count("select count(ur) from UserRole ur where ur.roleId = ?1 and ur.companyId = ?2", r.id, companyId);
        return new RoleDetail(r.id, r.companyId, r.code, r.name, r.description, r.isSystem, codes, users);
    }

    public RoleDetail saveRole(UUID id, RoleRequest request) {
        ctx().require(id == null ? "roles:create" : "roles:update");
        UUID companyId = ctx().requireCompanyId();
        Guard.that(!Guard.blank(request.name()), "Kod ve ad zorunludur");
        Role role;
        if (id == null) {
            Guard.that(!Guard.blank(request.code()), "Kod ve ad zorunludur");
            Guard.state(!db.exists("select r.id from Role r where r.companyId = ?1 and r.code = ?2", companyId, request.code().trim()), "Bu kodda bir rol var");
            role = new Role();
            role.companyId = companyId;
            role.code = request.code().trim();
            role.name = request.name().trim();
            db.add(role);
        } else {
            role = db.first("select r from Role r where r.id = ?1 and r.companyId = ?2", Role.class, id, companyId)
                    .orElseThrow(() -> AppException.notFound("Rol bulunamadı"));
            Guard.state(!role.isSystem, "Sistem rolü değiştirilemez");
            role.name = request.name().trim();
        }
        role.description = request.description();
        List<String> codes = request.permissions() == null ? List.of() : request.permissions();
        List<Permission> wanted = codes.isEmpty() ? List.of() : db.list("select p from Permission p where p.code in ?1", Permission.class, codes);
        Set<UUID> wantedIds = new HashSet<>(wanted.stream().map(p -> p.id).toList());
        List<RolePermission> current = db.list("select rp from RolePermission rp where rp.roleId = ?1", RolePermission.class, role.id);
        for (RolePermission rp : current) if (!wantedIds.contains(rp.permissionId)) db.remove(rp);
        for (Permission p : wanted) {
            if (current.stream().noneMatch(rp -> rp.permissionId.equals(p.id))) {
                RolePermission rp = new RolePermission();
                rp.roleId = role.id;
                rp.permissionId = p.id;
                db.add(rp);
            }
        }
        db.flush();
        permissions.invalidate();
        return detail(role, companyId);
    }

    public void deleteRole(UUID id) {
        ctx().require("roles:delete");
        UUID companyId = ctx().requireCompanyId();
        Role role = db.first("select r from Role r where r.id = ?1 and r.companyId = ?2", Role.class, id, companyId)
                .orElseThrow(() -> AppException.notFound("Rol bulunamadı"));
        Guard.state(!role.isSystem, "Sistem rolü silinemez");
        Guard.state(!db.exists("select ur.id from UserRole ur where ur.roleId = ?1", id), "Kullanıcıya atanmış rol silinemez");
        db.remove(role);
        db.flush();
    }
}
