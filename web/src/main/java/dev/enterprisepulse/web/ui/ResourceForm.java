package dev.enterprisepulse.web.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Meta.FieldMeta;
import dev.enterprisepulse.web.core.Meta.ResourceMeta;
import dev.enterprisepulse.web.core.ResourceViews;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Metadata-driven form: editable inputs or a definition-list for read-only detail views. */
public class ResourceForm extends Div {
    private final ResourceMeta resource;
    private final boolean isNew;
    private final boolean readOnly;

    public ResourceForm(ResourceMeta resource, ObjectNode data, Map<String, Map<String, String>> labels, boolean isNew, boolean readOnly,
                        boolean showSystem, Set<String> fixedFields, int columns, Consumer<String> onChange) {
        this.resource = resource;
        this.isNew = isNew;
        this.readOnly = readOnly;
        setWidthFull();
        ResourceViews.View view = ResourceViews.of(resource.resource());
        if (readOnly) {
            Div dl = new Div();
            dl.addClassName("ep-dl");
            for (FieldMeta f : resource.fields()) {
                if (f.hidden() || view.hiddenFields.contains(f.name()) || fixedFields.contains(f.name())) continue;
                boolean visible = showSystem && !isNew ? !f.name().equals("companyId") && !f.name().equals("deletedAt") : !f.isSystem();
                if (!visible) continue;
                Div item = displayItem(f, data.get(f.name()), labels);
                if (f.kind().equals("text") || f.kind().equals("json")) item.addClassName("ep-dl-wide");
                dl.add(item);
            }
            add(dl);
            return;
        }
        FormLayout form = new FormLayout();
        form.setWidthFull();
        if (columns >= 4) {
            form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2), new FormLayout.ResponsiveStep("1000px", 4));
        } else {
            form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", columns));
        }
        for (FieldMeta f : resource.fields()) {
            if (f.hidden() || view.hiddenFields.contains(f.name()) || fixedFields.contains(f.name())) continue;
            boolean visible = showSystem && !isNew ? !f.name().equals("companyId") && !f.name().equals("deletedAt") : !f.isSystem();
            if (!visible) continue;
            boolean editable = editable(f, view);
            if (isNew && !editable) continue;
            if (isNew && f == resource.sequenceField()) continue;
            Component c = editable ? FieldInput.create(f, data, labels, false, onChange) : display(f, data.get(f.name()), labels);
            form.add(c);
            if (f.kind().equals("text") || f.kind().equals("json")) form.setColspan(c, columns >= 4 ? 4 : columns);
        }
        add(form);
    }

    private boolean editable(FieldMeta f, ResourceViews.View view) {
        if (readOnly || f.isSystem() || view.readOnlyFields.contains(f.name())) return false;
        if (f.readOnly()) return isNew && resource.inCompositeKey(f.name());
        if (!isNew && resource.inCompositeKey(f.name())) return false;
        if (!isNew && f.name().equals("status") && resource.draftLocked()) return false;
        if (!isNew && f == resource.sequenceField()) return false;
        return true;
    }

    private static Component display(FieldMeta f, JsonNode value, Map<String, Map<String, String>> labels) {
        return displayItem(f, value, labels);
    }

    private static Div displayItem(FieldMeta f, JsonNode value, Map<String, Map<String, String>> labels) {
        Div box = new Div();
        box.addClassName("ep-dl-item");
        Span caption = new Span(Ui.T(f.label()));
        caption.addClassName("ep-dl-label");
        box.add(caption);
        String raw = Fmt.raw(value);
        if (f.kind().equals("enum") && raw != null) {
            Div v = new Div(Ui.badge(Fmt.value(f, value, Ui.meta(), Ui.locale(), labels), Fmt.statusColor(raw)));
            v.addClassName("ep-dl-value");
            box.add(v);
        } else if (f.kind().equals("json") && raw != null) {
            Pre pre = new Pre(value.toPrettyString());
            pre.addClassName("ep-json");
            box.add(pre);
        } else {
            String text = Fmt.value(f, value, Ui.meta(), Ui.locale(), labels);
            Div v = new Div(new Span(text.isEmpty() ? "—" : text));
            v.addClassName("ep-dl-value");
            box.add(v);
        }
        return box;
    }
}
