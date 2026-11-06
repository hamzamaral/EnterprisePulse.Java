package dev.enterprisepulse.hr;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.AttendanceRecord;
import dev.enterprisepulse.data.generated.AttendanceSource;
import dev.enterprisepulse.data.generated.Department;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.data.generated.Gender;
import dev.enterprisepulse.data.generated.Holiday;
import dev.enterprisepulse.data.generated.LeaveBalance;
import dev.enterprisepulse.data.generated.LeaveRequest;
import dev.enterprisepulse.data.generated.LeaveRequestStatus;
import dev.enterprisepulse.data.generated.LeaveType;
import dev.enterprisepulse.data.generated.Position;
import dev.enterprisepulse.data.generated.Shift;
import dev.enterprisepulse.data.generated.ShiftAssignment;
import dev.enterprisepulse.hr.HrRules.ShiftTimes;
import dev.enterprisepulse.hr.HrRules.Timesheet;
import dev.enterprisepulse.hr.HrRules.Window;
import dev.enterprisepulse.hr.HrRules.WorkingDays;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.seed.DataSeeder;
import dev.enterprisepulse.seed.SeedStep;
import dev.enterprisepulse.workflow.WorkflowService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Demo organisation of Pulse Teknoloji: departments, staff, leave, holidays, shifts and two months of attendance. */
@Component
public class HrSeed implements SeedStep {
    private static final String[][] DEPARTMENTS = {
            {"YON", "Yönetim", null}, {"BT", "Bilgi Teknolojileri", "YON"}, {"IK", "İnsan Kaynakları", "YON"},
            {"FIN", "Finans", "YON"}, {"SAT", "Satış", "YON"}, {"OPS", "Operasyon", "YON"},
    };

    private static final String[][] POSITIONS = {
            {"GM", "Genel Müdür", "YON"}, {"BT-MDR", "BT Müdürü", "BT"}, {"YAZ", "Yazılım Geliştirici", "BT"}, {"SIS", "Sistem Yöneticisi", "BT"},
            {"IK-MDR", "İK Müdürü", "IK"}, {"IK-UZM", "İK Uzmanı", "IK"}, {"FIN-MDR", "Finans Müdürü", "FIN"}, {"MUH", "Muhasebe Uzmanı", "FIN"},
            {"SAT-MDR", "Satış Müdürü", "SAT"}, {"SAT-TEM", "Satış Temsilcisi", "SAT"}, {"OPS-MDR", "Operasyon Müdürü", "OPS"}, {"LOJ", "Lojistik Uzmanı", "OPS"},
    };

    /** Name, department, position, manager, hire date, gender; the first one heads the company. */
    public static final String[][] EMPLOYEES = {
            {"Hakan Erdem", "YON", "GM", null, "2015-01-05", "MALE"},
            {"Deniz Aksoy", "BT", "BT-MDR", "Hakan Erdem", "2018-03-12", "FEMALE"},
            {"Ayşe Yılmaz", "IK", "IK-MDR", "Hakan Erdem", "2017-06-01", "FEMALE"},
            {"Mehmet Kaya", "FIN", "FIN-MDR", "Hakan Erdem", "2016-09-19", "MALE"},
            {"Zeynep Demir", "SAT", "SAT-MDR", "Hakan Erdem", "2019-02-04", "FEMALE"},
            {"Mustafa Çelik", "OPS", "OPS-MDR", "Hakan Erdem", "2018-11-15", "MALE"},
            {"Kerem Polat", "BT", "YAZ", "Deniz Aksoy", "2022-04-18", "MALE"},
            {"Elif Şahin", "BT", "YAZ", "Deniz Aksoy", "2023-01-09", "FEMALE"},
            {"Ahmet Yıldız", "BT", "SIS", "Deniz Aksoy", "2021-07-01", "MALE"},
            {"Fatma Öztürk", "IK", "IK-UZM", "Ayşe Yılmaz", "2020-10-12", "FEMALE"},
            {"Emre Aydın", "FIN", "MUH", "Mehmet Kaya", "2022-08-22", "MALE"},
            {"Selin Arslan", "SAT", "SAT-TEM", "Zeynep Demir", "2023-05-15", "FEMALE"},
            {"Burak Doğan", "SAT", "SAT-TEM", "Zeynep Demir", "2024-02-01", "MALE"},
            {"Merve Koç", "OPS", "LOJ", "Mustafa Çelik", "2021-03-29", "FEMALE"},
            {"Can Kurt", "OPS", "LOJ", "Mustafa Çelik", "2025-06-02", "MALE"},
    };

