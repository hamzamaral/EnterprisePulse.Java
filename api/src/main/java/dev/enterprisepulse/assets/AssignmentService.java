package dev.enterprisepulse.assets;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Asset;
import dev.enterprisepulse.data.generated.AssetAssignment;
import dev.enterprisepulse.data.generated.AssetCondition;
import dev.enterprisepulse.data.generated.AssigneeType;
import dev.enterprisepulse.data.generated.AssignmentStatus;
import dev.enterprisepulse.data.generated.AssetStatus;
import dev.enterprisepulse.data.generated.Company;
import dev.enterprisepulse.data.generated.Employee;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NumberingService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Zimmet: hand-over, return and transfer (devir) of assets, the holder's acknowledgement and "my assets". */
@Service
@Transactional
public class AssignmentService {
    public record AssignRequest(AssigneeType assigneeType, UUID employeeId, UUID departmentId, UUID locationId, LocalDate assignedAt,
                                LocalDate expectedReturnDate, String notes) {}

    public record ReturnRequest(LocalDate returnedAt, AssetCondition conditionIn, UUID locationId, String returnNotes) {}

    public record AssignmentDto(UUID id, String number, UUID assetId, String assetTag, String assetName, AssigneeType assigneeType, UUID assigneeId,
                                String assigneeName, AssignmentStatus status, LocalDate assignedAt, LocalDate expectedReturnDate, AssetCondition conditionOut,
                                String notes, Instant acknowledgedAt, LocalDate returnedAt, AssetCondition conditionIn, String returnNotes,
                                String assignedByName, String returnedByName) {}

    public record DocumentAsset(String assetTag, String name, String categoryName, String brand, String model, String serialNumber) {}

    public record DocumentAssignee(String name, String employeeNo, String departmentName, String positionName) {}

    /** Data of the printable hand-over form (zimmet tutanağı). */
    public record AssignmentDocumentDto(AssignmentDto assignment, String companyName, String companyAddress, DocumentAsset asset, DocumentAssignee assignee,
                                        boolean canAcknowledge) {}

    private record Assignee(UUID id, String name) {}

    private final Db db;
    private final NumberingService numbering;
    private final NotificationService notifications;
    private final AssetService assets;

