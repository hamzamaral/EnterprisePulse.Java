package dev.enterprisepulse.performance;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Competency;
import dev.enterprisepulse.data.generated.CompetencyRating;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.data.generated.PerformanceGoal;
import dev.enterprisepulse.data.generated.PerformanceReview;
import dev.enterprisepulse.data.generated.PerformanceReviewStatus;
import dev.enterprisepulse.data.generated.ReviewCycle;
import dev.enterprisepulse.data.generated.ReviewCycleCompetency;
import dev.enterprisepulse.data.generated.ReviewCycleStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Değerlendirme dönemi. Puan hedefin ağırlığı ile yetkinlik ortalamasının karışımıdır. */
@Service
@Transactional
public class PerformanceService {
    public record SaveCycle(String name, LocalDate periodStart, LocalDate periodEnd, LocalDate selfReviewDue, LocalDate managerReviewDue, int goalWeight, List<UUID> competencyIds) {}
    public record SaveGoal(String title, int weight) {}
    public record RatingInput(UUID id, int rating) {}
    public record SelfReview(String comment, List<RatingInput> goals, List<RatingInput> competencies) {}
    public record ManagerReview(String comment, String strengths, String improvements, List<RatingInput> goals, List<RatingInput> competencies) {}
    public record ReviewDto(UUID id, UUID cycleId, String cycleName, UUID employeeId, String employeeName, UUID reviewerId, String reviewerName,
                             PerformanceReviewStatus status, BigDecimal goalScore, BigDecimal competencyScore, BigDecimal overallScore, int goalWeight) {}
    public record ReviewList(List<ReviewDto> items) {}

    private final Db db;

    public PerformanceService(Db db) { this.db = db; }

    public ReviewCycle createCycle(SaveCycle dto) {
        CurrentUser.get().require("performance:create");
        Guard.that(dto.name() != null && !dto.name().isBlank(), "Dönem adı gerekli");
        Guard.that(dto.goalWeight() >= 0 && dto.goalWeight() <= 100, "Hedef ağırlığı 0 ile 100 arasında olmalı");
        Guard.that(dto.competencyIds() != null && !dto.competencyIds().isEmpty(), "En az bir yetkinlik seçilmeli");
        ReviewCycle cycle = new ReviewCycle();
        cycle.name = dto.name().trim();
        cycle.periodStart = dto.periodStart();
        cycle.periodEnd = dto.periodEnd();
        cycle.selfReviewDue = dto.selfReviewDue();
        cycle.managerReviewDue = dto.managerReviewDue();
        cycle.goalWeight = dto.goalWeight();
        cycle.createdById = CurrentUser.get().requireUserId();
        db.add(cycle);
        db.flush();
        for (UUID competencyId : dto.competencyIds().stream().distinct().toList()) {
            Guard.that(db.exists("select c.id from Competency c where c.id = ?1 and c.isActive = true", competencyId), "Yetkinlik bulunamadı");
            ReviewCycleCompetency link = new ReviewCycleCompetency();
            link.cycleId = cycle.id;
            link.competencyId = competencyId;
            db.add(link);
        }
        db.flush();
        return cycle;
    }

    public void launch(UUID id) {
        CurrentUser.get().require("performance:update");
        ReviewCycle cycle = Guard.found(db.first("select c from ReviewCycle c where c.id = ?1", ReviewCycle.class, id), "Değerlendirme dönemi bulunamadı");
        Guard.state(cycle.status == ReviewCycleStatus.DRAFT, "Dönem zaten başlamış");
        List<ReviewCycleCompetency> competencies = db.list("select c from ReviewCycleCompetency c where c.cycleId = ?1", ReviewCycleCompetency.class, cycle.id);
        Guard.that(!competencies.isEmpty(), "Yetkinlik seçilmeden dönem başlamaz");
        List<Employee> employees = db.list("select e from Employee e where e.status = ?1", Employee.class, EmployeeStatus.ACTIVE);
        Guard.that(!employees.isEmpty(), "Aktif personel yok");
        for (Employee employee : employees) {
            PerformanceReview review = new PerformanceReview();
            review.cycleId = cycle.id;
            review.employeeId = employee.id;
            review.reviewerId = employee.managerId;
            db.add(review);
            db.flush();
            for (ReviewCycleCompetency competency : competencies) {
                CompetencyRating rating = new CompetencyRating();
                rating.reviewId = review.id;
                rating.competencyId = competency.competencyId;
                db.add(rating);
            }
        }
        cycle.status = ReviewCycleStatus.ACTIVE;
        cycle.launchedAt = Instant.now();
        db.flush();
    }

