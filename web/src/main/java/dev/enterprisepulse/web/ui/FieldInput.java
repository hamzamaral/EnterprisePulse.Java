package dev.enterprisepulse.web.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Meta.FieldMeta;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

/** Editor for one metadata field, bound directly to a JSON record. */
public final class FieldInput {
    private FieldInput() {}

    public static Component create(FieldMeta f, ObjectNode data, Map<String, Map<String, String>> labels, boolean disabled, Consumer<String> onChange) {
        String caption = Ui.T(f.label());
        String name = f.name();
        JsonNode current = data.get(name);
        String raw = Fmt.raw(current);
        Consumer<Object> set = v -> {
            if (v == null) data.putNull(name);
            else data.set(name, ApiClient.JSON.valueToTree(v));
            if (onChange != null) onChange.accept(name);
        };
        Component c;
        switch (f.kind()) {
            case "bool" -> {
                Checkbox cb = new Checkbox(caption, current != null && current.asBoolean());
                cb.addValueChangeListener(e -> set.accept(e.getValue()));
                c = cb;
            }
            case "enum" -> {
                ComboBox<String> box = new ComboBox<>(caption, f.values());
                box.setItemLabelGenerator(v -> Ui.meta().enumLabel(f.enumName(), v, Ui.locale()));
                box.setClearButtonVisible(!f.required());
                box.setRequiredIndicatorVisible(f.required());
                if (raw != null) box.setValue(raw);
                box.addValueChangeListener(e -> set.accept(e.getValue()));
                c = box;
            }
            case "int", "long" -> {
                IntegerField n = new IntegerField(caption);
                n.setRequiredIndicatorVisible(f.required());
                if (raw != null) {
                    try {
                        n.setValue(Integer.parseInt(raw));
                    } catch (NumberFormatException ignored) {
                    }
                }
                n.addValueChangeListener(e -> set.accept(e.getValue()));
                c = n;
            }
            case "decimal" -> {
                BigDecimalField n = new BigDecimalField(caption);
                n.setRequiredIndicatorVisible(f.required());
                if (raw != null) {
                    try {
                        n.setValue(new BigDecimal(raw));
                    } catch (NumberFormatException ignored) {
                    }
                }
                n.addValueChangeListener(e -> set.accept(e.getValue()));
                c = n;
            }
            case "date" -> {
                DatePicker d = new DatePicker(caption);
                d.setI18n(Ui.datePickerI18n());
                d.setRequiredIndicatorVisible(f.required());
                d.setClearButtonVisible(!f.required());
                d.setValue(Fmt.date(raw));
                d.addValueChangeListener(e -> set.accept(e.getValue() == null ? null : e.getValue().toString()));
                c = d;
            }
            case "datetime" -> {
                DateTimePicker d = new DateTimePicker(caption);
                d.setDatePickerI18n(Ui.datePickerI18n());
                d.setRequiredIndicatorVisible(f.required());
                Instant i = Fmt.instant(raw);
                if (i != null) d.setValue(LocalDateTime.ofInstant(i, Fmt.ZONE));
                d.addValueChangeListener(e -> set.accept(e.getValue() == null ? null : e.getValue().atZone(Fmt.ZONE).toInstant().toString()));
                c = d;
            }
            case "text" -> {
                TextArea t = new TextArea(caption);
                t.setRequiredIndicatorVisible(f.required());
                t.setValue(raw == null ? "" : raw);
                t.setMinHeight("90px");
                t.addValueChangeListener(e -> set.accept(e.getValue().isEmpty() ? null : e.getValue()));
                c = t;
            }
            case "json" -> {
                TextArea t = new TextArea(caption);
                t.addClassName("ep-mono");
                t.setValue(current == null || current.isNull() ? "" : current.toPrettyString());
                t.setMinHeight("110px");
                t.addValueChangeListener(e -> {
                    String v = e.getValue();
                    if (v.isBlank()) {
                        data.putNull(name);
                        t.setInvalid(false);
                        return;
                    }
                    try {
                        data.set(name, ApiClient.JSON.readTree(v));
                        t.setInvalid(false);
                    } catch (IOException ex) {
                        t.setInvalid(true);
                    }
                });
                c = t;
            }
            default -> {
                if (f.ref() != null) {
                    RefPicker p = new RefPicker(f.ref(), caption);
                    p.setRequiredIndicatorVisible(f.required());
                    String known = raw != null && labels != null && labels.containsKey(name) ? labels.get(name).get(raw) : null;
                    p.setRefId(raw, known);
                    p.addValueChangeListener(e -> set.accept(p.getRefId()));
                    c = p;
                } else {
                    TextField t = new TextField(caption);
                    t.setRequiredIndicatorVisible(f.required());
                    t.setValue(raw == null ? "" : raw);
                    t.addValueChangeListener(e -> set.accept(e.getValue().isEmpty() ? null : e.getValue()));
                    c = t;
                }
            }
        }
        if (c instanceof HasEnabled he) he.setEnabled(!disabled);
        c.getElement().getStyle().set("width", "100%");
        return c;
    }
}
