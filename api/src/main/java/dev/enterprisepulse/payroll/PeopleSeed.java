package dev.enterprisepulse.payroll;

import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.CandidateSource;
import dev.enterprisepulse.data.generated.Competency;
import dev.enterprisepulse.data.generated.CompetencyRating;
import dev.enterprisepulse.data.generated.Department;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmploymentType;
import dev.enterprisepulse.data.generated.InterviewRecommendation;
import dev.enterprisepulse.data.generated.InterviewType;
import dev.enterprisepulse.data.generated.PerformanceGoal;
import dev.enterprisepulse.data.generated.Position;
import dev.enterprisepulse.data.generated.TrainingCategory;
import dev.enterprisepulse.data.generated.TrainingCourse;
import dev.enterprisepulse.data.generated.TrainingEnrollmentStatus;
import dev.enterprisepulse.data.generated.TrainingFormat;
import dev.enterprisepulse.data.generated.TrainingSession;
import dev.enterprisepulse.data.generated.TreasuryAccount;
import dev.enterprisepulse.performance.PerformanceService;
import dev.enterprisepulse.recruitment.RecruitmentService;
import dev.enterprisepulse.seed.SeedStep;
import dev.enterprisepulse.training.TrainingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** October payroll for two salaries, one hired candidate, one completed review and a mandatory course. */
@Component
public class PeopleSeed implements SeedStep {
    private final Db db;
    private final PayrollService payroll;
    private final RecruitmentService recruitment;
    private final PerformanceService performance;
    private final TrainingService training;

    public PeopleSeed(Db db, PayrollService payroll, RecruitmentService recruitment, PerformanceService performance, TrainingService training) {
        this.db = db;
        this.payroll = payroll;
        this.recruitment = recruitment;
        this.performance = performance;
        this.training = training;
    }

    @Override public int order() { return 60; }

    @Override
    public void run(Context ctx) {
        Employee deniz = db.first("select e from Employee e where e.firstName = 'Deniz' and e.lastName = 'Aksoy'", Employee.class).orElse(null);
        Employee kerem = db.first("select e from Employee e where e.firstName = 'Kerem' and e.lastName = 'Polat'", Employee.class).orElse(null);
        if (deniz == null || kerem == null) return;
        LocalDate today = LocalDate.now();
        payroll(deniz, kerem, today);
        recruitment(deniz, today);
        performance(kerem, today);
        training(kerem);
    }

    private void payroll(Employee deniz, Employee kerem, LocalDate today) {
        if (db.exists("select p.id from PayrollPeriod p where p.year = ?1 and p.month = 10", today.getYear())) return;
        payroll.saveParameter(new PayrollService.SaveParameter(today.getYear(), new BigDecimal("26005.50"), new BigDecimal("195041.40"),
                new BigDecimal("0.14"), new BigDecimal("0.01"), new BigDecimal("0.1675"), new BigDecimal("0.02"), new BigDecimal("0.00759"),
                new BigDecimal("1.5"), 225, List.of(new PayrollService.TaxBracket(new BigDecimal("158000"), new BigDecimal("0.15")), new PayrollService.TaxBracket(null, new BigDecimal("0.20")))));
        payroll.saveSalary(new PayrollService.SaveSalary(deniz.id, LocalDate.of(today.getYear(), 10, 1), new BigDecimal("90000"), "Yıllık ücret"));
        payroll.saveSalary(new PayrollService.SaveSalary(kerem.id, LocalDate.of(today.getYear(), 10, 1), new BigDecimal("55000"), "Yıllık ücret"));
        PayrollService.PeriodDto period = payroll.open(today.getYear(), 10);
        payroll.approve(period.id());
        TreasuryAccount bank = db.first("select a from TreasuryAccount a where a.code = 'BANK-TRY'", TreasuryAccount.class).orElse(null);
        if (bank != null) payroll.pay(period.id(), new PayrollService.PayPayroll(bank.id, today.minusDays(1)));
    }

