package dev.enterprisepulse.crm;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.Lead;
import dev.enterprisepulse.data.generated.LeadSource;
import dev.enterprisepulse.data.generated.LeadStatus;
import dev.enterprisepulse.data.generated.PartnerType;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NumberingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LeadService {
    public record Save(String companyName, String contactName, String email, String phone, String city, LeadSource source,
                        BigDecimal estimatedValue, String currencyCode, String notes, UUID ownerId) {}

    public record Convert(boolean createOpportunity, String opportunityTitle, BigDecimal amount) {}

    public record Disqualify(String reason) {}

    public record Detail(UUID id, String number, String companyName, String contactName, String email, String phone, String city,
                          LeadSource source, LeadStatus status, BigDecimal estimatedValue, String currencyCode, String notes,
                          UUID ownerId, String ownerName, String disqualifyReason, UUID partnerId, String partnerTitle, Instant convertedAt) {}

    private final Db db;
    private final NumberingService numbering;
    private final OpportunityService opportunities;
    private final NotificationService notifications;

    public LeadService(Db db, NumberingService numbering, OpportunityService opportunities, NotificationService notifications) {
        this.db = db;
        this.numbering = numbering;
        this.opportunities = opportunities;
        this.notifications = notifications;
    }

    public static boolean isOpen(LeadStatus status) {
        return status == LeadStatus.NEW || status == LeadStatus.CONTACTED || status == LeadStatus.QUALIFIED;
    }

    public Detail create(Save dto) {
        Guard.that(CurrentUser.get().has("crm:create"), "Potansiyel müşteri oluşturma yetkiniz yok");
        Lead lead = new Lead();
        lead.number = numbering.next("LEAD");
        lead.createdById = CurrentUser.get().requireUserId();
        apply(lead, dto);
        db.add(lead);
        db.flush();
        return get(lead.id);
    }

    public Detail update(UUID id, Save dto) {
        Guard.that(CurrentUser.get().has("crm:update"), "Potansiyel müşteri güncelleme yetkiniz yok");
        Lead lead = find(id);
        Guard.state(isOpen(lead.status), "Kapanmış potansiyel müşteri güncellenemez");
        apply(lead, dto);
        return get(id);
    }

    public Detail disqualify(UUID id, String reason) {
        Guard.that(CurrentUser.get().has("crm:update"), "Potansiyel müşteri güncelleme yetkiniz yok");
        Guard.that(reason != null && !reason.isBlank(), "Eleme gerekçesi gerekli");
        Lead lead = find(id);
        Guard.state(isOpen(lead.status), "Kapanmış potansiyel müşteri elenemez");
        lead.status = LeadStatus.DISQUALIFIED;
        lead.disqualifyReason = reason.trim();
        return get(id);
    }

    /** Opens a customer account (or links one with the same e-mail) and optionally a prospecting opportunity. */
    public Detail convert(UUID id, Convert dto) {
        Guard.that(CurrentUser.get().has("crm:update"), "Potansiyel müşteri güncelleme yetkiniz yok");
        Lead lead = find(id);
        Guard.state(isOpen(lead.status), "Yalnızca açık potansiyel müşteri dönüştürülür");
        BusinessPartner partner = match(lead);
        if (partner == null) {
            partner = new BusinessPartner();
            partner.code = numbering.next("PARTNER");
            partner.title = lead.companyName;
            partner.type = PartnerType.CUSTOMER;
            partner.email = lead.email;
            partner.phone = lead.phone;
            partner.city = lead.city;
            partner.contactName = lead.contactName;
            partner.currencyCode = lead.currencyCode;
            db.add(partner);
            db.flush();
        }
        lead.partnerId = partner.id;
        lead.status = LeadStatus.CONVERTED;
        lead.convertedAt = Instant.now();
        db.flush();
        if (dto.createOpportunity()) {
            String title = dto.opportunityTitle() == null || dto.opportunityTitle().isBlank() ? lead.companyName : dto.opportunityTitle().trim();
            BigDecimal amount = dto.amount() != null ? dto.amount() : lead.estimatedValue != null ? lead.estimatedValue : BigDecimal.ZERO;
            opportunities.create(new OpportunityService.Save(title, partner.id, lead.id, amount, lead.currencyCode, null, lead.ownerId, lead.notes));
        }
        if (lead.ownerId != null && !lead.ownerId.equals(CurrentUser.get().userId)) {
            notifications.notify(new NotificationService.NotifyRequest(List.of(lead.ownerId), lead.companyId, "crm.lead_converted",
                    "Potansiyel müşteri carisi açıldı", lead.number + " — " + lead.companyName, "/crm/leads/" + lead.id));
        }
        return get(id);
    }

    @Transactional(readOnly = true)
    public Detail get(UUID id) {
        Guard.that(CurrentUser.get().has("crm:read"), "Potansiyel müşteri görme yetkiniz yok");
        return map(find(id));
    }

    private BusinessPartner match(Lead lead) {
        if (lead.email == null || lead.email.isBlank()) return null;
        return db.first("select p from BusinessPartner p where p.email = ?1", BusinessPartner.class, lead.email).orElse(null);
    }

    private void apply(Lead lead, Save dto) {
        Guard.that(dto.companyName() != null && !dto.companyName().isBlank(), "Firma adı gerekli");
        Guard.that(dto.estimatedValue() == null || dto.estimatedValue().signum() >= 0, "Tahmini tutar negatif olamaz");
        if (dto.ownerId() != null) Guard.that(db.exists("select u.id from User u where u.id = ?1", dto.ownerId()), "Sorumlu kullanıcı bulunamadı");
        lead.companyName = dto.companyName().trim();
        lead.contactName = blank(dto.contactName());
        lead.email = blank(dto.email());
        lead.phone = blank(dto.phone());
        lead.city = blank(dto.city());
        lead.source = dto.source() == null ? LeadSource.OTHER : dto.source();
        lead.estimatedValue = dto.estimatedValue();
        lead.currencyCode = dto.currencyCode() == null || dto.currencyCode().isBlank() ? "TRY" : dto.currencyCode().trim().toUpperCase();
        lead.notes = blank(dto.notes());
        lead.ownerId = dto.ownerId() != null ? dto.ownerId() : CurrentUser.get().userId;
    }

    private Lead find(UUID id) {
        return Guard.found(db.first("select l from Lead l where l.id = ?1", Lead.class, id), "Potansiyel müşteri bulunamadı");
    }

    private Detail map(Lead l) {
        BusinessPartner partner = l.partnerId == null ? null : db.find(BusinessPartner.class, l.partnerId);
        return new Detail(l.id, l.number, l.companyName, l.contactName, l.email, l.phone, l.city, l.source, l.status, l.estimatedValue, l.currencyCode,
                l.notes, l.ownerId, name(l.ownerId), l.disqualifyReason, l.partnerId, partner == null ? null : partner.title, l.convertedAt);
    }

    private String name(UUID userId) {
        if (userId == null) return null;
        User user = db.find(User.class, userId);
        return user == null ? null : user.firstName + " " + user.lastName;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
