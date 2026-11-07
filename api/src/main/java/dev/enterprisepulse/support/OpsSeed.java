package dev.enterprisepulse.support;

import dev.enterprisepulse.budgets.BudgetService;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.contracts.ContractService;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BudgetItem;
import dev.enterprisepulse.data.generated.BudgetItemKind;
import dev.enterprisepulse.data.generated.BudgetSource;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.ContractBillingCycle;
import dev.enterprisepulse.data.generated.ContractFlow;
import dev.enterprisepulse.data.generated.ContractParty;
import dev.enterprisepulse.data.generated.ContractType;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.TicketCategory;
import dev.enterprisepulse.data.generated.TicketPriority;
import dev.enterprisepulse.data.generated.TicketStatus;
import dev.enterprisepulse.data.generated.VehicleDocumentType;
import dev.enterprisepulse.data.generated.VehicleFuelType;
import dev.enterprisepulse.data.generated.VehicleOwnership;
import dev.enterprisepulse.data.generated.VehicleServiceType;
import dev.enterprisepulse.fleet.FleetService;
import dev.enterprisepulse.seed.SeedStep;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Open support ticket, expiring service contract, approved company budget and one assigned vehicle. */
@Component
public class OpsSeed implements SeedStep {
    private final Db db;
    private final SupportService support;
    private final ContractService contracts;
    private final BudgetService budgets;
    private final FleetService fleet;

    public OpsSeed(Db db, SupportService support, ContractService contracts, BudgetService budgets, FleetService fleet) {
        this.db = db;
        this.support = support;
        this.contracts = contracts;
        this.budgets = budgets;
        this.fleet = fleet;
    }

    @Override public int order() { return 70; }

    @Override
    public void run(Context ctx) {
        Employee kerem = db.first("select e from Employee e where e.firstName = 'Kerem' and e.lastName = 'Polat'", Employee.class).orElse(null);
        UUID keremUser = kerem == null ? null : kerem.userId;
        BusinessPartner customer = db.first("select p from BusinessPartner p where p.code = 'MUS-001'", BusinessPartner.class).orElse(null);
        if (kerem == null || keremUser == null || customer == null) return;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        support(ctx.pulseId, ctx.adminId, keremUser);
        contracts(customer.id, kerem.id, ctx.adminId, today);
        budgets(today.getYear(), ctx.adminId);
        fleet(kerem.id, today);
    }

    private void support(UUID pulseId, UUID adminId, UUID keremUserId) {
        support.ensureDefaults();
        TicketCategory category = db.first("select c from TicketCategory c where c.name = 'BT Destek'", TicketCategory.class).orElse(null);
        if (category == null) {
            category = new TicketCategory();
            category.name = "BT Destek";
            category.defaultAssigneeId = adminId;
            category.sortOrder = 1;
            db.add(category);
            db.flush();
        }
        if (db.exists("select t.id from Ticket t")) return;
        SupportService.TicketDto created;
        try (var scope = CurrentUser.get().actAs(pulseId, keremUserId, false)) {
            created = support.create(new SupportService.SaveTicket("Monitör montajında ses yok", "Kurulum sonrası hoparlör çıkışı gelmiyor.", category.id, TicketPriority.HIGH, null));
        }
        support.changeStatus(created.id(), new SupportService.ChangeTicketStatus(TicketStatus.IN_PROGRESS, "İncelemeye alındı"));
    }

    private void contracts(UUID partnerId, UUID keremId, UUID adminId, LocalDate today) {
        if (db.exists("select t.id from ContractType t")) return;
        ContractType service = new ContractType();
        service.name = "Hizmet";
        service.party = ContractParty.PARTNER;
        service.flow = ContractFlow.INCOME;
        service.defaultTermMonths = 12;
        service.defaultNoticeDays = 30;
        service.sortOrder = 1;
        ContractType employment = new ContractType();
        employment.name = "İş sözleşmesi";
        employment.party = ContractParty.EMPLOYEE;
        employment.flow = ContractFlow.NONE;
        employment.defaultTermMonths = 12;
        employment.defaultNoticeDays = 30;
        employment.sortOrder = 2;
        db.add(service);
        db.add(employment);
        db.flush();

        ContractService.ContractDto active = contracts.create(new ContractService.SaveContract("Boğaziçi bakım hizmeti", service.id, partnerId, null, adminId,
                today.minusMonths(10), today.plusDays(20), today.minusMonths(10), new BigDecimal("15000"), "TRY", ContractBillingCycle.MONTHLY, true, 12, 30, 30, "Yıllık bakım"));
        contracts.activate(active.id(), null);

        ContractService.ContractDto job = contracts.create(new ContractService.SaveContract("Kerem Polat iş sözleşmesi", employment.id, null, keremId, adminId,
                today.minusYears(1), today.plusMonths(2), today.minusYears(1), null, "TRY", ContractBillingCycle.ONE_TIME, false, null, 30, 30, null));
        contracts.activate(job.id(), null);
        contracts.runCompany();
    }

