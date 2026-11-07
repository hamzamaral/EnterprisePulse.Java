package dev.enterprisepulse.web;

import dev.enterprisepulse.accounting.AccountingService;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.einvoice.EInvoiceService;
import dev.enterprisepulse.production.ProductionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Muhasebe, e-Fatura simülasyonu and Üretim/MRP. */
@RestController
public class AccountingController {
    private final AccountingService accounting;
    private final EInvoiceService einvoice;
    private final ProductionService production;

    public AccountingController(AccountingService accounting, EInvoiceService einvoice, ProductionService production) {
        this.accounting = accounting;
        this.einvoice = einvoice;
        this.production = production;
    }

    private static void require(String... codes) {
        CurrentUser user = CurrentUser.get();
        if (codes.length == 1) user.require(codes[0]);
        else if (!user.hasAny(codes)) user.require(codes[0]);
    }

    // Accounting
    @GetMapping("/api/accounting/summary")
    @Tag(name = "Accounting")
    public AccountingService.AccountingSummaryDto summary(@RequestParam(required = false) Integer year) {
        require("accounting:read");
        return accounting.summary(year);
    }

    @GetMapping("/api/accounting/options")
    public AccountingService.AccountingOptionsDto options() { require("accounting:update"); return accounting.options(); }

    @GetMapping("/api/accounting/settings")
    public AccountingService.AccountingSettingsDto settings() { require("accounting:read"); return accounting.settings(); }

    @PatchMapping("/api/accounting/settings")
    public AccountingService.AccountingSettingsDto updateSettings(@RequestBody AccountingService.UpdateAccountingSettings body) {
        require("accounting:close");
        return accounting.updateSettings(body);
    }

    @GetMapping("/api/accounting/pending")
    public List<AccountingService.PendingPostingDto> pending() { require("accounting:read"); return accounting.pending(); }

    @PostMapping("/api/accounting/run")
    public AccountingService.PostingRunResultDto run() { require("accounting:approve"); return accounting.runCompany(); }

