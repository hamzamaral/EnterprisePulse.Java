package dev.enterprisepulse.accounting;

import static dev.enterprisepulse.accounting.AccountingMath.AccountMovement;
import static dev.enterprisepulse.accounting.AccountingMath.AccountRef;
import static dev.enterprisepulse.accounting.AccountingMath.AmountRow;
import static dev.enterprisepulse.accounting.AccountingMath.DraftLine;
import static dev.enterprisepulse.accounting.AccountingMath.PostingLine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.*;
import dev.enterprisepulse.platform.Extensibility.ScheduledJob;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Genel muhasebe: chart, manual journals, automatic postings, period lock and statements. */
@Service
@Transactional
public class AccountingService implements ScheduledJob {
    public static final String SETTINGS_KEY = "accounting";
    public static final String JOURNAL_SEQUENCE = "JOURNAL_ENTRY";

    public record SaveJournalLine(UUID accountId, UUID partnerId, UUID employeeId, String description, BigDecimal debit, BigDecimal credit) {}
    public record SaveJournalEntry(LocalDate entryDate, String description, List<SaveJournalLine> lines) {}
    public record ReverseJournalEntry(LocalDate date, String note) {}
    public record CreateLedgerAccount(String code, String name) {}
    public record UpdateLedgerAccount(String name, Boolean isActive) {}
    public record UpdateAccountingSettings(LocalDate lockedUntil) {}

    public record LedgerAccountDto(UUID id, String code, String name, LedgerAccountType type, UUID parentId, int level, boolean isSystem, boolean isActive,
                                   BigDecimal debit, BigDecimal credit, BigDecimal balance, boolean hasEntries, int childCount) {}
    public record LedgerRowDto(UUID entryId, String entryNumber, LocalDate entryDate, JournalSource source, String sourceNumber, String description, String lineDescription,
                               String accountCode, String partnerName, String employeeName, BigDecimal debit, BigDecimal credit, BigDecimal balance) {}
    public record AccountLedgerDto(LedgerAccountDto account, LocalDate from, LocalDate to, BigDecimal opening, List<LedgerRowDto> rows, BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal closing) {}
    public record JournalLineDto(UUID id, int lineNo, UUID accountId, String accountCode, String accountName, UUID partnerId, String partnerName, UUID employeeId,
                                 String employeeName, String description, BigDecimal debit, BigDecimal credit) {}
    public record EntryRef(UUID id, String number) {}
    public record JournalEntryDetailDto(UUID id, String number, LocalDate entryDate, JournalEntryStatus status, JournalSource source, UUID sourceId, String sourceNumber,
                                        String description, BigDecimal total, boolean isReversal, boolean isReversed, String createdByName, Instant postedAt, UUID documentId,
                                        List<JournalLineDto> lines, EntryRef reverses, EntryRef reversal, boolean locked, boolean canEdit, boolean canPost, boolean canReverse, boolean canDelete) {}
    public record PendingPostingDto(JournalSource source, UUID sourceId, String sourceNumber, String description, LocalDate date, BigDecimal amount, String action, boolean locked) {}
    public record PostingRunResultDto(int posted, int reversed, int skipped) {}
    public record TrialBalanceLine(UUID accountId, String code, String name, int level, BigDecimal periodDebit, BigDecimal periodCredit, BigDecimal totalDebit, BigDecimal totalCredit,
                                   BigDecimal balanceDebit, BigDecimal balanceCredit) {}
    public record TrialBalanceDto(LocalDate from, LocalDate to, List<TrialBalanceLine> rows, AccountingMath.TrialBalanceTotals totals) {}
    public record BalanceSheetDto(LocalDate date, List<AccountingMath.StatementClass> assets, List<AccountingMath.StatementClass> liabilities, BigDecimal totalAssets, BigDecimal totalLiabilities) {}
    public record IncomeStatementDto(LocalDate from, LocalDate to, List<AccountingMath.IncomeSection> sections, BigDecimal netSales, BigDecimal grossProfit, BigDecimal operatingProfit,
                                     BigDecimal ordinaryProfit, BigDecimal profitBeforeTax, BigDecimal netProfit) {}
    public record MonthResultDto(int month, BigDecimal revenue, BigDecimal expense, BigDecimal profit) {}
    public record AccountingSummaryDto(int year, String currencyCode, BigDecimal cash, BigDecimal receivables, BigDecimal payables, BigDecimal netSales, BigDecimal netProfit,
                                       List<MonthResultDto> months, int pendingCount, int entryCount, LocalDate lockedUntil, boolean chartInstalled) {}
    public record AccountingSettingsDto(LocalDate lockedUntil) {}
    public record OptionDto(UUID id, String code, String name) {}
    public record AccountingOptionsDto(List<OptionDto> accounts, List<OptionDto> partners, List<OptionDto> employees) {}
    public record InstallResult(int added) {}

    private record AccountSum(BigDecimal debit, BigDecimal credit, int lines) {}
    private record Candidate(JournalSource source, UUID sourceId, String sourceNumber, String description, LocalDate date, BigDecimal amount,
                             boolean wanted, LocalDate reverseDate, UUID ownerId, Supplier<List<DraftLine>> lines, boolean mutable) {
        Candidate(JournalSource source, UUID sourceId, String sourceNumber, String description, LocalDate date, BigDecimal amount,
                  boolean wanted, LocalDate reverseDate, UUID ownerId, Supplier<List<DraftLine>> lines) {
            this(source, sourceId, sourceNumber, description, date, amount, wanted, reverseDate, ownerId, lines, false);
        }
    }
    private record Planned(Candidate candidate, String action, UUID openEntryId, boolean locked) {}
    private record MovementCost(BigDecimal increase, BigDecimal decrease) {}

    private static final Map<TreasuryDocumentType, JournalSource> TREASURY_SOURCES = Map.of(
            TreasuryDocumentType.OPENING, JournalSource.TREASURY_OPENING,
            TreasuryDocumentType.CHEQUE_COLLECTION, JournalSource.CHEQUE,
            TreasuryDocumentType.CHEQUE_PAYMENT, JournalSource.CHEQUE,
            TreasuryDocumentType.EXPENSE_PAYMENT, JournalSource.EXPENSE_PAYMENT,
            TreasuryDocumentType.PAYROLL_PAYMENT, JournalSource.PAYROLL_PAYMENT);
    private static final List<String> REVENUE_PREFIXES = List.of("60", "61", "64", "67");

    private final Db db;
    private final NumberingService numbering;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public AccountingService(Db db, NumberingService numbering, ObjectMapper json, TransactionTemplate tx) {
        this.db = db;
        this.numbering = numbering;
        this.json = json;
        this.tx = tx;
    }

    @Override public String id() { return "accounting-posting"; }
    @Override public String cron() { return "0 30 2 * * *"; }

    private static CurrentUser ctx() { return CurrentUser.get(); }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run() {
        CurrentUser user = ctx();
        List<UUID> companies;
        try (var bypass = user.withoutTenant()) {
            companies = tx.execute(s -> db.list("select c.id from Company c", UUID.class));
        }
        for (UUID companyId : companies) {
            try (var scope = user.actAs(companyId, null, true)) {
                try { tx.executeWithoutResult(s -> runCompany()); }
                catch (RuntimeException ignored) { /* next company */ }
            }
        }
    }

    // ---------- Period lock ----------

    public LocalDate lockedUntil(UUID companyId) {
        Setting row = db.first("select s from Setting s where s.companyId = ?1 and s.key = ?2", Setting.class, companyId, SETTINGS_KEY).orElse(null);
        if (row == null || row.value == null || !row.value.hasNonNull("lockedUntil")) return null;
        return LocalDate.parse(row.value.get("lockedUntil").asText());
    }

