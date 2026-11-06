package dev.enterprisepulse.hr;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Department;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.data.generated.LeaveBalance;
import dev.enterprisepulse.data.generated.LeaveRequestStatus;
import dev.enterprisepulse.data.generated.Position;
import dev.enterprisepulse.data.generated.Shift;
import dev.enterprisepulse.hr.HrRules.ShiftTimes;
import dev.enterprisepulse.hr.HrRules.Timesheet;
import dev.enterprisepulse.resource.ResourceHook;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

public final class HrHooks {
    private HrHooks() {}

    /** Walks parent links from {@code start}; true when {@code self} is reached. */
    private static boolean reaches(Map<UUID, UUID> parents, UUID start, UUID self) {
        int guard = 0;
        for (UUID cursor = start; cursor != null && guard++ < 10_000; cursor = parents.get(cursor)) {
            if (cursor.equals(self)) return true;
        }
        return false;
    }

    private static Map<UUID, UUID> links(Db db, String jpql) {
        Map<UUID, UUID> out = new HashMap<>();
        for (Object[] row : db.list(jpql, Object[].class)) out.put((UUID) row[0], (UUID) row[1]);
        return out;
    }

    @Component
    public static class DepartmentsHook implements ResourceHook {
        private final Db db;

        public DepartmentsHook(Db db) {
            this.db = db;
        }

        @Override
        public String resource() {
            return "departments";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            Department d = (Department) entity;
            if (d.parentId == null) return;
            Guard.that(!d.parentId.equals(d.id), "Bir departman kendisinin üst departmanı olamaz");
            Map<UUID, UUID> parents = links(db, "select x.id, x.parentId from Department x");
            Guard.that(parents.containsKey(d.parentId), "Üst departman bulunamadı");
            parents.put(d.id, d.parentId);
            Guard.that(!reaches(parents, d.parentId, d.id), "Departman hiyerarşisi döngüsel olamaz");
        }

        @Override
        public void beforeDelete(Object entity) {
            Department d = (Department) entity;
            boolean busy = db.exists("select x.id from Department x where x.parentId = ?1", d.id)
                    || db.exists("select e.id from Employee e where e.departmentId = ?1 and e.status = ?2", d.id, EmployeeStatus.ACTIVE);
            Guard.state(!busy, "Departmanın alt departmanları veya aktif personeli var");
            d.isActive = false;
        }
    }

    @Component
    public static class PositionsHook implements ResourceHook {
        private final Db db;

        public PositionsHook(Db db) {
            this.db = db;
        }

        @Override
        public String resource() {
            return "positions";
        }

        @Override
        public void beforeDelete(Object entity) {
            Position p = (Position) entity;
            Guard.state(!db.exists("select e.id from Employee e where e.positionId = ?1 and e.status = ?2", p.id, EmployeeStatus.ACTIVE),
                    "Pozisyon aktif personele atanmış");
            p.isActive = false;
        }
    }

    @Component
    public static class EmployeesHook implements ResourceHook {
        private final Db db;

        public EmployeesHook(Db db) {
            this.db = db;
        }

        @Override
        public String resource() {
            return "employees";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            Employee e = (Employee) entity;
            if (isNew) {
                e.status = EmployeeStatus.ACTIVE;
                e.terminationDate = null;
                e.terminationReason = null;
            } else {
                // Termination goes through the terminate action so the asset and date checks always run.
                e.status = db.original(e, "status");
                e.terminationDate = db.<LocalDate>original(e, "terminationDate");
                e.terminationReason = db.original(e, "terminationReason");
            }
            if (e.userId != null) {
                UUID companyId = CurrentUser.get().requireCompanyId();
                Guard.that(db.exists("select m.id from UserCompany m where m.userId = ?1 and m.companyId = ?2", e.userId, companyId),
                        "Kullanıcı bu şirketin üyesi değil");
                Guard.state(!db.exists("select x.id from Employee x where x.userId = ?1 and x.id <> ?2", e.userId, e.id),
                        "Bu kullanıcı başka bir personel kaydına bağlı");
            }
            if (e.managerId != null) {
                Guard.that(!e.managerId.equals(e.id), "Personel kendisinin yöneticisi olamaz");
                Map<UUID, UUID> managers = links(db, "select x.id, x.managerId from Employee x");
                Guard.that(managers.containsKey(e.managerId), "Yönetici personel bulunamadı");
                managers.put(e.id, e.managerId);
                Guard.that(!reaches(managers, e.managerId, e.id), "Raporlama hiyerarşisi döngüsel olamaz");
            }
        }

        @Override
        public void beforeDelete(Object entity) {
            Employee e = (Employee) entity;
            boolean history = db.exists("select x.id from Employee x where x.managerId = ?1", e.id)
                    || db.exists("select r.id from LeaveRequest r where r.employeeId = ?1", e.id);
            Guard.state(!history, "Personelin geçmiş kayıtları var; silmek yerine işten çıkışını yapın");
            db.execute("update Department d set d.managerId = null where d.managerId = ?1", e.id);
            e.userId = null;
        }
    }

    @Component
    public static class ShiftsHook implements ResourceHook {
        @Override
        public String resource() {
            return "shifts";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            Shift s = (Shift) entity;
            if (s.code != null) s.code = s.code.trim().toUpperCase(Locale.ROOT);
            Guard.that(Timesheet.isClock(s.startTime) && Timesheet.isClock(s.endTime), "Saatler SS:dd biçiminde olmalı");
            Guard.that(!s.startTime.equals(s.endTime), "Vardiya başlangıç ve bitişi farklı olmalı");
            Guard.that(s.breakMinutes >= 0 && s.breakMinutes <= 240, "Mola 0-240 dakika arasında olmalı");
            var w = Timesheet.window(new ShiftTimes(s.code, s.startTime, s.endTime, s.breakMinutes));
            Guard.that(s.breakMinutes < w.end() - w.start(), "Mola vardiyadan kısa olmalı");
        }
    }

    /** Requests are created and closed through the leave endpoints so balances and approvals stay consistent. */
    @Component
    public static class LeaveRequestsHook implements ResourceHook {
        @Override
        public String resource() {
            return "leave-requests";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            throw AppException.conflict("İzin talepleri İzinlerim / İzin Talepleri ekranından oluşturulur ve iptal edilir");
        }

        @Override
        public void beforeDelete(Object entity) {
            throw AppException.conflict("İzin talepleri silinemez; iptal edin");
        }
    }

    @Component
    public static class LeaveBalancesHook implements ResourceHook {
        private final Db db;

        public LeaveBalancesHook(Db db) {
            this.db = db;
        }

        @Override
        public String resource() {
            return "leave-balances";
        }

        @Override
        public void beforeSave(Object entity, boolean isNew) {
            LeaveBalance b = (LeaveBalance) entity;
            if (isNew) b.usedDays = 0;
            else b.usedDays = db.<Integer>original(b, "usedDays");
            Guard.that(b.entitledDays >= 0 && b.entitledDays <= 365, "Hak edilen gün 0-365 arasında olmalı");
        }

        @Override
        public void beforeDelete(Object entity) {
            LeaveBalance b = (LeaveBalance) entity;
            boolean pending = db.exists("select r.id from LeaveRequest r where r.employeeId = ?1 and r.leaveTypeId = ?2 and year(r.startDate) = ?3 and r.status = ?4",
                    b.employeeId, b.leaveTypeId, b.year, LeaveRequestStatus.PENDING);
            Guard.state(b.usedDays == 0 && !pending, "Kullanılmış veya bekleyen izni olan bakiye silinemez");
        }
    }
}
