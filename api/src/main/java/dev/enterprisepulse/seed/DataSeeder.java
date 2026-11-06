package dev.enterprisepulse.seed;

import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Branch;
import dev.enterprisepulse.data.generated.Company;
import dev.enterprisepulse.data.generated.Currency;
import dev.enterprisepulse.data.generated.ExchangeRate;
import dev.enterprisepulse.data.generated.Permission;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.data.generated.UserCompany;
import dev.enterprisepulse.data.generated.UserRole;
import dev.enterprisepulse.identity.PasswordHasher;
import dev.enterprisepulse.meta.Meta;
import dev.enterprisepulse.meta.MetadataStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DataSeeder {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final String[][] SAMPLE_EMPLOYEES = {
            {"Ayşe", "Yılmaz"}, {"Mehmet", "Kaya"}, {"Zeynep", "Demir"}, {"Mustafa", "Çelik"}, {"Elif", "Şahin"}, {"Ahmet", "Yıldız"},
            {"Fatma", "Öztürk"}, {"Emre", "Aydın"}, {"Selin", "Arslan"}, {"Burak", "Doğan"}, {"Merve", "Koç"}, {"Can", "Kurt"},
    };

    private record Membership(UUID companyId, UUID roleId, boolean isDefault, UUID branchId) {}

    private final Db db;
    private final MetadataStore meta;
    private final CompanyBootstrap bootstrap;
    private final List<SeedStep> steps;
    private final TransactionTemplate tx;

    public DataSeeder(Db db, MetadataStore meta, CompanyBootstrap bootstrap, List<SeedStep> steps, TransactionTemplate tx) {
        this.db = db;
        this.meta = meta;
        this.bootstrap = bootstrap;
        this.steps = steps;
        this.tx = tx;
    }

    public void seed() {
        CurrentUser ctx = CurrentUser.reset();
        SeedStep.Context seed = new SeedStep.Context();
        try (var scope = ctx.withoutTenant()) {
            tx.executeWithoutResult(s -> {
                seedPermissions();
                seedCurrencies();
                Company pulse = company("PULSE", "Pulse Teknoloji", new String[][] {{"MERKEZ", "Genel Merkez", "İstanbul"}, {"ANK", "Ankara Şube", "Ankara"}, {"IZM", "İzmir Şube", "İzmir"}});
                Company logistics = company("PULSE-LOJ", "Pulse Lojistik", new String[][] {{"MERKEZ", "Merkez Depo", "Kocaeli"}});
                Map<String, UUID> pulseRoles = bootstrap.initialize(pulse);
                Map<String, UUID> logisticsRoles = bootstrap.initialize(logistics);
                UUID hq = db.one("select b.id from Branch b where b.companyId = ?1 and b.code = 'MERKEZ'", UUID.class, pulse.id);
                seed.pulseId = pulse.id;
                seed.logisticsId = logistics.id;
                seed.hqBranchId = hq;
                seed.pulseRoles = pulseRoles;
                User admin = user("admin@enterprisepulse.dev", "Admin123!", "Sistem", "Yöneticisi", true,
                        List.of(new Membership(pulse.id, pulseRoles.get("admin"), true, hq), new Membership(logistics.id, logisticsRoles.get("admin"), false, null)));
                User manager = user("manager@enterprisepulse.dev", "Manager123!", "Deniz", "Aksoy", false,
                        List.of(new Membership(pulse.id, pulseRoles.get("manager"), true, hq), new Membership(logistics.id, logisticsRoles.get("manager"), false, null)));
                User employee = user("employee@enterprisepulse.dev", "Employee123!", "Kerem", "Polat", false,
                        List.of(new Membership(pulse.id, pulseRoles.get("employee"), true, hq)));
                seed.adminId = admin.id;
                seed.usersByName.put("Deniz Aksoy", manager.id);
                seed.usersByName.put("Kerem Polat", employee.id);
                for (String[] e : SAMPLE_EMPLOYEES) {
                    User u = user(slug(e[0]) + "." + slug(e[1]) + "@enterprisepulse.dev", "Employee123!", e[0], e[1], false,
                            List.of(new Membership(pulse.id, pulseRoles.get("employee"), true, null)));
                    seed.usersByName.put(e[0] + " " + e[1], u.id);
                }
            });
        }
        for (SeedStep step : steps.stream().sorted(Comparator.comparingInt(SeedStep::order)).toList()) {
            log.info("Seed: {}", step.getClass().getSimpleName());
            try (var scope = ctx.actAs(seed.pulseId, seed.adminId, true)) {
                tx.executeWithoutResult(s -> step.run(seed));
            }
        }
        CurrentUser.reset();
    }

    public static String slug(String value) {
        return value.toLowerCase(new Locale("tr")).replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
    }

    private void seedPermissions() {
        Set<String> existing = new HashSet<>(db.list("select p.code from Permission p", String.class));
        for (Meta.PermissionModule module : meta.document().permissions) {
            for (Meta.PermissionAction action : module.actions) {
                if (existing.contains(action.code)) continue;
                Permission p = new Permission();
                p.code = action.code;
                p.module = module.module;
                p.action = action.action;
                p.description = module.label.tr + " - " + action.label.tr;
                db.add(p);
            }
        }
        db.flush();
    }

    private void seedCurrencies() {
        Object[][] currencies = {{"TRY", "Türk Lirası", "₺", "1", "1"}, {"USD", "US Dollar", "$", "41.2", "41.35"},
                {"EUR", "Euro", "€", "47.9", "48.1"}, {"GBP", "British Pound", "£", "55.1", "55.4"}};
        LocalDate today = LocalDate.now();
        for (Object[] c : currencies) {
            String code = (String) c[0];
            if (db.find(Currency.class, code) == null) {
                Currency currency = new Currency();
                currency.code = code;
                currency.name = (String) c[1];
                currency.symbol = (String) c[2];
                db.add(currency);
            }
            if (!code.equals("TRY") && !db.exists("select x.id from ExchangeRate x where x.currencyCode = ?1 and x.rateDate = ?2", code, today)) {
                ExchangeRate rate = new ExchangeRate();
                rate.currencyCode = code;
                rate.rateDate = today;
                rate.buying = new BigDecimal((String) c[3]);
                rate.selling = new BigDecimal((String) c[4]);
                db.add(rate);
            }
        }
        db.flush();
    }

    private Company company(String code, String name, String[][] branches) {
        Company company = db.first("select c from Company c where c.code = ?1", Company.class, code).orElse(null);
        if (company == null) {
            company = new Company();
            company.code = code;
            company.name = name;
            company.legalName = name + " A.Ş.";
            company.city = branches[0][2];
            company.email = "info@" + slug(code) + ".dev";
            db.add(company);
            db.flush();
        }
        for (String[] b : branches) {
            if (db.exists("select x.id from Branch x where x.companyId = ?1 and x.code = ?2", company.id, b[0])) continue;
            Branch branch = new Branch();
            branch.companyId = company.id;
            branch.code = b[0];
            branch.name = b[1];
            branch.city = b[2];
            db.add(branch);
        }
        db.flush();
        return company;
    }

    private User user(String email, String password, String first, String last, boolean superAdmin, List<Membership> memberships) {
        User user = db.first("select u from User u where u.email = ?1", User.class, email).orElse(null);
        if (user == null) {
            user = new User();
            user.email = email;
            user.passwordHash = PasswordHasher.hash(password);
            user.firstName = first;
            user.lastName = last;
            user.isSuperAdmin = superAdmin;
            db.add(user);
            db.flush();
        }
        for (Membership m : memberships) {
            if (!db.exists("select x.id from UserCompany x where x.userId = ?1 and x.companyId = ?2", user.id, m.companyId())) {
                UserCompany uc = new UserCompany();
                uc.userId = user.id;
                uc.companyId = m.companyId();
                uc.isDefault = m.isDefault();
                uc.defaultBranchId = m.branchId();
                db.add(uc);
            }
            if (!db.exists("select x.id from UserRole x where x.userId = ?1 and x.roleId = ?2", user.id, m.roleId())) {
                UserRole ur = new UserRole();
                ur.userId = user.id;
                ur.roleId = m.roleId();
                ur.companyId = m.companyId();
                db.add(ur);
            }
        }
        db.flush();
        return user;
    }
}