    public static boolean isLocked(LocalDate date, LocalDate until) { return until != null && !date.isAfter(until); }

    private static void assertOpenPeriod(LocalDate date, LocalDate until) {
        Guard.that(!isLocked(date, until), "Dönem " + until + " tarihine kadar kilitli; daha ileri bir tarih seçin");
    }

    private void lockAccounting(UUID companyId) {
        db.nativeRows("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?1))) AS l", "accounting:" + companyId);
    }

    // ---------- Chart ----------

    public int installChart(UUID companyId) {
        UUID company = companyId != null ? companyId : ctx().requireCompanyId();
        Map<String, LedgerAccount> existing = new HashMap<>();
        try (var bypass = ctx().withoutTenant()) {
            for (LedgerAccount a : db.list("select a from LedgerAccount a where a.companyId = ?1", LedgerAccount.class, company)) existing.put(a.code, a);
        }
        int created = 0;
        for (AccountingChart.DefaultAccount account : AccountingChart.defaults()) {
            LedgerAccount row = existing.get(account.code());
            if (row != null) {
                if (account.isSystem() && (!row.isSystem || !row.isActive)) { row.isSystem = true; row.isActive = true; }
                continue;
            }
            LedgerAccount neu = new LedgerAccount();
            neu.companyId = company;
            neu.code = account.code();
            neu.name = account.name();
            neu.type = account.type();
            neu.isSystem = account.isSystem();
            db.add(neu);
            created++;
        }
        db.flush();
        return created;
    }

    public List<LedgerAccountDto> listAccounts() {
        List<LedgerAccount> accounts = db.list("select a from LedgerAccount a", LedgerAccount.class);
        Map<UUID, AccountSum> sums = accountSums();
        accounts.sort((a, b) -> AccountingMath.compareCodes(a.code, b.code));
        return accounts.stream().map(a -> toDto(a, accounts, sums)).toList();
    }

    public LedgerAccountDto getAccount(UUID id) {
        return listAccounts().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(() -> AppException.notFound("Hesap bulunamadı"));
    }

    public LedgerAccountDto createAccount(CreateLedgerAccount dto) {
        String code = dto.code() == null ? "" : dto.code().trim();
        Guard.that(AccountingChart.isValidCode(code), "Hesap kodu 100 veya 100.01 biçiminde olmalı");
        Guard.that(dto.name() != null && !dto.name().isBlank(), "Hesap adı gerekli");
        LedgerAccount parent = null;
        int dot = code.lastIndexOf('.');
        if (dot > 0) {
            String parentCode = code.substring(0, dot);
            parent = db.first("select a from LedgerAccount a where a.code = ?1", LedgerAccount.class, parentCode).orElse(null);
            Guard.that(parent != null, parentCode + " hesabı yok");
        }
        Guard.state(!db.exists("select a.id from LedgerAccount a where a.code = ?1", code), code + " hesabı zaten var");
        LedgerAccount row = new LedgerAccount();
        row.code = code;
        row.name = dto.name().trim();
        row.type = parent != null ? parent.type : AccountingChart.typeOf(code);
        row.parentId = parent == null ? null : parent.id;
        db.add(row);
        db.flush();
        return getAccount(row.id);
    }

    public LedgerAccountDto updateAccount(UUID id, UpdateLedgerAccount dto) {
        LedgerAccount account = Guard.found(db.first("select a from LedgerAccount a where a.id = ?1", LedgerAccount.class, id), "Hesap bulunamadı");
        Guard.state(!(Boolean.FALSE.equals(dto.isActive()) && account.isSystem), "Sistem hesapları otomatik kayıtlarda kullanılır");
        if (dto.name() != null && !dto.name().isBlank()) account.name = dto.name().trim();
        if (dto.isActive() != null) account.isActive = dto.isActive();
        db.flush();
        return getAccount(id);
    }

    public void removeAccount(UUID id) {
        LedgerAccount account = Guard.found(db.first("select a from LedgerAccount a where a.id = ?1", LedgerAccount.class, id), "Hesap bulunamadı");
        Guard.state(!account.isSystem, "Sistem hesapları silinemez");
        long children = db.count("select count(a) from LedgerAccount a where a.parentId = ?1", id);
        long lines = db.count("select count(l) from JournalLine l where l.accountId = ?1", id);
        Guard.state(children == 0 && lines == 0, "Hesabın alt hesabı veya kaydı var; pasifleştirin");
        db.remove(account);
    }

    public AccountLedgerDto ledger(UUID id, LocalDate fromDate, LocalDate toDate, UUID partnerId) {
        LedgerAccountDto account = getAccount(id);
        LocalDate to = toDate != null ? toDate : AccountingClock.today();
        LocalDate from = fromDate != null ? fromDate : LocalDate.of(to.getYear(), 1, 1);
        Guard.that(!from.isAfter(to), "Başlangıç tarihi bitişten sonra olamaz");
        List<LedgerAccount> all = db.list("select a from LedgerAccount a", LedgerAccount.class);
        List<UUID> accountIds = all.stream().filter(a -> AccountingMath.covers(account.code(), a.code)).map(a -> a.id).toList();
        List<JournalLine> before = db.list("""
                select l from JournalLine l, JournalEntry e where l.entryId = e.id and e.status = ?1 and l.accountId in ?2 and e.entryDate < ?3
                and (?4 is null or l.partnerId = ?4)
                """, JournalLine.class, JournalEntryStatus.POSTED, accountIds, from, partnerId);
        BigDecimal opening = before.stream().map(l -> l.debit.subtract(l.credit)).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<JournalLine> lines = db.list("""
                select l from JournalLine l, JournalEntry e where l.entryId = e.id and e.status = ?1 and l.accountId in ?2
                and e.entryDate >= ?3 and e.entryDate <= ?4 and (?5 is null or l.partnerId = ?5)
                order by e.entryDate, e.number, l.lineNo
                """, JournalLine.class, JournalEntryStatus.POSTED, accountIds, from, to, partnerId);
        BigDecimal balance = opening;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<LedgerRowDto> rows = new ArrayList<>();
        for (JournalLine line : lines) {
            JournalEntry entry = db.get(JournalEntry.class, line.entryId);
            LedgerAccount acc = db.get(LedgerAccount.class, line.accountId);
            balance = balance.add(line.debit.subtract(line.credit));
            totalDebit = totalDebit.add(line.debit);
            totalCredit = totalCredit.add(line.credit);
            String partnerName = line.partnerId == null ? null : db.get(BusinessPartner.class, line.partnerId).title;
            String employeeName = line.employeeId == null ? null : name(db.get(Employee.class, line.employeeId));
            rows.add(new LedgerRowDto(entry.id, entry.number, entry.entryDate, entry.source, entry.sourceNumber, entry.description, line.description,
                    acc.code, partnerName, employeeName, line.debit, line.credit, balance));
        }
        return new AccountLedgerDto(account, from, to, opening, rows, totalDebit, totalCredit, balance);
    }

    private Map<UUID, AccountSum> accountSums() {
        Map<UUID, AccountSum> map = new HashMap<>();
        for (Object[] row : db.list("""
                select l.accountId, sum(case when e.status = ?1 then l.debit else 0 end), sum(case when e.status = ?1 then l.credit else 0 end), count(l)
                from JournalLine l, JournalEntry e where l.entryId = e.id group by l.accountId
                """, Object[].class, JournalEntryStatus.POSTED)) {
            map.put((UUID) row[0], new AccountSum((BigDecimal) row[1], (BigDecimal) row[2], ((Number) row[3]).intValue()));
        }
        return map;
    }

