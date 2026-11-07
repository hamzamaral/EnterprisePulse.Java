package dev.enterprisepulse.web.views.purchasing;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Draft purchase order form and the order card (approve, receive, invoice). */
@Route(value = "purchasing/orders/new", layout = MainLayout.class)
@RouteAlias(value = "purchasing/orders/:id", layout = MainLayout.class)
public class OrderView extends Div implements BeforeEnterObserver {
    private String id;

    public OrderView() {
        addClassName("ep-page");
        setSizeFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("new");
        removeAll();
        if ("new".equals(id)) form();
        else detail();
    }

    private void form() {
        H2 title = new H2(Ui.T("Yeni satın alma siparişi", "New purchase order"));
        RefPicker partner = new RefPicker("business-partners", Ui.T("Tedarikçi", "Supplier"));
        partner.setFilters(Map.of("type__in", "SUPPLIER,BOTH"));
        RefPicker warehouse = new RefPicker("warehouses", Ui.T("Depo", "Warehouse"));
        DatePicker date = new DatePicker(Ui.T("Tarih", "Date"), LocalDate.now());
        date.setI18n(Ui.datePickerI18n());
        DatePicker expected = new DatePicker(Ui.T("Beklenen", "Expected"), LocalDate.now().plusDays(7));
        expected.setI18n(Ui.datePickerI18n());
        TextField currency = new TextField(Ui.T("Para birimi", "Currency"), "TRY");
        TextField sku = new TextField("SKU");
        BigDecimalField qty = new BigDecimalField(Ui.T("Miktar", "Qty"));
        qty.setValue(BigDecimal.ONE);
        BigDecimalField price = new BigDecimalField(Ui.T("Birim fiyat", "Unit price"));
        BigDecimalField vat = new BigDecimalField("KDV %");
        vat.setValue(new BigDecimal("20"));
        TextArea notes = new TextArea(Ui.T("Not", "Notes"));
        Button save = new Button(Ui.T("Taslak kaydet", "Save draft"), e -> {
            if (partner.getValue() == null || warehouse.getValue() == null) {
                Ui.error(Ui.T("Tedarikçi ve depo gerekli", "Supplier and warehouse are required"));
                return;
            }
            JsonNode items = Ui.api().get("/api/stock/items?includeServices=true&search=" + java.net.URLEncoder.encode(sku.getValue() == null ? "" : sku.getValue().trim(), java.nio.charset.StandardCharsets.UTF_8));
            if (items == null || items.isEmpty()) {
                Ui.error(Ui.T("Ürün bulunamadı", "Item not found"));
                return;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("variantId", items.get(0).path("variantId").asText());
            line.put("quantity", qty.getValue());
            line.put("unitPrice", price.getValue() == null ? BigDecimal.ZERO : price.getValue());
            line.put("vatRate", vat.getValue() == null ? BigDecimal.ZERO : vat.getValue());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("partnerId", partner.getValue().id);
            body.put("warehouseId", warehouse.getValue().id);
            body.put("orderDate", date.getValue().toString());
            body.put("expectedDate", expected.getValue() == null ? null : expected.getValue().toString());
            body.put("currencyCode", currency.getValue());
            body.put("notes", notes.getValue());
            body.put("lines", List.of(line));
            if (Ui.run(() -> id = Ui.api().post("/api/purchasing/orders", body).path("id").asText(), Ui.T("Sipariş kaydedildi", "Order saved"))) {
                Ui.go("purchasing/orders/" + id);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div box = new Div(partner, warehouse, date, expected, currency, sku, qty, price, vat, notes, save);
        box.addClassName("ep-stack");
        add(title, box);
    }

    private void detail() {
        JsonNode o = Ui.api().get("/api/purchasing/orders/" + id);
        if (o == null) {
            add(Ui.alert(Ui.L("notFound"), "warning"));
            return;
        }
        H2 title = new H2(o.path("number").asText() + " · " + o.path("partnerTitle").asText());
        Div meta = new Div(Ui.muted(o.path("status").asText() + " · " + o.path("warehouseName").asText() + " · "
                + Fmt.money(o.path("totals").path("grandTotal").decimalValue(), o.path("currencyCode").asText(), "tr")
                + " · " + o.path("receivedPercent").asInt() + "%"));
        Grid<JsonNode> grid = lines(o.path("lines"));
        add(title, meta, grid);
        Div actions = new Div();
        if ("DRAFT".equals(o.path("status").asText())) {
            Button approve = new Button(Ui.T("Onayla", "Approve"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/purchasing/orders/" + id + "/approve"), Ui.T("Sipariş onaylandı", "Order approved"))) reload();
            });
            approve.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(approve);
        }
        if (o.path("canReceive").asBoolean()) actions.add(new Button(Ui.T("Mal kabul", "Receive"), e -> Ui.go("purchasing/receipts/new?order=" + id)));
        if (o.path("canInvoice").asBoolean()) actions.add(new Button(Ui.T("Fatura", "Invoice"), e -> Ui.go("purchasing/invoices/new?order=" + id)));
        if (o.path("canCancel").asBoolean()) actions.add(new Button(Ui.T("İptal", "Cancel"), e -> {
            if (Ui.run(() -> Ui.api().post("/api/purchasing/orders/" + id + "/cancel", Map.of("reason", "İptal")), Ui.T("Sipariş iptal edildi", "Order cancelled"))) reload();
        }));
        add(actions);
    }

    static Grid<JsonNode> lines(JsonNode array) {
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("sku").asText("")).setHeader("SKU");
        grid.addColumn(n -> n.path("description").asText()).setHeader(Ui.T("Açıklama", "Description")).setFlexGrow(1);
        grid.addColumn(n -> Fmt.number(n.path("quantity").decimalValue(), 3, "tr")).setHeader(Ui.T("Miktar", "Qty"));
        grid.addColumn(n -> n.has("receivedQty") ? Fmt.number(n.path("receivedQty").decimalValue(), 3, "tr") : "").setHeader(Ui.T("Teslim", "Received"));
        grid.addColumn(n -> n.has("invoicedQty") ? Fmt.number(n.path("invoicedQty").decimalValue(), 3, "tr") : "").setHeader(Ui.T("Faturalanan", "Invoiced"));
        grid.addColumn(n -> Fmt.money(n.path("lineTotal").decimalValue(), "TRY", "tr")).setHeader(Ui.T("Tutar", "Amount"));
        List<JsonNode> rows = new ArrayList<>();
        array.forEach(rows::add);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        return grid;
    }

    private void reload() {
        removeAll();
        detail();
    }
}
