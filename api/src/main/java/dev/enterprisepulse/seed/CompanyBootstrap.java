package dev.enterprisepulse.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.ApproverType;
import dev.enterprisepulse.data.generated.Company;
import dev.enterprisepulse.data.generated.NumberSequence;
import dev.enterprisepulse.data.generated.Permission;
import dev.enterprisepulse.data.generated.Role;
import dev.enterprisepulse.data.generated.RolePermission;
import dev.enterprisepulse.data.generated.Setting;
import dev.enterprisepulse.data.generated.TaxRate;
import dev.enterprisepulse.data.generated.Unit;
import dev.enterprisepulse.data.generated.WorkflowDefinition;
import dev.enterprisepulse.data.generated.WorkflowStep;
import dev.enterprisepulse.meta.Meta;
import dev.enterprisepulse.meta.MetadataStore;
import java.math.BigDecimal;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Everything a new company needs before its first document: taxes, units, series, roles, approvals. */
@Component
@Transactional
public class CompanyBootstrap {
    public record StepDef(String name, UUID roleId) {}

    private final Db db;
    private final MetadataStore meta;
    private final ObjectMapper json;

    public CompanyBootstrap(Db db, MetadataStore meta, ObjectMapper json) {
        this.db = db;
        this.meta = meta;
        this.json = json;
    }

    public Map<String, UUID> initialize(Company company) {
        UUID companyId = company.id;
        int year = Year.now().getValue();
        try (var scope = CurrentUser.get().withoutTenant()) {
            Object[][] taxes = {{"KDV20", "KDV %20", 20, true}, {"KDV10", "KDV %10", 10, false}, {"KDV1", "KDV %1", 1, false}, {"KDV0", "KDV İstisna", 0, false}};
            for (Object[] t : taxes) {
                if (db.exists("select x.id from TaxRate x where x.companyId = ?1 and x.code = ?2", companyId, t[0])) continue;
                TaxRate tax = new TaxRate();
                tax.companyId = companyId;
                tax.code = (String) t[0];
                tax.name = (String) t[1];
                tax.rate = BigDecimal.valueOf((Integer) t[2]);
                tax.isDefault = (Boolean) t[3];
                db.add(tax);
            }
            String[][] units = {{"ADET", "Adet"}, {"KG", "Kilogram"}, {"LT", "Litre"}, {"M", "Metre"}, {"M2", "Metrekare"}, {"KOLI", "Koli"}, {"SAAT", "Saat"}};
            for (String[] u : units) {
                if (db.exists("select x.id from Unit x where x.companyId = ?1 and x.code = ?2", companyId, u[0])) continue;
                Unit unit = new Unit();
                unit.companyId = companyId;
                unit.code = u[0];
                unit.name = u[1];
                db.add(unit);
            }
            for (Meta.SequenceMeta s : meta.document().sequences) {
                if (db.exists("select x.id from NumberSequence x where x.companyId = ?1 and x.code = ?2", companyId, s.code)) continue;
                NumberSequence seq = new NumberSequence();
                seq.companyId = companyId;
                seq.code = s.code;
                seq.name = s.name;
                seq.prefix = s.prefix;
                seq.includeYear = s.yearly;
                seq.resetYearly = s.yearly;
                seq.currentYear = year;
                db.add(seq);
            }
            if (!db.exists("select x.id from Setting x where x.companyId = ?1 and x.key = 'general'", companyId)) {
                Setting setting = new Setting();
                setting.companyId = companyId;
                setting.key = "general";
                setting.value = json.valueToTree(Map.of("dateFormat", "dd.MM.yyyy", "timezone", "Europe/Istanbul", "fiscalYearStartMonth", 1));
                db.add(setting);
            }
            db.flush();

            Map<String, UUID> permissions = db.list("select p from Permission p", Permission.class).stream().collect(Collectors.toMap(p -> p.code, p -> p.id));
            Map<String, UUID> roles = new LinkedHashMap<>();
            for (Map.Entry<String, Meta.RoleSeed> e : meta.document().roles.entrySet()) {
                String code = e.getKey();
                Meta.RoleSeed def = e.getValue();
                Role role = db.first("select r from Role r where r.companyId = ?1 and r.code = ?2", Role.class, companyId, code).orElse(null);
                if (role == null) {
                    role = new Role();
                    role.companyId = companyId;
                    role.code = code;
                    role.name = def.name;
                    role.description = def.description;
                    role.isSystem = code.equals("admin");
                    db.add(role);
                    db.flush();
                }
                db.execute("delete from RolePermission rp where rp.roleId = ?1", role.id);
                for (String c : def.codes) {
                    UUID permissionId = permissions.get(c);
                    if (permissionId == null) continue;
                    RolePermission rp = new RolePermission();
                    rp.roleId = role.id;
                    rp.permissionId = permissionId;
                    db.add(rp);
                }
                roles.put(code, role.id);
            }
            db.flush();

            ensureWorkflow(companyId, "PURCHASE_REQUEST", "Satın Alma Talebi Onayı", "PurchaseRequest",
                    "Önce müdür, ardından şirket yöneticisi onaylar", List.of(new StepDef("Müdür Onayı", roles.get("manager")), new StepDef("Yönetici Onayı", roles.get("admin"))));
            ensureWorkflow(companyId, "LEAVE_REQUEST", "İzin Talebi Onayı", "LeaveRequest", "Yönetici onayı", List.of(new StepDef("Yönetici Onayı", roles.get("manager"))));
            ensureWorkflow(companyId, "EXPENSE_REPORT", "Masraf Formu Onayı", "ExpenseReport",
                    "Müdür onayından sonra ödemeye hazır olur", List.of(new StepDef("Müdür Onayı", roles.get("manager"))));
            return roles;
        }
    }

    public void ensureWorkflow(UUID companyId, String code, String name, String entityType, String description, List<StepDef> steps) {
        if (db.exists("select d.id from WorkflowDefinition d where d.companyId = ?1 and d.code = ?2", companyId, code)) return;
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.companyId = companyId;
        definition.code = code;
        definition.name = name;
        definition.entityType = entityType;
        definition.description = description;
        db.add(definition);
        int order = 1;
        for (StepDef s : steps) {
            WorkflowStep step = new WorkflowStep();
            step.definitionId = definition.id;
            step.order = order++;
            step.name = s.name();
            step.approverType = ApproverType.ROLE;
            step.approverRoleId = s.roleId();
            db.add(step);
        }
        db.flush();
    }
}
