package dev.enterprisepulse.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.common.Guard;
import dev.enterprisepulse.common.Paged;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.meta.Meta.FieldMeta;
import dev.enterprisepulse.meta.Meta.ResourceMeta;
import dev.enterprisepulse.meta.MetadataStore;
import dev.enterprisepulse.platform.NumberingService;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.Session;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metadata-driven CRUD for every generated entity: paging, search, filters, foreign-key labels,
 * tenant-safe references, draft locks, document numbers and soft delete.
 */
@Service
@Transactional
public class ResourceService {
    public record LookupItem(String id, String label) {}

    public record ListResult(List<Object> data, Paged.PageMeta meta, Map<String, Map<String, String>> labels) {}

    public record OneResult(Object data, Map<String, Map<String, String>> labels) {}

    public record AllRows(ResourceMeta meta, List<Object> rows, Map<String, Map<String, String>> labels) {}

    private final Db db;
    private final MetadataStore meta;
    private final NumberingService numbering;
    private final Map<String, ResourceHook> hooks = new LinkedHashMap<>();
    private final ObjectMapper json;

    public ResourceService(Db db, MetadataStore meta, NumberingService numbering, List<ResourceHook> hooks, ObjectMapper json) {
        this.db = db;
        this.meta = meta;
        this.numbering = numbering;
        this.json = json;
        for (ResourceHook h : hooks) this.hooks.put(h.resource(), h);
    }

    private HibernateCriteriaBuilder cb() {
        return db.em().unwrap(Session.class).getCriteriaBuilder();
    }

    // ---------- read ----------

    @Transactional(readOnly = true)
    public ListResult list(String resource, ListQuery query) {
        ResourceMeta r = meta.get(resource);
        HibernateCriteriaBuilder cb = cb();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<?> countRoot = countQuery.from(r.type);
        countQuery.select(cb.count(countRoot)).where(predicates(r, countQuery, countRoot, query).toArray(Predicate[]::new));
        long total = db.em().createQuery(countQuery).getSingleResult();

        CriteriaQuery<Object> q = cb.createQuery(Object.class);
        Root<?> root = q.from(r.type);
        q.select(root).where(predicates(r, q, root, query).toArray(Predicate[]::new));
        sort(r, q, root, query.sort, query.order);
        int pageSize = Math.max(1, Math.min(query.pageSize, 5000));
        int page = Math.max(1, query.page);
        List<Object> rows = db.em().createQuery(q).setFirstResult((page - 1) * pageSize).setMaxResults(pageSize).getResultList();
        return new ListResult(rows, Paged.of(rows, total, page, pageSize).meta(), labelsFor(r, rows));
    }

    /** All rows matching the query (export, reports); capped to keep memory bounded. */
    @Transactional(readOnly = true)
    public AllRows all(String resource, ListQuery query) {
        query.page = 1;
        query.pageSize = 5000;
        ListResult result = list(resource, query);
        return new AllRows(meta.get(resource), result.data(), result.labels());
    }