    public ReviewDto addGoal(UUID reviewId, SaveGoal dto) {
        PerformanceReview review = find(reviewId);
        assertParty(review, true);
        Guard.state(review.status == PerformanceReviewStatus.SELF_REVIEW, "Hedef yalnızca öz değerlendirmede eklenir");
        Guard.that(dto.title() != null && !dto.title().isBlank(), "Hedef başlığı gerekli");
        Guard.that(dto.weight() > 0 && dto.weight() <= 100, "Ağırlık 1 ile 100 arasında olmalı");
        int sort = db.list("select g.sortOrder from PerformanceGoal g where g.reviewId = ?1", Integer.class, reviewId).stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        PerformanceGoal goal = new PerformanceGoal();
        goal.reviewId = reviewId;
        goal.title = dto.title().trim();
        goal.weight = dto.weight();
        goal.sortOrder = sort;
        db.add(goal);
        db.flush();
        return get(reviewId);
    }

    public ReviewDto submitSelf(UUID reviewId, SelfReview dto) {
        PerformanceReview review = find(reviewId);
        assertParty(review, true);
        Guard.state(review.status == PerformanceReviewStatus.SELF_REVIEW, "Öz değerlendirme kapalı");
        List<PerformanceGoal> goals = goals(reviewId);
        List<CompetencyRating> ratings = ratings(reviewId);
        applyGoals(goals, dto.goals(), true);
        applyCompetencies(ratings, dto.competencies(), true);
        Guard.that(goals.stream().mapToInt(g -> g.weight).sum() == 100, "Hedef ağırlıkları 100 olmalı");
        Guard.that(goals.stream().allMatch(g -> g.selfRating != null && g.selfRating >= 1 && g.selfRating <= 5), "Her hedefin öz puanı 1 ile 5 arasında olmalı");
        Guard.that(ratings.stream().allMatch(r -> r.selfRating != null && r.selfRating >= 1 && r.selfRating <= 5), "Her yetkinliğin öz puanı gerekli");
        review.selfComment = blank(dto.comment());
        review.selfSubmittedAt = Instant.now();
        review.status = PerformanceReviewStatus.MANAGER_REVIEW;
        db.flush();
        return get(reviewId);
    }

