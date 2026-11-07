package dev.enterprisepulse.accounting;

import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Journal arithmetic and the statements derived from posted lines (mizan, bilanço, gelir tablosu). */
public final class AccountingMath {
    private AccountingMath() {}

    public record DraftLine(String code, BigDecimal debit, BigDecimal credit, UUID partnerId, UUID employeeId, String description) {
        public DraftLine {
            debit = debit == null ? BigDecimal.ZERO : debit;
            credit = credit == null ? BigDecimal.ZERO : credit;
        }

        public static DraftLine debit(String code, BigDecimal amount) {
            return new DraftLine(code, amount, BigDecimal.ZERO, null, null, null);
        }

        public static DraftLine credit(String code, BigDecimal amount) {
            return new DraftLine(code, BigDecimal.ZERO, amount, null, null, null);
        }

        public static DraftLine debit(String code, BigDecimal amount, UUID partnerId, UUID employeeId, String description) {
            return new DraftLine(code, amount, BigDecimal.ZERO, partnerId, employeeId, description);
        }

        public static DraftLine credit(String code, BigDecimal amount, UUID partnerId, UUID employeeId, String description) {
            return new DraftLine(code, BigDecimal.ZERO, amount, partnerId, employeeId, description);
        }
    }

    public record PostingLine(String code, BigDecimal debit, BigDecimal credit, UUID partnerId, UUID employeeId, String description) {}

    public record AccountRef(String code, String name) {}

    public record AccountMovement(String code, String name, BigDecimal openingDebit, BigDecimal openingCredit, BigDecimal periodDebit, BigDecimal periodCredit) {}

    public record TrialBalanceTotals(BigDecimal periodDebit, BigDecimal periodCredit, BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal balanceDebit, BigDecimal balanceCredit) {}

    public record TrialBalanceRow(String code, String name, int level, BigDecimal periodDebit, BigDecimal periodCredit, BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal balanceDebit, BigDecimal balanceCredit) {}

    public record AmountRow(String code, String name, BigDecimal amount) {}

    public record StatementGroup(String code, String name, BigDecimal total, List<AmountRow> accounts) {}

    public record StatementClass(String code, String name, BigDecimal total, List<StatementGroup> groups) {}

    public record BalanceSheet(List<StatementClass> assets, List<StatementClass> liabilities, BigDecimal totalAssets, BigDecimal totalLiabilities) {}

    public record IncomeSection(String key, String name, BigDecimal total, List<AmountRow> accounts) {}

    public record IncomeStatement(List<IncomeSection> sections, BigDecimal netSales, BigDecimal grossProfit, BigDecimal operatingProfit, BigDecimal ordinaryProfit, BigDecimal profitBeforeTax, BigDecimal netProfit) {}

    public record TrialBalanceResult(List<TrialBalanceRow> rows, TrialBalanceTotals totals) {}

    public static BigDecimal toBase(BigDecimal amount, BigDecimal rate) {
        return DocumentMath.toBase(amount, rate);
    }

    public static List<PostingLine> normalizeLines(List<DraftLine> lines) {
        Map<String, PostingLine> merged = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        for (DraftLine line : lines) {
            BigDecimal net = line.debit().subtract(line.credit());
            if (net.signum() == 0) continue;
            boolean debit = net.signum() > 0;
            String description = line.description() == null || line.description().isBlank() ? null : line.description().trim();
            String key = String.join("|", debit ? "D" : "C", line.code(), String.valueOf(line.partnerId()), String.valueOf(line.employeeId()), String.valueOf(description));
            PostingLine current = merged.get(key);
            if (current == null) {
                current = new PostingLine(line.code(), BigDecimal.ZERO, BigDecimal.ZERO, line.partnerId(), line.employeeId(), description);
                order.add(key);
            }
            if (debit) {
                merged.put(key, new PostingLine(current.code(), current.debit().add(net), current.credit(), current.partnerId(), current.employeeId(), current.description()));
            } else {
                merged.put(key, new PostingLine(current.code(), current.debit(), current.credit().subtract(net), current.partnerId(), current.employeeId(), current.description()));
            }
        }
        List<PostingLine> rows = order.stream().map(merged::get).toList();
        List<PostingLine> out = new ArrayList<>();
        rows.stream().filter(l -> l.debit().signum() > 0).forEach(out::add);
        rows.stream().filter(l -> l.credit().signum() > 0).forEach(out::add);
        return out;
    }

