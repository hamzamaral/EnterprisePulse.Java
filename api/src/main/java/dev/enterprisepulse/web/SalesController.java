package dev.enterprisepulse.web;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.finance.ChequeService;
import dev.enterprisepulse.finance.FinanceReportService;
import dev.enterprisepulse.finance.PaymentService;
import dev.enterprisepulse.finance.TreasuryService;
import dev.enterprisepulse.sales.DeliveryNoteService;
import dev.enterprisepulse.sales.SalesInvoiceService;
import dev.enterprisepulse.sales.SalesOrderService;
import dev.enterprisepulse.sales.SalesQuoteService;
import dev.enterprisepulse.sales.SalesReturnService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Sales")
public class SalesController {
    public record AcceptQuote(UUID warehouseId, LocalDate deliveryDate) {}
    public record ChequeAction(UUID accountId, LocalDate date, String note) {}

    private final SalesQuoteService quotes;
    private final SalesOrderService orders;
    private final DeliveryNoteService deliveries;
    private final SalesInvoiceService invoices;
    private final SalesReturnService returns;
    private final TreasuryService treasury;
    private final PaymentService payments;
    private final ChequeService cheques;
    private final FinanceReportService reports;

    public SalesController(SalesQuoteService quotes, SalesOrderService orders, DeliveryNoteService deliveries, SalesInvoiceService invoices, SalesReturnService returns,
                            TreasuryService treasury, PaymentService payments, ChequeService cheques, FinanceReportService reports) {
        this.quotes = quotes;
        this.orders = orders;
        this.deliveries = deliveries;
        this.invoices = invoices;
        this.returns = returns;
        this.treasury = treasury;
        this.payments = payments;
        this.cheques = cheques;
        this.reports = reports;
    }

    private static void require(String code) { CurrentUser.get().require(code); }

    @PostMapping("/api/sales/quotes") public SalesQuoteService.Detail createQuote(@RequestBody SalesQuoteService.Create body) { require("sales:create"); return quotes.create(body); }
    @GetMapping("/api/sales/quotes/{id}") public SalesQuoteService.Detail quote(@PathVariable UUID id) { require("sales:read"); return quotes.get(id); }
    @PostMapping("/api/sales/quotes/{id}/reject") public SalesQuoteService.Detail rejectQuote(@PathVariable UUID id) { require("sales:update"); return quotes.reject(id); }
    @PostMapping("/api/sales/quotes/{id}/accept") public UUID acceptQuote(@PathVariable UUID id, @RequestBody AcceptQuote body) { require("sales:create"); return quotes.accept(id, body.warehouseId(), body.deliveryDate()); }

    @GetMapping("/api/sales/orders/open") public java.util.List<SalesOrderService.Detail> openOrders() { require("sales:read"); return orders.open(); }
    @PostMapping("/api/sales/orders") public SalesOrderService.Detail createOrder(@RequestBody SalesOrderService.Save body) { require("sales:create"); return orders.create(body); }
    @GetMapping("/api/sales/orders/{id}") public SalesOrderService.Detail order(@PathVariable UUID id) { require("sales:read"); return orders.get(id); }
    @PutMapping("/api/sales/orders/{id}") public SalesOrderService.Detail updateOrder(@PathVariable UUID id, @RequestBody SalesOrderService.Save body) { require("sales:update"); return orders.update(id, body); }
    @PostMapping("/api/sales/orders/{id}/approve") public SalesOrderService.Detail approveOrder(@PathVariable UUID id) { require("sales:approve"); return orders.approve(id); }
    @PostMapping("/api/sales/orders/{id}/cancel") public SalesOrderService.Detail cancelOrder(@PathVariable UUID id) { require("sales:update"); return orders.cancel(id); }

    @PostMapping("/api/sales/deliveries") public DeliveryNoteService.Detail deliver(@RequestBody DeliveryNoteService.Create body) { require("sales:deliver"); return deliveries.create(body); }
    @GetMapping("/api/sales/deliveries/{id}") public DeliveryNoteService.Detail delivery(@PathVariable UUID id) { require("sales:read"); return deliveries.get(id); }

