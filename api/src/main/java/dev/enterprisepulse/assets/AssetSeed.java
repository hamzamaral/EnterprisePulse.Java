package dev.enterprisepulse.assets;

import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Asset;
import dev.enterprisepulse.data.generated.AssetAssignment;
import dev.enterprisepulse.data.generated.AssetCategory;
import dev.enterprisepulse.data.generated.AssetCondition;
import dev.enterprisepulse.data.generated.AssetCount;
import dev.enterprisepulse.data.generated.AssetCountLine;
import dev.enterprisepulse.data.generated.AssetCountStatus;
import dev.enterprisepulse.data.generated.AssetLocation;
import dev.enterprisepulse.data.generated.AssetMaintenance;
import dev.enterprisepulse.data.generated.AssetStatus;
import dev.enterprisepulse.data.generated.AssigneeType;
import dev.enterprisepulse.data.generated.AssignmentStatus;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.MaintenanceStatus;
import dev.enterprisepulse.data.generated.MaintenanceType;
import dev.enterprisepulse.platform.NumberingService;
import dev.enterprisepulse.seed.SeedStep;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Asset categories, locations, sample assets with assignments and maintenance, two counts, and the admin account's
 * employee profile (the general manager) with its own assets.
 */
@Component
public class AssetSeed implements SeedStep {
    private static final Object[][] CATEGORIES = {
            {"BLG", "Bilgisayar", 36}, {"TEL", "Mobil Cihaz", 24}, {"MON", "Monitör ve Ekran", 48},
            {"OFS", "Ofis Ekipmanı", 60}, {"MOB", "Mobilya", 60}, {"ARC", "Araç", 60},
    };

    private static final String[][] LOCATIONS = {
            {"DEPO", "Merkez Depo", "MERKEZ"}, {"BT-ODA", "BT Sistem Odası", "MERKEZ"}, {"TOPLANTI", "Toplantı Salonu", "MERKEZ"}, {"ANK-DEPO", "Ankara Depo", "ANK"},
    };

    private record Previous(String employee, String from, String to) {}

    private record Service(String title, String vendor, String startedAt, int cost) {}

    /** Holder: "employee:Name", "department:CODE", "location:CODE" or "stock:LOCATION". */
    private record Sample(String name, String category, String brand, String model, String serial, String purchaseDate, int cost, String holder,
                          String since, boolean acknowledged, Previous previous, Service maintenance, String retired) {
        Sample(String name, String category, String brand, String model, String serial, String purchaseDate, int cost, String holder, String since, boolean ack) {
            this(name, category, brand, model, serial, purchaseDate, cost, holder, since, ack, null, null, null);
        }
    }

    private static List<Sample> samples(int year) {
        return List.of(
                new Sample("Dizüstü Bilgisayar", "BLG", "Dell", "Latitude 7440", "DL7440-8KX21", "2024-04-15", 48500, "employee:Kerem Polat", "2024-05-02", true),
                new Sample("Cep Telefonu", "TEL", "Apple", "iPhone 15", "F2LXK9P1Q7", "2025-09-20", 52000, "employee:Kerem Polat", "2025-10-01", false),
                new Sample("Dizüstü Bilgisayar", "BLG", "Apple", "MacBook Pro 14", "C02FK3MBP14", "2024-01-10", 89000, "employee:Deniz Aksoy", "2024-01-15", true),
                new Sample("Dizüstü Bilgisayar", "BLG", "Lenovo", "ThinkPad T14 Gen 4", "PF4T14-22981", "2023-01-03", 39500, "employee:Elif Şahin", "2023-01-09", true),
                new Sample("Monitör 27\"", "MON", "Dell", "P2723DE", "CN0P27-55K1", "2023-01-03", 11800, "employee:Elif Şahin", "2023-01-09", true),
                new Sample("Cep Telefonu", "TEL", "Samsung", "Galaxy S23", "R5CW31ABX9", "2023-08-14", 31000, "employee:Ahmet Yıldız", "2023-08-21", true),
                new Sample("Akıllı Ekran 65\"", "MON", "Samsung", "QM65R", "SQM65-7781", "2024-06-05", 42000, "location:TOPLANTI", "2024-06-10", false),
                new Sample("Ağ Anahtarı", "OFS", "Cisco", "Catalyst 9200", "FOC2419X0AB", "2022-11-01", 64000, "location:BT-ODA", "2022-11-07", false),
                new Sample("Çok Fonksiyonlu Yazıcı", "OFS", "HP", "LaserJet Pro MFP 4103", "VNB3K40112", "2024-02-12", 18500, "department:FIN", "2024-02-15", false),
                new Sample("Hafif Ticari Araç", "ARC", "Ford", "Transit Connect", "WF0XXXTTGXNA12345", "2022-03-01", 685000, "department:OPS", "2022-03-10", false),
                new Sample("Dizüstü Bilgisayar", "BLG", "Lenovo", "ThinkPad E14", "PF4E14-10457", "2024-02-01", 27500, "stock:DEPO", null, false,
                        new Previous("Burak Doğan", "2024-02-01", "2025-12-19"), null, null),
                new Sample("Dizüstü Bilgisayar", "BLG", "HP", "EliteBook 840 G9", "5CG2310QWE", "2022-09-12", 34000, "stock:BT-ODA", null, false, null,
                        new Service("Ekran değişimi", "HP Yetkili Servis", year + "-10-02", 4200), null),
                new Sample("Tablet", "TEL", "Apple", "iPad Air 11", "DMPXK2IPAD", "2025-03-17", 28000, "stock:ANK-DEPO", null, false),
                new Sample("Ergonomik Ofis Koltuğu", "MOB", "Herman Miller", "Aeron", "HM-AER-30219", "2023-05-02", 36000, "stock:DEPO", null, false),
                new Sample("Masaüstü Bilgisayar", "BLG", "Dell", "OptiPlex 7070", "DLOPT7070-991", "2019-06-10", 14500, "stock:DEPO", null, false, null, null,
                        "Ekonomik ömrünü tamamladı, anakart arızası"));
    }