    private List<Predicate> predicates(ResourceMeta r, AbstractQuery<?> q, Root<?> root, ListQuery query) {
        HibernateCriteriaBuilder cb = cb();
        List<Predicate> out = new ArrayList<>();
        ResourceHook hook = hooks.get(r.resource);
        if (hook != null) {
            Predicate scope = hook.scope(cb, q, root);
            if (scope != null) out.add(scope);
        }
        for (Map.Entry<String, String> e : query.filters.entrySet()) {
            String[] parts = e.getKey().split("__");
            FieldMeta f = r.field(parts[0]);
            if (f == null || f.hidden) continue;
            String op = parts.length > 1 ? parts[1] : "eq";
            Path<Object> path = root.get(f.field.getName());
            Class<?> type = f.field.getType();
            String raw = e.getValue();
            switch (op) {
                case "null" -> out.add("true".equals(raw) ? cb.isNull(path) : cb.isNotNull(path));
                case "in" -> {
                    List<Object> values = Arrays.stream(raw.split(",")).filter(s -> !s.isBlank()).map(s -> convert(s, type)).toList();
                    out.add(values.isEmpty() ? cb.disjunction() : path.in(values));
                }
                default -> out.add(compare(cb, op, path, convert(raw, type)));
            }
        }
        if (query.search != null && !query.search.isBlank()) {
            String pattern = "%" + query.search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            List<Predicate> any = new ArrayList<>();
            for (FieldMeta f : r.fields) {
                if ((f.kind.equals("string") || f.kind.equals("text")) && !f.hidden && f.field.getType() == String.class) {
                    any.add(cb.ilike(root.get(f.field.getName()), pattern, '\\'));
                }
            }
            if (!any.isEmpty()) out.add(cb.or(any.toArray(Predicate[]::new)));
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate compare(HibernateCriteriaBuilder cb, String op, Path<Object> path, Object value) {
        if (value == null) return op.equals("ne") ? cb.isNotNull(path) : cb.isNull(path);
        Expression<Comparable> c = (Expression<Comparable>) (Expression) path;
        Comparable v = (Comparable) value;
        return switch (op) {
            case "gte" -> cb.greaterThanOrEqualTo(c, v);
            case "lte" -> cb.lessThanOrEqualTo(c, v);
            case "gt" -> cb.greaterThan(c, v);
            case "lt" -> cb.lessThan(c, v);
            case "ne" -> cb.notEqual(path, value);
            default -> cb.equal(path, value);
        };
    }

    private void sort(ResourceMeta r, CriteriaQuery<?> q, Root<?> root, String sort, String order) {
        HibernateCriteriaBuilder cb = cb();
        FieldMeta f = sort == null ? null : r.field(sort);
        if (f == null || f.hidden) f = r.field("createdAt");
        if (f == null) f = r.field(r.labelFields.get(0));
        if (f == null) f = r.fields.get(0);
        boolean desc = order == null ? f.name.equals("createdAt") || f.name.equals("date") : order.equalsIgnoreCase("desc");
        Path<Object> p = root.get(f.field.getName());
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
        orders.add(desc ? cb.desc(p) : cb.asc(p));
        FieldMeta id = r.field("id");
        if (id != null && id != f) orders.add(cb.asc(root.get("id")));
        q.orderBy(orders);
    }

    @Transactional(readOnly = true)
    public OneResult get(String resource, String id) {
        ResourceMeta r = meta.get(resource);
        Object entity = find(r, id);
        return new OneResult(entity, labelsFor(r, List.of(entity)));
    }

    public Object find(ResourceMeta r, String id) {
        FieldMeta key = keyField(r);
        HibernateCriteriaBuilder cb = cb();
        CriteriaQuery<Object> q = cb.createQuery(Object.class);
        Root<?> root = q.from(r.type);
        List<Predicate> where = new ArrayList<>();
        ResourceHook hook = hooks.get(r.resource);
        if (hook != null) {
            Predicate scope = hook.scope(cb, q, root);
            if (scope != null) where.add(scope);
        }
        where.add(cb.equal(root.get(key.field.getName()), convert(id, key.field.getType())));
        q.select(root).where(where.toArray(Predicate[]::new));
        return db.em().createQuery(q).setMaxResults(1).getResultList().stream().findFirst().orElseThrow(AppException::notFound);
    }

    private static FieldMeta keyField(ResourceMeta r) {
        FieldMeta f = r.field("id");
        if (f == null) f = r.field("code");
        if (f == null) throw AppException.badRequest("Bu kaynak tekil anahtarla okunamaz");
        return f;
    }

    // ---------- labels / lookup ----------

    public String labelOf(ResourceMeta r, Object entity) {
        List<String> parts = new ArrayList<>();
        for (String name : r.labelFields) {
            FieldMeta f = r.field(name);
            Object v = f == null ? null : read(f, entity);
            if (v != null && !v.toString().isEmpty()) parts.add(v.toString());
        }
        if (!parts.isEmpty()) return String.join(" ", parts);
        Object key = read(keyField(r), entity);
        return key == null ? "" : key.toString();
    }

    public Map<String, Map<String, String>> labelsFor(ResourceMeta r, Collection<?> rows) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (FieldMeta f : r.fields) {
            if (f.ref == null || !meta.has(f.ref)) continue;
            List<String> ids = rows.stream().map(row -> read(f, row)).filter(Objects::nonNull).map(Object::toString).distinct().toList();
            if (ids.isEmpty()) continue;
            Map<String, String> labels = new LinkedHashMap<>();
            for (LookupItem item : lookup(f.ref, null, ids, 20, Map.of())) labels.put(item.id(), item.label());
            result.put(f.name, labels);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<LookupItem> lookup(String resource, String search, Collection<String> ids, int take, Map<String, String> filters) {
        ResourceMeta r = meta.get(resource);
        FieldMeta key = keyField(r);
        HibernateCriteriaBuilder cb = cb();
        CriteriaQuery<Object> q = cb.createQuery(Object.class);
        Root<?> root = q.from(r.type);
        if (ids != null) {
            List<Object> keys = ids.stream().map(id -> convert(id, key.field.getType())).toList();
            q.select(root).where(root.get(key.field.getName()).in(keys));
        } else {
            ListQuery lq = new ListQuery();
            lq.search = search;
            lq.filters = new LinkedHashMap<>(filters);
            List<Predicate> where = predicates(r, q, root, lq);
            if (r.field("isActive") != null) where.add(cb.isTrue(root.get("isActive")));
            q.select(root).where(where.toArray(Predicate[]::new));
            sort(r, q, root, r.labelFields.get(0).equals("id") ? null : r.labelFields.get(0), "asc");
        }
        var typed = db.em().createQuery(q);
        if (ids == null) typed.setMaxResults(Math.max(1, Math.min(take, 200)));
        return typed.getResultList().stream().map(row -> new LookupItem(String.valueOf(read(key, row)), labelOf(r, row))).toList();
    }

    // ---------- write ----------

    public Object create(String resource, ObjectNode body) {
        ResourceMeta r = meta.get(resource);
        Guard.state(!r.readOnly && !r.customCreate, "Bu kayıt türü buradan oluşturulamaz");
        Object entity;
        try {
            entity = r.type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        apply(r, entity, body, true);
        assertParentEditable(r, entity);
        FieldMeta createdBy = r.field("createdById");
        if (createdBy != null && read(createdBy, entity) == null) write(createdBy, entity, CurrentUser.get().userId);
        ResourceHook hook = hooks.get(resource);
        if (hook != null) hook.beforeSave(entity, true);
        FieldMeta number = r.sequenceField();
        boolean numbered = number != null && (read(number, entity) == null || read(number, entity).toString().isEmpty());
        validateRequired(r, entity, numbered ? number : null);
        if (numbered) write(number, entity, numbering.next(r.sequence));
        db.add(entity);
        db.flush();
        if (hook != null) hook.afterSave(entity, true);
        return entity;
    }

    public Object update(String resource, String id, ObjectNode body) {
        ResourceMeta r = meta.get(resource);
        Guard.state(!r.readOnly, "Bu kayıt türü değiştirilemez");
        Object entity = find(r, id);
        assertDraft(r, entity);
        assertParentEditable(r, entity);
        ResourceHook hook = hooks.get(resource);
        // Queries must not flush the half-applied entity: hooks compare it with its loaded state.
        var flushMode = db.em().getFlushMode();
        db.em().setFlushMode(jakarta.persistence.FlushModeType.COMMIT);
        try {
            apply(r, entity, body, false);
            if (hook != null) hook.beforeSave(entity, false);
            validateRequired(r, entity, null);
        } finally {
            db.em().setFlushMode(flushMode);
        }
        db.flush();
        if (hook != null) hook.afterSave(entity, false);
        return entity;
    }

    public void delete(String resource, String id) {
        ResourceMeta r = meta.get(resource);
        Guard.state(!r.readOnly, "Bu kayıt türü silinemez");
        Object entity = find(r, id);
        assertDraft(r, entity);
        assertParentEditable(r, entity);
        ResourceHook hook = hooks.get(resource);
        if (hook != null) hook.beforeDelete(entity);
        db.remove(entity);
        db.flush();
    }

    private static void assertDraft(ResourceMeta r, Object entity) {
        if (!r.draftLocked()) return;
        Object status = read(r.field("status"), entity);
        Guard.state("DRAFT".equals(String.valueOf(status)), "Yalnızca taslak belgeler düzenlenebilir veya silinebilir");
    }

    private void assertParentEditable(ResourceMeta r, Object entity) {
        if (r.parent == null || !meta.has(r.parent.resource)) return;
        ResourceMeta parent = meta.get(r.parent.resource);
        if (!parent.draftLocked()) return;
        FieldMeta link = r.field(r.parent.field);
        Object parentId = link == null ? null : read(link, entity);
        if (parentId == null) return;
        Object owner = find(parent, parentId.toString());
        Guard.state("DRAFT".equals(String.valueOf(read(parent.field("status"), owner))), "Belge taslak değil; kalemleri değiştirilemez");
    }

    private void apply(ResourceMeta r, Object entity, ObjectNode body, boolean isNew) {
        var it = body.fields();
        while (it.hasNext()) {
            var entry = it.next();
            FieldMeta f = r.field(entry.getKey());
            if (f == null || f.hidden) continue;
            boolean keyPart = r.compositeKey != null && r.compositeKey.contains(f.name);
            if (f.readOnly && !(isNew && keyPart)) continue;
            if (!isNew && r.draftLocked() && f.name.equals("status")) continue;
            if (!isNew && keyPart) continue;
            Class<?> type = f.field.getType();
            JsonNode node = entry.getValue();
            Object value;
            try {
                if (node == null || node.isNull()) value = null;
                else if (type == JsonNode.class) value = node;
                else if (node.isTextual() && type != String.class) value = node.asText().isEmpty() ? null : convert(node.asText(), type);
                else value = json.treeToValue(node, type);
                if (value instanceof String s) value = s.trim();
            } catch (AppException | IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
                throw AppException.badRequest("Geçersiz değer: " + f.label.tr);
            }
            if (value == null && type.isPrimitive()) throw AppException.badRequest("Zorunlu alan: " + f.label.tr);
            if (f.ref != null && value != null) assertReference(f, value);
            write(f, entity, value);
        }
    }

    /** A reference must point at a row the caller can see (same company, not deleted). */
    private void assertReference(FieldMeta f, Object value) {
        if (!meta.has(f.ref)) return;
        if (lookup(f.ref, null, List.of(value.toString()), 1, Map.of()).isEmpty()) {
            throw AppException.badRequest("Geçersiz seçim: " + f.label.tr);
        }
    }

    private static void validateRequired(ResourceMeta r, Object entity, FieldMeta skip) {
        for (FieldMeta f : r.fields) {
            if (!f.required || f.readOnly || f == skip) continue;
            Object v = read(f, entity);
            if (v == null || (v instanceof String s && s.isEmpty())) throw AppException.badRequest("Zorunlu alan: " + f.label.tr);
        }
    }

    public static Object read(FieldMeta f, Object entity) {
        try {
            return f.field.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void write(FieldMeta f, Object entity, Object value) {
        try {
            f.field.set(entity, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object convert(String raw, Class<?> type) {
        boolean nullable = !type.isPrimitive();
        if (raw.equals("null") && nullable) return null;
        try {
            if (type == String.class) return raw;
            if (type == UUID.class) return UUID.fromString(raw);
            if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, raw.toUpperCase(java.util.Locale.ROOT));
            if (type == boolean.class || type == Boolean.class) return raw.equals("true") || raw.equals("1");
            if (type == LocalDate.class) return LocalDate.parse(raw.substring(0, Math.min(10, raw.length())));
            if (type == Instant.class) return raw.length() == 10 ? LocalDate.parse(raw).atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : OffsetDateTime.parse(raw.endsWith("Z") || raw.contains("+") ? raw : raw + "Z").toInstant();
            if (type == int.class || type == Integer.class) return Integer.parseInt(raw);
            if (type == long.class || type == Long.class) return Long.parseLong(raw);
            if (type == double.class || type == Double.class) return Double.parseDouble(raw);
            if (type == BigDecimal.class) return new BigDecimal(raw);
        } catch (RuntimeException e) {
            throw AppException.badRequest("Geçersiz değer: " + raw);
        }
        throw AppException.badRequest("Desteklenmeyen filtre türü");
    }
}
