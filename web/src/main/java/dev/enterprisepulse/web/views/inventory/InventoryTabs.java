package dev.enterprisepulse.web.views.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.ResourceViews.TabContext;
import dev.enterprisepulse.web.ui.Ui;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Product and warehouse tabs: variants, on-hand stock and the stock card. */
public final class InventoryTabs {
    private InventoryTabs() {}

    public static Div variants(TabContext ctx) {
        String id = ctx.record().path("id").asText();
        JsonNode product = Ui.api().get("/api/stock/products/" + id);
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("sku").asText()).setHeader("SKU");
        grid.addColumn(n -> n.path("name").asText("")).setHeader(Ui.T("Varyant", "Variant")).setFlexGrow(1);
        grid.addColumn(n -> Fmt.number(n.path("onHand").decimalValue(), 3, "tr")).setHeader(Ui.T("Eldeki", "On hand"));
        grid.addColumn(n -> Fmt.number(n.path("minStock").decimalValue(), 3, "tr")).setHeader(Ui.T("Minimum", "Minimum"));
        grid.addColumn(n -> n.path("lowStock").asBoolean() ? Ui.T("Altında", "Low") : "").setHeader("");
        List<JsonNode> rows = new ArrayList<>();
        if (product != null) product.path("variants").forEach(rows::add);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        Button add = new Button(Ui.T("Varyant ekle", "Add variant"), e -> addVariant(id, ctx));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div box = new Div(add, grid);
        box.addClassName("ep-stack");
        return box;
    }

    public static Div stock(TabContext ctx) {
        JsonNode product = Ui.api().get("/api/stock/products/" + ctx.record().path("id").asText());
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("warehouseName").asText()).setHeader(Ui.T("Depo", "Warehouse")).setFlexGrow(1);
        grid.addColumn(n -> n.path("sku").asText()).setHeader("SKU");
        grid.addColumn(n -> Fmt.number(n.path("quantity").decimalValue(), 3, "tr")).setHeader(Ui.T("Miktar", "Qty"));
        List<JsonNode> rows = new ArrayList<>();
        if (product != null) product.path("stock").forEach(rows::add);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        return new Div(grid);
    }

    public static Div ledger(TabContext ctx) {
        JsonNode page = Ui.api().get("/api/stock/ledger?productId=" + ctx.record().path("id").asText() + "&pageSize=100");
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("movementDate").asText()).setHeader(Ui.T("Tarih", "Date"));
        grid.addColumn(n -> n.path("movementNumber").asText()).setHeader(Ui.T("Hareket", "Movement"));
        grid.addColumn(n -> n.path("warehouseName").asText()).setHeader(Ui.T("Depo", "Warehouse"));
        grid.addColumn(n -> n.path("sku").asText()).setHeader("SKU");
        grid.addColumn(n -> Fmt.number(n.path("quantity").decimalValue(), 3, "tr")).setHeader(Ui.T("Miktar", "Qty"));
        grid.addColumn(n -> Fmt.number(n.path("balanceAfter").decimalValue(), 3, "tr")).setHeader(Ui.T("Bakiye", "Balance"));
        List<JsonNode> rows = new ArrayList<>();
        if (page != null) page.path("data").forEach(rows::add);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        grid.addItemClickListener(e -> Ui.go("inventory/movements/" + e.getItem().path("movementId").asText()));
        return new Div(grid);
    }

    public static Div warehouse(TabContext ctx) {
        JsonNode page = Ui.api().get("/api/stock/levels?warehouseId=" + ctx.record().path("id").asText() + "&pageSize=200");
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("sku").asText()).setHeader("SKU");
        grid.addColumn(n -> n.path("productName").asText()).setHeader(Ui.T("Ürün", "Product")).setFlexGrow(1);
        grid.addColumn(n -> Fmt.number(n.path("quantity").decimalValue(), 3, "tr")).setHeader(Ui.T("Miktar", "Qty"));
        grid.addColumn(n -> Fmt.money(n.path("stockValue").decimalValue(), "TRY", "tr")).setHeader(Ui.T("Değer", "Value"));
        List<JsonNode> rows = new ArrayList<>();
        if (page != null) page.path("data").forEach(rows::add);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        return new Div(grid);
    }

    private static void addVariant(String productId, TabContext ctx) {
        Dialog d = new Dialog();
        d.setHeaderTitle(Ui.T("Yeni varyant", "New variant"));
        TextField sku = new TextField("SKU");
        sku.setWidthFull();
        TextField name = new TextField(Ui.T("Ad", "Name"));
        name.setWidthFull();
        BigDecimalField min = new BigDecimalField(Ui.T("Minimum", "Minimum"));
        min.setValue(BigDecimal.ZERO);
        Button save = new Button(Ui.T("Kaydet", "Save"), e -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sku", sku.getValue());
            body.put("name", name.getValue());
            body.put("minStock", min.getValue());
            if (Ui.run(() -> Ui.api().post("/api/stock/products/" + productId + "/variants", body), Ui.T("Varyant eklendi", "Variant added"))) {
                d.close();
                ctx.onChanged().run();
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.add(sku, name, min);
        d.getFooter().add(new Button(Ui.T("Vazgeç", "Cancel"), ev -> d.close()), save);
        d.open();
    }
}
