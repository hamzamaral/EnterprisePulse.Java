package dev.enterprisepulse.recruitment;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.ApplicationActivity;
import dev.enterprisepulse.data.generated.ApplicationActivityType;
import dev.enterprisepulse.data.generated.ApplicationStage;
import dev.enterprisepulse.data.generated.Candidate;
import dev.enterprisepulse.data.generated.CandidateSource;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmploymentType;
import dev.enterprisepulse.data.generated.Interview;
import dev.enterprisepulse.data.generated.InterviewParticipant;
import dev.enterprisepulse.data.generated.InterviewRecommendation;
import dev.enterprisepulse.data.generated.InterviewStatus;
import dev.enterprisepulse.data.generated.InterviewType;
import dev.enterprisepulse.data.generated.JobApplication;
import dev.enterprisepulse.data.generated.JobOffer;
import dev.enterprisepulse.data.generated.JobOfferStatus;
import dev.enterprisepulse.data.generated.JobPosting;
import dev.enterprisepulse.data.generated.JobPostingStatus;
import dev.enterprisepulse.data.generated.SalaryRecord;
import dev.enterprisepulse.platform.NumberingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** İlan, aday ve başvuru. İşe alım kabul edilmiş tekliften personel ve ücret kaydı açar. */
@Service
@Transactional
public class RecruitmentService {
    public record SavePosting(String title, UUID departmentId, UUID positionId, UUID branchId, EmploymentType employmentType, String location, String description,
                               int openings, UUID hiringManagerId, BigDecimal salaryMin, BigDecimal salaryMax, LocalDate closesAt) {}
    public record SaveCandidate(String firstName, String lastName, String email, String phone, String city, CandidateSource source, String currentTitle, BigDecimal expectedSalary) {}
    public record MoveApplication(ApplicationStage stage, String rejectReason) {}
    public record ScheduleInterview(InterviewType type, Instant scheduledAt, int durationMinutes, String location, List<UUID> interviewerIds) {}
    public record InterviewFeedback(int rating, InterviewRecommendation recommendation, String feedback) {}
    public record SaveOffer(BigDecimal grossSalary, LocalDate startDate, LocalDate expiresAt, String notes) {}
    public record PostingDto(UUID id, String number, String title, JobPostingStatus status, int openings, int hired) {}
    public record ApplicationDto(UUID id, UUID postingId, String postingTitle, UUID candidateId, String candidateName, ApplicationStage stage, Integer rating,
                                  UUID employeeId, String offerStatus, BigDecimal offerSalary, boolean canHire) {}
    public record InterviewDto(UUID id, UUID applicationId, String candidateName, InterviewType type, InterviewStatus status, Instant scheduledAt, boolean needsFeedback) {}
    public record InterviewList(List<InterviewDto> items) {}

    private final Db db;
    private final NumberingService numbering;

    public RecruitmentService(Db db, NumberingService numbering) {
        this.db = db;
        this.numbering = numbering;
    }

    public PostingDto createPosting(SavePosting dto) {
        CurrentUser.get().require("recruitment:create");
        Guard.that(dto.title() != null && !dto.title().isBlank(), "İlan başlığı gerekli");
        Guard.that(dto.openings() > 0, "Kontenjan sıfırdan büyük olmalı");
        Guard.that(dto.salaryMin() == null || dto.salaryMax() == null || dto.salaryMax().compareTo(dto.salaryMin()) >= 0, "Ücret aralığı geçersiz");
        JobPosting posting = new JobPosting();
        posting.number = numbering.next("JOB_POSTING");
        posting.title = dto.title().trim();
        posting.departmentId = dto.departmentId();
        posting.positionId = dto.positionId();
        posting.branchId = dto.branchId();
        posting.employmentType = dto.employmentType() == null ? EmploymentType.FULL_TIME : dto.employmentType();
        posting.location = blank(dto.location());
        posting.description = blank(dto.description());
        posting.openings = dto.openings();
        posting.hiringManagerId = dto.hiringManagerId();
        posting.salaryMin = dto.salaryMin();
        posting.salaryMax = dto.salaryMax();
        posting.closesAt = dto.closesAt();
        posting.createdById = CurrentUser.get().requireUserId();
        db.add(posting);
        db.flush();
        return posting(posting.id);
    }

    public PostingDto publish(UUID id) {
        CurrentUser.get().require("recruitment:update");
        JobPosting posting = findPosting(id);
        Guard.state(posting.status == JobPostingStatus.DRAFT || posting.status == JobPostingStatus.ON_HOLD, "İlan yayına alınamaz");
        posting.status = JobPostingStatus.OPEN;
        if (posting.publishedAt == null) posting.publishedAt = Instant.now();
        db.flush();
        return posting(id);
    }

