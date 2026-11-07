package dev.enterprisepulse.web.views.work;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Route(value = "projects/:id", layout = MainLayout.class)
class ProjectView extends Div implements BeforeEnterObserver {
    private String id;
    ProjectView() { addClassName("ep-page"); setSizeFull(); }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("");
        removeAll();
        JsonNode row = Ui.api().get("/api/projects/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("name").asText()));
        add(new Span(row.path("status").asText()));
        add(new Div(Ui.muted(row.path("number").asText() + " · " + row.path("managerName").asText() + " · " + row.path("partnerTitle").asText())));
        add(new Div(Ui.T("Bütçe", "Budget") + " " + money(row, "budgetAmount")
                + " · " + Ui.T("İşçilik", "Labour") + " " + money(row, "labourCost")
                + " · " + Ui.T("Masraf", "Expenses") + " " + money(row, "expenseCost")
                + " · " + Ui.T("Kalan", "Left") + " " + money(row, "remaining")));
        add(grid(row.path("tasks"), "taskNo", "title", "status", "assigneeName"));
        add(grid(row.path("time"), "workDate", "employeeName", "hours", "costAmount"));
    }

    private static String money(JsonNode row, String field) {
        return row.path(field).isMissingNode() || row.path(field).isNull() ? "—" : Fmt.number(row.path(field).decimalValue(), 2, "tr");
    }

    static Grid<JsonNode> grid(JsonNode rows, String... keys) {
        Grid<JsonNode> grid = new Grid<>();
        for (String key : keys) grid.addColumn(n -> n.path(key).asText()).setHeader(key);
        List<JsonNode> items = new ArrayList<>();
        rows.forEach(items::add);
        grid.setItems(items);
        grid.setAllRowsVisible(true);
        return grid;
    }
}

@Route(value = "projects/my-tasks", layout = MainLayout.class)
class MyTasksView extends Div implements BeforeEnterObserver {
    MyTasksView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Görevlerim", "My tasks")));
        JsonNode row = Ui.api().get("/api/projects/my-tasks");
        add(ProjectView.grid(row == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode() : row.path("items"), "taskNo", "title", "status", "dueDate"));
    }
}

@Route(value = "projects/timesheet", layout = MainLayout.class)
class TimesheetView extends Div implements BeforeEnterObserver {
    TimesheetView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Zaman çizelgesi", "Timesheet")));
        JsonNode row = Ui.api().get("/api/projects/time");
        add(ProjectView.grid(row == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode() : row.path("items"), "workDate", "employeeName", "hours", "costAmount"));
    }
}

@Route(value = "crm/leads/:id", layout = MainLayout.class)
class LeadView extends Div implements BeforeEnterObserver {
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        addClassName("ep-page");
        String id = event.getRouteParameters().get("id").orElse("");
        JsonNode row = Ui.api().get("/api/crm/leads/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("number").asText() + " · " + row.path("companyName").asText()));
        add(new Div(Ui.muted(row.path("status").asText() + " · " + row.path("contactName").asText() + " · " + row.path("city").asText())));
        if (!row.path("partnerTitle").isMissingNode() && !row.path("partnerTitle").isNull()) add(new Div(row.path("partnerTitle").asText()));
    }
}

@Route(value = "crm/opportunities/:id", layout = MainLayout.class)
class OpportunityView extends Div implements BeforeEnterObserver {
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        addClassName("ep-page");
        String id = event.getRouteParameters().get("id").orElse("");
        JsonNode row = Ui.api().get("/api/crm/opportunities/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("title").asText()));
        add(new Div(Ui.muted(row.path("number").asText() + " · " + row.path("stage").asText() + " · %" + row.path("probability").asInt()
                + " · " + Fmt.number(row.path("amount").decimalValue(), 2, "tr") + " " + row.path("currencyCode").asText())));
        String stage = row.path("stage").asText();
        if (!"WON".equals(stage) && !"LOST".equals(stage)) {
            Button won = new Button(Ui.T("Kazanıldı", "Won"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/crm/opportunities/" + id + "/stage", Map.of("stage", "WON")), Ui.T("Aşama güncellendi", "Stage updated")))
                    getUI().ifPresent(ui -> ui.getPage().reload());
            });
            won.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            add(won);
        }
    }
}

@Route(value = "crm/activities/:id", layout = MainLayout.class)
class ActivityView extends Div implements BeforeEnterObserver {
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        addClassName("ep-page");
        String id = event.getRouteParameters().get("id").orElse("");
        JsonNode row = Ui.api().get("/api/crm/activities/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("subject").asText()));
        add(new Div(Ui.muted(row.path("type").asText() + " · " + row.path("ownerName").asText() + " · " + (row.path("overdue").asBoolean() ? Ui.T("Gecikti", "Overdue") : Ui.T("Açık", "Open")))));
        if (row.path("completedAt").isNull() || row.path("completedAt").isMissingNode()) {
            add(new Button(Ui.T("Tamamla", "Complete"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/crm/activities/" + id + "/complete"), Ui.T("Aktivite tamamlandı", "Activity completed")))
                    getUI().ifPresent(ui -> ui.getPage().reload());
            }));
        }
    }
}

@Route(value = "expenses/reports/:id", layout = MainLayout.class)
class ExpenseReportView extends Div implements BeforeEnterObserver {
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        addClassName("ep-page");
        String id = event.getRouteParameters().get("id").orElse("");
        JsonNode row = Ui.api().get("/api/expenses/reports/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("number").asText() + " · " + row.path("status").asText()));
        add(new Div(Ui.muted(row.path("title").asText() + " · " + row.path("employeeName").asText() + " · " + row.path("projectName").asText()
                + " · " + Fmt.number(row.path("totalAmount").decimalValue(), 2, "tr"))));
        add(ProjectView.grid(row.path("lines"), "categoryName", "description", "quantity", "amountBase"));
    }
}

@Route(value = "expenses/my", layout = MainLayout.class)
class MyExpensesView extends Div implements BeforeEnterObserver {
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        addClassName("ep-page");
        add(new H2(Ui.T("Masraflarım", "My expenses")));
        JsonNode row = Ui.api().get("/api/expenses/reports/mine");
        add(ProjectView.grid(row == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode() : row.path("items"), "number", "title", "status", "totalAmount"));
    }
}
