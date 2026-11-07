package dev.enterprisepulse.web.views.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vaadin.flow.component.UI;
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

@Route(value = "support/tickets/:id", layout = MainLayout.class)
class TicketView extends Div implements BeforeEnterObserver {
    TicketView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        JsonNode row = Ui.api().get("/api/support/tickets/" + event.getRouteParameters().get("id").orElse(""));
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("subject").asText()));
        add(Ui.badge(row.path("status").asText(), Fmt.statusColor(row.path("status").asText())));
        add(new Div(Ui.muted(row.path("number").asText() + " · " + row.path("requesterName").asText() + " · "
                + (row.path("assigneeName").isNull() || row.path("assigneeName").isMissingNode() ? Ui.T("Atanmamış", "Unassigned") : row.path("assigneeName").asText()))));
        add(new Div(row.path("description").asText()));
        add(new Div(Ui.T("SLA", "SLA") + " " + row.path("responseSla").asText() + " / " + row.path("resolutionSla").asText()));
        add(OpsUi.grid(row.path("comments"), Ui.T("Yazan", "Author"), "authorName", Ui.T("Mesaj", "Message"), "body"));
    }
}

@Route(value = "support/my", layout = MainLayout.class)
class MyTicketsView extends Div implements BeforeEnterObserver {
    MyTicketsView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Destek Taleplerim", "My tickets")));
        JsonNode row = Ui.api().get("/api/support/mine");
        JsonNode items = row == null ? JsonNodeFactory.instance.arrayNode() : row.path("items");
        if (items.size() == 0) { add(Ui.muted(Ui.T("Talebiniz yok.", "You have no tickets."))); return; }
        Grid<JsonNode> grid = OpsUi.grid(items, Ui.T("Numara", "Number"), "number", Ui.T("Konu", "Subject"), "subject", Ui.T("Durum", "Status"), "status");
        grid.addItemClickListener(e -> UI.getCurrent().navigate("support/tickets/" + e.getItem().path("id").asText()));
        add(grid);
    }
}

@Route(value = "contracts/:id", layout = MainLayout.class)
class ContractView extends Div implements BeforeEnterObserver {
    ContractView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        JsonNode row = Ui.api().get("/api/contracts/" + event.getRouteParameters().get("id").orElse(""));
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("title").asText()));
        add(Ui.badge(row.path("state").asText(), Fmt.statusColor(row.path("status").asText())));
        String counterparty = OpsUi.text(row, "partnerTitle");
        if (counterparty.isEmpty()) counterparty = OpsUi.text(row, "employeeName");
        add(new Div(Ui.muted(row.path("number").asText() + " · " + row.path("typeName").asText() + " · " + (counterparty.isEmpty() ? "—" : counterparty))));
        String amount = row.path("amount").isNull() || row.path("amount").isMissingNode() ? ""
                : " · " + Fmt.money(row.path("amount").decimalValue(), row.path("currencyCode").asText(), Ui.locale());
        add(new Div(Fmt.date(row.path("startDate")) + " → " + (OpsUi.text(row, "endDate").isEmpty() ? "—" : Fmt.date(row.path("endDate"))) + amount));
    }
}

@Route(value = "budgets/:id", layout = MainLayout.class)
class BudgetView extends Div implements BeforeEnterObserver {
    BudgetView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        JsonNode row = Ui.api().get("/api/budgets/" + event.getRouteParameters().get("id").orElse(""));
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("name").asText()));
        add(Ui.badge(row.path("status").asText(), Fmt.statusColor(row.path("status").asText())));
        add(new Div(row.path("year").asText()
                + " · " + Ui.T("Gelir plan", "Income plan") + " " + money(row, "plannedIncome")
                + " · " + Ui.T("Gider plan", "Expense plan") + " " + money(row, "plannedExpense")));
        add(OpsUi.grid(row.path("rows"), Ui.T("Kalem", "Item"), "name", Ui.T("Plan YTD", "Plan YTD"), "plannedYtd",
                Ui.T("Gerçek", "Actual"), "actualYtd", Ui.T("Durum", "State"), "state"));
    }
    private static String money(JsonNode row, String field) { return Fmt.number(row.path(field).decimalValue(), 2, Ui.locale()); }
}

@Route(value = "fleet/vehicles/:id", layout = MainLayout.class)
class VehicleView extends Div implements BeforeEnterObserver {
    VehicleView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        JsonNode row = Ui.api().get("/api/fleet/vehicles/" + event.getRouteParameters().get("id").orElse(""));
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("plate").asText()));
        add(Ui.badge(row.path("status").asText(), Fmt.statusColor(row.path("status").asText())));
        add(new Div(row.path("brand").asText() + " " + row.path("model").asText() + " · " + row.path("odometer").asText() + " km"));
        String driver = OpsUi.text(row, "driverName");
        add(new Div(Ui.T("Sürücü", "Driver") + " " + (driver.isEmpty() ? "—" : driver) + " · " + Ui.T("Bakım", "Service") + " " + row.path("serviceState").asText()));
        add(new Div(Ui.T("Belge uyarısı", "Document warnings") + " " + row.path("openDocumentWarnings").asText()));
    }
}

@Route(value = "fleet/my", layout = MainLayout.class)
class MyVehiclesView extends Div implements BeforeEnterObserver {
    MyVehiclesView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Araçlarım", "My vehicles")));
        JsonNode row = Ui.api().get("/api/fleet/mine");
        JsonNode items = row == null ? JsonNodeFactory.instance.arrayNode() : row.path("items");
        if (items.size() == 0) { add(Ui.muted(Ui.T("Zimmetli aracınız yok.", "You have no assigned vehicle."))); return; }
        Grid<JsonNode> grid = OpsUi.grid(items, Ui.T("Plaka", "Plate"), "plate", Ui.T("Marka", "Brand"), "brand", Ui.T("Model", "Model"), "model", "Km", "odometer");
        grid.addItemClickListener(e -> UI.getCurrent().navigate("fleet/vehicles/" + e.getItem().path("id").asText()));
        add(grid);
    }
}

final class OpsUi {
    private OpsUi() {}

    /** Columns come in header/key pairs. */
    static Grid<JsonNode> grid(JsonNode rows, String... headerKeyPairs) {
        Grid<JsonNode> grid = new Grid<>();
        for (int i = 0; i + 1 < headerKeyPairs.length; i += 2) {
            String key = headerKeyPairs[i + 1];
            grid.addColumn(n -> n.path(key).asText()).setHeader(headerKeyPairs[i]);
        }
        List<JsonNode> items = new ArrayList<>();
        if (rows != null) rows.forEach(items::add);
        grid.setItems(items);
        grid.setAllRowsVisible(true);
        return grid;
    }

    static String text(JsonNode row, String field) {
        JsonNode n = row.path(field);
        return n.isMissingNode() || n.isNull() ? "" : n.asText();
    }
}
