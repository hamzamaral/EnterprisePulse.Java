package dev.enterprisepulse.partners;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.LedgerDocumentType;
import dev.enterprisepulse.data.generated.PartnerLedgerEntry;
import dev.enterprisepulse.data.generated.PartnerType;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PartnerLedgerService {
    public static final PartnerType[] CUSTOMERS = {PartnerType.CUSTOMER, PartnerType.BOTH};
    public static final PartnerType[] SUPPLIERS = {PartnerType.SUPPLIER, PartnerType.BOTH};

    public record Posting(UUID partnerId, LocalDate entryDate, LocalDate dueDate, LedgerDocumentType documentType, UUID documentId,
                           String documentNumber, String description, BigDecimal debit, BigDecimal credit, String currencyCode, BigDecimal exchangeRate) {}

    public record StatementEntry(UUID id, LocalDate entryDate, LocalDate dueDate, LedgerDocumentType documentType, UUID documentId, String documentNumber,
                                  String description, String currencyCode, BigDecimal exchangeRate, BigDecimal debit, BigDecimal credit,
                                  BigDecimal debitBase, BigDecimal creditBase, BigDecimal balance, boolean reversal) {}

    public record Statement(UUID partnerId, String code, String title, String baseCurrency, LocalDate dateFrom, LocalDate dateTo,
                             BigDecimal openingBalance, BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal closingBalance, List<StatementEntry> entries) {}

    private final Db db;

    public PartnerLedgerService(Db db) {
        this.db = db;
    }

    public PartnerLedgerEntry post(Posting p) {
        Guard.that(p.debit().signum() >= 0 && p.credit().signum() >= 0 && p.debit().add(p.credit()).signum() > 0, "Cari tutarı sıfırdan büyük olmalı");
        PartnerLedgerEntry entry = new PartnerLedgerEntry();
        entry.partnerId = p.partnerId();
        entry.entryDate = p.entryDate();
        entry.dueDate = p.dueDate();
        entry.documentType = p.documentType();
        entry.documentId = p.documentId();
        entry.documentNumber = p.documentNumber();
        entry.description = p.description();
        entry.debit = p.debit();
        entry.credit = p.credit();
        entry.currencyCode = p.currencyCode();
        entry.exchangeRate = p.exchangeRate();
        entry.debitBase = DocumentMath.toBase(p.debit(), p.exchangeRate());
        entry.creditBase = DocumentMath.toBase(p.credit(), p.exchangeRate());
        entry.createdById = CurrentUser.get().requireUserId();
        db.add(entry);
        db.flush();
        return entry;
    }

    public void reverse(LedgerDocumentType type, UUID documentId, LocalDate entryDate, String description) {
        List<PartnerLedgerEntry> entries = db.list("select e from PartnerLedgerEntry e where e.documentType = ?1 and e.documentId = ?2 and e.reversesId is null",
                PartnerLedgerEntry.class, type, documentId);
        List<UUID> reversed = db.list("select e.reversesId from PartnerLedgerEntry e where e.documentType = ?1 and e.documentId = ?2 and e.reversesId is not null",
                UUID.class, type, documentId);
        for (PartnerLedgerEntry e : entries) {
            if (reversed.contains(e.id)) continue;
            PartnerLedgerEntry row = new PartnerLedgerEntry();
            row.partnerId = e.partnerId;
            row.entryDate = entryDate;
            row.documentType = type;
            row.documentId = documentId;
            row.documentNumber = e.documentNumber;
            row.description = description;
            row.debit = e.credit;
            row.credit = e.debit;
            row.currencyCode = e.currencyCode;
            row.exchangeRate = e.exchangeRate;
            row.debitBase = e.creditBase;
            row.creditBase = e.debitBase;
            row.reversesId = e.id;
            row.createdById = CurrentUser.get().requireUserId();
            db.add(row);
        }
        db.flush();
    }

    public BigDecimal balance(UUID partnerId) {
        return db.first("select coalesce(sum(e.debitBase - e.creditBase), 0) from PartnerLedgerEntry e where e.partnerId = ?1", BigDecimal.class, partnerId)
                .orElse(BigDecimal.ZERO);
    }

    public Statement statement(UUID partnerId, LocalDate from, LocalDate to) {
        BusinessPartner partner = Guard.found(db.first("select p from BusinessPartner p where p.id = ?1", BusinessPartner.class, partnerId), "Cari hesap bulunamadı");
        String base = db.one("select c.baseCurrency from Company c where c.id = ?1", String.class, partner.companyId);
        BigDecimal opening = from == null ? BigDecimal.ZERO
                : db.first("select coalesce(sum(e.debitBase - e.creditBase), 0) from PartnerLedgerEntry e where e.partnerId = ?1 and e.entryDate < ?2",
                BigDecimal.class, partnerId, from).orElse(BigDecimal.ZERO);
        List<PartnerLedgerEntry> rows = db.list("""
                select e from PartnerLedgerEntry e where e.partnerId = ?1
                  and (?2 is null or e.entryDate >= ?2) and (?3 is null or e.entryDate <= ?3)
                 order by e.entryDate, e.seq""", PartnerLedgerEntry.class, partnerId, from, to);
        BigDecimal running = opening;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        List<StatementEntry> entries = new ArrayList<>();
        for (PartnerLedgerEntry r : rows) {
            running = running.add(r.debitBase).subtract(r.creditBase);
            debit = debit.add(r.debitBase);
            credit = credit.add(r.creditBase);
            entries.add(new StatementEntry(r.id, r.entryDate, r.dueDate, r.documentType, r.documentId, r.documentNumber, r.description, r.currencyCode,
                    r.exchangeRate, r.debit, r.credit, r.debitBase, r.creditBase, running, r.reversesId != null));
        }
        return new Statement(partner.id, partner.code, partner.title, base, from, to, opening, debit, credit, running, entries);
    }

    public BusinessPartner forDocument(UUID id, PartnerType[] allowed) {
        BusinessPartner partner = Guard.found(db.first("select p from BusinessPartner p where p.id = ?1", BusinessPartner.class, id), "Cari hesap bulunamadı");
        Guard.state(partner.isActive, partner.title + " pasif durumda");
        Guard.state(Arrays.asList(allowed).contains(partner.type),
                Arrays.asList(allowed).contains(PartnerType.CUSTOMER) ? partner.title + " bir müşteri değil" : partner.title + " bir tedarikçi değil");
        return partner;
    }
}
