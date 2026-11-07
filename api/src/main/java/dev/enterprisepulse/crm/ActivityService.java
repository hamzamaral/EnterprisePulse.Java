package dev.enterprisepulse.crm;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.CrmActivity;
import dev.enterprisepulse.data.generated.CrmActivityType;
import dev.enterprisepulse.data.generated.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ActivityService {
    public record Save(CrmActivityType type, String subject, String description, Instant dueAt, UUID ownerId, UUID leadId, UUID opportunityId, UUID partnerId) {}

    public record Detail(UUID id, CrmActivityType type, String subject, String description, Instant dueAt, Instant completedAt, boolean overdue,
                          UUID ownerId, String ownerName, UUID leadId, UUID opportunityId, UUID partnerId) {}

    private final Db db;

    public ActivityService(Db db) {
        this.db = db;
    }

    public Detail create(Save dto) {
        Guard.that(CurrentUser.get().has("crm:create"), "Aktivite oluşturma yetkiniz yok");
        CrmActivity row = new CrmActivity();
        row.createdById = CurrentUser.get().requireUserId();
        apply(row, dto);
        db.add(row);
        db.flush();
        return get(row.id);
    }

    public Detail complete(UUID id) {
        Guard.that(CurrentUser.get().has("crm:update"), "Aktivite güncelleme yetkiniz yok");
        CrmActivity row = find(id);
        Guard.state(row.completedAt == null, "Aktivite zaten tamamlanmış");
        row.completedAt = Instant.now();
        return get(id);
    }

    @Transactional(readOnly = true)
    public Detail get(UUID id) {
        Guard.that(CurrentUser.get().has("crm:read"), "Aktivite görme yetkiniz yok");
        return map(find(id));
    }

    private void apply(CrmActivity row, Save dto) {
        Guard.that(dto.subject() != null && !dto.subject().isBlank(), "Konu gerekli");
        Guard.that(dto.leadId() != null || dto.opportunityId() != null || dto.partnerId() != null, "Aktivite bir kayıtla ilişkilendirilmeli");
        if (dto.leadId() != null) Guard.that(db.exists("select l.id from Lead l where l.id = ?1", dto.leadId()), "Potansiyel müşteri bulunamadı");
        if (dto.opportunityId() != null) Guard.that(db.exists("select o.id from Opportunity o where o.id = ?1", dto.opportunityId()), "Fırsat bulunamadı");
        if (dto.partnerId() != null) Guard.that(db.exists("select p.id from BusinessPartner p where p.id = ?1", dto.partnerId()), "Cari hesap bulunamadı");
        UUID owner = dto.ownerId() != null ? dto.ownerId() : CurrentUser.get().requireUserId();
        Guard.that(db.exists("select u.id from User u where u.id = ?1", owner), "Sorumlu kullanıcı bulunamadı");
        row.type = dto.type() == null ? CrmActivityType.NOTE : dto.type();
        row.subject = dto.subject().trim();
        row.description = dto.description() == null || dto.description().isBlank() ? null : dto.description().trim();
        row.dueAt = dto.dueAt();
        row.ownerId = owner;
        row.leadId = dto.leadId();
        row.opportunityId = dto.opportunityId();
        row.partnerId = dto.partnerId();
    }

    private CrmActivity find(UUID id) {
        return Guard.found(db.first("select a from CrmActivity a where a.id = ?1", CrmActivity.class, id), "Aktivite bulunamadı");
    }

    private Detail map(CrmActivity a) {
        User owner = db.get(User.class, a.ownerId);
        boolean overdue = a.completedAt == null && a.dueAt != null && a.dueAt.isBefore(Instant.now());
        return new Detail(a.id, a.type, a.subject, a.description, a.dueAt, a.completedAt, overdue, a.ownerId, owner.firstName + " " + owner.lastName,
                a.leadId, a.opportunityId, a.partnerId);
    }
}
