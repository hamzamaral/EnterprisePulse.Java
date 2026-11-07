package dev.enterprisepulse.web;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.payroll.PayrollService;
import dev.enterprisepulse.performance.PerformanceService;
import dev.enterprisepulse.recruitment.RecruitmentService;
import dev.enterprisepulse.training.TrainingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "People")
public class PeopleController {
    private final PayrollService payroll;
    private final RecruitmentService recruitment;
    private final PerformanceService performance;
    private final TrainingService training;

    public PeopleController(PayrollService payroll, RecruitmentService recruitment, PerformanceService performance, TrainingService training) {
        this.payroll = payroll;
        this.recruitment = recruitment;
        this.performance = performance;
        this.training = training;
    }

    private static void require(String code) { CurrentUser.get().require(code); }

    @PutMapping("/api/payroll/parameters") public Object parameters(@RequestBody PayrollService.SaveParameter body) { require("payroll:update"); return payroll.saveParameter(body); }
    @PostMapping("/api/payroll/salaries") public PayrollService.SalaryDto salary(@RequestBody PayrollService.SaveSalary body) { require("payroll:update"); return payroll.saveSalary(body); }
    @PostMapping("/api/payroll/periods") public PayrollService.PeriodDto open(@RequestBody OpenPeriod body) { require("payroll:create"); return payroll.open(body.year(), body.month()); }
    @GetMapping("/api/payroll/periods/{id}") public PayrollService.PeriodDto period(@PathVariable UUID id) { require("payroll:read"); return payroll.get(id); }
    @PostMapping("/api/payroll/payslips/{id}") public PayrollService.PeriodDto adjust(@PathVariable UUID id, @RequestBody PayrollService.PayslipAdjust body) { require("payroll:update"); return payroll.adjust(id, body); }
    @PostMapping("/api/payroll/periods/{id}/approve") public PayrollService.PeriodDto approve(@PathVariable UUID id) { require("payroll:approve"); return payroll.approve(id); }
    @PostMapping("/api/payroll/periods/{id}/pay") public PayrollService.PeriodDto pay(@PathVariable UUID id, @RequestBody PayrollService.PayPayroll body) { require("payroll:pay"); return payroll.pay(id, body); }
    @GetMapping("/api/payroll/mine") public PayrollService.Mine minePayroll() { return payroll.mine(); }

    @PostMapping("/api/recruitment/postings") public RecruitmentService.PostingDto posting(@RequestBody RecruitmentService.SavePosting body) { require("recruitment:create"); return recruitment.createPosting(body); }
    @PostMapping("/api/recruitment/postings/{id}/publish") public RecruitmentService.PostingDto publish(@PathVariable UUID id) { require("recruitment:update"); return recruitment.publish(id); }
    @PostMapping("/api/recruitment/candidates") public Object candidate(@RequestBody RecruitmentService.SaveCandidate body) { require("recruitment:create"); return recruitment.createCandidate(body); }
    @PostMapping("/api/recruitment/postings/{id}/applications") public RecruitmentService.ApplicationDto apply(@PathVariable UUID id, @RequestBody ApplyBody body) { require("recruitment:create"); return recruitment.apply(id, body.candidateId()); }
    @GetMapping("/api/recruitment/applications/{id}") public RecruitmentService.ApplicationDto application(@PathVariable UUID id) { require("recruitment:read"); return recruitment.application(id); }
    @PostMapping("/api/recruitment/applications/{id}/stage") public RecruitmentService.ApplicationDto stage(@PathVariable UUID id, @RequestBody RecruitmentService.MoveApplication body) { require("recruitment:update"); return recruitment.move(id, body.stage(), body.rejectReason()); }
    @PostMapping("/api/recruitment/applications/{id}/interviews") public RecruitmentService.ApplicationDto interview(@PathVariable UUID id, @RequestBody RecruitmentService.ScheduleInterview body) { require("recruitment:update"); return recruitment.scheduleInterview(id, body); }
    @PostMapping("/api/recruitment/interviews/{id}/feedback") public void feedback(@PathVariable UUID id, @RequestBody RecruitmentService.InterviewFeedback body) { require("recruitment:update"); recruitment.feedback(id, body); }
    @PostMapping("/api/recruitment/applications/{id}/offers") public RecruitmentService.ApplicationDto offer(@PathVariable UUID id, @RequestBody RecruitmentService.SaveOffer body) { require("recruitment:update"); return recruitment.offer(id, body); }
    @PostMapping("/api/recruitment/offers/{id}/accept") public RecruitmentService.ApplicationDto accept(@PathVariable UUID id) { require("recruitment:update"); return recruitment.respondOffer(id, true); }
    @PostMapping("/api/recruitment/applications/{id}/hire") public RecruitmentService.ApplicationDto hire(@PathVariable UUID id) { require("recruitment:hire"); return recruitment.hire(id); }
    @GetMapping("/api/recruitment/interviews/mine") public RecruitmentService.InterviewList interviews() { return recruitment.myInterviews(); }

    @PostMapping("/api/performance/cycles") public Object cycle(@RequestBody PerformanceService.SaveCycle body) { require("performance:create"); return performance.createCycle(body); }
    @PostMapping("/api/performance/cycles/{id}/launch") public void launch(@PathVariable UUID id) { require("performance:update"); performance.launch(id); }
    @GetMapping("/api/performance/reviews/{id}") public PerformanceService.ReviewDto review(@PathVariable UUID id) { require("performance:read"); return performance.get(id); }
    @PostMapping("/api/performance/reviews/{id}/goals") public PerformanceService.ReviewDto goal(@PathVariable UUID id, @RequestBody PerformanceService.SaveGoal body) { return performance.addGoal(id, body); }
    @PostMapping("/api/performance/reviews/{id}/self") public PerformanceService.ReviewDto self(@PathVariable UUID id, @RequestBody PerformanceService.SelfReview body) { return performance.submitSelf(id, body); }
    @PostMapping("/api/performance/reviews/{id}/manager") public PerformanceService.ReviewDto manager(@PathVariable UUID id, @RequestBody PerformanceService.ManagerReview body) { return performance.submitManager(id, body); }
    @GetMapping("/api/performance/mine") public PerformanceService.ReviewList reviews() { return performance.mine(); }

    @PostMapping("/api/training/sessions") public Object session(@RequestBody TrainingService.SaveSession body) { require("training:create"); return training.createSession(body); }
    @PostMapping("/api/training/sessions/{id}/enroll") public void enroll(@PathVariable UUID id, @RequestBody EnrollBody body) { require("training:update"); training.enroll(id, body.employeeId()); }
    @PostMapping("/api/training/sessions/{id}/complete") public void complete(@PathVariable UUID id, @RequestBody CompleteBody body) { require("training:update"); training.complete(id, body.lines()); }
    @GetMapping("/api/training/mine") public TrainingService.EnrollmentList trainings() { return training.mine(); }
    @GetMapping("/api/training/compliance") public TrainingService.ComplianceList compliance() { require("training:read"); return training.compliance(); }

    public record OpenPeriod(int year, int month) {}
    public record ApplyBody(UUID candidateId) {}
    public record EnrollBody(UUID employeeId) {}
    public record CompleteBody(List<TrainingService.AttendanceLine> lines) {}
}
