package dev.enterprisepulse.platform;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.WorkflowStatus;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import dev.enterprisepulse.workflow.WorkflowService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class PlatformDashboard implements Extensibility.DashboardProvider {
    private final Db db;
    private final WorkflowService workflow;

    public PlatformDashboard(Db db, WorkflowService workflow) {
        this.db = db;
        this.workflow = workflow;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public DashboardSection section() {
        CurrentUser ctx = CurrentUser.get();
        UUID userId = ctx.requireUserId();
        List<DashboardCard> cards = new ArrayList<>();
        cards.add(new DashboardCard("approvals", "Onayımı bekleyenler", String.valueOf(workflow.inboxCount()), "TaskAlt", null, "/workflow/inbox", "warning"));
        cards.add(new DashboardCard("notifications", "Okunmamış bildirim",
                String.valueOf(db.count("select count(n) from Notification n where n.userId = ?1 and n.readAt is null", userId)), "Notifications", "/notifications"));
        if (ctx.has("users:read")) {
            long users = db.count("select count(m) from UserCompany m join User u on u.id = m.userId where m.companyId = ?1 and u.isActive = true", ctx.requireCompanyId());
            cards.add(new DashboardCard("users", "Aktif kullanıcı", String.valueOf(users), "People", "/r/users"));
        }
        if (ctx.has("workflow:read")) {
            cards.add(new DashboardCard("running", "Devam eden onay süreçleri",
                    String.valueOf(db.count("select count(i) from WorkflowInstance i where i.status = ?1", WorkflowStatus.PENDING)), "AccountTree", "/workflow/instances"));
        }
        return new DashboardSection("platform", "Genel", cards);
    }
}
