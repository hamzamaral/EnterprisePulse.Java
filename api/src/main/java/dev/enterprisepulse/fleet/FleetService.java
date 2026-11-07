package dev.enterprisepulse.fleet;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.EmployeeStatus;
import dev.enterprisepulse.data.generated.Vehicle;
import dev.enterprisepulse.data.generated.VehicleAssignment;
import dev.enterprisepulse.data.generated.VehicleDocument;
import dev.enterprisepulse.data.generated.VehicleDocumentType;
import dev.enterprisepulse.data.generated.VehicleFuelEntry;
import dev.enterprisepulse.data.generated.VehicleFuelType;
import dev.enterprisepulse.data.generated.VehicleOdometerReading;
import dev.enterprisepulse.data.generated.VehicleOdometerSource;
import dev.enterprisepulse.data.generated.VehicleOwnership;
import dev.enterprisepulse.data.generated.VehicleService;
import dev.enterprisepulse.data.generated.VehicleServiceType;
import dev.enterprisepulse.data.generated.VehicleStatus;
import dev.enterprisepulse.platform.Extensibility.ScheduledJob;
import dev.enterprisepulse.platform.NotificationService;
import dev.enterprisepulse.platform.NotificationService.NotifyRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Filo. Plaka TR formatında; yakıt, bakım ve belge hatırlatmaları günlük işte gider. */
@Service
@Transactional
public class FleetService implements ScheduledJob {
    public record SaveVehicle(String plate, String brand, String model, Integer modelYear, String color, VehicleFuelType fuelType, VehicleOwnership ownership,
                              LocalDate contractEndsOn, UUID departmentId, Integer serviceIntervalKm, Integer serviceIntervalMonths, String notes) {}
    public record AssignVehicle(UUID employeeId, LocalDate startDate, int startOdometer, String note) {}
    public record ReturnVehicle(LocalDate endDate, int endOdometer, String note) {}
    public record SaveFuel(LocalDate entryDate, int odometer, BigDecimal liters, BigDecimal amount, boolean fullTank, String station, String note) {}
    public record SaveService(VehicleServiceType type, LocalDate serviceDate, Integer odometer, UUID partnerId, String description, BigDecimal cost) {}
    public record SaveDocument(VehicleDocumentType type, LocalDate startDate, LocalDate endDate, String provider, String policyNumber, BigDecimal cost, String note) {}
    public record VehicleDto(UUID id, String plate, String brand, String model, VehicleStatus status, int odometer, UUID driverId, String driverName,
                             LocalDate nextServiceDate, Integer nextServiceKm, String serviceState, int openDocumentWarnings) {}
    public record VehicleList(List<VehicleDto> items) {}

    private static final Pattern PLATE = Pattern.compile("^(0[1-9]|[1-7]\\d|8[01])([A-Z]{1,3})(\\d{2,4})$");
    public static final int WARN_DAYS = 30;
    public static final int WARN_KM = 1000;
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final Db db;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public FleetService(Db db, NotificationService notifications, TransactionTemplate tx) {
        this.db = db;
        this.notifications = notifications;
        this.tx = tx;
    }

    @Override public String id() { return "fleet-daily"; }

    @Override public String cron() { return "0 0 8 * * *"; }

    private static CurrentUser ctx() { return CurrentUser.get(); }

    private static LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run() {
        CurrentUser ctx = ctx();
        List<UUID> companies;
        try (var bypass = ctx.withoutTenant()) {
            companies = tx.execute(s -> db.list("select c.id from Company c", UUID.class));
        }
        for (UUID companyId : companies) {
            try (var scope = ctx.actAs(companyId, null, true)) {
                tx.executeWithoutResult(s -> remind());
            }
        }
    }

