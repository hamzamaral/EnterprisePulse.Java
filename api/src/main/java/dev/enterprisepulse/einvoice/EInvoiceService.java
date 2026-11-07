package dev.enterprisepulse.einvoice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.enterprisepulse.accounting.AccountingClock;
import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.Company;
import dev.enterprisepulse.data.generated.EInvoice;
import dev.enterprisepulse.data.generated.EInvoiceDirection;
import dev.enterprisepulse.data.generated.EInvoiceEvent;
import dev.enterprisepulse.data.generated.EInvoiceProfile;
import dev.enterprisepulse.data.generated.EInvoiceStatus;
import dev.enterprisepulse.data.generated.InvoiceStatus;
import dev.enterprisepulse.data.generated.NumberSequence;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.PurchaseInvoice;
import dev.enterprisepulse.data.generated.SalesInvoice;
import dev.enterprisepulse.data.generated.SalesInvoiceLine;
import dev.enterprisepulse.data.generated.SalesOrder;
import dev.enterprisepulse.data.generated.Setting;
import dev.enterprisepulse.data.generated.TaxRate;
import dev.enterprisepulse.data.generated.Unit;
import dev.enterprisepulse.data.generated.User;
import dev.enterprisepulse.einvoice.EInvoiceRules.EInvoiceLine;
import dev.enterprisepulse.einvoice.EInvoiceRules.EInvoiceLineInput;
import dev.enterprisepulse.einvoice.EInvoiceRules.OutgoingStep;
import dev.enterprisepulse.einvoice.EInvoiceRules.ValidationInput;
import dev.enterprisepulse.einvoice.EInvoiceRules.VatBreakdown;
import dev.enterprisepulse.einvoice.EInvoiceXml.OrderReference;
import dev.enterprisepulse.einvoice.EInvoiceXml.UblInvoice;
import dev.enterprisepulse.einvoice.EInvoiceXml.UblParty;
import dev.enterprisepulse.partners.PartnerLedgerService;
import dev.enterprisepulse.platform.Extensibility.ScheduledJob;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NotificationService.NotifyRequest;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.purchasing.PurchaseInvoiceService;
import dev.enterprisepulse.trade.TradeService.LineInput;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** e-Fatura / e-Arşiv simulation. Outgoing invoices queue from sales; incoming ones are simulated supplier invoices. */
@Service
@Transactional
public class EInvoiceService implements ScheduledJob {
    public static final String SETTINGS_KEY = "einvoice";
    public static final String INVOICE_SEQUENCE = "EINVOICE";
    public static final String ARCHIVE_SEQUENCE = "EARCHIVE";

    public record SendBody(UUID salesInvoiceId, EInvoiceProfile profile) {}
    public record RespondBody(String action, String note) {}
    public record SimulateIncoming(UUID partnerId, EInvoiceProfile profile) {}
    public record SettingsDto(String senderAlias, EInvoiceProfile defaultProfile, boolean autoSend) {}
    public record UpdateSettings(String senderAlias, EInvoiceProfile defaultProfile, Boolean autoSend) {}
    public record SyncResultDto(int processed) {}
    public record SummaryDto(int unsent, int inProgress, int errors, int awaitingResponse, int toRespond, int toRecord,
                             int outgoingThisMonth, BigDecimal outgoingAmountThisMonth, int incomingThisMonth) {}
    public record UnsentInvoiceDto(UUID id, String number, UUID partnerId, String partnerTitle, LocalDate invoiceDate,
                                   String currencyCode, BigDecimal grandTotal, EInvoiceProfile profile) {}
    public record EventDto(UUID id, EInvoiceStatus status, String code, String message, String userName, Instant createdAt) {}
    public record DetailDto(UUID id, EInvoiceDirection direction, EInvoiceProfile profile, EInvoiceStatus status, String number,
                            UUID uuid, UUID partnerId, String partnerCode, String partnerTitle, LocalDate issueDate, String currencyCode,
                            BigDecimal taxTotal, BigDecimal payableAmount, UUID salesInvoiceId, String salesInvoiceNumber,
                            UUID purchaseInvoiceId, String purchaseInvoiceNumber, String statusCode, String statusMessage,
                            Instant sentAt, Instant deliveredAt, Instant respondedAt, boolean canRespond, BigDecimal exchangeRate,
                            BigDecimal lineExtensionAmount, BigDecimal allowanceTotal, String senderAlias, String receiverAlias,
                            String senderTaxId, String receiverTaxId, String responseNote, LocalDate responseDeadline,
                            List<EInvoiceLine> lines, List<VatBreakdown> taxes, List<EventDto> events, String xml,
                            String createdByName, boolean canResend, boolean canRecord) {}

    private record Step(EInvoiceStatus status, String code, String message) {}

    private static final Step QUEUED = new Step(EInvoiceStatus.QUEUED, EInvoiceRules.Codes.QUEUED, "Zarf kuyruğa eklendi");
    private static final TypeReference<List<EInvoiceLine>> LINES_TYPE = new TypeReference<>() {};

