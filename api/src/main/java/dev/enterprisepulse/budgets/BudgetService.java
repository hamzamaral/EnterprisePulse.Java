package dev.enterprisepulse.budgets;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Budget;
import dev.enterprisepulse.data.generated.BudgetAlert;
import dev.enterprisepulse.data.generated.BudgetAlertLevel;
import dev.enterprisepulse.data.generated.BudgetEntry;
import dev.enterprisepulse.data.generated.BudgetEvent;
import dev.enterprisepulse.data.generated.BudgetEventType;
import dev.enterprisepulse.data.generated.BudgetItem;
import dev.enterprisepulse.data.generated.BudgetItemKind;
import dev.enterprisepulse.data.generated.BudgetLine;
import dev.enterprisepulse.data.generated.BudgetSource;
import dev.enterprisepulse.data.generated.BudgetStatus;
import dev.enterprisepulse.data.generated.ExpenseReportStatus;
import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.PayrollPeriodStatus;
import dev.enterprisepulse.data.generated.PurchaseInvoice;
import dev.enterprisepulse.data.generated.SalesInvoice;
import dev.enterprisepulse.platform.Extensibility.ScheduledJob;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NotificationService.NotifyRequest;
import dev.enterprisepulse.trade.DocumentMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Yıllık bütçe. Gerçekleşen MANUAL girişler ile masraf, bordro ve satış kaynaklarından toplanır. */
@Service
@Transactional
public class BudgetService implements ScheduledJob {
    public record SaveBudgetItem(String code, String name, BudgetItemKind kind, BudgetSource source, String description) {}
    public record SaveBudget(int year, UUID departmentId, String name, UUID ownerId, String note) {}
    public record PlanLine(UUID itemId, int month, BigDecimal amount) {}
    public record SavePlan(List<PlanLine> lines) {}
    public record SaveEntry(UUID itemId, UUID departmentId, LocalDate entryDate, BigDecimal amount, String description) {}
    public record BudgetRowDto(UUID itemId, String code, String name, BudgetItemKind kind, BigDecimal plannedYear, BigDecimal plannedYtd,
                               BigDecimal actualYtd, BigDecimal utilization, String state) {}
    public record BudgetDto(UUID id, int year, String name, BudgetStatus status, UUID departmentId, String departmentName, UUID ownerId,
                            BigDecimal plannedIncome, BigDecimal plannedExpense, BigDecimal actualIncome, BigDecimal actualExpense,
                            List<BudgetRowDto> rows, boolean canApprove, boolean canReopen) {}

    private static final UUID NO_CATEGORY = new UUID(0L, 0L);

    private final Db db;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public BudgetService(Db db, NotificationService notifications, TransactionTemplate tx) {
        this.db = db;
        this.notifications = notifications;
        this.tx = tx;
    }

    @Override public String id() { return "budgets-daily"; }

    @Override public String cron() { return "0 0 8 * * *"; }

    private static CurrentUser ctx() { return CurrentUser.get(); }

    private static LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run() {
        CurrentUser ctx = ctx();
        List<UUID> companies;
        try (var bypass = ctx.withoutTenant()) {
            companies = tx.execute(s -> db.list("select c.id from Company c", UUID.class));
        }
        for (UUID companyId : companies) {
            try (var scope = ctx.actAs(companyId, null, true)) {
                tx.executeWithoutResult(s -> checkAlerts());
            }
        }
    }

    public BudgetItem saveItem(SaveBudgetItem dto) {
        Guard.that(ctx().has("budgets:update"), "Bütçe kalemi yetkiniz yok");
        Guard.that(!Guard.blank(dto.code()) && !Guard.blank(dto.name()), "Kod ve ad gerekli");
        Guard.that(dto.kind() != null, "Kalem türü gerekli");
        BudgetItem row = db.first("select i from BudgetItem i where i.code = ?1", BudgetItem.class, dto.code().trim()).orElse(null);
        boolean isNew = row == null;
        if (isNew) {
            row = new BudgetItem();
            row.code = dto.code().trim();
            row.categoryIds = NO_CATEGORY;
        }
        row.name = dto.name().trim();
        row.kind = dto.kind();
        row.source = dto.source() == null ? BudgetSource.MANUAL : dto.source();
        row.description = Guard.blank(dto.description()) ? null : dto.description().trim();
        row.isActive = true;
        if (isNew) db.add(row);
        db.flush();
        return row;
    }

