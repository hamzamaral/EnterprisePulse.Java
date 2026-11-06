package dev.enterprisepulse.workflow;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.common.Paged;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.ApproverType;
import dev.enterprisepulse.data.generated.Role;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.data.generated.WorkflowAction;
import dev.enterprisepulse.data.generated.WorkflowActionType;
import dev.enterprisepulse.data.generated.WorkflowDefinition;
import dev.enterprisepulse.data.generated.WorkflowInstance;
import dev.enterprisepulse.data.generated.WorkflowStatus;
import dev.enterprisepulse.data.generated.WorkflowStep;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NotificationService.NotifyRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generic multi-step approval engine shared by every module. */
@Service
@Transactional
public class WorkflowService {
    public record Finished(UUID instanceId, UUID companyId, String definitionCode, String entityType, String entityId,
                           UUID actorId, WorkflowStatus status, String comment) {}

    /** Business modules react to approvals of their documents (leave, expense, purchase request...). */
    public interface Listener {
        String entityType();

        void onFinished(Finished e);
    }

    public record StartRequest(String definitionCode, String entityId, String title) {}

    public record ActionDto(UUID id, int stepOrder, WorkflowActionType type, UUID userId, String userName, String comment, Instant createdAt) {}

    public record StepDto(UUID id, int order, String name, ApproverType approverType, UUID approverRoleId, UUID approverUserId, String approverName) {}

    public record InstanceDto(UUID id, UUID definitionId, String definitionName, String entityType, String entityId, String title,
                              WorkflowStatus status, int currentStepOrder, String currentStepName, int totalSteps, UUID startedById,
                              String startedByName, Instant startedAt, Instant completedAt, boolean canAct, List<StepDto> steps,
                              List<ActionDto> actions) {}

    private record Loaded(WorkflowInstance instance, WorkflowDefinition definition, List<WorkflowStep> steps) {
        WorkflowStep current() {
            return steps.stream().filter(s -> s.order == instance.currentStepOrder).findFirst()
                    .orElseThrow(() -> AppException.conflict("Akış tanımında mevcut adım yok"));
        }
    }

    private final Db db;
    private final NotificationService notifications;
    private final List<Listener> listeners;

    public WorkflowService(Db db, NotificationService notifications, List<Listener> listeners) {
        this.db = db;
        this.notifications = notifications;
        this.listeners = listeners;
    }

    private static CurrentUser ctx() {
        return CurrentUser.get();
    }

    public InstanceDto start(StartRequest request) {
        UUID userId = ctx().requireUserId();
        Guard.that(request.definitionCode() != null && request.entityId() != null, "Akış ve kayıt zorunludur");
        WorkflowDefinition definition = db.first("select d from WorkflowDefinition d where d.code = ?1 and d.isActive = true",
                        WorkflowDefinition.class, request.definitionCode())
                .orElseThrow(() -> AppException.notFound("Onay akışı tanımlı değil: " + request.definitionCode()));
        List<WorkflowStep> steps = steps(definition.id);
        Guard.that(!steps.isEmpty(), "Onay akışında adım yok");
        Guard.state(!db.exists("select i.id from WorkflowInstance i where i.entityType = ?1 and i.entityId = ?2 and i.status = ?3",
                definition.entityType, request.entityId(), WorkflowStatus.PENDING), "Bu kayıt için devam eden bir onay süreci var");

        WorkflowInstance instance = new WorkflowInstance();
        instance.definitionId = definition.id;
        instance.entityType = definition.entityType;
        instance.entityId = request.entityId();
        instance.title = request.title() == null ? definition.name : request.title();
        instance.currentStepOrder = 1;
        instance.startedById = userId;
        db.add(instance);
        WorkflowAction action = new WorkflowAction();
        action.instanceId = instance.id;
        action.stepOrder = 0;
        action.type = WorkflowActionType.START;
        action.userId = userId;
        db.add(action);
        db.flush();
        notifyApprovers(instance, steps.get(0));
        return get(instance.id);
    }