    private static LedgerAccountDto toDto(LedgerAccount account, List<LedgerAccount> all, Map<UUID, AccountSum> sums) {
        List<AccountSum> own = all.stream().filter(a -> AccountingMath.covers(account.code, a.code)).map(a -> sums.get(a.id)).filter(Objects::nonNull).toList();
        BigDecimal debit = own.stream().map(AccountSum::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = own.stream().map(AccountSum::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LedgerAccountDto(account.id, account.code, account.name, account.type, account.parentId, account.code.split("\\.").length - 1,
                account.isSystem, account.isActive, debit, credit, debit.subtract(credit), own.stream().anyMatch(s -> s.lines() > 0),
                (int) all.stream().filter(a -> account.id.equals(a.parentId)).count());
    }

    // ---------- Journal ----------

    private JournalEntry writeEntry(UUID companyId, UUID userId, LocalDate date, JournalSource source, UUID sourceId, String sourceNumber,
                                    String description, List<DraftLine> draft, UUID reversesId) {
        List<PostingLine> lines = AccountingMath.normalizeLines(draft);
        BigDecimal[] totals = AccountingMath.lineTotals(lines);
        Guard.that(totals[0].signum() > 0 && totals[0].compareTo(totals[1]) == 0, "Fiş dengesiz: borç " + totals[0] + ", alacak " + totals[1]);
        List<String> codes = lines.stream().map(PostingLine::code).distinct().toList();
        Map<String, UUID> ids = new HashMap<>();
        try (var bypass = ctx().withoutTenant()) {
            for (LedgerAccount a : db.list("select a from LedgerAccount a where a.companyId = ?1 and a.code in ?2", LedgerAccount.class, companyId, codes)) {
                ids.put(a.code, a.id);
            }
        }
        String missing = codes.stream().filter(c -> !ids.containsKey(c)).findFirst().orElse(null);
        Guard.state(missing == null, missing + " hesabı hesap planında yok");
        JournalEntry entry = new JournalEntry();
        entry.companyId = companyId;
        entry.number = numbering.next(JOURNAL_SEQUENCE, companyId);
        entry.entryDate = date;
        entry.status = JournalEntryStatus.POSTED;
        entry.source = source;
        entry.sourceId = sourceId;
        entry.sourceNumber = sourceNumber;
        entry.description = description;
        entry.total = totals[0];
        entry.reversesId = reversesId;
        entry.postedAt = Instant.now();
        entry.createdById = userId;
        db.add(entry);
        db.flush();
        int no = 0;
        for (PostingLine line : lines) {
            JournalLine row = new JournalLine();
            row.entryId = entry.id;
            row.lineNo = ++no;
            row.accountId = ids.get(line.code());
            row.partnerId = line.partnerId();
            row.employeeId = line.employeeId();
            row.description = line.description();
            row.debit = line.debit();
            row.credit = line.credit();
            db.add(row);
        }
        db.flush();
        return entry;
    }

    private JournalEntry reverseEntry(UUID companyId, UUID userId, UUID entryId, LocalDate date, String note) {
        JournalEntry original;
        try (var bypass = ctx().withoutTenant()) {
            original = db.first("select e from JournalEntry e where e.id = ?1 and e.companyId = ?2", JournalEntry.class, entryId, companyId)
                    .orElseThrow(() -> AppException.notFound("Yevmiye kaydı bulunamadı"));
        }
        List<JournalLine> lines = db.list("select l from JournalLine l where l.entryId = ?1", JournalLine.class, entryId);
        String text = note == null ? null : note.trim();
        List<DraftLine> draft = new ArrayList<>();
        for (JournalLine line : lines) {
            LedgerAccount account = db.get(LedgerAccount.class, line.accountId);
            draft.add(new DraftLine(account.code, line.credit, line.debit, line.partnerId, line.employeeId, line.description));
        }
        return writeEntry(companyId, userId, date, original.source, original.sourceId, original.sourceNumber,
                "Ters kayıt: " + original.number + " " + original.description + (text == null || text.isEmpty() ? "" : " — " + text), draft, original.id);
    }

    public JournalEntryDetailDto getEntry(UUID id) {
        JournalEntry row = Guard.found(db.first("select e from JournalEntry e where e.id = ?1", JournalEntry.class, id), "Yevmiye kaydı bulunamadı");
        LocalDate until = lockedUntil(row.companyId);
        boolean locked = isLocked(row.entryDate, until);
        boolean manual = row.source == JournalSource.MANUAL;
        boolean draft = row.status == JournalEntryStatus.DRAFT;
        List<JournalLine> lines = db.list("select l from JournalLine l where l.entryId = ?1 order by l.lineNo", JournalLine.class, id);
        List<JournalLineDto> lineDtos = new ArrayList<>();
        for (JournalLine line : lines) {
            LedgerAccount account = db.get(LedgerAccount.class, line.accountId);
            String partnerName = line.partnerId == null ? null : db.get(BusinessPartner.class, line.partnerId).title;
            String employeeName = line.employeeId == null ? null : name(db.get(Employee.class, line.employeeId));
            lineDtos.add(new JournalLineDto(line.id, line.lineNo, line.accountId, account.code, account.name, line.partnerId, partnerName, line.employeeId,
                    employeeName, line.description, line.debit, line.credit));
        }
        User createdBy = db.get(User.class, row.createdById);
        JournalEntry reverses = row.reversesId == null ? null : db.find(JournalEntry.class, row.reversesId);
        JournalEntry reversal = db.first("select e from JournalEntry e where e.reversesId = ?1", JournalEntry.class, row.id).orElse(null);
        List<BigDecimal[]> amounts = lines.stream().map(l -> new BigDecimal[] {l.debit, l.credit}).toList();
        return new JournalEntryDetailDto(row.id, row.number, row.entryDate, row.status, row.source, row.sourceId, row.sourceNumber, row.description, row.total,
                row.reversesId != null, reversal != null, createdBy.firstName + " " + createdBy.lastName, row.postedAt, documentId(row), lineDtos,
                reverses == null ? null : new EntryRef(reverses.id, reverses.number),
                reversal == null ? null : new EntryRef(reversal.id, reversal.number),
                locked, draft && ctx().has("accounting:update"),
                draft && ctx().has("accounting:approve") && !locked && AccountingMath.isBalancedAmounts(amounts),
                !draft && manual && ctx().has("accounting:approve") && row.reversesId == null && reversal == null,
                draft && ctx().has("accounting:update"));
    }

    private UUID documentId(JournalEntry row) {
        if (row.sourceId == null) return null;
        return switch (row.source) {
            case MANUAL -> null;
            case TREASURY_OPENING, CHEQUE, EXPENSE_PAYMENT, PAYROLL_PAYMENT -> {
                TreasuryTransaction txRow = db.find(TreasuryTransaction.class, row.sourceId);
                if (txRow == null) yield null;
                yield row.source == JournalSource.TREASURY_OPENING ? txRow.accountId : txRow.documentId;
            }
            case PARTNER_OPENING -> {
                PartnerLedgerEntry e = db.find(PartnerLedgerEntry.class, row.sourceId);
                yield e == null ? null : e.partnerId;
            }
            case PRODUCTION -> {
                ProductionRecord r = db.find(ProductionRecord.class, row.sourceId);
                yield r == null ? null : r.workOrderId;
            }
            default -> row.sourceId;
        };
    }

    public JournalEntryDetailDto createEntry(SaveJournalEntry dto) {
        UUID companyId = ctx().requireCompanyId();
        UUID userId = ctx().requireUserId();
        Guard.that(dto.description() != null && !dto.description().isBlank(), "Açıklama gerekli");
        List<JournalLine> lines = validateLines(dto);
        JournalEntry entry = new JournalEntry();
        entry.companyId = companyId;
        entry.number = numbering.next(JOURNAL_SEQUENCE, companyId);
        entry.entryDate = dto.entryDate();
        entry.description = dto.description().trim();
        entry.total = lines.stream().map(l -> l.debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        entry.createdById = userId;
        db.add(entry);
        db.flush();
        for (JournalLine line : lines) { line.entryId = entry.id; db.add(line); }
        db.flush();
        return getEntry(entry.id);
    }

    public JournalEntryDetailDto updateEntry(UUID id, SaveJournalEntry dto) {
        Guard.that(dto.description() != null && !dto.description().isBlank(), "Açıklama gerekli");
        JournalEntry entry = lockDraft(id);
        for (JournalLine line : db.list("select l from JournalLine l where l.entryId = ?1", JournalLine.class, id)) db.remove(line);
        db.flush();
        List<JournalLine> lines = validateLines(dto);
        for (JournalLine line : lines) { line.entryId = id; db.add(line); }
        entry.entryDate = dto.entryDate();
        entry.description = dto.description().trim();
        entry.total = lines.stream().map(l -> l.debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        db.flush();
        return getEntry(id);
    }

    public void removeEntry(UUID id) {
        JournalEntry entry = lockDraft(id);
        for (JournalLine line : db.list("select l from JournalLine l where l.entryId = ?1", JournalLine.class, id)) db.remove(line);
        db.remove(entry);
    }

    public JournalEntryDetailDto postEntry(UUID id) {
        UUID companyId = ctx().requireCompanyId();
        lockAccounting(companyId);
        JournalEntry entry = lockDraft(id);
        assertOpenPeriod(entry.entryDate, lockedUntil(companyId));
        List<JournalLine> lines = db.list("select l from JournalLine l where l.entryId = ?1", JournalLine.class, id);
        List<BigDecimal[]> amounts = lines.stream().map(l -> new BigDecimal[] {l.debit, l.credit}).toList();
        BigDecimal[] totals = AccountingMath.lineTotalsFromAmounts(amounts);
        Guard.that(AccountingMath.isBalancedAmounts(amounts), "Borç " + totals[0] + " ile alacak " + totals[1] + " eşit olmalı");
        for (JournalLine line : lines) {
            LedgerAccount account = db.get(LedgerAccount.class, line.accountId);
            Guard.that(account.isActive, account.code + " hesabı pasif");
        }
        entry.status = JournalEntryStatus.POSTED;
        entry.postedAt = Instant.now();
        db.flush();
        return getEntry(id);
    }

    public JournalEntryDetailDto reverse(UUID id, ReverseJournalEntry dto) {
        UUID companyId = ctx().requireCompanyId();
        lockAccounting(companyId);
        db.nativeRows("SELECT id FROM journal_entries WHERE id = ?1 FOR UPDATE", id);
        JournalEntry entry = Guard.found(db.first("select e from JournalEntry e where e.id = ?1", JournalEntry.class, id), "Yevmiye kaydı bulunamadı");
        Guard.state(entry.status == JournalEntryStatus.POSTED, "Yalnızca kesinleşmiş fişler ters kaydedilir");
        Guard.state(entry.source == JournalSource.MANUAL, "Otomatik fişi ters kaydetmek için kaynak belgeyi iptal edin");
        Guard.state(entry.reversesId == null && !db.exists("select e.id from JournalEntry e where e.reversesId = ?1", id), "Fiş zaten ters kaydedilmiş");
        Guard.that(!dto.date().isBefore(entry.entryDate), "Ters kayıt fişten önceki bir tarihe atılamaz");
        assertOpenPeriod(dto.date(), lockedUntil(companyId));
        JournalEntry reversal = reverseEntry(companyId, ctx().requireUserId(), id, dto.date(), dto.note());
        return getEntry(reversal.id);
    }

    private JournalEntry lockDraft(UUID id) {
        db.nativeRows("SELECT id FROM journal_entries WHERE id = ?1 FOR UPDATE", id);
        JournalEntry entry = Guard.found(db.first("select e from JournalEntry e where e.id = ?1", JournalEntry.class, id), "Yevmiye kaydı bulunamadı");
        Guard.state(entry.status == JournalEntryStatus.DRAFT && entry.source == JournalSource.MANUAL, "Yalnızca taslak fişler değiştirilebilir");
        return entry;
    }

    private List<JournalLine> validateLines(SaveJournalEntry dto) {
        Guard.that(dto.lines() != null && !dto.lines().isEmpty(), "En az bir satır gerekli");
        for (int i = 0; i < dto.lines().size(); i++) {
            SaveJournalLine line = dto.lines().get(i);
            BigDecimal debit = nz(line.debit());
            BigDecimal credit = nz(line.credit());
            Guard.that(debit.signum() >= 0 && credit.signum() >= 0 && (debit.signum() > 0) != (credit.signum() > 0),
                    "Satır " + (i + 1) + ": borç veya alacaktan yalnız biri girilmeli");
        }
        List<UUID> accountIds = dto.lines().stream().map(SaveJournalLine::accountId).distinct().toList();
        List<UUID> partnerIds = dto.lines().stream().map(SaveJournalLine::partnerId).filter(Objects::nonNull).distinct().toList();
        List<UUID> employeeIds = dto.lines().stream().map(SaveJournalLine::employeeId).filter(Objects::nonNull).distinct().toList();
        Guard.that(db.count("select count(a) from LedgerAccount a where a.id in ?1 and a.isActive = true", accountIds) == accountIds.size(), "Bu şirketin aktif hesaplarını seçin");
        Guard.that(partnerIds.isEmpty() || db.count("select count(p) from BusinessPartner p where p.id in ?1", partnerIds) == partnerIds.size(), "Cari bulunamadı");
        Guard.that(employeeIds.isEmpty() || db.count("select count(e) from Employee e where e.id in ?1", employeeIds) == employeeIds.size(), "Personel bulunamadı");
        List<JournalLine> rows = new ArrayList<>();
        for (int i = 0; i < dto.lines().size(); i++) {
            SaveJournalLine line = dto.lines().get(i);
            JournalLine row = new JournalLine();
            row.lineNo = i + 1;
            row.accountId = line.accountId();
            row.partnerId = line.partnerId();
            row.employeeId = line.employeeId();
            row.description = line.description() == null || line.description().isBlank() ? null : line.description().trim();
            row.debit = nz(line.debit());
            row.credit = nz(line.credit());
            rows.add(row);
        }
        return rows;
    }

    // ---------- Posting run ----------

    public List<PendingPostingDto> pending() {
        return plan(ctx().requireCompanyId()).stream()
                .map(p -> new PendingPostingDto(p.candidate().source(), p.candidate().sourceId(), p.candidate().sourceNumber(), p.candidate().description(),
                        "REVERSE".equals(p.action()) ? p.candidate().reverseDate() : p.candidate().date(), p.candidate().amount(), p.action(), p.locked()))
                .sorted(Comparator.comparing(PendingPostingDto::date).thenComparing(PendingPostingDto::sourceNumber))
                .toList();
    }

    public PostingRunResultDto runCompany() {
        UUID companyId = ctx().requireCompanyId();
        installChart(companyId);
        List<Planned> plan = plan(companyId);
        int posted = 0, reversed = 0, skipped = 0;
        for (Planned item : plan) {
            if (item.locked()) { skipped++; continue; }
            int[] outcome = apply(companyId, item);
            if (outcome == null) skipped++;
            else { posted += outcome[0]; reversed += outcome[1]; }
        }
        return new PostingRunResultDto(posted, reversed, skipped);
    }

    private List<Planned> plan(UUID companyId) {
        List<Candidate> candidates = collect(companyId);
        List<JournalEntry> entries = db.list("select e from JournalEntry e where e.status = ?1 and e.source <> ?2", JournalEntry.class, JournalEntryStatus.POSTED, JournalSource.MANUAL);
        LocalDate until = lockedUntil(companyId);
        Set<UUID> reversed = entries.stream().map(e -> e.reversesId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<String, UUID> open = new HashMap<>();
        for (JournalEntry e : entries) {
            if (e.sourceId == null || e.reversesId != null || reversed.contains(e.id)) continue;
            open.put(key(e.source, e.sourceId), e.id);
        }
        List<Planned> plan = new ArrayList<>();
        for (Candidate candidate : candidates) {
            UUID openId = open.get(key(candidate.source(), candidate.sourceId()));
            String action = null;
            if (candidate.wanted() && openId == null) action = "POST";
            else if (!candidate.wanted() && openId != null) action = "REVERSE";
            else if (candidate.wanted() && openId != null && candidate.mutable() && changed(openId, candidate)) action = "REPOST";
            if (action == null) continue;
            boolean locked = (!"POST".equals(action) && isLocked(candidate.reverseDate(), until)) || (!"REVERSE".equals(action) && isLocked(candidate.date(), until));
            plan.add(new Planned(candidate, action, openId, locked));
        }
        return plan;
    }

    private boolean changed(UUID entryId, Candidate candidate) {
        List<DraftLine> draft = candidate.lines().get();
        if (draft == null) return false;
        List<PostingLine> posted = new ArrayList<>();
        for (JournalLine line : db.list("select l from JournalLine l where l.entryId = ?1", JournalLine.class, entryId)) {
            LedgerAccount account = db.get(LedgerAccount.class, line.accountId);
            posted.add(new PostingLine(account.code, line.debit, line.credit, line.partnerId, line.employeeId, line.description));
        }
        return !AccountingMath.fingerprint(posted).equals(AccountingMath.fingerprint(AccountingMath.normalizeLines(draft)));
    }

    private int[] apply(UUID companyId, Planned item) {
        Candidate candidate = item.candidate();
        List<DraftLine> lines = "REVERSE".equals(item.action()) ? List.of() : candidate.lines().get();
        if (!"REVERSE".equals(item.action()) && (lines == null || AccountingMath.normalizeLines(lines).isEmpty())) return null;
        lockAccounting(companyId);
        List<JournalEntry> current = db.list("select e from JournalEntry e where e.source = ?1 and e.sourceId = ?2 and e.status = ?3",
                JournalEntry.class, candidate.source(), candidate.sourceId(), JournalEntryStatus.POSTED);
        Set<UUID> reversedIds = current.stream().map(e -> e.reversesId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        JournalEntry openEntry = current.stream().filter(e -> e.reversesId == null && !reversedIds.contains(e.id)).findFirst().orElse(null);
        UUID openId = openEntry == null ? null : openEntry.id;
        if (!Objects.equals(openId, item.openEntryId())) return null;
        UUID actor = ctx().userId != null ? ctx().userId : candidate.ownerId();
        int posted = 0, reversed = 0;
        if (openEntry != null) {
            reverseEntry(companyId, actor, openEntry.id, "REPOST".equals(item.action()) ? AccountingClock.today() : candidate.reverseDate(), null);
            reversed++;
        }
        if (!"REVERSE".equals(item.action())) {
            writeEntry(companyId, actor, candidate.date(), candidate.source(), candidate.sourceId(), candidate.sourceNumber(), candidate.description(), lines, null);
            posted++;
        }
        return new int[] {posted, reversed};
    }

    private static String key(JournalSource source, UUID sourceId) { return source + ":" + sourceId; }

    private List<Candidate> collect(UUID companyId) {
        Company company = db.get(Company.class, companyId);
        Map<UUID, MovementCost> costs = movementCosts();
        List<Candidate> all = new ArrayList<>();
        all.addAll(salesInvoices());
        all.addAll(salesReturns(costs));
        all.addAll(purchaseInvoices());
        all.addAll(deliveries(costs));
        all.addAll(stockMovements(costs));
        all.addAll(payments());
        all.addAll(treasury(company.baseCurrency));
        all.addAll(partnerOpenings());
        all.addAll(expenseReports());
        all.addAll(payrolls());
        all.addAll(production());
        return all;
    }

    private List<Candidate> salesInvoices() {
        List<Candidate> out = new ArrayList<>();
        for (SalesInvoice i : db.list("select i from SalesInvoice i where i.status in ?1", SalesInvoice.class, List.of(InvoiceStatus.ISSUED, InvoiceStatus.CANCELLED))) {
            BusinessPartner partner = db.get(BusinessPartner.class, i.partnerId);
            BigDecimal total = AccountingMath.toBase(i.grandTotal, i.exchangeRate);
            BigDecimal vat = AccountingMath.toBase(i.vatTotal, i.exchangeRate);
            out.add(new Candidate(JournalSource.SALES_INVOICE, i.id, i.number, "Satış faturası — " + partner.title, i.invoiceDate, total,
                    i.status == InvoiceStatus.ISSUED, AccountingClock.localDate(i.cancelledAt != null ? i.cancelledAt : Instant.now()), i.createdById,
                    () -> PostingRules.salesInvoice(i.partnerId, total, vat)));
        }
        return out;
    }

    private List<Candidate> salesReturns(Map<UUID, MovementCost> costs) {
        List<Candidate> out = new ArrayList<>();
        for (SalesReturn r : db.list("select r from SalesReturn r", SalesReturn.class)) {
            BusinessPartner partner = db.get(BusinessPartner.class, r.partnerId);
            BigDecimal total = AccountingMath.toBase(r.grandTotal, r.exchangeRate);
            BigDecimal vat = AccountingMath.toBase(r.vatTotal, r.exchangeRate);
            BigDecimal cost = r.stockMovementId != null && costs.containsKey(r.stockMovementId) ? costs.get(r.stockMovementId).increase() : BigDecimal.ZERO;
            out.add(new Candidate(JournalSource.SALES_RETURN, r.id, r.number, "Satış iadesi — " + partner.title, r.returnDate, total, true, r.returnDate, r.createdById,
                    () -> PostingRules.salesReturn(r.partnerId, total, vat, cost)));
        }
        return out;
    }

    private List<Candidate> purchaseInvoices() {
        List<Candidate> out = new ArrayList<>();
        for (PurchaseInvoice i : db.list("select i from PurchaseInvoice i where i.status in ?1", PurchaseInvoice.class, List.of(InvoiceStatus.ISSUED, InvoiceStatus.CANCELLED))) {
            BusinessPartner partner = db.get(BusinessPartner.class, i.partnerId);
            BigDecimal total = AccountingMath.toBase(i.grandTotal, i.exchangeRate);
            BigDecimal vat = AccountingMath.toBase(i.vatTotal, i.exchangeRate);
            BigDecimal goods = BigDecimal.ZERO;
            for (PurchaseInvoiceLine line : db.list("select l from PurchaseInvoiceLine l where l.invoiceId = ?1", PurchaseInvoiceLine.class, i.id)) {
                if (line.variantId == null) continue;
                ProductVariant variant = db.find(ProductVariant.class, line.variantId);
                if (variant == null) continue;
                Product product = db.get(Product.class, variant.productId);
                if (product.type == ProductType.GOODS) {
                    goods = goods.add(DocumentMath.line(new DocumentMath.LineInput(line.quantity, line.unitPrice, line.discountRate, line.vatRate)).net());
                }
            }
            goods = AccountingMath.toBase(goods, i.exchangeRate);
            BigDecimal goodsFinal = goods;
            out.add(new Candidate(JournalSource.PURCHASE_INVOICE, i.id, i.number, "Alış faturası " + i.supplierInvoiceNo + " — " + partner.title, i.invoiceDate, total,
                    i.status == InvoiceStatus.ISSUED, AccountingClock.localDate(i.cancelledAt != null ? i.cancelledAt : Instant.now()), i.createdById,
                    () -> PostingRules.purchaseInvoice(i.partnerId, total, vat, goodsFinal)));
        }
        return out;
    }

    private List<Candidate> deliveries(Map<UUID, MovementCost> costs) {
        List<Candidate> out = new ArrayList<>();
        for (DeliveryNote d : db.list("select d from DeliveryNote d where d.stockMovementId is not null", DeliveryNote.class)) {
            BusinessPartner partner = db.get(BusinessPartner.class, d.partnerId);
            BigDecimal cost = costs.containsKey(d.stockMovementId) ? costs.get(d.stockMovementId).decrease() : BigDecimal.ZERO;
            out.add(new Candidate(JournalSource.DELIVERY_NOTE, d.id, d.number, "Satılan malın maliyeti — " + partner.title, d.deliveryDate, cost, true, d.deliveryDate, d.createdById,
                    () -> PostingRules.deliveryCost(cost)));
        }
        return out;
    }

    private List<Candidate> stockMovements(Map<UUID, MovementCost> costs) {
        Set<UUID> linked = new HashSet<>();
        db.list("select g.stockMovementId from GoodsReceipt g where g.stockMovementId is not null", UUID.class).forEach(linked::add);
        db.list("select d.stockMovementId from DeliveryNote d where d.stockMovementId is not null", UUID.class).forEach(linked::add);
        db.list("select r.stockMovementId from SalesReturn r where r.stockMovementId is not null", UUID.class).forEach(linked::add);
        db.list("select r.issueMovementId from ProductionRecord r where r.issueMovementId is not null", UUID.class).forEach(linked::add);
        db.list("select r.receiptMovementId from ProductionRecord r where r.receiptMovementId is not null", UUID.class).forEach(linked::add);
        List<Candidate> out = new ArrayList<>();
        for (StockMovement m : db.list("select m from StockMovement m where m.type <> ?1", StockMovement.class, StockMovementType.TRANSFER)) {
            if (linked.contains(m.id)) continue;
            MovementCost value = costs.getOrDefault(m.id, new MovementCost(BigDecimal.ZERO, BigDecimal.ZERO));
            String label = switch (m.type) {
                case RECEIPT -> "Faturasız stok girişi";
                case ISSUE -> "Stok çıkışı (sarf)";
                case ADJUSTMENT -> "Stok sayım farkı";
                default -> "Depo transferi";
            };
            String description = m.reference == null || m.reference.isBlank() ? label : label + " — " + m.reference;
            out.add(new Candidate(JournalSource.STOCK_MOVEMENT, m.id, m.number, description, m.movementDate, value.increase().add(value.decrease()), true, m.movementDate, m.createdById,
                    () -> PostingRules.stockMovement(m.type, value.increase(), value.decrease())));
        }
        return out;
    }

    private List<Candidate> payments() {
        List<Candidate> out = new ArrayList<>();
        for (PartnerPayment p : db.list("select p from PartnerPayment p", PartnerPayment.class)) {
            BusinessPartner partner = db.get(BusinessPartner.class, p.partnerId);
            boolean incoming = p.direction == PaymentDirection.IN;
            boolean endorsed = db.exists("select c.id from Cheque c where c.endorsementPaymentId = ?1", p.id);
            out.add(new Candidate(incoming ? JournalSource.COLLECTION : JournalSource.PAYMENT, p.id, p.number, (incoming ? "Tahsilat" : "Ödeme") + " — " + partner.title,
                    p.paymentDate, p.amountBase, p.status == PaymentRecordStatus.POSTED,
                    AccountingClock.localDate(p.cancelledAt != null ? p.cancelledAt : Instant.now()), p.createdById,
                    () -> incoming ? PostingRules.collection(p.partnerId, p.method, p.amountBase) : PostingRules.payment(p.partnerId, p.method, p.amountBase, endorsed)));
        }
        return out;
    }

    private List<Candidate> treasury(String baseCurrency) {
        List<TreasuryDocumentType> types = new ArrayList<>(TREASURY_SOURCES.keySet());
        List<Candidate> out = new ArrayList<>();
        for (TreasuryTransaction t : db.list("select t from TreasuryTransaction t where t.reversesId is null and t.documentType in ?1", TreasuryTransaction.class, types)) {
            TreasuryAccount account = db.get(TreasuryAccount.class, t.accountId);
            BigDecimal signed = t.amountIn.subtract(t.amountOut);
            Cheque cheque = t.documentId != null && (t.documentType == TreasuryDocumentType.CHEQUE_COLLECTION || t.documentType == TreasuryDocumentType.CHEQUE_PAYMENT)
                    ? db.find(Cheque.class, t.documentId) : null;
            ExpenseReport report = t.documentId != null && t.documentType == TreasuryDocumentType.EXPENSE_PAYMENT ? db.find(ExpenseReport.class, t.documentId) : null;
            LocalDate reversalDate = null;
            if (db.exists("select x.id from TreasuryTransaction x where x.reversesId = ?1", t.id)) {
                TreasuryTransaction rev = db.first("select x from TreasuryTransaction x where x.reversesId = ?1", TreasuryTransaction.class, t.id).orElse(null);
                if (rev != null) reversalDate = rev.txDate;
            }
            boolean needsCheque = t.documentType == TreasuryDocumentType.CHEQUE_COLLECTION || t.documentType == TreasuryDocumentType.CHEQUE_PAYMENT;
            String kindLabel = cheque != null && cheque.kind == ChequeKind.NOTE ? "Senet" : "Çek";
            String description = switch (t.documentType) {
                case OPENING -> "Açılış bakiyesi — " + account.name;
                case CHEQUE_COLLECTION -> kindLabel + " tahsili — " + (cheque == null ? "" : db.get(BusinessPartner.class, cheque.partnerId).title);
                case CHEQUE_PAYMENT -> kindLabel + " ödemesi — " + (cheque == null ? "" : db.get(BusinessPartner.class, cheque.partnerId).title);
                case EXPENSE_PAYMENT -> "Masraf ödemesi — " + (report == null ? "" : name(db.get(Employee.class, report.employeeId)));
                default -> "Net maaş ödemesi";
            };
            LocalDate reverse = reversalDate != null ? reversalDate : AccountingClock.today();
            out.add(new Candidate(TREASURY_SOURCES.get(t.documentType), t.id, t.documentNumber, description.trim().replaceAll(" —$", ""), t.txDate, signed.abs(),
                    reversalDate == null, reverse, t.createdById, () -> {
                        if (needsCheque && cheque == null) return null;
                        BigDecimal amount = account.currencyCode.equals(baseCurrency) ? signed : null;
                        if (amount == null) {
                            BigDecimal rate = cheque != null ? cheque.exchangeRate : rateOn(account.currencyCode, t.txDate);
                            if (rate == null) return null;
                            amount = AccountingMath.toBase(signed, rate);
                        }
                        return switch (t.documentType) {
                            case OPENING -> PostingRules.treasuryOpening(account.type, amount);
                            case CHEQUE_COLLECTION -> PostingRules.chequeCollection(cheque.kind, account.type, amount, cheque.partnerId);
                            case CHEQUE_PAYMENT -> PostingRules.chequePayment(cheque.kind, account.type, amount.negate(), cheque.partnerId);
                            case EXPENSE_PAYMENT -> PostingRules.employeePayment(account.type, amount.negate(), report == null ? null : report.employeeId);
                            default -> PostingRules.employeePayment(account.type, amount.negate(), null);
                        };
                    }));
        }
        return out;
    }

    private List<Candidate> partnerOpenings() {
        List<Candidate> out = new ArrayList<>();
        for (PartnerLedgerEntry e : db.list("select e from PartnerLedgerEntry e where e.documentType = ?1 and e.reversesId is null", PartnerLedgerEntry.class, LedgerDocumentType.OPENING)) {
            BusinessPartner partner = db.get(BusinessPartner.class, e.partnerId);
            LocalDate reversalDate = null;
            PartnerLedgerEntry rev = db.first("select x from PartnerLedgerEntry x where x.reversesId = ?1", PartnerLedgerEntry.class, e.id).orElse(null);
            if (rev != null) reversalDate = rev.entryDate;
            LocalDate reverse = reversalDate != null ? reversalDate : AccountingClock.today();
            out.add(new Candidate(JournalSource.PARTNER_OPENING, e.id, e.documentNumber, "Cari açılış bakiyesi — " + partner.title, e.entryDate, e.debitBase.add(e.creditBase),
                    reversalDate == null, reverse, e.createdById, () -> PostingRules.partnerOpening(e.partnerId, e.debitBase, e.creditBase)));
        }
        return out;
    }

    private List<Candidate> expenseReports() {
        List<Candidate> out = new ArrayList<>();
        for (ExpenseReport r : db.list("select r from ExpenseReport r where r.decidedAt is not null", ExpenseReport.class)) {
            Employee employee = db.get(Employee.class, r.employeeId);
            BigDecimal vat = BigDecimal.ZERO;
            for (ExpenseLine line : db.list("select l from ExpenseLine l where l.reportId = ?1", ExpenseLine.class, r.id)) {
                vat = vat.add(AccountingMath.toBase(line.vatAmount, line.exchangeRate));
            }
            BigDecimal vatFinal = vat;
            out.add(new Candidate(JournalSource.EXPENSE_REPORT, r.id, r.number, "Masraf formu — " + name(employee), AccountingClock.localDate(r.decidedAt), r.totalAmount,
                    r.status == ExpenseReportStatus.APPROVED || r.status == ExpenseReportStatus.PAID, AccountingClock.localDate(r.updatedAt), r.createdById,
                    () -> PostingRules.expenseReport(r.employeeId, r.totalAmount, vatFinal)));
        }
        return out;
    }

    private List<Candidate> payrolls() {
        LocalDate today = AccountingClock.today();
        List<Candidate> out = new ArrayList<>();
        for (PayrollPeriod r : db.list("select p from PayrollPeriod p where p.imported = false", PayrollPeriod.class)) {
            LocalDate monthEnd = YearMonth.of(r.year, r.month).atEndOfMonth();
            LocalDate approved = r.approvedAt != null ? AccountingClock.localDate(r.approvedAt) : today;
            out.add(new Candidate(JournalSource.PAYROLL, r.id, r.number, String.format("%02d/%d bordro tahakkuku", r.month, r.year),
                    monthEnd.isAfter(today) ? approved : monthEnd, r.employerCostTotal,
                    r.status == PayrollPeriodStatus.APPROVED || r.status == PayrollPeriodStatus.PAID,
                    AccountingClock.localDate(r.updatedAt), r.approvedById != null ? r.approvedById : r.createdById,
                    () -> PostingRules.payroll(r.netTotal, r.taxTotal, r.sgkTotal, r.deductionTotal), true));
        }
        return out;
    }

    private List<Candidate> production() {
        List<Candidate> out = new ArrayList<>();
        for (ProductionRecord r : db.list("select r from ProductionRecord r", ProductionRecord.class)) {
            WorkOrder order = db.get(WorkOrder.class, r.workOrderId);
            out.add(new Candidate(JournalSource.PRODUCTION, r.id, r.number, "Üretim — iş emri " + order.number, r.recordDate,
                    r.materialCost.add(r.laborCost).add(r.overheadCost), true, r.recordDate, r.createdById,
                    () -> PostingRules.production(r.materialCost, r.laborCost, r.overheadCost)));
        }
        return out;
    }

    private Map<UUID, MovementCost> movementCosts() {
        Map<UUID, MovementCost> map = new HashMap<>();
        for (Object[] row : db.list("""
                select t.movementId,
                       sum(case when t.quantity > 0 then t.quantity * t.unitCost else 0 end),
                       sum(case when t.quantity < 0 then -t.quantity * t.unitCost else 0 end)
                from StockTransaction t group by t.movementId
                """, Object[].class)) {
            map.put((UUID) row[0], new MovementCost(DocumentMath.round2((BigDecimal) row[1]), DocumentMath.round2((BigDecimal) row[2])));
        }
        return map;
    }

    private BigDecimal rateOn(String currencyCode, LocalDate date) {
        ExchangeRate rate = db.first("select r from ExchangeRate r where r.currencyCode = ?1 and r.rateDate <= ?2 order by r.rateDate desc", ExchangeRate.class, currencyCode, date)
                .orElseGet(() -> db.first("select r from ExchangeRate r where r.currencyCode = ?1 and r.rateDate > ?2 order by r.rateDate", ExchangeRate.class, currencyCode, date).orElse(null));
        return rate == null ? null : rate.selling;
    }

    // ---------- Reports ----------

    private LocalDate[] range(LocalDate fromDate, LocalDate toDate) {
        LocalDate to = toDate != null ? toDate : AccountingClock.today();
        LocalDate from = fromDate != null ? fromDate : LocalDate.of(to.getYear(), 1, 1);
        Guard.that(!from.isAfter(to), "Başlangıç tarihi bitişten sonra olamaz");
        return new LocalDate[] {from, to};
    }

    public TrialBalanceDto trialBalance(LocalDate fromDate, LocalDate toDate, boolean detailed) {
        LocalDate[] r = range(fromDate, toDate);
        List<AccountMovement> sums = sums(r[0], r[1]);
        List<LedgerAccount> accounts = db.list("select a from LedgerAccount a", LedgerAccount.class);
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (LedgerAccount a : accounts) ids.putIfAbsent(a.code, a.id);
        AccountingMath.TrialBalanceResult result = AccountingMath.trialBalance(accounts.stream().map(a -> new AccountRef(a.code, a.name)).toList(), sums, detailed);
        List<TrialBalanceLine> rows = result.rows().stream().map(row -> new TrialBalanceLine(ids.get(row.code()), row.code(), row.name(), row.level(),
                row.periodDebit(), row.periodCredit(), row.totalDebit(), row.totalCredit(), row.balanceDebit(), row.balanceCredit())).toList();
        return new TrialBalanceDto(r[0], r[1], rows, result.totals());
    }

    public BalanceSheetDto balanceSheet(LocalDate dateValue) {
        LocalDate date = dateValue != null ? dateValue : AccountingClock.today();
        LocalDate yearStart = fiscalYearStart(date);
        List<AccountMovement> all = sums(yearStart, date);
        Map<String, String> names = mainNames();
        List<AmountRow> balances = mainRows(all, names, s -> s.openingDebit().add(s.periodDebit()).subtract(s.openingCredit()).subtract(s.periodCredit()));
        BigDecimal current = all.stream().filter(s -> AccountingChart.isResultAccount(s.code())).map(s -> s.periodCredit().subtract(s.periodDebit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prior = all.stream().filter(s -> AccountingChart.isResultAccount(s.code())).map(s -> s.openingCredit().subtract(s.openingDebit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        AccountingMath.BalanceSheet sheet = AccountingMath.balanceSheetOf(balances, current, prior);
        return new BalanceSheetDto(date, sheet.assets(), sheet.liabilities(), sheet.totalAssets(), sheet.totalLiabilities());
    }

    public IncomeStatementDto incomeStatement(LocalDate fromDate, LocalDate toDate) {
        LocalDate[] r = range(fromDate, toDate);
        List<AccountMovement> sums = sums(r[0], r[1]);
        Map<String, String> names = mainNames();
        AccountingMath.IncomeStatement statement = AccountingMath.incomeStatementOf(mainRows(sums.stream().filter(s -> AccountingChart.isResultAccount(s.code())).toList(), names,
                s -> s.periodCredit().subtract(s.periodDebit())));
        return new IncomeStatementDto(r[0], r[1], statement.sections(), statement.netSales(), statement.grossProfit(), statement.operatingProfit(),
                statement.ordinaryProfit(), statement.profitBeforeTax(), statement.netProfit());
    }

    public AccountingSummaryDto summary(Integer year) {
        UUID companyId = ctx().requireCompanyId();
        LocalDate today = AccountingClock.today();
        int y = year != null ? year : today.getYear();
        LocalDate start = LocalDate.of(y, 1, 1);
        LocalDate end = LocalDate.of(y, 12, 31);
        LocalDate balanceDate = end.isBefore(today) ? end : today;
        Company company = db.get(Company.class, companyId);
        List<AccountMovement> balances = sums(balanceDate, balanceDate);
        List<PendingPostingDto> pendingRows = pending();
        long entries = db.count("select count(e) from JournalEntry e where e.entryDate >= ?1 and e.entryDate <= ?2", start, end);
        long accountCount = db.count("select count(a) from LedgerAccount a");
        LocalDate until = lockedUntil(companyId);
        List<Object[]> lines = db.list("""
                select e.entryDate, a.code, l.debit, l.credit from JournalLine l, JournalEntry e, LedgerAccount a
                where l.entryId = e.id and l.accountId = a.id and e.status = ?1 and e.entryDate >= ?2 and e.entryDate <= ?3
                and (a.code like '6%' or a.code like '7%')
                """, Object[].class, JournalEntryStatus.POSTED, start, end);
        Map<String, BigDecimal[]> months = new HashMap<>();
        for (Object[] row : lines) {
            int month = ((LocalDate) row[0]).getMonthValue();
            String prefix = ((String) row[1]).substring(0, 2);
            String key = month + "|" + prefix;
            BigDecimal[] sum = months.computeIfAbsent(key, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            sum[0] = sum[0].add((BigDecimal) row[2]);
            sum[1] = sum[1].add((BigDecimal) row[3]);
        }
        List<MonthResultDto> monthly = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal[]> e : months.entrySet()) {
                if (!e.getKey().startsWith(m + "|")) continue;
                String prefix = e.getKey().substring(e.getKey().indexOf('|') + 1);
                BigDecimal debit = e.getValue()[0];
                BigDecimal credit = e.getValue()[1];
                if (REVENUE_PREFIXES.contains(prefix)) revenue = revenue.add(credit.subtract(debit));
                else expense = expense.add(debit.subtract(credit));
            }
            monthly.add(new MonthResultDto(m, revenue, expense, revenue.subtract(expense)));
        }
        BigDecimal netSales = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> e : months.entrySet()) {
            String prefix = e.getKey().substring(e.getKey().indexOf('|') + 1);
            if ("60".equals(prefix) || "61".equals(prefix)) netSales = netSales.add(e.getValue()[1].subtract(e.getValue()[0]));
        }
        return new AccountingSummaryDto(y, company.baseCurrency, balance("10", 1, balances), balance("12", 1, balances), balance("32", -1, balances),
                netSales, monthly.stream().map(MonthResultDto::profit).reduce(BigDecimal.ZERO, BigDecimal::add), monthly,
                pendingRows.size(), (int) entries, until, accountCount > 0);
    }

    private static BigDecimal balance(String prefix, int sign, List<AccountMovement> balances) {
        return balances.stream().filter(s -> s.code().startsWith(prefix))
                .map(s -> s.openingDebit().add(s.periodDebit()).subtract(s.openingCredit()).subtract(s.periodCredit()).multiply(BigDecimal.valueOf(sign)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public AccountingSettingsDto settings() { return new AccountingSettingsDto(lockedUntil(ctx().requireCompanyId())); }

    public AccountingSettingsDto updateSettings(UpdateAccountingSettings dto) {
        UUID companyId = ctx().requireCompanyId();
        Guard.that(dto.lockedUntil() == null || !dto.lockedUntil().isAfter(AccountingClock.today()), "Kilit tarihi ileri bir tarih olamaz");
        ObjectNode value = json.createObjectNode();
        if (dto.lockedUntil() == null) value.putNull("lockedUntil");
        else value.put("lockedUntil", dto.lockedUntil().toString());
        Setting row = db.first("select s from Setting s where s.key = ?1", Setting.class, SETTINGS_KEY).orElse(null);
        if (row == null) {
            row = new Setting();
            row.companyId = companyId;
            row.key = SETTINGS_KEY;
            row.value = value;
            db.add(row);
        } else row.value = value;
        db.flush();
        return new AccountingSettingsDto(dto.lockedUntil());
    }

    public AccountingOptionsDto options() {
        List<OptionDto> accounts = db.list("select a from LedgerAccount a where a.isActive = true", LedgerAccount.class).stream()
                .sorted((a, b) -> AccountingMath.compareCodes(a.code, b.code))
                .map(a -> new OptionDto(a.id, a.code, a.name)).toList();
        List<OptionDto> partners = db.list("select p from BusinessPartner p where p.isActive = true order by p.title", BusinessPartner.class).stream()
                .map(p -> new OptionDto(p.id, p.code, p.title)).toList();
        List<OptionDto> employees = db.list("select e from Employee e where e.status = ?1 order by e.firstName, e.lastName", Employee.class, EmployeeStatus.ACTIVE).stream()
                .map(e -> new OptionDto(e.id, e.employeeNo, name(e))).toList();
        return new AccountingOptionsDto(accounts, partners, employees);
    }

    private Map<String, String> mainNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (LedgerAccount a : db.list("select a from LedgerAccount a where a.parentId is null", LedgerAccount.class)) names.putIfAbsent(a.code, a.name);
        return names;
    }

    private LocalDate fiscalYearStart(LocalDate date) {
        Setting general = db.first("select s from Setting s where s.key = 'general'", Setting.class).orElse(null);
        int month = 1;
        if (general != null && general.value != null && general.value.has("fiscalYearStartMonth")) month = general.value.get("fiscalYearStartMonth").asInt(1);
        int year = date.getMonthValue() >= month ? date.getYear() : date.getYear() - 1;
        return LocalDate.of(year, month, 1);
    }

    private List<AccountMovement> sums(LocalDate from, LocalDate to) {
        List<AccountMovement> rows = new ArrayList<>();
        for (Object[] row : db.list("""
                select a.code, a.name,
                       sum(case when e.entryDate < ?1 then l.debit else 0 end),
                       sum(case when e.entryDate < ?1 then l.credit else 0 end),
                       sum(case when e.entryDate >= ?1 then l.debit else 0 end),
                       sum(case when e.entryDate >= ?1 then l.credit else 0 end)
                from JournalLine l, JournalEntry e, LedgerAccount a
                where l.entryId = e.id and l.accountId = a.id and e.status = ?2 and e.entryDate <= ?3
                group by a.code, a.name
                """, Object[].class, from, JournalEntryStatus.POSTED, to)) {
            rows.add(new AccountMovement((String) row[0], (String) row[1], (BigDecimal) row[2], (BigDecimal) row[3], (BigDecimal) row[4], (BigDecimal) row[5]));
        }
        return rows;
    }

    private static List<AmountRow> mainRows(List<AccountMovement> sums, Map<String, String> names, java.util.function.Function<AccountMovement, BigDecimal> amount) {
        Map<String, AmountRow> rows = new HashMap<>();
        for (AccountMovement s : sums) {
            String code = AccountingChart.mainCode(s.code());
            AmountRow row = rows.getOrDefault(code, new AmountRow(code, names.getOrDefault(code, code), BigDecimal.ZERO));
            rows.put(code, new AmountRow(code, row.name(), row.amount().add(amount.apply(s))));
        }
        return new ArrayList<>(rows.values());
    }


    private static String name(Employee e) { return e.firstName + " " + e.lastName; }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