    public BudgetDto create(SaveBudget dto) {
        Guard.that(ctx().has("budgets:update"), "Bütçe oluşturma yetkiniz yok");
        Guard.that(!Guard.blank(dto.name()), "Bütçe adı gerekli");
        if (dto.departmentId() != null) Guard.that(db.exists("select d.id from Department d where d.id = ?1", dto.departmentId()), "Departman bulunamadı");
        Budget budget = new Budget();
        budget.year = dto.year();
        budget.departmentId = dto.departmentId();
        budget.name = dto.name().trim();
        budget.ownerId = dto.ownerId() != null ? dto.ownerId() : ctx().userId;
        budget.note = Guard.blank(dto.note()) ? null : dto.note().trim();
        budget.createdById = ctx().requireUserId();
        db.add(budget);
        event(budget.id, ctx().requireUserId(), BudgetEventType.CREATED, null);
        db.flush();
        return get(budget.id);
    }

    public BudgetDto savePlan(UUID id, SavePlan dto) {
        Guard.that(ctx().has("budgets:update"), "Plan güncelleme yetkiniz yok");
        Budget budget = find(id);
        Guard.state(budget.status == BudgetStatus.DRAFT, "Onaylı bütçe planı değişmez");
        Guard.that(dto.lines() != null && !dto.lines().isEmpty(), "Plan satırı gerekli");
        for (PlanLine line : dto.lines()) {
            Guard.that(line.month() >= 1 && line.month() <= 12, "Ay 1 ile 12 arasında olmalı");
            Guard.that(line.amount() != null && line.amount().signum() >= 0, "Plan tutarı negatif olamaz");
            Guard.that(db.exists("select i.id from BudgetItem i where i.id = ?1 and i.isActive = true", line.itemId()), "Bütçe kalemi bulunamadı");
        }
        db.execute("delete from BudgetLine l where l.budgetId = ?1", id);
        for (PlanLine line : dto.lines()) {
            BudgetLine row = new BudgetLine();
            row.budgetId = id;
            row.itemId = line.itemId();
            row.month = line.month();
            row.amount = line.amount();
            db.add(row);
        }
        event(id, ctx().requireUserId(), BudgetEventType.PLAN_UPDATED, null);
        db.flush();
        return get(id);
    }

    public BudgetDto approve(UUID id) {
        Guard.that(ctx().has("budgets:approve"), "Bütçe onay yetkiniz yok");
        Budget budget = find(id);
        Guard.state(budget.status == BudgetStatus.DRAFT, "Yalnızca taslak bütçe onaylanır");
        Guard.that(db.count("select count(l) from BudgetLine l where l.budgetId = ?1", id) > 0, "Plansız bütçe onaylanamaz");
        budget.status = BudgetStatus.APPROVED;
        budget.approvedAt = Instant.now();
        budget.approvedById = ctx().requireUserId();
        event(id, ctx().requireUserId(), BudgetEventType.APPROVED, null);
        db.flush();
        return get(id);
    }

    public BudgetDto reopen(UUID id) {
        Guard.that(ctx().has("budgets:update"), "Bütçe yeniden açma yetkiniz yok");
        Budget budget = find(id);
        Guard.state(budget.status == BudgetStatus.APPROVED, "Yalnızca onaylı bütçe yeniden açılır");
        budget.status = BudgetStatus.DRAFT;
        budget.revision += 1;
        budget.approvedAt = null;
        budget.approvedById = null;
        db.flush();
        db.execute("delete from BudgetAlert a where a.budgetId = ?1", id);
        event(id, ctx().requireUserId(), BudgetEventType.REOPENED, null);
        db.flush();
        return get(id);
    }

