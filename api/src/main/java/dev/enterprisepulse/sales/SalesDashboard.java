package dev.enterprisepulse.sales;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.PaymentStatus;
import dev.enterprisepulse.data.generated.SalesInvoice;
import dev.enterprisepulse.data.generated.SalesOrderStatus;
import dev.enterprisepulse.finance.TreasuryService;
import dev.enterprisepulse.platform.Extensibility;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class SalesDashboard implements Extensibility.DashboardProvider {
    private final Db db;
    private final TreasuryService treasury;

    public SalesDashboard(Db db, TreasuryService treasury) {
        this.db = db;
        this.treasury = treasury;
    }

    @Override public int order() { return 40; }

    @Override
    public DashboardSection section() {
        CurrentUser ctx = CurrentUser.get();
        List<DashboardCard> cards = new ArrayList<>();
        if (ctx.has("sales:read")) {
            long open = db.list("select o.id from SalesOrder o where o.status = ?1 or o.status = ?2", UUID.class, SalesOrderStatus.APPROVED, SalesOrderStatus.PARTIALLY_DELIVERED).size();
            cards.add(new DashboardCard("open-sales-orders", "Açık satış siparişi", String.valueOf(open), "PointOfSale", "/r/sales-orders"));
        }
        if (ctx.has("finance:read") || ctx.has("sales:read")) {
            LocalDate today = LocalDate.now();
            List<SalesInvoice> overdue = db.list("select i from SalesInvoice i where i.status = ?1 and i.paymentStatus <> ?2 and i.dueDate < ?3", SalesInvoice.class,
                    InvoiceStatus.ISSUED, PaymentStatus.PAID, today);
            BigDecimal amount = overdue.stream().map(i -> DocumentMath.toBase(i.grandTotal.subtract(i.paidAmount), i.exchangeRate)).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!overdue.isEmpty()) {
                cards.add(new DashboardCard("overdue-receivables", "Vadesi geçen alacak", String.format(Locale.forLanguageTag("tr"), "%,.2f", amount),
                        "AccountBalanceWallet", overdue.size() + " fatura", "/finance/aging", "warning"));
            }
        }
        if (ctx.has("finance:read")) {
            BigDecimal cash = treasury.accounts().stream().filter(a -> "TRY".equals(a.currencyCode()) && a.isActive()).map(TreasuryService.AccountDto::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
            cards.add(new DashboardCard("cash-balance", "Kasa ve banka (TRY)", String.format(Locale.forLanguageTag("tr"), "%,.2f", cash), "AccountBalance", "/r/treasury-accounts"));
        }
        return cards.isEmpty() ? null : new DashboardSection("sales", "Satış ve Finans", cards);
    }
}
