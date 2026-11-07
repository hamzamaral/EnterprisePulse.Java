package dev.enterprisepulse.payroll;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.ApplicationStage;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.data.generated.JobPostingStatus;
import dev.enterprisepulse.data.generated.PayrollPeriod;
import dev.enterprisepulse.data.generated.PerformanceReviewStatus;
import dev.enterprisepulse.data.generated.TrainingCourse;
import dev.enterprisepulse.data.generated.TrainingEnrollment;
import dev.enterprisepulse.data.generated.TrainingEnrollmentStatus;
import dev.enterprisepulse.data.generated.TrainingSession;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardProvider;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

public final class PeopleDashboard {
    private PeopleDashboard() {}

    private static String money(java.math.BigDecimal value) {
        return String.format(Locale.forLanguageTag("tr"), "%,.2f", value);
    }

    @Component
    @Transactional(readOnly = true)
    public static class Payroll implements DashboardProvider {
        private final Db db;
        public Payroll(Db db) { this.db = db; }
        @Override public int order() { return 70; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("payroll:read")) return null;
            PayrollPeriod period = db.first("select p from PayrollPeriod p order by p.year desc, p.month desc", PayrollPeriod.class).orElse(null);
            String label = period == null ? "Dönem yok" : period.year + "-" + String.format("%02d", period.month) + " " + period.status;
            return new DashboardSection("payroll", "Bordro", List.of(new DashboardCard("last-payroll", "Son bordro net", money(period == null ? java.math.BigDecimal.ZERO : period.netTotal), "Payments", label, "/r/payroll-periods", "primary")));
        }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Recruitment implements DashboardProvider {
        private final Db db;
        public Recruitment(Db db) { this.db = db; }
        @Override public int order() { return 75; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("recruitment:read")) return null;
            long open = db.count("select count(p) from JobPosting p where p.status = ?1", JobPostingStatus.OPEN);
            long pipeline = db.count("select count(a) from JobApplication a where a.stage <> ?1 and a.stage <> ?2 and a.stage <> ?3", ApplicationStage.HIRED, ApplicationStage.REJECTED, ApplicationStage.WITHDRAWN);
            return new DashboardSection("recruitment", "İşe Alım", List.of(
                    new DashboardCard("open-postings", "Açık ilan", Long.toString(open), "Work", "/r/job-postings"),
                    new DashboardCard("open-applications", "Süren başvuru", Long.toString(pipeline), "PersonSearch", "/r/job-applications")));
        }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Performance implements DashboardProvider {
        private final Db db;
        public Performance(Db db) { this.db = db; }
        @Override public int order() { return 80; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("performance:read")) return null;
            long open = db.count("select count(r) from PerformanceReview r where r.status <> ?1", PerformanceReviewStatus.COMPLETED);
            long done = db.count("select count(r) from PerformanceReview r where r.status = ?1", PerformanceReviewStatus.COMPLETED);
            return new DashboardSection("performance", "Performans", List.of(
                    new DashboardCard("open-reviews", "Açık değerlendirme", Long.toString(open), "RateReview", "/performance/my"),
                    new DashboardCard("done-reviews", "Tamamlanan", Long.toString(done), "TaskAlt", "/r/review-cycles")));
        }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Training implements DashboardProvider {
        private final Db db;
        public Training(Db db) { this.db = db; }
        @Override public int order() { return 85; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("training:read")) return null;
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            List<TrainingCourse> courses = db.list("select c from TrainingCourse c where c.isMandatory = true and c.isActive = true", TrainingCourse.class);
            long employees = db.count("select count(e) from Employee e where e.status = ?1", EmployeeStatus.ACTIVE);
            Set<String> covered = new HashSet<>();
            for (TrainingEnrollment enrollment : db.list("select e from TrainingEnrollment e where e.status = ?1", TrainingEnrollment.class, TrainingEnrollmentStatus.ATTENDED)) {
                if (enrollment.certificateExpiresAt != null && enrollment.certificateExpiresAt.isBefore(today)) continue;
                TrainingSession session = db.get(TrainingSession.class, enrollment.sessionId);
                if (courses.stream().anyMatch(c -> c.id.equals(session.courseId))) covered.add(enrollment.employeeId + ":" + session.courseId);
            }
            long gaps = Math.max(0, employees * courses.size() - covered.size());
            return new DashboardSection("training", "Eğitim", List.of(
                    new DashboardCard("training-gaps", "Zorunlu eğitim açığı", Long.toString(gaps), "School", null, "/training/compliance", gaps > 0 ? "warning" : "primary")));
        }
    }
}