    public VehicleDto create(SaveVehicle dto) {
        Guard.that(ctx().has("fleet:update"), "Araç kaydı yetkiniz yok");
        String plate = normalize(dto.plate());
        Guard.that(plate != null, "Plaka geçersiz");
        Guard.that(!db.exists("select v.id from Vehicle v where v.plate = ?1", plate), "Plaka zaten kayıtlı");
        Guard.that(!Guard.blank(dto.brand()) && !Guard.blank(dto.model()), "Marka ve model gerekli");
        Guard.that(dto.fuelType() != null, "Yakıt türü gerekli");
        Vehicle vehicle = new Vehicle();
        vehicle.plate = plate;
        vehicle.brand = dto.brand().trim();
        vehicle.model = dto.model().trim();
        vehicle.modelYear = dto.modelYear();
        vehicle.color = blank(dto.color());
        vehicle.fuelType = dto.fuelType();
        vehicle.ownership = dto.ownership() == null ? VehicleOwnership.OWNED : dto.ownership();
        vehicle.contractEndsOn = dto.contractEndsOn();
        vehicle.departmentId = dto.departmentId();
        vehicle.serviceIntervalKm = dto.serviceIntervalKm();
        vehicle.serviceIntervalMonths = dto.serviceIntervalMonths();
        vehicle.notes = blank(dto.notes());
        vehicle.createdById = ctx().requireUserId();
        db.add(vehicle);
        db.flush();
        return get(vehicle.id);
    }

    public VehicleDto assign(UUID id, AssignVehicle dto) {
        Guard.that(ctx().has("fleet:update"), "Zimmet yetkiniz yok");
        Vehicle vehicle = active(id);
        Guard.that(!db.exists("select a.id from VehicleAssignment a where a.vehicleId = ?1 and a.endDate is null", id), "Araç zaten zimmetli");
        Guard.that(db.exists("select e.id from Employee e where e.id = ?1 and e.status = ?2", dto.employeeId(), EmployeeStatus.ACTIVE), "Personel bulunamadı");
        Guard.that(dto.startDate() != null, "Başlangıç tarihi gerekli");
        Guard.that(dto.startOdometer() >= vehicle.odometer, "Kilometre mevcut okumadan küçük olamaz");
        VehicleAssignment row = new VehicleAssignment();
        row.vehicleId = id;
        row.employeeId = dto.employeeId();
        row.startDate = dto.startDate();
        row.startOdometer = dto.startOdometer();
        row.note = blank(dto.note());
        row.createdById = ctx().requireUserId();
        db.add(row);
        recordOdometer(vehicle, dto.startDate(), dto.startOdometer(), VehicleOdometerSource.ASSIGNMENT, null, null);
        db.flush();
        return get(id);
    }

    public VehicleDto returnVehicle(UUID id, ReturnVehicle dto) {
        Guard.that(ctx().has("fleet:update"), "İade yetkiniz yok");
        Vehicle vehicle = active(id);
        VehicleAssignment assignment = db.first("select a from VehicleAssignment a where a.vehicleId = ?1 and a.endDate is null", VehicleAssignment.class, id)
                .orElseThrow(() -> AppException.conflict("Açık zimmet yok"));
        Guard.that(dto.endDate() != null && !dto.endDate().isBefore(assignment.startDate), "İade tarihi zimmetten önce olamaz");
        Guard.that(dto.endOdometer() >= assignment.startOdometer, "İade kilometresı başlangıçtan küçük olamaz");
        assignment.endDate = dto.endDate();
        assignment.endOdometer = dto.endOdometer();
        if (dto.note() != null) assignment.note = blank(dto.note());
        recordOdometer(vehicle, dto.endDate(), dto.endOdometer(), VehicleOdometerSource.ASSIGNMENT, null, null);
        db.flush();
        return get(id);
    }

