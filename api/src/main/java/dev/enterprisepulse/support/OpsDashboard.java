package dev.enterprisepulse.support;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BudgetStatus;
import dev.enterprisepulse.data.generated.ContractStatus;
import dev.enterprisepulse.data.generated.TicketStatus;
import dev.enterprisepulse.data.generated.VehicleDocument;
import dev.enterprisepulse.data.generated.VehicleStatus;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardProvider;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

public final class OpsDashboard {
    private OpsDashboard() {}

    private static final List<TicketStatus> OPEN = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING);

    @Component
    @Transactional(readOnly = true)
    public static class Support implements DashboardProvider {
        private final Db db;
        public Support(Db db) { this.db = db; }
        @Override public int order() { return 90; }
        @Override public DashboardSection section() {
            CurrentUser user = CurrentUser.get();
            if (!user.has("support:read") && !user.has("support:update")) return null;
            long open = db.count("select count(t) from Ticket t where t.status in ?1", OPEN);
            long overdue = db.count("select count(t) from Ticket t where t.status in ?1 and t.resolutionDueAt < ?2 and t.pausedAt is null", OPEN, Instant.now());
            return new DashboardSection("support", "Destek", List.of(
                    new DashboardCard("open-tickets", "Açık talep", Long.toString(open), "SupportAgent", "/r/tickets"),
                    new DashboardCard("overdue-tickets", "SLA aşımı", Long.toString(overdue), "Timer", null, "/r/tickets", overdue > 0 ? "warning" : "primary")));
        }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Contract implements DashboardProvider {
        private final Db db;
        public Contract(Db db) { this.db = db; }
        @Override public int order() { return 95; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("contracts:read")) return null;
            long active = db.count("select count(c) from Contract c where c.status = ?1", ContractStatus.ACTIVE);
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            long expiring = db.count("select count(c) from Contract c where c.status = ?1 and c.endDate is not null and c.endDate <= ?2", ContractStatus.ACTIVE, today.plusDays(30));
            return new DashboardSection("contracts", "Sözleşmeler", List.of(
                    new DashboardCard("active-contracts", "Aktif sözleşme", Long.toString(active), "Description", "/r/contracts"),
                    new DashboardCard("expiring-contracts", "Yaklaşan bitiş", Long.toString(expiring), "Event", null, "/r/contracts", expiring > 0 ? "warning" : "primary")));
        }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Budget implements DashboardProvider {
        private final Db db;
        public Budget(Db db) { this.db = db; }
        @Override public int order() { return 100; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("budgets:read")) return null;
            int year = LocalDate.now(ZoneOffset.UTC).getYear();
            long approved = db.count("select count(b) from Budget b where b.year = ?1 and b.status = ?2", year, BudgetStatus.APPROVED);
            long alerts = db.count("select count(a) from BudgetAlert a where a.budgetId in (select b.id from Budget b where b.year = ?1)", year);
            return new DashboardSection("budgets", "Bütçe", List.of(
                    new DashboardCard("approved-budgets", "Onaylı bütçe", Long.toString(approved), "AccountBalance", "/r/budgets"),
                    new DashboardCard("budget-alerts", "Uyarı", Long.toString(alerts), "Warning", null, "/r/budgets", alerts > 0 ? "warning" : "primary")));
        }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Fleet implements DashboardProvider {
        private final Db db;
        public Fleet(Db db) { this.db = db; }
        @Override public int order() { return 105; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("fleet:read")) return null;
            long active = db.count("select count(v) from Vehicle v where v.status = ?1", VehicleStatus.ACTIVE);
            LocalDate limit = LocalDate.now(ZoneOffset.UTC).plusDays(30);
            List<VehicleDocument> docs = db.list("select d from VehicleDocument d where d.vehicleId in (select v.id from Vehicle v where v.status <> ?1)",
                    VehicleDocument.class, VehicleStatus.RETIRED);
            Map<String, VehicleDocument> latest = new LinkedHashMap<>();
            for (VehicleDocument doc : docs) {
                String key = doc.vehicleId + ":" + doc.type;
                VehicleDocument current = latest.get(key);
                if (current == null || doc.endDate.isAfter(current.endDate)) latest.put(key, doc);
            }
            long warnings = latest.values().stream().filter(d -> !d.endDate.isAfter(limit)).count();
            return new DashboardSection("fleet", "Araç ve Filo", List.of(
                    new DashboardCard("active-vehicles", "Aktif araç", Long.toString(active), "DirectionsCar", "/r/vehicles"),
                    new DashboardCard("doc-warnings", "Belge uyarısı", Long.toString(warnings), "FactCheck", null, "/r/vehicles", warnings > 0 ? "warning" : "primary")));
        }
    }
}