    private record LeaveTypeSeed(String code, String name, int days, boolean paid, boolean balance, String color) {}

    private static final List<LeaveTypeSeed> LEAVE_TYPES = List.of(
            new LeaveTypeSeed("YILLIK", "Yıllık İzin", 14, true, true, "#6366f1"), new LeaveTypeSeed("MAZERET", "Mazeret İzni", 5, true, true, "#f59e0b"),
            new LeaveTypeSeed("HASTALIK", "Hastalık İzni", 0, true, false, "#ef4444"), new LeaveTypeSeed("UCRETSIZ", "Ücretsiz İzin", 0, false, false, "#64748b"));

    private static final String[][] FIXED_HOLIDAYS = {
            {"01-01", "Yılbaşı"}, {"04-23", "Ulusal Egemenlik ve Çocuk Bayramı"}, {"05-01", "Emek ve Dayanışma Günü"},
            {"05-19", "Atatürk'ü Anma, Gençlik ve Spor Bayramı"}, {"07-15", "Demokrasi ve Milli Birlik Günü"}, {"08-30", "Zafer Bayramı"}, {"10-29", "Cumhuriyet Bayramı"},
    };

    /** Religious holidays follow the lunar calendar, so they are listed per year. */
    private static final Map<Integer, String[][]> RELIGIOUS_HOLIDAYS = Map.of(
            2026, new String[][] {{"2026-03-20", "Ramazan Bayramı 1. Gün"}, {"2026-03-21", "Ramazan Bayramı 2. Gün"}, {"2026-03-22", "Ramazan Bayramı 3. Gün"},
                    {"2026-05-27", "Kurban Bayramı 1. Gün"}, {"2026-05-28", "Kurban Bayramı 2. Gün"}, {"2026-05-29", "Kurban Bayramı 3. Gün"}, {"2026-05-30", "Kurban Bayramı 4. Gün"}},
            2027, new String[][] {{"2027-03-09", "Ramazan Bayramı 1. Gün"}, {"2027-03-10", "Ramazan Bayramı 2. Gün"}, {"2027-03-11", "Ramazan Bayramı 3. Gün"},
                    {"2027-05-16", "Kurban Bayramı 1. Gün"}, {"2027-05-17", "Kurban Bayramı 2. Gün"}, {"2027-05-18", "Kurban Bayramı 3. Gün"}, {"2027-05-19", "Kurban Bayramı 4. Gün"}});

    private static final Object[][] SHIFTS = {
            {"GND", "Gündüz", "09:00", "18:00", 60, "#6366f1"}, {"SBH", "Sabah", "07:00", "15:00", 30, "#0ea5e9"},
            {"AKS", "Akşam", "15:00", "23:00", 30, "#f59e0b"}, {"GCE", "Gece", "23:00", "07:00", 30, "#64748b"},
    };

    /** Europe/Istanbul has no daylight saving time. */
    private static final int OFFSET = 180;

    private final Db db;
    private final NumberingService numbering;
    private final WorkflowService workflow;

    public HrSeed(Db db, NumberingService numbering, WorkflowService workflow) {
        this.db = db;
        this.numbering = numbering;
        this.workflow = workflow;
    }

    @Override
    public int order() {
        return 10;
    }

    private UUID existingId(String table, UUID companyId, String code) {
        List<Object[]> rows = db.nativeRows("select cast(id as text), 1 from " + table + " where company_id = ?1 and code = ?2", companyId, code);
        return rows.isEmpty() ? null : UUID.fromString((String) rows.get(0)[0]);
    }

