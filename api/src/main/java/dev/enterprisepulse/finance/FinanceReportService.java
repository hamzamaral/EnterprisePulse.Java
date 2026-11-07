package dev.enterprisepulse.finance;

import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.PaymentStatus;
import dev.enterprisepulse.data.generated.PurchaseInvoice;
import dev.enterprisepulse.data.generated.SalesInvoice;
import dev.enterprisepulse.data.generated.TreasuryTransaction;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FinanceReportService {
    public record AgingLine(UUID id, String number, String partner, LocalDate dueDate, int days, BigDecimal openAmount, BigDecimal openBase, String bucket) {}

    public record AgingBucket(String key, String label, int count, BigDecimal amount) {}

    public record AgingReport(String kind, BigDecimal totalBase, List<AgingBucket> buckets, List<AgingLine> lines) {}

    public record DocumentLine(UUID id, String number, String partner, LocalDate date, String currencyCode, BigDecimal grandTotal, BigDecimal grandTotalBase, PaymentStatus paymentStatus) {}

    public record DocumentReport(LocalDate from, LocalDate to, int count, BigDecimal grandTotalBase, List<DocumentLine> lines) {}

    public record CashAccount(UUID id, String code, String name, String currencyCode, BigDecimal amountIn, BigDecimal amountOut, BigDecimal net) {}

    public record CashFlowReport(LocalDate from, LocalDate to, List<CashAccount> accounts) {}

    private final Db db;
    private final TreasuryService treasury;

    public FinanceReportService(Db db, TreasuryService treasury) {
        this.db = db;
        this.treasury = treasury;
    }

    public AgingReport aging(String kind) {
        boolean sales = kind == null || !kind.equalsIgnoreCase("purchase");
        LocalDate today = LocalDate.now();
        List<AgingLine> lines = new ArrayList<>();
        if (sales) {
            for (SalesInvoice invoice : db.list("select i from SalesInvoice i where i.status = ?1 and i.paymentStatus <> ?2", SalesInvoice.class, InvoiceStatus.ISSUED, PaymentStatus.PAID)) {
                lines.add(line(invoice.id, invoice.number, db.get(BusinessPartner.class, invoice.partnerId).title, invoice.dueDate, today, invoice.grandTotal.subtract(invoice.paidAmount), invoice.exchangeRate));
            }
        } else {
            for (PurchaseInvoice invoice : db.list("select i from PurchaseInvoice i where i.status = ?1 and i.paymentStatus <> ?2", PurchaseInvoice.class, InvoiceStatus.ISSUED, PaymentStatus.PAID)) {
                lines.add(line(invoice.id, invoice.number, db.get(BusinessPartner.class, invoice.partnerId).title, invoice.dueDate, today, invoice.grandTotal.subtract(invoice.paidAmount), invoice.exchangeRate));
            }
        }
        lines.sort(Comparator.comparingInt(AgingLine::days).reversed());
        List<AgingBucket> buckets = List.of(bucket("current", "Vadesi gelmemiş", lines), bucket("1-30", "1-30 gün", lines), bucket("31-60", "31-60 gün", lines),
                bucket("61-90", "61-90 gün", lines), bucket("90+", "90 gün üzeri", lines));
        return new AgingReport(sales ? "sales" : "purchase", lines.stream().map(AgingLine::openBase).reduce(BigDecimal.ZERO, BigDecimal::add), buckets, lines);
    }

    public DocumentReport sales(LocalDate from, LocalDate to) {
        List<DocumentLine> lines = new ArrayList<>();
        for (SalesInvoice invoice : db.list("select i from SalesInvoice i where i.status = ?1 and i.invoiceDate >= ?2 and i.invoiceDate <= ?3 order by i.invoiceDate desc",
                SalesInvoice.class, InvoiceStatus.ISSUED, from, to)) {
            lines.add(new DocumentLine(invoice.id, invoice.number, db.get(BusinessPartner.class, invoice.partnerId).title, invoice.invoiceDate, invoice.currencyCode,
                    invoice.grandTotal, DocumentMath.toBase(invoice.grandTotal, invoice.exchangeRate), invoice.paymentStatus));
        }
        return report(from, to, lines);
    }

    public DocumentReport purchases(LocalDate from, LocalDate to) {
        List<DocumentLine> lines = new ArrayList<>();
        for (PurchaseInvoice invoice : db.list("select i from PurchaseInvoice i where i.status = ?1 and i.invoiceDate >= ?2 and i.invoiceDate <= ?3 order by i.invoiceDate desc",
                PurchaseInvoice.class, InvoiceStatus.ISSUED, from, to)) {
            lines.add(new DocumentLine(invoice.id, invoice.number, db.get(BusinessPartner.class, invoice.partnerId).title, invoice.invoiceDate, invoice.currencyCode,
                    invoice.grandTotal, DocumentMath.toBase(invoice.grandTotal, invoice.exchangeRate), invoice.paymentStatus));
        }
        return report(from, to, lines);
    }

    public CashFlowReport cashFlow(LocalDate from, LocalDate to) {
        List<TreasuryTransaction> txs = db.list("select t from TreasuryTransaction t where t.txDate >= ?1 and t.txDate <= ?2", TreasuryTransaction.class, from, to);
        List<CashAccount> accounts = new ArrayList<>();
        for (TreasuryService.AccountDto account : treasury.accounts()) {
            BigDecimal inn = BigDecimal.ZERO;
            BigDecimal out = BigDecimal.ZERO;
            for (TreasuryTransaction tx : txs) {
                if (!tx.accountId.equals(account.id())) continue;
                inn = inn.add(tx.amountIn);
                out = out.add(tx.amountOut);
            }
            accounts.add(new CashAccount(account.id(), account.code(), account.name(), account.currencyCode(), inn, out, inn.subtract(out)));
        }
        return new CashFlowReport(from, to, accounts);
    }

    private static DocumentReport report(LocalDate from, LocalDate to, List<DocumentLine> lines) {
        return new DocumentReport(from, to, lines.size(), lines.stream().map(DocumentLine::grandTotalBase).reduce(BigDecimal.ZERO, BigDecimal::add), lines);
    }

    private static AgingBucket bucket(String key, String label, List<AgingLine> lines) {
        List<AgingLine> mine = lines.stream().filter(l -> l.bucket().equals(key)).toList();
        return new AgingBucket(key, label, mine.size(), mine.stream().map(AgingLine::openBase).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static AgingLine line(UUID id, String number, String partner, LocalDate due, LocalDate today, BigDecimal open, BigDecimal rate) {
        int days = (int) ChronoUnit.DAYS.between(due, today);
        String bucket = days <= 0 ? "current" : days <= 30 ? "1-30" : days <= 60 ? "31-60" : days <= 90 ? "61-90" : "90+";
        return new AgingLine(id, number, partner, due, Math.max(days, 0), DocumentMath.round2(open), DocumentMath.toBase(open, rate), bucket);
    }
}