    public Candidate createCandidate(SaveCandidate dto) {
        CurrentUser.get().require("recruitment:create");
        Guard.that(dto.firstName() != null && !dto.firstName().isBlank() && dto.lastName() != null && !dto.lastName().isBlank() && dto.email() != null && !dto.email().isBlank(), "Ad, soyad ve e-posta gerekli");
        Guard.that(!db.exists("select c.id from Candidate c where c.email = ?1", dto.email().trim()), "Bu e-posta ile aday zaten var");
        Candidate candidate = new Candidate();
        candidate.firstName = dto.firstName().trim();
        candidate.lastName = dto.lastName().trim();
        candidate.email = dto.email().trim();
        candidate.phone = blank(dto.phone());
        candidate.city = blank(dto.city());
        candidate.source = dto.source() == null ? CandidateSource.OTHER : dto.source();
        candidate.currentTitle = blank(dto.currentTitle());
        candidate.expectedSalary = dto.expectedSalary();
        candidate.createdById = CurrentUser.get().requireUserId();
        db.add(candidate);
        db.flush();
        return candidate;
    }

    public ApplicationDto apply(UUID postingId, UUID candidateId) {
        CurrentUser.get().require("recruitment:create");
        JobPosting posting = findPosting(postingId);
        Guard.state(posting.status == JobPostingStatus.OPEN, "Yalnızca yayındaki ilana başvurulur");
        Guard.that(db.exists("select c.id from Candidate c where c.id = ?1", candidateId), "Aday bulunamadı");
        Guard.that(!db.exists("select a.id from JobApplication a where a.postingId = ?1 and a.candidateId = ?2", postingId, candidateId), "Aday bu ilana zaten başvurmuş");
        int sort = db.list("select a.sortOrder from JobApplication a where a.postingId = ?1 and a.stage = ?2", Integer.class, postingId, ApplicationStage.APPLIED).stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        JobApplication application = new JobApplication();
        application.postingId = postingId;
        application.candidateId = candidateId;
        application.sortOrder = sort;
        application.createdById = CurrentUser.get().requireUserId();
        db.add(application);
        db.flush();
        log(application, ApplicationActivityType.STAGE, null, ApplicationStage.APPLIED, "Başvuru alındı");
        return application(application.id);
    }

    public ApplicationDto move(UUID id, ApplicationStage stage, String reason) {
        CurrentUser.get().require("recruitment:update");
        JobApplication application = findApplication(id);
        Guard.state(application.stage != ApplicationStage.HIRED && application.stage != ApplicationStage.REJECTED && application.stage != ApplicationStage.WITHDRAWN, "Kapanmış başvuru taşınamaz");
        Guard.that(stage != ApplicationStage.HIRED, "İşe alım teklif kabulünden yapılır");
        if (stage == ApplicationStage.REJECTED || stage == ApplicationStage.WITHDRAWN) Guard.that(reason != null && !reason.isBlank(), "Gerekçe gerekli");
        ApplicationStage from = application.stage;
        application.stage = stage;
        application.stageChangedAt = Instant.now();
        application.rejectReason = stage == ApplicationStage.REJECTED ? reason.trim() : null;
        log(application, ApplicationActivityType.STAGE, from, stage, reason);
        return application(id);
    }

    public ApplicationDto scheduleInterview(UUID applicationId, ScheduleInterview dto) {
        CurrentUser.get().require("recruitment:update");
        Guard.that(dto.interviewerIds() != null && !dto.interviewerIds().isEmpty(), "En az bir görüşmeci gerekli");
        Guard.that(dto.durationMinutes() > 0, "Süre sıfırdan büyük olmalı");
        JobApplication application = findApplication(applicationId);
        Guard.state(application.stage == ApplicationStage.SCREENING || application.stage == ApplicationStage.INTERVIEW, "Mülakat bu aşamada planlanamaz");
        Interview interview = new Interview();
        interview.applicationId = application.id;
        interview.type = dto.type();
        interview.scheduledAt = dto.scheduledAt();
        interview.durationMinutes = dto.durationMinutes();
        interview.location = blank(dto.location());
        interview.createdById = CurrentUser.get().requireUserId();
        db.add(interview);
        db.flush();
        for (UUID employeeId : dto.interviewerIds().stream().distinct().toList()) {
            Guard.that(db.exists("select e.id from Employee e where e.id = ?1", employeeId), "Görüşmeci bulunamadı");
            InterviewParticipant participant = new InterviewParticipant();
            participant.interviewId = interview.id;
            participant.employeeId = employeeId;
            db.add(participant);
        }
        if (application.stage == ApplicationStage.SCREENING) {
            application.stage = ApplicationStage.INTERVIEW;
            application.stageChangedAt = Instant.now();
            log(application, ApplicationActivityType.STAGE, ApplicationStage.SCREENING, ApplicationStage.INTERVIEW, null);
        }
        log(application, ApplicationActivityType.INTERVIEW, application.stage, application.stage, dto.type().name());
        return application(application.id);
    }