    public AssignmentService(Db db, NumberingService numbering, NotificationService notifications, AssetService assets) {
        this.db = db;
        this.numbering = numbering;
        this.notifications = notifications;
        this.assets = assets;
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> mine() {
        UUID userId = CurrentUser.get().requireUserId();
        List<AssetAssignment> rows = db.list("select a from AssetAssignment a where a.employeeId in (select e.id from Employee e where e.userId = ?1)",
                AssetAssignment.class, userId);
        return toDtos(rows).stream()
                .sorted(Comparator.comparing(AssignmentDto::status).thenComparing(AssignmentDto::assignedAt, Comparator.reverseOrder())).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> forAsset(UUID assetId) {
        return toDtos(db.list("select a from AssetAssignment a where a.assetId = ?1 order by a.assignedAt desc, a.createdAt desc", AssetAssignment.class, assetId));
    }

    /** Visible to the holder or with assets:read. */
    @Transactional(readOnly = true)
    public AssignmentDocumentDto document(UUID id) {
        CurrentUser ctx = CurrentUser.get();
        AssetAssignment row = db.first("select a from AssetAssignment a where a.id = ?1", AssetAssignment.class, id).orElse(null);
        Employee employee = row == null || row.employeeId == null ? null : db.find(Employee.class, row.employeeId);
        boolean isHolder = employee != null && employee.userId != null && employee.userId.equals(ctx.userId);
        if (row == null || !(isHolder || ctx.has("assets:read"))) throw AppException.notFound("Zimmet kaydı bulunamadı");
        Company company = db.get(Company.class, row.companyId);
        String address = Stream.of(company.address, company.city).filter(s -> !Guard.blank(s)).collect(Collectors.joining(", "));
        Asset a = db.get(Asset.class, row.assetId);
        String category = db.one("select c.name from AssetCategory c where c.id = ?1", String.class, a.categoryId);
        AssignmentDto dto = toDtos(List.of(row)).get(0);
        UUID departmentId = employee != null ? employee.departmentId : row.departmentId;
        String department = departmentId == null ? null : assets.names("Department", List.of(departmentId)).get(departmentId);
        String position = employee == null || employee.positionId == null ? null : assets.names("Position", List.of(employee.positionId)).get(employee.positionId);
        return new AssignmentDocumentDto(dto, company.legalName != null ? company.legalName : company.name, address.isEmpty() ? null : address,
                new DocumentAsset(a.assetTag, a.name, category, a.brand, a.model, a.serialNumber),
                new DocumentAssignee(dto.assigneeName(), employee == null ? null : employee.employeeNo, department, position),
                isHolder && row.status == AssignmentStatus.ACTIVE && row.acknowledgedAt == null);
    }

    public AssignmentDocumentDto acknowledge(UUID id) {
        AssignmentDocumentDto doc = document(id);
        if (!doc.canAcknowledge()) throw AppException.forbidden("Yalnızca demirbaşı teslim alan personel bir kez onaylayabilir");
        db.get(AssetAssignment.class, id).acknowledgedAt = Instant.now();
        db.flush();
        return document(id);
    }

    public AssignmentDto assign(UUID assetId, AssignRequest body) {
        Asset asset = assets.get(assetId);
        Guard.state(asset.status == AssetStatus.IN_STOCK, "Yalnızca depodaki demirbaşlar zimmetlenebilir");
        Assignee assignee = resolve(body);
        AssetAssignment row = create(asset, body, assignee);
        db.flush();
        notifyHolder(row, asset);
        return toDtos(List.of(row)).get(0);
    }

    public AssignmentDto returnAsset(UUID assetId, ReturnRequest body) {
        Asset asset = assets.get(assetId);
        AssetAssignment active = active(assetId);
        Guard.that(body.returnedAt() != null && body.conditionIn() != null, "İade tarihi ve fiziksel durum zorunludur");
        Guard.that(!body.returnedAt().isBefore(active.assignedAt), "İade tarihi zimmet tarihinden önce olamaz");
        if (body.locationId() != null) Guard.that(db.exists("select l.id from AssetLocation l where l.id = ?1", body.locationId()), "Lokasyon bulunamadı");
        active.status = AssignmentStatus.RETURNED;
        active.returnedAt = body.returnedAt();
        active.conditionIn = body.conditionIn();
        active.returnNotes = AssetService.clean(body.returnNotes());
        active.returnedById = CurrentUser.get().requireUserId();
        asset.status = AssetStatus.IN_STOCK;
        asset.condition = body.conditionIn();
        if (body.locationId() != null) asset.locationId = body.locationId();
        db.flush();
        return toDtos(List.of(active)).get(0);
    }

    /** Closes the current assignment and opens a new one in a single transaction (devir). */
    public AssignmentDto transfer(UUID assetId, AssignRequest body) {
        Asset asset = assets.get(assetId);
        AssetAssignment active = active(assetId);
        Assignee assignee = resolve(body);
        Guard.that(!assignee.id().equals(holderId(active)), "Demirbaş zaten bu sorumluya zimmetli");
        Guard.that(!body.assignedAt().isBefore(active.assignedAt), "Devir tarihi mevcut zimmetten önce olamaz");
        active.status = AssignmentStatus.RETURNED;
        active.returnedAt = body.assignedAt();
        active.conditionIn = asset.condition;
        active.returnNotes = "Devir: " + assignee.name();
        active.returnedById = CurrentUser.get().requireUserId();
        db.flush();
        AssetAssignment row = create(asset, body, assignee);
        db.flush();
        notifyHolder(row, asset);
        return toDtos(List.of(row)).get(0);
    }

    private AssetAssignment create(Asset asset, AssignRequest body, Assignee assignee) {
        if (body.expectedReturnDate() != null) {
            Guard.that(!body.expectedReturnDate().isBefore(body.assignedAt()), "Beklenen iade tarihi zimmet tarihinden önce olamaz");
        }
        AssetAssignment row = new AssetAssignment();
        row.number = numbering.next("ASSET_ASSIGNMENT");
        row.assetId = asset.id;
        row.assigneeType = body.assigneeType();
        row.employeeId = body.assigneeType() == AssigneeType.EMPLOYEE ? assignee.id() : null;
        row.departmentId = body.assigneeType() == AssigneeType.DEPARTMENT ? assignee.id() : null;
        row.locationId = body.assigneeType() == AssigneeType.LOCATION ? assignee.id() : null;
        row.assignedAt = body.assignedAt();
        row.expectedReturnDate = body.expectedReturnDate();
        row.conditionOut = asset.condition;
        row.notes = AssetService.clean(body.notes());
        row.assignedById = CurrentUser.get().requireUserId();
        db.add(row);
        asset.status = AssetStatus.ASSIGNED;
        return row;
    }

    private AssetAssignment active(UUID assetId) {
        return db.first("select a from AssetAssignment a where a.assetId = ?1 and a.status = ?2", AssetAssignment.class, assetId, AssignmentStatus.ACTIVE)
                .orElseThrow(() -> AppException.conflict("Demirbaş zimmetli değil"));
    }

    private static UUID holderId(AssetAssignment a) {
        return a.assigneeType == AssigneeType.EMPLOYEE ? a.employeeId : a.assigneeType == AssigneeType.DEPARTMENT ? a.departmentId : a.locationId;
    }

    private Assignee resolve(AssignRequest body) {
        Guard.that(body.assigneeType() != null, "Zimmet türü zorunludur");
        Guard.that(body.assignedAt() != null, "Zimmet tarihi zorunludur");
        switch (body.assigneeType()) {
            case EMPLOYEE -> {
                Employee e = body.employeeId() == null ? null : db.first("select e from Employee e where e.id = ?1", Employee.class, body.employeeId()).orElse(null);
                if (e == null) throw AppException.notFound("Personel bulunamadı");
                Guard.that(e.status == EmployeeStatus.ACTIVE, "Demirbaş yalnızca aktif personele zimmetlenebilir");
                return new Assignee(e.id, e.firstName + " " + e.lastName);
            }
            case DEPARTMENT -> {
                String name = body.departmentId() == null ? null : assets.names("Department", List.of(body.departmentId())).get(body.departmentId());
                if (name == null) throw AppException.notFound("Departman bulunamadı");
                return new Assignee(body.departmentId(), name);
            }
            default -> {
                String name = body.locationId() == null ? null : assets.names("AssetLocation", List.of(body.locationId())).get(body.locationId());
                if (name == null) throw AppException.notFound("Lokasyon bulunamadı");
                return new Assignee(body.locationId(), name);
            }
        }
    }

    private void notifyHolder(AssetAssignment row, Asset asset) {
        if (row.employeeId == null) return;
        UUID userId = db.first("select e.userId from Employee e where e.id = ?1", UUID.class, row.employeeId).orElse(null);
        if (userId == null) return;
        notifications.notify(new NotificationService.NotifyRequest(List.of(userId), row.companyId, "assets.assigned", "Adınıza zimmet kaydı oluşturuldu",
                asset.assetTag + " · " + asset.name + " — teslim alındığını onaylayın.", "/assets/assignments/" + row.id));
    }

    private List<AssignmentDto> toDtos(List<AssetAssignment> rows) {
        Set<UUID> assetIds = new HashSet<>(), employeeIds = new HashSet<>(), departmentIds = new HashSet<>(), locationIds = new HashSet<>(), userIds = new HashSet<>();
        for (AssetAssignment r : rows) {
            assetIds.add(r.assetId);
            if (r.employeeId != null) employeeIds.add(r.employeeId);
            if (r.departmentId != null) departmentIds.add(r.departmentId);
            if (r.locationId != null) locationIds.add(r.locationId);
            userIds.add(r.assignedById);
            if (r.returnedById != null) userIds.add(r.returnedById);
        }
        Map<UUID, Object[]> assetRows = new java.util.HashMap<>();
        if (!assetIds.isEmpty()) {
            for (Object[] a : db.list("select a.id, a.assetTag, a.name from Asset a where a.id in ?1", Object[].class, assetIds)) assetRows.put((UUID) a[0], a);
        }
        Map<UUID, String> employees = assets.names("Employee", employeeIds), departments = assets.names("Department", departmentIds),
                locations = assets.names("AssetLocation", locationIds), users = assets.names("User", userIds);
        return rows.stream().map(r -> {
            Object[] a = assetRows.getOrDefault(r.assetId, new Object[] {r.assetId, "", ""});
            UUID holder = holderId(r);
            String name = (r.assigneeType == AssigneeType.EMPLOYEE ? employees : r.assigneeType == AssigneeType.DEPARTMENT ? departments : locations)
                    .getOrDefault(holder, "");
            return new AssignmentDto(r.id, r.number, r.assetId, (String) a[1], (String) a[2], r.assigneeType, holder, name, r.status, r.assignedAt,
                    r.expectedReturnDate, r.conditionOut, r.notes, r.acknowledgedAt, r.returnedAt, r.conditionIn, r.returnNotes,
                    users.getOrDefault(r.assignedById, ""), r.returnedById == null ? null : users.get(r.returnedById));
        }).filter(Objects::nonNull).toList();
    }
}