    public InstanceDto approve(UUID id, String comment) {
        Loaded l = pending(id);
        WorkflowStep step = l.current();
        assertCanAct(step);
        boolean last = step.order == l.steps().size();
        transition(l.instance(), WorkflowActionType.APPROVE, comment, i -> {
            i.currentStepOrder = last ? step.order : step.order + 1;
            i.status = last ? WorkflowStatus.APPROVED : WorkflowStatus.PENDING;
            i.completedAt = last ? Instant.now() : null;
        });
        if (last) {
            finish(l, comment);
            notifications.notify(new NotifyRequest(List.of(l.instance().startedById), l.instance().companyId, "workflow.approved",
                    "Onay tamamlandı", "\"" + l.instance().title + "\" onaylandı.", "/workflow/instances/" + id));
        } else {
            notifyApprovers(l.instance(), l.steps().stream().filter(s -> s.order == step.order + 1).findFirst().orElseThrow());
        }
        return get(id);
    }

    public InstanceDto reject(UUID id, String comment) {
        Guard.that(!Guard.blank(comment), "Ret gerekçesi zorunludur");
        Loaded l = pending(id);
        assertCanAct(l.current());
        transition(l.instance(), WorkflowActionType.REJECT, comment, i -> {
            i.status = WorkflowStatus.REJECTED;
            i.completedAt = Instant.now();
        });
        finish(l, comment);
        notifications.notify(new NotifyRequest(List.of(l.instance().startedById), l.instance().companyId, "workflow.rejected",
                "Onay reddedildi", "\"" + l.instance().title + "\" reddedildi: " + comment, "/workflow/instances/" + id));
        return get(id);
    }

    public InstanceDto cancel(UUID id, String comment) {
        Loaded l = pending(id);
        Guard.state(l.instance().startedById.equals(ctx().userId) || ctx().superAdmin, "Yalnızca talep sahibi iptal edebilir");
        transition(l.instance(), WorkflowActionType.CANCEL, comment, i -> {
            i.status = WorkflowStatus.CANCELLED;
            i.completedAt = Instant.now();
        });
        finish(l, comment);
        return get(id);
    }

    /** Cancels the pending approval of a document (the module withdrew or deleted it). */
    public void cancelForEntity(String entityType, String entityId) {
        db.first("select i from WorkflowInstance i where i.entityType = ?1 and i.entityId = ?2 and i.status = ?3",
                WorkflowInstance.class, entityType, entityId, WorkflowStatus.PENDING).ifPresent(instance -> {
            instance.status = WorkflowStatus.CANCELLED;
            instance.completedAt = Instant.now();
            WorkflowAction action = new WorkflowAction();
            action.instanceId = instance.id;
            action.stepOrder = instance.currentStepOrder;
            action.type = WorkflowActionType.CANCEL;
            action.userId = ctx().requireUserId();
            db.add(action);
            db.flush();
        });
    }

    @Transactional(readOnly = true)
    public Paged<InstanceDto> inbox(int page, int pageSize) {
        List<Loaded> mine = mineToApprove();
        int size = Math.max(1, Math.min(pageSize, 200));
        int p = Math.max(1, page);
        List<Loaded> slice = mine.stream().skip((long) (p - 1) * size).limit(size).toList();
        return Paged.of(toDtos(slice), mine.size(), p, size);
    }

    @Transactional(readOnly = true)
    public int inboxCount() {
        return mineToApprove().size();
    }

    private List<Loaded> mineToApprove() {
        Set<UUID> roleIds = roleIds();
        return db.list("select i from WorkflowInstance i where i.status = ?1 order by i.startedAt", WorkflowInstance.class, WorkflowStatus.PENDING)
                .stream().map(this::load).filter(l -> isApprover(l.current(), roleIds)).toList();
    }

