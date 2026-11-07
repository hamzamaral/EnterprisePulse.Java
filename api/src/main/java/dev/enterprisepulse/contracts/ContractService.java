package dev.enterprisepulse.contracts;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Contract;
import dev.enterprisepulse.data.generated.ContractBillingCycle;
import dev.enterprisepulse.data.generated.ContractEvent;
import dev.enterprisepulse.data.generated.ContractEventType;
import dev.enterprisepulse.data.generated.ContractParty;
import dev.enterprisepulse.data.generated.ContractStatus;
import dev.enterprisepulse.data.generated.ContractType;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.platform.Extensibility.ScheduledJob;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NotificationService.NotifyRequest;
import dev.enterprisepulse.platform.NumberingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Sözleşme. Günlük iş otomatik yeniler, sona erdirir ve hatırlatır. */
@Service
@Transactional
public class ContractService implements ScheduledJob {
    public record SaveContract(String title, UUID typeId, UUID partnerId, UUID employeeId, UUID ownerId, LocalDate startDate, LocalDate endDate,
                               LocalDate signedDate, BigDecimal amount, String currencyCode, ContractBillingCycle billingCycle, boolean autoRenew,
                               Integer renewalMonths, Integer noticeDays, Integer reminderDays, String description) {}
    public record RenewContract(LocalDate endDate, BigDecimal amount, String note) {}
    public record TerminateContract(LocalDate terminatedOn, String reason) {}
    public record ActivateBody(LocalDate signedDate) {}
    public record ContractDto(UUID id, String number, String title, ContractStatus status, String state, ContractParty party, UUID typeId, String typeName,
                              UUID partnerId, String partnerTitle, UUID employeeId, String employeeName, UUID ownerId, String ownerName,
                              LocalDate startDate, LocalDate endDate, BigDecimal amount, String currencyCode, boolean autoRenew, int renewalCount,
                              boolean canActivate, boolean canRenew, boolean canTerminate) {}
    public record ContractRunResult(int renewed, int expired, int terminated, int reminded) {}

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final Db db;
    private final NumberingService numbering;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public ContractService(Db db, NumberingService numbering, NotificationService notifications, TransactionTemplate tx) {
        this.db = db;
        this.numbering = numbering;
        this.notifications = notifications;
        this.tx = tx;
    }

    @Override public String id() { return "contracts-daily"; }

    @Override public String cron() { return "0 0 7 * * *"; }

    private static CurrentUser ctx() { return CurrentUser.get(); }

    private static LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run() {
        CurrentUser ctx = ctx();
        List<UUID> companies;
        try (var bypass = ctx.withoutTenant()) {
            companies = tx.execute(s -> db.list("select c.id from Company c", UUID.class));
        }
        for (UUID companyId : companies) {
            try (var scope = ctx.actAs(companyId, null, true)) {
                tx.executeWithoutResult(s -> runCompany());
            }
        }
    }

    public ContractRunResult runCompany() {
        LocalDate today = today();
        int renewed = 0, expired = 0, terminated = 0, reminded = 0;
        List<Contract> contracts = db.list("select c from Contract c where c.status = ?1", Contract.class, ContractStatus.ACTIVE);
        for (Contract contract : contracts) {
            if (contract.terminatedOn != null && contract.terminatedOn.isBefore(today)) {
                contract.status = ContractStatus.TERMINATED;
                event(contract.id, null, ContractEventType.TERMINATED, null, contract.terminatedOn.toString(), null);
                terminated++;
                continue;
            }
            if (contract.endDate == null || !contract.endDate.isBefore(today)) {
                if (reminderDue(contract, today)) {
                    contract.remindedFor = contract.endDate;
                    event(contract.id, null, ContractEventType.REMINDER_SENT, null, contract.endDate == null ? null : contract.endDate.toString(), null);
                    notifications.notify(new NotifyRequest(List.of(contract.ownerId), contract.companyId, "contracts.reminder",
                            "Sözleşme hatırlatması: " + contract.number,
                            contract.title + " · bitiş " + (contract.endDate == null ? "" : contract.endDate.format(DISPLAY)), "/contracts/" + contract.id, true));
                    reminded++;
                }
                continue;
            }
            if (contract.autoRenew && contract.renewalMonths != null && contract.renewalMonths > 0) {
                LocalDate endDate = contract.endDate;
                int times = 0;
                while (endDate.isBefore(today)) {
                    endDate = termEnd(endDate.plusDays(1), contract.renewalMonths);
                    times++;
                }
                LocalDate from = contract.endDate;
                contract.endDate = endDate;
                contract.renewalCount += times;
                contract.remindedFor = null;
                event(contract.id, null, ContractEventType.AUTO_RENEWED, from.toString(), endDate.toString(), null);
                renewed += times;
            } else {
                contract.status = ContractStatus.EXPIRED;
                event(contract.id, null, ContractEventType.EXPIRED, null, contract.endDate.toString(), null);
                expired++;
            }
        }
        db.flush();
        return new ContractRunResult(renewed, expired, terminated, reminded);
    }