    public VehicleDto addFuel(UUID id, SaveFuel dto) {
        Guard.that(ctx().has("fleet:update") || isDriver(id), "Yakıt girişi yetkiniz yok");
        Vehicle vehicle = active(id);
        Guard.that(dto.entryDate() != null, "Tarih gerekli");
        Guard.that(dto.liters() != null && dto.liters().signum() > 0 && dto.amount() != null && dto.amount().signum() >= 0, "Litre ve tutar geçersiz");
        assertOdometer(vehicle.id, dto.entryDate(), dto.odometer());
        UUID driver = db.first("select a.employeeId from VehicleAssignment a where a.vehicleId = ?1 and a.startDate <= ?2 and (a.endDate is null or a.endDate >= ?2)",
                UUID.class, id, dto.entryDate()).orElse(null);
        VehicleFuelEntry entry = new VehicleFuelEntry();
        entry.vehicleId = id;
        entry.entryDate = dto.entryDate();
        entry.odometer = dto.odometer();
        entry.liters = dto.liters();
        entry.amount = dto.amount();
        entry.fullTank = dto.fullTank();
        entry.station = blank(dto.station());
        entry.employeeId = driver;
        entry.note = blank(dto.note());
        entry.createdById = ctx().requireUserId();
        db.add(entry);
        db.flush();
        recordOdometer(vehicle, dto.entryDate(), dto.odometer(), VehicleOdometerSource.FUEL, entry.id, null);
        recalcFuel(id);
        db.flush();
        return get(id);
    }

    public VehicleDto addService(UUID id, SaveService dto) {
        Guard.that(ctx().has("fleet:update"), "Bakım kaydı yetkiniz yok");
        Vehicle vehicle = active(id);
        Guard.that(!Guard.blank(dto.description()), "Açıklama gerekli");
        Guard.that(dto.type() != null && dto.serviceDate() != null, "Tür ve tarih gerekli");
        if (dto.odometer() != null) assertOdometer(vehicle.id, dto.serviceDate(), dto.odometer());
        VehicleService service = new VehicleService();
        service.vehicleId = id;
        service.type = dto.type();
        service.serviceDate = dto.serviceDate();
        service.odometer = dto.odometer();
        service.partnerId = dto.partnerId();
        service.description = dto.description().trim();
        service.cost = dto.cost() == null ? BigDecimal.ZERO : dto.cost();
        service.createdById = ctx().requireUserId();
        db.add(service);
        db.flush();
        if (dto.odometer() != null) recordOdometer(vehicle, dto.serviceDate(), dto.odometer(), VehicleOdometerSource.SERVICE, null, service.id);
        if (dto.type() == VehicleServiceType.PERIODIC) {
            if (vehicle.serviceIntervalKm != null && vehicle.serviceIntervalKm > 0 && dto.odometer() != null) vehicle.nextServiceKm = dto.odometer() + vehicle.serviceIntervalKm;
            if (vehicle.serviceIntervalMonths != null && vehicle.serviceIntervalMonths > 0) vehicle.nextServiceDate = dto.serviceDate().plusMonths(vehicle.serviceIntervalMonths);
            vehicle.serviceRemindedFor = null;
        }
        db.flush();
        return get(id);
    }

    public VehicleDto addDocument(UUID id, SaveDocument dto) {
        Guard.that(ctx().has("fleet:update"), "Belge kaydı yetkiniz yok");
        active(id);
        Guard.that(dto.type() != null && dto.endDate() != null, "Tür ve bitiş tarihi gerekli");
        Guard.that(!dto.endDate().isBefore(dto.startDate() != null ? dto.startDate() : dto.endDate()), "Bitiş başlangıçtan önce olamaz");
        VehicleDocument doc = new VehicleDocument();
        doc.vehicleId = id;
        doc.type = dto.type();
        doc.startDate = dto.startDate();
        doc.endDate = dto.endDate();
        doc.provider = blank(dto.provider());
        doc.policyNumber = blank(dto.policyNumber());
        doc.cost = dto.cost() == null ? BigDecimal.ZERO : dto.cost();
        doc.note = blank(dto.note());
        doc.createdById = ctx().requireUserId();
        db.add(doc);
        db.flush();
        return get(id);
    }

    @Transactional(readOnly = true)
    public VehicleList mine() {
        UUID employeeId = employeeIdOfUser();
        if (employeeId == null) return new VehicleList(List.of());
        List<UUID> ids = db.list("select a.vehicleId from VehicleAssignment a where a.employeeId = ?1 and a.endDate is null", UUID.class, employeeId);
        List<VehicleDto> list = new ArrayList<>();
        for (UUID id : ids) list.add(get(id));
        return new VehicleList(list);
    }

