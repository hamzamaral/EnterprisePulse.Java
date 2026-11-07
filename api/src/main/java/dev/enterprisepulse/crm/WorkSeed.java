package dev.enterprisepulse.crm;

import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.CrmActivityType;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.ExpenseCategory;
import dev.enterprisepulse.data.generated.ExpenseCategoryKind;
import dev.enterprisepulse.data.generated.LeadSource;
import dev.enterprisepulse.data.generated.OpportunityStage;
import dev.enterprisepulse.data.generated.ProjectMemberRole;
import dev.enterprisepulse.data.generated.ProjectStatus;
import dev.enterprisepulse.data.generated.ProjectTaskPriority;
import dev.enterprisepulse.data.generated.ProjectTaskStatus;
import dev.enterprisepulse.data.generated.TreasuryAccount;
import dev.enterprisepulse.expenses.ExpenseReportService;
import dev.enterprisepulse.projects.ProjectService;
import dev.enterprisepulse.seed.SeedStep;
import dev.enterprisepulse.workflow.WorkflowService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** A converted lead, an open prospect, a negotiation, an overdue meeting, and a project with one paid and one pending expense. */
@Component
public class WorkSeed implements SeedStep {
    private final Db db;
    private final LeadService leads;
    private final OpportunityService opportunities;
    private final ActivityService activities;
    private final ProjectService projects;
    private final ExpenseReportService expenses;
    private final WorkflowService workflow;

    public WorkSeed(Db db, LeadService leads, OpportunityService opportunities, ActivityService activities, ProjectService projects,
                     ExpenseReportService expenses, WorkflowService workflow) {
        this.db = db;
        this.leads = leads;
        this.opportunities = opportunities;
        this.activities = activities;
        this.projects = projects;
        this.expenses = expenses;
        this.workflow = workflow;
    }

    @Override public int order() { return 50; }