    @Transactional(readOnly = true)
    public Paged<InstanceDto> list(WorkflowStatus status, String entityType, boolean mine, String search, int page, int pageSize) {
        if (!mine) ctx().require("workflow:read");
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (status != null) {
            params.add(status);
            where.append(" and i.status = ?").append(params.size());
        }
        if (entityType != null) {
            params.add(entityType);
            where.append(" and i.entityType = ?").append(params.size());
        }
        if (mine) {
            params.add(ctx().requireUserId());
            where.append(" and i.startedById = ?").append(params.size());
        }
        if (!Guard.blank(search)) {
            params.add("%" + search.trim() + "%");
            where.append(" and i.title ilike ?").append(params.size());
        }
        int size = Math.max(1, Math.min(pageSize, 200));
        int p = Math.max(1, page);
        long total = db.count("select count(i) from WorkflowInstance i" + where, params.toArray());
        List<WorkflowInstance> rows = db.query("select i from WorkflowInstance i" + where + " order by i.startedAt desc", WorkflowInstance.class, params.toArray())
                .setFirstResult((p - 1) * size).setMaxResults(size).getResultList();
        return Paged.of(toDtos(rows.stream().map(this::load).toList()), total, p, size);
    }

    @Transactional(readOnly = true)
    public InstanceDto get(UUID id) {
        WorkflowInstance instance = db.first("select i from WorkflowInstance i where i.id = ?1", WorkflowInstance.class, id)
                .orElseThrow(() -> AppException.notFound("Onay süreci bulunamadı"));
        Loaded l = load(instance);
        List<WorkflowAction> actions = db.list("select a from WorkflowAction a where a.instanceId = ?1 order by a.createdAt", WorkflowAction.class, id);
        Set<UUID> roleIds = roleIds();
        UUID me = ctx().userId;
        boolean approver = instance.status == WorkflowStatus.PENDING && isApprover(l.current(), roleIds);
        boolean canSee = ctx().has("workflow:read") || instance.startedById.equals(me) || actions.stream().anyMatch(a -> a.userId.equals(me)) || approver;
        if (!canSee) throw AppException.forbidden();

        Set<UUID> userIds = new HashSet<>();
        actions.forEach(a -> userIds.add(a.userId));
        l.steps().stream().filter(s -> s.approverUserId != null).forEach(s -> userIds.add(s.approverUserId));
        Map<UUID, String> names = names(userIds);
        Set<UUID> stepRoles = l.steps().stream().map(s -> s.approverRoleId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> roleNames = stepRoles.isEmpty() ? Map.of()
                : db.list("select r from Role r where r.id in ?1", Role.class, stepRoles).stream().collect(Collectors.toMap(r -> r.id, r -> r.name));

        InstanceDto dto = toDtos(List.of(l)).get(0);
        List<StepDto> steps = l.steps().stream().map(s -> new StepDto(s.id, s.order, s.name, s.approverType, s.approverRoleId, s.approverUserId,
                s.approverType == ApproverType.ROLE ? roleNames.getOrDefault(s.approverRoleId, "") : names.getOrDefault(s.approverUserId, ""))).toList();
        List<ActionDto> actionDtos = actions.stream().map(a -> new ActionDto(a.id, a.stepOrder, a.type, a.userId, names.getOrDefault(a.userId, ""), a.comment, a.createdAt)).toList();
        return new InstanceDto(dto.id(), dto.definitionId(), dto.definitionName(), dto.entityType(), dto.entityId(), dto.title(), dto.status(),
                dto.currentStepOrder(), dto.currentStepName(), dto.totalSteps(), dto.startedById(), dto.startedByName(), dto.startedAt(),
                dto.completedAt(), approver, steps, actionDtos);
    }

    /** Latest instance of a document, used by module detail pages. */
    @Transactional(readOnly = true)
    public InstanceDto forEntity(String entityType, String entityId) {
        return db.first("select i.id from WorkflowInstance i where i.entityType = ?1 and i.entityId = ?2 order by i.startedAt desc",
                UUID.class, entityType, entityId).map(this::get).orElse(null);
    }

    private Loaded pending(UUID id) {
        WorkflowInstance instance = db.first("select i from WorkflowInstance i where i.id = ?1", WorkflowInstance.class, id)
                .orElseThrow(() -> AppException.notFound("Onay süreci bulunamadı"));
        Guard.state(instance.status == WorkflowStatus.PENDING, "Onay süreci zaten sonuçlanmış");
        return load(instance);
    }

    private Loaded load(WorkflowInstance instance) {
        WorkflowDefinition definition = db.get(WorkflowDefinition.class, instance.definitionId);
        return new Loaded(instance, definition, steps(definition.id));
    }

    private List<WorkflowStep> steps(UUID definitionId) {
        return db.list("select s from WorkflowStep s where s.definitionId = ?1", WorkflowStep.class, definitionId)
                .stream().sorted(Comparator.comparingInt(s -> s.order)).toList();
    }

    /** Optimistic concurrency: two approvers clicking at once cannot both move the instance. */
    private void transition(WorkflowInstance instance, WorkflowActionType type, String comment, Consumer<WorkflowInstance> change) {
        int expected = instance.currentStepOrder;
        db.flush();
        int moved = db.nativeExecute("update workflow_instances set current_step_order = current_step_order where id = ?1 and status = 'PENDING' and current_step_order = ?2",
                instance.id, expected);
        Guard.state(moved == 1, "Onay süreci başka biri tarafından güncellendi, sayfayı yenileyin");
        db.em().refresh(instance);
        Guard.state(instance.status == WorkflowStatus.PENDING && instance.currentStepOrder == expected, "Onay süreci başka biri tarafından güncellendi, sayfayı yenileyin");
        change.accept(instance);
        WorkflowAction action = new WorkflowAction();
        action.instanceId = instance.id;
        action.stepOrder = expected;
        action.type = type;
        action.userId = ctx().requireUserId();
        action.comment = comment;
        db.add(action);
        db.flush();
    }

    private void finish(Loaded l, String comment) {
        WorkflowInstance i = l.instance();
        Finished e = new Finished(i.id, i.companyId, l.definition().code, i.entityType, i.entityId, ctx().requireUserId(), i.status, comment);
        for (Listener listener : listeners) if (listener.entityType().equals(i.entityType)) listener.onFinished(e);
    }

    private boolean isApprover(WorkflowStep step, Set<UUID> roleIds) {
        if (ctx().superAdmin) return true;
        return step.approverType == ApproverType.USER
                ? step.approverUserId != null && step.approverUserId.equals(ctx().userId)
                : step.approverRoleId != null && roleIds.contains(step.approverRoleId);
    }

    private void assertCanAct(WorkflowStep step) {
        if (!isApprover(step, roleIds())) throw AppException.forbidden("Bu adımın onaycısı değilsiniz");
    }

    private Set<UUID> roleIds() {
        UUID userId = ctx().userId;
        if (userId == null) return Set.of();
        return new HashSet<>(db.list("select ur.roleId from UserRole ur where ur.userId = ?1", UUID.class, userId));
    }

    private void notifyApprovers(WorkflowInstance instance, WorkflowStep step) {
        List<UUID> userIds = step.approverType == ApproverType.USER
                ? List.of(step.approverUserId)
                : db.list("select ur.userId from UserRole ur join User u on u.id = ur.userId where ur.roleId = ?1 and u.isActive = true", UUID.class, step.approverRoleId);
        notifications.notify(new NotifyRequest(userIds, instance.companyId, "workflow.approval_requested",
                "Onayınızı bekleyen talep", "\"" + instance.title + "\" — " + step.name, "/workflow/instances/" + instance.id, true));
    }

    private Map<UUID, String> names(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, String> out = new HashMap<>();
        for (User u : db.list("select u from User u where u.id in ?1", User.class, ids)) out.put(u.id, u.firstName + " " + u.lastName);
        return out;
    }

    private List<InstanceDto> toDtos(List<Loaded> rows) {
        Map<UUID, String> names = names(rows.stream().map(r -> r.instance().startedById).collect(Collectors.toSet()));
        return rows.stream().map(l -> {
            WorkflowInstance r = l.instance();
            String stepName = l.steps().stream().filter(s -> s.order == r.currentStepOrder).map(s -> s.name).findFirst().orElse(null);
            return new InstanceDto(r.id, r.definitionId, l.definition().name, r.entityType, r.entityId, r.title, r.status, r.currentStepOrder,
                    stepName, l.steps().size(), r.startedById, names.getOrDefault(r.startedById, ""), r.startedAt, r.completedAt, false, null, null);
        }).toList();
    }
}
