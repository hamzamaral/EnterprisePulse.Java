package dev.enterprisepulse.crm;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.CrmActivity;
import dev.enterprisepulse.data.generated.ExpenseReportStatus;
import dev.enterprisepulse.data.generated.LeadStatus;
import dev.enterprisepulse.data.generated.Opportunity;
import dev.enterprisepulse.data.generated.OpportunityStage;
import dev.enterprisepulse.data.generated.ProjectStatus;
import dev.enterprisepulse.data.generated.ProjectTaskStatus;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardProvider;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

public final class WorkDashboard {
    private WorkDashboard() {}

    private static String money(BigDecimal value) {
        return String.format(Locale.forLanguageTag("tr"), "%,.2f", value);
    }

    @Component
    @Transactional(readOnly = true)
    public static class Crm implements DashboardProvider {
        private final Db db;
        public Crm(Db db) { this.db = db; }
        @Override public int order() { return 50; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("crm:read")) return null;
            long openLeads = db.count("select count(l) from Lead l where l.status = ?1 or l.status = ?2 or l.status = ?3", LeadStatus.NEW, LeadStatus.CONTACTED, LeadStatus.QUALIFIED);
            List<Opportunity> pipeline = db.list("select o from Opportunity o where o.stage <> ?1 and o.stage <> ?2", Opportunity.class, OpportunityStage.WON, OpportunityStage.LOST);
            BigDecimal weighted = pipeline.stream().map(o -> o.amount.multiply(BigDecimal.valueOf(o.probability)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)).reduce(BigDecimal.ZERO, BigDecimal::add);
            long overdue = db.list("select a from CrmActivity a where a.completedAt is null and a.dueAt is not null and a.dueAt < ?1", CrmActivity.class, Instant.now()).size();
            return new DashboardSection("crm", "CRM", List.of(
                    new DashboardCard("open-leads", "Açık potansiyel", String.valueOf(openLeads), "PersonAdd", "/r/leads"),
                    new DashboardCard("pipeline", "Ağırlıklı fırsat", money(weighted), "TrendingUp", pipeline.size() + " fırsat", "/r/opportunities", "primary"),
                    new DashboardCard("overdue-activities", "Geciken aktivite", String.valueOf(overdue), "EventBusy", null, "/r/crm-activities", overdue > 0 ? "warning" : "primary")));
        }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Expenses implements DashboardProvider {
        private final Db db;
        public Expenses(Db db) { this.db = db; }
        @Override public int order() { return 55; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("expenses:read") && !CurrentUser.get().has("expenses:pay")) return null;
            long pending = db.count("select count(r) from ExpenseReport r where r.status = ?1 or r.status = ?2", ExpenseReportStatus.PENDING, ExpenseReportStatus.APPROVED);
            List<BigDecimal> sums = db.list("select coalesce(sum(r.totalAmount), 0) from ExpenseReport r where r.status = ?1", BigDecimal.class, ExpenseReportStatus.APPROVED);
            BigDecimal approved = sums.isEmpty() || sums.get(0) == null ? BigDecimal.ZERO : sums.get(0);
            return new DashboardSection("expenses", "Masraf ve Harcırah", List.of(
                    new DashboardCard("open-expenses", "Bekleyen masraf", String.valueOf(pending), "Receipt", "/r/expense-reports"),
                    new DashboardCard("payable-expenses", "Ödenecek masraf", money(approved), "Payments", "/r/expense-reports")));
        }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Projects implements DashboardProvider {
        private final Db db;
        public Projects(Db db) { this.db = db; }
        @Override public int order() { return 60; }
        @Override public DashboardSection section() {
            UUID employeeId = db.first("select e.id from Employee e where e.userId = ?1", UUID.class, CurrentUser.get().userId).orElse(null);
            long myTasks = employeeId == null ? 0 : db.count("select count(t) from ProjectTask t where t.assigneeId = ?1 and t.status <> ?2", employeeId, ProjectTaskStatus.DONE);
            if (!CurrentUser.get().has("projects:read") && myTasks == 0) return null;
            long active = CurrentUser.get().has("projects:read") ? db.count("select count(p) from Project p where p.status = ?1", ProjectStatus.ACTIVE) : 0;
            return new DashboardSection("projects", "Projeler", List.of(
                    new DashboardCard("active-projects", "Aktif proje", String.valueOf(active), "AccountTree", "/r/projects"),
                    new DashboardCard("my-tasks", "Görevlerim", String.valueOf(myTasks), "TaskAlt", "/projects/my-tasks")));
        }
    }
}
