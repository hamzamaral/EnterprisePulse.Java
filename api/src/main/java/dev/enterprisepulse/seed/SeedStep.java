package dev.enterprisepulse.seed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Demo data for one module; each step must be idempotent because the seeder runs on every start. */
public interface SeedStep {
    int order();

    void run(Context ctx);

    final class Context {
        public UUID pulseId;
        public UUID logisticsId;
        public UUID hqBranchId;
        public UUID adminId;
        public Map<String, UUID> pulseRoles = new LinkedHashMap<>();
        public final Map<String, UUID> usersByName = new LinkedHashMap<>();
    }
}
