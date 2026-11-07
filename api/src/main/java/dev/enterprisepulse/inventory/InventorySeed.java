package dev.enterprisepulse.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.BusinessPartner;
import dev.enterprisepulse.data.generated.PartnerType;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductCategory;
import dev.enterprisepulse.data.generated.ProductType;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.PurchaseOrderStatus;
import dev.enterprisepulse.data.generated.SerialNumber;
import dev.enterprisepulse.data.generated.StockMovementType;
import dev.enterprisepulse.data.generated.TrackingType;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.purchasing.GoodsReceiptService;
import dev.enterprisepulse.purchasing.PurchaseInvoiceService;
import dev.enterprisepulse.purchasing.PurchaseOrderService;
import dev.enterprisepulse.purchasing.PurchaseRequestService;
import dev.enterprisepulse.purchasing.SupplierQuoteService;
import dev.enterprisepulse.seed.SeedStep;
import dev.enterprisepulse.trade.TradeService;
import dev.enterprisepulse.workflow.WorkflowService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Demo stock, partners and purchasing. Receipts are real stock receipts. */
@Component
public class InventorySeed implements SeedStep {
    private record Line(String sku, BigDecimal qty, BigDecimal cost, String lot, Integer expiryInDays, List<String> serials) {
        static Line cost(String sku, String qty, String cost) { return new Line(sku, new BigDecimal(qty), new BigDecimal(cost), null, null, null); }
        static Line lotCost(String sku, String qty, String cost, String lot, int expiry) { return new Line(sku, new BigDecimal(qty), new BigDecimal(cost), lot, expiry, null); }
        static Line serials(String sku, String qty, List<String> serials) { return new Line(sku, new BigDecimal(qty), null, null, null, serials); }
        static Line lot(String sku, String qty, String lot) { return new Line(sku, new BigDecimal(qty), null, lot, null, null); }
        static Line plain(String sku, String qty) { return new Line(sku, new BigDecimal(qty), null, null, null, null); }
        Line withCost(String value) { return new Line(sku, qty, new BigDecimal(value), lot, expiryInDays, serials); }
    }

    private final Db db;
    private final ObjectMapper json;
    private final StockMovementService movements;
    private final StockCountService counts;
    private final PurchaseRequestService requests;
    private final SupplierQuoteService quotes;
    private final PurchaseOrderService orders;
    private final GoodsReceiptService receipts;
    private final PurchaseInvoiceService invoices;
    private final WorkflowService workflow;

