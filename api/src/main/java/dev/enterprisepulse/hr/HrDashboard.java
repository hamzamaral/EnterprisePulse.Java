package dev.enterprisepulse.hr;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.data.generated.LeaveRequestStatus;
import dev.enterprisepulse.platform.Extensibility;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class HrDashboard implements Extensibility.DashboardProvider {
    private final Db db;

    public HrDashboard(Db db) {
        this.db = db;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public DashboardSection section() {
        CurrentUser ctx = CurrentUser.get();
        List<DashboardCard> cards = new ArrayList<>();
        LocalDate today = LocalDate.ofInstant(Instant.now().plusSeconds(3 * 3600), ZoneOffset.UTC);
        UUID own = db.first("select e.id from Employee e where e.userId = ?1", UUID.class, ctx.userId).orElse(null);
        if (own != null) {
            db.first("select b.entitledDays - b.usedDays from LeaveBalance b join LeaveType t on t.id = b.leaveTypeId where b.employeeId = ?1 and b.year = ?2 and t.code = 'YILLIK'",
                    Integer.class, own, today.getYear()).ifPresent(annual ->
                    cards.add(new DashboardCard("my-leave", "Kalan yıllık izin", annual + " gün", "BeachAccess", null, "/hr/my-leave", "info")));
        }
        if (ctx.has("employees:read")) {
            cards.add(new DashboardCard("employees", "Aktif personel",
                    String.valueOf(db.count("select count(e) from Employee e where e.status = ?1", EmployeeStatus.ACTIVE)), "Badge", "/r/employees"));
        }
        if (ctx.has("leave:read")) {
            long onLeave = db.count("select count(r) from LeaveRequest r where r.status = ?1 and r.startDate <= ?2 and r.endDate >= ?2", LeaveRequestStatus.APPROVED, today);
            long pending = db.count("select count(r) from LeaveRequest r where r.status = ?1", LeaveRequestStatus.PENDING);
            cards.add(new DashboardCard("on-leave", "Bugün izinde", String.valueOf(onLeave), "EventBusy", "/r/leave-requests?status=APPROVED"));
            cards.add(new DashboardCard("leave-pending", "Bekleyen izin talebi", String.valueOf(pending), "HourglassTop", null, "/r/leave-requests?status=PENDING", "warning"));
        }
        return cards.isEmpty() ? null : new DashboardSection("hr", "İnsan Kaynakları", cards);
    }
}
