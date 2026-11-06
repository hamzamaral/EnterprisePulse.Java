package dev.enterprisepulse.web.ui;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Meta.FieldMeta;
import java.util.Map;

/** Grid columns for metadata fields (enum badges, formatted numbers/dates, reference labels). */
public final class Cells {
    private Cells() {}

    public static Grid.Column<ObjectNode> column(Grid<ObjectNode> grid, FieldMeta f, Map<String, Map<String, String>> labels) {
        Grid.Column<ObjectNode> col;
        if (f.kind().equals("enum")) {
            col = grid.addColumn(new ComponentRenderer<>(row -> {
                String raw = Fmt.raw(row.get(f.name()));
                return raw == null ? new Span() : Ui.badge(Ui.meta().enumLabel(f.enumName(), raw, Ui.locale()), Fmt.statusColor(raw));
            }));
        } else {
            col = grid.addColumn(row -> Fmt.value(f, row.get(f.name()), Ui.meta(), Ui.locale(), labels));
        }
        col.setHeader(Ui.T(f.label())).setKey(f.name()).setResizable(true).setAutoWidth(true);
        if (f.numeric()) col.setTextAlign(ColumnTextAlign.END);
        return col;
    }

    /** Merges a page's reference labels into the grid-wide map. */
    public static void merge(Map<String, Map<String, String>> target, Map<String, Map<String, String>> page) {
        page.forEach((k, v) -> target.computeIfAbsent(k, x -> new java.util.HashMap<>()).putAll(v));
    }
}