    @Transactional(readOnly = true)
    public VehicleDto get(UUID id) {
        Guard.that(ctx().has("fleet:read") || isDriver(id), "Araç görme yetkiniz yok");
        Vehicle vehicle = db.first("select v from Vehicle v where v.id = ?1", Vehicle.class, id).orElseThrow(() -> AppException.notFound("Araç bulunamadı"));
        VehicleAssignment assignment = db.first("select a from VehicleAssignment a where a.vehicleId = ?1 and a.endDate is null", VehicleAssignment.class, id).orElse(null);
        String driverName = null;
        if (assignment != null)
            driverName = db.first("select e.firstName || ' ' || e.lastName from Employee e where e.id = ?1", String.class, assignment.employeeId).orElse(null);
        LocalDate today = today();
        List<VehicleDocument> docs = db.list("select d from VehicleDocument d where d.vehicleId = ?1", VehicleDocument.class, id);
        int warnings = (int) latestDocuments(docs).stream().filter(d -> !d.endDate.isAfter(today.plusDays(WARN_DAYS))).count();
        return new VehicleDto(vehicle.id, vehicle.plate, vehicle.brand, vehicle.model, vehicle.status, vehicle.odometer,
                assignment == null ? null : assignment.employeeId, driverName, vehicle.nextServiceDate, vehicle.nextServiceKm,
                serviceState(vehicle, today), warnings);
    }

    public void remind() {
        LocalDate today = today();
        List<Vehicle> vehicles = db.list("select v from Vehicle v where v.status <> ?1", Vehicle.class, VehicleStatus.RETIRED);
        for (Vehicle vehicle : vehicles) {
            String serviceState = serviceState(vehicle, today);
            if (serviceState.equals("DUE_SOON") || serviceState.equals("OVERDUE")) {
                String key = serviceState + ":" + Objects.toString(vehicle.nextServiceKm, "") + ":" + Objects.toString(vehicle.nextServiceDate, "");
                if (!key.equals(vehicle.serviceRemindedFor)) {
                    vehicle.serviceRemindedFor = key;
                    List<UUID> managers = notifications.usersWithPermission(ctx().requireCompanyId(), "fleet:update");
                    notifications.notify(new NotifyRequest(managers, vehicle.companyId, "fleet.service",
                            "Bakım hatırlatması: " + vehicle.plate, vehicle.brand + " " + vehicle.model + " · " + serviceState,
                            "/fleet/vehicles/" + vehicle.id, true));
                }
            }
            List<VehicleDocument> docs = db.list("select d from VehicleDocument d where d.vehicleId = ?1", VehicleDocument.class, vehicle.id);
            for (VehicleDocument doc : latestDocuments(docs)) {
                String state = doc.endDate.isBefore(today) ? "EXPIRED" : !doc.endDate.isAfter(today.plusDays(WARN_DAYS)) ? "EXPIRING" : "VALID";
                if (state.equals("VALID") || state.equals(doc.remindedState)) continue;
                doc.remindedState = state;
                List<UUID> managers = notifications.usersWithPermission(ctx().requireCompanyId(), "fleet:update");
                notifications.notify(new NotifyRequest(managers, vehicle.companyId, "fleet.document",
                        "Belge hatırlatması: " + vehicle.plate, doc.type + " · " + state + " · " + doc.endDate.format(DISPLAY),
                        "/fleet/vehicles/" + vehicle.id, true));
            }
        }
        db.flush();
    }

    /** Latest document (by end date) of each type. */
    private static List<VehicleDocument> latestDocuments(List<VehicleDocument> docs) {
        Map<VehicleDocumentType, VehicleDocument> latest = new LinkedHashMap<>();
        for (VehicleDocument doc : docs.stream().sorted(Comparator.comparing((VehicleDocument d) -> d.endDate).reversed()).toList())
            latest.putIfAbsent(doc.type, doc);
        return new ArrayList<>(latest.values());
    }

