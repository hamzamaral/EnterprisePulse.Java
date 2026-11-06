package dev.enterprisepulse.web.views.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route(value = "hr/shift-plan", layout = MainLayout.class)
@PageTitle("Vardiya planı · EnterprisePulse")
public class ShiftPlanView extends Div implements BeforeEnterObserver {
    private static final String DEFAULT = "__default__";
    private static final String OFF = "__off__";

    private final Div sheet = new Div();
    private final Span range = new Span();
    private final List<ObjectNode> shifts = new ArrayList<>();
    private LocalDate monday = mondayOf(LocalDate.now());
    private String departmentId;
    private JsonNode plan;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        if (!Session.current().has("attendance:read")) {
            add(Ui.alert(Ui.L("forbidden"), "error"));
            return;
        }
        RefPicker department = new RefPicker("departments", Ui.T("Departman", "Department"));
        department.setWidth("220px");
        department.addValueChangeListener(e -> {
            departmentId = department.getRefId();
            load();
        });
        add(Ui.pageHeader(Ui.iconButton(VaadinIcon.ARROW_LEFT, Ui.L("back"), () -> Ui.go("r/shifts")),
                Ui.grow(Ui.title(Ui.T("Haftalık vardiya planı", "Weekly shift plan")),
                        Ui.muted(Ui.T("Plan girilmeyen hafta içi günler standart 09:00–18:00 sayılır", "Weekdays without a plan follow the standard 09:00–18:00"))),
                department));

        range.addClassNames("ep-strong", "ep-grow");
        Div bar = new Div(Ui.iconButton(VaadinIcon.CHEVRON_LEFT, Ui.T("Önceki hafta", "Previous week"), () -> move(-7)),
                new Button(Ui.T("Bu hafta", "This week"), e -> {
                    monday = mondayOf(LocalDate.now());
                    load();
                }),
                Ui.iconButton(VaadinIcon.CHEVRON_RIGHT, Ui.T("Sonraki hafta", "Next week"), () -> move(7)), range);
        bar.addClassNames("ep-row", "ep-row-wrap", "ep-section-title");
        if (canEdit()) bar.add(new Button(Ui.T("Önceki haftayı kopyala", "Copy previous week"), VaadinIcon.COPY.create(), e -> copyPreviousWeek()));
        sheet.addClassNames("ep-card", "ep-sheet-wrap");
        add(bar, sheet);