    private void recruitment(Employee deniz, LocalDate today) {
        if (db.exists("select p.id from JobPosting p where p.title = 'Kıdemli yazılım geliştirici'")) return;
        Department department = db.first("select d from Department d where d.code = 'BT'", Department.class).orElse(null);
        Position position = db.first("select p from Position p where p.code = 'YAZ'", Position.class).orElse(null);
        RecruitmentService.PostingDto posting = recruitment.createPosting(new RecruitmentService.SavePosting("Kıdemli yazılım geliştirici", department == null ? null : department.id, position == null ? null : position.id,
                null, EmploymentType.FULL_TIME, "İstanbul", "Mağaza ve depo yazılımı.", 1, deniz.id, new BigDecimal("70000"), new BigDecimal("90000"), today.plusDays(30)));
        recruitment.publish(posting.id());
        var candidate = recruitment.createCandidate(new RecruitmentService.SaveCandidate("Ece", "Yılmaz", "ece.yilmaz@example", "0532 000 00 00", "İstanbul", CandidateSource.LINKEDIN, "Yazılım geliştirici", new BigDecimal("75000")));
        RecruitmentService.ApplicationDto application = recruitment.apply(posting.id(), candidate.id);
        recruitment.move(application.id(), dev.enterprisepulse.data.generated.ApplicationStage.SCREENING, null);
        recruitment.scheduleInterview(application.id(), new RecruitmentService.ScheduleInterview(InterviewType.TECHNICAL, Instant.now().plus(1, ChronoUnit.DAYS), 60, "Levent", List.of(deniz.id)));
        UUID interviewId = db.one("select i.id from Interview i where i.applicationId = ?1", UUID.class, application.id());
        recruitment.feedback(interviewId, new RecruitmentService.InterviewFeedback(4, InterviewRecommendation.YES, "Teknik görüşme olumlu."));
        recruitment.offer(application.id(), new RecruitmentService.SaveOffer(new BigDecimal("72000"), today, today.plusDays(7), null));
        UUID offerId = db.one("select o.id from JobOffer o where o.applicationId = ?1", UUID.class, application.id());
        recruitment.respondOffer(offerId, true);
        recruitment.hire(application.id());
        if (!db.exists("select p.id from JobPosting p where p.title = 'Sistem yöneticisi'")) {
            RecruitmentService.PostingDto open = recruitment.createPosting(new RecruitmentService.SavePosting("Sistem yöneticisi", department == null ? null : department.id, null, null,
                    EmploymentType.FULL_TIME, "İstanbul", "Altyapı ve destek.", 1, deniz.id, new BigDecimal("60000"), new BigDecimal("80000"), today.plusDays(45)));
            recruitment.publish(open.id());
        }
    }

    private void performance(Employee kerem, LocalDate today) {
        if (db.exists("select c.id from ReviewCycle c where c.name = '2026 Değerlendirme'")) return;
        UUID communication = competency("İletişim", 1);
        UUID technical = competency("Teknik yetkinlik", 2);
        var cycle = performance.createCycle(new PerformanceService.SaveCycle("2026 Değerlendirme", LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31),
                today.plusDays(14), today.plusDays(28), 60, List.of(communication, technical)));
        performance.launch(cycle.id);
        UUID reviewId = db.one("select r.id from PerformanceReview r where r.cycleId = ?1 and r.employeeId = ?2", UUID.class, cycle.id, kerem.id);
        performance.addGoal(reviewId, new PerformanceService.SaveGoal("Mağaza kurulumunu zamanında teslim etmek", 60));
        performance.addGoal(reviewId, new PerformanceService.SaveGoal("Saha hatalarını azaltmak", 40));
        List<UUID> goals = new ArrayList<>();
        for (PerformanceGoal goal : db.list("select g from PerformanceGoal g where g.reviewId = ?1 order by g.sortOrder", PerformanceGoal.class, reviewId)) goals.add(goal.id);
        List<PerformanceService.RatingInput> goalRatings = new ArrayList<>();
        List<PerformanceService.RatingInput> managerGoals = new ArrayList<>();
        for (int i = 0; i < goals.size(); i++) {
            goalRatings.add(new PerformanceService.RatingInput(goals.get(i), 4));
            managerGoals.add(new PerformanceService.RatingInput(goals.get(i), i == 0 ? 5 : 4));
        }
        List<PerformanceService.RatingInput> competencyRatings = new ArrayList<>();
        for (CompetencyRating rating : db.list("select r from CompetencyRating r where r.reviewId = ?1", CompetencyRating.class, reviewId))
            competencyRatings.add(new PerformanceService.RatingInput(rating.id, 4));
        performance.submitSelf(reviewId, new PerformanceService.SelfReview("Kurulum sahada ilerledi.", goalRatings, competencyRatings));
        performance.submitManager(reviewId, new PerformanceService.ManagerReview("Hedefler tuttu.", "Saha disiplini", "Dokümantasyon", managerGoals, competencyRatings));
    }

    private UUID competency(String name, int sort) {
        Competency row = db.first("select c from Competency c where c.name = ?1", Competency.class, name).orElse(null);
        if (row != null) return row.id;
        row = new Competency();
        row.name = name;
        row.sortOrder = sort;
        db.add(row);
        db.flush();
        return row.id;
    }

    private void training(Employee kerem) {
        if (db.exists("select c.id from TrainingCourse c where c.code = 'ISG'")) return;
        TrainingCourse course = new TrainingCourse();
        course.code = "ISG";
        course.title = "İş güvenliği";
        course.category = TrainingCategory.OCCUPATIONAL_SAFETY;
        course.format = TrainingFormat.CLASSROOM;
        course.durationHours = new BigDecimal("8");
        course.isMandatory = true;
        course.validityMonths = 12;
        db.add(course);
        db.flush();
        TrainingSession done = training.createSession(new TrainingService.SaveSession(course.id, Instant.now().minus(20, ChronoUnit.DAYS), Instant.now().minus(20, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS), "Levent", "Deniz Aksoy", 12, BigDecimal.ZERO, false));
        training.enroll(done.id, kerem.id);
        training.complete(done.id, List.of(new TrainingService.AttendanceLine(kerem.id, TrainingEnrollmentStatus.ATTENDED, 90)));
        TrainingSession planned = training.createSession(new TrainingService.SaveSession(course.id, Instant.now().plus(7, ChronoUnit.DAYS), Instant.now().plus(7, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS), "Levent", "Deniz Aksoy", 12, BigDecimal.ZERO, true));
        training.enroll(planned.id, kerem.id);
    }
}
