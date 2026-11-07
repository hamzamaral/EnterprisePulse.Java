package dev.enterprisepulse.web;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.crm.ActivityService;
import dev.enterprisepulse.crm.LeadService;
import dev.enterprisepulse.crm.OpportunityService;
import dev.enterprisepulse.expenses.ExpenseReportService;
import dev.enterprisepulse.projects.ProjectService;
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
@Tag(name = "Work")
public class WorkController {
    private final LeadService leads;
    private final OpportunityService opportunities;
    private final ActivityService activities;
    private final ExpenseReportService expenses;
    private final ProjectService projects;

    public WorkController(LeadService leads, OpportunityService opportunities, ActivityService activities, ExpenseReportService expenses, ProjectService projects) {
        this.leads = leads;
        this.opportunities = opportunities;
        this.activities = activities;
        this.expenses = expenses;
        this.projects = projects;
    }

    private static void require(String code) { CurrentUser.get().require(code); }

    @PostMapping("/api/crm/leads") public LeadService.Detail createLead(@RequestBody LeadService.Save body) { require("crm:create"); return leads.create(body); }
    @GetMapping("/api/crm/leads/{id}") public LeadService.Detail lead(@PathVariable UUID id) { require("crm:read"); return leads.get(id); }
    @PutMapping("/api/crm/leads/{id}") public LeadService.Detail updateLead(@PathVariable UUID id, @RequestBody LeadService.Save body) { require("crm:update"); return leads.update(id, body); }
    @PostMapping("/api/crm/leads/{id}/disqualify") public LeadService.Detail disqualify(@PathVariable UUID id, @RequestBody LeadService.Disqualify body) { require("crm:update"); return leads.disqualify(id, body.reason()); }
    @PostMapping("/api/crm/leads/{id}/convert") public LeadService.Detail convert(@PathVariable UUID id, @RequestBody LeadService.Convert body) { require("crm:update"); return leads.convert(id, body); }

    @PostMapping("/api/crm/opportunities") public OpportunityService.Detail createOpportunity(@RequestBody OpportunityService.Save body) { require("crm:create"); return opportunities.create(body); }
    @GetMapping("/api/crm/opportunities/{id}") public OpportunityService.Detail opportunity(@PathVariable UUID id) { require("crm:read"); return opportunities.get(id); }
    @PostMapping("/api/crm/opportunities/{id}/stage") public OpportunityService.Detail stage(@PathVariable UUID id, @RequestBody OpportunityService.StageChange body) { require("crm:update"); return opportunities.changeStage(id, body.stage(), body.lostReason()); }

    @PostMapping("/api/crm/activities") public ActivityService.Detail createActivity(@RequestBody ActivityService.Save body) { require("crm:create"); return activities.create(body); }
    @GetMapping("/api/crm/activities/{id}") public ActivityService.Detail activity(@PathVariable UUID id) { require("crm:read"); return activities.get(id); }
    @PostMapping("/api/crm/activities/{id}/complete") public ActivityService.Detail complete(@PathVariable UUID id) { require("crm:update"); return activities.complete(id); }

    @GetMapping("/api/expenses/reports/mine") public ExpenseReportService.ListResult mine() { return expenses.mine(); }
    @PostMapping("/api/expenses/reports") public ExpenseReportService.Detail createReport(@RequestBody ExpenseReportService.Save body) { require("expenses:create"); return expenses.create(body); }
    @GetMapping("/api/expenses/reports/{id}") public ExpenseReportService.Detail report(@PathVariable UUID id) { return expenses.get(id); }
    @PutMapping("/api/expenses/reports/{id}") public ExpenseReportService.Detail updateReport(@PathVariable UUID id, @RequestBody ExpenseReportService.Save body) { require("expenses:update"); return expenses.update(id, body); }
    @PostMapping("/api/expenses/reports/{id}/submit") public ExpenseReportService.Detail submit(@PathVariable UUID id) { return expenses.submit(id); }
    @PostMapping("/api/expenses/reports/{id}/pay") public ExpenseReportService.Detail pay(@PathVariable UUID id, @RequestBody ExpenseReportService.Pay body) { require("expenses:pay"); return expenses.pay(id, body); }
    @PostMapping("/api/expenses/reports/{id}/cancel") public ExpenseReportService.Detail cancelReport(@PathVariable UUID id, @RequestBody PurchasingController.CancelBody body) { require("expenses:update"); return expenses.cancel(id, body.reason() != null ? body.reason() : body.text()); }

    @GetMapping("/api/projects/my-tasks") public ProjectService.TaskList myTasks() { return projects.myTasks(); }
    @GetMapping("/api/projects/time") public ProjectService.TimeList time(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) { return projects.time(from, to); }
    @PostMapping("/api/projects") public ProjectService.Detail createProject(@RequestBody ProjectService.Save body) { require("projects:create"); return projects.create(body); }
    @GetMapping("/api/projects/{id}") public ProjectService.Detail project(@PathVariable UUID id) { return projects.get(id); }
    @PostMapping("/api/projects/{id}/status") public ProjectService.Detail status(@PathVariable UUID id, @RequestBody ProjectService.StatusChange body) { require("projects:update"); return projects.changeStatus(id, body.status()); }
    @PostMapping("/api/projects/{id}/members") public ProjectService.Detail member(@PathVariable UUID id, @RequestBody ProjectService.MemberInput body) { require("projects:update"); return projects.addMember(id, body); }
    @PostMapping("/api/projects/{id}/tasks") public ProjectService.Detail task(@PathVariable UUID id, @RequestBody ProjectService.TaskInput body) { require("projects:update"); return projects.addTask(id, body); }
    @PostMapping("/api/projects/tasks/{id}/move") public ProjectService.Detail move(@PathVariable UUID id, @RequestBody ProjectService.Move body) { return projects.moveTask(id, body.status()); }
    @PostMapping("/api/projects/{id}/time") public ProjectService.Detail log(@PathVariable UUID id, @RequestBody ProjectService.TimeInput body) { return projects.logTime(id, body); }
}
