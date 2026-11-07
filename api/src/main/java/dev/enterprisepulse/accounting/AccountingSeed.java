package dev.enterprisepulse.accounting;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.JournalEntry;
import dev.enterprisepulse.data.generated.JournalEntryStatus;
import dev.enterprisepulse.data.generated.JournalLine;
import dev.enterprisepulse.data.generated.LedgerAccount;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardProvider;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.sales.SalesHooks;
import dev.enterprisepulse.seed.SeedStep;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

public final class AccountingSeed {
    private AccountingSeed() {}

    @Component public static class LedgerAccountsHook extends SalesHooks.Managed {
        public LedgerAccountsHook() { super("ledger-accounts", "Hesap planı Muhasebe > Hesap Planı ekranından yönetilir"); }
    }

    @Component public static class JournalEntriesHook extends SalesHooks.Managed {
        public JournalEntriesHook() { super("journal-entries", "Yevmiye fişleri Muhasebe > Yevmiye ekranından kaydedilir ve kesinleştirilir"); }
    }

    @Component public static class JournalLinesHook extends SalesHooks.Managed {
        public JournalLinesHook() { super("journal-lines", "Fiş satırları fişle birlikte kaydedilir"); }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Dashboard implements DashboardProvider {
        private final AccountingService accounting;
        public Dashboard(AccountingService accounting) { this.accounting = accounting; }
        @Override public int order() { return 60; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("accounting:read")) return null;
            AccountingService.AccountingSummaryDto summary = accounting.summary(null);
            int pending = summary.pendingCount();
            return new DashboardSection("accounting", "Muhasebe", List.of(
                    new DashboardCard("accounting-cash", "Kasa ve banka", summary.cash().toPlainString() + " " + summary.currencyCode(), "AccountBalanceWallet", "/accounting"),
                    new DashboardCard("accounting-profit", summary.year() + " net kâr", summary.netProfit().toPlainString() + " " + summary.currencyCode(), "TrendingUp", null, "/accounting/reports",
                            summary.netProfit().signum() < 0 ? "error" : "success"),
                    new DashboardCard("accounting-pending", "Muhasebeleşmeyi bekleyen", Integer.toString(pending), "PendingActions", null, "/accounting", pending > 0 ? "warning" : "primary")));
        }
    }

    @Component
    public static class Seed implements SeedStep {
        private final Db db;
        private final AccountingService accounting;
        private final NumberingService numbering;

        public Seed(Db db, AccountingService accounting, NumberingService numbering) {
            this.db = db;
            this.accounting = accounting;
            this.numbering = numbering;
        }

        @Override public int order() { return 80; }

        @Override
        public void run(Context ctx) {
            accounting.installChart(ctx.pulseId);
            accounting.installChart(ctx.logisticsId);
            try (var scope = CurrentUser.get().actAs(ctx.pulseId, ctx.adminId, true)) {
                if (db.exists("select e.id from JournalEntry e")) return;
                LocalDate today = AccountingClock.today();
                LocalDate monthStart = LocalDate.of(today.getYear(), today.getMonth(), 1);
                LocalDate previousMonthEnd = monthStart.minusDays(1);
                LocalDate rentDate = monthStart.plusDays(4).isBefore(today) ? monthStart.plusDays(4) : today;
                record Line(String code, BigDecimal debit, BigDecimal credit, String text) {}
                record Entry(LocalDate date, String description, JournalEntryStatus status, Line[] lines) {}
                Entry[] entries = {
                        new Entry(previousMonthEnd, "Banka hesap işletim ücreti ve EFT masrafları", JournalEntryStatus.POSTED,
                                new Line[] {new Line("653", bd("1250"), BigDecimal.ZERO, "Hesap işletim ücreti ve EFT"), new Line("102", BigDecimal.ZERO, bd("1250"), null)}),
                        new Entry(previousMonthEnd, "Vadeli mevduat faiz geliri", JournalEntryStatus.POSTED,
                                new Line[] {new Line("102", bd("3400"), BigDecimal.ZERO, null), new Line("642", BigDecimal.ZERO, bd("3400"), "Aylık vadeli mevduat faizi")}),
                        new Entry(rentDate, "Merkez ofis kirası (%20 stopaj)", JournalEntryStatus.POSTED,
                                new Line[] {new Line("770", bd("60000"), BigDecimal.ZERO, "Brüt kira"), new Line("360", BigDecimal.ZERO, bd("12000"), "Kira stopajı"), new Line("102", BigDecimal.ZERO, bd("48000"), "Net kira ödemesi")}),
                        new Entry(previousMonthEnd, "Demirbaş amortismanı", JournalEntryStatus.DRAFT,
                                new Line[] {new Line("770", bd("8500"), BigDecimal.ZERO, "Aylık amortisman payı"), new Line("257", BigDecimal.ZERO, bd("8500"), null)}),
                };
                Map<String, UUID> ids = db.list("select a from LedgerAccount a", LedgerAccount.class).stream().collect(Collectors.toMap(a -> a.code, a -> a.id));
                for (Entry e : entries) {
                    JournalEntry entry = new JournalEntry();
                    entry.companyId = ctx.pulseId;
                    entry.number = numbering.next(AccountingService.JOURNAL_SEQUENCE, ctx.pulseId);
                    entry.entryDate = e.date();
                    entry.status = e.status();
                    entry.description = e.description();
                    entry.total = java.util.Arrays.stream(e.lines()).map(Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
                    entry.postedAt = e.status() == JournalEntryStatus.POSTED ? Instant.now() : null;
                    entry.createdById = ctx.adminId;
                    db.add(entry);
                    db.flush();
                    int no = 0;
                    for (Line line : e.lines()) {
                        JournalLine row = new JournalLine();
                        row.entryId = entry.id;
                        row.lineNo = ++no;
                        row.accountId = ids.get(line.code());
                        row.description = line.text();
                        row.debit = line.debit();
                        row.credit = line.credit();
                        db.add(row);
                    }
                }
                db.flush();
            }
        }

        private static BigDecimal bd(String v) { return new BigDecimal(v); }
    }
}
