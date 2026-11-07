package dev.enterprisepulse.web.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Meta;
import dev.enterprisepulse.web.core.Texts;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Record side panels: approval flow, attached files and audit history. */
public final class Panels {
    private Panels() {}

    public static class WorkflowPanel extends Div {
        private final String entityType;
        private final String entityId;
        private final Runnable onChanged;

        public WorkflowPanel(String entityType, String entityId, Runnable onChanged) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.onChanged = onChanged;
            load();
        }

        private void load() {
            removeAll();
            JsonNode[] instance = new JsonNode[1];
            Ui.run(() -> instance[0] = Ui.api().get("/api/workflow/entity/" + entityType + "/" + ApiClient.enc(entityId)));
            if (instance[0] == null || instance[0].isNull()) {
                add(Ui.muted(Ui.T("Bu kayıt için başlatılmış bir onay süreci yok.", "No approval flow was started for this record.")));
                return;
            }
            add(new WorkflowView((ObjectNode) instance[0], () -> {
                load();
                if (onChanged != null) onChanged.run();
            }));
        }
    }

    public static class FilesPanel extends Div {
        private final String entityType;
        private final String entityId;
        private final Div list = new Div();

        public FilesPanel(String entityType, String entityId) {
            this.entityType = entityType;
            this.entityId = entityId;
            Div bar = Ui.pageHeader(Ui.grow(new H4(Ui.L("files"))));
            if (Ui.session().has("files:create")) {
                UI ui = UI.getCurrent();
                Upload upload = new Upload(UploadHandler.inMemory((meta, data) -> ui.access(() -> {
                    if (Ui.run(() -> Ui.api().upload("/api/files", meta.fileName(), meta.contentType(), data,
                            Map.of("entityType", entityType, "entityId", entityId)), Ui.L("saved"))) load();
                })));
                upload.setMaxFileSize(20 * 1024 * 1024);
                upload.setDropAllowed(true);
                upload.addClassName("ep-upload");
                bar.add(upload);
            }
            list.addClassName("ep-simple-table");
            add(bar, list);
            load();
        }

        private void load() {
            list.removeAll();
            List<ObjectNode> files = new java.util.ArrayList<>();
            Ui.run(() -> files.addAll(Ui.rows(Ui.api().get("/api/files?entityType=" + entityType + "&entityId=" + entityId))));
            for (ObjectNode f : files) {
                Anchor name = new Anchor();
                name.setText(f.path("originalName").asText());
                name.getElement().addEventListener("click", e -> Ui.download("/api/files/" + f.path("id").asText() + "/download"));
                name.addClassName("ep-link");
                Div row = new Div(VaadinIcon.FILE_O.create(), name, new Span(size(f.path("size").asLong())), new Span(Fmt.dateTime(f.get("createdAt"))),
                        Ui.iconButton(VaadinIcon.TRASH, Ui.L("delete"), () -> Ui.confirm(Ui.L("confirmDelete"), () -> {
                            if (Ui.run(() -> Ui.api().delete("/api/files/" + f.path("id").asText()), Ui.L("deleted"))) load();
                        })));
                row.addClassName("ep-simple-row");
                list.add(row);
            }
            if (files.isEmpty()) list.add(Ui.muted(Ui.L("noRecords")));
        }

        private static String size(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
            return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
        }
    }

    public static class HistoryPanel extends Div {
        public HistoryPanel(String entityType, String entityId) {
            addClassName("ep-simple-table");
            Meta.ResourceMeta resource = Ui.meta() == null ? null : Ui.meta().byModel(entityType);
            List<ObjectNode> rows = new java.util.ArrayList<>();
            Ui.run(() -> rows.addAll(Ui.rows(Ui.api().get("/api/r/audit-logs?entityType=" + entityType + "&entityId=" + entityId
                    + "&sort=createdAt&order=desc&pageSize=100").get("data"))));
            Div head = new Div(new Span(Ui.T("Tarih", "Date")), new Span(Ui.T("Kullanıcı", "User")), new Span(Ui.T("İşlem", "Action")),
                    new Span(Ui.T("Değişiklikler", "Changes")));
            head.addClassNames("ep-simple-row", "ep-head");
            add(head);
            for (ObjectNode row : rows) {
                Div changes = new Div();
                for (String[] line : changeLines(resource, row)) {
                    Span bold = new Span(line[0]);
                    bold.getStyle().set("font-weight", "600");
                    Div item = new Div(bold, new Span(": " + line[1] + " → " + line[2]));
                    changes.add(item);
                }
                String email = row.path("userEmail").asText("");
                Div r = new Div(new Span(Fmt.dateTime(row.get("createdAt"))), new Span(email.isBlank() ? "—" : email),
                        Ui.badge(actionLabel(row.path("action").asText()), "contrast"), changes);
                r.addClassName("ep-simple-row");
                add(r);
            }
            if (rows.isEmpty()) add(Ui.muted(Ui.L("noRecords")));
        }

        private static String actionLabel(String action) {
            return switch (action == null ? "" : action) {
                case "create" -> Ui.T("Oluşturuldu", "Created");
                case "update" -> Ui.T("Güncellendi", "Updated");
                case "delete" -> Ui.T("Silindi", "Deleted");
                default -> action == null || action.isBlank() ? "—" : action;
            };
        }

        private static List<String[]> changeLines(Meta.ResourceMeta resource, ObjectNode row) {
            List<String[]> lines = new java.util.ArrayList<>();
            JsonNode newValues = row.get("newValues");
            JsonNode oldValues = row.get("oldValues");
            boolean create = "create".equals(row.path("action").asText());
            java.util.Iterator<String> keys = null;
            if (newValues != null && newValues.isObject() && newValues.size() > 0) keys = newValues.fieldNames();
            else if (oldValues != null && oldValues.isObject()) keys = oldValues.fieldNames();
            if (keys == null) return lines;
            while (keys.hasNext()) {
                String key = keys.next();
                Meta.FieldMeta field = resource == null ? null : resource.field(key);
                if (field != null && field.isSystem()) continue;
                if (key.equals("id") || key.equals("companyId")) continue;
                if (create && field != null && (field.kind().equals("uuid") || field.kind().equals("json"))) continue;
                String label = field == null ? key : Ui.T(field.label());
                JsonNode old = oldValues == null ? null : oldValues.get(key);
                JsonNode neu = newValues == null ? null : newValues.get(key);
                String from = format(field, old);
                String to = format(field, neu);
                if (from.equals("—") && to.equals("—")) continue;
                lines.add(new String[] { label, from, to });
            }
            return lines;
        }

        private static String format(Meta.FieldMeta field, JsonNode node) {
            if (node == null || node.isNull() || node.isMissingNode()) return "—";
            Meta.Document meta = Ui.meta();
            String locale = Ui.locale();
            if (field != null && "enum".equals(field.kind()) && field.values() != null && !field.values().isEmpty()) {
                String name = enumName(node, field.values());
                if (name != null) return meta.enumLabel(field.enumName(), name, locale);
            }
            if (field != null && Set.of("date", "datetime", "bool", "decimal", "enum").contains(field.kind())) {
                return Fmt.value(field, node, meta, locale, Map.of());
            }
            if (node.isTextual()) {
                String s = node.asText();
                return s == null || s.isBlank() ? "—" : s;
            }
            if (node.isBoolean()) return Texts.get(node.asBoolean() ? "yes" : "no", locale);
            if (node.isNumber()) return node.asText();
            return node.asText();
        }

        private static String enumName(JsonNode node, List<String> values) {
            if (node.isInt() || node.isLong()) {
                int i = node.asInt();
                return i >= 0 && i < values.size() ? values.get(i) : null;
            }
            String s = node.asText();
            if (s == null || s.isBlank()) return null;
            try {
                int i = Integer.parseInt(s);
                if (i >= 0 && i < values.size()) return values.get(i);
            } catch (NumberFormatException ignored) {}
            return s;
        }
    }
}
