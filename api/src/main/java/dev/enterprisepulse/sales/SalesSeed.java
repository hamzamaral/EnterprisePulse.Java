package dev.enterprisepulse.sales;

import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.TreasuryAccount;
import dev.enterprisepulse.data.generated.TreasuryAccountType;
import dev.enterprisepulse.data.generated.TreasuryDocumentType;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.finance.PaymentService;
import dev.enterprisepulse.finance.TreasuryService;
import dev.enterprisepulse.seed.SeedStep;
import dev.enterprisepulse.trade.TradeService.LineInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SalesSeed implements SeedStep {
    private final Db db;
    private final TreasuryService treasury;
    private final SalesQuoteService quotes;
    private final SalesOrderService orders;
    private final DeliveryNoteService deliveries;
    private final SalesInvoiceService invoices;
    private final PaymentService payments;

    public SalesSeed(Db db, TreasuryService treasury, SalesQuoteService quotes, SalesOrderService orders, DeliveryNoteService deliveries,
                      SalesInvoiceService invoices, PaymentService payments) {
        this.db = db;
        this.treasury = treasury;
        this.quotes = quotes;
        this.orders = orders;
        this.deliveries = deliveries;
        this.invoices = invoices;
        this.payments = payments;
    }

    @Override public int order() { return 40; }

    @Override
    public void run(SeedStep.Context ctx) {
        account("KASA-MRK", "Merkez Kasa", TreasuryAccountType.CASH, "TRY", null, new BigDecimal("25000"));
        account("BANK-TRY", "Merkez Vadesiz TL", TreasuryAccountType.BANK, "TRY", "Pulse Bank", new BigDecimal("180000"));
        account("BANK-EUR", "Merkez Vadesiz EUR", TreasuryAccountType.BANK, "EUR", "Pulse Bank", new BigDecimal("4000"));
        if (db.exists("select q.id from SalesQuote q")) return;
        ProductVariant monitor = variant("PRD-MONITOR");
        ProductVariant mouse = variant("PRD-MOUSE-SYH");
        ProductVariant service = variant("SRV-KURULUM");
        ProductVariant paper = variant("PRD-A4");
        BusinessPartner bogazici = partner("MUS-001");
        BusinessPartner baskent = partner("MUS-002");
        Warehouse warehouse = db.first("select w from Warehouse w where w.code = ?1", Warehouse.class, "MRK").orElse(null);
        if (monitor == null || mouse == null || service == null || paper == null || bogazici == null || baskent == null || warehouse == null) return;
        LocalDate today = LocalDate.now();
        var accepted = quotes.create(new SalesQuoteService.Create(bogazici.id, today.minusDays(12), today.plusDays(20), "TRY", null,
                "Mağaza açılışı için monitör, mouse ve kurulum.", List.of(line(monitor.id, "4", "8499"), line(mouse.id, "10", "549"), line(service.id, "2", "1500"))));
        UUID orderId = quotes.accept(accepted.id(), warehouse.id, today.minusDays(5));
        var order = orders.approve(orderId);
        var monitorLine = order.lines().stream().filter(l -> "PRD-MONITOR".equals(l.sku())).findFirst().orElseThrow();
        var mouseLine = order.lines().stream().filter(l -> "PRD-MOUSE-SYH".equals(l.sku())).findFirst().orElseThrow();
        var serviceLine = order.lines().stream().filter(l -> "SRV-KURULUM".equals(l.sku())).findFirst().orElseThrow();
        deliveries.create(new DeliveryNoteService.Create(orderId, today.minusDays(8), "Boğaziçi Perakende, Levent", "Pulse Lojistik", null, List.of(
                new DeliveryNoteService.LineInput(monitorLine.id(), new BigDecimal("2"), null, null),
                new DeliveryNoteService.LineInput(mouseLine.id(), new BigDecimal("10"), null, null))));
        var invoice = invoices.create(new SalesInvoiceService.Create(bogazici.id, orderId, today.minusDays(7), today.minusDays(3), "TRY", null, null, List.of(
                priced(monitorLine), priced(mouseLine), priced(serviceLine))));
        UUID cash = db.first("select a from TreasuryAccount a where a.code = ?1", TreasuryAccount.class, "KASA-MRK").orElseThrow().id;
        payments.create(new PaymentService.Create(dev.enterprisepulse.data.generated.PaymentDirection.IN, dev.enterprisepulse.data.generated.PaymentMethod.CASH,
                bogazici.id, today.minusDays(2), cash, new BigDecimal("12000"), "TRY", null, "Açılış siparişi kısmi tahsilat", null, null, null, null, null, null,
                List.of(new PaymentService.AllocationIn(invoice.id(), null, new BigDecimal("12000")))));
        payments.create(new PaymentService.Create(dev.enterprisepulse.data.generated.PaymentDirection.IN, dev.enterprisepulse.data.generated.PaymentMethod.CHEQUE,
                bogazici.id, today.minusDays(1), null, new BigDecimal("8000"), "TRY", null, "Kalan için müşteri çeki", "CEK-88421", "Pulse Bank", "Levent",
                "Boğaziçi Perakende A.Ş.", today.plusDays(20), null, null));
        quotes.create(new SalesQuoteService.Create(baskent.id, today.minusDays(2), today.plusDays(15), "TRY", null, "A4 kâğıt teklifi, henüz yanıt yok.",
                List.of(line(paper.id, "20", "590"))));
    }

    private void account(String code, String name, TreasuryAccountType type, String currency, String bank, BigDecimal opening) {
        TreasuryAccount account = db.first("select a from TreasuryAccount a where a.code = ?1", TreasuryAccount.class, code).orElse(null);
        if (account == null) {
            account = new TreasuryAccount();
            account.code = code;
            account.name = name;
            account.type = type;
            account.currencyCode = currency;
            account.bankName = bank;
            db.add(account);
            db.flush();
        }
        if (opening.signum() > 0 && !db.exists("select t.id from TreasuryTransaction t where t.accountId = ?1", account.id)) {
            treasury.post(account.id, LocalDate.now().minusDays(90), TreasuryDocumentType.OPENING, account.id, "ACILIS", "Açılış bakiyesi", opening, BigDecimal.ZERO);
        }
    }

    private ProductVariant variant(String sku) {
        return db.first("select v from ProductVariant v where v.sku = ?1", ProductVariant.class, sku).orElse(null);
    }

    private BusinessPartner partner(String code) {
        return db.first("select p from BusinessPartner p where p.code = ?1", BusinessPartner.class, code).orElse(null);
    }

    private static LineInput line(UUID variantId, String qty, String price) {
        return new LineInput(variantId, null, new BigDecimal(qty), new BigDecimal(price), BigDecimal.ZERO, new BigDecimal("20"), null, null);
    }

    private static LineInput priced(SalesOrderService.LineDto line) {
        return new LineInput(line.variantId(), line.description(), line.sku().equals("PRD-MONITOR") ? new BigDecimal("2") : line.quantity(),
                line.unitPrice(), line.discountRate(), line.vatRate(), null, line.id());
    }
}