    private final Db db;
    private final NumberingService numbering;
    private final NotificationService notifications;
    private final PurchaseInvoiceService purchaseInvoices;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public EInvoiceService(Db db, NumberingService numbering, NotificationService notifications, PurchaseInvoiceService purchaseInvoices,
                           ObjectMapper json, TransactionTemplate tx) {
        this.db = db;
        this.numbering = numbering;
        this.notifications = notifications;
        this.purchaseInvoices = purchaseInvoices;
        this.json = json;
        this.tx = tx;
    }

    @Override public String id() { return "einvoice-sync"; }
    @Override public String cron() { return "0 * * * * *"; }

    private static CurrentUser ctx() { return CurrentUser.get(); }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run() {
        CurrentUser user = ctx();
        List<UUID> companies;
        try (var bypass = user.withoutTenant()) {
            companies = tx.execute(s -> db.list("select c.id from Company c", UUID.class));
        }
        for (UUID companyId : companies) {
            try (var scope = user.actAs(companyId, null, true)) {
                tx.executeWithoutResult(s -> sync());
            }
        }
    }

    // ---------- Queries ----------

    public SummaryDto summary() {
        LocalDate today = AccountingClock.today();
        LocalDate monthStart = LocalDate.of(today.getYear(), today.getMonth(), 1);
        Instant windowStart = Instant.now().minusSeconds(EInvoiceRules.RESPONSE_DAYS * 86400L);
        long unsent = db.count("select count(i) from SalesInvoice i where i.status = ?1 and not exists (select 1 from EInvoice e where e.salesInvoiceId = i.id)",
                InvoiceStatus.ISSUED);
        long inProgress = db.count("select count(e) from EInvoice e where e.direction = ?1 and e.status in (?2, ?3)",
                EInvoiceDirection.OUTGOING, EInvoiceStatus.QUEUED, EInvoiceStatus.SENT);
        long errors = db.count("select count(e) from EInvoice e where e.direction = ?1 and e.status = ?2",
                EInvoiceDirection.OUTGOING, EInvoiceStatus.ERROR);
        long awaiting = db.count("select count(e) from EInvoice e where e.direction = ?1 and e.profile = ?2 and e.status = ?3",
                EInvoiceDirection.OUTGOING, EInvoiceProfile.TICARIFATURA, EInvoiceStatus.DELIVERED);
        long toRespond = db.count("select count(e) from EInvoice e where e.direction = ?1 and e.profile = ?2 and e.status = ?3 and e.deliveredAt >= ?4",
                EInvoiceDirection.INCOMING, EInvoiceProfile.TICARIFATURA, EInvoiceStatus.DELIVERED, windowStart);
        long toRecord = db.count("""
                select count(e) from EInvoice e where e.direction = ?1 and e.status <> ?2
                  and (e.purchaseInvoiceId is null or exists (select 1 from PurchaseInvoice p where p.id = e.purchaseInvoiceId and p.status = ?3))
                """, EInvoiceDirection.INCOMING, EInvoiceStatus.REJECTED, InvoiceStatus.CANCELLED);
        List<Object[]> month = db.list("""
                select count(e), coalesce(sum(e.payableAmount), 0) from EInvoice e
                 where e.direction = ?1 and e.issueDate >= ?2 and e.status not in (?3, ?4)
                """, Object[].class, EInvoiceDirection.OUTGOING, monthStart, EInvoiceStatus.ERROR, EInvoiceStatus.CANCELLED);
        long outgoingCount = month.isEmpty() ? 0 : ((Number) month.get(0)[0]).longValue();
        BigDecimal outgoingAmount = month.isEmpty() ? BigDecimal.ZERO : (BigDecimal) month.get(0)[1];
        long incomingMonth = db.count("select count(e) from EInvoice e where e.direction = ?1 and e.issueDate >= ?2",
                EInvoiceDirection.INCOMING, monthStart);
        return new SummaryDto((int) unsent, (int) inProgress, (int) errors, (int) awaiting, (int) toRespond, (int) toRecord,
                (int) outgoingCount, outgoingAmount, (int) incomingMonth);
    }

    public List<UnsentInvoiceDto> unsent() {
        SettingsDto settings = readSettings(ctx().requireCompanyId());
        List<SalesInvoice> rows = db.list("""
                select i from SalesInvoice i where i.status = ?1
                  and not exists (select 1 from EInvoice e where e.salesInvoiceId = i.id)
                order by i.invoiceDate desc, i.createdAt desc
                """, SalesInvoice.class, InvoiceStatus.ISSUED);
        List<UnsentInvoiceDto> result = new ArrayList<>();
        for (SalesInvoice i : rows.stream().limit(50).toList()) {
            BusinessPartner partner = db.get(BusinessPartner.class, i.partnerId);
            EInvoiceProfile profile = EInvoiceRules.resolveProfile(partner.eInvoiceAlias, null, settings.defaultProfile());
            result.add(new UnsentInvoiceDto(i.id, i.number, i.partnerId, partner.title, i.invoiceDate, i.currencyCode, i.grandTotal, profile));
        }
        return result;
    }

