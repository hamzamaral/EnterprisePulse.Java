package dev.enterprisepulse.assets;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.AppProperties;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Asset;
import dev.enterprisepulse.data.generated.AssetMaintenance;
import dev.enterprisepulse.data.generated.AssetStatus;
import dev.enterprisepulse.data.generated.AssetCondition;
import dev.enterprisepulse.data.generated.AssigneeType;
import dev.enterprisepulse.data.generated.AssignmentStatus;
import dev.enterprisepulse.data.generated.MaintenanceStatus;
import dev.enterprisepulse.data.generated.MaintenanceType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Asset card (demirbaş kartı): detail with depreciation, QR label, retirement and maintenance. */
@Service
@Transactional
public class AssetService {
    public static final Set<AssetStatus> OUT_OF_SERVICE = Set.of(AssetStatus.RETIRED, AssetStatus.LOST);

    public record HolderDto(AssigneeType type, UUID assignmentId, UUID id, String name) {}

    public record AssetDetailDto(UUID id, String assetTag, String name, UUID categoryId, String categoryName, String brand, String model, String serialNumber,
                                 AssetStatus status, AssetCondition condition, UUID locationId, String locationName, HolderDto holder,
                                 LocalDate purchaseDate, BigDecimal purchaseCost, String currency, BigDecimal salvageValue, Integer usefulLifeMonths,
                                 String supplier, String invoiceNo, LocalDate warrantyEndDate, String warrantyState, String notes, Instant retiredAt,
                                 String retireReason, Depreciation.Result depreciation) {}

    public record RetireRequest(AssetStatus status, String reason) {}

    public record MaintenanceDto(UUID id, MaintenanceType type, MaintenanceStatus status, String title, String description, String vendor, BigDecimal cost,
                                 LocalDate startedAt, LocalDate completedAt) {}

    public record CreateMaintenanceRequest(MaintenanceType type, String title, String description, String vendor, BigDecimal cost, LocalDate startedAt) {}

    public record CompleteMaintenanceRequest(LocalDate completedAt, BigDecimal cost, String description) {}

    public record Label(byte[] png, String fileName) {}

    private final Db db;
    private final AppProperties props;

    public AssetService(Db db, AppProperties props) {
        this.db = db;
        this.props = props;
    }

    public static LocalDate today() {
        return LocalDate.ofInstant(Instant.now().plusSeconds(3 * 3600), ZoneOffset.UTC);
    }

    public static String clean(String value) {
        return Guard.blank(value) ? null : value.trim();
    }

    public Asset get(UUID id) {
        return Guard.found(db.first("select a from Asset a where a.id = ?1", Asset.class, id), "Demirbaş bulunamadı");
    }

    @Transactional(readOnly = true)
    public AssetDetailDto detail(UUID id) {
        Asset a = get(id);
        Object[] category = db.one("select c.name, c.usefulLifeMonths from AssetCategory c where c.id = ?1", Object[].class, a.categoryId);
        String location = a.locationId == null ? null : db.first("select l.name from AssetLocation l where l.id = ?1", String.class, a.locationId).orElse(null);
        int life = a.usefulLifeMonths != null ? a.usefulLifeMonths : (Integer) category[1];
        Depreciation.Result depreciation = a.purchaseDate != null && a.purchaseCost != null
                ? Depreciation.calculate(a.purchaseCost, a.salvageValue, life, a.purchaseDate, today())
                : null;
        return new AssetDetailDto(a.id, a.assetTag, a.name, a.categoryId, (String) category[0], a.brand, a.model, a.serialNumber, a.status, a.condition,
                a.locationId, location, holders(List.of(a.id)).get(a.id), a.purchaseDate, a.purchaseCost, a.currency, a.salvageValue, a.usefulLifeMonths,
                a.supplier, a.invoiceNo, a.warrantyEndDate, warrantyState(a.warrantyEndDate), a.notes, a.retiredAt, a.retireReason, depreciation);
    }

    /** expired, expiring (60 days or less) or active. */
    public static String warrantyState(LocalDate end) {
        if (end == null) return null;
        long days = ChronoUnit.DAYS.between(today(), end);
        return days < 0 ? "expired" : days <= 60 ? "expiring" : "active";
    }

    /** Display names of employees, users, departments, positions or asset locations by id. */
    public Map<UUID, String> names(String entity, Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        String label = entity.equals("Employee") || entity.equals("User") ? "concat(x.firstName, ' ', x.lastName)" : "x.name";
        for (Object[] r : db.list("select x.id, " + label + " from " + entity + " x where x.id in ?1", Object[].class, ids)) out.put((UUID) r[0], (String) r[1]);
        return out;
    }

