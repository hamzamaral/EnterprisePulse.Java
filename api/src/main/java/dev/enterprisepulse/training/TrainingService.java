package dev.enterprisepulse.training;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.data.generated.TrainingCourse;
import dev.enterprisepulse.data.generated.TrainingEnrollment;
import dev.enterprisepulse.data.generated.TrainingEnrollmentStatus;
import dev.enterprisepulse.data.generated.TrainingSession;
import dev.enterprisepulse.data.generated.TrainingSessionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Eğitim oturumu ve yoklama. Zorunlu eğitimde geçerli sertifikası olmayan personel açıkta kalır. */
@Service
@Transactional
public class TrainingService {
    public record SaveSession(UUID courseId, Instant startsAt, Instant endsAt, String location, String instructor, Integer capacity, BigDecimal cost, boolean selfEnrollment) {}
    public record AttendanceLine(UUID employeeId, TrainingEnrollmentStatus status, Integer score) {}
    public record EnrollmentDto(UUID id, UUID sessionId, String courseTitle, Instant startsAt, TrainingEnrollmentStatus status, Integer score, LocalDate certificateExpiresAt) {}
    public record EnrollmentList(List<EnrollmentDto> items) {}
    public record ComplianceGap(UUID employeeId, String employeeName, UUID courseId, String courseTitle) {}
    public record ComplianceList(List<ComplianceGap> items) {}

    private final Db db;

    public TrainingService(Db db) { this.db = db; }

    public TrainingSession createSession(SaveSession dto) {
        CurrentUser.get().require("training:create");
        Guard.that(dto.endsAt() != null && dto.startsAt() != null && dto.endsAt().isAfter(dto.startsAt()), "Bitiş başlangıçtan sonra olmalı");
        Guard.that(dto.capacity() == null || dto.capacity() > 0, "Kontenjan sıfırdan büyük olmalı");
        TrainingCourse course = Guard.found(db.first("select c from TrainingCourse c where c.id = ?1 and c.isActive = true", TrainingCourse.class, dto.courseId()), "Eğitim bulunamadı");
        TrainingSession session = new TrainingSession();
        session.courseId = course.id;
        session.startsAt = dto.startsAt();
        session.endsAt = dto.endsAt();
        session.location = blank(dto.location());
        session.instructor = blank(dto.instructor());
        session.capacity = dto.capacity();
        session.cost = dto.cost();
        session.selfEnrollment = dto.selfEnrollment();
        session.createdById = CurrentUser.get().requireUserId();
        db.add(session);
        db.flush();
        return session;
    }

    public void enroll(UUID sessionId, UUID employeeId) {
        TrainingSession session = Guard.found(db.first("select s from TrainingSession s where s.id = ?1", TrainingSession.class, sessionId), "Oturum bulunamadı");
        Guard.state(session.status == TrainingSessionStatus.PLANNED, "Yalnızca planlı oturuma kayıt olunur");
        UUID target = employeeId;
        if (target == null) {
            Guard.that(session.selfEnrollment, "Bu oturuma kendi kendine kayıt kapalı");
            target = employeeIdOfUser();
            Guard.that(target != null, "Personel kaydınız yok");
        } else CurrentUser.get().require("training:update");
        Guard.that(db.exists("select e.id from Employee e where e.id = ?1 and e.status = ?2", target, EmployeeStatus.ACTIVE), "Personel bulunamadı");
        Guard.that(!db.exists("select e.id from TrainingEnrollment e where e.sessionId = ?1 and e.employeeId = ?2", session.id, target), "Personel zaten kayıtlı");
        long taken = db.count("select count(e) from TrainingEnrollment e where e.sessionId = ?1 and (e.status = ?2 or e.status = ?3)", session.id, TrainingEnrollmentStatus.ENROLLED, TrainingEnrollmentStatus.ATTENDED);
        Guard.that(session.capacity == null || taken < session.capacity, "Oturum kontenjanı dolu");
        TrainingEnrollment enrollment = new TrainingEnrollment();
        enrollment.sessionId = session.id;
        enrollment.employeeId = target;
        db.add(enrollment);
        db.flush();
    }

