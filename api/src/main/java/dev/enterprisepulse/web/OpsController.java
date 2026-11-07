package dev.enterprisepulse.web;

import dev.enterprisepulse.budgets.BudgetService;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.contracts.ContractService;
import dev.enterprisepulse.data.generated.BudgetItem;
import dev.enterprisepulse.fleet.FleetService;
import dev.enterprisepulse.support.SupportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Ops")
public class OpsController {
    private final SupportService support;
    private final ContractService contracts;
    private final BudgetService budgets;
    private final FleetService fleet;

    public OpsController(SupportService support, ContractService contracts, BudgetService budgets, FleetService fleet) {
        this.support = support;
        this.contracts = contracts;
        this.budgets = budgets;
        this.fleet = fleet;
    }

    private static void require(String code) { CurrentUser.get().require(code); }

    // Support
    @PostMapping("/api/support/tickets") public SupportService.TicketDto createTicket(@RequestBody SupportService.SaveTicket body) { return support.create(body); }
    @GetMapping("/api/support/tickets/{id}") public SupportService.TicketDto ticket(@PathVariable UUID id) { return support.get(id); }
    @PutMapping("/api/support/tickets/{id}") public SupportService.TicketDto updateTicket(@PathVariable UUID id, @RequestBody SupportService.UpdateTicket body) { require("support:update"); return support.update(id, body); }
    @PostMapping("/api/support/tickets/{id}/status") public SupportService.TicketDto ticketStatus(@PathVariable UUID id, @RequestBody SupportService.ChangeTicketStatus body) { require("support:update"); return support.changeStatus(id, body); }
    @PostMapping("/api/support/tickets/{id}/comments") public SupportService.TicketDto ticketComment(@PathVariable UUID id, @RequestBody SupportService.TicketCommentBody body) { return support.comment(id, body); }
    @PostMapping("/api/support/tickets/{id}/close") public SupportService.TicketDto closeTicket(@PathVariable UUID id, @RequestBody(required = false) SupportService.CloseTicket body) { return support.close(id, body); }
    @PostMapping("/api/support/tickets/{id}/rate") public SupportService.TicketDto rateTicket(@PathVariable UUID id, @RequestBody SupportService.RateTicket body) { return support.rate(id, body); }
    @PostMapping("/api/support/tickets/{id}/reopen") public SupportService.TicketDto reopenTicket(@PathVariable UUID id, @RequestBody SupportService.ReopenTicket body) { return support.reopen(id, body); }
    @GetMapping("/api/support/mine") public SupportService.TicketList myTickets() { return support.mine(); }

    // Contracts
    @PostMapping({"/api/contracts", "/api/contracts/"}) public ContractService.ContractDto createContract(@RequestBody ContractService.SaveContract body) { require("contracts:create"); return contracts.create(body); }
    @GetMapping("/api/contracts/{id}") public ContractService.ContractDto contract(@PathVariable UUID id) { return contracts.get(id); }
    @PostMapping("/api/contracts/{id}/activate") public ContractService.ContractDto activate(@PathVariable UUID id, @RequestBody(required = false) ContractService.ActivateBody body) { require("contracts:update"); return contracts.activate(id, body == null ? null : body.signedDate()); }
    @PostMapping("/api/contracts/{id}/renew") public ContractService.ContractDto renew(@PathVariable UUID id, @RequestBody ContractService.RenewContract body) { require("contracts:update"); return contracts.renew(id, body); }
    @PostMapping("/api/contracts/{id}/terminate") public ContractService.ContractDto terminate(@PathVariable UUID id, @RequestBody ContractService.TerminateContract body) { require("contracts:update"); return contracts.terminate(id, body); }
    @PostMapping("/api/contracts/run") public ContractService.ContractRunResult runContracts() { require("contracts:update"); return contracts.runCompany(); }

    // Budgets
    @PostMapping("/api/budgets/items") public BudgetItem budgetItem(@RequestBody BudgetService.SaveBudgetItem body) { require("budgets:update"); return budgets.saveItem(body); }
    @PostMapping({"/api/budgets", "/api/budgets/"}) public BudgetService.BudgetDto createBudget(@RequestBody BudgetService.SaveBudget body) { require("budgets:update"); return budgets.create(body); }
    @GetMapping("/api/budgets/{id}") public BudgetService.BudgetDto budget(@PathVariable UUID id) { return budgets.get(id); }
    @PostMapping("/api/budgets/{id}/plan") public BudgetService.BudgetDto plan(@PathVariable UUID id, @RequestBody BudgetService.SavePlan body) { require("budgets:update"); return budgets.savePlan(id, body); }
    @PostMapping("/api/budgets/{id}/approve") public BudgetService.BudgetDto approveBudget(@PathVariable UUID id) { require("budgets:approve"); return budgets.approve(id); }
    @PostMapping("/api/budgets/{id}/reopen") public BudgetService.BudgetDto reopenBudget(@PathVariable UUID id) { require("budgets:update"); return budgets.reopen(id); }
    @PostMapping("/api/budgets/entries") public void budgetEntry(@RequestBody BudgetService.SaveEntry body) { require("budgets:update"); budgets.addEntry(body); }

    // Fleet
    @PostMapping("/api/fleet/vehicles") public FleetService.VehicleDto createVehicle(@RequestBody FleetService.SaveVehicle body) { require("fleet:update"); return fleet.create(body); }
    @GetMapping("/api/fleet/vehicles/{id}") public FleetService.VehicleDto vehicle(@PathVariable UUID id) { return fleet.get(id); }
    @PostMapping("/api/fleet/vehicles/{id}/assign") public FleetService.VehicleDto assign(@PathVariable UUID id, @RequestBody FleetService.AssignVehicle body) { require("fleet:update"); return fleet.assign(id, body); }
    @PostMapping("/api/fleet/vehicles/{id}/return") public FleetService.VehicleDto returnVehicle(@PathVariable UUID id, @RequestBody FleetService.ReturnVehicle body) { require("fleet:update"); return fleet.returnVehicle(id, body); }
    @PostMapping("/api/fleet/vehicles/{id}/fuel") public FleetService.VehicleDto fuel(@PathVariable UUID id, @RequestBody FleetService.SaveFuel body) { return fleet.addFuel(id, body); }
    @PostMapping("/api/fleet/vehicles/{id}/services") public FleetService.VehicleDto service(@PathVariable UUID id, @RequestBody FleetService.SaveService body) { require("fleet:update"); return fleet.addService(id, body); }
    @PostMapping("/api/fleet/vehicles/{id}/documents") public FleetService.VehicleDto document(@PathVariable UUID id, @RequestBody FleetService.SaveDocument body) { require("fleet:update"); return fleet.addDocument(id, body); }
    @GetMapping("/api/fleet/mine") public FleetService.VehicleList myVehicles() { return fleet.mine(); }
}
