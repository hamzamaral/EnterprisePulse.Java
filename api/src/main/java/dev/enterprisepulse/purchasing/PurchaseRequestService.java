package dev.enterprisepulse.purchasing;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.PurchaseRequest;
import dev.enterprisepulse.data.generated.PurchaseRequestLine;
import dev.enterprisepulse.data.generated.PurchaseRequestStatus;
import dev.enterprisepulse.data.generated.Unit;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.data.generated.WorkflowStatus;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NotificationService.NotifyRequest;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.workflow.WorkflowService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PurchaseRequestService {
    public static final String WORKFLOW = "PURCHASE_REQUEST";
    public static final String ENTITY = "PurchaseRequest";

    public record LineInput(UUID variantId, String description, BigDecimal quantity, BigDecimal estimatedPrice) {}

    public record CreateRequest(String title, LocalDate neededBy, UUID departmentId, String notes, List<LineInput> lines) {}

    public record LineDto(UUID id, int lineNo, UUID variantId, UUID productId, String sku, String unitCode, String description, BigDecimal quantity, BigDecimal estimatedPrice) {}

    public record Detail(UUID id, String number, String title, LocalDate requestDate, LocalDate neededBy, UUID departmentId, PurchaseRequestStatus status,
                          UUID requestedById, String requestedByName, BigDecimal estimatedTotal, int quoteCount, UUID workflowInstanceId, Instant decidedAt,
                          String notes, boolean canCancel, List<LineDto> lines) {}

    private final Db db;
    private final NumberingService numbering;
    private final WorkflowService workflow;
    private final NotificationService notifications;

    public PurchaseRequestService(Db db, NumberingService numbering, WorkflowService workflow, NotificationService notifications) {
        this.db = db;
        this.numbering = numbering;
        this.workflow = workflow;
        this.notifications = notifications;
    }

    public Detail create(CreateRequest body) {
        CurrentUser ctx = CurrentUser.get();
        Guard.that(ctx.hasAny("workflow:start", "purchasing:create"), "Satın alma talebi oluşturma yetkiniz yok");
        Guard.that(body.title() != null && !body.title().isBlank(), "Başlık gerekli");
        Guard.that(body.lines() != null && !body.lines().isEmpty(), "En az bir satır gerekli");
        PurchaseRequest request = new PurchaseRequest();
        request.number = numbering.next("PURCHASE_REQUEST");
        request.title = body.title().trim();
        request.requestDate = LocalDate.now();
        request.neededBy = body.neededBy();
        request.departmentId = body.departmentId();
        request.notes = blank(body.notes());
        request.requestedById = ctx.requireUserId();
        db.add(request);
        int n = 1;
        for (LineInput line : body.lines()) {
            Guard.that(line.quantity() != null && line.quantity().signum() > 0, "Satır " + n + ": miktar sıfırdan büyük olmalı");
            String description = line.description();
            if (line.variantId() != null) {
                ProductVariant v = db.get(ProductVariant.class, line.variantId());
                Product p = db.get(Product.class, v.productId);
                if (description == null || description.isBlank()) description = v.name != null && !v.name.isBlank() ? p.name + " (" + v.name + ")" : p.name;
            }
            Guard.that(description != null && !description.isBlank(), "Satır " + n + ": açıklama gerekli");
            PurchaseRequestLine row = new PurchaseRequestLine();
            row.requestId = request.id;
            row.lineNo = n++;
            row.variantId = line.variantId();
            row.description = description.trim();
            row.quantity = line.quantity();
            row.estimatedPrice = line.estimatedPrice();
            db.add(row);
        }
        db.flush();
        var instance = workflow.start(new WorkflowService.StartRequest(WORKFLOW, request.id.toString(), request.number + " — " + request.title));
        request.workflowInstanceId = instance.id();
        request.status = instance.status() == WorkflowStatus.APPROVED ? PurchaseRequestStatus.APPROVED : PurchaseRequestStatus.PENDING;
        db.flush();
        return detail(request.id);
    }

    public Detail cancel(UUID id, String reason) {
        PurchaseRequest request = get(id);
        CurrentUser ctx = CurrentUser.get();
        Guard.that(ctx.has("purchasing:update") || request.requestedById.equals(ctx.userId), "Bu talebi iptal etme yetkiniz yok");
        Guard.state(request.status == PurchaseRequestStatus.PENDING || request.status == PurchaseRequestStatus.APPROVED, "Talep bu durumda iptal edilemez");
        if (request.status == PurchaseRequestStatus.PENDING && request.workflowInstanceId != null) workflow.cancel(request.workflowInstanceId, reason);
        request.status = PurchaseRequestStatus.CANCELLED;
        request.decidedAt = Instant.now();
        if (reason != null && !reason.isBlank()) request.notes = (request.notes == null ? "" : request.notes + "\n") + "[İptal] " + reason.trim();
        db.flush();
        return detail(id);
    }

    public void decide(UUID id, PurchaseRequestStatus status) {
        PurchaseRequest request = db.find(PurchaseRequest.class, id);
        if (request == null || request.status != PurchaseRequestStatus.PENDING) return;
        request.status = status;
        request.decidedAt = Instant.now();
        db.flush();
        if (status != PurchaseRequestStatus.CANCELLED && !request.requestedById.equals(CurrentUser.get().userId)) {
            notifications.notify(new NotifyRequest(List.of(request.requestedById), request.companyId, "purchasing.request",
                    status == PurchaseRequestStatus.APPROVED ? "Satın alma talebiniz onaylandı" : "Satın alma talebiniz reddedildi",
                    request.number + " — " + request.title, "/r/purchase-requests/" + request.id));
        }
    }

    public Detail detail(UUID id) {
        PurchaseRequest r = get(id);
        ownOnly(r);
        User user = db.find(User.class, r.requestedById);
        List<LineDto> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseRequestLine l : db.list("select l from PurchaseRequestLine l where l.requestId = ?1 order by l.lineNo", PurchaseRequestLine.class, r.id)) {
            ProductVariant v = l.variantId == null ? null : db.find(ProductVariant.class, l.variantId);
            Product p = v == null ? null : db.find(Product.class, v.productId);
            Unit unit = p == null || p.unitId == null ? null : db.find(Unit.class, p.unitId);
            if (l.estimatedPrice != null) total = total.add(l.quantity.multiply(l.estimatedPrice));
            lines.add(new LineDto(l.id, l.lineNo, l.variantId, p == null ? null : p.id, v == null ? null : v.sku, unit == null ? null : unit.code, l.description, l.quantity, l.estimatedPrice));
        }
        int quotes = (int) db.count("select count(q) from SupplierQuote q where q.requestId = ?1", r.id);
        boolean canCancel = (CurrentUser.get().has("purchasing:update") || r.requestedById.equals(CurrentUser.get().userId))
                && (r.status == PurchaseRequestStatus.PENDING || r.status == PurchaseRequestStatus.APPROVED);
        return new Detail(r.id, r.number, r.title, r.requestDate, r.neededBy, r.departmentId, r.status, r.requestedById,
                user == null ? "" : user.firstName + " " + user.lastName, total, quotes, r.workflowInstanceId, r.decidedAt, r.notes, canCancel, lines);
    }

    private PurchaseRequest get(UUID id) {
        return Guard.found(db.first("select r from PurchaseRequest r where r.id = ?1", PurchaseRequest.class, id), "Talep bulunamadı");
    }

    private void ownOnly(PurchaseRequest request) {
        if (!CurrentUser.get().has("purchasing:read") && !request.requestedById.equals(CurrentUser.get().userId)) throw dev.enterprisepulse.common.AppException.forbidden();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Component
    public static class WorkflowListener implements WorkflowService.Listener {
        private final ObjectProvider<PurchaseRequestService> requests;

        public WorkflowListener(ObjectProvider<PurchaseRequestService> requests) {
            this.requests = requests;
        }

        @Override
        public String entityType() {
            return ENTITY;
        }

        @Override
        public void onFinished(WorkflowService.Finished e) {
            UUID id;
            try {
                id = UUID.fromString(e.entityId());
            } catch (IllegalArgumentException ex) {
                return;
            }
            PurchaseRequestStatus status = e.status() == WorkflowStatus.APPROVED ? PurchaseRequestStatus.APPROVED
                    : e.status() == WorkflowStatus.REJECTED ? PurchaseRequestStatus.REJECTED : PurchaseRequestStatus.CANCELLED;
            requests.getObject().decide(id, status);
        }
    }
}