    @Override
    public void run(Context ctx) {
        UUID meal = category("YEMEK", "Yemek fişi", ExpenseCategoryKind.RECEIPT, null, new BigDecimal("400"), true);
        UUID perDiem = category("HARCI", "Harcırah", ExpenseCategoryKind.PER_DIEM, new BigDecimal("750"), null, false);
        UUID mileage = category("KM", "Kilometre", ExpenseCategoryKind.MILEAGE, new BigDecimal("6.50"), null, false);
        Employee deniz = db.first("select e from Employee e where e.firstName = 'Deniz' and e.lastName = 'Aksoy'", Employee.class).orElse(null);
        Employee kerem = db.first("select e from Employee e where e.firstName = 'Kerem' and e.lastName = 'Polat'", Employee.class).orElse(null);
        BusinessPartner customer = db.first("select p from BusinessPartner p where p.code = 'MUS-001'", BusinessPartner.class).orElse(null);
        BusinessPartner baskent = db.first("select p from BusinessPartner p where p.code = 'MUS-002'", BusinessPartner.class).orElse(null);
        if (deniz == null || kerem == null || customer == null || baskent == null || meal == null) return;

        LocalDate today = LocalDate.now();
        if (!db.exists("select l.id from Lead l where l.companyName = 'Ege Lojistik'")) {
            var lead = leads.create(new LeadService.Save("Ege Lojistik", "Selin Acar", "selin.acar@ege-lojistik.example", "0232 000 00 00", "İzmir",
                    LeadSource.REFERRAL, new BigDecimal("120000"), "TRY", "Depo yazılımı için yönlendirildi.", ctx.adminId));
            leads.convert(lead.id(), new LeadService.Convert(true, "Depo yazılımı", new BigDecimal("120000")));
            UUID proposal = db.first("select o.id from Opportunity o where o.leadId = ?1", UUID.class, lead.id()).orElseThrow();
            opportunities.changeStage(proposal, OpportunityStage.PROPOSAL, null);
            opportunities.create(new OpportunityService.Save("Şube yenileme", baskent.id, null, new BigDecimal("85000"), "TRY", today.plusDays(21), ctx.adminId, "İkinci şube donanımı."));
            UUID negotiation = db.first("select o.id from Opportunity o where o.partnerId = ?1 and o.title = 'Şube yenileme'", UUID.class, baskent.id).orElseThrow();
            opportunities.changeStage(negotiation, OpportunityStage.NEGOTIATION, null);
            activities.create(new ActivityService.Save(CrmActivityType.MEETING, "Şube keşfi", "Yerinde keşif toplantısı.", Instant.now().minusSeconds(86400), ctx.adminId, null, negotiation, baskent.id));
        }
        if (!db.exists("select l.id from Lead l where l.companyName = 'Marmara Tekstil'")) {
            leads.create(new LeadService.Save("Marmara Tekstil", "Can Demir", "can.demir@marmara-tekstil.example", null, "Bursa",
                    LeadSource.WEBSITE, new BigDecimal("45000"), "TRY", "Web formundan geldi, henüz aranmadı.", ctx.adminId));
        }

        UUID projectId = db.first("select p.id from Project p where p.name = 'Boğaziçi mağaza kurulumu'", UUID.class).orElse(null);
        if (projectId == null) {
            var created = projects.create(new ProjectService.Save("Boğaziçi mağaza kurulumu", "Monitör ve kurulum işinin saha takibi.", customer.id, deniz.id,
                    today.minusDays(10), today.plusDays(20), new BigDecimal("100000"), new BigDecimal("600")));
            projects.changeStatus(created.id(), ProjectStatus.ACTIVE);
            projects.addMember(created.id(), new ProjectService.MemberInput(kerem.id, ProjectMemberRole.MEMBER, new BigDecimal("450")));
            projects.addTask(created.id(), new ProjectService.TaskInput("Monitör montajı", "Mağaza katındaki monitörler.", ProjectTaskPriority.HIGH, kerem.id, today.plusDays(3), new BigDecimal("8")));
            UUID taskId = db.first("select t.id from ProjectTask t where t.projectId = ?1", UUID.class, created.id()).orElseThrow();
            projects.moveTask(taskId, ProjectTaskStatus.IN_PROGRESS);
            projects.logTime(created.id(), new ProjectService.TimeInput(taskId, kerem.id, today.minusDays(1), new BigDecimal("4"), "Montaj ve kablo."));
            projectId = created.id();
        }
        if (db.exists("select r.id from ExpenseReport r")) return;
        TreasuryAccount cash = db.first("select a from TreasuryAccount a where a.code = 'KASA-MRK'", TreasuryAccount.class).orElse(null);
        if (cash == null || perDiem == null || mileage == null) return;
        var paid = expenses.create(new ExpenseReportService.Save(kerem.id, projectId, "İzmir kurulum harcırahı", "İki günlük saha.",
                List.of(new ExpenseReportService.LineInput(perDiem, today.minusDays(2), "Saha harcırahı", null, new BigDecimal("2"), null, null, "TRY", null, null))));
        var submitted = expenses.submit(paid.id());
        if (submitted.workflowInstanceId() != null) workflow.approve(submitted.workflowInstanceId(), "Uygun");
        expenses.pay(paid.id(), new ExpenseReportService.Pay(cash.id, today.minusDays(1)));
        var pending = expenses.create(new ExpenseReportService.Save(kerem.id, projectId, "Saha kilometresi", null,
                List.of(new ExpenseReportService.LineInput(mileage, today.minusDays(1), "İzmir gidiş-dönüş", null, new BigDecimal("40"), null, null, "TRY", null, null))));
        expenses.submit(pending.id());
    }

    private UUID category(String code, String name, ExpenseCategoryKind kind, BigDecimal rate, BigDecimal limit, boolean receipt) {
        ExpenseCategory row = db.first("select c from ExpenseCategory c where c.code = ?1", ExpenseCategory.class, code).orElse(null);
        if (row != null) return row.id;
        row = new ExpenseCategory();
        row.code = code;
        row.name = name;
        row.kind = kind;
        row.rate = rate;
        row.limitAmount = limit;
        row.requiresReceipt = receipt;
        db.add(row);
        db.flush();
        return row.id;
    }
}