    @Override
    public void run(Context ctx) {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        Map<String, UUID> departments = new HashMap<>();
        for (String[] d : DEPARTMENTS) {
            UUID id = existingId("departments", ctx.pulseId, d[0]);
            if (id == null) {
                Department row = new Department();
                row.code = d[0];
                row.name = d[1];
                row.parentId = d[2] == null ? null : departments.get(d[2]);
                db.add(row);
                db.flush();
                id = row.id;
            }
            departments.put(d[0], id);
        }

        Map<String, UUID> positions = new HashMap<>();
        for (String[] p : POSITIONS) {
            UUID id = existingId("positions", ctx.pulseId, p[0]);
            if (id == null) {
                Position row = new Position();
                row.code = p[0];
                row.name = p[1];
                row.departmentId = departments.get(p[2]);
                db.add(row);
                db.flush();
                id = row.id;
            }
            positions.put(p[0], id);
        }

        Map<String, Employee> employees = new LinkedHashMap<>();
        int index = 0;
        for (String[] s : EMPLOYEES) {
            index++;
            String fullName = s[0];
            String first = fullName.substring(0, fullName.indexOf(' '));
            String last = fullName.substring(fullName.indexOf(' ') + 1);
            Employee e = db.first("select e from Employee e where e.firstName = ?1 and e.lastName = ?2", Employee.class, first, last).orElse(null);
            if (e == null) {
                UUID userId = ctx.usersByName.get(fullName);
                e = new Employee();
                e.employeeNo = numbering.next("EMPLOYEE");
                e.firstName = first;
                e.lastName = last;
                e.gender = Gender.valueOf(s[5]);
                e.userId = userId;
                e.branchId = ctx.hqBranchId;
                e.email = userId == null ? DataSeeder.slug(first) + "@pulse.example" : db.one("select u.email from User u where u.id = ?1", String.class, userId);
                e.phone = String.format("+90 532 000 %04d", index);
                e.hireDate = LocalDate.parse(s[4]);
                e.departmentId = departments.get(s[1]);
                e.positionId = positions.get(s[2]);
                e.managerId = s[3] == null ? null : employees.get(s[3]).id;
                db.add(e);
                db.flush();
            }
            employees.put(fullName, e);
        }
        for (String[] s : EMPLOYEES) {
            if (!s[2].equals("GM") && !s[2].endsWith("-MDR")) continue;
            db.execute("update Department d set d.managerId = ?1 where d.companyId = ?2 and d.code = ?3 and d.managerId is null",
                    employees.get(s[0]).id, ctx.pulseId, s[1]);
        }

        Map<String, UUID> leaveTypes = new HashMap<>();
        for (LeaveTypeSeed t : LEAVE_TYPES) {
            LeaveType row = db.first("select t from LeaveType t where t.code = ?1", LeaveType.class, t.code()).orElse(null);
            if (row == null) {
                row = new LeaveType();
                row.code = t.code();
                row.name = t.name();
                row.defaultDays = t.days();
                row.isPaid = t.paid();
                row.requiresBalance = t.balance();
                row.color = t.color();
                db.add(row);
                db.flush();
            }
            leaveTypes.put(t.code(), row.id);
        }
        for (Employee e : employees.values()) {
            for (var entry : Map.of("YILLIK", WorkingDays.annualLeaveDays(e.hireDate, year), "MAZERET", 5).entrySet()) {
                UUID typeId = leaveTypes.get(entry.getKey());
                if (db.exists("select b.id from LeaveBalance b where b.employeeId = ?1 and b.leaveTypeId = ?2 and b.year = ?3", e.id, typeId, year)) continue;
                LeaveBalance b = new LeaveBalance();
                b.employeeId = e.id;
                b.leaveTypeId = typeId;
                b.year = year;
                b.entitledDays = entry.getValue();
                db.add(b);
            }
        }
        db.flush();

        Map<LocalDate, String> holidays = new LinkedHashMap<>();
        for (int y : new int[] {year, year + 1}) {
            for (String[] h : FIXED_HOLIDAYS) holidays.putIfAbsent(LocalDate.parse(y + "-" + h[0]), h[1]);
        }
        for (int y : new int[] {year, year + 1}) {
            for (String[] h : RELIGIOUS_HOLIDAYS.getOrDefault(y, new String[0][])) holidays.putIfAbsent(LocalDate.parse(h[0]), h[1]);
        }
        Set<LocalDate> existingHolidays = new HashSet<>(db.list("select h.date from Holiday h", LocalDate.class));
        holidays.forEach((date, name) -> {
            if (existingHolidays.contains(date)) return;
            Holiday h = new Holiday();
            h.date = date;
            h.name = name;
            db.add(h);
        });
        db.flush();

        Map<String, Shift> shifts = new HashMap<>();
        for (Object[] s : SHIFTS) {
            Shift row = db.first("select s from Shift s where s.code = ?1", Shift.class, s[0]).orElse(null);
            if (row == null) {
                row = new Shift();
                row.code = (String) s[0];
                row.name = (String) s[1];
                row.startTime = (String) s[2];
                row.endTime = (String) s[3];
                row.breakMinutes = (Integer) s[4];
                row.color = (String) s[5];
                db.add(row);
                db.flush();
            }
            shifts.put(row.code, row);
        }

        seedLeaveRequests(ctx, employees, leaveTypes, holidays.keySet(), year);
        seedAttendance(new ArrayList<>(employees.values()), shifts, departments.get("OPS"));
    }

