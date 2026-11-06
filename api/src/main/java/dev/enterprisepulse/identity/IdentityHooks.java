package dev.enterprisepulse.identity;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.data.generated.UserCompany;
import dev.enterprisepulse.resource.ResourceHook;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.UUID;
import org.springframework.stereotype.Component;

public final class IdentityHooks {
    private IdentityHooks() {}

    private static Predicate membership(CriteriaBuilder cb, AbstractQuery<?> query, Root<?> root, String rootKey, String memberKey, UUID value, String valueKey) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<UserCompany> m = sub.from(UserCompany.class);
        sub.select(cb.literal(1)).where(cb.equal(m.get(memberKey), root.get(rootKey)), cb.equal(m.get(valueKey), value));
        return cb.exists(sub);
    }

    /** Users are global accounts; lists show only the members of the active company. */
    @Component
    public static class UsersHook implements ResourceHook {
        @Override
        public String resource() {
            return "users";
        }

        @Override
        public Predicate scope(CriteriaBuilder cb, AbstractQuery<?> query, Root<?> root) {
            return membership(cb, query, root, "id", "userId", CurrentUser.get().companyId, "companyId");
        }

        @Override
        public void beforeDelete(Object entity) {
            CurrentUser ctx = CurrentUser.get();
            Guard.state(!((User) entity).id.equals(ctx.userId), "Kendi hesabınızı silemezsiniz");
            Guard.state(ctx.superAdmin, "Hesabı tamamen silmek yalnızca süper yöneticiye açıktır; kullanıcıyı şirketten çıkarın");
        }
    }

    @Component
    public static class RolesHook implements ResourceHook {
        @Override
        public String resource() {
            return "roles";
        }

        @Override
        public Predicate scope(CriteriaBuilder cb, AbstractQuery<?> query, Root<?> root) {
            return cb.or(cb.equal(root.get("companyId"), CurrentUser.get().companyId), cb.isNull(root.get("companyId")));
        }
    }

    /** Non super admins only see the companies they belong to. */
    @Component
    public static class CompaniesHook implements ResourceHook {
        @Override
        public String resource() {
            return "companies";
        }

        @Override
        public Predicate scope(CriteriaBuilder cb, AbstractQuery<?> query, Root<?> root) {
            CurrentUser ctx = CurrentUser.get();
            if (ctx.superAdmin) return null;
            return membership(cb, query, root, "id", "companyId", ctx.userId, "userId");
        }
    }
}