    private void recalcFuel(UUID vehicleId) {
        List<VehicleFuelEntry> entries = db.list("select e from VehicleFuelEntry e where e.vehicleId = ?1 order by e.odometer, e.entryDate", VehicleFuelEntry.class, vehicleId);
        VehicleFuelEntry previousFull = null;
        for (VehicleFuelEntry entry : entries) {
            if (previousFull != null && entry.odometer > previousFull.odometer) {
                entry.distance = entry.odometer - previousFull.odometer;
                if (entry.fullTank && entry.distance > 0)
                    entry.consumption = entry.liters.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(entry.distance), 2, RoundingMode.HALF_UP);
                else entry.consumption = null;
            } else {
                entry.distance = null;
                entry.consumption = null;
            }
            if (entry.fullTank) previousFull = entry;
        }
    }

    private void recordOdometer(Vehicle vehicle, LocalDate date, int odometer, VehicleOdometerSource source, UUID fuelId, UUID serviceId) {
        if (odometer > vehicle.odometer) vehicle.odometer = odometer;
        VehicleOdometerReading reading = new VehicleOdometerReading();
        reading.vehicleId = vehicle.id;
        reading.readingDate = date;
        reading.odometer = odometer;
        reading.source = source;
        reading.fuelEntryId = fuelId;
        reading.serviceId = serviceId;
        reading.createdById = ctx().userId;
        db.add(reading);
    }

    private void assertOdometer(UUID vehicleId, LocalDate date, int odometer) {
        List<VehicleOdometerReading> readings = db.list("select r from VehicleOdometerReading r where r.vehicleId = ?1", VehicleOdometerReading.class, vehicleId);
        int min = 0;
        Integer later = null;
        for (VehicleOdometerReading r : readings) {
            if (!r.readingDate.isAfter(date)) min = Math.max(min, r.odometer);
            else later = later == null ? r.odometer : Math.min(later, r.odometer);
        }
        Guard.that(odometer >= min, "Kilometre en az " + min + " olmalı");
        if (later != null) Guard.that(odometer <= later, "Kilometre en fazla " + later + " olmalı");
    }

    static String serviceState(Vehicle vehicle, LocalDate today) {
        boolean overdueKm = vehicle.nextServiceKm != null && vehicle.odometer >= vehicle.nextServiceKm;
        boolean overdueDate = vehicle.nextServiceDate != null && !today.isBefore(vehicle.nextServiceDate);
        if (overdueKm || overdueDate) return "OVERDUE";
        boolean soonKm = vehicle.nextServiceKm != null && vehicle.odometer >= vehicle.nextServiceKm - WARN_KM;
        boolean soonDate = vehicle.nextServiceDate != null && !today.isBefore(vehicle.nextServiceDate.minusDays(WARN_DAYS));
        return soonKm || soonDate ? "DUE_SOON" : "OK";
    }

    private Vehicle active(UUID id) {
        Vehicle vehicle = db.first("select v from Vehicle v where v.id = ?1", Vehicle.class, id).orElseThrow(() -> AppException.notFound("Araç bulunamadı"));
        Guard.state(vehicle.status != VehicleStatus.RETIRED, "Hurdaya ayrılmış araç güncellenemez");
        return vehicle;
    }

    private boolean isDriver(UUID vehicleId) {
        UUID employeeId = employeeIdOfUser();
        return employeeId != null && db.exists("select a.id from VehicleAssignment a where a.vehicleId = ?1 and a.employeeId = ?2 and a.endDate is null", vehicleId, employeeId);
    }

    private UUID employeeIdOfUser() {
        UUID userId = ctx().userId;
        if (userId == null) return null;
        return db.first("select e.id from Employee e where e.userId = ?1", UUID.class, userId).orElse(null);
    }

    static String normalize(String value) {
        if (value == null) return null;
        String compact = value.toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
        Matcher match = PLATE.matcher(compact);
        return match.matches() ? match.group(1) + " " + match.group(2) + " " + match.group(3) : null;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