    @GetMapping("/api/accounting/trial-balance")
    public AccountingService.TrialBalanceDto trialBalance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                         @RequestParam(required = false) Boolean detailed) {
        require("accounting:read");
        return accounting.trialBalance(from, to, detailed != null && detailed);
    }

    @GetMapping("/api/accounting/balance-sheet")
    public AccountingService.BalanceSheetDto balanceSheet(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        require("accounting:read");
        return accounting.balanceSheet(date);
    }

    @GetMapping("/api/accounting/income-statement")
    public AccountingService.IncomeStatementDto incomeStatement(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        require("accounting:read");
        return accounting.incomeStatement(from, to);
    }

    @GetMapping({"/api/ledger-accounts", "/api/ledger-accounts/"})
    public List<AccountingService.LedgerAccountDto> accounts() { require("accounting:read"); return accounting.listAccounts(); }

    @PostMapping("/api/ledger-accounts/install")
    public Map<String, Integer> install() { require("accounting:update"); return Map.of("added", accounting.installChart(null)); }

    @PostMapping({"/api/ledger-accounts", "/api/ledger-accounts/"})
    public AccountingService.LedgerAccountDto createAccount(@RequestBody AccountingService.CreateLedgerAccount body) {
        require("accounting:update");
        return accounting.createAccount(body);
    }

    @GetMapping("/api/ledger-accounts/{id}")
    public AccountingService.LedgerAccountDto account(@PathVariable UUID id) { require("accounting:read"); return accounting.getAccount(id); }

    @GetMapping("/api/ledger-accounts/{id}/ledger")
    public AccountingService.AccountLedgerDto ledger(@PathVariable UUID id,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) UUID partnerId) {
        require("accounting:read");
        return accounting.ledger(id, from, to, partnerId);
    }

    @PatchMapping("/api/ledger-accounts/{id}")
    public AccountingService.LedgerAccountDto updateAccount(@PathVariable UUID id, @RequestBody AccountingService.UpdateLedgerAccount body) {
        require("accounting:update");
        return accounting.updateAccount(id, body);
    }

    @DeleteMapping("/api/ledger-accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        require("accounting:update");
        accounting.removeAccount(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/journal-entries/{id}")
    public AccountingService.JournalEntryDetailDto entry(@PathVariable UUID id) { require("accounting:read"); return accounting.getEntry(id); }

    @PostMapping({"/api/journal-entries", "/api/journal-entries/"})
    public AccountingService.JournalEntryDetailDto createEntry(@RequestBody AccountingService.SaveJournalEntry body) {
        require("accounting:update");
        return accounting.createEntry(body);
    }

    @PatchMapping("/api/journal-entries/{id}")
    public AccountingService.JournalEntryDetailDto updateEntry(@PathVariable UUID id, @RequestBody AccountingService.SaveJournalEntry body) {
        require("accounting:update");
        return accounting.updateEntry(id, body);
    }

    @DeleteMapping("/api/journal-entries/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable UUID id) {
        require("accounting:update");
        accounting.removeEntry(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/journal-entries/{id}/post")
    public AccountingService.JournalEntryDetailDto post(@PathVariable UUID id) { require("accounting:approve"); return accounting.postEntry(id); }

    @PostMapping("/api/journal-entries/{id}/reverse")
    public AccountingService.JournalEntryDetailDto reverse(@PathVariable UUID id, @RequestBody AccountingService.ReverseJournalEntry body) {
        require("accounting:approve");
        return accounting.reverse(id, body);
    }

    // EInvoice
    @GetMapping("/api/einvoices/summary")
    @Tag(name = "EInvoice")
    public EInvoiceService.SummaryDto einvoiceSummary() { require("einvoice:read"); return einvoice.summary(); }

    @GetMapping("/api/einvoices/unsent")
    public List<EInvoiceService.UnsentInvoiceDto> unsent() { require("einvoice:read"); return einvoice.unsent(); }

    @GetMapping("/api/einvoices/settings")
    public EInvoiceService.SettingsDto einvoiceSettings() { require("einvoice:read"); return einvoice.settings(); }

    @PatchMapping("/api/einvoices/settings")
    public EInvoiceService.SettingsDto updateEinvoiceSettings(@RequestBody EInvoiceService.UpdateSettings body) {
        require("einvoice:update");
        return einvoice.updateSettings(body);
    }

    @PostMapping({"/api/einvoices", "/api/einvoices/"})
    public EInvoiceService.DetailDto send(@RequestBody EInvoiceService.SendBody body) { require("einvoice:send"); return einvoice.send(body); }

    @PostMapping("/api/einvoices/sync")
    public EInvoiceService.SyncResultDto sync() { require("einvoice:send"); return einvoice.sync(); }

    @PostMapping("/api/einvoices/simulate-incoming")
    public EInvoiceService.DetailDto simulateIncoming(@RequestBody(required = false) EInvoiceService.SimulateIncoming body) {
        require("einvoice:update");
        return einvoice.simulateIncoming(body == null ? new EInvoiceService.SimulateIncoming(null, null) : body);
    }

    @GetMapping("/api/einvoices/{id}")
    public EInvoiceService.DetailDto einvoice(@PathVariable UUID id) { require("einvoice:read"); return einvoice.get(id); }

    @PostMapping("/api/einvoices/{id}/resend")
    public EInvoiceService.DetailDto resend(@PathVariable UUID id) { require("einvoice:send"); return einvoice.resend(id); }

    @PostMapping("/api/einvoices/{id}/respond")
    public EInvoiceService.DetailDto respond(@PathVariable UUID id, @RequestBody EInvoiceService.RespondBody body) {
        require("einvoice:update");
        return einvoice.respond(id, body);
    }

    @PostMapping("/api/einvoices/{id}/simulate-response")
    public EInvoiceService.DetailDto simulateResponse(@PathVariable UUID id, @RequestBody EInvoiceService.RespondBody body) {
        require("einvoice:send");
        return einvoice.simulateResponse(id, body);
    }

    @PostMapping("/api/einvoices/{id}/record")
    public EInvoiceService.DetailDto record(@PathVariable UUID id) {
        require("einvoice:update", "purchasing:create");
        return einvoice.record(id);
    }

    // Production
    @GetMapping("/api/production/summary")
    @Tag(name = "Production")
    public ProductionService.SummaryDto productionSummary() { require("production:read"); return production.summary(); }

    @GetMapping("/api/production/requirements")
    public List<ProductionService.RequirementDto> requirements() { require("production:read"); return production.requirements(); }

    @PostMapping("/api/production/requirements/purchase-request")
    public ProductionService.CreatedDocumentDto purchaseRequest(@RequestBody ProductionService.CreateRequirementRequest body) {
        require("production:read");
        return production.createPurchaseRequest(body);
    }

    @PostMapping("/api/production/boms")
    public ProductionService.BomDetailDto createBom(@RequestBody ProductionService.CreateBom body) { require("production:update"); return production.createBom(body); }

    @GetMapping("/api/production/boms/{id}")
    public ProductionService.BomDetailDto bom(@PathVariable UUID id) { require("production:read"); return production.getBom(id); }

    @GetMapping("/api/production/boms/{id}/plan")
    public ProductionService.BomPlanDto plan(@PathVariable UUID id, @RequestParam(required = false) java.math.BigDecimal quantity, @RequestParam(required = false) UUID warehouseId) {
        require("production:read");
        return production.plan(id, quantity == null ? java.math.BigDecimal.ONE : quantity, warehouseId);
    }

    @PatchMapping("/api/production/boms/{id}")
    public ProductionService.BomDetailDto updateBom(@PathVariable UUID id, @RequestBody ProductionService.UpdateBom body) {
        require("production:update");
        return production.updateBom(id, body);
    }

    @DeleteMapping("/api/production/boms/{id}")
    public ResponseEntity<Void> deleteBom(@PathVariable UUID id) {
        require("production:update");
        production.removeBom(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/production/work-orders")
    public ProductionService.WorkOrderDetailDto createWorkOrder(@RequestBody ProductionService.CreateWorkOrder body) {
        require("production:update");
        return production.createWorkOrder(body);
    }

    @GetMapping("/api/production/work-orders/{id}")
    public ProductionService.WorkOrderDetailDto workOrder(@PathVariable UUID id) { require("production:read"); return production.getWorkOrder(id); }

    @PatchMapping("/api/production/work-orders/{id}")
    public ProductionService.WorkOrderDetailDto updateWorkOrder(@PathVariable UUID id, @RequestBody ProductionService.UpdateWorkOrder body) {
        require("production:update");
        return production.updateWorkOrder(id, body);
    }

    @PostMapping("/api/production/work-orders/{id}/release")
    public ProductionService.WorkOrderDetailDto release(@PathVariable UUID id) { require("production:update"); return production.release(id); }

    @PostMapping("/api/production/work-orders/{id}/cancel")
    public ProductionService.WorkOrderDetailDto cancel(@PathVariable UUID id) { require("production:update"); return production.cancel(id); }

    @PostMapping("/api/production/work-orders/{id}/complete")
    public ProductionService.WorkOrderDetailDto complete(@PathVariable UUID id) { require("production:update"); return production.complete(id); }

    @PostMapping("/api/production/work-orders/{id}/records")
    public ProductionService.WorkOrderDetailDto recordProduction(@PathVariable UUID id, @RequestBody ProductionService.RecordProduction body) {
        require("production:record");
        return production.record(id, body);
    }
}
