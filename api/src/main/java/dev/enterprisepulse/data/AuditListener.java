package dev.enterprisepulse.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.generated.Company;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

/** Writes an audit row for every insert, update and (soft) delete, with old/new values and secrets redacted. */
@Component
public class AuditListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {
    private static final Set<String> SKIPPED = Set.of(
            "AuditLog", "Notification", "RefreshToken", "PasswordResetToken", "NumberSequence",
            "StockBalance", "LotBalance", "StockTransaction", "StockMovementLine",
            "StockMovementSerial", "StockCountLine", "JournalLine");
    private static final Set<String> REDACTED = Set.of("passwordHash", "twoFactorSecret", "tokenHash");
    private static final Set<String> IGNORED = Set.of("createdAt", "updatedAt");

    private final ObjectMapper json;

    public AuditListener(EntityManagerFactory emf, ObjectMapper json) {
        this.json = json;
        var registry = emf.unwrap(SessionFactoryImpl.class).getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
        registry.appendListeners(EventType.POST_DELETE, this);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        Map<String, Object> values = new LinkedHashMap<>();
        String[] names = event.getPersister().getPropertyNames();
        for (int i = 0; i < names.length; i++) put(values, names[i], event.getState()[i]);
        write(event.getSession(), event.getEntity(), "create", null, values);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (event.getOldState() == null) return;
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        String[] names = event.getPersister().getPropertyNames();
        boolean softDeleted = false;
        for (int i = 0; i < names.length; i++) {
            Object old = event.getOldState()[i], now = event.getState()[i];
            if (IGNORED.contains(names[i]) || Objects.equals(old, now)) continue;
            if (names[i].equals("deletedAt") && old == null && now != null) softDeleted = true;
            put(before, names[i], old);
            put(after, names[i], now);
        }
        if (after.isEmpty()) return;
        write(event.getSession(), event.getEntity(), softDeleted ? "delete" : "update", before, after);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        Map<String, Object> values = new LinkedHashMap<>();
        String[] names = event.getPersister().getPropertyNames();
        for (int i = 0; i < names.length; i++) put(values, names[i], event.getDeletedState()[i]);
        write(event.getSession(), event.getEntity(), "delete", values, null);
    }

    private static void put(Map<String, Object> map, String name, Object value) {
        if (IGNORED.contains(name)) return;
        String key = name.endsWith("_") ? name.substring(0, name.length() - 1) : name;
        map.put(key, REDACTED.contains(name) && value != null ? "[REDACTED]" : value);
    }

    private void write(SharedSessionContractImplementor session, Object entity, String action, Map<String, Object> before, Map<String, Object> after) {
        String type = entity.getClass().getSimpleName();
        if (SKIPPED.contains(type)) return;
        CurrentUser ctx = CurrentUser.get();
        UUID companyId = entity instanceof TenantEntity t ? t.getCompanyId() : entity instanceof Company c ? c.id : ctx.companyId;
        String entityId = entity instanceof Identified e ? String.valueOf(e.getId()) : null;
        Connection connection = session.getJdbcCoordinator().getLogicalConnection().getPhysicalConnection();
        String sql = "insert into audit_logs (id, company_id, user_id, user_email, action, entity_type, entity_id, old_values, new_values, ip, user_agent, request_id, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)";
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setObject(1, UUID.randomUUID());
            st.setObject(2, companyId, Types.OTHER);
            st.setObject(3, ctx.userId, Types.OTHER);
            st.setString(4, ctx.email);
            st.setString(5, action);
            st.setString(6, type);
            st.setString(7, entityId);
            st.setString(8, before == null ? null : json.writeValueAsString(before));
            st.setString(9, after == null ? null : json.writeValueAsString(after));
            st.setString(10, ctx.ip);
            st.setString(11, ctx.userAgent);
            st.setString(12, ctx.requestId);
            st.setTimestamp(13, Timestamp.from(Instant.now()));
            st.executeUpdate();
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Audit write failed", e);
        }
    }
}