    public void addEntry(SaveEntry dto) {
        Guard.that(ctx().has("budgets:update"), "Bütçe girişi yetkiniz yok");
        Guard.that(dto.amount() != null && dto.amount().signum() != 0, "Tutar sıfır olamaz");
        Guard.that(!Guard.blank(dto.description()), "Açıklama gerekli");
        Guard.that(dto.entryDate() != null, "Tarih gerekli");
        BudgetItem item = db.first("select i from BudgetItem i where i.id = ?1 and i.isActive = true", BudgetItem.class, dto.itemId())
                .orElseThrow(() -> AppException.notFound("Bütçe kalemi bulunamadı"));
        Guard.that(item.source == BudgetSource.MANUAL, "Yalnızca manuel kaleme giriş yazılır");
        BudgetEntry entry = new BudgetEntry();
        entry.itemId = dto.itemId();
        entry.departmentId = dto.departmentId();
        entry.entryDate = dto.entryDate();
        entry.amount = dto.amount();
        entry.description = dto.description().trim();
        entry.createdById = ctx().requireUserId();
        db.add(entry);
        db.flush();
    }

    @Transactional(readOnly = true)
    public BudgetDto get(UUID id) {
        Budget budget = find(id);
        Guard.that(ctx().has("budgets:read") || Objects.equals(budget.ownerId, ctx().userId), "Bütçe görme yetkiniz yok");
        String departmentName = budget.departmentId == null ? null
                : db.first("select d.name from Department d where d.id = ?1", String.class, budget.departmentId).orElse(null);
        List<BudgetLine> lines = db.list("select l from BudgetLine l where l.budgetId = ?1", BudgetLine.class, id);
        List<BudgetItem> items = db.list("select i from BudgetItem i where i.isActive = true order by i.sortOrder, i.code", BudgetItem.class);
        Map<UUID, BigDecimal> actuals = actuals(budget.year, budget.departmentId);
        int elapsed = Math.min(12, today().getMonthValue());
        List<BudgetRowDto> rows = new ArrayList<>();
        BigDecimal plannedIncome = BigDecimal.ZERO, plannedExpense = BigDecimal.ZERO, actualIncome = BigDecimal.ZERO, actualExpense = BigDecimal.ZERO;
        for (BudgetItem item : items) {
            BigDecimal plannedYear = BigDecimal.ZERO, plannedYtd = BigDecimal.ZERO;
            for (BudgetLine line : lines) {
                if (!line.itemId.equals(item.id)) continue;
                plannedYear = plannedYear.add(line.amount);
                if (line.month <= elapsed) plannedYtd = plannedYtd.add(line.amount);
            }
            BigDecimal actualYtd = actuals.getOrDefault(item.id, BigDecimal.ZERO);
            if (plannedYear.signum() == 0 && actualYtd.signum() == 0) continue;
            BigDecimal util;
            if (plannedYtd.signum() == 0) util = actualYtd.signum() == 0 ? BigDecimal.ZERO : new BigDecimal("999");
            else util = actualYtd.divide(plannedYtd, 20, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
            String state = util.compareTo(BigDecimal.valueOf(100)) >= 0 ? "OVER" : util.compareTo(BigDecimal.valueOf(90)) >= 0 ? "WARNING" : "ON_TRACK";
            rows.add(new BudgetRowDto(item.id, item.code, item.name, item.kind, plannedYear, plannedYtd, actualYtd, util, state));
            if (item.kind == BudgetItemKind.INCOME) {
                plannedIncome = plannedIncome.add(plannedYear);
                actualIncome = actualIncome.add(actualYtd);
            } else {
                plannedExpense = plannedExpense.add(plannedYear);
                actualExpense = actualExpense.add(actualYtd);
            }
        }
        return new BudgetDto(budget.id, budget.year, budget.name, budget.status, budget.departmentId, departmentName, budget.ownerId,
                plannedIncome, plannedExpense, actualIncome, actualExpense, rows,
                budget.status == BudgetStatus.DRAFT && ctx().has("budgets:approve"),
                budget.status == BudgetStatus.APPROVED && ctx().has("budgets:update"));
    }

    public void checkAlerts() {
        int year = today().getYear();
        List<Budget> budgets = db.list("select b from Budget b where b.year = ?1 and b.status = ?2", Budget.class, year, BudgetStatus.APPROVED);
        for (Budget budget : budgets) {
            BudgetDto detail = get(budget.id);
            Set<String> sent = new HashSet<>();
            for (BudgetAlert alert : db.list("select a from BudgetAlert a where a.budgetId = ?1", BudgetAlert.class, budget.id))
                sent.add(alert.itemId + ":" + alert.level);
            for (BudgetRowDto row : detail.rows()) {
                if (row.kind() != BudgetItemKind.EXPENSE) continue;
                BudgetAlertLevel level = row.utilization().compareTo(BigDecimal.valueOf(100)) >= 0 ? BudgetAlertLevel.OVER
                        : row.utilization().compareTo(BigDecimal.valueOf(90)) >= 0 ? BudgetAlertLevel.WARNING : null;
                if (level == null) continue;
                if (!sent.add(row.itemId() + ":" + level)) continue;
                BudgetAlert alert = new BudgetAlert();
                alert.budgetId = budget.id;
                alert.itemId = row.itemId();
                alert.level = level;
                db.add(alert);
                event(budget.id, null, BudgetEventType.ALERT_SENT, row.code() + " " + level);
                if (budget.ownerId != null)
                    notifications.notify(new NotifyRequest(List.of(budget.ownerId), budget.companyId, "budgets.alert",
                            "Bütçe uyarısı: " + budget.name, row.name() + " kullanımı %" + plain(row.utilization()), "/budgets/" + budget.id, true));
            }
        }
        db.flush();
    }

    private static String plain(BigDecimal value) {
        BigDecimal v = value.stripTrailingZeros();
        return (v.scale() < 0 ? v.setScale(0) : v).toPlainString();
    }

    private Map<UUID, BigDecimal> actuals(int year, UUID departmentId) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        List<BudgetItem> items = db.list("select i from BudgetItem i where i.isActive = true", BudgetItem.class);
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (BudgetItem item : items) map.put(item.id, BigDecimal.ZERO);
        for (BudgetItem item : items) {
            BigDecimal amount = switch (item.source) {
                case MANUAL -> departmentId == null
                        ? sum("select sum(e.amount) from BudgetEntry e where e.itemId = ?1 and e.entryDate >= ?2 and e.entryDate <= ?3 and e.departmentId is null",
                                item.id, start, end)
                        : sum("select sum(e.amount) from BudgetEntry e where e.itemId = ?1 and e.entryDate >= ?2 and e.entryDate <= ?3 and e.departmentId = ?4",
                                item.id, start, end, departmentId);
                case EXPENSES -> sum("select sum(r.totalAmount) from ExpenseReport r where r.status = ?1 and r.paymentDate is not null and r.paymentDate >= ?2 and r.paymentDate <= ?3",
                        ExpenseReportStatus.PAID, start, end);
                case PAYROLL -> sum("select sum(p.employerCostTotal) from PayrollPeriod p where p.year = ?1 and (p.status = ?2 or p.status = ?3)",
                        year, PayrollPeriodStatus.APPROVED, PayrollPeriodStatus.PAID);
                case SALES -> {
                    BigDecimal total = BigDecimal.ZERO;
                    for (SalesInvoice i : db.list("select i from SalesInvoice i where i.status = ?1 and i.invoiceDate >= ?2 and i.invoiceDate <= ?3",
                            SalesInvoice.class, InvoiceStatus.ISSUED, start, end))
                        total = total.add(DocumentMath.toBase(i.grandTotal.subtract(i.vatTotal), i.exchangeRate));
                    yield total;
                }
                case PURCHASES -> {
                    BigDecimal total = BigDecimal.ZERO;
                    for (PurchaseInvoice i : db.list("select i from PurchaseInvoice i where i.status = ?1 and i.invoiceDate >= ?2 and i.invoiceDate <= ?3",
                            PurchaseInvoice.class, InvoiceStatus.ISSUED, start, end))
                        total = total.add(DocumentMath.toBase(i.grandTotal.subtract(i.vatTotal), i.exchangeRate));
                    yield total;
                }
            };
            map.put(item.id, amount);
        }
        return map;
    }

    private BigDecimal sum(String jpql, Object... params) {
        BigDecimal value = db.query(jpql, BigDecimal.class, params).getSingleResult();
        return value == null ? BigDecimal.ZERO : value;
    }

    private Budget find(UUID id) {
        return db.first("select b from Budget b where b.id = ?1", Budget.class, id).orElseThrow(() -> AppException.notFound("Bütçe bulunamadı"));
    }

    private void event(UUID budgetId, UUID actorId, BudgetEventType type, String note) {
        BudgetEvent row = new BudgetEvent();
        row.budgetId = budgetId;
        row.actorId = actorId;
        row.type = type;
        row.note = note;
        db.add(row);
    }
}
