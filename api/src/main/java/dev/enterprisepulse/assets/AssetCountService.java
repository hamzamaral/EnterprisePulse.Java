package dev.enterprisepulse.assets;

import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Asset;
import dev.enterprisepulse.data.generated.AssetCondition;
import dev.enterprisepulse.data.generated.AssetCount;
import dev.enterprisepulse.data.generated.AssetCountLine;
import dev.enterprisepulse.data.generated.AssetCountStatus;
import dev.enterprisepulse.data.generated.AssignmentStatus;
import dev.enterprisepulse.platform.NumberingService;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asset verification (demirbaş sayımı). Starting a count snapshots every in-service asset in scope with its current holder;
 * assets are then marked found by scanning their QR label, asset tag or serial number. Scanned assets outside the scope are unexpected.
 */
@Service
@Transactional
public class AssetCountService {
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);

    public record CreateRequest(LocalDate countDate, UUID locationId, UUID categoryId, String notes) {}

    public record ScanRequest(String code) {}

    public record UpdateLineRequest(Boolean found, AssetCondition condition, String note, boolean clearCondition) {}

    public record CompleteRequest(boolean applyConditions) {}

    public record LineDto(UUID id, UUID assetId, String assetTag, String assetName, String categoryName, String serialNumber, boolean expected,
                          String expectedHolder, boolean found, Instant foundAt, String foundByName, AssetCondition condition, AssetCondition currentCondition,
                          String note) {}

    public record DetailDto(UUID id, String number, AssetCountStatus status, LocalDate countDate, UUID locationId, String locationName, UUID categoryId,
                            String categoryName, int expectedCount, int foundCount, int missingCount, int unexpectedCount, String createdByName,
                            Instant createdAt, Instant completedAt, String notes, List<LineDto> lines) {}

    /** FOUND, ALREADY_FOUND or UNEXPECTED (scanned but outside the count's scope). */
    public record ScanResultDto(String result, LineDto line) {}

    private final Db db;
    private final NumberingService numbering;
    private final AssetService assets;

    public AssetCountService(Db db, NumberingService numbering, AssetService assets) {
        this.db = db;
        this.numbering = numbering;
        this.assets = assets;
    }

    @Transactional(readOnly = true)
    public DetailDto detail(UUID id) {
        AssetCount c = Guard.found(db.first("select c from AssetCount c where c.id = ?1", AssetCount.class, id), "Sayım bulunamadı");
        List<LineDto> lines = lines(db.list("select l from AssetCountLine l where l.countId = ?1", AssetCountLine.class, id)).stream()
                .sorted(Comparator.comparing(LineDto::assetTag)).toList();
        int expected = (int) lines.stream().filter(LineDto::expected).count();
        int found = (int) lines.stream().filter(l -> l.expected() && l.found()).count();
        String location = c.locationId == null ? null : assets.names("AssetLocation", List.of(c.locationId)).get(c.locationId);
        String category = c.categoryId == null ? null : assets.names("AssetCategory", List.of(c.categoryId)).get(c.categoryId);
        return new DetailDto(c.id, c.number, c.status, c.countDate, c.locationId, location, c.categoryId, category, expected, found, expected - found,
                lines.size() - expected, assets.names("User", List.of(c.createdById)).get(c.createdById), c.createdAt, c.completedAt, c.notes, lines);
    }

    public DetailDto create(CreateRequest body) {
        Guard.that(body.countDate() != null, "Sayım tarihi zorunludur");
        if (body.locationId() != null) Guard.that(db.exists("select l.id from AssetLocation l where l.id = ?1", body.locationId()), "Lokasyon bulunamadı");
        if (body.categoryId() != null) Guard.that(db.exists("select c.id from AssetCategory c where c.id = ?1", body.categoryId()), "Demirbaş kategorisi bulunamadı");
        String open = db.list("select c from AssetCount c where c.status = ?1", AssetCount.class, AssetCountStatus.DRAFT).stream()
                .filter(c -> Objects.equals(c.locationId, body.locationId()) && Objects.equals(c.categoryId, body.categoryId()))
                .map(c -> c.number).findFirst().orElse(null);
        Guard.state(open == null, "Bu kapsam için " + open + " numaralı sayım hâlâ açık");

        List<Asset> inScope = db.list("select a from Asset a where a.status not in ?1 order by a.assetTag", Asset.class, AssetService.OUT_OF_SERVICE).stream()
                .filter(a -> body.categoryId() == null || body.categoryId().equals(a.categoryId)).toList();
        if (body.locationId() != null) {
            Set<UUID> placed = new HashSet<>(db.list("select x.assetId from AssetAssignment x where x.status = ?1 and x.locationId = ?2", UUID.class,
                    AssignmentStatus.ACTIVE, body.locationId()));
            inScope = inScope.stream().filter(a -> body.locationId().equals(a.locationId) || placed.contains(a.id)).toList();
        }
        Guard.that(!inScope.isEmpty(), "Bu kapsamda demirbaş yok");
        Map<UUID, AssetService.HolderDto> holders = assets.holders(inScope.stream().map(a -> a.id).toList());
        Map<UUID, String> locations = assets.names("AssetLocation", inScope.stream().map(a -> a.locationId).filter(x -> x != null).toList());

        AssetCount count = new AssetCount();
        count.number = numbering.next("ASSET_COUNT");
        count.countDate = body.countDate();
        count.locationId = body.locationId();
        count.categoryId = body.categoryId();
        count.notes = AssetService.clean(body.notes());
        count.createdById = CurrentUser.get().requireUserId();
        db.add(count);
        for (Asset a : inScope) {
            AssetCountLine line = new AssetCountLine();
            line.countId = count.id;
            line.assetId = a.id;
            AssetService.HolderDto holder = holders.get(a.id);
            line.expectedHolder = holder != null ? holder.name() : locations.get(a.locationId);
            db.add(line);
        }
        db.flush();
        return detail(count.id);
    }

    public ScanResultDto scan(UUID id, ScanRequest body) {
        String code = body == null || body.code() == null ? "" : body.code().trim();
        Guard.that(!code.isEmpty() && code.length() <= 500, "Kod zorunludur");
        lockDraft(id);
        Matcher match = UUID_PATTERN.matcher(code);
        String upper = code.toUpperCase(Locale.ROOT);
        Asset asset = match.find()
                ? db.first("select a from Asset a where a.id = ?1", Asset.class, UUID.fromString(match.group())).orElse(null)
                : db.first("select a from Asset a where upper(a.assetTag) = ?1 or upper(a.serialNumber) = ?1", Asset.class, upper).orElse(null);
        if (asset == null) throw AppException.notFound("\"" + code + "\" ile eşleşen demirbaş yok");

        AssetCountLine line = db.first("select l from AssetCountLine l where l.countId = ?1 and l.assetId = ?2", AssetCountLine.class, id, asset.id).orElse(null);
        String result;
        if (line != null && line.foundAt != null) result = "ALREADY_FOUND";
        else {
            if (line != null) result = "FOUND";
            else {
                AssetService.HolderDto holder = assets.holders(List.of(asset.id)).get(asset.id);
                line = new AssetCountLine();
                line.countId = id;
                line.assetId = asset.id;
                line.expected = false;
                line.expectedHolder = holder != null ? holder.name()
                        : asset.locationId == null ? null : assets.names("AssetLocation", List.of(asset.locationId)).get(asset.locationId);
                db.add(line);
                result = "UNEXPECTED";
            }
            line.foundAt = Instant.now();
            line.foundById = CurrentUser.get().requireUserId();
            db.flush();
        }
        return new ScanResultDto(result, lines(List.of(line)).get(0));
    }

    public LineDto updateLine(UUID id, UUID lineId, UpdateLineRequest body) {
        lockDraft(id);
        AssetCountLine line = line(id, lineId);
        Guard.that(!(Boolean.FALSE.equals(body.found()) && !line.expected), "Beklenmeyen demirbaşı bulunmadı yapmak yerine sayımdan çıkarın");
        if (body.condition() != null) line.condition = body.condition();
        if (body.clearCondition()) line.condition = null;
        if (body.note() != null) line.note = AssetService.clean(body.note());
        if (Boolean.TRUE.equals(body.found()) && line.foundAt == null) {
            line.foundAt = Instant.now();
            line.foundById = CurrentUser.get().requireUserId();
        }
        if (Boolean.FALSE.equals(body.found())) {
            line.foundAt = null;
            line.foundById = null;
            line.condition = null;
        }
        db.flush();
        return lines(List.of(line)).get(0);
    }

    public void removeLine(UUID id, UUID lineId) {
        lockDraft(id);
        AssetCountLine line = line(id, lineId);
        Guard.that(!line.expected, "Beklenen demirbaşlar sayımdan çıkarılamaz");
        db.em().remove(line);
        db.flush();
    }

    /** Optionally copies the conditions recorded during the count onto the asset cards. */
    public DetailDto complete(UUID id, CompleteRequest body) {
        AssetCount count = lockDraft(id);
        if (body != null && body.applyConditions()) {
            for (AssetCountLine l : db.list("select l from AssetCountLine l where l.countId = ?1 and l.foundAt is not null and l.condition is not null",
                    AssetCountLine.class, id)) {
                Asset a = db.find(Asset.class, l.assetId);
                if (a != null && a.condition != l.condition) a.condition = l.condition;
            }
        }
        count.status = AssetCountStatus.COMPLETED;
        count.completedAt = Instant.now();
        db.flush();
        return detail(id);
    }

    public DetailDto cancel(UUID id) {
        AssetCount count = lockDraft(id);
        count.status = AssetCountStatus.CANCELLED;
        db.flush();
        return detail(id);
    }

    /** Locks the count row so concurrent scans and completion cannot interleave. */
    private AssetCount lockDraft(UUID id) {
        AssetCount count = db.query("select c from AssetCount c where c.id = ?1", AssetCount.class, id).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList().stream().findFirst().orElseThrow(() -> AppException.notFound("Sayım bulunamadı"));
        Guard.state(count.status == AssetCountStatus.DRAFT, "Yalnızca açık sayımlar değiştirilebilir");
        return count;
    }

    private AssetCountLine line(UUID id, UUID lineId) {
        return Guard.found(db.first("select l from AssetCountLine l where l.id = ?1 and l.countId = ?2", AssetCountLine.class, lineId, id), "Sayım satırı bulunamadı");
    }

    private List<LineDto> lines(List<AssetCountLine> rows) {
        Map<UUID, Asset> byId = new HashMap<>();
        Set<UUID> assetIds = new HashSet<>();
        rows.forEach(l -> assetIds.add(l.assetId));
        if (!assetIds.isEmpty()) db.list("select a from Asset a where a.id in ?1", Asset.class, assetIds).forEach(a -> byId.put(a.id, a));
        Map<UUID, String> categories = assets.names("AssetCategory", byId.values().stream().map(a -> a.categoryId).distinct().toList());
        Map<UUID, String> users = assets.names("User", rows.stream().map(l -> l.foundById).filter(x -> x != null).distinct().toList());
        return rows.stream().filter(l -> byId.containsKey(l.assetId)).map(l -> {
            Asset a = byId.get(l.assetId);
            return new LineDto(l.id, l.assetId, a.assetTag, a.name, categories.get(a.categoryId), a.serialNumber, l.expected, l.expectedHolder, l.foundAt != null,
                    l.foundAt, l.foundById == null ? null : users.get(l.foundById), l.condition, a.condition, l.note);
        }).toList();
    }
}