    public DetailDto get(UUID id) {
        EInvoice row = Guard.found(db.first("select e from EInvoice e where e.id = ?1", EInvoice.class, id), "e-Fatura bulunamadı");
        BusinessPartner partner = db.get(BusinessPartner.class, row.partnerId);
        SalesInvoice sales = row.salesInvoiceId == null ? null : db.find(SalesInvoice.class, row.salesInvoiceId);
        PurchaseInvoice purchase = row.purchaseInvoiceId == null ? null : db.find(PurchaseInvoice.class, row.purchaseInvoiceId);
        User createdBy = row.createdById == null ? null : db.find(User.class, row.createdById);
        Instant now = Instant.now();
        boolean incoming = row.direction == EInvoiceDirection.INCOMING;
        boolean recorded = purchase != null && purchase.status != InvoiceStatus.CANCELLED;
        List<EInvoiceLine> lines = readLines(row);
        LocalDate deadline = row.profile == EInvoiceProfile.TICARIFATURA && row.deliveredAt != null
                ? AccountingClock.localDate(row.deliveredAt.plusSeconds(EInvoiceRules.RESPONSE_DAYS * 86400L)) : null;
        List<EInvoiceEvent> events = db.list("select e from EInvoiceEvent e where e.eInvoiceId = ?1 order by e.createdAt", EInvoiceEvent.class, id);
        List<EventDto> eventDtos = events.stream().map(e -> {
            User u = e.userId == null ? null : db.find(User.class, e.userId);
            return new EventDto(e.id, e.status, e.code, e.message, u == null ? null : u.firstName + " " + u.lastName, e.createdAt);
        }).toList();
        return new DetailDto(row.id, row.direction, row.profile, row.status, row.number, row.uuid, row.partnerId, partner.code, partner.title,
                row.issueDate, row.currencyCode, row.taxTotal, row.payableAmount, row.salesInvoiceId, sales == null ? null : sales.number,
                row.purchaseInvoiceId, purchase == null ? null : purchase.number, row.statusCode, row.statusMessage, row.sentAt, row.deliveredAt,
                row.respondedAt, EInvoiceRules.canRespond(row.profile, row.status, row.deliveredAt, now), row.exchangeRate, row.lineExtensionAmount,
                row.allowanceTotal, row.senderAlias, row.receiverAlias, row.senderTaxId, row.receiverTaxId, row.responseNote, deadline, lines,
                taxesOf(lines), eventDtos, row.xml, createdBy == null ? null : createdBy.firstName + " " + createdBy.lastName,
                !incoming && row.status == EInvoiceStatus.ERROR && sales != null && sales.status == InvoiceStatus.ISSUED,
                incoming && !recorded && (row.profile == EInvoiceProfile.TICARIFATURA ? row.status == EInvoiceStatus.ACCEPTED : row.status != EInvoiceStatus.REJECTED));
    }

    private List<EInvoiceLine> readLines(EInvoice row) {
        if (row.lines == null || row.lines.isNull()) return List.of();
        return json.convertValue(row.lines, LINES_TYPE);
    }