    public InventorySeed(Db db, ObjectMapper json, StockMovementService movements, StockCountService counts, PurchaseRequestService requests,
                         SupplierQuoteService quotes, PurchaseOrderService orders, GoodsReceiptService receipts, PurchaseInvoiceService invoices,
                         WorkflowService workflow) {
        this.db = db;
        this.json = json;
        this.movements = movements;
        this.counts = counts;
        this.requests = requests;
        this.quotes = quotes;
        this.orders = orders;
        this.receipts = receipts;
        this.invoices = invoices;
        this.workflow = workflow;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void run(Context ctx) {
        Map<String, UUID> categories = categories();
        Map<String, UUID> warehouses = warehouses(ctx.pulseId);
        if (db.exists("select p.id from Product p")) return;
        Map<String, UUID> variants = products(categories);
        LocalDate today = LocalDate.now();
        move(StockMovementType.RECEIPT, today.minusDays(60), null, warehouses.get("MRK"), "İRS-A-104288", "Yıllık tedarik anlaşması kapsamında ilk teslimat", List.of(
                Line.serials("PRD-LAPTOP", "6", range("LTP7-", 1, 6)).withCost("32000"),
                Line.serials("PRD-PHONE", "4", range("PHN-", 1, 4)).withCost("18000"),
                Line.cost("PRD-MONITOR", "20", "6500"), Line.cost("PRD-MOUSE-SYH", "60", "350"), Line.cost("PRD-MOUSE-BYZ", "40", "360"),
                Line.cost("PRD-KLAVYE-Q", "25", "1200"), Line.cost("PRD-KLAVYE-F", "10", "1250"),
                Line.cost("PRD-POLO-LCV-S", "20", "220"), Line.cost("PRD-POLO-LCV-M", "40", "220"), Line.cost("PRD-POLO-LCV-L", "40", "220"), Line.cost("PRD-POLO-LCV-XL", "20", "220"),
                Line.cost("PRD-A4", "80", "450"), Line.cost("PRD-TONER", "12", "1400"), Line.lotCost("PRD-KAHVE", "30", "520", "KHV-2401", 200),
                Line.cost("PRD-SU", "50", "70"), Line.lotCost("PRD-DEZ", "40", "180", "DZN-0915", 300)), variants);

        Map<String, UUID> partners = partners();
        var ankara = orders.create(order(partners.get("CAR-001"), warehouses.get("ANK"), today.minusDays(50), today.minusDays(43), "TRY", null,
                "Ankara ofisi açılış ihtiyaçları", List.of(
                        item(variants, "PRD-MONITOR", "8", "6700", "0", "20"), item(variants, "PRD-MOUSE-SYH", "20", "355", "0", "20"),
                        item(variants, "PRD-A4", "30", "460", "0", "20"), item(variants, "PRD-KAHVE", "15", "540", "0", "10"), item(variants, "PRD-SU", "30", "72", "0", "10"))));
        orders.approve(ankara.id());
        var ankaraDetail = orders.detail(ankara.id());
        receipts.create(new GoodsReceiptService.Create(ankara.id(), warehouses.get("ANK"), today.minusDays(45), "İRS-B-55120", null,
                ankaraDetail.lines().stream().map(l -> new GoodsReceiptService.LineInput(l.id(), l.quantity(), "PRD-KAHVE".equals(l.sku()) ? "KHV-2402" : null,
                        "PRD-KAHVE".equals(l.sku()) ? today.plusDays(240) : null, null)).toList()));
        invoices.create(new PurchaseInvoiceService.Create(partners.get("CAR-001"), ankara.id(), "KRD" + today.getYear() + "000418", today.minusDays(44), today.minusDays(14),
                "TRY", null, null, ankaraDetail.lines().stream().map(l -> new TradeService.LineInput(l.variantId(), l.description(), l.quantity(), l.unitPrice(), l.discountRate(), l.vatRate(), null, l.id())).toList()));

        move(StockMovementType.TRANSFER, today.minusDays(30), warehouses.get("MRK"), warehouses.get("IZM"), null, "İzmir şubesi açılış sevkiyatı", List.of(
                Line.serials("PRD-LAPTOP", "2", List.of("LTP7-0005", "LTP7-0006")), Line.plain("PRD-MONITOR", "4"),
                Line.plain("PRD-MOUSE-SYH", "15"), Line.plain("PRD-A4", "20"), Line.lot("PRD-DEZ", "10", "DZN-0915")), variants);
        move(StockMovementType.ISSUE, today.minusDays(20), warehouses.get("MRK"), null, "Bilgi İşlem talebi #118", "Yeni başlayan personel için ekipman çıkışı", List.of(
                Line.serials("PRD-LAPTOP", "2", List.of("LTP7-0001", "LTP7-0002")), Line.plain("PRD-MONITOR", "3"),
                Line.plain("PRD-MOUSE-SYH", "10"), Line.plain("PRD-KLAVYE-Q", "5"), Line.plain("PRD-TONER", "6"),
                Line.lot("PRD-KAHVE", "8", "KHV-2401"), Line.plain("PRD-POLO-LCV-M", "34")), variants);
        purchasing(ctx, variants, warehouses, partners, today);
        move(StockMovementType.ISSUE, today.minusDays(5), warehouses.get("ANK"), null, "Ofis tüketimi", null, List.of(
                Line.plain("PRD-SU", "12"), Line.lot("PRD-KAHVE", "5", "KHV-2402")), variants);
        stockCounts(warehouses);
    }

    private void purchasing(Context ctx, Map<String, UUID> variants, Map<String, UUID> warehouses, Map<String, UUID> partners, LocalDate today) {
        try (var ignored = CurrentUser.get().actAs(ctx.pulseId, ctx.usersByName.get("Kerem Polat"), false)) {
            CurrentUser.get().permissions.add("workflow:start");
            requests.create(new PurchaseRequestService.CreateRequest("5 adet dizüstü bilgisayar talebi", today.plusDays(14), null, "Yeni katılan yazılım ekibi için.",
                    List.of(new PurchaseRequestService.LineInput(variants.get("PRD-LAPTOP"), null, new BigDecimal("5"), new BigDecimal("32000")))));
        }
        UUID stationeryId;
        try (var ignored = CurrentUser.get().actAs(ctx.pulseId, ctx.usersByName.get("Deniz Aksoy"), false)) {
            CurrentUser.get().permissions.add("purchasing:create");
            stationeryId = requests.create(new PurchaseRequestService.CreateRequest("Ankara ofisi kırtasiye ihtiyacı", today.plusDays(10), null, null, List.of(
                    new PurchaseRequestService.LineInput(variants.get("PRD-A4"), null, new BigDecimal("40"), new BigDecimal("450")),
                    new PurchaseRequestService.LineInput(variants.get("PRD-TONER"), null, new BigDecimal("10"), new BigDecimal("1400")),
                    new PurchaseRequestService.LineInput(null, "Ergonomik ayak desteği", new BigDecimal("6"), new BigDecimal("750"))))).id();
        }
        var stationery = requests.detail(stationeryId);
        workflow.approve(stationery.workflowInstanceId(), "Uygun");
        workflow.approve(stationery.workflowInstanceId(), "Uygun");
        var lines = requests.detail(stationeryId).lines();
        quotes.create(quote(partners.get("TED-002"), stationeryId, today.minusDays(3), 3, List.of(
                item(variants, "PRD-A4", "40", "440", "0", "20", lines.get(0).id()), item(variants, "PRD-TONER", "10", "1380", "0", "20", lines.get(1).id()))));
        quotes.create(quote(partners.get("CAR-001"), stationeryId, today.minusDays(2), 7, List.of(
                item(variants, "PRD-A4", "40", "455", "5", "20", lines.get(0).id()), item(variants, "PRD-TONER", "10", "1350", "5", "20", lines.get(1).id()))));

        var restock = requests.create(new PurchaseRequestService.CreateRequest("Merkez depo monitör ve kahve ikmali", null, null, null, List.of(
                new PurchaseRequestService.LineInput(variants.get("PRD-MONITOR"), null, new BigDecimal("10"), new BigDecimal("6500")),
                new PurchaseRequestService.LineInput(variants.get("PRD-KAHVE"), null, new BigDecimal("20"), new BigDecimal("550")))));
        workflow.approve(restock.workflowInstanceId(), "Uygun");
        workflow.approve(restock.workflowInstanceId(), "Uygun");
        var restockLines = requests.detail(restock.id()).lines();
        quotes.create(quote(partners.get("CAR-001"), restock.id(), today.minusDays(15), 10, List.of(
                item(variants, "PRD-MONITOR", "10", "6750", "0", "20", restockLines.get(0).id()),
                item(variants, "PRD-KAHVE", "20", "545", "0", "10", restockLines.get(1).id()))));
        var accepted = quotes.create(quote(partners.get("TED-001"), restock.id(), today.minusDays(15), 5, List.of(
                item(variants, "PRD-MONITOR", "10", "6600", "0", "20", restockLines.get(0).id()),
                item(variants, "PRD-KAHVE", "20", "560", "0", "10", restockLines.get(1).id()))));
        UUID orderId = quotes.accept(accepted.id(), new SupplierQuoteService.Accept(warehouses.get("MRK"), today.plusDays(7)));
        orders.approve(orderId);
        var order = orders.detail(orderId);
        receipts.create(new GoodsReceiptService.Create(orderId, warehouses.get("MRK"), today.minusDays(10), "İRS-A-104731", null, List.of(
                new GoodsReceiptService.LineInput(order.lines().get(0).id(), new BigDecimal("5"), null, null, null),
                new GoodsReceiptService.LineInput(order.lines().get(1).id(), new BigDecimal("20"), "KHV-2403", today.plusDays(260), null))));
        invoices.create(new PurchaseInvoiceService.Create(partners.get("TED-001"), orderId, "ANB" + today.getYear() + "001204", today.minusDays(9), today.plusDays(21),
                "TRY", null, null, List.of(
                        new TradeService.LineInput(order.lines().get(0).variantId(), order.lines().get(0).description(), new BigDecimal("5"), new BigDecimal("6600"), BigDecimal.ZERO, new BigDecimal("20"), null, order.lines().get(0).id()),
                        new TradeService.LineInput(order.lines().get(1).variantId(), order.lines().get(1).description(), new BigDecimal("20"), new BigDecimal("560"), BigDecimal.ZERO, new BigDecimal("10"), null, order.lines().get(1).id()))));

        BigDecimal eur = db.first("select r.selling from ExchangeRate r where r.currencyCode = 'EUR' order by r.rateDate desc", BigDecimal.class).orElse(null);
        var euro = orders.create(order(partners.get("TED-003"), warehouses.get("MRK"), today.minusDays(3), today.plusDays(4), "EUR", eur, null, List.of(
                item(variants, "PRD-LAPTOP", "4", "690", "3", "20"), item(variants, "PRD-PHONE", "2", "380", "0", "20"))));
        var euroRow = db.get(dev.enterprisepulse.data.generated.PurchaseOrder.class, euro.id());
        euroRow.status = PurchaseOrderStatus.APPROVED;
        euroRow.approvedById = ctx.adminId;
        euroRow.approvedAt = Instant.now();
        orders.create(order(partners.get("TED-004"), warehouses.get("MRK"), today.minusDays(1), today.plusDays(6), null, null, null, List.of(
                item(variants, "PRD-DEZ", "30", "185", "0", "20"), item(variants, "PRD-SU", "60", "68", "0", "10"))));
        db.flush();
    }

    private void stockCounts(Map<String, UUID> warehouses) {
        var completed = counts.create(new StockCountService.CreateRequest(warehouses.get("MRK"), null, LocalDate.now().minusDays(3), "Ay sonu sayım farkları"));
        counts.updateLines(completed.id(), completed.lines().stream().map(l -> new StockCountService.LineUpdate(l.id(),
                "PRD-A4".equals(l.sku()) ? l.expectedQty().subtract(new BigDecimal("2")) : "PRD-SU".equals(l.sku()) ? l.expectedQty().add(BigDecimal.ONE) : l.expectedQty())).toList());
        counts.complete(completed.id());
        var open = counts.create(new StockCountService.CreateRequest(warehouses.get("IZM"), null, LocalDate.now(), "Çeyrek sonu sayımı"));
        counts.updateLines(open.id(), open.lines().stream().filter(l -> "PRD-MONITOR".equals(l.sku()))
                .map(l -> new StockCountService.LineUpdate(l.id(), l.expectedQty())).toList());
    }

    private void move(StockMovementType type, LocalDate date, UUID source, UUID target, String reference, String notes, List<Line> lines, Map<String, UUID> variants) {
        List<StockMovementService.LineInput> inputs = new ArrayList<>();
        for (Line l : lines) {
            UUID variantId = variants.get(l.sku());
            List<UUID> serialIds = null;
            if (l.serials() != null && type != StockMovementType.RECEIPT) {
                serialIds = db.list("select s.id from SerialNumber s where s.variantId = ?1 and s.serialNo in ?2", UUID.class, variantId, l.serials());
            }
            inputs.add(new StockMovementService.LineInput(variantId, l.qty(), l.cost(), null, l.lot(),
                    l.expiryInDays() == null ? null : LocalDate.now().plusDays(l.expiryInDays()),
                    type == StockMovementType.RECEIPT ? l.serials() : null, serialIds));
        }
        movements.post(new StockMovementService.CreateRequest(type, date, source, target, reference, notes, inputs), null);
    }

    private Map<String, UUID> categories() {
        String[][] rows = {{"ELK", "Elektronik", null}, {"BLG", "Bilgisayar ve Aksesuar", "ELK"}, {"TEL", "Telefon ve Tablet", "ELK"},
                {"OFS", "Ofis Malzemeleri", null}, {"KRT", "Kırtasiye", "OFS"}, {"TKS", "Tekstil", null}, {"GDA", "Gıda ve İçecek", null}, {"TMZ", "Temizlik", null}};
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (String[] row : rows) {
            ProductCategory existing = db.first("select c from ProductCategory c where c.code = ?1", ProductCategory.class, row[0]).orElse(null);
            if (existing == null) {
                existing = new ProductCategory();
                existing.code = row[0];
                existing.name = row[1];
                existing.parentId = row[2] == null ? null : ids.get(row[2]);
                db.add(existing);
                db.flush();
            }
            ids.put(row[0], existing.id);
        }
        return ids;
    }

    private Map<String, UUID> warehouses(UUID companyId) {
        String[][] rows = {{"MRK", "Merkez Depo", "MERKEZ", "Tuzla Organize Sanayi Bölgesi, İstanbul"},
                {"ANK", "Ankara Depo", "ANK", "Ostim OSB, Ankara"}, {"IZM", "İzmir Depo", "IZM", "Atatürk OSB, Çiğli, İzmir"}};
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (String[] row : rows) {
            Warehouse existing = db.first("select w from Warehouse w where w.code = ?1", Warehouse.class, row[0]).orElse(null);
            if (existing == null) {
                existing = new Warehouse();
                existing.code = row[0];
                existing.name = row[1];
                existing.address = row[3];
                existing.branchId = db.first("select b.id from Branch b where b.companyId = ?1 and b.code = ?2", UUID.class, companyId, row[2]).orElse(null);
                db.add(existing);
                db.flush();
            }
            ids.put(row[0], existing.id);
        }
        return ids;
    }

    private Map<String, UUID> products(Map<String, UUID> categories) {
        Map<String, UUID> units = new LinkedHashMap<>();
        db.list("select u from Unit u", dev.enterprisepulse.data.generated.Unit.class).forEach(u -> units.put(u.code, u.id));
        UUID vat = db.first("select t.id from TaxRate t where t.code = 'KDV20'", UUID.class).orElse(null);
        good("PRD-LAPTOP", "Dizüstü Bilgisayar 14\" i7 / 16 GB", categories.get("BLG"), units.get("ADET"), "32000", "39999", TrackingType.SERIAL, vat,
                variant("PRD-LAPTOP", null, "3", "8690000000017", null));
        good("PRD-MONITOR", "27\" IPS Monitör", categories.get("BLG"), units.get("ADET"), "6500", "8499", TrackingType.NONE, vat,
                variant("PRD-MONITOR", null, "5", "8690000000024", null));
        good("PRD-MOUSE", "Kablosuz Mouse", categories.get("BLG"), units.get("ADET"), "350", "549", TrackingType.NONE, vat,
                variant("PRD-MOUSE-SYH", "Siyah", "20", null, Map.of("Renk", "Siyah")), variant("PRD-MOUSE-BYZ", "Beyaz", "10", null, Map.of("Renk", "Beyaz")));
        good("PRD-KLAVYE", "Mekanik Klavye", categories.get("BLG"), units.get("ADET"), "1200", "1799", TrackingType.NONE, vat,
                variant("PRD-KLAVYE-Q", "Türkçe Q", "10", null, Map.of("Düzen", "Q")), variant("PRD-KLAVYE-F", "Türkçe F", "3", null, Map.of("Düzen", "F")));
        good("PRD-PHONE", "Akıllı Telefon 128 GB", categories.get("TEL"), units.get("ADET"), "18000", "22999", TrackingType.SERIAL, vat,
                variant("PRD-PHONE", null, "2", null, null));
        good("PRD-POLO", "Kurumsal Polo Tişört", categories.get("TKS"), units.get("ADET"), "220", null, TrackingType.NONE, vat,
                variant("PRD-POLO-LCV-S", "Lacivert / S", "5", null, Map.of("Renk", "Lacivert", "Beden", "S")),
                variant("PRD-POLO-LCV-M", "Lacivert / M", "10", null, Map.of("Renk", "Lacivert", "Beden", "M")),
                variant("PRD-POLO-LCV-L", "Lacivert / L", "10", null, Map.of("Renk", "Lacivert", "Beden", "L")),
                variant("PRD-POLO-LCV-XL", "Lacivert / XL", "5", null, Map.of("Renk", "Lacivert", "Beden", "XL")));
        good("PRD-A4", "A4 Fotokopi Kağıdı (5 top)", categories.get("KRT"), units.get("KOLI"), "450", "590", TrackingType.NONE, vat,
                variant("PRD-A4", null, "30", "8690000000031", null));
        good("PRD-TONER", "Lazer Yazıcı Toneri", categories.get("OFS"), units.get("ADET"), "1400", "1900", TrackingType.NONE, vat,
                variant("PRD-TONER", null, "8", null, null));
        good("PRD-KAHVE", "Filtre Kahve 1 kg", categories.get("GDA"), units.get("KG"), "520", "690", TrackingType.LOT, vat,
                variant("PRD-KAHVE", null, "10", null, null));
        good("PRD-SU", "Damacana Su 19 L", categories.get("GDA"), units.get("ADET"), "70", "95", TrackingType.NONE, vat,
                variant("PRD-SU", null, "20", null, null));
        good("PRD-DEZ", "Yüzey Dezenfektanı 5 L", categories.get("TMZ"), units.get("LT"), "180", "260", TrackingType.LOT, vat,
                variant("PRD-DEZ", null, "15", null, null));
        good("SRV-KURULUM", "Kurulum ve Yapılandırma Hizmeti", categories.get("BLG"), units.get("SAAT"), null, "1500", TrackingType.NONE, vat,
                variant("SRV-KURULUM", null, "0", null, null));
        db.flush();
        var service = db.first("select p from Product p where p.code = 'SRV-KURULUM'", Product.class).orElseThrow();
        service.type = ProductType.SERVICE;
        service.purchasePrice = null;
        Map<String, UUID> ids = new LinkedHashMap<>();
        db.list("select v from ProductVariant v", ProductVariant.class).forEach(v -> ids.put(v.sku, v.id));
        return ids;
    }

    private void good(String code, String name, UUID category, UUID unit, String purchase, String sale, TrackingType tracking, UUID vat, Variant... variants) {
        Product p = new Product();
        p.code = code;
        p.name = name;
        p.categoryId = category;
        p.unitId = unit;
        p.purchasePrice = purchase == null ? null : new BigDecimal(purchase);
        p.salePrice = sale == null ? null : new BigDecimal(sale);
        p.trackingType = tracking;
        p.vatRateId = vat;
        p.type = ProductType.GOODS;
        db.add(p);
        boolean first = true;
        for (Variant v : variants) {
            ProductVariant row = new ProductVariant();
            row.productId = p.id;
            row.sku = v.sku;
            row.name = v.name;
            row.barcode = v.barcode;
            row.minStock = new BigDecimal(v.min);
            row.isDefault = first;
            row.attributes = v.attributes == null ? null : json.valueToTree(v.attributes);
            db.add(row);
            first = false;
        }
    }

    private record Variant(String sku, String name, String min, String barcode, Map<String, String> attributes) {}

    private static Variant variant(String sku, String name, String min, String barcode, Map<String, String> attributes) {
        return new Variant(sku, name, min, barcode, attributes);
    }

    private Map<String, UUID> partners() {
        Object[][] rows = {
                {"TED-001", "Anadolu Bilişim Ltd. Şti.", PartnerType.SUPPLIER, "İstanbul", "1112223334", 30, "TRY", null},
                {"TED-002", "Ege Kırtasiye San. Tic. A.Ş.", PartnerType.SUPPLIER, "İzmir", "2223334445", 45, "TRY", null},
                {"TED-003", "TechSource Europe GmbH", PartnerType.SUPPLIER, "München", "3334445556", 60, "EUR", null},
                {"TED-004", "Marmara Ambalaj A.Ş.", PartnerType.SUPPLIER, "Kocaeli", "4445556667", 30, "TRY", null},
                {"MUS-001", "Boğaziçi Perakende A.Ş.", PartnerType.CUSTOMER, "İstanbul", "5556667778", 30, "TRY", "500000"},
                {"MUS-002", "Başkent Ofis Çözümleri Ltd.", PartnerType.CUSTOMER, "Ankara", "6667778889", 15, "TRY", "150000"},
                {"MUS-003", "Akdeniz Otelcilik A.Ş.", PartnerType.CUSTOMER, "Antalya", "7778889990", 45, "TRY", "250000"},
                {"MUS-004", "Nordic Retail AB", PartnerType.CUSTOMER, "Stockholm", "8889990001", 30, "USD", "40000"},
                {"CAR-001", "Karadeniz Ticaret ve Dağıtım", PartnerType.BOTH, "Trabzon", "9990001112", 30, "TRY", "100000"},
        };
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (Object[] r : rows) {
            BusinessPartner row = new BusinessPartner();
            row.code = (String) r[0];
            row.title = (String) r[1];
            row.type = (PartnerType) r[2];
            row.city = (String) r[3];
            row.taxNumber = (String) r[4];
            row.taxOffice = (String) r[3];
            row.paymentTermDays = (Integer) r[5];
            row.currencyCode = (String) r[6];
            row.creditLimit = r[7] == null ? null : new BigDecimal((String) r[7]);
            row.email = "info@" + row.code.toLowerCase() + ".example.com";
            row.phone = "+90 212 555 00 00";
            row.contactName = row.type == PartnerType.SUPPLIER ? "Satış Temsilcisi" : "Satın Alma Sorumlusu";
            db.add(row);
            ids.put(row.code, row.id);
        }
        db.flush();
        return ids;
    }

    private static PurchaseOrderService.Save order(UUID partner, UUID warehouse, LocalDate date, LocalDate expected, String currency, BigDecimal rate, String notes, List<TradeService.LineInput> lines) {
        return new PurchaseOrderService.Save(partner, warehouse, date, expected, currency, rate, notes, lines);
    }

    private static SupplierQuoteService.CreateQuote quote(UUID partner, UUID request, LocalDate date, int days, List<TradeService.LineInput> lines) {
        return new SupplierQuoteService.CreateQuote(partner, request, date, date.plusDays(30), days, "TRY", BigDecimal.ONE, null, lines);
    }

    private static TradeService.LineInput item(Map<String, UUID> variants, String sku, String qty, String price, String discount, String vat) {
        return item(variants, sku, qty, price, discount, vat, null);
    }

    private static TradeService.LineInput item(Map<String, UUID> variants, String sku, String qty, String price, String discount, String vat, UUID requestLineId) {
        return new TradeService.LineInput(variants.get(sku), null, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(discount), new BigDecimal(vat), requestLineId, null);
    }

    private static List<String> range(String prefix, int from, int to) {
        List<String> list = new ArrayList<>();
        for (int n = from; n <= to; n++) list.add(prefix + String.format("%04d", n));
        return list;
    }
}
