package dev.enterprisepulse.production;

import dev.enterprisepulse.accounting.AccountingClock;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Product;
import dev.enterprisepulse.data.generated.ProductCategory;
import dev.enterprisepulse.data.generated.ProductVariant;
import dev.enterprisepulse.data.generated.Unit;
import dev.enterprisepulse.data.generated.Warehouse;
import dev.enterprisepulse.data.generated.WorkOrder;
import dev.enterprisepulse.data.generated.WorkOrderStatus;
import dev.enterprisepulse.platform.Extensibility.DashboardCard;
import dev.enterprisepulse.platform.Extensibility.DashboardProvider;
import dev.enterprisepulse.platform.Extensibility.DashboardSection;
import dev.enterprisepulse.production.ProductionService.BomLineInput;
import dev.enterprisepulse.production.ProductionService.CreateBom;
import dev.enterprisepulse.production.ProductionService.CreateWorkOrder;
import dev.enterprisepulse.production.ProductionService.RecordProduction;
import dev.enterprisepulse.sales.SalesHooks;
import dev.enterprisepulse.seed.SeedStep;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

public final class ProductionSeed {
    private ProductionSeed() {}

    @Component public static class BillOfMaterialsHook extends SalesHooks.Managed {
        public BillOfMaterialsHook() { super("bill-of-materials", "Ürün ağaçları Üretim > Ürün Ağaçları ekranından kaydedilir"); }
    }

    @Component public static class BomLinesHook extends SalesHooks.Managed {
        public BomLinesHook() { super("bom-lines", "Ürün ağacı bileşenleri reçeteyle birlikte kaydedilir"); }
    }

    @Component public static class WorkOrdersHook extends SalesHooks.Managed {
        public WorkOrdersHook() { super("work-orders", "İş emirleri Üretim > İş Emirleri ekranından yönetilir"); }
    }

    @Component public static class WorkOrderComponentsHook extends SalesHooks.Managed {
        public WorkOrderComponentsHook() { super("work-order-components", "İş emri bileşenleri ürün ağacından kopyalanır"); }
    }

    @Component public static class ProductionRecordsHook extends SalesHooks.Managed {
        public ProductionRecordsHook() { super("production-records", "Üretim fişleri iş emri üzerinden girilir; stok hareketleri otomatik oluşur"); }
    }

    @Component
    @Transactional(readOnly = true)
    public static class Dashboard implements DashboardProvider {
        private final ProductionService production;
        public Dashboard(ProductionService production) { this.production = production; }
        @Override public int order() { return 55; }
        @Override public DashboardSection section() {
            if (!CurrentUser.get().has("production:read")) return null;
            ProductionService.SummaryDto s = production.summary();
            int open = s.draft() + s.released() + s.inProgress();
            return new DashboardSection("production", "Üretim", List.of(
                    new DashboardCard("production-open", "Açık iş emri", Integer.toString(open), "PrecisionManufacturing", "/r/work-orders"),
                    new DashboardCard("production-late", "Geciken iş emri", Integer.toString(s.late()), "RunningWithErrors", null, "/r/work-orders", s.late() > 0 ? "error" : "primary"),
                    new DashboardCard("production-shortage", "Malzeme eksiği", Integer.toString(s.shortages()), "Inventory", null, "/production/requirements", s.shortages() > 0 ? "warning" : "primary")));
        }
    }

    @Component
    public static class Seed implements SeedStep {
        private record MadeProduct(String code, String name, String unit, BigDecimal salePrice, BigDecimal minStock) {}
        private record SampleBom(String sku, String name, BigDecimal quantity, BigDecimal labor, BigDecimal overhead, String notes, SampleLine[] lines) {}
        private record SampleLine(String sku, BigDecimal quantity, BigDecimal scrap) {}
        private record SampleOrder(String sku, BigDecimal quantity, WorkOrderStatus status, int start, int end, String reference, SampleRecord[] records) {}
        private record SampleRecord(int daysAgo, BigDecimal quantity) {}

        private static final MadeProduct[] PRODUCTS = {
                new MadeProduct("PRD-ISTASYON", "Çalışma İstasyonu Seti", "ADET", new BigDecimal("10900"), new BigDecimal("2")),
                new MadeProduct("PRD-KIT", "Yeni Personel Başlangıç Kiti", "ADET", new BigDecimal("12900"), BigDecimal.ZERO),
                new MadeProduct("PRD-IKRAM", "Toplantı İkram Paketi", "ADET", new BigDecimal("790"), BigDecimal.ZERO),
        };

        private static final SampleBom[] BOMS = {
                new SampleBom("PRD-ISTASYON", "Çalışma istasyonu montajı", BigDecimal.ONE, new BigDecimal("250"), new BigDecimal("75"),
                        "Monitör, klavye ve mouse kutudan çıkarılıp test edilir, set halinde paketlenir.",
                        new SampleLine[] {new SampleLine("PRD-MONITOR", BigDecimal.ONE, BigDecimal.ZERO), new SampleLine("PRD-KLAVYE-Q", BigDecimal.ONE, BigDecimal.ZERO),
                                new SampleLine("PRD-MOUSE-SYH", BigDecimal.ONE, BigDecimal.ZERO)}),
                new SampleBom("PRD-KIT", "Personel başlangıç kiti", BigDecimal.ONE, new BigDecimal("120"), new BigDecimal("40"), null,
                        new SampleLine[] {new SampleLine("PRD-ISTASYON", BigDecimal.ONE, BigDecimal.ZERO), new SampleLine("PRD-POLO-LCV-M", BigDecimal.ONE, BigDecimal.ZERO),
                                new SampleLine("PRD-A4", BigDecimal.ONE, BigDecimal.ZERO)}),
                new SampleBom("PRD-IKRAM", "İkram paketi (10 kişilik)", new BigDecimal("10"), new BigDecimal("300"), new BigDecimal("100"), null,
                        new SampleLine[] {new SampleLine("PRD-KAHVE", new BigDecimal("5"), new BigDecimal("4")), new SampleLine("PRD-SU", new BigDecimal("20"), BigDecimal.ZERO)}),
        };