        shifts.clear();
        Ui.run(() -> shifts.addAll(Ui.rows(Ui.api().get("/api/r/shifts?pageSize=100&sort=startTime&order=asc").get("data"))));
        load();
    }

    private static LocalDate mondayOf(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private boolean canEdit() {
        return Session.current().has("attendance:update");
    }

    private void move(int days) {
        monday = monday.plusDays(days);
        load();
    }

    private String query(LocalDate from) {
        return "from=" + from + "&to=" + from.plusDays(6) + (departmentId != null ? "&departmentId=" + departmentId : "");
    }

    private void load() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM", Fmt.locale(Ui.locale()));
        range.setText(monday.format(f) + " – " + monday.plusDays(6).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Fmt.locale(Ui.locale()))));
        if (Ui.run(() -> plan = Ui.api().get("/api/hr/shift-plan?" + query(monday)))) render();
    }

    private static Map<String, JsonNode> entries(JsonNode plan) {
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode e : plan.path("entries")) map.put(e.path("employeeId").asText() + "/" + e.path("date").asText(), e);
        return map;
    }

    private void render() {
        Map<String, JsonNode> entries = entries(plan);
        DateTimeFormatter head = DateTimeFormatter.ofPattern("EEE dd.MM", Fmt.locale(Ui.locale()));
        Element table = new Element("table");
        table.getClassList().addAll(List.of("ep-sheet", "ep-plan"));
        Element headRow = new Element("tr");
        headRow.appendChild(th(Ui.T("Personel", "Employee"), "ep-sheet-name"));
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            headRow.appendChild(th(d.format(head), HrUi.weekend(d) ? "ep-weekend" : null));
        }
        table.appendChild(new Element("thead").appendChild(headRow));
        Element body = new Element("tbody");
        for (JsonNode e : plan.path("employees")) {
            String employeeId = e.path("id").asText();
            Element tr = new Element("tr");
            Element name = new Element("td");
            name.getClassList().add("ep-sheet-name");
            name.appendChild(new Element("div").appendChild(new Element("b").setText(e.path("name").asText())),
                    new Element("small").setText(e.hasNonNull("departmentName") ? e.get("departmentName").asText() : e.path("employeeNo").asText()));
            tr.appendChild(name);
            for (int i = 0; i < 7; i++) {
                LocalDate d = monday.plusDays(i);
                JsonNode entry = entries.get(employeeId + "/" + d);
                String value = entry == null ? DEFAULT : entry.hasNonNull("shiftId") ? entry.get("shiftId").asText() : OFF;
                tr.appendChild(new Element("td").appendChild(select(employeeId, d, value)));
            }
            body.appendChild(tr);
        }
        table.appendChild(body);
        sheet.removeAll();
        sheet.getElement().appendChild(table);
    }

    private Element select(String employeeId, LocalDate d, String value) {
        Element select = new Element("select");
        select.getClassList().add("ep-select");
        select.getStyle().set("border-left-color", colorOf(value));
        if (!canEdit()) select.setAttribute("disabled", true);
        select.appendChild(option(DEFAULT, HrUi.weekend(d) ? Ui.T("Hafta sonu", "Weekend") : Ui.T("Standart", "Standard"), value),
                option(OFF, Ui.T("Tatil günü", "Day off"), value));
        for (ObjectNode s : shifts) {
            String id = s.path("id").asText();
            if (s.path("isActive").asBoolean() || id.equals(value))
                select.appendChild(option(id, s.path("name").asText() + " " + s.path("startTime").asText() + "–" + s.path("endTime").asText(), value));
        }
        select.addEventListener("change", ev -> {
            String v = ev.getEventData().getString("element.value");
            save(List.of(change(employeeId, d, DEFAULT.equals(v) ? null : OFF.equals(v) ? null : v, DEFAULT.equals(v))), null);
        }).addEventData("element.value");
        return select;
    }

    private static Element option(String value, String text, String selected) {
        Element o = new Element("option").setAttribute("value", value).setText(text);
        if (value.equals(selected)) o.setAttribute("selected", true);
        return o;
    }

    private String colorOf(String value) {
        return shifts.stream().filter(s -> s.path("id").asText().equals(value)).map(s -> s.path("color").asText(null)).filter(c -> c != null)
                .findFirst().orElse("transparent");
    }

    private static ObjectNode change(String employeeId, LocalDate d, String shiftId, boolean useDefault) {
        return ApiClient.obj("employeeId", employeeId, "date", d.toString(), "shiftId", shiftId, "useDefault", useDefault);
    }

    private void copyPreviousWeek() {
        JsonNode[] previous = new JsonNode[1];
        if (!Ui.run(() -> previous[0] = Ui.api().get("/api/hr/shift-plan?" + query(monday.minusDays(7))))) return;
        Map<String, JsonNode> source = entries(previous[0]);
        List<ObjectNode> changes = new ArrayList<>();
        for (JsonNode e : plan.path("employees")) {
            String employeeId = e.path("id").asText();
            for (int i = 0; i < 7; i++) {
                LocalDate d = monday.plusDays(i);
                JsonNode s = source.get(employeeId + "/" + d.minusDays(7));
                changes.add(s != null ? change(employeeId, d, HrUi.text(s.get("shiftId")), false) : change(employeeId, d, null, true));
            }
        }
        if (!changes.isEmpty()) save(changes, Ui.T("Önceki hafta kopyalandı", "Previous week copied"));
    }

    private void save(List<ObjectNode> changes, String message) {
        if (Ui.run(() -> Ui.api().put("/api/hr/shift-plan", ApiClient.obj("entries", changes)), message)) load();
    }

    private static Element th(String text, String className) {
        Element e = new Element("th").setText(text);
        if (className != null) e.getClassList().add(className);
        return e;
    }
}