    public void feedback(UUID interviewId, InterviewFeedback dto) {
        Guard.that(dto.rating() >= 1 && dto.rating() <= 5, "Puan 1 ile 5 arasında olmalı");
        Interview interview = Guard.found(db.first("select i from Interview i where i.id = ?1", Interview.class, interviewId), "Mülakat bulunamadı");
        Guard.state(interview.status == InterviewStatus.SCHEDULED, "Mülakat kapalı");
        List<InterviewParticipant> participants = db.list("select p from InterviewParticipant p where p.interviewId = ?1", InterviewParticipant.class, interview.id);
        UUID me = employeeId();
        InterviewParticipant participant = participants.stream().filter(p -> p.employeeId.equals(me)).findFirst().orElse(null);
        if (participant == null && CurrentUser.get().has("recruitment:update") && participants.size() == 1) participant = participants.get(0);
        Guard.that(participant != null, "Bu mülakatın görüşmecisi değilsiniz");
        participant.rating = dto.rating();
        participant.recommendation = dto.recommendation();
        participant.feedback = blank(dto.feedback());
        participant.submittedAt = Instant.now();
        if (participants.stream().allMatch(p -> p.submittedAt != null)) interview.status = InterviewStatus.COMPLETED;
        db.flush();
    }

    public ApplicationDto offer(UUID applicationId, SaveOffer dto) {
        CurrentUser.get().require("recruitment:update");
        Guard.that(dto.grossSalary() != null && dto.grossSalary().signum() > 0, "Teklif ücreti sıfırdan büyük olmalı");
        JobApplication application = findApplication(applicationId);
        Guard.state(application.stage == ApplicationStage.INTERVIEW || application.stage == ApplicationStage.OFFER, "Teklif bu aşamada verilemez");
        Guard.that(!db.exists("select o.id from JobOffer o where o.applicationId = ?1 and o.status = ?2", applicationId, JobOfferStatus.PENDING), "Bekleyen bir teklif zaten var");
        JobOffer offer = new JobOffer();
        offer.applicationId = application.id;
        offer.grossSalary = dto.grossSalary();
        offer.startDate = dto.startDate();
        offer.expiresAt = dto.expiresAt();
        offer.notes = blank(dto.notes());
        offer.createdById = CurrentUser.get().requireUserId();
        db.add(offer);
        log(application, ApplicationActivityType.OFFER, application.stage, application.stage, dto.grossSalary().toPlainString());
        return application(application.id);
    }

    public ApplicationDto respondOffer(UUID offerId, boolean accept) {
        CurrentUser.get().require("recruitment:update");
        JobOffer offer = Guard.found(db.first("select o from JobOffer o where o.id = ?1", JobOffer.class, offerId), "Teklif bulunamadı");
        Guard.state(offer.status == JobOfferStatus.PENDING, "Teklif zaten yanıtlanmış");
        JobApplication application = findApplication(offer.applicationId);
        offer.status = accept ? JobOfferStatus.ACCEPTED : JobOfferStatus.DECLINED;
        offer.respondedAt = Instant.now();
        if (accept && application.stage != ApplicationStage.OFFER) {
            ApplicationStage from = application.stage;
            application.stage = ApplicationStage.OFFER;
            application.stageChangedAt = Instant.now();
            log(application, ApplicationActivityType.STAGE, from, ApplicationStage.OFFER, "Teklif kabul edildi");
        }
        db.flush();
        return application(application.id);
    }