    private static List<VatBreakdown> taxesOf(List<EInvoiceLine> lines) {
        return lines.stream().collect(Collectors.groupingBy(EInvoiceLine::vatRate)).entrySet().stream()
                .sorted(Comparator.comparing(e -> new BigDecimal(e.getKey())))
                .map(e -> new VatBreakdown(e.getKey(),
                        money(e.getValue().stream().map(l -> new BigDecimal(l.net())).reduce(BigDecimal.ZERO, BigDecimal::add)),
                        money(e.getValue().stream().map(l -> new BigDecimal(l.vat())).reduce(BigDecimal.ZERO, BigDecimal::add))))
                .toList();
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    // ---------- Outgoing ----------

    public DetailDto send(SendBody dto) {
        UUID companyId = ctx().requireCompanyId();
        UUID userId = ctx().requireUserId();
        db.nativeRows("SELECT id FROM sales_invoices WHERE id = ?1 FOR UPDATE", dto.salesInvoiceId());
        SalesInvoice invoice = loadSource(dto.salesInvoiceId());
        if (invoice == null) throw AppException.notFound("Satış faturası bulunamadı");
        Guard.state(invoice.status == InvoiceStatus.ISSUED, "İptal edilmiş fatura gönderilemez");
        EInvoice existing = db.first("select e from EInvoice e where e.salesInvoiceId = ?1", EInvoice.class, invoice.id).orElse(null);
        if (existing != null) {
            throw AppException.conflict(existing.status == EInvoiceStatus.ERROR
                    ? "GİB bu e-faturayı reddetti; yeniden gönderin" : "Fatura zaten gönderilmiş");
        }
        SettingsDto settings = readSettings(companyId);
        BusinessPartner partner = db.get(BusinessPartner.class, invoice.partnerId);
        String alias = partner.eInvoiceAlias;
        EInvoiceProfile profile = EInvoiceRules.resolveProfile(alias, dto.profile(), settings.defaultProfile());
        if (profile == null) {
            throw AppException.badRequest(alias != null && !alias.isEmpty()
                    ? partner.title + " e-Fatura mükellefi; e-Arşiv kullanılamaz"
                    : partner.title + " e-Fatura mükellefi değil; e-Arşiv olarak gönderin");
        }
        String number = nextNumber(companyId, profile);
        EInvoice data = outgoingData(invoice, partner, profile, number, settings.senderAlias());
        data.companyId = companyId;
        data.direction = EInvoiceDirection.OUTGOING;
        data.salesInvoiceId = invoice.id;
        data.partnerId = invoice.partnerId;
        data.createdById = userId;
        db.add(data);
        EInvoiceEvent event = new EInvoiceEvent();
        event.eInvoiceId = data.id;
        event.status = EInvoiceStatus.QUEUED;
        event.code = QUEUED.code();
        event.message = QUEUED.message();
        event.userId = userId;
        db.add(event);
        db.flush();
        return get(data.id);
    }

    public DetailDto resend(UUID id) {
        UUID companyId = ctx().requireCompanyId();
        EInvoice current = lock(id);
        Guard.state(current.direction == EInvoiceDirection.OUTGOING, "Yalnızca giden faturalar yeniden gönderilir");
        Guard.state(current.status == EInvoiceStatus.ERROR, "Yalnızca GİB'in reddettiği faturalar yeniden gönderilir");
        SalesInvoice invoice = current.salesInvoiceId == null ? null : loadSource(current.salesInvoiceId);
        Guard.state(invoice != null && invoice.status == InvoiceStatus.ISSUED, "Satış faturası iptal edilmiş");
        SettingsDto settings = readSettings(companyId);
        BusinessPartner partner = db.get(BusinessPartner.class, invoice.partnerId);
        String alias = partner.eInvoiceAlias;
        EInvoiceProfile profile = EInvoiceRules.resolveProfile(alias, current.profile, settings.defaultProfile());
        if (profile == null) profile = EInvoiceRules.resolveProfile(alias, null, settings.defaultProfile());
        boolean sameSeries = (profile == EInvoiceProfile.EARSIVFATURA) == (current.profile == EInvoiceProfile.EARSIVFATURA);
        String number = sameSeries ? current.number : nextNumber(companyId, profile);
        EInvoice data = outgoingData(invoice, partner, profile, number, settings.senderAlias());
        current.profile = data.profile;
        current.status = data.status;
        current.number = data.number;
        current.uuid = data.uuid;
        current.issueDate = data.issueDate;
        current.currencyCode = data.currencyCode;
        current.exchangeRate = data.exchangeRate;
        current.lineExtensionAmount = data.lineExtensionAmount;
        current.allowanceTotal = data.allowanceTotal;
        current.taxTotal = data.taxTotal;
        current.payableAmount = data.payableAmount;
        current.senderAlias = data.senderAlias;
        current.receiverAlias = data.receiverAlias;
        current.senderTaxId = data.senderTaxId;
        current.receiverTaxId = data.receiverTaxId;
        current.lines = data.lines;
        current.xml = data.xml;
        current.statusCode = data.statusCode;
        current.statusMessage = data.statusMessage;
        current.partnerId = invoice.partnerId;
        current.sentAt = null;
        current.deliveredAt = null;
        current.respondedAt = null;
        current.responseNote = null;
        EInvoiceEvent event = new EInvoiceEvent();
        event.eInvoiceId = id;
        event.status = EInvoiceStatus.QUEUED;
        event.code = EInvoiceRules.Codes.QUEUED;
        event.message = "Yeniden gönderildi (yeni ETTN " + data.uuid + ")";
        event.userId = ctx().userId;
        db.add(event);
        db.flush();
        return get(id);
    }

    public SyncResultDto sync() {
        Instant now = Instant.now();
        List<EInvoice> rows = db.list("""
                select e from EInvoice e where e.direction = ?1 and (
                    e.status = ?2 or e.status = ?3 or (e.status = ?4 and e.profile = ?5))
                order by e.createdAt
                """, EInvoice.class, EInvoiceDirection.OUTGOING, EInvoiceStatus.QUEUED, EInvoiceStatus.SENT,
                EInvoiceStatus.DELIVERED, EInvoiceProfile.TICARIFATURA);
        int processed = 0;
        for (EInvoice row : rows) {
            List<EInvoiceLine> lines = readLines(row);
            OutgoingStep step = EInvoiceRules.nextOutgoingStep(row.profile, row.status, row.deliveredAt,
                    () -> EInvoiceRules.validateForGib(new ValidationInput(row.profile, row.number, row.senderTaxId, row.receiverTaxId,
                            row.receiverAlias, row.currencyCode, row.exchangeRate, lines, row.payableAmount)), now);
            if (step == null) continue;
            if (!transition(row.id, row.status, new Step(step.status(), step.code(), step.message()), null)) continue;
            processed++;
            if (step.status() == EInvoiceStatus.ERROR) {
                BusinessPartner partner = db.find(BusinessPartner.class, row.partnerId);
                String title = partner == null ? "" : partner.title;
                notifyCompany(row.companyId, "einvoice:send", "e-Fatura reddedildi: " + row.number, title + " — " + step.message(), "/e-invoices/" + row.id);
            }
        }
        return new SyncResultDto(processed);
    }

    public DetailDto respond(UUID id, RespondBody dto) {
        answer(id, dto, EInvoiceDirection.INCOMING);
        return get(id);
    }

    public DetailDto simulateResponse(UUID id, RespondBody dto) {
        EInvoice row = answer(id, dto, EInvoiceDirection.OUTGOING);
        if ("REJECT".equals(dto.action())) {
            notifyCompany(ctx().requireCompanyId(), "einvoice:send", "Ticari fatura reddedildi: " + row.number, dto.note() == null ? "" : dto.note(), "/e-invoices/" + id);
        }
        return get(id);
    }

    // ---------- Incoming ----------

    public DetailDto simulateIncoming(SimulateIncoming dto) {
        UUID companyId = ctx().requireCompanyId();
        Company company = db.get(Company.class, companyId);
        BusinessPartner partner = dto.partnerId() != null
                ? db.first("select p from BusinessPartner p where p.id = ?1 and p.isActive = true", BusinessPartner.class, dto.partnerId()).orElse(null)
                : randomSupplier(company.baseCurrency);
        Guard.that(partner != null, "Tedarikçi bulunamadı");
        Guard.that(Arrays.asList(PartnerLedgerService.SUPPLIERS).contains(partner.type), partner.title + " tedarikçi değil");
        Guard.that(company.baseCurrency.equals(partner.currencyCode), "Yalnızca ana para biriminde faturalayan tedarikçiler simüle edilir");
        List<ProductVariant> variants = db.list("""
                select v from ProductVariant v, Product p where v.productId = p.id and v.isActive = true and p.isActive = true
                  and p.purchasePrice is not null and p.purchasePrice > 0
                """, ProductVariant.class);
        Guard.that(!variants.isEmpty(), "Önce alış fiyatı olan ürün ekleyin");
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<ProductVariant> picks = new ArrayList<>(variants);
        java.util.Collections.shuffle(picks, rnd);
        picks = picks.subList(0, Math.min(picks.size(), rnd.nextInt(1, 4)));
        List<EInvoiceLineInput> inputs = new ArrayList<>();
        for (ProductVariant v : picks) {
            Product p = db.get(Product.class, v.productId);
            Unit unit = db.get(Unit.class, p.unitId);
            BigDecimal vatRate = p.vatRateId == null ? new BigDecimal("20") : db.get(TaxRate.class, p.vatRateId).rate;
            String name = v.name != null && !v.name.isBlank() ? p.name + " " + v.name : p.name;
            BigDecimal qty = p.type == ProductType.SERVICE ? BigDecimal.valueOf(rnd.nextInt(1, 4)) : BigDecimal.valueOf(rnd.nextInt(1, 11));
            inputs.add(new EInvoiceLineInput(name, v.sku, v.id, unit.code, qty, p.purchasePrice, BigDecimal.ZERO, vatRate));
        }
        var built = EInvoiceRules.buildLines(inputs);
        SettingsDto settings = readSettings(companyId);
        Instant now = Instant.now();
        LocalDate issueDate = AccountingClock.today();
        String prefix = (partner.code.chars().filter(Character::isLetterOrDigit).mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining())
                .toUpperCase() + "XXX").substring(0, 3);
        String number = prefix + issueDate.getYear() + String.format("%09d", rnd.nextInt(1, 1_000_000_000));
        UUID uuid = UUID.randomUUID();
        EInvoiceProfile profile = dto.profile() == null ? EInvoiceProfile.TICARIFATURA : dto.profile();
        String senderAlias = partner.eInvoiceAlias != null ? partner.eInvoiceAlias : "urn:mail:defaultgb@" + partner.code.toLowerCase() + ".com.tr";
        String xml = EInvoiceXml.build(new UblInvoice(profile, number, uuid, issueDate.toString(), localTime(now), partner.currencyCode, null,
                List.of("Vade: " + partner.paymentTermDays + " gün"), partnerParty(partner), companyParty(company), built.lines(), built.totals(), null));
        EInvoice invoice = new EInvoice();
        invoice.companyId = companyId;
        invoice.direction = EInvoiceDirection.INCOMING;
        invoice.profile = profile;
        invoice.status = EInvoiceStatus.DELIVERED;
        invoice.number = number;
        invoice.uuid = uuid;
        invoice.partnerId = partner.id;
        invoice.issueDate = issueDate;
        invoice.currencyCode = partner.currencyCode;
        invoice.exchangeRate = BigDecimal.ONE;
        invoice.lineExtensionAmount = new BigDecimal(built.totals().lineExtension());
        invoice.allowanceTotal = new BigDecimal(built.totals().allowance());
        invoice.taxTotal = built.lines().stream().map(l -> new BigDecimal(l.vat())).reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.payableAmount = new BigDecimal(built.totals().payable());
        invoice.senderAlias = senderAlias;
        invoice.receiverAlias = settings.senderAlias();
        invoice.senderTaxId = partner.taxNumber;
        invoice.receiverTaxId = company.taxNumber;
        invoice.lines = json.valueToTree(built.lines());
        invoice.xml = xml;
        invoice.statusCode = EInvoiceRules.Codes.COMPLETED;
        invoice.statusMessage = "Gelen kutusuna ulaştı";
        invoice.sentAt = now;
        invoice.deliveredAt = now;
        invoice.createdById = ctx().userId;
        db.add(invoice);
        EInvoiceEvent event = new EInvoiceEvent();
        event.eInvoiceId = invoice.id;
        event.status = EInvoiceStatus.DELIVERED;
        event.code = EInvoiceRules.Codes.COMPLETED;
        event.message = "Gelen kutusuna ulaştı (simülasyon)";
        event.userId = ctx().userId;
        db.add(event);
        db.flush();
        notifyCompany(companyId, "einvoice:update", "Yeni gelen e-Fatura: " + partner.title,
                number + " · " + built.totals().payable() + " " + partner.currencyCode, "/e-invoices/" + invoice.id);
        return get(invoice.id);
    }

