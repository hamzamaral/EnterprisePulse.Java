package dev.enterprisepulse.payroll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Company;
import dev.enterprisepulse.data.generated.Department;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.data.generated.PayrollParameter;
import dev.enterprisepulse.data.generated.PayrollPeriod;
import dev.enterprisepulse.data.generated.PayrollPeriodStatus;
import dev.enterprisepulse.data.generated.Payslip;
import dev.enterprisepulse.data.generated.PayslipItem;
import dev.enterprisepulse.data.generated.PayslipItemKind;
import dev.enterprisepulse.data.generated.Position;
import dev.enterprisepulse.data.generated.SalaryRecord;
import dev.enterprisepulse.data.generated.TreasuryAccount;
import dev.enterprisepulse.data.generated.TreasuryDocumentType;
import dev.enterprisepulse.finance.TreasuryService;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aylık bordro. SGK tavanı, kümülatif gelir vergisi ve asgari ücret istisnası dönem hesabına girer. */
@Service
@Transactional
public class PayrollService {
    public record TaxBracket(BigDecimal upTo, BigDecimal rate) {}
    public record SaveParameter(int year, BigDecimal minimumWage, BigDecimal sgkCeiling, BigDecimal sgkEmployeeRate, BigDecimal unemploymentEmployeeRate,
                                 BigDecimal sgkEmployerRate, BigDecimal unemploymentEmployerRate, BigDecimal stampTaxRate, BigDecimal overtimeRate, int monthlyHours, List<TaxBracket> taxBrackets) {}
    public record SaveSalary(UUID employeeId, LocalDate effectiveFrom, BigDecimal grossSalary, String note) {}
    public record SalaryDto(UUID id, UUID employeeId, BigDecimal grossSalary) {}
    public record PayslipAdjust(BigDecimal overtimeHours, Integer missingDays, String earningDescription, BigDecimal earningAmount, String deductionDescription, BigDecimal deductionAmount) {}
    public record PayPayroll(UUID accountId, LocalDate date) {}
    public record PayslipDto(UUID id, UUID employeeId, String employeeNo, String employeeName, BigDecimal monthlySalary, int payDays, BigDecimal overtimeHours,
                              BigDecimal grossPay, BigDecimal sgkEmployee, BigDecimal unemploymentEmployee, BigDecimal incomeTax, BigDecimal incomeTaxExemption,
                              BigDecimal stampTax, BigDecimal stampTaxExemption, BigDecimal otherDeductions, BigDecimal netPay, BigDecimal employerCost) {}
    public record MyPayslip(UUID id, UUID periodId, String number, int year, int month, BigDecimal grossPay, BigDecimal netPay, PayrollPeriodStatus status) {}
    public record Mine(List<MyPayslip> items) {}
    public record PeriodDto(UUID id, String number, int year, int month, PayrollPeriodStatus status, int employeeCount, BigDecimal grossTotal, BigDecimal netTotal,
                             BigDecimal sgkTotal, BigDecimal taxTotal, BigDecimal employerCostTotal, boolean canApprove, boolean canPay, List<PayslipDto> payslips) {}
    private record Bracket(BigDecimal upTo, BigDecimal rate) {}

    private final Db db;
    private final NumberingService numbering;
    private final TreasuryService treasury;
    private final ObjectMapper json = new ObjectMapper();

    public PayrollService(Db db, NumberingService numbering, TreasuryService treasury) {
        this.db = db;
        this.numbering = numbering;
        this.treasury = treasury;
    }

    public PayrollParameter saveParameter(SaveParameter dto) {
        CurrentUser.get().require("payroll:update");
        Guard.that(dto.taxBrackets() != null && !dto.taxBrackets().isEmpty() && dto.taxBrackets().stream().allMatch(b -> b.rate() != null && b.rate().signum() >= 0 && b.rate().compareTo(BigDecimal.ONE) < 0), "Vergi dilimleri geçersiz");
        Guard.that(dto.minimumWage().signum() > 0 && dto.sgkCeiling().compareTo(dto.minimumWage()) >= 0 && dto.monthlyHours() > 0, "Bordro parametreleri geçersiz");
        PayrollParameter row = db.first("select p from PayrollParameter p where p.year = ?1", PayrollParameter.class, dto.year()).orElse(null);
        boolean isNew = row == null;
        if (isNew) {
            row = new PayrollParameter();
            row.year = dto.year();
        }
        row.minimumWage = dto.minimumWage();
        row.sgkCeiling = dto.sgkCeiling();
        row.sgkEmployeeRate = dto.sgkEmployeeRate();
        row.unemploymentEmployeeRate = dto.unemploymentEmployeeRate();
        row.sgkEmployerRate = dto.sgkEmployerRate();
        row.unemploymentEmployerRate = dto.unemploymentEmployerRate();
        row.stampTaxRate = dto.stampTaxRate();
        row.overtimeRate = dto.overtimeRate();
        row.monthlyHours = dto.monthlyHours();
        row.taxBrackets = json.valueToTree(dto.taxBrackets());
        if (isNew) db.add(row);
        db.flush();
        return row;
    }

