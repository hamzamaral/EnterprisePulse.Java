package dev.enterprisepulse.hr;

import com.fasterxml.jackson.databind.JsonNode;
import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.AttendanceRecord;
import dev.enterprisepulse.data.generated.AttendanceSource;
import dev.enterprisepulse.data.generated.Department;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.LeaveRequest;
import dev.enterprisepulse.data.generated.LeaveRequestStatus;
import dev.enterprisepulse.data.generated.Shift;
import dev.enterprisepulse.data.generated.ShiftAssignment;
import dev.enterprisepulse.hr.HrRules.DayResult;
import dev.enterprisepulse.hr.HrRules.DayStatus;
import dev.enterprisepulse.hr.HrRules.ShiftTimes;
import dev.enterprisepulse.hr.HrRules.Timesheet;
import dev.enterprisepulse.hr.HrRules.Window;
import dev.enterprisepulse.hr.HrRules.WorkingDays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shift planning (vardiya) and attendance (puantaj). */
@Service
@Transactional
public class AttendanceService {
    private static final int MAX_PLAN_DAYS = 31;
    private static final String DEFAULT_TIME_ZONE = "Europe/Istanbul";

    public record TimesheetDay(LocalDate date, DayStatus status, String shiftCode, String shiftColor, String plannedStart, String plannedEnd,
                               String checkIn, String checkOut, int plannedMinutes, int workedMinutes, int lateMinutes, int overtimeMinutes,
                               AttendanceSource source, String note) {}

    public static final class TimesheetTotals {
        public int plannedDays, presentDays, lateDays, absentDays, leaveDays, plannedMinutes, workedMinutes, lateMinutes, overtimeMinutes;
    }

    public record TimesheetRow(UUID employeeId, String employeeNo, String employeeName, String departmentName, List<TimesheetDay> days, TimesheetTotals totals) {}

    public record TimesheetDto(String month, List<LocalDate> days, LocalDate today, List<TimesheetRow> rows) {}

    public record MyAttendanceDto(TimesheetDay today, boolean canCheckIn, boolean canCheckOut, TimesheetRow row) {}

    public record PlanEmployee(UUID id, String employeeNo, String name, String departmentName) {}

    public record PlanEntry(UUID employeeId, LocalDate date, UUID shiftId) {}

    public record ShiftPlanDto(LocalDate from, LocalDate to, List<PlanEmployee> employees, List<PlanEntry> entries) {}

    public record ShiftPlanChange(UUID employeeId, LocalDate date, UUID shiftId, boolean useDefault) {}

    public record UpdateShiftPlanRequest(List<ShiftPlanChange> entries) {}

    public record UpdateRecordRequest(UUID employeeId, LocalDate date, String checkIn, String checkOut, String note) {}

    private record EmployeeRow(UUID id, String employeeNo, String firstName, String lastName, LocalDate hireDate, LocalDate terminationDate, String department) {}

    private final Db db;

    public AttendanceService(Db db) {
        this.db = db;
    }

    // ---------- shift plan ----------

    @Transactional(readOnly = true)
    public ShiftPlanDto plan(LocalDate from, LocalDate to, UUID departmentId) {
        Guard.that(!to.isBefore(from), "Bitiş başlangıçtan önce olamaz");
        Guard.that(to.toEpochDay() - from.toEpochDay() < MAX_PLAN_DAYS, "Aralık en fazla " + MAX_PLAN_DAYS + " gün olabilir");
        List<EmployeeRow> employees = employees(from, to, departmentId);
        List<UUID> ids = employees.stream().map(EmployeeRow::id).toList();
        List<PlanEntry> entries = ids.isEmpty() ? List.of()
                : db.list("select a from ShiftAssignment a where a.date >= ?1 and a.date <= ?2 and a.employeeId in ?3 order by a.date", ShiftAssignment.class, from, to, ids)
                .stream().map(a -> new PlanEntry(a.employeeId, a.date, a.shiftId)).toList();
        return new ShiftPlanDto(from, to, employees.stream().map(e -> new PlanEmployee(e.id(), e.employeeNo(), e.firstName() + " " + e.lastName(), e.department())).toList(), entries);
    }