    private static List<Sample> profileAssets(int year) {
        int last = year - 1;
        return List.of(
                new Sample("Dizüstü Bilgisayar", "BLG", "Apple", "MacBook Pro 16", "C02HK7MBP16", last + "-11-04", 124_000, "", last + "-11-10", true),
                new Sample("Cep Telefonu", "TEL", "Apple", "iPhone 16 Pro", "G7QXM2P4R9", last + "-09-25", 78_000, "", last + "-10-01", true),
                new Sample("Monitör 27\"", "MON", "LG", "UltraFine 27UQ850", "304NTAB1K882", last + "-11-04", 21_500, "", last + "-11-10", false));
    }

    private final Db db;
    private final NumberingService numbering;
    private final AssetService assets;

    public AssetSeed(Db db, NumberingService numbering, AssetService assets) {
        this.db = db;
        this.numbering = numbering;
        this.assets = assets;
    }

    @Override
    public int order() {
        return 20;
    }

    private static Instant at9(LocalDate date) {
        return date.atTime(9, 0).toInstant(ZoneOffset.UTC);
    }

    @Override
    public void run(Context ctx) {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        Map<String, UUID> categories = new HashMap<>();
        for (Object[] c : CATEGORIES) {
            AssetCategory row = db.first("select c from AssetCategory c where c.code = ?1", AssetCategory.class, c[0]).orElse(null);
            if (row == null) {
                row = new AssetCategory();
                row.code = (String) c[0];
                row.name = (String) c[1];
                row.usefulLifeMonths = (Integer) c[2];
                db.add(row);
                db.flush();
            }
            categories.put(row.code, row.id);
        }
        Map<String, UUID> locations = new HashMap<>();
        for (String[] l : LOCATIONS) {
            AssetLocation row = db.first("select l from AssetLocation l where l.code = ?1", AssetLocation.class, l[0]).orElse(null);
            if (row == null) {
                row = new AssetLocation();
                row.code = l[0];
                row.name = l[1];
                row.branchId = db.first("select b.id from Branch b where b.companyId = ?1 and b.code = ?2", UUID.class, ctx.pulseId, l[2]).orElse(null);
                db.add(row);
                db.flush();
            }
            locations.put(row.code, row.id);
        }
        if (db.nativeRows("select id, 1 from assets where company_id = ?1 limit 1", ctx.pulseId).isEmpty()) seedAssets(ctx, year, categories, locations);
        seedCounts(ctx, year);
        seedProfile(ctx, year, categories);
    }

    private static BigDecimal salvage(int cost) {
        return BigDecimal.valueOf(cost).multiply(new BigDecimal("0.05")).setScale(0, RoundingMode.HALF_EVEN);
    }

