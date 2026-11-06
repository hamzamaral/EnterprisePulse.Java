package dev.enterprisepulse.web.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The API metadata document (/api/meta) that drives every generic screen. */
public final class Meta {
    private Meta() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Text(String tr, String en) {
        public String of(String locale) {
            return "en".equals(locale) && en != null ? en : tr;
        }
    }

    public static final Set<String> SYSTEM_FIELDS = Set.of("id", "companyId", "createdAt", "updatedAt", "deletedAt", "createdById");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldMeta(String name, String kind, boolean required, boolean readOnly, boolean hidden, Text label,
                            @JsonProperty("enum") String enumName, List<String> values, Integer scale, String ref,
                            @JsonProperty("default") String defaultValue) {
        public FieldMeta {
            if (kind == null) kind = "string";
            if (values == null) values = List.of();
        }

        public boolean isSystem() {
            return SYSTEM_FIELDS.contains(name);
        }

        public boolean numeric() {
            return kind.equals("decimal") || kind.equals("int") || kind.equals("long");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParentMeta(String field, String resource) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChildMeta(String resource, String field, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceMeta(String resource, String model, String module, Text label, List<String> labelFields, boolean tenant,
                               boolean softDelete, boolean readOnly, boolean customCreate, String sequence, boolean draftOnly,
                               ParentMeta parent, List<String> compositeKey, List<FieldMeta> fields, List<ChildMeta> children) {
        public ResourceMeta {
            if (labelFields == null) labelFields = List.of();
            if (fields == null) fields = List.of();
            if (children == null) children = List.of();
        }

        public FieldMeta field(String name) {
            for (FieldMeta f : fields) if (f.name().equals(name)) return f;
            return null;
        }

        /** Field filled from the document number series on create (number, employeeNo, assetTag, code). */
        public FieldMeta sequenceField() {
            if (sequence == null) return null;
            for (String name : List.of("number", "employeeNo", "assetTag", "code")) {
                FieldMeta f = field(name);
                if (f != null) return f;
            }
            return null;
        }

        public String keyField() {
            return field("id") != null ? "id" : field("code") != null ? "code" : fields.get(0).name();
        }

        public boolean draftLocked() {
            return draftOnly && !children.isEmpty();
        }

        public boolean inCompositeKey(String name) {
            return compositeKey != null && compositeKey.contains(name);
        }

        public String permissionFor(String action, Map<String, List<String>> registry) {
            List<String> actions = registry.get(module);
            if (actions == null || actions.contains(action)) return module + ":" + action;
            return action.equals("read") ? module + ":read" : module + ":update";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NavItem(Text label, String resource, String page, String query, List<String> permission) {
        public NavItem {
            if (permission == null) permission = List.of();
        }

        public String href() {
            return resource != null ? "r/" + resource + (query != null ? "?" + query : "") : page;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NavGroup(Text label, List<NavItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PermAction(String code, String action, Text label) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PermModule(String module, Text label, List<PermAction> actions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Document {
        public List<ResourceMeta> resources = new ArrayList<>();
        public List<NavGroup> navigation = new ArrayList<>();
        public List<PermModule> permissions = new ArrayList<>();
        public Map<String, Map<String, Map<String, String>>> enums = new LinkedHashMap<>();

        private Map<String, ResourceMeta> byName;
        private Map<String, List<String>> registry;

        public ResourceMeta get(String resource) {
            if (byName == null) {
                Map<String, ResourceMeta> map = new LinkedHashMap<>();
                for (ResourceMeta r : resources) map.put(r.resource(), r);
                byName = map;
            }
            return byName.get(resource);
        }

        public ResourceMeta byModel(String model) {
            return resources.stream().filter(r -> r.model().equals(model)).findFirst().orElse(null);
        }

        public Map<String, List<String>> registry() {
            if (registry == null) {
                Map<String, List<String>> map = new LinkedHashMap<>();
                for (PermModule p : permissions) map.put(p.module(), p.actions().stream().map(PermAction::action).toList());
                registry = map;
            }
            return registry;
        }

        public String enumLabel(String enumName, String value, String locale) {
            if (value == null) return "";
            if (enumName == null) return value;
            Map<String, Map<String, String>> byLocale = enums.get(enumName);
            if (byLocale == null) return value;
            Map<String, String> labels = byLocale.getOrDefault(locale, byLocale.get("tr"));
            return labels == null ? value : labels.getOrDefault(value, value);
        }
    }

    private static volatile Document cached;

    public static Document load(ApiClient api) {
        Document doc = cached;
        if (doc == null) {
            try {
                doc = ApiClient.JSON.treeToValue(api.get("/api/meta"), Document.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new ApiException(500, ex.getMessage());
            }
            cached = doc;
        }
        return doc;
    }

    public static Document get() {
        return cached;
    }
}