    /** Current holder of each asset: the active assignment's employee, department or location. */
    public Map<UUID, HolderDto> holders(Collection<UUID> assetIds) {
        Map<UUID, HolderDto> out = new HashMap<>();
        if (assetIds.isEmpty()) return out;
        List<Object[]> rows = db.list("select a.id, a.assetId, a.assigneeType, a.employeeId, a.departmentId, a.locationId from AssetAssignment a "
                + "where a.assetId in ?1 and a.status = ?2", Object[].class, assetIds, AssignmentStatus.ACTIVE);
        Map<UUID, String> employees = names("Employee", rows.stream().map(r -> (UUID) r[3]).filter(x -> x != null).toList());
        Map<UUID, String> departments = names("Department", rows.stream().map(r -> (UUID) r[4]).filter(x -> x != null).toList());
        Map<UUID, String> locations = names("AssetLocation", rows.stream().map(r -> (UUID) r[5]).filter(x -> x != null).toList());
        for (Object[] r : rows) {
            AssigneeType type = (AssigneeType) r[2];
            UUID id = (UUID) (type == AssigneeType.EMPLOYEE ? r[3] : type == AssigneeType.DEPARTMENT ? r[4] : r[5]);
            String name = (type == AssigneeType.EMPLOYEE ? employees : type == AssigneeType.DEPARTMENT ? departments : locations).getOrDefault(id, "");
            out.putIfAbsent((UUID) r[1], new HolderDto(type, (UUID) r[0], id, name));
        }
        return out;
    }

    public AssetDetailDto retire(UUID id, RetireRequest body) {
        Asset a = get(id);
        Guard.that(body.status() != null && OUT_OF_SERVICE.contains(body.status()), "Durum Hizmet dışı veya Kayıp olmalı");
        Guard.that(body.reason() != null && body.reason().trim().length() >= 2, "Neden zorunludur");
        Guard.state(a.status != AssetStatus.ASSIGNED, "Hizmet dışı bırakmadan önce demirbaşı iade alın");
        Guard.state(!OUT_OF_SERVICE.contains(a.status), "Demirbaş zaten hizmet dışı");
        a.status = body.status();
        a.retiredAt = Instant.now();
        a.retireReason = body.reason().trim();
        db.flush();
        return detail(id);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceDto> maintenances(UUID assetId) {
        get(assetId);
        return db.list("select m from AssetMaintenance m where m.assetId = ?1 order by m.startedAt desc, m.createdAt desc", AssetMaintenance.class, assetId)
                .stream().map(AssetService::toDto).toList();
    }

    /** Opening a maintenance record takes an in-stock asset out of circulation until it is completed. */
    public MaintenanceDto createMaintenance(UUID assetId, CreateMaintenanceRequest body) {
        Asset a = get(assetId);
        Guard.state(!OUT_OF_SERVICE.contains(a.status), "Demirbaş hizmet dışı");
        Guard.that(body.type() != null, "Bakım türü zorunludur");
        Guard.that(!Guard.blank(body.title()), "Başlık zorunludur");
        Guard.that(body.startedAt() != null, "Başlangıç tarihi zorunludur");
        Guard.that(body.cost() == null || body.cost().signum() >= 0, "Tutar negatif olamaz");
        AssetMaintenance m = new AssetMaintenance();
        m.assetId = assetId;
        m.type = body.type();
        m.title = body.title().trim();
        m.description = clean(body.description());
        m.vendor = clean(body.vendor());
        m.cost = body.cost();
        m.startedAt = body.startedAt();
        m.createdById = CurrentUser.get().requireUserId();
        db.add(m);
        if (a.status == AssetStatus.IN_STOCK) a.status = AssetStatus.IN_MAINTENANCE;
        db.flush();
        return toDto(m);
    }

    public MaintenanceDto completeMaintenance(UUID assetId, UUID maintenanceId, CompleteMaintenanceRequest body) {
        Asset a = get(assetId);
        AssetMaintenance m = Guard.found(db.first("select m from AssetMaintenance m where m.id = ?1 and m.assetId = ?2", AssetMaintenance.class, maintenanceId, assetId),
                "Bakım kaydı bulunamadı");
        Guard.state(m.status != MaintenanceStatus.COMPLETED, "Bakım zaten tamamlanmış");
        Guard.that(body.completedAt() != null, "Tamamlanma tarihi zorunludur");
        Guard.that(!body.completedAt().isBefore(m.startedAt), "Tamamlanma tarihi başlangıçtan önce olamaz");
        Guard.that(body.cost() == null || body.cost().signum() >= 0, "Tutar negatif olamaz");
        m.status = MaintenanceStatus.COMPLETED;
        m.completedAt = body.completedAt();
        if (body.cost() != null) m.cost = body.cost();
        if (clean(body.description()) != null) m.description = clean(body.description());
        db.flush();
        boolean stillOpen = db.exists("select x.id from AssetMaintenance x where x.assetId = ?1 and x.status = ?2", assetId, MaintenanceStatus.OPEN);
        if (!stillOpen && a.status == AssetStatus.IN_MAINTENANCE) {
            a.status = AssetStatus.IN_STOCK;
            db.flush();
        }
        return toDto(m);
    }

    /** PNG QR label that opens the asset card; the count screen also accepts the scanned URL. */
    @Transactional(readOnly = true)
    public Label label(UUID id) {
        Asset a = get(id);
        String url = props.webAppUrl.replaceAll("/+$", "") + "/r/assets/" + a.id;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 330, 330,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return new Label(out.toByteArray(), a.assetTag + ".png");
        } catch (WriterException | IOException ex) {
            throw new AppException(500, "QR etiketi oluşturulamadı");
        }
    }

    private static MaintenanceDto toDto(AssetMaintenance m) {
        return new MaintenanceDto(m.id, m.type, m.status, m.title, m.description, m.vendor, m.cost, m.startedAt, m.completedAt);
    }
}