    public DetailDto record(UUID id) {
        EInvoice row = Guard.found(db.first("select e from EInvoice e where e.id = ?1", EInvoice.class, id), "e-Fatura bulunamadı");
        Guard.state(row.direction == EInvoiceDirection.INCOMING, "Yalnızca gelen faturalar kaydedilir");
        PurchaseInvoice existing = row.purchaseInvoiceId == null ? null : db.find(PurchaseInvoice.class, row.purchaseInvoiceId);
        Guard.state(existing == null || existing.status == InvoiceStatus.CANCELLED, "Fatura zaten kaydedilmiş");
        Guard.state(row.status != EInvoiceStatus.REJECTED, "Reddedilen fatura kaydedilemez");
        Guard.state(!(row.profile == EInvoiceProfile.TICARIFATURA && row.status != EInvoiceStatus.ACCEPTED), "Önce ticari faturayı kabul edin");
        List<EInvoiceLine> lines = readLines(row);
        Set<UUID> variantIds = lines.stream().map(EInvoiceLine::variantId).filter(v -> v != null).collect(Collectors.toSet());
        Set<UUID> known = new HashSet<>();
        if (!variantIds.isEmpty()) {
            for (ProductVariant v : db.list("""
                    select v from ProductVariant v, Product p where v.id in ?1 and v.productId = p.id
                      and v.isActive = true and p.isActive = true
                    """, ProductVariant.class, variantIds)) {
                known.add(v.id);
            }
        }
        List<LineInput> tradeLines = lines.stream()
                .map(l -> new LineInput(l.variantId() != null && known.contains(l.variantId()) ? l.variantId() : null, l.description(),
                        new BigDecimal(l.quantity()), new BigDecimal(l.unitPrice()), new BigDecimal(l.discountRate()), new BigDecimal(l.vatRate()), null, null))
                .toList();
        PurchaseInvoiceService.Detail invoice = purchaseInvoices.create(new PurchaseInvoiceService.Create(row.partnerId, null, row.number, row.issueDate, null,
                row.currencyCode, BigDecimal.ONE.compareTo(row.exchangeRate) == 0 ? null : row.exchangeRate,
                "e-Fatura " + row.number + " (ETTN " + row.uuid + ")", tradeLines));
        row = db.get(EInvoice.class, id);
        row.purchaseInvoiceId = invoice.id();
        EInvoiceEvent event = new EInvoiceEvent();
        event.eInvoiceId = id;
        event.status = row.status;
        event.message = "Alış faturası oluşturuldu: " + invoice.number();
        event.userId = ctx().userId;
        db.add(event);
        db.flush();
        return get(id);
    }

