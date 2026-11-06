package dev.enterprisepulse.common;

import dev.enterprisepulse.data.Tenancy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Identity and tenant of the running request or job. Lives in a thread local ({@link #get()}); the API
 * filter fills it per request, jobs and the seeder use {@link #actAs}.
 */
public final class CurrentUser {
    private static final ThreadLocal<CurrentUser> HOLDER = ThreadLocal.withInitial(CurrentUser::new);

    public UUID userId;
    public String email;
    public String fullName;
    public UUID companyId;
    public UUID branchId;
    public boolean superAdmin;
    public Set<String> permissions = new HashSet<>();
    public String ip;
    public String userAgent;
    public String requestId = UUID.randomUUID().toString().replace("-", "");
    public String locale = "tr";
    /** When true the tenant restriction is off (login, cross-company jobs). */
    public boolean bypassTenant;

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static CurrentUser reset() {
        HOLDER.remove();
        return HOLDER.get();
    }

    public UUID requireUserId() {
        if (userId == null) throw AppException.unauthorized();
        return userId;
    }

    public UUID requireCompanyId() {
        if (companyId == null) throw AppException.badRequest("Aktif şirket seçilmedi");
        return companyId;
    }

    public boolean has(String code) {
        return superAdmin || permissions.contains(code);
    }

    public boolean hasAny(String... codes) {
        return superAdmin || Arrays.stream(codes).anyMatch(permissions::contains);
    }

    public void require(String code) {
        if (!has(code)) throw AppException.forbidden("Missing permission: " + code);
    }

    /** Runs as a company/user (jobs, seed); closing restores the previous identity. */
    public Scope actAs(UUID company, UUID user, boolean admin) {
        UUID c = companyId, u = userId;
        boolean a = superAdmin, b = bypassTenant;
        companyId = company;
        userId = user;
        superAdmin = admin;
        bypassTenant = false;
        Tenancy.reapply();
        return () -> {
            companyId = c;
            userId = u;
            superAdmin = a;
            bypassTenant = b;
            Tenancy.reapply();
        };
    }

    public Scope withoutTenant() {
        boolean previous = bypassTenant;
        bypassTenant = true;
        Tenancy.reapply();
        return () -> {
            bypassTenant = previous;
            Tenancy.reapply();
        };
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
