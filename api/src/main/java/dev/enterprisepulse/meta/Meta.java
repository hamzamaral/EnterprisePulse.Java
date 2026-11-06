package dev.enterprisepulse.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generated resource metadata (tools/codegen) bound to the JPA entity classes. */
public final class Meta {
    private Meta() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Text {
        public String tr = "";
        public String en = "";

        public String of(String locale) {
            return "en".equals(locale) ? en : tr;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldMeta {
        public String name;
        public String kind = "string";
        public boolean required;
        public boolean readOnly;
        public boolean hidden;
        public Text label = new Text();
        public String enumName;
        public List<String> values;
        public Integer scale;
        public String ref;
        public String defaultValue;
        @JsonIgnore public Field field;

        public void setEnum(String v) { enumName = v; }
        public void setDefault(String v) { defaultValue = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParentMeta {
        public String field;
        public String resource;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChildMeta {
        public String resource;
        public String field;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceMeta {
        public String resource;
        public String model;
        public String table;
        public String module;
        public Text label = new Text();
        public List<String> labelFields = new ArrayList<>();
        public boolean tenant;
        public boolean softDelete;
        public boolean readOnly;
        public boolean customCreate;
        public String sequence;
        public boolean draftOnly;
        public ParentMeta parent;
        public List<String> compositeKey;
        public List<FieldMeta> fields = new ArrayList<>();
        public List<ChildMeta> children = new ArrayList<>();
        @JsonIgnore public Class<?> type;
        @JsonIgnore private final Map<String, FieldMeta> byName = new LinkedHashMap<>();

        void index() {
            for (FieldMeta f : fields) byName.put(f.name, f);
        }

        public FieldMeta field(String name) {
            return byName.get(name);
        }

        public static final List<String> SEQUENCE_FIELD_NAMES = List.of("number", "employeeNo", "assetTag", "code");

        /** Field filled from the document number series on create. */
        public FieldMeta sequenceField() {
            if (sequence == null) return null;
            return SEQUENCE_FIELD_NAMES.stream().map(this::field).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }

        /** Documents with lines are edited only while in draft; later steps go through actions. */
        public boolean draftLocked() {
            return draftOnly && !children.isEmpty();
        }

        public String permissionFor(String action, Map<String, List<String>> registry) {
            List<String> actions = registry.get(module);
            if (actions == null || actions.contains(action)) return module + ":" + action;
            return "read".equals(action) ? module + ":read" : module + ":update";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PermissionAction {
        public String code;
        public String action;
        public Text label = new Text();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PermissionModule {
        public String module;
        public Text label = new Text();
        public List<PermissionAction> actions = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RoleSeed {
        public String name;
        public String description;
        public List<String> codes = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SequenceMeta {
        public String code;
        public String name;
        public String prefix;
        public boolean yearly;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        public List<ResourceMeta> resources = new ArrayList<>();
        public List<PermissionModule> permissions = new ArrayList<>();
        public Map<String, RoleSeed> roles = new LinkedHashMap<>();
        public List<SequenceMeta> sequences = new ArrayList<>();
        public Map<String, Map<String, Map<String, String>>> enums = new LinkedHashMap<>();
        public ObjectNode common;
    }
}
