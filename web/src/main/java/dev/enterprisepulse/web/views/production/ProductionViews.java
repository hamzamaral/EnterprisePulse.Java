package dev.enterprisepulse.web.views.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.List;

@Route(value = "production/requirements", layout = MainLayout.class)
class RequirementsView extends Div implements BeforeEnterObserver {
    RequirementsView() { addClassName("ep-page"); setSizeFull(); }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Malzeme ihtiyacı", "Material requirements")));
        JsonNode summary = Ui.api().get("/api/production/summary");
        if (summary != null) {
            add(new Div(Ui.muted(Ui.T("Aktif reçete", "Active BOMs") + " " + summary.path("activeBoms").asText()
                    + " · " + Ui.T("Taslak", "Draft") + " " + summary.path("draft").asText()
                    + " · " + Ui.T("Serbest", "Released") + " " + summary.path("released").asText()
                    + " · " + Ui.T("Üretimde", "In progress") + " " + summary.path("inProgress").asText()
                    + " · " + Ui.T("Eksik", "Shortages") + " " + summary.path("shortages").asText())));
        }
        JsonNode rows = Ui.api().get("/api/production/requirements");
        Grid<JsonNode> grid = ProductionUi.grid(rows,
                Ui.T("SKU", "SKU"), "variant.sku",
                Ui.T("Üretim", "Production"), "productionDemand",
                Ui.T("Satış", "Sales"), "salesDemand",
                Ui.T("Stok", "On hand"), "onHand",
                Ui.T("Eksik", "Shortage"), "shortage");
        add(grid);
    }
}

@Route(value = "production/boms/:id", layout = MainLayout.class)
class BomDetailView extends Div implements BeforeEnterObserver {
    BomDetailView() { addClassName("ep-page"); setSizeFull(); }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        String id = event.getRouteParameters().get("id").orElse("");
        JsonNode row = Ui.api().get("/api/production/boms/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("code").asText() + " · " + row.path("name").asText()));
        add(new Div(Ui.muted(row.path("variant").path("sku").asText() + " · " + Ui.T("Birim maliyet", "Unit cost") + " "
                + Fmt.number(row.path("unitCost").decimalValue(), 4, Ui.locale())
                + " · " + (row.path("isDefault").asBoolean() ? Ui.T("Varsayılan", "Default") : ""))));
        add(ProductionUi.grid(row.path("lines"), Ui.T("SKU", "SKU"), "variant.sku", Ui.T("Miktar", "Qty"), "quantity",
                Ui.T("Fire %", "Scrap %"), "scrapRate", Ui.T("Birim", "Per unit"), "quantityPer"));
    }
}

@Route(value = "production/work-orders/:id", layout = MainLayout.class)
class WorkOrderDetailView extends Div implements BeforeEnterObserver {
    WorkOrderDetailView() { addClassName("ep-page"); setSizeFull(); }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        String id = event.getRouteParameters().get("id").orElse("");
        JsonNode row = Ui.api().get("/api/production/work-orders/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        JsonNode order = row.path("order");
        add(new H2(order.path("number").asText()));
        add(Ui.badge(order.path("status").asText(), Fmt.statusColor(order.path("status").asText())));
        add(new Div(Ui.muted(order.path("variant").path("sku").asText() + " · " + order.path("producedQty").asText() + " / "
                + order.path("quantity").asText() + " · %" + order.path("progress").asText())));
        add(ProductionUi.grid(row.path("components"), Ui.T("SKU", "SKU"), "variant.sku", Ui.T("Gerekli", "Required"), "requiredQty",
                Ui.T("Sarf", "Consumed"), "consumedQty", Ui.T("Stok", "On hand"), "onHand"));
        add(ProductionUi.grid(row.path("records"), Ui.T("Fiş", "Record"), "number", Ui.T("Tarih", "Date"), "recordDate",
                Ui.T("Miktar", "Qty"), "quantity", Ui.T("Birim maliyet", "Unit cost"), "unitCost"));
    }
}

final class ProductionUi {
    private ProductionUi() {}

    static Grid<JsonNode> grid(JsonNode rows, String... headerKeyPairs) {
        Grid<JsonNode> grid = new Grid<>();
        for (int i = 0; i + 1 < headerKeyPairs.length; i += 2) {
            String key = headerKeyPairs[i + 1];
            grid.addColumn(n -> path(n, key)).setHeader(headerKeyPairs[i]);
        }
        List<JsonNode> items = new ArrayList<>();
        if (rows != null) rows.forEach(items::add);
        grid.setItems(items);
        grid.setAllRowsVisible(true);
        return grid;
    }

    private static String path(JsonNode n, String key) {
        JsonNode cur = n;
        for (String part : key.split("\\.")) cur = cur.path(part);
        return cur.isMissingNode() || cur.isNull() ? "" : cur.asText();
    }
}
