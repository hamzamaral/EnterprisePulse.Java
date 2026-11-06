package dev.enterprisepulse.hr;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.AssignmentStatus;
import dev.enterprisepulse.data.generated.Branch;
import dev.enterprisepulse.data.generated.Department;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.data.generated.EmploymentType;
import dev.enterprisepulse.data.generated.Position;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmployeeService {
    public record OrgChartNode(UUID id, String fullName, String positionName, String departmentName, String email, List<OrgChartNode> children) {}

    public record TerminateRequest(LocalDate terminationDate, String reason) {}

    public record MyEmployeeDto(UUID id, String employeeNo, String fullName, String email, String phone, LocalDate hireDate, String departmentName,
                                String positionName, String managerName, String branchName, EmploymentType employmentType) {}

    private final Db db;

    public EmployeeService(Db db) {
        this.db = db;
    }

    private Map<UUID, String> names(Class<?> type) {
        Map<UUID, String> out = new HashMap<>();
        for (Object[] r : db.list("select x.id, x.name from " + type.getSimpleName() + " x", Object[].class)) out.put((UUID) r[0], (String) r[1]);
        return out;
    }

    /** Reporting hierarchy of active employees built from the manager links; visible to every employee. */
    @Transactional(readOnly = true)
    public List<OrgChartNode> orgChart() {
        List<Employee> rows = db.list("select e from Employee e where e.status = ?1 order by e.firstName, e.lastName", Employee.class, EmployeeStatus.ACTIVE);
        Map<UUID, String> positions = names(Position.class), departments = names(Department.class);
        Map<UUID, OrgChartNode> nodes = new LinkedHashMap<>();
        for (Employee e : rows) {
            nodes.put(e.id, new OrgChartNode(e.id, e.firstName + " " + e.lastName, positions.get(e.positionId), departments.get(e.departmentId), e.email, new ArrayList<>()));
        }
        List<OrgChartNode> roots = new ArrayList<>();
        for (Employee e : rows) {
            OrgChartNode parent = e.managerId == null ? null : nodes.get(e.managerId);
            if (parent != null) parent.children().add(nodes.get(e.id));
            else roots.add(nodes.get(e.id));
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public MyEmployeeDto mine() {
        UUID userId = CurrentUser.get().requireUserId();
        Employee e = db.first("select e from Employee e where e.userId = ?1", Employee.class, userId)
                .orElseThrow(() -> AppException.notFound("Hesabınıza bağlı bir personel kaydı yok"));
        String manager = e.managerId == null ? null
                : db.first("select concat(m.firstName, ' ', m.lastName) from Employee m where m.id = ?1", String.class, e.managerId).orElse(null);
        return new MyEmployeeDto(e.id, e.employeeNo, e.firstName + " " + e.lastName, e.email, e.phone, e.hireDate,
                e.departmentId == null ? null : names(Department.class).get(e.departmentId),
                e.positionId == null ? null : names(Position.class).get(e.positionId), manager,
                e.branchId == null ? null : names(Branch.class).get(e.branchId), e.employmentType);
    }

    public Employee terminate(UUID id, TerminateRequest body) {
        Guard.that(body.terminationDate() != null, "Çıkış tarihi zorunludur");
        Employee e = db.first("select e from Employee e where e.id = ?1", Employee.class, id).orElseThrow(() -> AppException.notFound("Personel bulunamadı"));
        Guard.state(e.status != EmployeeStatus.TERMINATED, "Personelin çıkışı zaten yapılmış");
        Guard.that(!body.terminationDate().isBefore(e.hireDate), "Çıkış tarihi işe giriş tarihinden önce olamaz");
        long held = db.count("select count(a) from AssetAssignment a where a.employeeId = ?1 and a.status = ?2", id, AssignmentStatus.ACTIVE);
        Guard.state(held == 0, "Personelin üzerinde " + held + " zimmetli demirbaş var; çıkıştan önce iade alın");
        e.status = EmployeeStatus.TERMINATED;
        e.terminationDate = body.terminationDate();
        e.terminationReason = Guard.blank(body.reason()) ? null : body.reason().trim();
        db.flush();
        return e;
    }
}
