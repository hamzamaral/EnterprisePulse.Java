package dev.enterprisepulse.web;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.partners.PartnerLedgerService;
import dev.enterprisepulse.purchasing.GoodsReceiptService;
import dev.enterprisepulse.purchasing.PurchaseInvoiceService;
import dev.enterprisepulse.purchasing.PurchaseOrderService;
import dev.enterprisepulse.purchasing.PurchaseRequestService;
import dev.enterprisepulse.purchasing.SupplierQuoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Purchasing")
public class PurchasingController {
    public record CancelBody(String reason, String comment) {
        public String text() {
            return reason == null || reason.isBlank() ? comment : reason;
        }
    }

    private final PartnerLedgerService ledger;
    private final PurchaseRequestService requests;
    private final SupplierQuoteService quotes;
    private final PurchaseOrderService orders;
    private final GoodsReceiptService receipts;
    private final PurchaseInvoiceService invoices;

    public PurchasingController(PartnerLedgerService ledger, PurchaseRequestService requests, SupplierQuoteService quotes, PurchaseOrderService orders,
                                 GoodsReceiptService receipts, PurchaseInvoiceService invoices) {
        this.ledger = ledger;
        this.requests = requests;
        this.quotes = quotes;
        this.orders = orders;
        this.receipts = receipts;
        this.invoices = invoices;
    }

    private static void require(String code) {
        CurrentUser.get().require(code);
    }

    private static void requireAny(String... codes) {
        if (!CurrentUser.get().hasAny(codes)) throw dev.enterprisepulse.common.AppException.forbidden("Missing permission: " + codes[0]);
    }

    @GetMapping("/api/partners/{id}/statement")
    public PartnerLedgerService.Statement statement(@PathVariable UUID id, @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo) {
        requireAny("partners:read", "purchasing:read", "sales:read", "finance:read");
        return ledger.statement(id, dateFrom, dateTo);
    }

    @GetMapping("/api/partners/{id}/balance")
    public BigDecimal balance(@PathVariable UUID id) {
        requireAny("partners:read", "purchasing:read", "sales:read", "finance:read");
        return ledger.balance(id);
    }

    @PostMapping("/api/purchasing/requests")
    public PurchaseRequestService.Detail createRequest(@RequestBody PurchaseRequestService.CreateRequest body) {
        requireAny("workflow:start", "purchasing:create");
        return requests.create(body);
    }

    @GetMapping("/api/purchasing/requests/{id}")
    public PurchaseRequestService.Detail request(@PathVariable UUID id) {
        requireAny("purchasing:read", "workflow:start", "purchasing:create");
        return requests.detail(id);
    }

    @PostMapping("/api/purchasing/requests/{id}/cancel")
    public PurchaseRequestService.Detail cancelRequest(@PathVariable UUID id, @RequestBody(required = false) CancelBody body) {
        requireAny("workflow:start", "purchasing:update");
        return requests.cancel(id, body == null ? null : body.text());
    }

    @GetMapping("/api/purchasing/requests/{id}/compare")
    public SupplierQuoteService.Comparison compare(@PathVariable UUID id) {
        require("purchasing:read");
        return quotes.compare(id);
    }

    @PostMapping("/api/purchasing/quotes")
    public SupplierQuoteService.Detail createQuote(@RequestBody SupplierQuoteService.CreateQuote body) {
        require("purchasing:create");
        return quotes.create(body);
    }

    @GetMapping("/api/purchasing/quotes/{id}")
    public SupplierQuoteService.Detail quote(@PathVariable UUID id) {
        require("purchasing:read");
        return quotes.detail(id);
    }

    @PostMapping("/api/purchasing/quotes/{id}/reject")
    public SupplierQuoteService.Detail reject(@PathVariable UUID id) {
        require("purchasing:update");
        return quotes.reject(id);
    }

    @PostMapping("/api/purchasing/quotes/{id}/accept")
    public UUID accept(@PathVariable UUID id, @RequestBody SupplierQuoteService.Accept body) {
        require("purchasing:create");
        return quotes.accept(id, body);
    }

    @GetMapping("/api/purchasing/orders/open")
    public List<PurchaseOrderService.Detail> openOrders() {
        require("purchasing:read");
        return orders.open();
    }

    @PostMapping("/api/purchasing/orders")
    public PurchaseOrderService.Detail createOrder(@RequestBody PurchaseOrderService.Save body) {
        require("purchasing:create");
        return orders.create(body);
    }

    @GetMapping("/api/purchasing/orders/{id}")
    public PurchaseOrderService.Detail order(@PathVariable UUID id) {
        require("purchasing:read");
        return orders.detail(id);
    }

    @PutMapping("/api/purchasing/orders/{id}")
    public PurchaseOrderService.Detail updateOrder(@PathVariable UUID id, @RequestBody PurchaseOrderService.Save body) {
        require("purchasing:update");
        return orders.update(id, body);
    }

    @PostMapping("/api/purchasing/orders/{id}/approve")
    public PurchaseOrderService.Detail approve(@PathVariable UUID id) {
        require("purchasing:approve");
        return orders.approve(id);
    }

    @PostMapping("/api/purchasing/orders/{id}/cancel")
    public PurchaseOrderService.Detail cancelOrder(@PathVariable UUID id, @RequestBody(required = false) CancelBody body) {
        require("purchasing:update");
        return orders.cancel(id);
    }

    @PostMapping("/api/purchasing/receipts")
    public GoodsReceiptService.Detail createReceipt(@RequestBody GoodsReceiptService.Create body) {
        require("purchasing:receive");
        return receipts.create(body);
    }

    @GetMapping("/api/purchasing/receipts/{id}")
    public GoodsReceiptService.Detail receipt(@PathVariable UUID id) {
        require("purchasing:read");
        return receipts.detail(id);
    }

    @PostMapping("/api/purchasing/invoices")
    public PurchaseInvoiceService.Detail createInvoice(@RequestBody PurchaseInvoiceService.Create body) {
        requireAny("purchasing:create", "finance:create");
        return invoices.create(body);
    }

    @GetMapping("/api/purchasing/invoices/{id}")
    public PurchaseInvoiceService.Detail invoice(@PathVariable UUID id) {
        requireAny("purchasing:read", "finance:read");
        return invoices.detail(id);
    }

    @PostMapping("/api/purchasing/invoices/{id}/cancel")
    public PurchaseInvoiceService.Detail cancelInvoice(@PathVariable UUID id, @RequestBody(required = false) CancelBody body) {
        require("purchasing:update");
        return invoices.cancel(id, body == null ? null : body.text());
    }
}