    public SalaryDto saveSalary(SaveSalary dto) {
        CurrentUser.get().require("payroll:update");
        Guard.that(dto.grossSalary() != null && dto.grossSalary().signum() > 0, "Brüt ücret sıfırdan büyük olmalı");
        Guard.that(db.exists("select e.id from Employee e where e.id = ?1", dto.employeeId()), "Personel bulunamadı");
        SalaryRecord row = db.first("select s from SalaryRecord s where s.employeeId = ?1 and s.effectiveFrom = ?2", SalaryRecord.class, dto.employeeId(), dto.effectiveFrom()).orElse(null);
        boolean isNew = row == null;
        if (isNew) {
            row = new SalaryRecord();
            row.employeeId = dto.employeeId();
            row.effectiveFrom = dto.effectiveFrom();
            row.createdById = CurrentUser.get().requireUserId();
        }
        row.grossSalary = dto.grossSalary();
        row.note = blank(dto.note());
        if (isNew) db.add(row);
        db.flush();
        return new SalaryDto(row.id, row.employeeId, row.grossSalary);
    }

    public PeriodDto open(int year, int month) {
        CurrentUser.get().require("payroll:create");
        Guard.that(month >= 1 && month <= 12, "Ay 1 ile 12 arasında olmalı");
        Guard.state(!db.exists("select p.id from PayrollPeriod p where p.year = ?1 and p.month = ?2", year, month), "Bu ayın bordrosu zaten var");
        Guard.that(db.exists("select p.id from PayrollParameter p where p.year = ?1", year), "Bu yılın bordro parametresi yok");
        PayrollPeriod period = new PayrollPeriod();
        period.number = numbering.next("PAYROLL");
        period.year = year;
        period.month = month;
        period.createdById = CurrentUser.get().requireUserId();
        db.add(period);
        db.flush();
        calculate(period.id, null);
        return get(period.id);
    }

    public PeriodDto adjust(UUID payslipId, PayslipAdjust dto) {
        Payslip slip = Guard.found(db.first("select s from Payslip s where s.id = ?1", Payslip.class, payslipId), "Bordro pusulası bulunamadı");
        PayrollPeriod period = db.get(PayrollPeriod.class, slip.periodId);
        Guard.state(period.status == PayrollPeriodStatus.DRAFT, "Onaylı bordro değişmez");
        CurrentUser.get().require("payroll:update");
        if (dto.overtimeHours() != null) {
            Guard.that(dto.overtimeHours().signum() >= 0, "Fazla mesai negatif olamaz");
            slip.overtimeHours = dto.overtimeHours();
        }
        if (dto.missingDays() != null) {
            Guard.that(dto.missingDays() >= 0, "Eksik gün negatif olamaz");
            slip.missingDays = dto.missingDays();
        }
        if (dto.earningAmount() != null && dto.earningAmount().signum() > 0) addItem(slip.id, PayslipItemKind.EARNING, dto.earningDescription(), dto.earningAmount());
        if (dto.deductionAmount() != null && dto.deductionAmount().signum() > 0) addItem(slip.id, PayslipItemKind.DEDUCTION, dto.deductionDescription(), dto.deductionAmount());
        db.flush();
        calculate(slip.periodId, slip.id);
        return get(slip.periodId);
    }

    public PeriodDto approve(UUID id) {
        CurrentUser.get().require("payroll:approve");
        PayrollPeriod period = Guard.found(db.first("select p from PayrollPeriod p where p.id = ?1", PayrollPeriod.class, id), "Bordro dönemi bulunamadı");
        Guard.state(period.status == PayrollPeriodStatus.DRAFT, "Yalnızca taslak bordro onaylanır");
        Guard.that(db.count("select count(s) from Payslip s where s.periodId = ?1", id) > 0, "Pusulasız bordro onaylanamaz");
        period.status = PayrollPeriodStatus.APPROVED;
        period.approvedAt = Instant.now();
        period.approvedById = CurrentUser.get().requireUserId();
        db.flush();
        return get(id);
    }