    private Asset asset(Sample s, Map<String, UUID> categories, String invoiceNo) {
        LocalDate bought = LocalDate.parse(s.purchaseDate());
        Asset a = new Asset();
        a.assetTag = numbering.next("ASSET");
        a.name = s.name();
        a.categoryId = categories.get(s.category());
        a.brand = s.brand();
        a.model = s.model();
        a.serialNumber = s.serial();
        a.condition = AssetCondition.GOOD;
        a.purchaseDate = bought;
        a.purchaseCost = BigDecimal.valueOf(s.cost());
        a.salvageValue = salvage(s.cost());
        a.supplier = s.brand() + " Türkiye Distribütörü";
        a.invoiceNo = invoiceNo;
        a.warrantyEndDate = bought.plusYears(2);
        return a;
    }

    private AssetAssignment assignment(UUID assetId, UUID adminId, LocalDate since) {
        AssetAssignment x = new AssetAssignment();
        x.number = numbering.next("ASSET_ASSIGNMENT");
        x.assetId = assetId;
        x.assignedAt = since;
        x.conditionOut = AssetCondition.NEW;
        x.assignedById = adminId;
        return x;
    }

    private void seedAssets(Context ctx, int year, Map<String, UUID> categories, Map<String, UUID> locations) {
        Map<String, UUID> employees = new HashMap<>();
        for (Object[] e : db.list("select concat(e.firstName, ' ', e.lastName), e.id from Employee e order by e.employeeNo", Object[].class)) {
            employees.putIfAbsent((String) e[0], (UUID) e[1]);
        }
        Map<String, UUID> departments = new HashMap<>();
        for (Object[] d : db.list("select d.code, d.id from Department d", Object[].class)) departments.put((String) d[0], (UUID) d[1]);
        List<Sample> samples = samples(year);
        for (int index = 0; index < samples.size(); index++) {
            Sample s = samples.get(index);
            String kind = s.holder().substring(0, s.holder().indexOf(':'));
            String key = s.holder().substring(s.holder().indexOf(':') + 1);
            LocalDate bought = LocalDate.parse(s.purchaseDate());
            Asset a = asset(s, categories, String.format("FTR-%d-%05d", bought.getYear(), index + 101));
            a.status = s.retired() != null ? AssetStatus.RETIRED : s.maintenance() != null ? AssetStatus.IN_MAINTENANCE
                    : kind.equals("stock") ? AssetStatus.IN_STOCK : AssetStatus.ASSIGNED;
            if (s.retired() != null) a.condition = AssetCondition.DAMAGED;
            a.locationId = kind.equals("stock") ? locations.get(key) : null;
            a.retiredAt = s.retired() != null ? Instant.now() : null;
            a.retireReason = s.retired();
            db.add(a);
            db.flush();

            if (s.previous() != null) {
                Previous p = s.previous();
                AssetAssignment x = assignment(a.id, ctx.adminId, LocalDate.parse(p.from()));
                x.assigneeType = AssigneeType.EMPLOYEE;
                x.employeeId = employees.get(p.employee());
                x.status = AssignmentStatus.RETURNED;
                x.acknowledgedAt = LocalDateTime.parse(p.from() + "T00:00:00").toInstant(ZoneOffset.UTC);
                x.returnedAt = LocalDate.parse(p.to());
                x.conditionIn = AssetCondition.GOOD;
                x.returnNotes = "Cihaz yenilendi, eski cihaz depoya alındı";
                x.returnedById = ctx.adminId;
                db.add(x);
            }
            if (!kind.equals("stock")) {
                LocalDate since = LocalDate.parse(s.since());
                AssetAssignment x = assignment(a.id, ctx.adminId, since);
                x.assigneeType = switch (kind) {
                    case "employee" -> AssigneeType.EMPLOYEE;
                    case "department" -> AssigneeType.DEPARTMENT;
                    default -> AssigneeType.LOCATION;
                };
                x.employeeId = kind.equals("employee") ? employees.get(key) : null;
                x.departmentId = kind.equals("department") ? departments.get(key) : null;
                x.locationId = kind.equals("location") ? locations.get(key) : null;
                x.acknowledgedAt = s.acknowledged() ? at9(since) : null;
                db.add(x);
            }
            if (s.maintenance() != null) {
                AssetMaintenance m = new AssetMaintenance();
                m.assetId = a.id;
                m.type = MaintenanceType.REPAIR;
                m.title = s.maintenance().title();
                m.vendor = s.maintenance().vendor();
                m.cost = BigDecimal.valueOf(s.maintenance().cost());
                m.startedAt = LocalDate.parse(s.maintenance().startedAt());
                m.createdById = ctx.adminId;
                db.add(m);
            }
            if (s.category().equals("ARC")) {
                AssetMaintenance m = new AssetMaintenance();
                m.assetId = a.id;
                m.type = MaintenanceType.SERVICE;
                m.status = MaintenanceStatus.COMPLETED;
                m.title = "Periyodik bakım (60.000 km)";
                m.vendor = "Ford Yetkili Servis";
                m.cost = BigDecimal.valueOf(9800);
                m.startedAt = LocalDate.of(year, 4, 14);
                m.completedAt = LocalDate.of(year, 4, 15);
                m.createdById = ctx.adminId;
                db.add(m);
            }
            db.flush();
        }
    }

