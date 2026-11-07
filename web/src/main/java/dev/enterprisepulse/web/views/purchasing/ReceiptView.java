package dev.enterprisepulse.web.views.purchasing;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Goods receipt against the remaining quantity of an approved order. */
@Route(value = "purchasing/receipts/new", layout = MainLayout.class)
@RouteAlias(value = "purchasing/receipts/:id", layout = MainLayout.class)
public class ReceiptView extends Div implements BeforeEnterObserver {
    private String id;
    private String orderId;

    public ReceiptView() {
        addClassName("ep-page");
        setSizeFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("new");
        orderId = event.getLocation().getQueryParameters().getSingleParameter("order").orElse(null);
        removeAll();
        if ("new".equals(id)) form();
        else detail();
    }

    private void form() {
        H2 title = new H2(Ui.T("Mal kabul", "Goods receipt"));
        JsonNode open = Ui.api().get("/api/purchasing/orders/open");
        if ((orderId == null || orderId.isBlank()) && open != null && open.size() > 0) orderId = open.get(0).path("id").asText();
        JsonNode order = orderId == null ? null : Ui.api().get("/api/purchasing/orders/" + orderId);
        if (order == null) {
            add(title, Ui.alert(Ui.T("Teslim alınacak sipariş yok", "No order is waiting for a receipt"), "warning"));
            return;
        }
        Div meta = new Div(Ui.muted(order.path("number").asText() + " · " + order.path("partnerTitle").asText()));
        DatePicker date = new DatePicker(Ui.T("Tarih", "Date"), LocalDate.now());
        date.setI18n(Ui.datePickerI18n());
        TextField noteNo = new TextField(Ui.T("İrsaliye no", "Delivery note"));
        List<JsonNode> pending = new ArrayList<>();
        Map<String, BigDecimalField> qty = new LinkedHashMap<>();
        Map<String, TextField> lots = new LinkedHashMap<>();
        for (JsonNode line : order.path("lines")) {
            if (!line.path("stocked").asBoolean() || line.path("remainingQty").decimalValue().signum() <= 0) continue;
            pending.add(line);
            BigDecimalField field = new BigDecimalField();
            field.setValue(line.path("remainingQty").decimalValue());
            qty.put(line.path("id").asText(), field);
            TextField lot = new TextField();
            lots.put(line.path("id").asText(), lot);
        }
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("sku").asText()).setHeader("SKU").setFlexGrow(1);
        grid.addColumn(n -> Fmt.number(n.path("remainingQty").decimalValue(), 3, "tr")).setHeader(Ui.T("Kalan", "Remaining"));
        grid.addComponentColumn(n -> qty.get(n.path("id").asText())).setHeader(Ui.T("Kabul", "Receive"));
        grid.addComponentColumn(n -> lots.get(n.path("id").asText())).setHeader("Lot");
        grid.setItems(pending);
        grid.setAllRowsVisible(true);
        Button save = new Button(Ui.T("Kaydet", "Save"), e -> {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (JsonNode line : pending) {
                BigDecimal amount = qty.get(line.path("id").asText()).getValue();
                if (amount == null || amount.signum() <= 0) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orderLineId", line.path("id").asText());
                row.put("quantity", amount);
                String lot = lots.get(line.path("id").asText()).getValue();
                if (lot != null && !lot.isBlank()) row.put("lotNo", lot.trim());
                lines.add(row);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orderId", order.path("id").asText());
            body.put("warehouseId", order.path("warehouseId").asText());
            body.put("receiptDate", date.getValue().toString());
            body.put("deliveryNoteNo", noteNo.getValue());
            body.put("lines", lines);
            if (Ui.run(() -> id = Ui.api().post("/api/purchasing/receipts", body).path("id").asText(), Ui.T("Mal kabul kaydedildi", "Receipt posted"))) {
                Ui.go("purchasing/receipts/" + id);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(title, meta, date, noteNo, grid, save);
    }

    private void detail() {
        JsonNode r = Ui.api().get("/api/purchasing/receipts/" + id);
        if (r == null) {
            add(Ui.alert(Ui.L("notFound"), "warning"));
            return;
        }
        H2 title = new H2(r.path("number").asText() + " · " + r.path("partnerTitle").asText());
        Div meta = new Div(Ui.muted(r.path("receiptDate").asText() + " · " + r.path("orderNumber").asText() + " · " + r.path("deliveryNoteNo").asText("")));
        if (!r.path("stockMovementId").asText("").isBlank()) {
            meta.add(new Button(Ui.T("Stok hareketi", "Stock movement") + " " + r.path("stockMovementNumber").asText(),
                    e -> Ui.go("inventory/movements/" + r.path("stockMovementId").asText())));
        }
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("sku").asText()).setHeader("SKU");
        grid.addColumn(n -> n.path("description").asText()).setHeader(Ui.T("Açıklama", "Description")).setFlexGrow(1);
        grid.addColumn(n -> Fmt.number(n.path("quantity").decimalValue(), 3, "tr")).setHeader(Ui.T("Miktar", "Qty"));
        grid.addColumn(n -> n.path("lotNo").asText("")).setHeader("Lot");
        grid.addColumn(n -> n.path("serialNos").asText("")).setHeader(Ui.T("Seri", "Serial"));
        List<JsonNode> lines = new ArrayList<>();
        r.path("lines").forEach(lines::add);
        grid.setItems(lines);
        grid.setAllRowsVisible(true);
        add(title, meta, new Button(Ui.T("Sipariş", "Order"), e -> Ui.go("purchasing/orders/" + r.path("orderId").asText())), grid);
    }
}