    public ContractDto create(SaveContract dto) {
        Guard.that(ctx().has("contracts:create"), "Sözleşme oluşturma yetkiniz yok");
        Guard.that(!Guard.blank(dto.title()), "Başlık gerekli");
        ContractType type = db.first("select t from ContractType t where t.id = ?1 and t.isActive = true", ContractType.class, dto.typeId())
                .orElseThrow(() -> AppException.badRequest("Sözleşme türü bulunamadı"));
        assertParty(type.party, dto.partnerId(), dto.employeeId());
        boolean autoRenew = dto.autoRenew();
        Integer renewalMonths = dto.renewalMonths() != null ? dto.renewalMonths() : (autoRenew ? type.defaultTermMonths : null);
        assertTerms(dto.startDate(), dto.endDate(), autoRenew, renewalMonths);
        String baseCurrency = db.one("select c.baseCurrency from Company c where c.id = ?1", String.class, ctx().companyId);
        Contract contract = new Contract();
        contract.number = numbering.next("CONTRACT");
        contract.title = dto.title().trim();
        contract.typeId = type.id;
        contract.party = type.party;
        contract.partnerId = type.party == ContractParty.PARTNER ? dto.partnerId() : null;
        contract.employeeId = type.party == ContractParty.EMPLOYEE ? dto.employeeId() : null;
        contract.ownerId = dto.ownerId() != null ? dto.ownerId() : ctx().requireUserId();
        contract.startDate = dto.startDate();
        contract.endDate = dto.endDate();
        contract.signedDate = dto.signedDate();
        contract.amount = dto.amount();
        contract.currencyCode = Guard.blank(dto.currencyCode()) ? baseCurrency : dto.currencyCode().trim();
        contract.billingCycle = dto.billingCycle() == null ? ContractBillingCycle.ONE_TIME : dto.billingCycle();
        contract.autoRenew = autoRenew;
        contract.renewalMonths = renewalMonths;
        contract.noticeDays = dto.noticeDays() != null ? dto.noticeDays() : type.defaultNoticeDays;
        contract.reminderDays = dto.reminderDays() != null ? dto.reminderDays() : 30;
        contract.description = blank(dto.description());
        contract.createdById = ctx().requireUserId();
        db.add(contract);
        event(contract.id, ctx().requireUserId(), ContractEventType.CREATED, null, null, null);
        db.flush();
        return get(contract.id);
    }

    public ContractDto activate(UUID id, LocalDate signedDate) {
        Guard.that(ctx().has("contracts:update"), "Sözleşme güncelleme yetkiniz yok");
        Contract contract = find(id);
        Guard.state(contract.status == ContractStatus.DRAFT, "Yalnızca taslak sözleşme aktive edilir");
        contract.status = ContractStatus.ACTIVE;
        contract.signedDate = signedDate != null ? signedDate : contract.signedDate != null ? contract.signedDate : today();
        contract.activatedAt = Instant.now();
        event(id, ctx().requireUserId(), ContractEventType.ACTIVATED, null, contract.signedDate.toString(), null);
        db.flush();
        runCompany();
        return get(id);
    }

    public ContractDto renew(UUID id, RenewContract dto) {
        Guard.that(ctx().has("contracts:update"), "Sözleşme güncelleme yetkiniz yok");
        Contract contract = find(id);
        boolean renewable = (contract.status == ContractStatus.ACTIVE && contract.terminatedOn == null) || contract.status == ContractStatus.EXPIRED;
        Guard.state(renewable && contract.endDate != null, "Yalnızca süreli aktif veya bitmiş sözleşme yenilenir");
        LocalDate today = today();
        Guard.that(dto.endDate() != null && dto.endDate().isAfter(contract.endDate) && !dto.endDate().isBefore(today),
                "Yeni bitiş bugünden ve mevcut bitişten sonra olmalı");
        LocalDate from = contract.endDate;
        contract.endDate = dto.endDate();
        if (dto.amount() != null) contract.amount = dto.amount();
        contract.status = ContractStatus.ACTIVE;
        contract.renewalCount += 1;
        contract.remindedFor = null;
        event(id, ctx().requireUserId(), ContractEventType.RENEWED, from.toString(), dto.endDate().toString(), blank(dto.note()));
        db.flush();
        return get(id);
    }