    // ---------- Settings ----------

    public SettingsDto settings() { return readSettings(ctx().requireCompanyId()); }

    public SettingsDto updateSettings(UpdateSettings dto) {
        UUID companyId = ctx().requireCompanyId();
        SettingsDto current = readSettings(companyId);
        SettingsDto value = new SettingsDto(
                Guard.blank(dto.senderAlias()) ? current.senderAlias() : dto.senderAlias().trim(),
                dto.defaultProfile() == null ? current.defaultProfile() : dto.defaultProfile(),
                dto.autoSend() == null ? current.autoSend() : dto.autoSend());
        Guard.that(EInvoiceRules.ALIAS_PATTERN.matcher(value.senderAlias()).matches(),
                "Gönderici posta kutusu etiketi urn:mail:ad@alan.com biçiminde olmalı");
        ObjectNode node = json.createObjectNode();
        node.put("senderAlias", value.senderAlias());
        node.put("defaultProfile", value.defaultProfile().name());
        node.put("autoSend", value.autoSend());
        Setting row = db.first("select s from Setting s where s.key = ?1", Setting.class, SETTINGS_KEY).orElse(null);
        if (row == null) {
            row = new Setting();
            row.companyId = companyId;
            row.key = SETTINGS_KEY;
            row.value = node;
            db.add(row);
        } else {
            row.value = node;
        }
        db.flush();
        return value;
    }

    public void autoSend(UUID salesInvoiceId) {
        SettingsDto settings = readSettings(ctx().requireCompanyId());
        if (!settings.autoSend()) return;
        try {
            send(new SendBody(salesInvoiceId, null));
        } catch (AppException ignored) {
            // Stays in unsent list for manual send.
        }
    }

    // ---------- Internals ----------