    @PostMapping("/api/sales/invoices") public SalesInvoiceService.Detail invoice(@RequestBody SalesInvoiceService.Create body) { require("sales:invoice"); return invoices.create(body); }
    @GetMapping("/api/sales/invoices/{id}") public SalesInvoiceService.Detail salesInvoice(@PathVariable UUID id) { require("sales:read"); return invoices.get(id); }
    @PostMapping("/api/sales/invoices/{id}/cancel") public SalesInvoiceService.Detail cancelInvoice(@PathVariable UUID id, @RequestBody PurchasingController.CancelBody body) { require("sales:invoice"); return invoices.cancel(id, body.text()); }

    @PostMapping("/api/sales/returns") public SalesReturnService.Detail salesReturn(@RequestBody SalesReturnService.Create body) { require("sales:invoice"); return returns.create(body); }
    @GetMapping("/api/sales/returns/{id}") public SalesReturnService.Detail salesReturn(@PathVariable UUID id) { require("sales:read"); return returns.get(id); }

    @GetMapping("/api/finance/accounts") public java.util.List<TreasuryService.AccountDto> accounts() { require("finance:read"); return treasury.accounts(); }
    @GetMapping("/api/finance/accounts/{id}/transactions") public java.util.List<TreasuryService.TxDto> transactions(@PathVariable UUID id) { require("finance:read"); return treasury.transactions(id); }
    @PostMapping("/api/finance/payments") public PaymentService.Detail pay(@RequestBody PaymentService.Create body) { require("finance:create"); return payments.create(body); }
    @GetMapping("/api/finance/payments/{id}") public PaymentService.Detail payment(@PathVariable UUID id) { require("finance:read"); return payments.get(id); }
    @PostMapping("/api/finance/payments/{id}/cancel") public PaymentService.Detail cancelPayment(@PathVariable UUID id, @RequestBody PurchasingController.CancelBody body) { require("finance:update"); return payments.cancel(id, body.text()); }
    @GetMapping("/api/finance/cheques/{id}") public ChequeService.Detail cheque(@PathVariable UUID id) { require("finance:read"); return cheques.get(id); }
    @PostMapping("/api/finance/cheques/{id}/deposit") public ChequeService.Detail deposit(@PathVariable UUID id, @RequestBody ChequeAction body) { require("finance:update"); return cheques.deposit(id, body.accountId(), body.date() == null ? LocalDate.now() : body.date()); }
    @PostMapping("/api/finance/cheques/{id}/collect") public ChequeService.Detail collect(@PathVariable UUID id, @RequestBody(required = false) ChequeAction body) { require("finance:approve"); return cheques.collect(id, body == null || body.date() == null ? LocalDate.now() : body.date()); }
    @PostMapping("/api/finance/cheques/{id}/bounce") public ChequeService.Detail bounce(@PathVariable UUID id, @RequestBody(required = false) ChequeAction body) { require("finance:update"); return cheques.bounce(id, body == null || body.date() == null ? LocalDate.now() : body.date(), body == null ? null : body.note()); }
    @PostMapping("/api/finance/cheques/{id}/pay") public ChequeService.Detail payCheque(@PathVariable UUID id, @RequestBody(required = false) ChequeAction body) { require("finance:approve"); return cheques.pay(id, body == null || body.date() == null ? LocalDate.now() : body.date()); }
    @GetMapping("/api/finance/reports/aging") public FinanceReportService.AgingReport aging(@RequestParam(required = false) String kind) { require("finance:read"); return reports.aging(kind); }
    @GetMapping("/api/finance/reports/sales") public FinanceReportService.DocumentReport salesReport(@RequestParam LocalDate from, @RequestParam LocalDate to) { require("finance:read"); return reports.sales(from, to); }
    @GetMapping("/api/finance/reports/purchases") public FinanceReportService.DocumentReport purchaseReport(@RequestParam LocalDate from, @RequestParam LocalDate to) { require("finance:read"); return reports.purchases(from, to); }
    @GetMapping("/api/finance/reports/cashflow") public FinanceReportService.CashFlowReport cashFlow(@RequestParam LocalDate from, @RequestParam LocalDate to) { require("finance:read"); return reports.cashFlow(from, to); }
}