    public ContractDto terminate(UUID id, TerminateContract dto) {
        Guard.that(ctx().has("contracts:update"), "Sözleşme güncelleme yetkiniz yok");
        Contract contract = find(id);
        Guard.state(contract.status == ContractStatus.ACTIVE && contract.terminatedOn == null, "Yalnızca aktif sözleşme feshedilir");
        Guard.that(dto.terminatedOn() != null, "Fesih tarihi gerekli");
        contract.terminatedOn = dto.terminatedOn();
        contract.terminationReason = blank(dto.reason());
        event(id, ctx().requireUserId(), ContractEventType.TERMINATION_SCHEDULED, null, dto.terminatedOn().toString(), blank(dto.reason()));
        db.flush();
        runCompany();
        return get(id);
    }

    @Transactional(readOnly = true)
    public ContractDto get(UUID id) {
        Contract contract = find(id);
        Guard.that(ctx().has("contracts:read") || Objects.equals(contract.ownerId, ctx().userId), "Sözleşme görme yetkiniz yok");
        return map(contract, today());
    }

    private ContractDto map(Contract c, LocalDate today) {
        boolean canUpdate = ctx().has("contracts:update");
        String typeName = db.first("select t.name from ContractType t where t.id = ?1", String.class, c.typeId).orElse(null);
        String partnerTitle = c.partnerId == null ? null : db.first("select p.title from BusinessPartner p where p.id = ?1", String.class, c.partnerId).orElse(null);
        String employeeName = null;
        if (c.employeeId != null)
            employeeName = db.first("select e from Employee e where e.id = ?1", Employee.class, c.employeeId).map(e -> e.firstName + " " + e.lastName).orElse(null);
        String ownerName = db.first("select u from User u where u.id = ?1", User.class, c.ownerId).map(u -> u.firstName + " " + u.lastName).orElse(null);
        return new ContractDto(c.id, c.number, c.title, c.status, state(c, today), c.party, c.typeId, typeName, c.partnerId, partnerTitle,
                c.employeeId, employeeName, c.ownerId, ownerName, c.startDate, c.endDate, c.amount, c.currencyCode, c.autoRenew, c.renewalCount,
                canUpdate && c.status == ContractStatus.DRAFT,
                canUpdate && c.endDate != null && ((c.status == ContractStatus.ACTIVE && c.terminatedOn == null) || c.status == ContractStatus.EXPIRED),
                canUpdate && c.status == ContractStatus.ACTIVE && c.terminatedOn == null);
    }

    static String state(Contract c, LocalDate today) {
        if (c.status != ContractStatus.ACTIVE) return c.status.toString();
        if (c.terminatedOn != null) return "TERMINATING";
        LocalDate action = actionDate(c);
        return action != null && !today.isBefore(action.minusDays(c.reminderDays)) ? "EXPIRING" : "ACTIVE";
    }

    private static LocalDate actionDate(Contract c) {
        if (c.endDate == null) return null;
        return c.autoRenew && c.terminatedOn == null ? c.endDate.minusDays(c.noticeDays) : c.endDate;
    }

    private static boolean reminderDue(Contract c, LocalDate today) {
        if (c.terminatedOn != null || c.endDate == null) return false;
        if (c.endDate.equals(c.remindedFor)) return false;
        LocalDate due = actionDate(c);
        return due != null && !today.isBefore(due.minusDays(c.reminderDays)) && !today.isAfter(c.endDate);
    }

    static LocalDate termEnd(LocalDate start, int months) {
        return start.plusMonths(months).minusDays(1);
    }

    private static void assertTerms(LocalDate start, LocalDate end, boolean autoRenew, Integer renewalMonths) {
        Guard.that(start != null, "Başlangıç tarihi gerekli");
        if (end != null) Guard.that(!end.isBefore(start), "Bitiş başlangıçtan önce olamaz");
        if (autoRenew) Guard.that(end != null && renewalMonths != null && renewalMonths > 0, "Otomatik yenileme için süre ve ay gerekli");
    }

    private void assertParty(ContractParty party, UUID partnerId, UUID employeeId) {
        if (party == ContractParty.PARTNER) {
            Guard.that(partnerId != null, "Cari gerekli");
            Guard.that(db.exists("select p.id from BusinessPartner p where p.id = ?1", partnerId), "Cari bulunamadı");
        } else {
            Guard.that(employeeId != null, "Personel gerekli");
            Guard.that(db.exists("select e.id from Employee e where e.id = ?1", employeeId), "Personel bulunamadı");
        }
    }

    private Contract find(UUID id) {
        return db.first("select c from Contract c where c.id = ?1", Contract.class, id).orElseThrow(() -> AppException.notFound("Sözleşme bulunamadı"));
    }

    private void event(UUID contractId, UUID actorId, ContractEventType type, String from, String to, String note) {
        ContractEvent row = new ContractEvent();
        row.contractId = contractId;
        row.actorId = actorId;
        row.type = type;
        row.fromValue = from;
        row.toValue = to;
        row.note = note;
        db.add(row);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