    private record LeaveSample(String employee, String type, LocalDate start, LocalDate end, String reason, boolean approved) {}

    private void seedLeaveRequests(Context ctx, Map<String, Employee> employees, Map<String, UUID> types, Set<LocalDate> holidays, int year) {
        if (db.exists("select r.id from LeaveRequest r")) return;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate nextMonday = today.plusDays(14);
        while (nextMonday.getDayOfWeek() != DayOfWeek.MONDAY) nextMonday = nextMonday.plusDays(1);
        if (nextMonday.getYear() != year) nextMonday = LocalDate.of(year, 12, 21);
        List<LeaveSample> samples = List.of(
                new LeaveSample("Elif Şahin", "YILLIK", LocalDate.of(year, 7, 13), LocalDate.of(year, 7, 17), "Yaz tatili", true),
                new LeaveSample("Can Kurt", "HASTALIK", LocalDate.of(year, 2, 9), LocalDate.of(year, 2, 10), "Doktor raporu", true),
                new LeaveSample("Kerem Polat", "YILLIK", nextMonday, nextMonday.plusDays(4), "Aile ziyareti", false));
        for (LeaveSample s : samples) {
            Employee employee = employees.get(s.employee());
            UUID requester = ctx.usersByName.get(s.employee());
            int days = WorkingDays.count(s.start(), s.end(), holidays);
            LeaveRequest request = new LeaveRequest();
            request.number = numbering.next(LeaveService.WORKFLOW_CODE);
            request.employeeId = employee.id;
            request.leaveTypeId = types.get(s.type());
            request.startDate = s.start();
            request.endDate = s.end();
            request.days = days;
            request.reason = s.reason();
            request.status = s.approved() ? LeaveRequestStatus.APPROVED : LeaveRequestStatus.PENDING;
            request.createdById = requester;
            request.decidedAt = s.approved() ? Instant.now() : null;
            db.add(request);
            db.flush();
            if (s.approved()) {
                db.execute("update LeaveBalance b set b.usedDays = b.usedDays + ?1 where b.employeeId = ?2 and b.leaveTypeId = ?3 and b.year = ?4",
                        days, employee.id, request.leaveTypeId, year);
                continue;
            }
            try (var scope = CurrentUser.get().actAs(ctx.pulseId, requester, false)) {
                var instance = workflow.start(new WorkflowService.StartRequest(LeaveService.WORKFLOW_CODE, request.id.toString(),
                        s.employee() + " — Yıllık İzin (" + days + " gün)"));
                request.workflowInstanceId = instance.id();
                db.flush();
            }
        }
    }

