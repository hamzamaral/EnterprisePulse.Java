package dev.enterprisepulse.web.views.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.ui.Ui;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Shared formatting of the asset screens (card, hand-over form, counts). */
public final class AssetUi {
    private AssetUi() {}

    public static final List<String> CONDITIONS = List.of("NEW", "GOOD", "FAIR", "DAMAGED");

    public static String day(JsonNode node) {
        String s = text(node);
        return s.equals("—") ? s : Fmt.date(node);
    }

    public static String stamp(JsonNode node) {
        Instant i = node == null || node.isNull() ? null : Fmt.instant(node.asText());
        return i == null ? "" : Fmt.DATE_TIME.format(i.atZone(Fmt.ZONE));
    }

    public static BigDecimal dec(JsonNode node) {
        return node == null || node.isNull() ? BigDecimal.ZERO : node.decimalValue();
    }

    public static String money(JsonNode node, String currency) {
        return node == null || node.isNull() ? "—" : Fmt.money(dec(node), currency, Ui.locale());
    }

    public static String text(JsonNode node) {
        return node == null || node.isNull() || node.asText().isEmpty() ? "—" : node.asText();
    }

    public static String enumLabel(String enumName, JsonNode value) {
        return value == null || value.isNull() ? "—" : Ui.meta().enumLabel(enumName, value.asText(), Ui.locale());
    }

    public static String conditionColor(String condition) {
        if (condition == null) return "contrast";
        return switch (condition) {
            case "NEW", "GOOD" -> "success";
            case "FAIR" -> "warning";
            case "DAMAGED" -> "error";
            default -> "contrast";
        };
    }

    public static Span conditionBadge(JsonNode condition) {
        return Ui.badge(enumLabel("AssetCondition", condition), conditionColor(condition == null || condition.isNull() ? null : condition.asText()));
    }

    /** Badge for a warranty state (expired, expiring, active); null when unknown. */
    public static Span warranty(String state) {
        if (state == null) return null;
        return switch (state) {
            case "expired" -> Ui.badge(Ui.T("Garanti bitti", "Warranty expired"), "error");
            case "expiring" -> Ui.badge(Ui.T("Garanti bitiyor", "Warranty ending"), "warning");
            case "active" -> Ui.badge(Ui.T("Garanti devam ediyor", "Under warranty"), "success");
            default -> null;
        };
    }

    /** Caption / value pair laid out in an ep-facts grid. */
    public static Div fact(String caption, Component... value) {
        Div v = new Div(value);
        Div d = new Div(Ui.muted(caption), v);
        d.addClassName("ep-fact");
        return d;
    }

    public static Div fact(String caption, String value) {
        return fact(caption, new Span(value));
    }

    public static Div facts(Component... facts) {
        Div d = new Div(facts);
        d.addClassName("ep-facts");
        return d;
    }

    public static Select<String> enumSelect(String label, String enumName, List<String> values, String value) {
        Select<String> s = new Select<>();
        s.setLabel(label);
        s.setItems(values);
        s.setItemLabelGenerator(v -> v == null ? "" : Ui.meta().enumLabel(enumName, v, Ui.locale()));
        if (value != null) s.setValue(value);
        s.setWidthFull();
        return s;
    }
}