    private EInvoice answer(UUID id, RespondBody dto, EInvoiceDirection direction) {
        String note = dto.note() == null ? null : dto.note().trim();
        Guard.that("ACCEPT".equals(dto.action()) || "REJECT".equals(dto.action()), "İşlem ACCEPT veya REJECT olmalı");
        Guard.that(!("REJECT".equals(dto.action()) && (note == null || note.isEmpty())), "Faturayı reddetmek için bir gerekçe girin");
        EInvoice row = lock(id);
        Guard.state(row.direction == direction,
                direction == EInvoiceDirection.INCOMING ? "Yalnızca gelen faturalar yanıtlanır" : "Giden faturayı yalnızca alıcı yanıtlar");
        Guard.state(EInvoiceRules.canRespond(row.profile, row.status, row.deliveredAt, Instant.now()),
                "Yalnızca ulaşmış ticari faturalar " + EInvoiceRules.RESPONSE_DAYS + " gün içinde yanıtlanır");
        if ("REJECT".equals(dto.action()) && row.purchaseInvoiceId != null) {
            InvoiceStatus status = db.first("select p.status from PurchaseInvoice p where p.id = ?1", InvoiceStatus.class, row.purchaseInvoiceId).orElse(null);
            Guard.state(status != InvoiceStatus.ISSUED, "Reddetmeden önce alış faturasını iptal edin");
        }
        boolean accepted = "ACCEPT".equals(dto.action());
        boolean incoming = direction == EInvoiceDirection.INCOMING;
        Step step = accepted
                ? new Step(EInvoiceStatus.ACCEPTED, EInvoiceRules.Codes.ACCEPTED, incoming ? "Fatura kabul edildi" : "Alıcı faturayı kabul etti")
                : new Step(EInvoiceStatus.REJECTED, EInvoiceRules.Codes.REJECTED,
                        (incoming ? "Fatura reddedildi" : "Alıcı faturayı reddetti") + ": " + note);
        applyStep(row, step, ctx().userId, accepted ? null : note);
        db.flush();
        return row;
    }

    private boolean transition(UUID id, EInvoiceStatus from, Step step, UUID userId) {
        db.nativeRows("SELECT id FROM e_invoices WHERE id = ?1 FOR UPDATE", id);
        EInvoiceStatus status = db.first("select e.status from EInvoice e where e.id = ?1", EInvoiceStatus.class, id).orElse(null);
        if (status != from) return false;
        EInvoice row = db.get(EInvoice.class, id);
        applyStep(row, step, userId, null);
        db.flush();
        return true;
    }

    private void applyStep(EInvoice row, Step step, UUID userId, String responseNote) {
        Instant now = Instant.now();
        row.status = step.status();
        row.statusCode = step.code();
        row.statusMessage = step.message();
        if (step.status() == EInvoiceStatus.SENT) row.sentAt = now;
        if (step.status() == EInvoiceStatus.DELIVERED) row.deliveredAt = now;
        if (step.status() == EInvoiceStatus.ACCEPTED || step.status() == EInvoiceStatus.REJECTED) {
            row.respondedAt = now;
            row.responseNote = responseNote;
        }
        EInvoiceEvent event = new EInvoiceEvent();
        event.eInvoiceId = row.id;
        event.status = step.status();
        event.code = step.code();
        event.message = step.message();
        event.userId = userId;
        db.add(event);
    }

    private EInvoice lock(UUID id) {
        db.nativeRows("SELECT id FROM e_invoices WHERE id = ?1 FOR UPDATE", id);
        return Guard.found(db.first("select e from EInvoice e where e.id = ?1", EInvoice.class, id), "e-Fatura bulunamadı");
    }

    private SalesInvoice loadSource(UUID id) {
        return db.first("select i from SalesInvoice i where i.id = ?1", SalesInvoice.class, id).orElse(null);
    }

