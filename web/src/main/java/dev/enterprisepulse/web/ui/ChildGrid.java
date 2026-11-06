package dev.enterprisepulse.web.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import dev.enterprisepulse.web.core.Columns;
import dev.enterprisepulse.web.core.Meta.ChildMeta;
import dev.enterprisepulse.web.core.Meta.FieldMeta;
import dev.enterprisepulse.web.core.Meta.ResourceMeta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Lines/children of a record with add, edit (dialog) and delete. */
public class ChildGrid extends Div {
    private final ChildMeta child;
    private final String parentId;
    private final ResourceMeta resource;
    private final boolean editable;
    private final Runnable onChanged;
    private final Grid<ObjectNode> grid = new Grid<>();
    private final H4 caption = new H4();
    private final Map<String, Map<String, String>> labels = new HashMap<>();
    private List<ObjectNode> rows = new ArrayList<>();
    private Consumer<List<ObjectNode>> footer;

    public ChildGrid(ChildMeta child, String parentId, boolean editable, Runnable onChanged) {
        this.child = child;
        this.parentId = parentId;
        this.editable = editable;
        this.onChanged = onChanged;
        this.resource = Ui.meta().get(child.resource());
        addClassName("ep-child-grid");
        if (resource == null) return;
        Div bar = Ui.pageHeader(Ui.grow(caption));
        if (canCreate()) {
            Button add = new Button(Ui.L("add"), VaadinIcon.PLUS.create(), e -> add());
            add.addThemeVariants(ButtonVariant.LUMO_SMALL);
            bar.add(add);
        }
        add(bar);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);
        grid.setEmptyStateText(Ui.L("noRecords"));
        for (FieldMeta f : Columns.of(resource, child.field(), 9)) Cells.column(grid, f, labels);
        if (canDelete()) {
            grid.addColumn(new ComponentRenderer<>(row -> Ui.stopClick(Ui.iconButton(VaadinIcon.TRASH, Ui.L("delete"), () -> delete(row)))))
                    .setWidth("60px").setFlexGrow(0);
        }
        if (canEdit()) {
            grid.addClassName("ep-clickable");
            grid.addItemClickListener(e -> edit(e.getItem()));
        }
        add(grid);
        load();
    }

    /** Called after every load with the current rows (e.g. to show totals). */
    public void setFooter(Consumer<List<ObjectNode>> footer) {
        this.footer = footer;
        footer.accept(rows);
    }

    private boolean allowed(String action) {
        return Ui.session().has(resource.permissionFor(action, Ui.meta().registry()));
    }

    private boolean canCreate() {
        return editable && !resource.readOnly() && !resource.customCreate() && allowed("create");
    }

    private boolean canEdit() {
        return editable && !resource.readOnly() && resource.field("id") != null && allowed("update");
    }

    private boolean canDelete() {
        return canEdit() && allowed("delete");
    }

    public void load() {
        Ui.run(() -> {
            String sort = resource.field("lineNo") != null ? "lineNo" : resource.field("order") != null ? "order"
                    : resource.field("createdAt") != null ? "createdAt" : null;
            JsonNode res = Ui.api().get("/api/r/" + child.resource() + "?" + child.field() + "=" + parentId + "&pageSize=500"
                    + (sort != null ? "&sort=" + sort + "&order=asc" : ""));
            rows = Ui.rows(res.get("data"));
            labels.clear();
            labels.putAll(Ui.labels(res.get("labels")));
        });
        caption.setText(Ui.T(resource.label()) + " (" + rows.size() + ")");
        grid.setItems(rows);
        if (footer != null) footer.accept(rows);
    }

    private void add() {
        FormDialog.open(resource, null, null, Map.of(child.field(), parentId), saved -> changed());
    }

    private void edit(ObjectNode row) {
        FormDialog.open(resource, row, labels, Map.of(child.field(), parentId), saved -> changed());
    }

    private void delete(ObjectNode row) {
        Ui.confirm(Ui.L("confirmDelete"), () -> {
            if (Ui.run(() -> Ui.api().delete("/api/r/" + child.resource() + "/" + row.path("id").asText()), Ui.L("deleted"))) changed();
        });
    }

    private void changed() {
        load();
        if (onChanged != null) onChanged.run();
    }
}
