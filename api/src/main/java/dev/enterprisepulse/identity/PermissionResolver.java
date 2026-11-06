package dev.enterprisepulse.identity;

import dev.enterprisepulse.data.Db;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Permission codes of a user in a company, cached for a minute. */
@Component
public class PermissionResolver {
    private record Entry(Set<String> codes, long expiresAt) {}

    private final Db db;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public PermissionResolver(Db db) {
        this.db = db;
    }

    @Transactional(readOnly = true)
    public Set<String> resolve(UUID userId, UUID companyId) {
        String key = userId + ":" + companyId;
        Entry entry = cache.get(key);
        if (entry != null && entry.expiresAt() > System.currentTimeMillis()) return entry.codes();
        Set<String> codes = new HashSet<>(db.list("""
                select distinct p.code from UserRole ur
                  join Role r on r.id = ur.roleId
                  join RolePermission rp on rp.roleId = r.id
                  join Permission p on p.id = rp.permissionId
                 where ur.userId = ?1 and ur.companyId = ?2""", String.class, userId, companyId));
        cache.put(key, new Entry(codes, System.currentTimeMillis() + 60_000));
        return codes;
    }

    public void invalidate() {
        cache.clear();
    }
}
