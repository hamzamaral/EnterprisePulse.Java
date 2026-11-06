package dev.enterprisepulse.hr;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.common.Paged;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.data.generated.LeaveBalance;
import dev.enterprisepulse.data.generated.LeaveRequest;
import dev.enterprisepulse.data.generated.LeaveRequestStatus;
import dev.enterprisepulse.data.generated.LeaveType;
import dev.enterprisepulse.data.generated.WorkflowStatus;
import dev.enterprisepulse.hr.HrRules.WorkingDays;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NotificationService.NotifyRequest;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.workflow.WorkflowService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leave requests, balances and the approval round-trip (İzin). */
@Service
@Transactional
public class LeaveService {
    public static final String WORKFLOW_CODE = "LEAVE_REQUEST";
    public static final String ENTITY_TYPE = "LeaveRequest";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public record CreateLeaveRequest(UUID employeeId, UUID leaveTypeId, LocalDate startDate, LocalDate endDate, String reason) {}

    public record LeaveRequestDto(UUID id, String number, UUID employeeId, String employeeName, UUID leaveTypeId, String leaveTypeName,
                                  String leaveTypeColor, LocalDate startDate, LocalDate endDate, int days, String reason, LeaveRequestStatus status,
                                  UUID workflowInstanceId, Instant decidedAt, Instant createdAt, boolean canCancel) {}

    public record LeaveBalanceDto(UUID id, UUID employeeId, String employeeName, UUID leaveTypeId, String leaveTypeName, String leaveTypeColor,
                                  int year, int entitledDays, int usedDays, int pendingDays, int remainingDays) {}

    public record LeaveTypeOption(UUID id, String code, String name, String color, boolean requiresBalance) {}

    private final Db db;
    private final NumberingService numbering;
    private final WorkflowService workflow;
    private final NotificationService notifications;

    public LeaveService(Db db, NumberingService numbering, WorkflowService workflow, NotificationService notifications) {
        this.db = db;
        this.numbering = numbering;
        this.workflow = workflow;
        this.notifications = notifications;
    }

    private static CurrentUser ctx() {
        return CurrentUser.get();
    }