    public int updatePlan(UpdateShiftPlanRequest body) {
        List<ShiftPlanChange> changes = body.entries() == null ? List.of() : body.entries();
        Guard.that(!changes.isEmpty() && changes.size() <= 1000, "1 ile 1000 arasında değişiklik gönderin");
        Guard.that(changes.stream().allMatch(c -> c.employeeId() != null && c.date() != null), "Personel ve tarih zorunludur");
        Set<UUID> employeeIds = new HashSet<>();
        Set<UUID> shiftIds = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        for (ShiftPlanChange c : changes) {
            employeeIds.add(c.employeeId());
            dates.add(c.date());
            if (!c.useDefault() && c.shiftId() != null) shiftIds.add(c.shiftId());
        }
        if (db.count("select count(e) from Employee e where e.id in ?1", employeeIds) != employeeIds.size()) throw AppException.notFound("Personel bulunamadı");
        if (!shiftIds.isEmpty() && db.count("select count(s) from Shift s where s.isActive = true and s.id in ?1", shiftIds) != shiftIds.size()) {
            throw AppException.notFound("Vardiya bulunamadı veya aktif değil");
        }
        Map<String, ShiftAssignment> existing = new HashMap<>();
        for (ShiftAssignment a : db.list("select a from ShiftAssignment a where a.employeeId in ?1 and a.date in ?2", ShiftAssignment.class, employeeIds, dates)) {
            existing.put(a.employeeId + "/" + a.date, a);
        }
        for (ShiftPlanChange c : changes) {
            String key = c.employeeId() + "/" + c.date();
            ShiftAssignment row = existing.get(key);
            if (c.useDefault()) {
                if (row != null) {
                    db.em().remove(row);
                    existing.remove(key);
                }
                continue;
            }
            if (row == null) {
                row = new ShiftAssignment();
                row.employeeId = c.employeeId();
                row.date = c.date();
                db.add(row);
                existing.put(key, row);
            }
            row.shiftId = c.shiftId();
        }
        db.flush();
        return changes.size();
    }

    // ---------- timesheet ----------

