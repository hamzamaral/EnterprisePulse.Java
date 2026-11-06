package dev.enterprisepulse.web;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Paged;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.LeaveRequestStatus;
import dev.enterprisepulse.hr.AttendanceService;
import dev.enterprisepulse.hr.EmployeeService;
import dev.enterprisepulse.hr.LeaveService;
import dev.enterprisepulse.resource.ExportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hr")
@Tag(name = "HR")
public class HrController {
    public record CancelLeaveRequest(String comment) {}

    public record AccrueRequest(int year) {}

    public record EntitlementRequest(int entitledDays) {}

    private final EmployeeService employees;
    private final LeaveService leave;
    private final AttendanceService attendance;
    private final ExportService export;

    public HrController(EmployeeService employees, LeaveService leave, AttendanceService attendance, ExportService export) {
        this.employees = employees;
        this.leave = leave;
        this.attendance = attendance;
        this.export = export;
    }

    private static void require(String code) {
        CurrentUser.get().require(code);
    }

    @GetMapping("/org-chart")
    public List<EmployeeService.OrgChartNode> orgChart() {
        return employees.orgChart();
    }

    @GetMapping("/employees/me")
    public EmployeeService.MyEmployeeDto me() {
        return employees.mine();
    }

    @PostMapping("/employees/{id}/terminate")
    public Employee terminate(@PathVariable UUID id, @RequestBody EmployeeService.TerminateRequest body) {
        require("employees:update");
        return employees.terminate(id, body);
    }

    @GetMapping("/leave-types")
    public List<LeaveService.LeaveTypeOption> leaveTypes() {
        return leave.types();
    }

    @GetMapping("/leave-requests/working-days")
    public Map<String, Integer> workingDays(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
                                            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return Map.of("days", leave.workingDays(startDate, endDate));
    }

    @GetMapping("/leave-requests")
    public Paged<LeaveService.LeaveRequestDto> leaveRequests(@RequestParam(defaultValue = "false") boolean mine, @RequestParam(required = false) LeaveRequestStatus status,
                                                             @RequestParam(required = false) UUID employeeId, @RequestParam(required = false) String search,
                                                             @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int pageSize) {
        return leave.list(mine, status, employeeId, search, Math.max(1, page), Math.max(1, Math.min(200, pageSize)));
    }

    @GetMapping("/leave-requests/{id}")
    public LeaveService.LeaveRequestDto leaveRequest(@PathVariable UUID id) {
        return leave.get(id);
    }

    @PostMapping("/leave-requests")
    public LeaveService.LeaveRequestDto createLeaveRequest(@RequestBody LeaveService.CreateLeaveRequest body) {
        return leave.create(body);
    }

    @PostMapping("/leave-requests/{id}/cancel")
    public LeaveService.LeaveRequestDto cancelLeaveRequest(@PathVariable UUID id, @RequestBody(required = false) CancelLeaveRequest body) {
        return leave.cancel(id, body == null ? null : body.comment());
    }

    @GetMapping("/leave-balances")
    public List<LeaveService.LeaveBalanceDto> balances(@RequestParam(required = false) UUID employeeId, @RequestParam(required = false) Integer year) {
        return leave.visibleBalances(employeeId, year == null ? LocalDate.now(ZoneOffset.UTC).getYear() : year);
    }

    @PostMapping("/leave-balances/accrue")
    public Map<String, Integer> accrue(@RequestBody AccrueRequest body) {
        require("leave:update");
        return Map.of("created", leave.accrue(body.year()));
    }

    @PatchMapping("/leave-balances/{id}")
    public LeaveService.LeaveBalanceDto updateBalance(@PathVariable UUID id, @RequestBody EntitlementRequest body) {
        require("leave:update");
        return leave.updateEntitlement(id, body.entitledDays());
    }

    @GetMapping("/shift-plan")
    public AttendanceService.ShiftPlanDto shiftPlan(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                                    @RequestParam(required = false) UUID departmentId) {
        require("attendance:read");
        return attendance.plan(from, to, departmentId);
    }

    @PutMapping("/shift-plan")
    public Map<String, Integer> updateShiftPlan(@RequestBody AttendanceService.UpdateShiftPlanRequest body) {
        require("attendance:update");
        return Map.of("updated", attendance.updatePlan(body));
    }

    @GetMapping("/attendance/timesheet")
    public AttendanceService.TimesheetDto timesheet(@RequestParam String month, @RequestParam(required = false) UUID departmentId) {
        require("attendance:read");
        return attendance.timesheet(month, departmentId);
    }

    @GetMapping("/attendance/timesheet/export")
    public ResponseEntity<byte[]> exportTimesheet(@RequestParam String month, @RequestParam(required = false) UUID departmentId, @RequestParam(required = false) String format) {
        require("attendance:read");
        var sheet = attendance.timesheet(month, departmentId);
        List<String> headers = List.of("Sicil No", "Personel", "Departman", "Planlı gün", "Gelinen gün", "Geç kalınan", "Devamsız", "İzinli", "Planlı saat",
                "Çalışılan saat", "Fazla mesai (saat)");
        List<List<String>> rows = sheet.rows().stream().map(r -> List.of(r.employeeNo(), r.employeeName(), r.departmentName() == null ? "" : r.departmentName(),
                String.valueOf(r.totals().plannedDays), String.valueOf(r.totals().presentDays), String.valueOf(r.totals().lateDays),
                String.valueOf(r.totals().absentDays), String.valueOf(r.totals().leaveDays), hours(r.totals().plannedMinutes),
                hours(r.totals().workedMinutes), hours(r.totals().overtimeMinutes))).toList();
        return ResourceController.file(export.table("Puantaj " + month, "puantaj-" + month, headers, rows, "pdf".equals(format) ? "pdf" : "xlsx"));
    }

    private static String hours(int minutes) {
        return String.format(Locale.forLanguageTag("tr-TR"), "%.1f", minutes / 60.0);
    }

    @PutMapping("/attendance/records")
    public AttendanceService.TimesheetDay updateRecord(@RequestBody AttendanceService.UpdateRecordRequest body) {
        require("attendance:update");
        return attendance.updateRecord(body);
    }

    @GetMapping("/attendance/me")
    public AttendanceService.MyAttendanceDto myAttendance(@RequestParam(required = false) String month) {
        return attendance.mine(month);
    }

    @PostMapping("/attendance/check-in")
    public AttendanceService.MyAttendanceDto checkIn() {
        return attendance.checkIn();
    }

    @PostMapping("/attendance/check-out")
    public AttendanceService.MyAttendanceDto checkOut() {
        return attendance.checkOut();
    }
}
