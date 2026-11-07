package dev.enterprisepulse.crm;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.Lead;
import dev.enterprisepulse.data.generated.Opportunity;
import dev.enterprisepulse.data.generated.OpportunityStage;
import dev.enterprisepulse.data.generated.PartnerType;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.platform.NumberingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OpportunityService {
    public record Save(String title, UUID partnerId, UUID leadId, BigDecimal amount, String currencyCode, LocalDate expectedCloseDate, UUID ownerId, String description) {}

    public record StageChange(OpportunityStage stage, String lostReason) {}

    public record Detail(UUID id, String number, String title, UUID partnerId, String partnerTitle, UUID leadId, String leadNumber,
                          OpportunityStage stage, BigDecimal amount, String currencyCode, int probability, BigDecimal weightedAmount,
                          LocalDate expectedCloseDate, UUID ownerId, String ownerName, String description, String lostReason, Instant closedAt) {}

    private final Db db;
    private final NumberingService numbering;

    public OpportunityService(Db db, NumberingService numbering) {
        this.db = db;
        this.numbering = numbering;
    }

    public static int probabilityOf(OpportunityStage stage) {
        return switch (stage) {
            case PROSPECTING -> 10;
            case QUALIFICATION -> 25;
            case PROPOSAL -> 50;
            case NEGOTIATION -> 75;
            case WON -> 100;
            case LOST -> 0;
        };
    }

    public static boolean isOpen(OpportunityStage stage) {
        return stage != OpportunityStage.WON && stage != OpportunityStage.LOST;
    }

    public Detail create(Save dto) {
        Guard.that(CurrentUser.get().has("crm:create"), "Fırsat oluşturma yetkiniz yok");
        Opportunity row = new Opportunity();
        row.number = numbering.next("OPPORTUNITY");
        row.stage = OpportunityStage.PROSPECTING;
        row.probability = probabilityOf(OpportunityStage.PROSPECTING);
        row.createdById = CurrentUser.get().requireUserId();
        apply(row, dto);
        db.add(row);
        db.flush();
        return get(row.id);
    }

    public Detail changeStage(UUID id, OpportunityStage stage, String lostReason) {
        Guard.that(CurrentUser.get().has("crm:update"), "Fırsat güncelleme yetkiniz yok");
        Opportunity row = Guard.found(db.first("select o from Opportunity o where o.id = ?1", Opportunity.class, id), "Fırsat bulunamadı");
        Guard.state(isOpen(row.stage), "Kapanmış fırsatın aşaması değişmez");
        if (stage == OpportunityStage.LOST) Guard.that(lostReason != null && !lostReason.isBlank(), "Kaybetme gerekçesi gerekli");
        row.stage = stage;
        row.probability = probabilityOf(stage);
        row.lostReason = stage == OpportunityStage.LOST ? lostReason.trim() : null;
        row.closedAt = isOpen(stage) ? null : Instant.now();
        return get(id);
    }

    @Transactional(readOnly = true)
    public Detail get(UUID id) {
        Guard.that(CurrentUser.get().has("crm:read"), "Fırsat görme yetkiniz yok");
        Opportunity row = Guard.found(db.first("select o from Opportunity o where o.id = ?1", Opportunity.class, id), "Fırsat bulunamadı");
        return map(row);
    }

    private void apply(Opportunity row, Save dto) {
        Guard.that(dto.title() != null && !dto.title().isBlank(), "Fırsat başlığı gerekli");
        Guard.that(dto.amount() != null && dto.amount().signum() >= 0, "Tutar negatif olamaz");
        BusinessPartner partner = Guard.found(db.first("select p from BusinessPartner p where p.id = ?1", BusinessPartner.class, dto.partnerId()), "Cari hesap bulunamadı");
        Guard.that(partner.type == PartnerType.CUSTOMER || partner.type == PartnerType.BOTH, "Fırsat yalnızca müşteri carisine açılır");
        if (dto.leadId() != null) {
            Lead lead = Guard.found(db.first("select l from Lead l where l.id = ?1", Lead.class, dto.leadId()), "Potansiyel müşteri bulunamadı");
            Guard.that(lead.partnerId == null || lead.partnerId.equals(partner.id), "Fırsat, potansiyel müşterinin carisiyle eşleşmiyor");
        }
        if (dto.ownerId() != null) Guard.that(db.exists("select u.id from User u where u.id = ?1", dto.ownerId()), "Sorumlu kullanıcı bulunamadı");
        row.title = dto.title().trim();
        row.partnerId = partner.id;
        row.leadId = dto.leadId();
        row.amount = dto.amount();
        row.currencyCode = dto.currencyCode() == null || dto.currencyCode().isBlank() ? partner.currencyCode : dto.currencyCode().trim().toUpperCase();
        row.expectedCloseDate = dto.expectedCloseDate();
        row.ownerId = dto.ownerId() != null ? dto.ownerId() : CurrentUser.get().userId;
        row.description = dto.description() == null || dto.description().isBlank() ? null : dto.description().trim();
    }

    private Detail map(Opportunity o) {
        BusinessPartner partner = db.get(BusinessPartner.class, o.partnerId);
        Lead lead = o.leadId == null ? null : db.find(Lead.class, o.leadId);
        User owner = o.ownerId == null ? null : db.find(User.class, o.ownerId);
        BigDecimal weighted = o.amount.multiply(BigDecimal.valueOf(o.probability)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Detail(o.id, o.number, o.title, o.partnerId, partner.title, o.leadId, lead == null ? null : lead.number, o.stage, o.amount, o.currencyCode,
                o.probability, weighted, o.expectedCloseDate, o.ownerId, owner == null ? null : owner.firstName + " " + owner.lastName, o.description, o.lostReason, o.closedAt);
    }
}