    @Transactional(readOnly = true)
    public TimesheetDto timesheet(String month, UUID departmentId) {
        YearMonth ym = month(month);
        LocalDate from = ym.atDay(1), to = ym.atEndOfMonth();
        List<EmployeeRow> employees = employees(from, to, departmentId);
        int offset = offset();
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) days.add(d);
        return new TimesheetDto(ym.toString(), days, today(offset), rows(employees, from, to, offset));
    }

    @Transactional(readOnly = true)
    public MyAttendanceDto mine(String month) {
        EmployeeRow employee = own();
        int offset = offset();
        LocalDate today = today(offset);
        YearMonth ym = month == null || month.isBlank() ? YearMonth.from(today) : month(month);
        TimesheetRow row = rows(List.of(employee), ym.atDay(1), ym.atEndOfMonth(), offset).get(0);
        TimesheetRow todayRow = rows(List.of(employee), today, today, offset).get(0);
        AttendanceRecord record = db.first("select r from AttendanceRecord r where r.employeeId = ?1 and r.date = ?2", AttendanceRecord.class, employee.id(), today).orElse(null);
        AttendanceRecord open = openRecord(employee.id(), today);
        return new MyAttendanceDto(todayRow.days().get(0), record == null || record.checkIn == null, open != null, row);
    }

    public MyAttendanceDto checkIn() {
        EmployeeRow employee = own();
        LocalDate today = today(offset());
        AttendanceRecord record = db.first("select r from AttendanceRecord r where r.employeeId = ?1 and r.date = ?2", AttendanceRecord.class, employee.id(), today).orElse(null);
        Guard.state(record == null || record.checkIn == null, "Bugün zaten giriş yaptınız");
        if (record == null) {
            record = new AttendanceRecord();
            record.employeeId = employee.id();
            record.date = today;
            db.add(record);
        }
        record.checkIn = Instant.now();
        record.source = AttendanceSource.SELF;
        db.flush();
        return mine(null);
    }

    public MyAttendanceDto checkOut() {
        EmployeeRow employee = own();
        AttendanceRecord open = openRecord(employee.id(), today(offset()));
        if (open == null) throw AppException.conflict("Kapatılacak açık bir giriş yok");
        open.checkOut = Instant.now();
        db.flush();
        return mine(null);
    }

    /** HR correction of one day; clearing the times and the note removes the record. */
    public TimesheetDay updateRecord(UpdateRecordRequest body) {
        Guard.that(body.employeeId() != null && body.date() != null, "Personel ve tarih zorunludur");
        Employee found = db.first("select e from Employee e where e.id = ?1", Employee.class, body.employeeId())
                .orElseThrow(() -> AppException.notFound("Personel bulunamadı"));
        EmployeeRow e = row(found, departmentNames());
        String checkIn = Guard.blank(body.checkIn()) ? null : body.checkIn().trim();
        String checkOut = Guard.blank(body.checkOut()) ? null : body.checkOut().trim();
        Guard.that((checkIn == null || Timesheet.isClock(checkIn)) && (checkOut == null || Timesheet.isClock(checkOut)), "Saatler SS:dd biçiminde olmalı");
        Guard.that(checkOut == null || checkIn != null, "Çıkış için giriş saati gerekir");
        String note = Guard.blank(body.note()) ? null : body.note().trim();
        Guard.that(note == null || note.length() <= 500, "Not en fazla 500 karakter olabilir");
        int offset = offset();

        AttendanceRecord record = db.first("select r from AttendanceRecord r where r.employeeId = ?1 and r.date = ?2", AttendanceRecord.class, e.id(), body.date()).orElse(null);
        if (checkIn == null && note == null) {
            if (record != null) db.em().remove(record);
        } else {
            if (record == null) {
                record = new AttendanceRecord();
                record.employeeId = e.id();
                record.date = body.date();
                db.add(record);
            }
            Integer in = checkIn == null ? null : Timesheet.parseClock(checkIn);
            Integer out = checkOut == null ? null : Timesheet.parseClock(checkOut);
            if (in != null && out != null && out <= in) out += Timesheet.DAY_MINUTES;
            record.checkIn = in == null ? null : Timesheet.instantOf(body.date(), in, offset);
            record.checkOut = out == null ? null : Timesheet.instantOf(body.date(), out, offset);
            record.note = note;
            record.source = AttendanceSource.MANUAL;
            record.updatedById = CurrentUser.get().userId;
        }
        db.flush();
        return rows(List.of(e), body.date(), body.date(), offset).get(0).days().get(0);
    }

    // ---------- helpers ----------

    private List<TimesheetRow> rows(List<EmployeeRow> employees, LocalDate from, LocalDate to, int offset) {
        if (employees.isEmpty()) return List.of();
        List<UUID> ids = employees.stream().map(EmployeeRow::id).toList();
        Map<UUID, Shift> shifts = new HashMap<>();
        db.list("select s from Shift s", Shift.class).forEach(s -> shifts.put(s.id, s));
        Map<String, ShiftAssignment> plans = new HashMap<>();
        for (ShiftAssignment a : db.list("select a from ShiftAssignment a where a.employeeId in ?1 and a.date >= ?2 and a.date <= ?3", ShiftAssignment.class, ids, from, to)) {
            plans.put(a.employeeId + "/" + a.date, a);
        }
        Map<String, AttendanceRecord> records = new HashMap<>();
        for (AttendanceRecord r : db.list("select r from AttendanceRecord r where r.employeeId in ?1 and r.date >= ?2 and r.date <= ?3", AttendanceRecord.class, ids, from, to)) {
            records.put(r.employeeId + "/" + r.date, r);
        }
        Set<LocalDate> holidays = new HashSet<>(db.list("select h.date from Holiday h where h.date >= ?1 and h.date <= ?2", LocalDate.class, from, to));
        List<LeaveRequest> leaves = db.list("select l from LeaveRequest l where l.employeeId in ?1 and l.status = ?2 and l.startDate <= ?3 and l.endDate >= ?4",
                LeaveRequest.class, ids, LeaveRequestStatus.APPROVED, to, from);
        Instant now = Instant.now();

        List<TimesheetRow> out = new ArrayList<>();
        for (EmployeeRow e : employees) {
            TimesheetTotals totals = new TimesheetTotals();
            List<TimesheetDay> days = new ArrayList<>();
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                LocalDate d = date;
                boolean employed = !d.isBefore(e.hireDate()) && (e.terminationDate() == null || !d.isAfter(e.terminationDate()));
                ShiftTimes shift = employed ? resolveShift(d, plans.get(e.id() + "/" + d), shifts) : null;
                AttendanceRecord record = records.get(e.id() + "/" + d);
                boolean onLeave = employed && leaves.stream().anyMatch(l -> l.employeeId.equals(e.id()) && !l.startDate.isAfter(d) && !d.isAfter(l.endDate));
                boolean holiday = employed && holidays.contains(d);
                Integer checkIn = record != null && record.checkIn != null ? Timesheet.minutesSince(record.checkIn, d, offset) : null;
                Integer checkOut = record != null && record.checkOut != null ? Timesheet.minutesSince(record.checkOut, d, offset) : null;
                DayResult result = Timesheet.evaluate(shift, holiday, onLeave, checkIn, checkOut, Timesheet.minutesSince(now, d, offset));

                if (result.plannedMinutes() > 0) totals.plannedDays++;
                if (result.status() == DayStatus.PRESENT || result.status() == DayStatus.LATE || result.status() == DayStatus.INCOMPLETE) totals.presentDays++;
                if (result.lateMinutes() > 0) totals.lateDays++;
                if (result.status() == DayStatus.ABSENT) totals.absentDays++;
                if (result.status() == DayStatus.LEAVE) totals.leaveDays++;
                totals.plannedMinutes += result.plannedMinutes();
                totals.workedMinutes += result.workedMinutes();
                totals.lateMinutes += result.lateMinutes();
                totals.overtimeMinutes += result.overtimeMinutes();

                Window window = shift == null ? null : Timesheet.window(shift);
                days.add(new TimesheetDay(d, result.status(), shift == null ? null : shift.code(), shift == null ? null : shift.color(),
                        window == null ? null : Timesheet.formatClock(window.start()), window == null ? null : Timesheet.formatClock(window.end()),
                        checkIn == null ? null : Timesheet.formatClock(checkIn), checkOut == null ? null : Timesheet.formatClock(checkOut),
                        result.plannedMinutes(), result.workedMinutes(), result.lateMinutes(), result.overtimeMinutes(),
                        record == null ? null : record.source, record == null ? null : record.note));
            }
            out.add(new TimesheetRow(e.id(), e.employeeNo(), e.firstName() + " " + e.lastName(), e.department(), days, totals));
        }
        return out;
    }

    /** An explicit plan entry (no shift = day off), else the default weekday schedule. */
    private static ShiftTimes resolveShift(LocalDate date, ShiftAssignment entry, Map<UUID, Shift> shifts) {
        if (entry != null) {
            Shift s = entry.shiftId == null ? null : shifts.get(entry.shiftId);
            return s == null ? null : new ShiftTimes(s.code, s.startTime, s.endTime, s.breakMinutes, s.color);
        }
        return WorkingDays.isWeekend(date) ? null : Timesheet.DEFAULT_SHIFT;
    }

    private Map<UUID, String> departmentNames() {
        Map<UUID, String> out = new HashMap<>();
        db.list("select d from Department d", Department.class).forEach(d -> out.put(d.id, d.name));
        return out;
    }

    private static EmployeeRow row(Employee e, Map<UUID, String> departments) {
        return new EmployeeRow(e.id, e.employeeNo, e.firstName, e.lastName, e.hireDate, e.terminationDate, e.departmentId == null ? null : departments.get(e.departmentId));
    }

    private List<EmployeeRow> employees(LocalDate from, LocalDate to, UUID departmentId) {
        Map<UUID, String> departments = departmentNames();
        String jpql = "select e from Employee e where e.hireDate <= ?1 and (e.terminationDate is null or e.terminationDate >= ?2)"
                + (departmentId == null ? "" : " and e.departmentId = ?3") + " order by e.firstName, e.lastName";
        List<Employee> rows = departmentId == null ? db.list(jpql, Employee.class, to, from) : db.list(jpql, Employee.class, to, from, departmentId);
        return rows.stream().map(e -> row(e, departments)).toList();
    }

    private EmployeeRow own() {
        UUID userId = CurrentUser.get().requireUserId();
        Employee e = db.first("select e from Employee e where e.userId = ?1", Employee.class, userId)
                .orElseThrow(() -> AppException.notFound("Hesabınıza bağlı bir personel kaydı yok"));
        return row(e, departmentNames());
    }

    /** Latest check-in without a check-out from today or yesterday (night shifts). */
    private AttendanceRecord openRecord(UUID employeeId, LocalDate today) {
        return db.first("select r from AttendanceRecord r where r.employeeId = ?1 and r.checkIn is not null and r.checkOut is null and r.date >= ?2 and r.date <= ?3 order by r.date desc",
                AttendanceRecord.class, employeeId, today.minusDays(1), today).orElse(null);
    }

    public int offset() {
        JsonNode value = db.first("select s from Setting s where s.key = 'general'", dev.enterprisepulse.data.generated.Setting.class).map(s -> s.value).orElse(null);
        String zone = value != null && value.path("timezone").isTextual() ? value.path("timezone").asText() : DEFAULT_TIME_ZONE;
        ZoneId id;
        try {
            id = ZoneId.of(zone);
        } catch (RuntimeException ex) {
            id = ZoneId.of(DEFAULT_TIME_ZONE);
        }
        return id.getRules().getOffset(Instant.now()).getTotalSeconds() / 60;
    }

    public static LocalDate today(int offset) {
        return LocalDate.ofInstant(Instant.now().plusSeconds(offset * 60L), java.time.ZoneOffset.UTC);
    }

    private static YearMonth month(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw AppException.badRequest("Ay YYYY-AA biçiminde olmalı");
        }
    }
}