    @Transactional(readOnly = true)
    public int workingDays(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) return 0;
        return WorkingDays.count(start, end, holidays(start, end));
    }

    Set<LocalDate> holidays(LocalDate from, LocalDate to) {
        return new HashSet<>(db.list("select h.date from Holiday h where h.date >= ?1 and h.date <= ?2", LocalDate.class, from, to));
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeOption> types() {
        return db.list("select t from LeaveType t where t.isActive = true order by t.name", LeaveType.class).stream()
                .map(t -> new LeaveTypeOption(t.id, t.code, t.name, t.color, t.requiresBalance)).toList();
    }

    @Transactional(readOnly = true)
    public Paged<LeaveRequestDto> list(boolean mine, LeaveRequestStatus status, UUID employeeId, String search, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" from LeaveRequest r join Employee e on e.id = r.employeeId where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (mine || !ctx().has("leave:read")) {
            params.add(ctx().requireUserId());
            where.append(" and (e.userId = ?").append(params.size()).append(" or r.createdById = ?").append(params.size()).append(")");
        }
        if (status != null) {
            params.add(status);
            where.append(" and r.status = ?").append(params.size());
        }
        if (employeeId != null) {
            params.add(employeeId);
            where.append(" and r.employeeId = ?").append(params.size());
        }
        if (!Guard.blank(search)) {
            params.add("%" + search.trim() + "%");
            where.append(" and (r.number ilike ?").append(params.size()).append(" or concat(e.firstName, ' ', e.lastName) ilike ?").append(params.size()).append(")");
        }
        long total = db.count("select count(r)" + where, params.toArray());
        List<LeaveRequest> rows = db.query("select r" + where + " order by r.createdAt desc", LeaveRequest.class, params.toArray())
                .setFirstResult((page - 1) * pageSize).setMaxResults(pageSize).getResultList();
        return Paged.of(toDtos(rows), total, page, pageSize);
    }

    @Transactional(readOnly = true)
    public LeaveRequestDto get(UUID id) {
        return toDtos(List.of(visible(id))).get(0);
    }

    public LeaveRequestDto create(CreateLeaveRequest body) {
        Guard.that(body.leaveTypeId() != null && body.startDate() != null && body.endDate() != null, "İzin türü ve tarihler zorunludur");
        Employee employee = resolveEmployee(body.employeeId());
        LeaveType type = db.first("select t from LeaveType t where t.id = ?1", LeaveType.class, body.leaveTypeId())
                .orElseThrow(() -> AppException.notFound("İzin türü bulunamadı"));
        Guard.that(type.isActive, "İzin türü aktif değil");
        Guard.that(!body.endDate().isBefore(body.startDate()), "Bitiş tarihi başlangıçtan önce olamaz");
        Guard.that(body.endDate().getYear() == body.startDate().getYear(), "Bir izin talebi iki takvim yılına yayılamaz");
        int days = workingDays(body.startDate(), body.endDate());
        Guard.that(days > 0, "Seçilen aralıkta iş günü yok");

        boolean overlapping = db.exists("select r.id from LeaveRequest r where r.employeeId = ?1 and r.status in ?2 and r.startDate <= ?3 and r.endDate >= ?4",
                employee.id, List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED), body.endDate(), body.startDate());
        Guard.state(!overlapping, "Başka bir izin talebiyle çakışıyor");

        int year = body.startDate().getYear();
        if (type.requiresBalance) {
            LeaveBalanceDto balance = balances(employee.id, year).stream().filter(b -> b.leaveTypeId().equals(type.id)).findFirst()
                    .orElseThrow(() -> AppException.badRequest(year + " için " + type.name + " bakiyesi tanımlı değil"));
            Guard.that(days <= balance.remainingDays(), "Yetersiz bakiye: " + balance.remainingDays() + " gün kaldı, " + days + " gün istendi");
        }

        // Without an active approval flow the request is approved immediately.
        boolean hasWorkflow = db.exists("select d.id from WorkflowDefinition d where d.code = ?1 and d.isActive = true and exists (select s.id from WorkflowStep s where s.definitionId = d.id)",
                WORKFLOW_CODE);
        LeaveRequest request = new LeaveRequest();
        request.number = numbering.next(WORKFLOW_CODE);
        request.employeeId = employee.id;
        request.leaveTypeId = type.id;
        request.startDate = body.startDate();
        request.endDate = body.endDate();
        request.days = days;
        request.reason = Guard.blank(body.reason()) ? null : body.reason().trim();
        request.createdById = ctx().requireUserId();
        request.status = hasWorkflow ? LeaveRequestStatus.PENDING : LeaveRequestStatus.APPROVED;
        request.decidedAt = hasWorkflow ? null : Instant.now();
        db.add(request);
        db.flush();
        if (hasWorkflow) {
            var instance = workflow.start(new WorkflowService.StartRequest(WORKFLOW_CODE, request.id.toString(),
                    employee.firstName + " " + employee.lastName + " — " + type.name + " (" + days + " gün)"));
            request.workflowInstanceId = instance.id();
            db.flush();
        } else {
            adjustUsed(request, days);
        }
        return get(request.id);
    }

    /**
     * Pending requests are withdrawn by their creator (cancelling the approval); approved requests that have not started
     * are cancelled by HR ({@code leave:update}) and give the days back.
     */
    public LeaveRequestDto cancel(UUID id, String comment) {
        LeaveRequest request = visible(id);
        if (request.status == LeaveRequestStatus.PENDING) {
            Guard.state(request.createdById.equals(ctx().userId) || ctx().superAdmin, "Bekleyen talebi yalnızca talep sahibi geri çekebilir");
            if (request.workflowInstanceId != null) workflow.cancel(request.workflowInstanceId, comment);
            decide(request.id, LeaveRequestStatus.CANCELLED);
            return get(id);
        }
        if (request.status == LeaveRequestStatus.APPROVED) {
            if (!ctx().has("leave:update")) throw AppException.forbidden("Onaylanmış izni yalnızca İK iptal edebilir");
            Guard.state(!hasStarted(request), "İzin başlamış; iptal edilemez");
            request.status = LeaveRequestStatus.CANCELLED;
            request.decidedAt = Instant.now();
            db.flush();
            adjustUsed(request, -request.days);
            return get(id);
        }
        throw AppException.conflict("Talep zaten sonuçlanmış");
    }

    /** Applies an approval outcome; only pending requests change, so repeated events are harmless. */
    public void decide(UUID requestId, LeaveRequestStatus status) {
        LeaveRequest request = db.first("select r from LeaveRequest r where r.id = ?1 and r.status = ?2", LeaveRequest.class, requestId, LeaveRequestStatus.PENDING)
                .orElse(null);
        if (request == null) return;
        request.status = status;
        request.decidedAt = Instant.now();
        db.flush();
        if (status == LeaveRequestStatus.APPROVED) adjustUsed(request, request.days);
        UUID owner = db.first("select e.userId from Employee e where e.id = ?1", UUID.class, request.employeeId).orElse(null);
        if (owner != null && !owner.equals(request.createdById) && status != LeaveRequestStatus.CANCELLED) {
            notifications.notify(new NotifyRequest(List.of(owner), request.companyId, "leave.decided",
                    status == LeaveRequestStatus.APPROVED ? "İzniniz onaylandı" : "İzniniz reddedildi",
                    request.number + " (" + DMY.format(request.startDate) + " - " + DMY.format(request.endDate) + ")", "/hr/my-leave"));
        }
    }

    // ---------- balances ----------

    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> balances(UUID employeeId, int year) {
        List<Object[]> rows = employeeId == null
                ? db.list("select b, e, t from LeaveBalance b join Employee e on e.id = b.employeeId join LeaveType t on t.id = b.leaveTypeId where b.year = ?1 order by e.firstName, e.lastName, t.name",
                        Object[].class, year)
                : db.list("select b, e, t from LeaveBalance b join Employee e on e.id = b.employeeId join LeaveType t on t.id = b.leaveTypeId where b.year = ?1 and b.employeeId = ?2 order by t.name",
                        Object[].class, year, employeeId);
        String pendingQuery = "select r.employeeId, r.leaveTypeId, sum(r.days) from LeaveRequest r where r.status = ?1 and year(r.startDate) = ?2"
                + (employeeId == null ? "" : " and r.employeeId = ?3") + " group by r.employeeId, r.leaveTypeId";
        List<Object[]> pendingRows = employeeId == null
                ? db.list(pendingQuery, Object[].class, LeaveRequestStatus.PENDING, year)
                : db.list(pendingQuery, Object[].class, LeaveRequestStatus.PENDING, year, employeeId);
        Map<String, Integer> pending = new HashMap<>();
        for (Object[] p : pendingRows) pending.put(p[0] + "/" + p[1], ((Number) p[2]).intValue());
        return rows.stream().map(row -> {
            LeaveBalance b = (LeaveBalance) row[0];
            Employee e = (Employee) row[1];
            LeaveType t = (LeaveType) row[2];
            int p = pending.getOrDefault(b.employeeId + "/" + b.leaveTypeId, 0);
            return new LeaveBalanceDto(b.id, b.employeeId, e.firstName + " " + e.lastName, b.leaveTypeId, t.name, t.color, b.year,
                    b.entitledDays, b.usedDays, p, b.entitledDays - b.usedDays - p);
        }).toList();
    }

    /** Balances of the caller, or anyone's with {@code leave:read}. */
    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> visibleBalances(UUID employeeId, int year) {
        if (ctx().has("leave:read")) return balances(employeeId, year);
        Employee own = ownEmployee();
        if (employeeId != null && !employeeId.equals(own.id)) throw AppException.forbidden("Missing permission: leave:read");
        return balances(own.id, year);
    }

    /** Creates the missing balances of every active employee for balance-tracked leave types. */
    public int accrue(int year) {
        Guard.that(year >= 2000 && year <= 2100, "Geçersiz yıl");
        List<Employee> employees = db.list("select e from Employee e where e.status = ?1", Employee.class, EmployeeStatus.ACTIVE);
        List<LeaveType> types = db.list("select t from LeaveType t where t.isActive = true and t.requiresBalance = true", LeaveType.class);
        Set<String> existing = new HashSet<>();
        for (Object[] x : db.list("select b.employeeId, b.leaveTypeId from LeaveBalance b where b.year = ?1", Object[].class, year)) existing.add(x[0] + "/" + x[1]);
        int created = 0;
        for (Employee e : employees) {
            for (LeaveType t : types) {
                if (existing.contains(e.id + "/" + t.id)) continue;
                LeaveBalance b = new LeaveBalance();
                b.employeeId = e.id;
                b.leaveTypeId = t.id;
                b.year = year;
                b.entitledDays = "YILLIK".equals(t.code) ? Math.max(t.defaultDays, WorkingDays.annualLeaveDays(e.hireDate, year)) : t.defaultDays;
                db.add(b);
                created++;
            }
        }
        db.flush();
        return created;
    }

    public LeaveBalanceDto updateEntitlement(UUID id, int entitledDays) {
        Guard.that(entitledDays >= 0 && entitledDays <= 365, "Hak edilen gün 0-365 arasında olmalı");
        LeaveBalance balance = db.first("select b from LeaveBalance b where b.id = ?1", LeaveBalance.class, id)
                .orElseThrow(() -> AppException.notFound("İzin bakiyesi bulunamadı"));
        balance.entitledDays = entitledDays;
        db.flush();
        return balances(balance.employeeId, balance.year).stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    // ---------- helpers ----------

    private void adjustUsed(LeaveRequest request, int delta) {
        db.execute("update LeaveBalance b set b.usedDays = b.usedDays + ?1 where b.employeeId = ?2 and b.leaveTypeId = ?3 and b.year = ?4"
                        + " and exists (select t.id from LeaveType t where t.id = b.leaveTypeId and t.requiresBalance = true)",
                delta, request.employeeId, request.leaveTypeId, request.startDate.getYear());
    }

    public Employee ownEmployee() {
        UUID userId = ctx().requireUserId();
        return db.first("select e from Employee e where e.userId = ?1", Employee.class, userId)
                .orElseThrow(() -> AppException.notFound("Hesabınıza bağlı bir personel kaydı yok"));
    }

    private Employee resolveEmployee(UUID employeeId) {
        UUID userId = ctx().requireUserId();
        Employee own = db.first("select e from Employee e where e.userId = ?1", Employee.class, userId).orElse(null);
        Employee employee;
        if (employeeId != null && (own == null || !employeeId.equals(own.id))) {
            if (!ctx().has("leave:create")) throw AppException.forbidden("Yalnızca kendiniz için izin talep edebilirsiniz");
            employee = db.first("select e from Employee e where e.id = ?1", Employee.class, employeeId)
                    .orElseThrow(() -> AppException.notFound("Personel bulunamadı"));
        } else {
            if (own == null) throw AppException.notFound("Hesabınıza bağlı bir personel kaydı yok");
            employee = own;
        }
        Guard.that(employee.status == EmployeeStatus.ACTIVE, "Personel aktif değil");
        return employee;
    }

    private LeaveRequest visible(UUID id) {
        LeaveRequest row = db.first("select r from LeaveRequest r where r.id = ?1", LeaveRequest.class, id).orElse(null);
        boolean ok = row != null && (ctx().has("leave:read") || Objects.equals(row.createdById, ctx().userId)
                || db.exists("select e.id from Employee e where e.id = ?1 and e.userId = ?2", row.employeeId, ctx().userId));
        if (!ok) throw AppException.notFound("İzin talebi bulunamadı");
        return row;
    }

    private static boolean hasStarted(LeaveRequest r) {
        return !r.startDate.isAfter(LocalDate.now(ZoneOffset.UTC));
    }

    private boolean canCancel(LeaveRequest r) {
        return switch (r.status) {
            case PENDING -> Objects.equals(r.createdById, ctx().userId) || ctx().superAdmin;
            case APPROVED -> ctx().has("leave:update") && !hasStarted(r);
            default -> false;
        };
    }

    private List<LeaveRequestDto> toDtos(List<LeaveRequest> rows) {
        if (rows.isEmpty()) return List.of();
        Set<UUID> employeeIds = new HashSet<>(), typeIds = new HashSet<>();
        rows.forEach(r -> {
            employeeIds.add(r.employeeId);
            typeIds.add(r.leaveTypeId);
        });
        Map<UUID, Employee> employees = new HashMap<>();
        db.list("select e from Employee e where e.id in ?1", Employee.class, employeeIds).forEach(e -> employees.put(e.id, e));
        Map<UUID, LeaveType> types = new HashMap<>();
        db.list("select t from LeaveType t where t.id in ?1", LeaveType.class, typeIds).forEach(t -> types.put(t.id, t));
        return rows.stream().map(r -> {
            Employee e = employees.get(r.employeeId);
            LeaveType t = types.get(r.leaveTypeId);
            return new LeaveRequestDto(r.id, r.number, r.employeeId, e == null ? "" : e.firstName + " " + e.lastName, r.leaveTypeId,
                    t == null ? "" : t.name, t == null ? "#6366f1" : t.color, r.startDate, r.endDate, r.days, r.reason, r.status,
                    r.workflowInstanceId, r.decidedAt, r.createdAt, canCancel(r));
        }).toList();
    }

    /** Keeps leave status and balances in sync with the approval flow. */
    @Component
    public static class WorkflowListener implements WorkflowService.Listener {
        // Lazy: LeaveService depends on WorkflowService, which depends on the listeners.
        private final ObjectProvider<LeaveService> leave;

        public WorkflowListener(ObjectProvider<LeaveService> leave) {
            this.leave = leave;
        }

        @Override
        public String entityType() {
            return ENTITY_TYPE;
        }

        @Override
        public void onFinished(WorkflowService.Finished e) {
            UUID id;
            try {
                id = UUID.fromString(e.entityId());
            } catch (IllegalArgumentException ex) {
                return;
            }
            LeaveRequestStatus status = e.status() == WorkflowStatus.APPROVED ? LeaveRequestStatus.APPROVED
                    : e.status() == WorkflowStatus.REJECTED ? LeaveRequestStatus.REJECTED : LeaveRequestStatus.CANCELLED;
            leave.getObject().decide(id, status);
        }
    }
}