    public static BigDecimal[] lineTotals(List<PostingLine> lines) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (PostingLine line : lines) {
            debit = debit.add(line.debit());
            credit = credit.add(line.credit());
        }
        return new BigDecimal[] {debit, credit};
    }

    public static boolean isBalanced(List<PostingLine> lines) {
        BigDecimal[] totals = lineTotals(lines);
        return totals[0].signum() > 0 && totals[0].compareTo(totals[1]) == 0;
    }

    public static BigDecimal[] lineTotalsFromAmounts(Iterable<BigDecimal[]> lines) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (BigDecimal[] line : lines) {
            debit = debit.add(line[0]);
            credit = credit.add(line[1]);
        }
        return new BigDecimal[] {debit, credit};
    }

    public static boolean isBalancedAmounts(Iterable<BigDecimal[]> lines) {
        BigDecimal[] totals = lineTotalsFromAmounts(lines);
        return totals[0].signum() > 0 && totals[0].compareTo(totals[1]) == 0;
    }

    public static String fingerprint(List<PostingLine> lines) {
        return lines.stream()
                .map(l -> String.join("|", l.code(), money(l.debit()), money(l.credit()),
                        l.partnerId() == null ? "" : l.partnerId().toString(),
                        l.employeeId() == null ? "" : l.employeeId().toString()))
                .sorted()
                .collect(Collectors.joining(";"));
    }

    public static int compareCodes(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            if (i >= pa.length) return -1;
            if (i >= pb.length) return 1;
            int cmp = Integer.compare(Integer.parseInt(pa[i]), Integer.parseInt(pb[i]));
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    public static boolean covers(String parent, String code) {
        return code.equals(parent) || code.startsWith(parent + ".");
    }

    public static TrialBalanceResult trialBalance(List<AccountRef> accounts, List<AccountMovement> movements, boolean detailed) {
        Map<String, String> names = new LinkedHashMap<>();
        for (AccountRef account : accounts) names.putIfAbsent(account.code(), account.name());
        Set<String> codes = new LinkedHashSet<>();
        for (AccountMovement movement : movements) {
            String[] parts = movement.code().split("\\.");
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) cur.append('.');
                cur.append(parts[i]);
                codes.add(cur.toString());
            }
        }
        List<TrialBalanceRow> rows = new ArrayList<>();
        for (String code : codes.stream().sorted(AccountingMath::compareCodes).toList()) {
            int level = code.split("\\.").length - 1;
            if (!detailed && level > 0) continue;
            List<AccountMovement> own = movements.stream().filter(m -> covers(code, m.code())).toList();
            BigDecimal periodDebit = own.stream().map(AccountMovement::periodDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal periodCredit = own.stream().map(AccountMovement::periodCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDebit = periodDebit.add(own.stream().map(AccountMovement::openingDebit).reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal totalCredit = periodCredit.add(own.stream().map(AccountMovement::openingCredit).reduce(BigDecimal.ZERO, BigDecimal::add));
            if (totalDebit.signum() == 0 && totalCredit.signum() == 0) continue;
            BigDecimal balance = totalDebit.subtract(totalCredit);
            rows.add(new TrialBalanceRow(code, names.getOrDefault(code, code), level, periodDebit, periodCredit, totalDebit, totalCredit,
                    balance.signum() > 0 ? balance : BigDecimal.ZERO, balance.signum() < 0 ? balance.negate() : BigDecimal.ZERO));
        }
        List<TrialBalanceRow> mains = rows.stream().filter(r -> r.level() == 0).toList();
        return new TrialBalanceResult(rows, new TrialBalanceTotals(
                sum(mains, TrialBalanceRow::periodDebit), sum(mains, TrialBalanceRow::periodCredit),
                sum(mains, TrialBalanceRow::totalDebit), sum(mains, TrialBalanceRow::totalCredit),
                sum(mains, TrialBalanceRow::balanceDebit), sum(mains, TrialBalanceRow::balanceCredit)));
    }

    public static BalanceSheet balanceSheetOf(List<AmountRow> balances, BigDecimal currentResult, BigDecimal priorResult) {
        List<AmountRow> rows = new ArrayList<>();
        for (AmountRow balance : balances) {
            if (balance.code().charAt(0) > '5' || balance.amount().signum() == 0) continue;
            BigDecimal amount = balance.code().charAt(0) <= '2' ? balance.amount() : balance.amount().negate();
            rows.add(new AmountRow(AccountingChart.mainCode(balance.code()), balance.name(), amount));
        }
        addResult(rows, "570", "Geçmiş Yıllar Kârları (Zararları)", priorResult);
        addResult(rows, "590", "Dönem Net Kârı (Zararı)", currentResult);
        List<StatementClass> assets = classes(rows, '1', '2');
        List<StatementClass> liabilities = classes(rows, '3', '4', '5');
        return new BalanceSheet(assets, liabilities,
                assets.stream().map(StatementClass::total).reduce(BigDecimal.ZERO, BigDecimal::add),
                liabilities.stream().map(StatementClass::total).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public static IncomeStatement incomeStatementOf(List<AmountRow> results) {
        List<IncomeSection> sections = AccountingChart.INCOME_SECTIONS.stream().map(def -> {
            List<AmountRow> accounts = results.stream()
                    .filter(r -> Objects.equals(AccountingChart.incomeSection(r.code()), def.key()) && r.amount().signum() != 0)
                    .sorted((a, b) -> compareCodes(a.code(), b.code()))
                    .toList();
            return new IncomeSection(def.key(), def.label(), accounts.stream().map(AmountRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add), accounts);
        }).toList();
        BigDecimal netSales = total(sections, "grossSales").add(total(sections, "salesDeductions"));
        BigDecimal grossProfit = netSales.add(total(sections, "costOfSales"));
        BigDecimal operatingProfit = grossProfit.add(total(sections, "operatingExpenses"));
        BigDecimal ordinaryProfit = operatingProfit.add(total(sections, "otherIncome")).add(total(sections, "otherExpenses")).add(total(sections, "financeExpenses"));
        BigDecimal profitBeforeTax = ordinaryProfit.add(total(sections, "extraordinaryIncome")).add(total(sections, "extraordinaryExpenses"));
        return new IncomeStatement(sections, netSales, grossProfit, operatingProfit, ordinaryProfit, profitBeforeTax, profitBeforeTax.add(total(sections, "taxProvision")));
    }

    private static void addResult(List<AmountRow> rows, String code, String name, BigDecimal amount) {
        if (amount.signum() == 0) return;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).code().equals(code)) {
                rows.set(i, new AmountRow(code, rows.get(i).name(), rows.get(i).amount().add(amount)));
                return;
            }
        }
        rows.add(new AmountRow(code, name, amount));
    }

    private static List<StatementClass> classes(List<AmountRow> rows, char... digits) {
        List<StatementClass> out = new ArrayList<>();
        for (char cls : digits) {
            List<AmountRow> inClass = rows.stream().filter(r -> r.code().charAt(0) == cls).sorted((a, b) -> compareCodes(a.code(), b.code())).toList();
            List<StatementGroup> groups = new ArrayList<>();
            for (String code : inClass.stream().map(r -> r.code().substring(0, 2)).distinct().toList()) {
                List<AmountRow> accounts = inClass.stream().filter(r -> r.code().startsWith(code)).toList();
                groups.add(new StatementGroup(code, AccountingChart.GROUPS.getOrDefault(code, code),
                        accounts.stream().map(AmountRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add), accounts));
            }
            if (!groups.isEmpty()) {
                out.add(new StatementClass(String.valueOf(cls), AccountingChart.BALANCE_CLASSES.get(String.valueOf(cls)),
                        groups.stream().map(StatementGroup::total).reduce(BigDecimal.ZERO, BigDecimal::add), groups));
            }
        }
        return out;
    }

    private static BigDecimal total(List<IncomeSection> sections, String key) {
        return sections.stream().filter(s -> s.key().equals(key)).map(IncomeSection::total).findFirst().orElse(BigDecimal.ZERO);
    }

    private static BigDecimal sum(List<TrialBalanceRow> rows, java.util.function.Function<TrialBalanceRow, BigDecimal> getter) {
        return rows.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