    private void budgets(int year, UUID adminId) {
        if (db.exists("select b.id from Budget b where b.year = ?1", year)) return;
        BudgetItem payroll = budgets.saveItem(new BudgetService.SaveBudgetItem("PERSONEL", "Personel maliyeti", BudgetItemKind.EXPENSE, BudgetSource.PAYROLL, null));
        BudgetItem sales = budgets.saveItem(new BudgetService.SaveBudgetItem("SATIS", "Satış geliri", BudgetItemKind.INCOME, BudgetSource.SALES, null));
        BudgetItem expense = budgets.saveItem(new BudgetService.SaveBudgetItem("MASRAF", "Masraflar", BudgetItemKind.EXPENSE, BudgetSource.EXPENSES, null));
        BudgetItem other = budgets.saveItem(new BudgetService.SaveBudgetItem("DIGER", "Diğer gider", BudgetItemKind.EXPENSE, BudgetSource.MANUAL, null));
        BudgetService.BudgetDto budget = budgets.create(new BudgetService.SaveBudget(year, null, year + " şirket bütçesi", adminId, null));
        List<BudgetService.PlanLine> lines = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            lines.add(new BudgetService.PlanLine(payroll.id, month, new BigDecimal("200000")));
            lines.add(new BudgetService.PlanLine(sales.id, month, new BigDecimal("350000")));
            lines.add(new BudgetService.PlanLine(expense.id, month, new BigDecimal("25000")));
            lines.add(new BudgetService.PlanLine(other.id, month, new BigDecimal("10000")));
        }
        budgets.savePlan(budget.id(), new BudgetService.SavePlan(lines));
        budgets.approve(budget.id());
        budgets.addEntry(new BudgetService.SaveEntry(other.id, null, LocalDate.now(ZoneOffset.UTC).minusDays(5), new BigDecimal("8500"), "Ofis kirası"));
    }

    private void fleet(UUID keremId, LocalDate today) {
        if (db.exists("select v.id from Vehicle v")) return;
        FleetService.VehicleDto vehicle = fleet.create(new FleetService.SaveVehicle("34 EP 001", "Toyota", "Corolla", 2022, "Beyaz", VehicleFuelType.HYBRID,
                VehicleOwnership.OWNED, null, null, 15000, 12, null));
        fleet.assign(vehicle.id(), new FleetService.AssignVehicle(keremId, today.minusDays(40), 42000, "Saha aracı"));
        fleet.addFuel(vehicle.id(), new FleetService.SaveFuel(today.minusDays(30), 42350, new BigDecimal("40"), new BigDecimal("1800"), true, "Shell Levent", null));
        fleet.addFuel(vehicle.id(), new FleetService.SaveFuel(today.minusDays(5), 42800, new BigDecimal("38"), new BigDecimal("1710"), true, "Opet Maslak", null));
        fleet.addService(vehicle.id(), new FleetService.SaveService(VehicleServiceType.PERIODIC, today.minusDays(60), 41000, null, "Periyodik bakım", new BigDecimal("4500")));
        fleet.addDocument(vehicle.id(), new FleetService.SaveDocument(VehicleDocumentType.INSPECTION, today.minusMonths(6), today.plusDays(25), "TÜVTÜRK", null, new BigDecimal("2500"), null));
        fleet.addDocument(vehicle.id(), new FleetService.SaveDocument(VehicleDocumentType.TRAFFIC_INSURANCE, today.minusMonths(3), today.plusMonths(9), "Anadolu Sigorta", "POL-7781", new BigDecimal("12000"), null));
    }
}