    /** Links the admin account to the general manager so its self-service pages (my assets, my leave) are not empty. */
    private void seedProfile(Context ctx, int year, Map<String, UUID> categories) {
        Employee profile = db.first("select e from Employee e where e.userId = ?1", Employee.class, ctx.adminId)
                .or(() -> db.first("select e from Employee e where e.firstName = 'Hakan' and e.lastName = 'Erdem' and e.userId is null", Employee.class))
                .orElse(null);
        if (profile == null) return;
        profile.userId = ctx.adminId;
        db.flush();
        if (db.exists("select a.id from AssetAssignment a where a.employeeId = ?1", profile.id)) return;
        for (Sample s : profileAssets(year)) {
            Asset a = asset(s, categories, "FTR-" + LocalDate.parse(s.purchaseDate()).getYear() + "-" + s.serial().substring(s.serial().length() - 5));
            a.status = AssetStatus.ASSIGNED;
            db.add(a);
            db.flush();
            LocalDate since = LocalDate.parse(s.since());
            AssetAssignment x = assignment(a.id, ctx.adminId, since);
            x.assigneeType = AssigneeType.EMPLOYEE;
            x.employeeId = profile.id;
            x.acknowledgedAt = s.acknowledged() ? at9(since) : null;
            db.add(x);
            db.flush();
        }
    }

    /** A completed company-wide count (one asset missing) and an open computer count that is half scanned. */
    private void seedCounts(Context ctx, int year) {
        if (db.exists("select c.id from AssetCount c")) return;
        List<Asset> inService = db.list("select a from Asset a where a.status not in ?1 order by a.assetTag", Asset.class, AssetService.OUT_OF_SERVICE);
        if (inService.isEmpty()) return;
        var holders = assets.holders(inService.stream().map(a -> a.id).toList());
        var locationNames = assets.names("AssetLocation", inService.stream().map(a -> a.locationId).filter(x -> x != null).toList());
        java.util.function.Function<Asset, String> holderOf = a -> holders.containsKey(a.id) ? holders.get(a.id).name() : locationNames.get(a.locationId);

        Instant completedAt = LocalDateTime.of(year, 6, 30, 16, 0).toInstant(ZoneOffset.UTC);
        AssetCount done = new AssetCount();
        done.number = numbering.next("ASSET_COUNT");
        done.status = AssetCountStatus.COMPLETED;
        done.countDate = LocalDate.of(year, 6, 30);
        done.notes = "Yıl ortası demirbaş sayımı";
        done.createdById = ctx.adminId;
        done.completedAt = completedAt;
        db.add(done);
        for (int i = 0; i < inService.size(); i++) {
            boolean missing = i == inService.size() - 1;
            AssetCountLine l = new AssetCountLine();
            l.countId = done.id;
            l.assetId = inService.get(i).id;
            l.expectedHolder = holderOf.apply(inService.get(i));
            l.foundAt = missing ? null : completedAt;
            l.foundById = missing ? null : ctx.adminId;
            l.note = missing ? "Konumunda bulunamadı, sorumluya soruldu" : null;
            db.add(l);
        }

        UUID category = inService.get(0).categoryId;
        List<Asset> scope = inService.stream().filter(a -> a.categoryId.equals(category)).toList();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AssetCount open = new AssetCount();
        open.number = numbering.next("ASSET_COUNT");
        open.countDate = today.withDayOfMonth(1);
        open.categoryId = category;
        open.notes = "Bilgisayar envanteri kontrolü";
        open.createdById = ctx.adminId;
        db.add(open);
        for (int i = 0; i < scope.size(); i++) {
            boolean found = i < scope.size() / 2.0;
            AssetCountLine l = new AssetCountLine();
            l.countId = open.id;
            l.assetId = scope.get(i).id;
            l.expectedHolder = holderOf.apply(scope.get(i));
            l.foundAt = found ? Instant.now() : null;
            l.foundById = found ? ctx.adminId : null;
            db.add(l);
        }
        db.flush();
    }
}
