package dev.enterprisepulse.data;

import java.util.UUID;

/** Rows owned by one company; filtered by {@link Tenancy} and stamped by {@link EntityStamps}. */
public interface TenantEntity {
    UUID getCompanyId();

    void setCompanyId(UUID value);
}
