package dev.enterprisepulse.expenses;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Company;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.ExpenseCategory;
import dev.enterprisepulse.data.generated.ExpenseCategoryKind;
import dev.enterprisepulse.data.generated.ExpenseLine;
import dev.enterprisepulse.data.generated.ExpenseReport;
import dev.enterprisepulse.data.generated.ExpenseReportStatus;
import dev.enterprisepulse.data.generated.Project;
import dev.enterprisepulse.data.generated.ProjectStatus;
import dev.enterprisepulse.data.generated.TreasuryAccount;
import dev.enterprisepulse.data.generated.TreasuryDocumentType;
import dev.enterprisepulse.data.generated.WorkflowStatus;
import dev.enterprisepulse.finance.TreasuryService;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.projects.ProjectService;
import dev.enterprisepulse.trade.DocumentMath;
import dev.enterprisepulse.trade.TradeService;
import dev.enterprisepulse.workflow.WorkflowService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExpenseReportService {
    public static final String WORKFLOW = "EXPENSE_REPORT";
    public static final String ENTITY = "ExpenseReport";

    public record LineInput(UUID categoryId, LocalDate expenseDate, String description, String merchant, BigDecimal quantity, BigDecimal amount,
                             BigDecimal vatAmount, String currencyCode, BigDecimal exchangeRate, UUID receiptFileId) {}

    public record Save(UUID employeeId, UUID projectId, String title, String description, List<LineInput> lines) {}

    public record Pay(UUID accountId, LocalDate date) {}

    public record LineDto(UUID id, int lineNo, UUID categoryId, String categoryName, ExpenseCategoryKind kind, LocalDate expenseDate, String description,
                           String merchant, BigDecimal quantity, BigDecimal amount, BigDecimal vatAmount, String currencyCode, BigDecimal exchangeRate,
                           BigDecimal amountBase, boolean overLimit, boolean needsReceipt) {}

    public record Detail(UUID id, String number, UUID employeeId, String employeeName, UUID projectId, String projectName, String title, String description,
                          ExpenseReportStatus status, BigDecimal totalAmount, UUID workflowInstanceId, Instant submittedAt, Instant decidedAt,
                          UUID paymentAccountId, LocalDate paymentDate, boolean canSubmit, boolean canPay, boolean canCancel, List<LineDto> lines) {}

    public record ListResult(List<Detail> items) {}

    private final Db db;
    private final NumberingService numbering;
    private final WorkflowService workflow;
    private final TreasuryService treasury;
    private final TradeService trade;
    private final ProjectService projects;
    private final NotificationService notifications;

    public ExpenseReportService(Db db, NumberingService numbering, WorkflowService workflow, TreasuryService treasury, TradeService trade,
                                 ProjectService projects, NotificationService notifications) {
        this.db = db;
        this.numbering = numbering;
        this.workflow = workflow;
        this.treasury = treasury;
        this.trade = trade;
        this.projects = projects;
        this.notifications = notifications;
    }

    public Detail create(Save dto) {
        Employee employee = employee(dto.employeeId(), true);
        ExpenseReport report = new ExpenseReport();
        report.number = numbering.next("EXPENSE_REPORT");
        report.employeeId = employee.id;
        report.title = title(dto.title());
        report.description = blank(dto.description());
        report.projectId = project(dto.projectId());
        report.createdById = CurrentUser.get().requireUserId();
        report.totalAmount = BigDecimal.ZERO;
        db.add(report);
        db.flush();
        fill(report, dto.lines());
        return get(report.id);
    }

    public Detail update(UUID id, Save dto) {
        ExpenseReport report = editable(id);
        report.title = title(dto.title());
        report.description = blank(dto.description());
        report.projectId = project(dto.projectId());
        db.execute("delete from ExpenseLine l where l.reportId = ?1", id);
        db.flush();
        fill(report, dto.lines());
        return get(id);
    }

    public Detail submit(UUID id) {
        ExpenseReport report = editable(id);
        Guard.that(report.totalAmount.signum() > 0, "Masraf formunda satır olmalı");
        boolean missing = db.exists("select l.id from ExpenseLine l, ExpenseCategory c where l.categoryId = c.id and l.reportId = ?1 and l.receiptFileId is null and c.requiresReceipt = true and c.kind = ?2", id, ExpenseCategoryKind.RECEIPT);
        Guard.that(!missing, "Fiş zorunlu satıra fiş eklenmeden form gönderilemez");
        boolean hasWorkflow = db.exists("select s.id from WorkflowStep s, WorkflowDefinition d where s.definitionId = d.id and d.code = ?1 and d.isActive = true", WORKFLOW);
        report.submittedAt = Instant.now();
        if (hasWorkflow) {
            report.status = ExpenseReportStatus.PENDING;
            boolean over = db.exists("select l.id from ExpenseLine l, ExpenseCategory c where l.categoryId = c.id and l.reportId = ?1 and c.limitAmount is not null and l.amountBase > c.limitAmount", id);
            var instance = workflow.start(new WorkflowService.StartRequest(WORKFLOW, report.id.toString(),
                    over ? report.number + " — " + report.title + " (limit aşımı)" : report.number + " — " + report.title));
            report.workflowInstanceId = instance.id();
        } else {
            report.status = ExpenseReportStatus.APPROVED;
            report.decidedAt = Instant.now();
        }
        return get(id);
    }

    public void decide(UUID id, ExpenseReportStatus status) {
        ExpenseReport report = db.first("select r from ExpenseReport r where r.id = ?1 and r.status = ?2", ExpenseReport.class, id, ExpenseReportStatus.PENDING).orElse(null);
        if (report == null) return;
        report.status = status;
        report.decidedAt = Instant.now();
        Employee employee = db.get(Employee.class, report.employeeId);
        if (employee.userId != null && !employee.userId.equals(CurrentUser.get().userId)) {
            notifications.notify(new NotificationService.NotifyRequest(List.of(employee.userId), report.companyId, "expenses.report_decided",
                    status == ExpenseReportStatus.APPROVED ? "Masraf formunuz onaylandı" : "Masraf formunuz reddedildi",
                    report.number + " — " + report.title, "/expenses/reports/" + report.id));
        }
    }

    public Detail pay(UUID id, Pay dto) {
        Guard.that(CurrentUser.get().has("expenses:pay"), "Masraf ödeme yetkiniz yok");
        ExpenseReport report = Guard.found(db.first("select r from ExpenseReport r where r.id = ?1", ExpenseReport.class, id), "Masraf formu bulunamadı");
        Guard.state(report.status == ExpenseReportStatus.APPROVED, "Yalnızca onaylı form ödenir");
        TreasuryAccount account = Guard.found(db.first("select a from TreasuryAccount a where a.id = ?1 and a.isActive = true", TreasuryAccount.class, dto.accountId()), "Kasa veya banka hesabı bulunamadı");
        Company company = db.get(Company.class, CurrentUser.get().requireCompanyId());
        Guard.that(account.currencyCode.equals(company.baseCurrency), "Ödeme hesabının para birimi şirket para birimi olmalı");
        LocalDate date = dto.date() == null ? LocalDate.now() : dto.date();
        Guard.that(!date.isAfter(LocalDate.now().plusDays(1)), "Ödeme tarihi ileri olamaz");
        treasury.post(account.id, date, TreasuryDocumentType.EXPENSE_PAYMENT, report.id, report.number, report.title, BigDecimal.ZERO, report.totalAmount);
        report.status = ExpenseReportStatus.PAID;
        report.paymentAccountId = account.id;
        report.paymentDate = date;
        report.paidAt = Instant.now();
        report.paidById = CurrentUser.get().requireUserId();
        if (report.projectId != null) projects.addExpenseCost(report.projectId, report.totalAmount);
        return get(id);
    }

    public Detail cancel(UUID id, String reason) {
        ExpenseReport report = visible(id);
        Employee employee = db.get(Employee.class, report.employeeId);
        boolean mine = employee.userId != null && employee.userId.equals(CurrentUser.get().userId);
        Guard.that(mine || CurrentUser.get().has("expenses:update"), "Masraf formunu yalnızca sahibi iptal edebilir");
        Guard.state(report.status == ExpenseReportStatus.DRAFT || report.status == ExpenseReportStatus.PENDING || report.status == ExpenseReportStatus.APPROVED || report.status == ExpenseReportStatus.PAID, "Form zaten kapanmış");
        if (report.status == ExpenseReportStatus.PENDING && report.workflowInstanceId != null) workflow.cancel(report.workflowInstanceId, reason);
        if (report.status == ExpenseReportStatus.PAID) {
            treasury.reverse(TreasuryDocumentType.EXPENSE_PAYMENT, report.id, LocalDate.now(), reason == null ? "Masraf ödemesi iptal" : reason);
            if (report.projectId != null) projects.addExpenseCost(report.projectId, report.totalAmount.negate());
        }
        report.status = ExpenseReportStatus.CANCELLED;
        report.decidedAt = Instant.now();
        return get(id);
    }

    @Transactional(readOnly = true)
    public ListResult mine() {
        UUID employeeId = db.first("select e.id from Employee e where e.userId = ?1", UUID.class, CurrentUser.get().userId).orElse(null);
        if (employeeId == null) return new ListResult(List.of());
        List<UUID> ids = db.list("select r.id from ExpenseReport r where r.employeeId = ?1 order by r.createdAt desc", UUID.class, employeeId);
        List<Detail> items = new ArrayList<>();
        for (UUID id : ids) items.add(detail(id, true));
        return new ListResult(items);
    }

    @Transactional(readOnly = true)
    public Detail get(UUID id) {
        return detail(id, false);
    }

    private Detail detail(UUID id, boolean ownBypass) {
        ExpenseReport report = Guard.found(db.first("select r from ExpenseReport r where r.id = ?1", ExpenseReport.class, id), "Masraf formu bulunamadı");
        Employee employee = db.get(Employee.class, report.employeeId);
        if (!ownBypass && !CurrentUser.get().has("expenses:read") && (employee.userId == null || !employee.userId.equals(CurrentUser.get().userId))) {
            throw dev.enterprisepulse.common.AppException.notFound("Masraf formu bulunamadı");
        }
        Project project = report.projectId == null ? null : db.find(Project.class, report.projectId);
        boolean mine = employee.userId != null && employee.userId.equals(CurrentUser.get().userId);
        List<LineDto> lines = new ArrayList<>();
        for (ExpenseLine line : db.list("select l from ExpenseLine l where l.reportId = ?1 order by l.lineNo", ExpenseLine.class, id)) {
            ExpenseCategory category = db.get(ExpenseCategory.class, line.categoryId);
            boolean over = category.limitAmount != null && line.amountBase.compareTo(category.limitAmount) > 0;
            boolean needs = category.kind == ExpenseCategoryKind.RECEIPT && category.requiresReceipt && line.receiptFileId == null;
            lines.add(new LineDto(line.id, line.lineNo, line.categoryId, category.name, category.kind, line.expenseDate, line.description, line.merchant,
                    line.quantity, line.amount, line.vatAmount, line.currencyCode, line.exchangeRate, line.amountBase, over, needs));
        }
        return new Detail(report.id, report.number, report.employeeId, employee.firstName + " " + employee.lastName, report.projectId, project == null ? null : project.name,
                report.title, report.description, report.status, report.totalAmount, report.workflowInstanceId, report.submittedAt, report.decidedAt, report.paymentAccountId, report.paymentDate,
                report.status == ExpenseReportStatus.DRAFT && (mine || CurrentUser.get().has("expenses:update")),
                report.status == ExpenseReportStatus.APPROVED && CurrentUser.get().has("expenses:pay"),
                (report.status == ExpenseReportStatus.DRAFT || report.status == ExpenseReportStatus.PENDING || report.status == ExpenseReportStatus.APPROVED || report.status == ExpenseReportStatus.PAID)
                        && (mine || CurrentUser.get().has("expenses:update")),
                lines);
    }

    private void fill(ExpenseReport report, List<LineInput> lines) {
        Guard.that(lines != null && !lines.isEmpty(), "En az bir satır gerekli");
        BigDecimal total = BigDecimal.ZERO;
        int no = 1;
        for (LineInput input : lines) {
            ExpenseCategory category = Guard.found(db.first("select c from ExpenseCategory c where c.id = ?1 and c.isActive = true", ExpenseCategory.class, input.categoryId()), "Satır " + no + ": masraf türü bulunamadı");
            Guard.that(input.description() != null && !input.description().isBlank(), "Satır " + no + ": açıklama gerekli");
            Guard.that(input.expenseDate() != null && !input.expenseDate().isAfter(LocalDate.now().plusDays(1)), "Satır " + no + ": tarih ileri olamaz");
            BigDecimal quantity = input.quantity() == null ? BigDecimal.ONE : input.quantity();
            Guard.that(quantity.signum() > 0, "Satır " + no + ": miktar sıfırdan büyük olmalı");
            BigDecimal amount;
            if (category.kind == ExpenseCategoryKind.RECEIPT) {
                Guard.that(input.amount() != null && input.amount().signum() > 0, "Satır " + no + ": tutar sıfırdan büyük olmalı");
                amount = input.amount();
            } else {
                Guard.that(category.rate != null && category.rate.signum() > 0, "Satır " + no + ": birim tutar tanımlı değil");
                amount = quantity.multiply(category.rate).setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal vat = input.vatAmount() == null ? BigDecimal.ZERO : input.vatAmount();
            Guard.that(vat.signum() >= 0 && vat.compareTo(amount) <= 0, "Satır " + no + ": KDV tutarı satır tutarını aşamaz");
            var money = trade.resolveCurrency(input.currencyCode(), input.exchangeRate(), input.expenseDate());
            if (input.receiptFileId() != null) Guard.that(db.exists("select f.id from FileObject f where f.id = ?1", input.receiptFileId()), "Satır " + no + ": fiş dosyası bulunamadı");
            ExpenseLine line = new ExpenseLine();
            line.reportId = report.id;
            line.lineNo = no;
            line.categoryId = category.id;
            line.expenseDate = input.expenseDate();
            line.description = input.description().trim();
            line.merchant = blank(input.merchant());
            line.quantity = quantity;
            line.amount = amount;
            line.vatAmount = vat;
            line.currencyCode = money.currencyCode();
            line.exchangeRate = money.exchangeRate();
            line.amountBase = DocumentMath.toBase(amount, money.exchangeRate());
            line.receiptFileId = input.receiptFileId();
            db.add(line);
            total = total.add(line.amountBase);
            no++;
        }
        report.totalAmount = total.setScale(2, RoundingMode.HALF_UP);
    }

    private ExpenseReport editable(UUID id) {
        ExpenseReport report = visible(id);
        Guard.state(report.status == ExpenseReportStatus.DRAFT, "Yalnızca taslak form düzenlenir");
        Employee employee = db.get(Employee.class, report.employeeId);
        Guard.that(employee.userId != null && employee.userId.equals(CurrentUser.get().userId) || CurrentUser.get().has("expenses:update"), "Taslağı yalnızca sahibi düzenler");
        return report;
    }

    private ExpenseReport visible(UUID id) {
        ExpenseReport report = Guard.found(db.first("select r from ExpenseReport r where r.id = ?1", ExpenseReport.class, id), "Masraf formu bulunamadı");
        Employee employee = db.get(Employee.class, report.employeeId);
        if (!CurrentUser.get().has("expenses:read") && (employee.userId == null || !employee.userId.equals(CurrentUser.get().userId))) {
            throw dev.enterprisepulse.common.AppException.notFound("Masraf formu bulunamadı");
        }
        return report;
    }

    private Employee employee(UUID employeeId, boolean creating) {
        if (employeeId == null) {
            return Guard.found(db.first("select e from Employee e where e.userId = ?1", Employee.class, CurrentUser.get().userId), "Personel kaydınız yok");
        }
        Guard.that(CurrentUser.get().has(creating ? "expenses:create" : "expenses:update"), "Başkası adına masraf formu yetkiniz yok");
        return Guard.found(db.first("select e from Employee e where e.id = ?1", Employee.class, employeeId), "Personel bulunamadı");
    }

    private UUID project(UUID projectId) {
        if (projectId == null) return null;
        Guard.that(db.exists("select p.id from Project p where p.id = ?1 and p.status <> ?2", projectId, ProjectStatus.CANCELLED), "Proje bulunamadı");
        return projectId;
    }

    private static String title(String value) {
        Guard.that(value != null && !value.isBlank(), "Başlık gerekli");
        return value.trim();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Component
    public static class WorkflowListener implements WorkflowService.Listener {
        private final ObjectProvider<ExpenseReportService> reports;

        public WorkflowListener(ObjectProvider<ExpenseReportService> reports) {
            this.reports = reports;
        }

        @Override public String entityType() { return ENTITY; }

        @Override
        public void onFinished(WorkflowService.Finished e) {
            UUID id;
            try { id = UUID.fromString(e.entityId()); } catch (IllegalArgumentException ex) { return; }
            ExpenseReportStatus status = e.status() == WorkflowStatus.APPROVED ? ExpenseReportStatus.APPROVED
                    : e.status() == WorkflowStatus.REJECTED ? ExpenseReportStatus.REJECTED : ExpenseReportStatus.CANCELLED;
            reports.getObject().decide(id, status);
        }
    }
}