    public PeriodDto pay(UUID id, PayPayroll dto) {
        CurrentUser.get().require("payroll:pay");
        PayrollPeriod period = Guard.found(db.first("select p from PayrollPeriod p where p.id = ?1", PayrollPeriod.class, id), "Bordro dönemi bulunamadı");
        Guard.state(period.status == PayrollPeriodStatus.APPROVED, "Yalnızca onaylı bordro ödenir");
        TreasuryAccount account = Guard.found(db.first("select a from TreasuryAccount a where a.id = ?1 and a.isActive = true", TreasuryAccount.class, dto.accountId()), "Kasa veya banka hesabı bulunamadı");
        Company company = db.get(Company.class, CurrentUser.get().requireCompanyId());
        Guard.that(account.currencyCode.equals(company.baseCurrency), "Ödeme hesabının para birimi şirket para birimi olmalı");
        LocalDate date = dto.date() == null ? LocalDate.now() : dto.date();
        treasury.post(account.id, date, TreasuryDocumentType.PAYROLL_PAYMENT, period.id, period.number, period.year + "/" + String.format("%02d", period.month) + " bordro", BigDecimal.ZERO, period.netTotal);
        period.status = PayrollPeriodStatus.PAID;
        period.paymentAccountId = account.id;
        period.paymentDate = date;
        period.paidAt = Instant.now();
        period.paidById = CurrentUser.get().requireUserId();
        db.flush();
        return get(id);
    }

    @Transactional(readOnly = true)
    public Mine mine() {
        UUID userId = CurrentUser.get().userId;
        Employee employee = userId == null ? null : db.first("select e from Employee e where e.userId = ?1", Employee.class, userId).orElse(null);
        if (employee == null) return new Mine(List.of());
        List<MyPayslip> items = new ArrayList<>();
        for (Payslip slip : db.list("select s from Payslip s where s.employeeId = ?1", Payslip.class, employee.id)) {
            PayrollPeriod period = db.get(PayrollPeriod.class, slip.periodId);
            if (period.status == PayrollPeriodStatus.APPROVED || period.status == PayrollPeriodStatus.PAID)
                items.add(new MyPayslip(slip.id, period.id, period.number, period.year, period.month, slip.grossPay, slip.netPay, period.status));
        }
        items.sort(Comparator.comparing(MyPayslip::year).thenComparing(MyPayslip::month).reversed());
        return new Mine(items);
    }

    @Transactional(readOnly = true)
    public PeriodDto get(UUID id) {
        CurrentUser.get().require("payroll:read");
        PayrollPeriod period = Guard.found(db.first("select p from PayrollPeriod p where p.id = ?1", PayrollPeriod.class, id), "Bordro dönemi bulunamadı");
        List<PayslipDto> slips = new ArrayList<>();
        for (Payslip slip : db.list("select s from Payslip s where s.periodId = ?1 order by s.employeeName", Payslip.class, id)) slips.add(mapSlip(slip));
        return new PeriodDto(period.id, period.number, period.year, period.month, period.status, period.employeeCount, period.grossTotal, period.netTotal,
                period.sgkTotal, period.taxTotal, period.employerCostTotal, period.status == PayrollPeriodStatus.DRAFT, period.status == PayrollPeriodStatus.APPROVED, slips);
    }

