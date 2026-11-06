package dev.enterprisepulse.assets;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.AssetStatus;
import dev.enterprisepulse.data.generated.AssignmentStatus;
import dev.enterprisepulse.platform.Extensibility;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class AssetDashboard implements Extensibility.DashboardProvider {
    private final Db db;

    public AssetDashboard(Db db) {
        this.db = db;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public DashboardSection section() {
        CurrentUser ctx = CurrentUser.get();
        List<DashboardCard> cards = new ArrayList<>();
        UUID own = ctx.userId == null ? null : db.first("select e.id from Employee e where e.userId = ?1", UUID.class, ctx.userId).orElse(null);
        if (own != null) {
            long mine = db.count("select count(a) from AssetAssignment a where a.employeeId = ?1 and a.status = ?2", own, AssignmentStatus.ACTIVE);
            long waiting = db.count("select count(a) from AssetAssignment a where a.employeeId = ?1 and a.status = ?2 and a.acknowledgedAt is null",
                    own, AssignmentStatus.ACTIVE);
            cards.add(new DashboardCard("my-assets", "Zimmetimdeki demirbaşlar", String.valueOf(mine), "Inventory2",
                    waiting > 0 ? waiting + " tutanak onay bekliyor" : null, "/assets/my", waiting > 0 ? "warning" : null));
        }
        if (ctx.has("assets:read")) {
            long total = db.count("select count(a) from Asset a");
            long assigned = db.count("select count(a) from Asset a where a.status = ?1", AssetStatus.ASSIGNED);
            long maintenance = db.count("select count(a) from Asset a where a.status = ?1", AssetStatus.IN_MAINTENANCE);
            cards.add(new DashboardCard("assigned-assets", "Zimmetli demirbaş", assigned + " / " + total, "Laptop", "/r/assets?status=ASSIGNED"));
            if (maintenance > 0) {
                cards.add(new DashboardCard("maintenance-assets", "Bakımdaki demirbaş", String.valueOf(maintenance), "Build", null,
                        "/r/assets?status=IN_MAINTENANCE", "warning"));
            }
        }
        return cards.isEmpty() ? null : new DashboardSection("assets", "Demirbaş", cards);
    }
}
