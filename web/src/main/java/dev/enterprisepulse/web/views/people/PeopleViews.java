package dev.enterprisepulse.web.views.people;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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

@Route(value = "payroll/periods/:id", layout = MainLayout.class)
class PayrollPeriodView extends Div implements BeforeEnterObserver {
    PayrollPeriodView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        JsonNode row = Ui.api().get("/api/payroll/periods/" + event.getRouteParameters().get("id").orElse(""));
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("number").asText()));
        add(new Div(row.path("year").asText() + "-" + row.path("month").asText() + " · " + row.path("status").asText()
                + " · " + Ui.T("Net", "Net") + " " + money(row, "netTotal")));
        add(PeopleUi.grid(row.path("payslips"), "employeeName", "payDays", "grossPay", "netPay"));
    }
    private static String money(JsonNode row, String field) { return Fmt.number(row.path(field).decimalValue(), 2, "tr"); }
}

@Route(value = "payroll/my", layout = MainLayout.class)
class MyPayslipsView extends Div implements BeforeEnterObserver {
    MyPayslipsView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Bordrolarım", "My payslips")));
        JsonNode row = Ui.api().get("/api/payroll/mine");
        add(PeopleUi.grid(row == null ? JsonNodeFactory.instance.arrayNode() : row.path("items"), "number", "year", "month", "grossPay", "netPay", "status"));
    }
}

@Route(value = "recruitment/applications/:id", layout = MainLayout.class)
class ApplicationView extends Div implements BeforeEnterObserver {
    ApplicationView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        JsonNode row = Ui.api().get("/api/recruitment/applications/" + event.getRouteParameters().get("id").orElse(""));
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("candidateName").asText()));
        add(new Div(row.path("postingTitle").asText() + " · " + row.path("stage").asText()));
        if (!row.path("offerSalary").isMissingNode() && !row.path("offerSalary").isNull())
            add(new Div(Ui.T("Teklif", "Offer") + " " + Fmt.number(row.path("offerSalary").decimalValue(), 2, "tr") + " · " + row.path("offerStatus").asText()));
    }
}

@Route(value = "recruitment/interviews", layout = MainLayout.class)
class MyInterviewsView extends Div implements BeforeEnterObserver {
    MyInterviewsView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Mülakatlarım", "My interviews")));
        JsonNode row = Ui.api().get("/api/recruitment/interviews/mine");
        add(PeopleUi.grid(row == null ? JsonNodeFactory.instance.arrayNode() : row.path("items"), "candidateName", "type", "status"));
    }
}

@Route(value = "performance/my", layout = MainLayout.class)
class MyReviewsView extends Div implements BeforeEnterObserver {
    MyReviewsView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Değerlendirmelerim", "My reviews")));
        JsonNode row = Ui.api().get("/api/performance/mine");
        add(PeopleUi.grid(row == null ? JsonNodeFactory.instance.arrayNode() : row.path("items"), "cycleName", "employeeName", "status", "overallScore"));
    }
}

@Route(value = "training/my", layout = MainLayout.class)
class MyTrainingsView extends Div implements BeforeEnterObserver {
    MyTrainingsView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Eğitimlerim", "My trainings")));
        JsonNode row = Ui.api().get("/api/training/mine");
        add(PeopleUi.grid(row == null ? JsonNodeFactory.instance.arrayNode() : row.path("items"), "courseTitle", "status", "score"));
    }
}

@Route(value = "training/compliance", layout = MainLayout.class)
class ComplianceView extends Div implements BeforeEnterObserver {
    ComplianceView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Zorunlu Eğitim Uyumu", "Training compliance")));
        JsonNode row = Ui.api().get("/api/training/compliance");
        add(PeopleUi.grid(row == null ? JsonNodeFactory.instance.arrayNode() : row.path("items"), "employeeName", "courseTitle"));
    }
}

final class PeopleUi {
    private PeopleUi() {}

    static Grid<JsonNode> grid(JsonNode rows, String... keys) {
        Grid<JsonNode> grid = new Grid<>();
        for (String key : keys) grid.addColumn(n -> n.path(key).asText()).setHeader(key);
        List<JsonNode> items = new ArrayList<>();
        if (rows != null) rows.forEach(items::add);
        grid.setItems(items);
        grid.setAllRowsVisible(true);
        return grid;
    }
}
