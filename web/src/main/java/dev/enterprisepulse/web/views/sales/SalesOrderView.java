package dev.enterprisepulse.web.views.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Route(value = "sales/orders/new", layout = MainLayout.class)
@RouteAlias(value = "sales/orders/:id", layout = MainLayout.class)
public class SalesOrderView extends Div implements BeforeEnterObserver {
    private String id;

    public SalesOrderView() { addClassName("ep-page"); setSizeFull(); }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("new");
        removeAll();
        if ("new".equals(id)) add(new H2(Ui.T("Yeni satış siparişi", "New sales order")));
        else detail();
    }

    private void detail() {
        JsonNode o = Ui.api().get("/api/sales/orders/" + id);
        if (o == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(o.path("number").asText() + " · " + o.path("partnerTitle").asText()));
        add(new Div(Ui.muted(o.path("status").asText() + " · " + o.path("warehouseName").asText() + " · "
                + Fmt.money(o.path("totals").path("grandTotal").decimalValue(), o.path("currencyCode").asText(), "tr")
                + " · %" + o.path("deliveredPercent").asInt())));
        add(lines(o.path("lines"), true));
        Div actions = new Div();
        if ("DRAFT".equals(o.path("status").asText())) {
            Button approve = new Button(Ui.T("Onayla", "Approve"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/sales/orders/" + id + "/approve"), Ui.T("Sipariş onaylandı", "Order approved"))) reload();
            });
            approve.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(approve);
        }
        if (o.path("canDeliver").asBoolean()) actions.add(new Button(Ui.T("Sevk et", "Deliver"), e -> Ui.go("sales/deliveries/new?order=" + id)));
        if (o.path("canInvoice").asBoolean()) actions.add(new Button(Ui.T("Fatura", "Invoice"), e -> Ui.go("sales/invoices/new?order=" + id)));
        if (o.path("canCancel").asBoolean()) actions.add(new Button(Ui.T("İptal", "Cancel"), e -> {
            if (Ui.run(() -> Ui.api().post("/api/sales/orders/" + id + "/cancel", Map.of("reason", "İptal")))) reload();
        }));
        add(actions);
    }

    static Grid<JsonNode> lines(JsonNode array, boolean progress) {
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("sku").asText("")).setHeader("SKU");
        grid.addColumn(n -> n.path("description").asText()).setHeader(Ui.T("Açıklama", "Description")).setFlexGrow(1);
        grid.addColumn(n -> Fmt.number(n.path("quantity").decimalValue(), 3, "tr")).setHeader(Ui.T("Miktar", "Qty"));
        if (progress) {
            grid.addColumn(n -> n.has("deliveredQty") ? Fmt.number(n.path("deliveredQty").decimalValue(), 3, "tr") : "").setHeader(Ui.T("Sevk", "Delivered"));
            grid.addColumn(n -> n.has("invoicedQty") ? Fmt.number(n.path("invoicedQty").decimalValue(), 3, "tr") : "").setHeader(Ui.T("Fatura", "Invoiced"));
        }
        grid.addColumn(n -> n.has("lineTotal") ? Fmt.money(n.path("lineTotal").decimalValue(), "TRY", "tr") : "").setHeader(Ui.T("Tutar", "Amount"));
        List<JsonNode> rows = new ArrayList<>();
        array.forEach(rows::add);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        return grid;
    }

    private void reload() { removeAll(); detail(); }
}