    private EInvoice outgoingData(SalesInvoice invoice, BusinessPartner partner, EInvoiceProfile profile, String number, String senderAlias) {
        Company company = db.get(Company.class, invoice.companyId);
        List<SalesInvoiceLine> invoiceLines = db.list("select l from SalesInvoiceLine l where l.invoiceId = ?1 order by l.lineNo", SalesInvoiceLine.class, invoice.id);
        List<EInvoiceLineInput> inputs = new ArrayList<>();
        for (SalesInvoiceLine l : invoiceLines) {
            ProductVariant variant = l.variantId == null ? null : db.find(ProductVariant.class, l.variantId);
            String unitCode = null;
            if (variant != null) {
                Product product = db.get(Product.class, variant.productId);
                unitCode = db.get(Unit.class, product.unitId).code;
            }
            inputs.add(new EInvoiceLineInput(l.description, variant == null ? null : variant.sku, l.variantId, unitCode,
                    l.quantity, l.unitPrice, l.discountRate, l.vatRate));
        }
        var built = EInvoiceRules.buildLines(inputs);
        UUID uuid = UUID.randomUUID();
        boolean eArchive = profile == EInvoiceProfile.EARSIVFATURA;
        String receiverAlias = eArchive ? null : partner.eInvoiceAlias;
        List<String> notes = new ArrayList<>();
        notes.add("Fatura no: " + invoice.number);
        notes.add("Son ödeme tarihi: " + invoice.dueDate);
        if (invoice.notes != null && !invoice.notes.isEmpty()) notes.add(invoice.notes);
        SalesOrder order = invoice.orderId == null ? null : db.find(SalesOrder.class, invoice.orderId);
        String xml = EInvoiceXml.build(new UblInvoice(profile, number, uuid, invoice.invoiceDate.toString(), localTime(Instant.now()),
                invoice.currencyCode, "TRY".equals(invoice.currencyCode) ? null : invoice.exchangeRate.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString(),
                notes, companyParty(company), partnerParty(partner), built.lines(), built.totals(),
                order == null ? null : new OrderReference(order.number, order.orderDate.toString())));
        EInvoice data = new EInvoice();
        data.profile = profile;
        data.status = EInvoiceStatus.QUEUED;
        data.number = number;
        data.uuid = uuid;
        data.issueDate = invoice.invoiceDate;
        data.currencyCode = invoice.currencyCode;
        data.exchangeRate = invoice.exchangeRate;
        data.lineExtensionAmount = new BigDecimal(built.totals().lineExtension());
        data.allowanceTotal = new BigDecimal(built.totals().allowance());
        data.taxTotal = built.lines().stream().map(l -> new BigDecimal(l.vat())).reduce(BigDecimal.ZERO, BigDecimal::add);
        data.payableAmount = new BigDecimal(built.totals().payable());
        data.senderAlias = senderAlias;
        data.receiverAlias = receiverAlias;
        data.senderTaxId = company.taxNumber;
        data.receiverTaxId = partner.taxNumber;
        data.lines = json.valueToTree(built.lines());
        data.xml = xml;
        data.statusCode = QUEUED.code();
        data.statusMessage = QUEUED.message();
        return data;
    }

    private String nextNumber(UUID companyId, EInvoiceProfile profile) {
        int year = AccountingClock.today().getYear();
        ensureSequence(companyId, INVOICE_SEQUENCE, "e-Fatura", "EPF", year);
        ensureSequence(companyId, ARCHIVE_SEQUENCE, "e-Arşiv Fatura", "EPA", year);
        db.flush();
        String code = profile == EInvoiceProfile.EARSIVFATURA ? ARCHIVE_SEQUENCE : INVOICE_SEQUENCE;
        return EInvoiceRules.toGibNumber(numbering.next(code, companyId));
    }

    private void ensureSequence(UUID companyId, String code, String name, String prefix, int year) {
        NumberSequence row = db.first("select n from NumberSequence n where n.code = ?1", NumberSequence.class, code).orElse(null);
        if (row == null) {
            row = new NumberSequence();
            row.companyId = companyId;
            row.code = code;
            row.name = name;
            row.prefix = prefix;
            row.padding = 9;
            row.currentYear = year;
            db.add(row);
        } else if (row.padding != 9 && row.nextValue == 1) {
            row.padding = 9;
        }
    }

    private SettingsDto readSettings(UUID companyId) {
        String alias = "urn:mail:defaultgb@enterprisepulse.dev";
        EInvoiceProfile profile = EInvoiceProfile.TICARIFATURA;
        boolean auto = false;
        Setting row;
        try (var bypass = ctx().withoutTenant()) {
            row = db.first("select s from Setting s where s.companyId = ?1 and s.key = ?2", Setting.class, companyId, SETTINGS_KEY).orElse(null);
        }
        if (row != null && row.value != null) {
            JsonNode root = row.value;
            if (root.hasNonNull("senderAlias")) alias = root.get("senderAlias").asText();
            if (root.hasNonNull("defaultProfile")) {
                try { profile = EInvoiceProfile.valueOf(root.get("defaultProfile").asText()); } catch (IllegalArgumentException ignored) { }
            }
            if (root.has("autoSend") && root.get("autoSend").isBoolean()) auto = root.get("autoSend").asBoolean();
        }
        return new SettingsDto(alias, profile, auto);
    }

    private BusinessPartner randomSupplier(String currencyCode) {
        List<BusinessPartner> suppliers = db.list("""
                select p from BusinessPartner p where p.isActive = true and p.type in (?1) and p.currencyCode = ?2 and p.taxNumber is not null
                """, BusinessPartner.class, Arrays.asList(PartnerLedgerService.SUPPLIERS), currencyCode);
        return suppliers.isEmpty() ? null : suppliers.get(ThreadLocalRandom.current().nextInt(suppliers.size()));
    }

    private void notifyCompany(UUID companyId, String permission, String title, String body, String link) {
        List<UUID> users = notifications.usersWithPermission(companyId, permission);
        notifications.notify(new NotifyRequest(users, companyId, "einvoice", title, body, link));
    }

    private static String localTime(Instant utc) {
        return ZonedDateTime.ofInstant(utc, AccountingClock.ZONE).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private static UblParty companyParty(Company c) {
        return new UblParty(c.taxNumber, c.legalName != null ? c.legalName : c.name, c.taxOffice, c.address, c.city, c.country, c.email);
    }

    private static UblParty partnerParty(BusinessPartner p) {
        return new UblParty(p.taxNumber, p.title, p.taxOffice, p.address, p.city, "TR", p.email);
    }
}
