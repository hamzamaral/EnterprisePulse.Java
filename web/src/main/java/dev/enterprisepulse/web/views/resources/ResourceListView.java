package dev.enterprisepulse.web.views.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.ApiException;
import dev.enterprisepulse.web.core.Columns;
import dev.enterprisepulse.web.core.Meta;
import dev.enterprisepulse.web.core.Meta.FieldMeta;
import dev.enterprisepulse.web.core.Meta.NavItem;
import dev.enterprisepulse.web.core.Meta.ResourceMeta;
import dev.enterprisepulse.web.core.ResourceViews;
import dev.enterprisepulse.web.core.ResourceViews.Action;
import dev.enterprisepulse.web.core.ResourceViews.ActionContext;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.ActionRunner;
import dev.enterprisepulse.web.ui.Cells;
import dev.enterprisepulse.web.ui.FormDialog;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Generic paged list for any metadata resource: search, filters, sorting, export, create and row actions. */
@Route(value = "r/:resource", layout = MainLayout.class)
public class ResourceListView extends Div implements BeforeEnterObserver, HasDynamicTitle {
    private String resource;
    private ResourceMeta meta;
    private ResourceViews.View view;
    private Map<String, String> fixed = new LinkedHashMap<>();
    private final Map<String, String> filters = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> labels = new HashMap<>();
    private String search = "";
    private String sortField;
    private String sortOrder;
    private Grid<ObjectNode> grid;
    private final Span total = new Span();
    private String title = "EnterprisePulse";

