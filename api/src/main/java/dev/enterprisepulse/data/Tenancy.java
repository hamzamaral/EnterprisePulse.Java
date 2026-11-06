package dev.enterprisepulse.data;

import dev.enterprisepulse.common.CurrentUser;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Row-level tenant and soft-delete filtering. Entities carry a {@code @SQLRestriction} that reads the
 * transaction-local settings {@code ep.company} / {@code ep.bypass} (see V2 migration), which are set at
 * the start of every transaction and whenever {@link CurrentUser} switches company.
 */
public final class Tenancy {
    public static final String TENANT = "(ep_bypass() or company_id = ep_company())";
    public static final String LIVE = "deleted_at is null";
    public static final String TENANT_LIVE = "(ep_bypass() or company_id = ep_company()) and deleted_at is null";

    private static volatile EntityManagerFactory factory;

    private Tenancy() {}

    static void bind(EntityManagerFactory emf) {
        factory = emf;
    }

    public static void apply(Connection connection) throws SQLException {
        CurrentUser ctx = CurrentUser.get();
        try (PreparedStatement st = connection.prepareStatement("select set_config('ep.company', ?, true), set_config('ep.bypass', ?, true)")) {
            st.setString(1, ctx.companyId == null ? "" : ctx.companyId.toString());
            st.setString(2, ctx.bypassTenant ? "on" : "off");
            st.execute();
        }
    }

    /** Pushes the current identity into the running transaction, if any. */
    public static void reapply() {
        EntityManagerFactory emf = factory;
        if (emf == null) return;
        if (TransactionSynchronizationManager.getResource(emf) instanceof EntityManagerHolder holder) {
            holder.getEntityManager().unwrap(Session.class).doWork(Tenancy::apply);
        }
    }
}
