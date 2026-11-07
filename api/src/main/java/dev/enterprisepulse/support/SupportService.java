package dev.enterprisepulse.support;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Ticket;
import dev.enterprisepulse.data.generated.TicketCategory;
import dev.enterprisepulse.data.generated.TicketComment;
import dev.enterprisepulse.data.generated.TicketEvent;
import dev.enterprisepulse.data.generated.TicketEventType;
import dev.enterprisepulse.data.generated.TicketPriority;
import dev.enterprisepulse.data.generated.TicketSlaPolicy;
import dev.enterprisepulse.data.generated.TicketStatus;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NotificationService.NotifyRequest;
import dev.enterprisepulse.platform.NumberingService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Destek talebi. SLA takvimi saat cinsinden; WAITING çözüm saatini dondurur. */
@Service
@Transactional
public class SupportService {
    public record SaveTicket(String subject, String description, UUID categoryId, TicketPriority priority, UUID requesterId) {}
    public record UpdateTicket(String subject, UUID categoryId, TicketPriority priority, UUID assigneeId) {}
    public record ChangeTicketStatus(TicketStatus status, String comment) {}
    public record TicketCommentBody(String body, boolean isInternal) {}
    public record CloseTicket(Integer rating, String comment) {}
    public record RateTicket(int rating, String comment) {}
    public record ReopenTicket(String comment) {}

    public record TicketCommentDto(UUID id, UUID authorId, String authorName, String body, boolean isInternal, Instant createdAt) {}
    public record TicketDto(
            UUID id, String number, String subject, String description, TicketStatus status, TicketPriority priority,
            UUID categoryId, String categoryName, UUID requesterId, String requesterName, UUID assigneeId, String assigneeName,
            Instant firstResponseDueAt, Instant resolutionDueAt, Instant firstRespondedAt, Instant resolvedAt, Instant closedAt,
            String responseSla, String resolutionSla, Integer satisfactionRating, boolean canManage, boolean canComment, boolean canClose,
            boolean canReopen, boolean canRate, List<TicketStatus> nextStatuses, List<TicketCommentDto> comments) {}
    public record TicketList(List<TicketDto> items) {}

    private record Sla(int first, int resolve) {}

    private static final Map<TicketPriority, Sla> DEFAULT_SLA = new EnumMap<>(Map.of(
            TicketPriority.URGENT, new Sla(1, 4),
            TicketPriority.HIGH, new Sla(4, 24),
            TicketPriority.MEDIUM, new Sla(8, 72),
            TicketPriority.LOW, new Sla(24, 120)));

