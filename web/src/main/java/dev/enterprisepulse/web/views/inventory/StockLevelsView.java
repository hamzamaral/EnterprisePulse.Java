package dev.enterprisepulse.web.views.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Company or warehouse stock: quantity, value and items under their minimum. */
@Route(value = "inventory", layout = MainLayout.class)
public class StockLevelsView extends Div implements BeforeEnterObserver {
    private String warehouseId;
    private boolean lowOnly;
    private String search = "";
    private final Div stats = new Div();
    private final Grid<JsonNode> grid = new Grid<>();

    public StockLevelsView() {
        addClassName("ep-page");
        setSizeFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        QueryParameters q = event.getLocation().getQueryParameters();
        warehouseId = q.getSingleParameter("warehouse").orElse(null);
        lowOnly = q.getSingleParameter("low").orElse("").equals("1");
        search = q.getSingleParameter("search").orElse("");
        removeAll();
        build();
        load();
    }

    private void build() {
        H2 title = new H2(Ui.T("Stok durumu", "Stock levels"));
        stats.addClassName("ep-stat-row");
        TextField searchField = new TextField();
        searchField.setPlaceholder(Ui.T("Kod, ad veya barkod", "Code, name or barcode"));
        searchField.setValue(search);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.setValueChangeTimeout(300);
        searchField.addValueChangeListener(e -> {
            search = e.getValue() == null ? "" : e.getValue();
            load();
        });
        Checkbox low = new Checkbox(Ui.T("Yalnızca minimum altı", "Below minimum only"), lowOnly);
        low.addValueChangeListener(e -> {
            lowOnly = Boolean.TRUE.equals(e.getValue());
            load();
        });
        Select<String> warehouse = new Select<>();
        warehouse.setLabel(Ui.T("Depo", "Warehouse"));
        warehouse.setEmptySelectionAllowed(true);
        warehouse.setEmptySelectionCaption(Ui.T("Tüm depolar", "All warehouses"));
        List<String> ids = new ArrayList<>();
        Map<String, String> names = new java.util.LinkedHashMap<>();
        JsonNode rows = Ui.api().get("/api/r/warehouses?pageSize=50");
        if (rows != null) for (JsonNode w : rows.path("data")) {
            ids.add(w.path("id").asText());
            names.put(w.path("id").asText(), w.path("name").asText());
        }
        warehouse.setItems(ids);
        warehouse.setItemLabelGenerator(id -> id == null ? Ui.T("Tüm depolar", "All warehouses") : names.getOrDefault(id, id));
        if (warehouseId != null && names.containsKey(warehouseId)) warehouse.setValue(warehouseId);
        warehouse.addValueChangeListener(e -> {
            warehouseId = e.getValue();
            load();
        });
        HorizontalLayout filters = new HorizontalLayout(searchField, warehouse, low);
        filters.setAlignItems(FlexComponent.Alignment.END);
        filters.setWidthFull();
        grid.addColumn(n -> n.path("sku").asText()).setHeader("SKU").setAutoWidth(true);
        grid.addColumn(n -> n.path("productName").asText() + (n.path("variantName").asText("").isBlank() ? "" : " (" + n.path("variantName").asText() + ")")).setHeader(Ui.T("Ürün", "Product")).setFlexGrow(1);
        grid.addColumn(n -> n.path("categoryName").asText("")).setHeader(Ui.T("Kategori", "Category"));
        grid.addColumn(n -> Fmt.number(n.path("quantity").decimalValue(), 3, "tr")).setHeader(Ui.T("Miktar", "Qty"));
        grid.addColumn(n -> Fmt.money(n.path("stockValue").decimalValue(), "TRY", "tr")).setHeader(Ui.T("Değer", "Value"));
        grid.addColumn(n -> n.path("lowStock").asBoolean() ? Ui.T("Minimum altı", "Low") : "").setHeader("");
        grid.setAllRowsVisible(true);
        grid.addItemClickListener(e -> Ui.go("r/products/" + e.getItem().path("productId").asText()));
        add(title, stats, filters, grid);
    }

    private void load() {
        JsonNode summary = Ui.api().get("/api/stock/summary");
        stats.removeAll();
        if (summary != null) {
            stats.add(Ui.stat(Ui.T("Stok değeri", "Stock value"), Fmt.money(summary.path("stockValue").decimalValue(), "TRY", "tr")));
            stats.add(Ui.stat(Ui.T("Kalem", "Items"), String.valueOf(summary.path("itemCount").asInt())));
            stats.add(Ui.stat(Ui.T("Depo", "Warehouses"), String.valueOf(summary.path("warehouseCount").asInt())));
            stats.add(Ui.stat(Ui.T("Minimum altı", "Below minimum"), String.valueOf(summary.path("lowStockCount").asInt())));
        }
        String url = "/api/stock/levels?pageSize=200" + (warehouseId == null || warehouseId.isBlank() ? "" : "&warehouseId=" + warehouseId)
                + (lowOnly ? "&lowStock=true" : "") + (search.isBlank() ? "" : "&search=" + java.net.URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8));
        JsonNode page = Ui.api().get(url);
        List<JsonNode> items = new ArrayList<>();
        if (page != null) page.path("data").forEach(items::add);
        grid.setItems(items);
    }
}