    /** Deterministic pseudo-random number in [0, 100) so re-seeding yields the same data. */
    private static double noise(double a, double b) {
        return Math.abs(Math.sin(a * 12.9898 + b * 78.233) * 43758.5453) % 100;
    }

    /**
     * A morning / evening rotation for operations and attendance from the start of the previous month until yesterday
     * (some late arrivals, absences and overtime). Re-running tops up the days since the previous run.
     */
    private void seedAttendance(List<Employee> employees, Map<String, Shift> shifts, UUID opsId) {
        LocalDate today = LocalDate.ofInstant(Instant.now().plusSeconds(OFFSET * 60L), ZoneOffset.UTC);
        LocalDate from = today.withDayOfMonth(1).minusMonths(1);
        LocalDate planEnd = today.plusDays(13);
        Set<LocalDate> holidays = new HashSet<>(db.list("select h.date from Holiday h where h.date >= ?1 and h.date <= ?2", LocalDate.class, from, today));
        List<LeaveRequest> leaves = db.list("select l from LeaveRequest l where l.status = ?1 and l.endDate >= ?2", LeaveRequest.class, LeaveRequestStatus.APPROVED, from);
        Set<String> planned = new HashSet<>();
        for (Object[] x : db.list("select a.employeeId, a.date from ShiftAssignment a where a.date >= ?1", Object[].class, from)) planned.add(x[0] + "/" + x[1]);
        Set<String> recorded = new HashSet<>();
        for (Object[] x : db.list("select a.employeeId, a.date from AttendanceRecord a where a.date >= ?1", Object[].class, from)) recorded.add(x[0] + "/" + x[1]);

        List<Employee> ordered = employees.stream().filter(e -> e.status == EmployeeStatus.ACTIVE).sorted(Comparator.comparing(e -> e.employeeNo)).toList();
        for (int i = 0; i < ordered.size(); i++) {
            Employee e = ordered.get(i);
            boolean rotates = opsId.equals(e.departmentId);
            for (LocalDate date = from; !date.isAfter(planEnd); date = date.plusDays(1)) {
                if (WorkingDays.isWeekend(date)) continue;
                LocalDate d = date;
                double dayNumber = d.toEpochDay();
                ShiftTimes shift = Timesheet.DEFAULT_SHIFT;
                if (rotates) {
                    int week = (int) Math.floor((dayNumber + 3) / 7);
                    Shift s = shifts.get((week + i) % 2 == 0 ? "SBH" : "AKS");
                    if (planned.add(e.id + "/" + d)) {
                        ShiftAssignment a = new ShiftAssignment();
                        a.employeeId = e.id;
                        a.date = d;
                        a.shiftId = s.id;
                        db.add(a);
                    }
                    shift = new ShiftTimes(s.code, s.startTime, s.endTime, s.breakMinutes);
                }
                if (!d.isBefore(today) || d.isBefore(e.hireDate) || holidays.contains(d)) continue;
                if (leaves.stream().anyMatch(l -> l.employeeId.equals(e.id) && !l.startDate.isAfter(d) && !d.isAfter(l.endDate))) continue;
                double roll = noise(i + 1, dayNumber);
                if (roll < 3 || !recorded.add(e.id + "/" + d)) continue;
                Window w = Timesheet.window(shift);
                int checkIn = roll < 13 ? w.start() + 8 + (int) Math.round(roll * 2) : w.start() - (int) Math.round(roll % 15);
                int checkOut = roll > 85 ? w.end() + 45 + (int) Math.round(roll % 60) : w.end() + (int) Math.round(roll % 12);
                AttendanceRecord r = new AttendanceRecord();
                r.employeeId = e.id;
                r.date = d;
                r.source = AttendanceSource.SELF;
                r.checkIn = Timesheet.instantOf(d, checkIn, OFFSET);
                r.checkOut = Timesheet.instantOf(d, checkOut, OFFSET);
                db.add(r);
            }
        }
        db.flush();
    }
}
