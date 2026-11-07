package dev.enterprisepulse.projects;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.PartnerType;
import dev.enterprisepulse.data.generated.Project;
import dev.enterprisepulse.data.generated.ProjectMember;
import dev.enterprisepulse.data.generated.ProjectMemberRole;
import dev.enterprisepulse.data.generated.ProjectStatus;
import dev.enterprisepulse.data.generated.ProjectTask;
import dev.enterprisepulse.data.generated.ProjectTaskPriority;
import dev.enterprisepulse.data.generated.ProjectTaskStatus;
import dev.enterprisepulse.data.generated.TimeEntry;
import dev.enterprisepulse.platform.NumberingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectService {
    public record Save(String name, String description, UUID partnerId, UUID managerId, LocalDate startDate, LocalDate dueDate, BigDecimal budgetAmount, BigDecimal managerHourlyCost) {}
    public record MemberInput(UUID employeeId, ProjectMemberRole role, BigDecimal hourlyCost) {}
    public record TaskInput(String title, String description, ProjectTaskPriority priority, UUID assigneeId, LocalDate dueDate, BigDecimal estimatedHours) {}
    public record Move(ProjectTaskStatus status) {}
    public record TimeInput(UUID taskId, UUID employeeId, LocalDate workDate, BigDecimal hours, String description) {}
    public record StatusChange(ProjectStatus status) {}

    public record MemberDto(UUID id, UUID employeeId, String employeeName, ProjectMemberRole role, BigDecimal hourlyCost) {}
    public record TaskDto(UUID id, int taskNo, String title, String description, ProjectTaskStatus status, ProjectTaskPriority priority, UUID assigneeId, String assigneeName, LocalDate dueDate, BigDecimal estimatedHours, int sortOrder) {}
    public record TimeDto(UUID id, UUID employeeId, String employeeName, UUID taskId, Integer taskNo, LocalDate workDate, BigDecimal hours, BigDecimal costAmount, String description) {}
    public record TaskList(List<TaskDto> items) {}
    public record TimeList(List<TimeDto> items) {}
    public record Detail(UUID id, String number, String name, String description, UUID partnerId, String partnerTitle, UUID managerId, String managerName,
                          ProjectStatus status, LocalDate startDate, LocalDate dueDate, BigDecimal budgetAmount, BigDecimal expenseCost, BigDecimal labourCost,
                          BigDecimal totalCost, BigDecimal remaining, List<MemberDto> members, List<TaskDto> tasks, List<TimeDto> time) {}

    private final Db db;
    private final NumberingService numbering;

    public ProjectService(Db db, NumberingService numbering) {
        this.db = db;
        this.numbering = numbering;
    }

    public Detail create(Save dto) {
        Guard.that(CurrentUser.get().has("projects:create"), "Proje oluşturma yetkiniz yok");
        Guard.that(dto.name() != null && !dto.name().isBlank(), "Proje adı gerekli");
        Guard.that(dto.budgetAmount() == null || dto.budgetAmount().signum() >= 0, "Bütçe negatif olamaz");
        Guard.that(dto.managerHourlyCost() == null || dto.managerHourlyCost().signum() >= 0, "Saat maliyeti negatif olamaz");
        Employee manager = Guard.found(db.first("select e from Employee e where e.id = ?1", Employee.class, dto.managerId()), "Proje yöneticisi bulunamadı");
        if (dto.partnerId() != null) {
            BusinessPartner partner = Guard.found(db.first("select p from BusinessPartner p where p.id = ?1", BusinessPartner.class, dto.partnerId()), "Cari hesap bulunamadı");
            Guard.that(partner.type == PartnerType.CUSTOMER || partner.type == PartnerType.BOTH, "Proje yalnızca müşteri carisine bağlanır");
        }
        Project project = new Project();
        project.number = numbering.next("PROJECT");
        project.name = dto.name().trim();
        project.description = blank(dto.description());
        project.partnerId = dto.partnerId();
        project.managerId = manager.id;
        project.startDate = dto.startDate();
        project.dueDate = dto.dueDate();
        project.budgetAmount = dto.budgetAmount();
        project.createdById = CurrentUser.get().requireUserId();
        db.add(project);
        db.flush();
        ProjectMember member = new ProjectMember();
        member.projectId = project.id;
        member.employeeId = manager.id;
        member.role = ProjectMemberRole.LEAD;
        member.hourlyCost = dto.managerHourlyCost();
        db.add(member);
        db.flush();
        return get(project.id);
    }

    public Detail changeStatus(UUID id, ProjectStatus status) {
        Guard.that(CurrentUser.get().has("projects:update"), "Proje güncelleme yetkiniz yok");
        Project project = open(id);
        boolean ok = switch (project.status) {
            case PLANNED -> status == ProjectStatus.ACTIVE || status == ProjectStatus.CANCELLED;
            case ACTIVE -> status == ProjectStatus.ON_HOLD || status == ProjectStatus.COMPLETED || status == ProjectStatus.CANCELLED;
            case ON_HOLD -> status == ProjectStatus.ACTIVE || status == ProjectStatus.CANCELLED;
            default -> false;
        };
        Guard.state(ok, "Proje bu duruma geçemez");
        project.status = status;
        project.completedAt = status == ProjectStatus.COMPLETED ? Instant.now() : null;
        return get(id);
    }

    public Detail addMember(UUID id, MemberInput dto) {
        Guard.that(CurrentUser.get().has("projects:update"), "Proje güncelleme yetkiniz yok");
        open(id);
        Guard.that(dto.hourlyCost() == null || dto.hourlyCost().signum() >= 0, "Saat maliyeti negatif olamaz");
        Guard.that(db.exists("select e.id from Employee e where e.id = ?1", dto.employeeId()), "Personel bulunamadı");
        Guard.that(!db.exists("select m.id from ProjectMember m where m.projectId = ?1 and m.employeeId = ?2", id, dto.employeeId()), "Personel projede zaten var");
        ProjectMember member = new ProjectMember();
        member.projectId = id;
        member.employeeId = dto.employeeId();
        member.role = dto.role() == null ? ProjectMemberRole.MEMBER : dto.role();
        member.hourlyCost = dto.hourlyCost();
        db.add(member);
        return get(id);
    }

    public Detail addTask(UUID id, TaskInput dto) {
        ensureCanEdit(id);
        Guard.that(dto.title() != null && !dto.title().isBlank(), "Görev başlığı gerekli");
        Guard.that(dto.estimatedHours() == null || dto.estimatedHours().signum() >= 0, "Tahmini süre negatif olamaz");
        if (dto.assigneeId() != null) requireMember(id, dto.assigneeId());
        ProjectTask task = new ProjectTask();
        task.projectId = id;
        task.taskNo = maxInt("select max(t.taskNo) from ProjectTask t where t.projectId = ?1", id) + 1;
        task.title = dto.title().trim();
        task.description = blank(dto.description());
        task.priority = dto.priority() == null ? ProjectTaskPriority.MEDIUM : dto.priority();
        task.assigneeId = dto.assigneeId();
        task.dueDate = dto.dueDate();
        task.estimatedHours = dto.estimatedHours();
        task.sortOrder = maxInt("select max(t.sortOrder) from ProjectTask t where t.projectId = ?1 and t.status = ?2", id, ProjectTaskStatus.TODO) + 1;
        task.createdById = CurrentUser.get().requireUserId();
        db.add(task);
        return get(id);
    }

    public Detail moveTask(UUID taskId, ProjectTaskStatus status) {
        ProjectTask task = Guard.found(db.first("select t from ProjectTask t where t.id = ?1", ProjectTask.class, taskId), "Görev bulunamadı");
        boolean mine = task.assigneeId != null && db.exists("select e.id from Employee e where e.id = ?1 and e.userId = ?2", task.assigneeId, CurrentUser.get().userId);
        if (!mine) Guard.that(CurrentUser.get().has("projects:update"), "Görevi yalnızca atanan kişi veya proje yetkilisi taşır");
        open(task.projectId);
        task.status = status;
        task.sortOrder = maxInt("select max(t.sortOrder) from ProjectTask t where t.projectId = ?1 and t.status = ?2", task.projectId, status) + 1;
        task.completedAt = status == ProjectTaskStatus.DONE ? Instant.now() : null;
        return get(task.projectId);
    }

    public Detail logTime(UUID id, TimeInput dto) {
        open(id);
        Guard.that(dto.hours() != null && dto.hours().signum() > 0 && dto.hours().compareTo(new BigDecimal("24")) <= 0, "Süre 0 ile 24 saat arasında olmalı");
        Guard.that(dto.workDate() != null && !dto.workDate().isAfter(LocalDate.now().plusDays(1)), "Çalışma tarihi ileri olamaz");
        UUID employeeId = dto.employeeId();
        if (employeeId == null) {
            employeeId = db.first("select e.id from Employee e where e.userId = ?1", UUID.class, CurrentUser.get().userId).orElse(null);
            Guard.that(employeeId != null, "Personel kaydınız yok");
        } else if (!db.exists("select e.id from Employee e where e.id = ?1 and e.userId = ?2", employeeId, CurrentUser.get().userId)) {
            Guard.that(CurrentUser.get().has("projects:update"), "Başkası adına süre girme yetkiniz yok");
        }
        ProjectMember member = Guard.found(db.first("select m from ProjectMember m where m.projectId = ?1 and m.employeeId = ?2", ProjectMember.class, id, employeeId), "Süre giren kişi projenin üyesi değil");
        if (dto.taskId() != null) Guard.that(db.exists("select t.id from ProjectTask t where t.id = ?1 and t.projectId = ?2", dto.taskId(), id), "Görev bu projede değil");
        BigDecimal rate = member.hourlyCost == null ? BigDecimal.ZERO : member.hourlyCost;
        TimeEntry entry = new TimeEntry();
        entry.projectId = id;
        entry.taskId = dto.taskId();
        entry.employeeId = member.employeeId;
        entry.workDate = dto.workDate();
        entry.hours = dto.hours();
        entry.description = blank(dto.description());
        entry.costAmount = dto.hours().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        db.add(entry);
        return get(id);
    }

    public void addExpenseCost(UUID projectId, BigDecimal delta) {
        Project project = Guard.found(db.first("select p from Project p where p.id = ?1", Project.class, projectId), "Proje bulunamadı");
        project.expenseCost = project.expenseCost.add(delta).setScale(2, RoundingMode.HALF_UP);
        Guard.that(project.expenseCost.signum() >= 0, "Proje masraf maliyeti negatife düşemez");
    }

    @Transactional(readOnly = true)
    public TaskList myTasks() {
        UUID employeeId = db.first("select e.id from Employee e where e.userId = ?1", UUID.class, CurrentUser.get().userId).orElse(null);
        if (employeeId == null) return new TaskList(List.of());
        List<ProjectTask> tasks = db.list("select t from ProjectTask t where t.assigneeId = ?1 and t.status <> ?2 order by t.dueDate", ProjectTask.class, employeeId, ProjectTaskStatus.DONE);
        List<TaskDto> open = new ArrayList<>();
        for (ProjectTask task : tasks) {
            Project project = db.find(Project.class, task.projectId);
            if (project == null || project.status == ProjectStatus.CANCELLED || project.status == ProjectStatus.COMPLETED) continue;
            open.add(task(task));
        }
        return new TaskList(open);
    }

    @Transactional(readOnly = true)
    public TimeList time(LocalDate from, LocalDate to) {
        String jpql = "select t from TimeEntry t where 1 = 1";
        List<Object> params = new ArrayList<>();
        if (!CurrentUser.get().has("projects:read")) {
            UUID employeeId = db.first("select e.id from Employee e where e.userId = ?1", UUID.class, CurrentUser.get().userId).orElse(null);
            jpql += " and t.employeeId = ?" + (params.size() + 1);
            params.add(employeeId);
        }
        if (from != null) { jpql += " and t.workDate >= ?" + (params.size() + 1); params.add(from); }
        if (to != null) { jpql += " and t.workDate <= ?" + (params.size() + 1); params.add(to); }
        jpql += " order by t.workDate desc";
        List<TimeEntry> rows = db.query(jpql, TimeEntry.class, params.toArray()).setMaxResults(200).getResultList();
        return new TimeList(rows.stream().map(this::time).toList());
    }

    @Transactional(readOnly = true)
    public Detail get(UUID id) {
        Project project = Guard.found(db.first("select p from Project p where p.id = ?1", Project.class, id), "Proje bulunamadı");
        Employee manager = db.get(Employee.class, project.managerId);
        boolean member = db.exists("select m.id from ProjectMember m, Employee e where m.employeeId = e.id and m.projectId = ?1 and e.userId = ?2", id, CurrentUser.get().userId);
        if (!CurrentUser.get().has("projects:read") && (manager.userId == null || !manager.userId.equals(CurrentUser.get().userId)) && !member) {
            throw dev.enterprisepulse.common.AppException.notFound("Proje bulunamadı");
        }
        BusinessPartner partner = project.partnerId == null ? null : db.find(BusinessPartner.class, project.partnerId);
        List<MemberDto> members = db.list("select m from ProjectMember m where m.projectId = ?1", ProjectMember.class, id).stream().map(m -> {
            Employee e = db.get(Employee.class, m.employeeId);
            return new MemberDto(m.id, m.employeeId, e.firstName + " " + e.lastName, m.role, m.hourlyCost);
        }).toList();
        List<TaskDto> tasks = db.list("select t from ProjectTask t where t.projectId = ?1 order by t.status, t.sortOrder", ProjectTask.class, id).stream().map(this::task).toList();
        List<TimeDto> time = db.list("select t from TimeEntry t where t.projectId = ?1 order by t.workDate desc", TimeEntry.class, id).stream().map(this::time).toList();
        BigDecimal labour = time.stream().map(TimeDto::costAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = labour.add(project.expenseCost);
        BigDecimal remaining = project.budgetAmount == null ? null : project.budgetAmount.subtract(total);
        return new Detail(project.id, project.number, project.name, project.description, project.partnerId, partner == null ? null : partner.title,
                project.managerId, manager.firstName + " " + manager.lastName, project.status, project.startDate, project.dueDate, project.budgetAmount,
                project.expenseCost, labour, total, remaining, members, tasks, time);
    }

    private void ensureCanEdit(UUID id) {
        Project project = open(id);
        Employee manager = db.get(Employee.class, project.managerId);
        boolean lead = manager.userId != null && manager.userId.equals(CurrentUser.get().userId)
                || db.exists("select m.id from ProjectMember m, Employee e where m.employeeId = e.id and m.projectId = ?1 and m.role = ?2 and e.userId = ?3", id, ProjectMemberRole.LEAD, CurrentUser.get().userId);
        if (!lead) Guard.that(CurrentUser.get().has("projects:update"), "Görev ekleme yetkiniz yok");
    }

    private Project open(UUID id) {
        Project project = Guard.found(db.first("select p from Project p where p.id = ?1", Project.class, id), "Proje bulunamadı");
        Guard.state(project.status != ProjectStatus.COMPLETED && project.status != ProjectStatus.CANCELLED, "Kapanmış projede değişiklik yapılmaz");
        return project;
    }

    private void requireMember(UUID projectId, UUID employeeId) {
        Guard.that(db.exists("select m.id from ProjectMember m where m.projectId = ?1 and m.employeeId = ?2", projectId, employeeId), "Görev yalnızca proje üyesine atanır");
    }

    private int maxInt(String jpql, Object... params) {
        List<Integer> rows = db.list(jpql, Integer.class, params);
        return rows.isEmpty() || rows.get(0) == null ? 0 : rows.get(0);
    }

    private TaskDto task(ProjectTask t) {
        Employee assignee = t.assigneeId == null ? null : db.find(Employee.class, t.assigneeId);
        return new TaskDto(t.id, t.taskNo, t.title, t.description, t.status, t.priority, t.assigneeId, assignee == null ? null : assignee.firstName + " " + assignee.lastName, t.dueDate, t.estimatedHours, t.sortOrder);
    }

    private TimeDto time(TimeEntry t) {
        Employee employee = db.get(Employee.class, t.employeeId);
        ProjectTask task = t.taskId == null ? null : db.find(ProjectTask.class, t.taskId);
        return new TimeDto(t.id, t.employeeId, employee.firstName + " " + employee.lastName, t.taskId, task == null ? null : task.taskNo, t.workDate, t.hours, t.costAmount, t.description);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
