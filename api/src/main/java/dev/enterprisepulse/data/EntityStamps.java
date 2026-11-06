package dev.enterprisepulse.data;

import dev.enterprisepulse.common.CurrentUser;
import jakarta.persistence.PrePersist;

public class EntityStamps {
    @PrePersist
    public void beforeInsert(Object entity) {
        if (entity instanceof TenantEntity t && t.getCompanyId() == null) {
            t.setCompanyId(CurrentUser.get().requireCompanyId());
        }
    }
}