        private static final SampleOrder[] ORDERS = {
                new SampleOrder("PRD-ISTASYON", new BigDecimal("3"), WorkOrderStatus.COMPLETED, 14, 12, "Bilgi İşlem talebi #131",
                        new SampleRecord[] {new SampleRecord(12, new BigDecimal("3"))}),
                new SampleOrder("PRD-KIT", new BigDecimal("12"), WorkOrderStatus.RELEASED, -3, -7, "Eylül işe alım dönemi", new SampleRecord[0]),
                new SampleOrder("PRD-IKRAM", new BigDecimal("20"), WorkOrderStatus.DRAFT, -10, -11, null, new SampleRecord[0]),
                new SampleOrder("PRD-ISTASYON", new BigDecimal("6"), WorkOrderStatus.IN_PROGRESS, 6, 1, null,
                        new SampleRecord[] {new SampleRecord(2, new BigDecimal("2"))}),
        };

        private final Db db;
        private final ProductionService production;

        public Seed(Db db, ProductionService production) {
            this.db = db;
            this.production = production;
        }

        @Override public int order() { return 81; }

        @Override
        public void run(Context ctx) {
            try (var scope = CurrentUser.get().actAs(ctx.pulseId, ctx.adminId, true)) {
                if (db.exists("select b.id from BillOfMaterials b")) return;
                ensureProducts();
                List<String> skus = Stream.concat(Stream.of(BOMS).flatMap(b -> Stream.concat(Stream.of(b.sku()), Stream.of(b.lines()).map(SampleLine::sku))), Stream.empty())
                        .distinct().toList();
                Map<String, UUID> variants = db.list("select v from ProductVariant v where v.sku in ?1", ProductVariant.class, skus).stream()
                        .collect(Collectors.toMap(v -> v.sku, v -> v.id));
                if (variants.size() != skus.size()) return;
                Map<String, UUID> bomIds = new HashMap<>();
                for (SampleBom b : BOMS) {
                    var created = production.createBom(new CreateBom(variants.get(b.sku()), b.sku() + "-R1", b.name(), b.quantity(), b.labor(), b.overhead(), true, b.notes(),
                            Stream.of(b.lines()).map(l -> new BomLineInput(variants.get(l.sku()), l.quantity(), l.scrap(), null)).toList()));
                    bomIds.put(b.sku(), created.id());
                }
                Warehouse warehouse = db.first("select w from Warehouse w where w.code = 'MRK'", Warehouse.class).orElse(null);
                if (warehouse == null) return;
                LocalDate today = AccountingClock.today();
                for (SampleOrder o : ORDERS) {
                    var created = production.createWorkOrder(new CreateWorkOrder(bomIds.get(o.sku()), o.quantity(), today.minusDays(o.start()), today.minusDays(o.end()),
                            warehouse.id, warehouse.id, o.reference(), null));
                    if (o.status() != WorkOrderStatus.DRAFT) production.release(created.order().id());
                    for (SampleRecord r : o.records()) {
                        production.record(created.order().id(), new RecordProduction(r.quantity(), today.minusDays(r.daysAgo()), null, null, null, null, null));
                    }
                    WorkOrder row = db.get(WorkOrder.class, created.order().id());
                    Instant createdAt = today.minusDays(Math.max(o.start(), 0) + 2).atTime(LocalTime.MIN).toInstant(ZoneOffset.UTC);
                    row.createdAt = createdAt;
                    if (o.status() != WorkOrderStatus.DRAFT) row.releasedAt = createdAt;
                    if (o.records().length > 0) {
                        Instant first = today.minusDays(o.records()[0].daysAgo()).atTime(LocalTime.MIN).toInstant(ZoneOffset.UTC);
                        row.startedAt = first;
                        row.completedAt = o.status() == WorkOrderStatus.COMPLETED ? first : null;
                    }
                    db.flush();
                }
            }
        }

        private void ensureProducts() {
            ProductCategory category = db.first("select c from ProductCategory c where c.code = 'BLG'", ProductCategory.class).orElse(null);
            UUID vat = db.first("select t.id from TaxRate t where t.code = 'KDV20'", UUID.class).orElse(null);
            Map<String, UUID> units = db.list("select u from Unit u", Unit.class).stream().collect(Collectors.toMap(u -> u.code, u -> u.id));
            for (MadeProduct p : PRODUCTS) {
                if (!units.containsKey(p.unit()) || db.exists("select x.id from Product x where x.code = ?1", p.code())) continue;
                Product product = new Product();
                product.code = p.code();
                product.name = p.name();
                product.categoryId = category == null ? null : category.id;
                product.unitId = units.get(p.unit());
                product.salePrice = p.salePrice();
                product.vatRateId = vat;
                db.add(product);
                db.flush();
                ProductVariant variant = new ProductVariant();
                variant.productId = product.id;
                variant.sku = p.code();
                variant.minStock = p.minStock();
                variant.isDefault = true;
                db.add(variant);
            }
            db.flush();
        }
    }
}