    public ApplicationDto hire(UUID applicationId) {
        CurrentUser.get().require("recruitment:hire");
        JobApplication application = findApplication(applicationId);
        Guard.state(application.stage == ApplicationStage.OFFER && application.employeeId == null, "İşe alım için kabul edilmiş teklif gerekli");
        JobOffer offer = db.first("select o from JobOffer o where o.applicationId = ?1 and o.status = ?2 order by o.respondedAt desc", JobOffer.class, applicationId, JobOfferStatus.ACCEPTED).orElse(null);
        Guard.state(offer != null, "Kabul edilmiş teklif yok");
        Candidate candidate = db.get(Candidate.class, application.candidateId);
        JobPosting posting = db.get(JobPosting.class, application.postingId);
        Employee employee = new Employee();
        employee.employeeNo = numbering.next("EMPLOYEE");
        employee.firstName = candidate.firstName;
        employee.lastName = candidate.lastName;
        employee.email = candidate.email;
        employee.phone = candidate.phone;
        employee.hireDate = offer.startDate;
        employee.employmentType = posting.employmentType;
        employee.departmentId = posting.departmentId;
        employee.positionId = posting.positionId;
        employee.branchId = posting.branchId;
        employee.managerId = posting.hiringManagerId;
        db.add(employee);
        db.flush();
        SalaryRecord salary = new SalaryRecord();
        salary.employeeId = employee.id;
        salary.effectiveFrom = offer.startDate;
        salary.grossSalary = offer.grossSalary;
        salary.note = "İşe alım teklifi";
        salary.createdById = CurrentUser.get().requireUserId();
        db.add(salary);
        application.employeeId = employee.id;
        application.stage = ApplicationStage.HIRED;
        application.hiredAt = Instant.now();
        application.stageChangedAt = Instant.now();
        log(application, ApplicationActivityType.HIRED, ApplicationStage.OFFER, ApplicationStage.HIRED, employee.employeeNo);
        long hired = db.count("select count(a) from JobApplication a where a.postingId = ?1 and a.stage = ?2", posting.id, ApplicationStage.HIRED);
        if (hired >= posting.openings) {
            posting.status = JobPostingStatus.CLOSED;
            posting.closedAt = Instant.now();
        }
        db.flush();
        return application(application.id);
    }

    @Transactional(readOnly = true)
    public InterviewList myInterviews() {
        UUID me = employeeId();
        if (me == null) return new InterviewList(List.of());
        List<InterviewDto> items = new ArrayList<>();
        for (InterviewParticipant participant : db.list("select p from InterviewParticipant p where p.employeeId = ?1", InterviewParticipant.class, me)) {
            Interview interview = db.get(Interview.class, participant.interviewId);
            if (interview.status == InterviewStatus.CANCELLED) continue;
            JobApplication application = db.get(JobApplication.class, interview.applicationId);
            Candidate candidate = db.get(Candidate.class, application.candidateId);
            items.add(new InterviewDto(interview.id, application.id, candidate.firstName + " " + candidate.lastName, interview.type, interview.status, interview.scheduledAt, participant.submittedAt == null));
        }
        return new InterviewList(items);
    }

    @Transactional(readOnly = true)
    public ApplicationDto application(UUID id) {
        CurrentUser.get().require("recruitment:read");
        JobApplication application = findApplication(id);
        JobPosting posting = db.get(JobPosting.class, application.postingId);
        Candidate candidate = db.get(Candidate.class, application.candidateId);
        List<JobOffer> offers = db.list("select o from JobOffer o where o.applicationId = ?1 order by o.createdAt desc", JobOffer.class, id);
        JobOffer latest = offers.isEmpty() ? null : offers.get(0);
        boolean accepted = offers.stream().anyMatch(o -> o.status == JobOfferStatus.ACCEPTED);
        return new ApplicationDto(application.id, posting.id, posting.title, candidate.id, candidate.firstName + " " + candidate.lastName, application.stage, application.rating,
                application.employeeId, latest == null ? null : latest.status.name(), latest == null ? null : latest.grossSalary,
                application.stage == ApplicationStage.OFFER && accepted && application.employeeId == null);
    }

    private PostingDto posting(UUID id) {
        JobPosting posting = db.get(JobPosting.class, id);
        int hired = (int) db.count("select count(a) from JobApplication a where a.postingId = ?1 and a.stage = ?2", id, ApplicationStage.HIRED);
        return new PostingDto(posting.id, posting.number, posting.title, posting.status, posting.openings, hired);
    }

    private void log(JobApplication application, ApplicationActivityType type, ApplicationStage from, ApplicationStage to, String body) {
        ApplicationActivity activity = new ApplicationActivity();
        activity.applicationId = application.id;
        activity.type = type;
        activity.fromStage = from;
        activity.toStage = to;
        activity.body = blank(body);
        activity.createdById = CurrentUser.get().requireUserId();
        db.add(activity);
        db.flush();
    }

    private UUID employeeId() {
        UUID userId = CurrentUser.get().userId;
        if (userId == null) return null;
        return db.first("select e.id from Employee e where e.userId = ?1", UUID.class, userId).orElse(null);
    }

    private JobPosting findPosting(UUID id) { return Guard.found(db.first("select p from JobPosting p where p.id = ?1", JobPosting.class, id), "İlan bulunamadı"); }
    private JobApplication findApplication(UUID id) { return Guard.found(db.first("select a from JobApplication a where a.id = ?1", JobApplication.class, id), "Başvuru bulunamadı"); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
