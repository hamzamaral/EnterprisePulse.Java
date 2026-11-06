package dev.enterprisepulse.web.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Meta.FieldMeta;
import dev.enterprisepulse.web.core.Meta.ResourceMeta;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Consumer;

/** Create/edit dialog for simple records (and child lines). */
public final class FormDialog {
    private FormDialog() {}

    public static void open(ResourceMeta resource, ObjectNode record, Map<String, Map<String, String>> labels, Map<String, String> fixed,
                            Consumer<ObjectNode> onSaved) {
        boolean isNew = record == null;
        ObjectNode data = isNew ? defaults(resource) : record.deepCopy();
        if (fixed != null) fixed.forEach(data::put);
        Dialog d = new Dialog();
        d.setHeaderTitle((isNew ? Ui.L("new") : Ui.L("edit")) + " · " + Ui.T(resource.label()));
        d.setWidth("min(760px, 96vw)");
        d.add(new ResourceForm(resource, data, labels, isNew, false, false, fixed == null ? java.util.Set.of() : fixed.keySet(), 2, null));
        Button save = new Button(Ui.L("save"));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> {
            save.setEnabled(false);
            ObjectNode[] saved = new ObjectNode[1];
            boolean ok = Ui.run(() -> {
                ObjectNode body = payload(resource, data);
                JsonNode res = isNew
                        ? Ui.api().post("/api/r/" + resource.resource(), body)
                        : Ui.api().patch("/api/r/" + resource.resource() + "/" + ApiClient.enc(record.path(resource.keyField()).asText()), body);
                saved[0] = res != null && res.get("data") instanceof ObjectNode o ? o : ApiClient.JSON.createObjectNode();
            }, Ui.L("saved"));
            save.setEnabled(true);
            if (ok) {
                d.close();
                if (onSaved != null) onSaved.accept(saved[0]);
            }
        });
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), save);
        d.open();
    }

    public static ObjectNode defaults(ResourceMeta resource) {
        ObjectNode data = ApiClient.JSON.createObjectNode();
        for (FieldMeta f : resource.fields()) {
            if (f.defaultValue() == null || f.isSystem()) continue;
            String v = f.defaultValue();
            try {
                switch (f.kind()) {
                    case "bool" -> data.put(f.name(), v.equals("true"));
                    case "int", "long" -> data.put(f.name(), Long.parseLong(v));
                    case "decimal" -> data.put(f.name(), new BigDecimal(v));
                    case "enum", "string", "text" -> data.put(f.name(), v);
                    default -> { }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return data;
    }

    public static ObjectNode payload(ResourceMeta resource, ObjectNode data) {
        ObjectNode body = ApiClient.JSON.createObjectNode();
        for (FieldMeta f : resource.fields()) {
            if (!f.isSystem() && data.has(f.name())) body.set(f.name(), data.get(f.name()).deepCopy());
        }
        return body;
    }
}