    public void complete(UUID sessionId, List<AttendanceLine> lines) {
        CurrentUser.get().require("training:update");
        TrainingSession session = Guard.found(db.first("select s from TrainingSession s where s.id = ?1", TrainingSession.class, sessionId), "Oturum bulunamadı");
        Guard.state(session.status == TrainingSessionStatus.PLANNED, "Oturum zaten kapanmış");
        Guard.that(lines != null && !lines.isEmpty(), "Yoklama satırı gerekli");
        TrainingCourse course = db.get(TrainingCourse.class, session.courseId);
        for (AttendanceLine line : lines) {
            Guard.that(line.status() == TrainingEnrollmentStatus.ATTENDED || line.status() == TrainingEnrollmentStatus.NO_SHOW, "Yoklama sonucu katıldı veya katılmadı olmalı");
            Guard.that(line.score() == null || (line.score() >= 0 && line.score() <= 100), "Puan 0 ile 100 arasında olmalı");
            TrainingEnrollment enrollment = Guard.found(db.first("select e from TrainingEnrollment e where e.sessionId = ?1 and e.employeeId = ?2", TrainingEnrollment.class, session.id, line.employeeId()), "Kayıt bulunamadı");
            enrollment.status = line.status();
            enrollment.score = line.score();
            if (line.status() == TrainingEnrollmentStatus.ATTENDED && course.validityMonths != null)
                enrollment.certificateExpiresAt = session.endsAt.atZone(ZoneOffset.UTC).toLocalDate().plusMonths(course.validityMonths);
        }
        session.status = TrainingSessionStatus.COMPLETED;
        session.completedAt = Instant.now();
        db.flush();
    }

    @Transactional(readOnly = true)
    public EnrollmentList mine() {
        UUID me = employeeIdOfUser();
        if (me == null) return new EnrollmentList(List.of());
        List<EnrollmentDto> items = new ArrayList<>();
        for (TrainingEnrollment enrollment : db.list("select e from TrainingEnrollment e where e.employeeId = ?1", TrainingEnrollment.class, me)) {
            TrainingSession session = db.get(TrainingSession.class, enrollment.sessionId);
            TrainingCourse course = db.get(TrainingCourse.class, session.courseId);
            items.add(new EnrollmentDto(enrollment.id, session.id, course.title, session.startsAt, enrollment.status, enrollment.score, enrollment.certificateExpiresAt));
        }
        return new EnrollmentList(items);
    }

    @Transactional(readOnly = true)
    public ComplianceList compliance() {
        CurrentUser.get().require("training:read");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<TrainingCourse> courses = db.list("select c from TrainingCourse c where c.isMandatory = true and c.isActive = true", TrainingCourse.class);
        List<Employee> employees = db.list("select e from Employee e where e.status = ?1", Employee.class, EmployeeStatus.ACTIVE);
        List<TrainingEnrollment> attended = db.list("select e from TrainingEnrollment e where e.status = ?1", TrainingEnrollment.class, TrainingEnrollmentStatus.ATTENDED);
        List<ComplianceGap> gaps = new ArrayList<>();
        for (Employee employee : employees) {
            for (TrainingCourse course : courses) {
                boolean covered = false;
                for (TrainingEnrollment enrollment : attended) {
                    if (!enrollment.employeeId.equals(employee.id)) continue;
                    TrainingSession session = db.get(TrainingSession.class, enrollment.sessionId);
                    if (session.courseId.equals(course.id) && (enrollment.certificateExpiresAt == null || !enrollment.certificateExpiresAt.isBefore(today))) covered = true;
                }
                if (!covered) gaps.add(new ComplianceGap(employee.id, employee.firstName + " " + employee.lastName, course.id, course.title));
            }
        }
        return new ComplianceList(gaps);
    }

    private UUID employeeIdOfUser() {
        UUID userId = CurrentUser.get().userId;
        if (userId == null) return null;
        return db.first("select e.id from Employee e where e.userId = ?1", UUID.class, userId).orElse(null);
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