    @Override
    public String getPageTitle() {
        return title + " · EnterprisePulse";
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated() || Meta.get() == null) return;
        resource = event.getRouteParameters().get("resource").orElse("");
        fixed = new LinkedHashMap<>();
        event.getLocation().getQueryParameters().getParameters().forEach((k, v) -> {
            if (!v.isEmpty()) fixed.put(k, v.get(0));
        });
        filters.clear();
        search = "";
        sortField = null;
        sortOrder = null;
        build();
    }

    private void build() {
        removeAll();
        addClassName("ep-list-view");
        meta = Meta.get().get(resource);
        if (meta == null) {
            add(Ui.alert(Ui.L("notFound"), "warning"));
            return;
        }
        if (!Session.current().has(meta.permissionFor("read", Meta.get().registry()))) {
            add(Ui.alert(Ui.L("forbidden"), "error"));
            return;
        }
        view = ResourceViews.of(resource);
        title = resolveTitle();
        total.addClassName("ep-total");

        TextField searchField = new TextField();
        searchField.setPlaceholder(Ui.L("search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.setValueChangeTimeout(350);
        searchField.addValueChangeListener(e -> {
            search = e.getValue();
            refresh();
        });
        searchField.addClassName("ep-search");

        Div filterPanel = new Div();
        filterPanel.addClassName("ep-card");
        filterPanel.setVisible(false);
        List<FieldMeta> filterFields = filterFields();

        Div header = Ui.pageHeader(Ui.grow(Ui.title(title), total), searchField);
        if (!filterFields.isEmpty()) {
            Button filterButton = new Button(VaadinIcon.FILTER.create(), e -> filterPanel.setVisible(!filterPanel.isVisible()));
            filterButton.setTooltipText(Ui.L("filters"));
            filterButton.addThemeVariants(ButtonVariant.LUMO_ICON);
            header.add(filterButton);
            buildFilters(filterPanel, filterFields);
        }
        for (ResourceViews.HeaderLink link : view.links) {
            if (link.permission() != null && !Session.current().has(link.permission())) continue;
            Button b = new Button(Ui.T(link.label(), link.labelEn()), link.icon().create(), e -> Ui.go(link.href()));
            b.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            header.add(b);
        }
        MenuBar export = new MenuBar();
        export.addThemeVariants(MenuBarVariant.LUMO_ICON);
        SubMenu sub = export.addItem(VaadinIcon.DOWNLOAD.create()).getSubMenu();
        sub.addItem("Excel (.xlsx)", e -> exportAs("xlsx"));
        sub.addItem("PDF", e -> exportAs("pdf"));
        header.add(export);
        if (canCreate()) {
            Button create = new Button(Ui.L("new"), VaadinIcon.PLUS.create(), e -> create());
            create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            header.add(create);
        }
        add(header, filterPanel);

        grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.addClassName("ep-grid");
        grid.setEmptyStateText(Ui.L("noRecords"));
        grid.setPageSize(25);
        for (FieldMeta f : Columns.of(meta)) {
            if (fixed.containsKey(f.name())) continue;
            Cells.column(grid, f, labels).setSortProperty(f.name());
        }
        if (!view.actions.isEmpty()) {
            grid.addColumn(new ComponentRenderer<>(this::actionButtons)).setAutoWidth(true).setFlexGrow(0);
        }
        grid.setMultiSort(false);
        if (canOpen()) {
            grid.addClassName("ep-clickable");
            grid.addItemClickListener(e -> open(e.getItem()));
        }
        grid.setItems(query -> {
            List<QuerySortOrder> orders = query.getSortOrders();
            sortField = orders.isEmpty() ? null : orders.get(0).getSorted();
            sortOrder = orders.isEmpty() ? null : orders.get(0).getDirection() == SortDirection.DESCENDING ? "desc" : "asc";
            return fetch(query.getOffset(), query.getLimit());
        });
        add(grid);
    }

    private String resolveTitle() {
        for (Meta.NavGroup g : Meta.get().navigation) {
            for (NavItem i : g.items()) {
                if (!resource.equals(i.resource())) continue;
                if (i.query() == null && fixed.isEmpty()) return Ui.T(i.label());
                if (i.query() != null && !fixed.isEmpty() && fixed.entrySet().stream().allMatch(f -> i.query().contains(f.getKey() + "=" + f.getValue()))) {
                    return Ui.T(i.label());
                }
            }
        }
        return Ui.T(meta.label());
    }

    private boolean canCreate() {
        return !meta.readOnly() && !view.hideCreate && (!meta.customCreate() || view.createDialog != null || view.createHref != null)
                && Session.current().has(meta.permissionFor("create", Meta.get().registry()));
    }

    private boolean canOpen() {
        return meta.field("id") != null || meta.field("code") != null;
    }

    private List<FieldMeta> filterFields() {
        if (view.filterFields != null) return view.filterFields.stream().map(meta::field).filter(java.util.Objects::nonNull).toList();
        return meta.fields().stream()
                .filter(f -> !f.hidden() && !fixed.containsKey(f.name()) && !f.name().equals("createdById")
                        && (f.kind().equals("enum") || f.kind().equals("bool") || (f.ref() != null && !f.name().equals("companyId"))))
                .limit(8).toList();
    }

    private void buildFilters(Div panel, List<FieldMeta> fields) {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2), new FormLayout.ResponsiveStep("1000px", 4));
        for (FieldMeta f : fields) form.add(filterInput(f));
        Button clear = new Button(Ui.L("clear"), VaadinIcon.CLOSE_SMALL.create(), e -> {
            filters.clear();
            panel.removeAll();
            buildFilters(panel, fields);
            refresh();
        });
        clear.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        panel.add(form, clear);
    }

    private Component filterInput(FieldMeta f) {
        String caption = Ui.T(f.label());
        if (f.kind().equals("enum")) {
            ComboBox<String> box = new ComboBox<>(caption, f.values());
            box.setItemLabelGenerator(v -> Meta.get().enumLabel(f.enumName(), v, Ui.locale()));
            box.setClearButtonVisible(true);
            box.addValueChangeListener(e -> setFilter(f.name(), e.getValue()));
            return box;
        }
        if (f.kind().equals("bool")) {
            ComboBox<String> box = new ComboBox<>(caption, "true", "false");
            box.setItemLabelGenerator(v -> Ui.L(v.equals("true") ? "yes" : "no"));
            box.setClearButtonVisible(true);
            box.addValueChangeListener(e -> setFilter(f.name(), e.getValue()));
            return box;
        }
        if (f.ref() != null) {
            RefPicker p = new RefPicker(f.ref(), caption);
            p.addValueChangeListener(e -> setFilter(f.name(), p.getRefId()));
            return p;
        }
        if (f.kind().equals("date") || f.kind().equals("datetime")) {
            DatePicker from = new DatePicker(caption + " ≥");
            DatePicker to = new DatePicker(caption + " ≤");
            from.setI18n(Ui.datePickerI18n());
            to.setI18n(Ui.datePickerI18n());
            from.addValueChangeListener(e -> setFilter(f.name() + "__gte", e.getValue() == null ? null : e.getValue().toString()));
            to.addValueChangeListener(e -> setFilter(f.name() + "__lte", e.getValue() == null ? null
                    : e.getValue() + (f.kind().equals("datetime") ? "T23:59:59" : "")));
            Div box = new Div(from, to);
            box.addClassName("ep-row");
            return box;
        }
        TextField t = new TextField(caption);
        t.setValueChangeMode(ValueChangeMode.LAZY);
        t.setValueChangeTimeout(400);
        t.setClearButtonVisible(true);
        t.addValueChangeListener(e -> setFilter(f.name(), e.getValue()));
        return t;
    }

    private void setFilter(String key, String value) {
        if (value == null || value.isEmpty()) filters.remove(key);
        else filters.put(key, value);
        refresh();
    }

    private void refresh() {
        if (grid != null) grid.getDataProvider().refreshAll();
    }

    private String queryString() {
        List<String> parts = new ArrayList<>();
        Stream.concat(fixed.entrySet().stream(), filters.entrySet().stream())
                .forEach(e -> parts.add(ApiClient.enc(e.getKey()) + "=" + ApiClient.enc(e.getValue())));
        if (!search.isBlank()) parts.add("search=" + ApiClient.enc(search));
        if (sortField != null) parts.add("sort=" + sortField + "&order=" + sortOrder);
        else if (meta.field("createdAt") != null) parts.add("sort=createdAt&order=desc");
        return String.join("&", parts);
    }

    private Stream<ObjectNode> fetch(int offset, int limit) {
        int pageSize = grid.getPageSize();
        int first = offset / pageSize;
        int last = (offset + limit - 1) / pageSize;
        List<ObjectNode> rows = new ArrayList<>();
        try {
            for (int p = first; p <= last; p++) {
                JsonNode res = Ui.api().get("/api/r/" + resource + "?" + queryString() + "&page=" + (p + 1) + "&pageSize=" + pageSize);
                Cells.merge(labels, Ui.labels(res.get("labels")));
                List<ObjectNode> page = Ui.rows(res.get("data"));
                rows.addAll(page);
                total.setText(Ui.L("total") + ": " + res.path("meta").path("total").asInt());
                if (page.size() < pageSize) break;
            }
        } catch (ApiException ex) {
            if (ex.status != 401) Ui.error(ex.getMessage());
            return Stream.empty();
        }
        return rows.stream().skip(offset - (long) first * pageSize).limit(limit);
    }

    private void exportAs(String format) {
        Ui.download("/api/r/" + resource + "/export?format=" + format + "&" + queryString());
    }

    private void create() {
        if (view.createHref != null) {
            Ui.go(view.createHref);
            return;
        }
        if (view.createDialog != null) {
            view.createDialog.accept(this::refresh);
            return;
        }
        if (!meta.children().isEmpty() || meta.draftOnly()) {
            UI.getCurrent().navigate("r/" + resource + "/new", QueryParameters.simple(fixed));
            return;
        }
        FormDialog.open(meta, null, null, fixed, saved -> refresh());
    }

    private void open(ObjectNode row) {
        String key = ApiClient.enc(row.path(meta.keyField()).asText());
        UI.getCurrent().navigate(view.detailHref != null ? view.detailHref.replace("{id}", key) : "r/" + resource + "/" + key);
    }

    private Component actionButtons(ObjectNode row) {
        Div box = new Div();
        box.addClassName("ep-row");
        for (Action a : view.actions) {
            if (!a.visible.test(row) || (a.permission != null && !Session.current().has(a.permission))) continue;
            box.add(Ui.stopClick(Ui.iconButton(a.icon, a.text(Ui.locale()), () -> ActionRunner.run(a, new ActionContext(row, this::refresh)))));
        }
        return box;
    }

    static Map<String, String> query(BeforeEnterEvent event) {
        return event.getLocation().getQueryParameters().getParameters().entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0), (a, b) -> a, LinkedHashMap::new));
    }
}
