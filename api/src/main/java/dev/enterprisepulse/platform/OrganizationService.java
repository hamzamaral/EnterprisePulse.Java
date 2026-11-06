package dev.enterprisepulse.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Branch;
import dev.enterprisepulse.data.generated.Company;
import dev.enterprisepulse.data.generated.Setting;
import dev.enterprisepulse.data.generated.UserCompany;
import dev.enterprisepulse.data.generated.UserRole;
import dev.enterprisepulse.seed.CompanyBootstrap;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrganizationService {
    public record CreateCompanyRequest(String code, String name, String legalName, String taxNumber, String taxOffice,
                                       String email, String phone, String address, String city, String baseCurrency) {}

    private final Db db;
    private final CompanyBootstrap bootstrap;
    private final ObjectMapper json;

    public OrganizationService(Db db, CompanyBootstrap bootstrap, ObjectMapper json) {
        this.db = db;
        this.bootstrap = bootstrap;
        this.json = json;
    }

    public Company createCompany(CreateCompanyRequest request) {
        CurrentUser ctx = CurrentUser.get();
        ctx.require("companies:create");
        UUID userId = ctx.requireUserId();
        String code = request.code() == null ? "" : request.code().trim().toUpperCase(Locale.ROOT);
        Guard.that(code.length() >= 2, "Şirket kodu en az 2 karakter olmalı");
        Guard.that(!Guard.blank(request.name()), "Şirket adı zorunludur");
        Guard.state(db.nativeRows("select 1 from companies where code = ?1", code).isEmpty(), "Bu şirket kodu kullanılıyor");

        Company company = new Company();
        company.code = code;
        company.name = request.name().trim();
        company.legalName = request.legalName();
        company.taxNumber = request.taxNumber();
        company.taxOffice = request.taxOffice();
        company.email = request.email();
        company.phone = request.phone();
        company.address = request.address();
        company.city = request.city();
        company.baseCurrency = request.baseCurrency() == null ? "TRY" : request.baseCurrency();
        db.add(company);
        db.flush();

        try (var scope = ctx.withoutTenant()) {
            Branch hq = new Branch();
            hq.companyId = company.id;
            hq.code = "MERKEZ";
            hq.name = "Genel Merkez";
            hq.city = request.city();
            db.add(hq);
            db.flush();
            Map<String, UUID> roles = bootstrap.initialize(company);
            UserCompany membership = new UserCompany();
            membership.userId = userId;
            membership.companyId = company.id;
            membership.defaultBranchId = hq.id;
            db.add(membership);
            UserRole role = new UserRole();
            role.userId = userId;
            role.companyId = company.id;
            role.roleId = roles.get("admin");
            db.add(role);
            db.flush();
        }
        return company;
    }

    @Transactional(readOnly = true)
    public JsonNode getSetting(String key) {
        return db.first("select s from Setting s where s.key = ?1", Setting.class, key)
                .map(s -> s.value)
                .orElseGet(json::createObjectNode);
    }

    public JsonNode saveSetting(String key, JsonNode value) {
        CurrentUser.get().require("settings:update");
        Guard.that(value != null && value.isObject(), "Ayar değeri bir nesne olmalı");
        Setting setting = db.first("select s from Setting s where s.key = ?1", Setting.class, key).orElse(null);
        if (setting == null) {
            setting = new Setting();
            setting.key = key;
            setting.value = value;
            db.add(setting);
        } else {
            setting.value = value;
        }
        db.flush();
        return value;
    }

    /** Latest selling rate of a currency into TRY. */
    @Transactional(readOnly = true)
    public BigDecimal rate(String currency, LocalDate date) {
        if ("TRY".equals(currency)) return BigDecimal.ONE;
        LocalDate day = date == null ? LocalDate.now() : date;
        return db.first("select r.selling from ExchangeRate r where r.currencyCode = ?1 and r.rateDate <= ?2 order by r.rateDate desc",
                        BigDecimal.class, currency, day)
                .orElseThrow(() -> AppException.badRequest(currency + " için kur bulunamadı"));
    }
}