    private void calculate(UUID periodId, UUID onlySlipId) {
        PayrollPeriod period = db.get(PayrollPeriod.class, periodId);
        PayrollParameter parameter = db.one("select p from PayrollParameter p where p.year = ?1", PayrollParameter.class, period.year);
        List<Bracket> brackets = brackets(parameter.taxBrackets);
        LocalDate start = LocalDate.of(period.year, period.month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        List<Payslip> slips = new ArrayList<>(db.list("select s from Payslip s where s.periodId = ?1", Payslip.class, period.id));
        boolean creating = slips.isEmpty();
        if (creating) {
            for (Employee employee : db.list("select e from Employee e where e.hireDate <= ?1 and (e.status <> ?2 or e.terminationDate >= ?3)", Employee.class, end, EmployeeStatus.TERMINATED, start)) {
                SalaryRecord salary = db.first("select s from SalaryRecord s where s.employeeId = ?1 and s.effectiveFrom <= ?2 order by s.effectiveFrom desc", SalaryRecord.class, employee.id, end).orElse(null);
                if (salary == null) continue;
                int employed = employedDays(employee.hireDate, employee.terminationDate, start, end);
                if (employed <= 0) continue;
                Payslip slip = new Payslip();
                slip.periodId = period.id;
                slip.employeeId = employee.id;
                slip.employeeNo = employee.employeeNo;
                slip.employeeName = employee.firstName + " " + employee.lastName;
                Department department = employee.departmentId == null ? null : db.find(Department.class, employee.departmentId);
                Position position = employee.positionId == null ? null : db.find(Position.class, employee.positionId);
                slip.departmentName = department == null ? null : department.name;
                slip.positionName = position == null ? null : position.name;
                slip.monthlySalary = salary.grossSalary;
                slip.employedDays = employed;
                slip.overtimeHours = BigDecimal.ZERO;
                slips.add(slip);
            }
        }
        for (Payslip slip : slips) {
            if (onlySlipId != null && !onlySlipId.equals(slip.id)) continue;
            int payDays = slip.employedDays - slip.missingDays;
            Guard.that(payDays >= 0, "Eksik gün çalışma gününü aşamaz");
            slip.payDays = payDays;
            BigDecimal earnings = BigDecimal.ZERO;
            BigDecimal deductions = BigDecimal.ZERO;
            for (PayslipItem item : db.list("select i from PayslipItem i where i.payslipId = ?1", PayslipItem.class, slip.id)) {
                if (item.kind == PayslipItemKind.EARNING) earnings = earnings.add(item.amount);
                else deductions = deductions.add(item.amount);
            }
            slip.extraEarnings = earnings;
            slip.otherDeductions = deductions;
            slip.basePay = money(slip.monthlySalary.multiply(BigDecimal.valueOf(payDays)).divide(new BigDecimal("30"), 8, RoundingMode.HALF_UP));
            slip.overtimePay = money(slip.monthlySalary.divide(BigDecimal.valueOf(parameter.monthlyHours), 8, RoundingMode.HALF_UP).multiply(slip.overtimeHours).multiply(parameter.overtimeRate));
            slip.grossPay = slip.basePay.add(slip.overtimePay).add(slip.extraEarnings);
            BigDecimal floor = money(parameter.minimumWage.multiply(BigDecimal.valueOf(payDays)).divide(new BigDecimal("30"), 8, RoundingMode.HALF_UP));
            BigDecimal cap = money(parameter.sgkCeiling.multiply(BigDecimal.valueOf(payDays)).divide(new BigDecimal("30"), 8, RoundingMode.HALF_UP));
            slip.sgkBase = payDays == 0 ? BigDecimal.ZERO : cap.min(slip.grossPay.max(floor));
            slip.sgkEmployee = money(slip.sgkBase.multiply(parameter.sgkEmployeeRate));
            slip.unemploymentEmployee = money(slip.sgkBase.multiply(parameter.unemploymentEmployeeRate));
            slip.sgkEmployer = money(slip.sgkBase.multiply(parameter.sgkEmployerRate));
            slip.unemploymentEmployer = money(slip.sgkBase.multiply(parameter.unemploymentEmployerRate));
            slip.incomeTaxBase = slip.grossPay.subtract(slip.sgkEmployee).subtract(slip.unemploymentEmployee).max(BigDecimal.ZERO);
            BigDecimal prior = db.first("select coalesce(sum(s.incomeTaxBase), 0) from Payslip s, PayrollPeriod p where s.periodId = p.id and s.employeeId = ?1 and p.year = ?2 and s.id <> ?3 and (p.status <> ?4 or p.imported = true) and p.month < ?5",
                    BigDecimal.class, slip.employeeId, period.year, slip.id, PayrollPeriodStatus.DRAFT, period.month).orElse(BigDecimal.ZERO);
            slip.cumulativeTaxBase = prior.add(slip.incomeTaxBase);
            slip.incomeTax = bracketTax(slip.cumulativeTaxBase, brackets).subtract(bracketTax(prior, brackets)).max(BigDecimal.ZERO);
            BigDecimal exemptionBase = floor.subtract(money(floor.multiply(parameter.sgkEmployeeRate))).subtract(money(floor.multiply(parameter.unemploymentEmployeeRate))).max(BigDecimal.ZERO);
            slip.incomeTaxExemption = slip.incomeTax.min(bracketTax(exemptionBase, brackets));
            slip.stampTax = money(slip.grossPay.multiply(parameter.stampTaxRate));
            slip.stampTaxExemption = slip.stampTax.min(money(floor.multiply(parameter.stampTaxRate)));
            slip.netPay = slip.grossPay.subtract(slip.sgkEmployee).subtract(slip.unemploymentEmployee)
                    .subtract(slip.incomeTax.subtract(slip.incomeTaxExemption)).subtract(slip.stampTax.subtract(slip.stampTaxExemption)).subtract(slip.otherDeductions);
            Guard.that(slip.netPay.signum() >= 0, slip.employeeName + " net ücreti negatife düşüyor");
            slip.employerCost = slip.grossPay.add(slip.sgkEmployer).add(slip.unemploymentEmployer);
        }
        if (creating) for (Payslip slip : slips) db.add(slip);
        List<Payslip> all = db.list("select s from Payslip s where s.periodId = ?1", Payslip.class, period.id);
        period.employeeCount = all.size();
        period.grossTotal = sum(all, s -> s.grossPay);
        period.netTotal = sum(all, s -> s.netPay);
        period.sgkTotal = sum(all, s -> s.sgkEmployee.add(s.unemploymentEmployee).add(s.sgkEmployer).add(s.unemploymentEmployer));
        period.taxTotal = sum(all, s -> s.incomeTax.subtract(s.incomeTaxExemption).add(s.stampTax.subtract(s.stampTaxExemption)));
        period.deductionTotal = sum(all, s -> s.otherDeductions);
        period.employerCostTotal = sum(all, s -> s.employerCost);
        db.flush();
    }

    private void addItem(UUID payslipId, PayslipItemKind kind, String description, BigDecimal amount) {
        PayslipItem item = new PayslipItem();
        item.payslipId = payslipId;
        item.kind = kind;
        item.description = description == null || description.isBlank() ? (kind == PayslipItemKind.EARNING ? "Ek kazanç" : "Kesinti") : description.trim();
        item.amount = amount;
        db.add(item);
    }

    private static int employedDays(LocalDate hire, LocalDate termination, LocalDate start, LocalDate end) {
        if (hire.isAfter(end) || (termination != null && termination.isBefore(start))) return 0;
        int from = hire.isBefore(start) ? 1 : hire.getDayOfMonth();
        int to = termination == null || termination.isAfter(end) ? 30 : Math.min(30, termination.getDayOfMonth());
        return Math.max(0, to - from + 1);
    }

    private static BigDecimal bracketTax(BigDecimal amount, List<Bracket> brackets) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal previous = BigDecimal.ZERO;
        for (Bracket bracket : brackets) {
            BigDecimal slice = bracket.upTo == null ? amount : amount.min(bracket.upTo);
            if (slice.compareTo(previous) > 0) tax = tax.add(slice.subtract(previous).multiply(bracket.rate));
            previous = slice;
            if (bracket.upTo == null || amount.compareTo(bracket.upTo) <= 0) break;
        }
        return money(tax);
    }

    private static List<Bracket> brackets(JsonNode document) {
        List<Bracket> list = new ArrayList<>();
        for (JsonNode item : document) {
            BigDecimal upTo = item.hasNonNull("upTo") ? item.get("upTo").decimalValue() : null;
            list.add(new Bracket(upTo, item.get("rate").decimalValue()));
        }
        return list;
    }

    private static BigDecimal money(BigDecimal value) { return DocumentMath.round2(value); }

    private static BigDecimal sum(List<Payslip> slips, java.util.function.Function<Payslip, BigDecimal> field) {
        BigDecimal total = BigDecimal.ZERO;
        for (Payslip slip : slips) total = total.add(field.apply(slip));
        return total;
    }

    private static PayslipDto mapSlip(Payslip s) {
        return new PayslipDto(s.id, s.employeeId, s.employeeNo, s.employeeName, s.monthlySalary, s.payDays, s.overtimeHours, s.grossPay, s.sgkEmployee, s.unemploymentEmployee,
                s.incomeTax, s.incomeTaxExemption, s.stampTax, s.stampTaxExemption, s.otherDeductions, s.netPay, s.employerCost);
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