    private static final Map<TicketStatus, List<TicketStatus>> TRANSITIONS = new EnumMap<>(Map.of(
            TicketStatus.OPEN, List.of(TicketStatus.IN_PROGRESS, TicketStatus.WAITING, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.IN_PROGRESS, List.of(TicketStatus.OPEN, TicketStatus.WAITING, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.WAITING, List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.RESOLVED, List.of(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED),
            TicketStatus.CLOSED, List.of()));

    private static final Set<TicketStatus> ACTIVE = Set.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING);

    private final Db db;
    private final NumberingService numbering;
    private final NotificationService notifications;

    public SupportService(Db db, NumberingService numbering, NotificationService notifications) {
        this.db = db;
        this.numbering = numbering;
        this.notifications = notifications;
    }

    private static CurrentUser ctx() { return CurrentUser.get(); }

    private static boolean isAgent() { return ctx().has("support:update"); }

    private static boolean isStaff() { return isAgent() || ctx().has("support:read"); }

    public void ensureDefaults() {
        for (Map.Entry<TicketPriority, Sla> entry : DEFAULT_SLA.entrySet()) {
            if (db.exists("select p.id from TicketSlaPolicy p where p.priority = ?1", entry.getKey())) continue;
            TicketSlaPolicy policy = new TicketSlaPolicy();
            policy.priority = entry.getKey();
            policy.firstResponseHours = entry.getValue().first();
            policy.resolutionHours = entry.getValue().resolve();
            db.add(policy);
        }
        db.flush();
    }

    public TicketDto create(SaveTicket dto) {
        Guard.that(!Guard.blank(dto.subject()) && !Guard.blank(dto.description()), "Konu ve açıklama gerekli");
        UUID requesterId = dto.requesterId() != null ? dto.requesterId() : ctx().requireUserId();
        if (!Objects.equals(requesterId, ctx().userId)) Guard.that(ctx().has("support:create"), "Başkası adına talep açma yetkiniz yok");
        TicketCategory category = null;
        if (dto.categoryId() != null) {
            category = db.first("select c from TicketCategory c where c.id = ?1 and c.isActive = true", TicketCategory.class, dto.categoryId())
                    .orElseThrow(() -> AppException.badRequest("Kategori bulunamadı"));
        }
        TicketPriority priority = dto.priority() == null ? TicketPriority.MEDIUM : dto.priority();
        Instant now = Instant.now();
        Sla targets = targets(priority);
        Ticket ticket = new Ticket();
        ticket.number = numbering.next("TICKET");
        ticket.subject = dto.subject().trim();
        ticket.description = dto.description().trim();
        ticket.categoryId = category == null ? null : category.id;
        ticket.priority = priority;
        ticket.requesterId = requesterId;
        ticket.assigneeId = category == null ? null : category.defaultAssigneeId;
        ticket.firstResponseDueAt = now.plus(targets.first(), ChronoUnit.HOURS);
        ticket.resolutionDueAt = now.plus(targets.resolve(), ChronoUnit.HOURS);
        ticket.createdAt = now;
        db.add(ticket);
        event(ticket.id, ctx().requireUserId(), TicketEventType.CREATED, null, null);
        if (category != null && category.defaultAssigneeId != null)
            event(ticket.id, null, TicketEventType.ASSIGNED, null, userName(category.defaultAssigneeId));
        db.flush();
        List<UUID> recipients = ticket.assigneeId != null ? List.of(ticket.assigneeId)
                : notifications.usersWithPermission(ctx().requireCompanyId(), "support:update");
        notifications.notify(new NotifyRequest(recipients, ticket.companyId, "support.ticket_created",
                "Yeni destek talebi: " + ticket.number, ticket.subject, "/support/tickets/" + ticket.id));
        return get(ticket.id);
    }

    public TicketDto update(UUID id, UpdateTicket dto) {
        Guard.that(isAgent(), "Talep güncelleme yetkiniz yok");
        Ticket ticket = open(id);
        if (dto.subject() != null) ticket.subject = dto.subject().trim();
        if (dto.categoryId() != null && !dto.categoryId().equals(ticket.categoryId)) {
            Guard.that(db.exists("select c.id from TicketCategory c where c.id = ?1 and c.isActive = true", dto.categoryId()), "Kategori bulunamadı");
            event(id, ctx().requireUserId(), TicketEventType.CATEGORY_CHANGED, ticket.categoryId == null ? null : ticket.categoryId.toString(), dto.categoryId().toString());
            ticket.categoryId = dto.categoryId();
        }
        if (dto.priority() != null && dto.priority() != ticket.priority) {
            Sla targets = targets(dto.priority());
            ticket.firstResponseDueAt = ticket.createdAt.plus(targets.first(), ChronoUnit.HOURS);
            ticket.resolutionDueAt = ticket.createdAt.plus(targets.resolve(), ChronoUnit.HOURS).plus(ticket.pausedMinutes, ChronoUnit.MINUTES);
            event(id, ctx().requireUserId(), TicketEventType.PRIORITY_CHANGED, ticket.priority.toString(), dto.priority().toString());
            ticket.priority = dto.priority();
        }
        if (dto.assigneeId() != null && !dto.assigneeId().equals(ticket.assigneeId)) {
            event(id, ctx().requireUserId(), TicketEventType.ASSIGNED, userName(ticket.assigneeId), userName(dto.assigneeId()));
            ticket.assigneeId = dto.assigneeId();
            if (!dto.assigneeId().equals(ctx().userId))
                notifications.notify(new NotifyRequest(List.of(dto.assigneeId()), ticket.companyId, "support.ticket_assigned",
                        "Size destek talebi atandı: " + ticket.number, ticket.subject, "/support/tickets/" + ticket.id));
        }
        db.flush();
        return get(id);
    }

    public TicketDto changeStatus(UUID id, ChangeTicketStatus dto) {
        Guard.that(isAgent(), "Durum değiştirme yetkiniz yok");
        Ticket ticket = open(id);
        Guard.that(dto.status() != null, "Durum gerekli");
        Guard.state(TRANSITIONS.get(ticket.status).contains(dto.status()), ticket.status + " talebi " + dto.status() + " olamaz");
        Instant now = Instant.now();
        applyStatus(ticket, dto.status(), now, true);
        event(id, ctx().requireUserId(), TicketEventType.STATUS_CHANGED, ticket.status.toString(), dto.status().toString());
        if (dto.status() == TicketStatus.IN_PROGRESS && ticket.assigneeId == null) ticket.assigneeId = ctx().requireUserId();
        if (!Guard.blank(dto.comment())) {
            comment(id, ctx().requireUserId(), dto.comment().trim(), false);
            if (ticket.firstRespondedAt == null) ticket.firstRespondedAt = now;
        }
        ticket.status = dto.status();
        db.flush();
        if (!ticket.requesterId.equals(ctx().userId))
            notifications.notify(new NotifyRequest(List.of(ticket.requesterId), ticket.companyId, "support.ticket_status",
                    ticket.number + " durumu güncellendi", dto.comment() == null || dto.comment().isBlank() ? ticket.subject : dto.comment().trim(),
                    "/support/tickets/" + ticket.id));
        return get(id);
    }

    public TicketDto comment(UUID id, TicketCommentBody dto) {
        Guard.that(!Guard.blank(dto.body()), "Yanıt boş olamaz");
        if (dto.isInternal()) Guard.that(isAgent(), "İç not yalnızca destek ekibine açık");
        Ticket ticket = open(id);
        boolean isRequester = ticket.requesterId.equals(ctx().userId);
        Guard.that(isAgent() || isRequester, "Talebi görme yetkiniz yok");
        if (!isAgent()) Guard.state(ACTIVE.contains(ticket.status), "Yanıt için talep yeniden açılmalı");
        Instant now = Instant.now();
        comment(id, ctx().requireUserId(), dto.body().trim(), dto.isInternal());
        if (!dto.isInternal() && !isRequester && ticket.firstRespondedAt == null) ticket.firstRespondedAt = now;
        if (isRequester && ticket.status == TicketStatus.WAITING) {
            TicketStatus next = ticket.assigneeId == null ? TicketStatus.OPEN : TicketStatus.IN_PROGRESS;
            applyStatus(ticket, next, now, false);
            event(id, ctx().requireUserId(), TicketEventType.STATUS_CHANGED, ticket.status.toString(), next.toString());
            ticket.status = next;
        }
        db.flush();
        if (!dto.isInternal()) {
            List<UUID> recipients = isRequester
                    ? (ticket.assigneeId != null ? List.of(ticket.assigneeId) : notifications.usersWithPermission(ctx().requireCompanyId(), "support:update"))
                    : List.of(ticket.requesterId);
            notifications.notify(new NotifyRequest(recipients, ticket.companyId, "support.ticket_comment",
                    ticket.number + " talebine yanıt geldi", dto.body().trim(), "/support/tickets/" + ticket.id));
        }
        return get(id);
    }

    public TicketDto close(UUID id, CloseTicket dto) {
        Ticket ticket = requester(id);
        Guard.state(ticket.status != TicketStatus.CLOSED, "Talep zaten kapalı");
        Instant now = Instant.now();
        TicketStatus from = ticket.status;
        applyStatus(ticket, TicketStatus.CLOSED, now, false);
        ticket.status = TicketStatus.CLOSED;
        if (dto != null && dto.rating() != null && dto.rating() >= 1 && dto.rating() <= 5) {
            ticket.satisfactionRating = dto.rating();
            ticket.satisfactionComment = blank(dto.comment());
        }
        event(id, ctx().requireUserId(), TicketEventType.STATUS_CHANGED, from.toString(), "CLOSED");
        db.flush();
        return get(id);
    }

    public TicketDto rate(UUID id, RateTicket dto) {
        Guard.that(dto.rating() >= 1 && dto.rating() <= 5, "Puan 1 ile 5 arasında olmalı");
        Ticket ticket = requester(id);
        Guard.state(ticket.status == TicketStatus.CLOSED, "Yalnızca kapalı talep puanlanır");
        Guard.state(ticket.satisfactionRating == null, "Talep zaten puanlanmış");
        ticket.satisfactionRating = dto.rating();
        ticket.satisfactionComment = blank(dto.comment());
        event(id, ctx().requireUserId(), TicketEventType.RATED, null, Integer.toString(dto.rating()));
        db.flush();
        return get(id);
    }

    public TicketDto reopen(UUID id, ReopenTicket dto) {
        Guard.that(!Guard.blank(dto.comment()), "Gerekçe gerekli");
        Ticket ticket = requester(id);
        Guard.state(ticket.status == TicketStatus.RESOLVED, "Yalnızca çözülen talep yeniden açılır");
        Instant now = Instant.now();
        TicketStatus next = ticket.assigneeId == null ? TicketStatus.OPEN : TicketStatus.IN_PROGRESS;
        comment(id, ctx().requireUserId(), dto.comment().trim(), false);
        applyStatus(ticket, next, now, false);
        event(id, ctx().requireUserId(), TicketEventType.REOPENED, ticket.status.toString(), next.toString());
        ticket.status = next;
        ticket.resolvedAt = null;
        db.flush();
        return get(id);
    }

    @Transactional(readOnly = true)
    public TicketList mine() {
        List<Ticket> rows = db.list("select t from Ticket t where t.requesterId = ?1 order by t.createdAt desc", Ticket.class, ctx().userId);
        Instant now = Instant.now();
        Names names = new Names();
        return new TicketList(rows.stream().map(t -> map(t, now, names)).toList());
    }

    @Transactional(readOnly = true)
    public TicketDto get(UUID id) {
        Ticket ticket = db.first("select t from Ticket t where t.id = ?1", Ticket.class, id).orElseThrow(() -> AppException.notFound("Talep bulunamadı"));
        Guard.that(isStaff() || ticket.requesterId.equals(ctx().userId), "Talebi görme yetkiniz yok");
        return map(ticket, Instant.now(), new Names());
    }

    /** Caches user display names for one response. */
    private final class Names {
        private final Map<UUID, String> cache = new HashMap<>();

        String of(UUID id) {
            if (id == null) return null;
            return cache.computeIfAbsent(id, k -> {
                String name = userName(k);
                return name == null ? "" : name;
            });
        }
    }

    private TicketDto map(Ticket t, Instant now, Names names) {
        boolean open = t.status != TicketStatus.CLOSED;
        boolean isRequester = t.requesterId.equals(ctx().userId);
        boolean staff = isStaff();
        List<TicketComment> rows = db.list("select c from TicketComment c where c.ticketId = ?1 order by c.createdAt", TicketComment.class, t.id);
        List<TicketCommentDto> comments = new ArrayList<>();
        rows.stream().filter(c -> staff || !c.isInternal).sorted(Comparator.comparing(c -> c.createdAt))
                .forEach(c -> comments.add(new TicketCommentDto(c.id, c.authorId, names.of(c.authorId), c.body, c.isInternal, c.createdAt)));
        String categoryName = t.categoryId == null ? null
                : db.first("select c.name from TicketCategory c where c.id = ?1", String.class, t.categoryId).orElse(null);
        Instant firstDone = t.firstRespondedAt != null ? t.firstRespondedAt : (t.status == TicketStatus.CLOSED ? t.closedAt : null);
        Instant resolveDone = t.resolvedAt != null ? t.resolvedAt : (t.status == TicketStatus.CLOSED ? t.closedAt : null);
        boolean agent = isAgent();
        return new TicketDto(t.id, t.number, t.subject, t.description, t.status, t.priority, t.categoryId, categoryName,
                t.requesterId, names.of(t.requesterId), t.assigneeId, t.assigneeId == null ? null : names.of(t.assigneeId),
                t.firstResponseDueAt, t.resolutionDueAt, t.firstRespondedAt, t.resolvedAt, t.closedAt,
                sla(t.createdAt, t.firstResponseDueAt, firstDone, null, now),
                sla(t.createdAt, t.resolutionDueAt, resolveDone, t.pausedAt, now),
                t.satisfactionRating, agent && open, open && (agent || (isRequester && ACTIVE.contains(t.status))),
                isRequester && open, isRequester && t.status == TicketStatus.RESOLVED,
                isRequester && t.status == TicketStatus.CLOSED && t.satisfactionRating == null,
                agent ? new ArrayList<>(TRANSITIONS.get(t.status)) : List.of(), comments);
    }

    private static void applyStatus(Ticket ticket, TicketStatus to, Instant now, boolean byAgent) {
        if (ticket.pausedAt != null && to != TicketStatus.WAITING) {
            int minutes = Math.max(0, (int) Math.rint(Duration.between(ticket.pausedAt, now).toMillis() / 60000.0));
            ticket.pausedMinutes += minutes;
            ticket.resolutionDueAt = ticket.resolutionDueAt.plus(minutes, ChronoUnit.MINUTES);
            ticket.pausedAt = null;
        }
        if (to == TicketStatus.WAITING && ticket.pausedAt == null) ticket.pausedAt = now;
        if (byAgent && (to == TicketStatus.RESOLVED || to == TicketStatus.CLOSED)) {
            if (ticket.resolvedAt == null) ticket.resolvedAt = now;
            if (ticket.firstRespondedAt == null) ticket.firstRespondedAt = now;
        }
        if (to == TicketStatus.CLOSED) ticket.closedAt = now;
        if (ticket.status == TicketStatus.RESOLVED && ACTIVE.contains(to)) ticket.resolvedAt = null;
    }

    static String sla(Instant started, Instant due, Instant done, Instant paused, Instant now) {
        if (done != null) return !done.isAfter(due) ? "MET" : "BREACHED";
        Instant at = paused != null ? paused : now;
        if (at.isAfter(due)) return "BREACHED";
        if (paused != null) return "PAUSED";
        double window = Duration.between(started, due).toMillis();
        double left = Duration.between(now, due).toMillis();
        return window > 0 && left < window * 0.25 ? "AT_RISK" : "ON_TRACK";
    }

    private Sla targets(TicketPriority priority) {
        TicketSlaPolicy policy = db.first("select p from TicketSlaPolicy p where p.priority = ?1", TicketSlaPolicy.class, priority).orElse(null);
        return policy == null ? DEFAULT_SLA.get(priority) : new Sla(policy.firstResponseHours, policy.resolutionHours);
    }

    private Ticket open(UUID id) {
        Ticket ticket = db.first("select t from Ticket t where t.id = ?1", Ticket.class, id).orElseThrow(() -> AppException.notFound("Talep bulunamadı"));
        Guard.state(ticket.status != TicketStatus.CLOSED, "Kapalı talep değiştirilemez");
        return ticket;
    }

    private Ticket requester(UUID id) {
        Ticket ticket = db.first("select t from Ticket t where t.id = ?1", Ticket.class, id).orElseThrow(() -> AppException.notFound("Talep bulunamadı"));
        Guard.that(ticket.requesterId.equals(ctx().userId) || ctx().superAdmin, "Yalnızca talep sahibi işlem yapabilir");
        return ticket;
    }

    private void event(UUID ticketId, UUID actorId, TicketEventType type, String from, String to) {
        TicketEvent row = new TicketEvent();
        row.ticketId = ticketId;
        row.actorId = actorId;
        row.type = type;
        row.fromValue = from;
        row.toValue = to;
        db.add(row);
    }

    private void comment(UUID ticketId, UUID authorId, String body, boolean internal) {
        TicketComment row = new TicketComment();
        row.ticketId = ticketId;
        row.authorId = authorId;
        row.body = body;
        row.isInternal = internal;
        db.add(row);
    }

    private String userName(UUID id) {
        if (id == null) return null;
        return db.first("select u from User u where u.id = ?1", User.class, id).map(u -> u.firstName + " " + u.lastName).orElse(null);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
