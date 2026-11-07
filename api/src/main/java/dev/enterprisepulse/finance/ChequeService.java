package dev.enterprisepulse.finance;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.Cheque;
import dev.enterprisepulse.data.generated.ChequeDirection;
import dev.enterprisepulse.data.generated.ChequeEvent;
import dev.enterprisepulse.data.generated.ChequeKind;
import dev.enterprisepulse.data.generated.ChequeStatus;
import dev.enterprisepulse.data.generated.PartnerPayment;
import dev.enterprisepulse.data.generated.PaymentDirection;
import dev.enterprisepulse.data.generated.TreasuryAccount;
import dev.enterprisepulse.data.generated.TreasuryAccountType;
import dev.enterprisepulse.data.generated.TreasuryDocumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChequeService {
    public record EventDto(LocalDate eventDate, ChequeStatus fromStatus, ChequeStatus toStatus, String note) {}

    public record Detail(UUID id, String number, ChequeKind kind, ChequeDirection direction, ChequeStatus status, String serialNo, String bankName, LocalDate issueDate,
                          LocalDate dueDate, BigDecimal amount, String currencyCode, UUID partnerId, String partnerTitle, UUID accountId, String accountName,
                          UUID originPaymentId, UUID endorsementPaymentId, List<EventDto> events) {}

    private final Db db;
    private final TreasuryService treasury;

    public ChequeService(Db db, TreasuryService treasury) {
        this.db = db;
        this.treasury = treasury;
    }

    public Cheque open(PartnerPayment payment, ChequeKind kind, String serialNo, String bankName, String branchName, String drawer, LocalDate dueDate, String number) {
        boolean received = payment.direction == PaymentDirection.IN;
        Cheque cheque = new Cheque();
        cheque.number = number;
        cheque.kind = kind;
        cheque.direction = received ? ChequeDirection.RECEIVED : ChequeDirection.ISSUED;
        cheque.status = received ? ChequeStatus.PORTFOLIO : ChequeStatus.ISSUED;
        cheque.serialNo = serialNo.trim();
        cheque.bankName = blank(bankName);
        cheque.branchName = blank(branchName);
        cheque.drawer = blank(drawer);
        cheque.issueDate = payment.paymentDate;
        cheque.dueDate = dueDate;
        cheque.amount = payment.amount;
        cheque.currencyCode = payment.currencyCode;
        cheque.exchangeRate = payment.exchangeRate;
        cheque.partnerId = payment.partnerId;
        cheque.originPaymentId = payment.id;
        db.add(cheque);
        event(cheque, null, cheque.status, payment.paymentDate, received ? "Portföye alındı" : "Keşide edildi", null, null);
        return cheque;
    }

    public void endorse(UUID chequeId, PartnerPayment payment) {
        Cheque cheque = lock(chequeId);
        Guard.state(cheque.direction == ChequeDirection.RECEIVED && cheque.status == ChequeStatus.PORTFOLIO, "Yalnızca portföydeki çek ciro edilir");
        Guard.that(cheque.currencyCode.equals(payment.currencyCode) && cheque.amount.compareTo(payment.amount) == 0, "Ciro tutarı çek tutarıyla aynı olmalı");
        cheque.status = ChequeStatus.ENDORSED;
        cheque.endorsedToId = payment.partnerId;
        cheque.endorsementPaymentId = payment.id;
        event(cheque, ChequeStatus.PORTFOLIO, ChequeStatus.ENDORSED, payment.paymentDate, "Tedarikçiye ciro", payment.partnerId, null);
    }

    public Detail deposit(UUID id, UUID accountId, LocalDate date) {
        Cheque cheque = lock(id);
        Guard.state(cheque.direction == ChequeDirection.RECEIVED && cheque.status == ChequeStatus.PORTFOLIO, "Yalnızca portföydeki çek bankaya verilir");
        TreasuryAccount account = Guard.found(db.first("select a from TreasuryAccount a where a.id = ?1 and a.isActive = true and a.type = ?2", TreasuryAccount.class, accountId, TreasuryAccountType.BANK), "Banka hesabı bulunamadı");
        Guard.that(account.currencyCode.equals(cheque.currencyCode), "Banka hesabının para birimi çekle aynı olmalı");
        cheque.status = ChequeStatus.DEPOSITED;
        cheque.accountId = account.id;
        event(cheque, ChequeStatus.PORTFOLIO, ChequeStatus.DEPOSITED, date, account.name + " hesabına verildi", null, account.id);
        return get(id);
    }

    public Detail collect(UUID id, LocalDate date) {
        Cheque cheque = lock(id);
        Guard.state(cheque.status == ChequeStatus.DEPOSITED && cheque.accountId != null, "Tahsil için çek önce bankaya verilmiş olmalı");
        cheque.status = ChequeStatus.COLLECTED;
        event(cheque, ChequeStatus.DEPOSITED, ChequeStatus.COLLECTED, date, "Tahsil edildi", null, cheque.accountId);
        treasury.post(cheque.accountId, date, TreasuryDocumentType.CHEQUE_COLLECTION, cheque.id, cheque.number, "Çek tahsil " + cheque.serialNo, cheque.amount, BigDecimal.ZERO);
        return get(id);
    }

    public Detail bounce(UUID id, LocalDate date, String note) {
        Cheque cheque = lock(id);
        Guard.state(cheque.status == ChequeStatus.DEPOSITED, "Yalnızca bankadaki çek karşılıksız çıkar");
        ChequeStatus from = cheque.status;
        cheque.status = ChequeStatus.BOUNCED;
        event(cheque, from, ChequeStatus.BOUNCED, date, note == null || note.isBlank() ? "Karşılıksız" : note.trim(), null, null);
        return get(id);
    }

    public Detail pay(UUID id, LocalDate date) {
        Cheque cheque = lock(id);
        Guard.state(cheque.direction == ChequeDirection.ISSUED && cheque.status == ChequeStatus.ISSUED, "Yalnızca keşide edilmiş çek ödenir");
        UUID accountId = cheque.accountId != null ? cheque.accountId : db.get(PartnerPayment.class, cheque.originPaymentId).accountId;
        Guard.that(accountId != null, "Çekin banka hesabı yok");
        cheque.accountId = accountId;
        cheque.status = ChequeStatus.PAID;
        event(cheque, ChequeStatus.ISSUED, ChequeStatus.PAID, date, "Ödendi", null, accountId);
        treasury.post(accountId, date, TreasuryDocumentType.CHEQUE_PAYMENT, cheque.id, cheque.number, "Çek ödemesi " + cheque.serialNo, BigDecimal.ZERO, cheque.amount);
        return get(id);
    }

    public void cancelWithPayment(PartnerPayment payment, LocalDate date) {
        Cheque cheque = db.first("select c from Cheque c where c.originPaymentId = ?1", Cheque.class, payment.id).orElse(null);
        if (cheque != null) {
            ChequeStatus initial = cheque.direction == ChequeDirection.RECEIVED ? ChequeStatus.PORTFOLIO : ChequeStatus.ISSUED;
            Guard.state(cheque.status == initial, "Çek portföyden çıktığı için tahsilat iptal edilemez");
            ChequeStatus from = cheque.status;
            cheque.status = ChequeStatus.CANCELLED;
            event(cheque, from, ChequeStatus.CANCELLED, date, "Belge iptali", null, null);
        }
        Cheque endorsed = db.first("select c from Cheque c where c.endorsementPaymentId = ?1", Cheque.class, payment.id).orElse(null);
        if (endorsed != null) {
            Guard.state(endorsed.status == ChequeStatus.ENDORSED, "Ciro edilen çek artık geri alınamaz");
            endorsed.status = ChequeStatus.PORTFOLIO;
            endorsed.endorsedToId = null;
            endorsed.endorsementPaymentId = null;
            event(endorsed, ChequeStatus.ENDORSED, ChequeStatus.PORTFOLIO, date, "Ciro iptal", null, null);
        }
    }

    public Detail get(UUID id) {
        Cheque cheque = Guard.found(db.first("select c from Cheque c where c.id = ?1", Cheque.class, id), "Çek bulunamadı");
        BusinessPartner partner = db.get(BusinessPartner.class, cheque.partnerId);
        TreasuryAccount account = cheque.accountId == null ? null : db.find(TreasuryAccount.class, cheque.accountId);
        List<EventDto> events = new ArrayList<>();
        for (ChequeEvent event : db.list("select e from ChequeEvent e where e.chequeId = ?1 order by e.createdAt", ChequeEvent.class, id)) {
            events.add(new EventDto(event.eventDate, event.fromStatus, event.toStatus, event.note));
        }
        return new Detail(cheque.id, cheque.number, cheque.kind, cheque.direction, cheque.status, cheque.serialNo, cheque.bankName, cheque.issueDate, cheque.dueDate,
                cheque.amount, cheque.currencyCode, cheque.partnerId, partner.title, cheque.accountId, account == null ? null : account.name, cheque.originPaymentId,
                cheque.endorsementPaymentId, events);
    }

    private Cheque lock(UUID id) {
        db.nativeRows("SELECT id FROM cheques WHERE id = ?1 FOR UPDATE", id);
        return Guard.found(db.first("select c from Cheque c where c.id = ?1", Cheque.class, id), "Çek bulunamadı");
    }

    private void event(Cheque cheque, ChequeStatus from, ChequeStatus to, LocalDate date, String note, UUID partnerId, UUID accountId) {
        ChequeEvent row = new ChequeEvent();
        row.chequeId = cheque.id;
        row.eventDate = date;
        row.fromStatus = from;
        row.toStatus = to;
        row.note = note;
        row.partnerId = partnerId;
        row.accountId = accountId;
        row.createdById = CurrentUser.get().requireUserId();
        db.add(row);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