    public ReviewDto submitManager(UUID reviewId, ManagerReview dto) {
        PerformanceReview review = find(reviewId);
        assertParty(review, false);
        Guard.state(review.status == PerformanceReviewStatus.MANAGER_REVIEW, "Yönetici değerlendirmesi kapalı");
        List<PerformanceGoal> goals = goals(reviewId);
        List<CompetencyRating> ratings = ratings(reviewId);
        applyGoals(goals, dto.goals(), false);
        applyCompetencies(ratings, dto.competencies(), false);
        Guard.that(goals.stream().allMatch(g -> g.managerRating != null && g.managerRating >= 1 && g.managerRating <= 5), "Her hedefin yönetici puanı gerekli");
        Guard.that(ratings.stream().allMatch(r -> r.managerRating != null && r.managerRating >= 1 && r.managerRating <= 5), "Her yetkinliğin yönetici puanı gerekli");
        ReviewCycle cycle = db.get(ReviewCycle.class, review.cycleId);
        BigDecimal goalScore = BigDecimal.valueOf(goals.stream().mapToInt(g -> g.weight * g.managerRating).sum()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal competencyScore = BigDecimal.valueOf(ratings.stream().mapToInt(r -> r.managerRating).sum()).divide(BigDecimal.valueOf(ratings.size()), 2, RoundingMode.HALF_UP);
        review.goalScore = goalScore;
        review.competencyScore = competencyScore;
        review.overallScore = goalScore.multiply(BigDecimal.valueOf(cycle.goalWeight)).add(competencyScore.multiply(BigDecimal.valueOf(100 - cycle.goalWeight))).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        review.managerComment = blank(dto.comment());
        review.strengths = blank(dto.strengths());
        review.improvements = blank(dto.improvements());
        review.managerSubmittedAt = Instant.now();
        review.status = PerformanceReviewStatus.COMPLETED;
        db.flush();
        return get(reviewId);
    }

    @Transactional(readOnly = true)
    public ReviewList mine() {
        UUID me = employeeId();
        List<ReviewDto> items = new ArrayList<>();
        for (PerformanceReview review : db.list("select r from PerformanceReview r order by r.createdAt desc", PerformanceReview.class)) {
            if (!CurrentUser.get().has("performance:read") && !review.employeeId.equals(me) && (review.reviewerId == null || !review.reviewerId.equals(me))) continue;
            items.add(map(review));
            if (items.size() == 100) break;
        }
        return new ReviewList(items);
    }

    @Transactional(readOnly = true)
    public ReviewDto get(UUID id) {
        PerformanceReview review = find(id);
        UUID me = employeeId();
        if (!CurrentUser.get().has("performance:read") && !review.employeeId.equals(me) && (review.reviewerId == null || !review.reviewerId.equals(me)))
            throw dev.enterprisepulse.common.AppException.notFound("Değerlendirme bulunamadı");
        return map(review);
    }

    private void assertParty(PerformanceReview review, boolean self) {
        UUID me = employeeId();
        boolean allowed = self ? review.employeeId.equals(me) || CurrentUser.get().has("performance:update") : (review.reviewerId != null && review.reviewerId.equals(me)) || CurrentUser.get().has("performance:update");
        Guard.that(allowed, self ? "Öz değerlendirme yetkiniz yok" : "Yönetici değerlendirmesi yetkiniz yok");
    }

    private static void applyGoals(List<PerformanceGoal> goals, List<RatingInput> inputs, boolean self) {
        if (inputs == null) return;
        for (RatingInput input : inputs) {
            Guard.that(input.rating() >= 1 && input.rating() <= 5, "Puan 1 ile 5 arasında olmalı");
            PerformanceGoal goal = goals.stream().filter(g -> g.id.equals(input.id())).findFirst().orElseThrow(() -> dev.enterprisepulse.common.AppException.notFound("Hedef bulunamadı"));
            if (self) goal.selfRating = input.rating(); else goal.managerRating = input.rating();
        }
    }

    private static void applyCompetencies(List<CompetencyRating> ratings, List<RatingInput> inputs, boolean self) {
        if (inputs == null) return;
        for (RatingInput input : inputs) {
            Guard.that(input.rating() >= 1 && input.rating() <= 5, "Puan 1 ile 5 arasında olmalı");
            CompetencyRating rating = ratings.stream().filter(r -> r.id.equals(input.id())).findFirst().orElseThrow(() -> dev.enterprisepulse.common.AppException.notFound("Yetkinlik puanı bulunamadı"));
            if (self) rating.selfRating = input.rating(); else rating.managerRating = input.rating();
        }
    }

    private ReviewDto map(PerformanceReview review) {
        ReviewCycle cycle = db.get(ReviewCycle.class, review.cycleId);
        Employee employee = db.get(Employee.class, review.employeeId);
        Employee reviewer = review.reviewerId == null ? null : db.find(Employee.class, review.reviewerId);
        return new ReviewDto(review.id, cycle.id, cycle.name, employee.id, employee.firstName + " " + employee.lastName, review.reviewerId,
                reviewer == null ? null : reviewer.firstName + " " + reviewer.lastName, review.status, review.goalScore, review.competencyScore, review.overallScore, cycle.goalWeight);
    }

    private List<PerformanceGoal> goals(UUID reviewId) { return db.list("select g from PerformanceGoal g where g.reviewId = ?1", PerformanceGoal.class, reviewId); }
    private List<CompetencyRating> ratings(UUID reviewId) { return db.list("select r from CompetencyRating r where r.reviewId = ?1", CompetencyRating.class, reviewId); }
    private PerformanceReview find(UUID id) { return Guard.found(db.first("select r from PerformanceReview r where r.id = ?1", PerformanceReview.class, id), "Değerlendirme bulunamadı"); }
    private UUID employeeId() {
        UUID userId = CurrentUser.get().userId;
        if (userId == null) return null;
        return db.first("select e.id from Employee e where e.userId = ?1", UUID.class, userId).orElse(null);
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
