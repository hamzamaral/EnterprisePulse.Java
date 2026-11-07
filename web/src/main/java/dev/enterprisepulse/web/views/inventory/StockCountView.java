package dev.enterprisepulse.web.views.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opens a warehouse count and records the counted quantities. */
@Route(value = "inventory/counts/:id", layout = MainLayout.class)
public class StockCountView extends Div implements BeforeEnterObserver {
    private String id;
    private JsonNode count;

    public StockCountView() {
        addClassName("ep-page");
        setSizeFull();
    }

    public static void open(Runnable done) {
        Dialog d = new Dialog();
        d.setHeaderTitle(Ui.T("Stok sayımı", "Stock count"));
        RefPicker warehouse = new RefPicker("warehouses", Ui.T("Depo", "Warehouse"));
        warehouse.setWidthFull();
        DatePicker date = new DatePicker(Ui.T("Tarih", "Date"), LocalDate.now());
        date.setI18n(Ui.datePickerI18n());
        TextArea notes = new TextArea(Ui.T("Not", "Notes"));
        notes.setWidthFull();
        Button save = new Button(Ui.T("Başlat", "Start"), e -> {
            if (warehouse.getValue() == null) {
                Ui.error(Ui.T("Depo seçin", "Choose a warehouse"));
                return;
            }
            if (Ui.run(() -> {
                JsonNode created = Ui.api().post("/api/stock/counts", Map.of("warehouseId", warehouse.getValue().id,
                        "countDate", date.getValue() == null ? LocalDate.now().toString() : date.getValue().toString(),
                        "notes", notes.getValue() == null ? "" : notes.getValue()));
                d.close();
                Ui.go("inventory/counts/" + created.path("id").asText());
            })) if (done != null) done.run();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.add(warehouse, date, notes);
        d.getFooter().add(new Button(Ui.T("Vazgeç", "Cancel"), ev -> d.close()), save);
        d.open();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("");
        removeAll();
        if (!Ui.run(() -> count = Ui.api().get("/api/stock/counts/" + id))) {
            add(Ui.alert(Ui.L("notFound"), "warning"));
            return;
        }
        build();
    }

    private void build() {
        boolean draft = "DRAFT".equals(count.path("status").asText());
        H2 title = new H2(count.path("number").asText() + " · " + count.path("warehouseName").asText());
        Div meta = new Div(Ui.muted(count.path("countDate").asText() + " · " + count.path("countedCount").asInt() + "/" + count.path("lineCount").asInt()));
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("sku").asText()).setHeader("SKU");
        grid.addColumn(n -> n.path("productName").asText()).setHeader(Ui.T("Ürün", "Product")).setFlexGrow(1);
        grid.addColumn(n -> n.path("lotNo").asText("")).setHeader("Lot");
        grid.addColumn(n -> Fmt.number(n.path("expectedQty").decimalValue(), 3, "tr")).setHeader(Ui.T("Beklenen", "Expected"));
        if (draft) {
            grid.addComponentColumn(n -> {
                BigDecimalField field = new BigDecimalField();
                if (!n.path("countedQty").isNull() && !n.path("countedQty").asText().isBlank()) field.setValue(n.path("countedQty").decimalValue());
                field.addValueChangeListener(e -> {
                    if (e.isFromClient()) saveLine(n.path("id").asText(), e.getValue());
                });
                return field;
            }).setHeader(Ui.T("Sayılan", "Counted"));
        } else {
            grid.addColumn(n -> n.path("countedQty").isNull() ? "" : Fmt.number(n.path("countedQty").decimalValue(), 3, "tr")).setHeader(Ui.T("Sayılan", "Counted"));
            grid.addColumn(n -> n.path("difference").isNull() ? "" : Fmt.number(n.path("difference").decimalValue(), 3, "tr")).setHeader(Ui.T("Fark", "Difference"));
        }
        List<JsonNode> lines = new ArrayList<>();
        count.path("lines").forEach(lines::add);
        grid.setItems(lines);
        grid.setAllRowsVisible(true);
        add(title, meta, grid);
        if (!count.path("adjustmentId").asText("").isBlank()) {
            add(new Button(Ui.T("Düzeltme hareketi", "Adjustment") + " " + count.path("adjustmentNumber").asText(),
                    e -> Ui.go("inventory/movements/" + count.path("adjustmentId").asText())));
        }
        if (draft) {
            Button complete = new Button(Ui.T("Sayımı bitir", "Complete"), e -> {
                if (Ui.run(() -> count = Ui.api().post("/api/stock/counts/" + id + "/complete"), Ui.T("Sayım kapandı", "Count completed"))) {
                    removeAll();
                    build();
                }
            });
            complete.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            Button cancel = new Button(Ui.T("İptal", "Cancel"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/stock/counts/" + id + "/cancel"), Ui.T("Sayım iptal edildi", "Count cancelled"))) Ui.go("r/stock-counts");
            });
            add(new Div(complete, cancel));
        }
    }

    private void saveLine(String lineId, BigDecimal qty) {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", lineId);
        row.put("countedQty", qty);
        Ui.run(() -> Ui.api().put("/api/stock/counts/" + id + "/lines", List.of(row)));
    }
}
