package dev.enterprisepulse.data;

import java.time.Instant;

public interface SoftDelete {
    Instant getDeletedAt();

    void setDeletedAt(Instant value);
}
