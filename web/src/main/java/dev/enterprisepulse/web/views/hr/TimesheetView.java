package dev.enterprisepulse.web.views.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Route(value = "hr/timesheet", layout = MainLayout.class)
@PageTitle("Puantaj · EnterprisePulse")
public class TimesheetView extends Div implements BeforeEnterObserver {
    private final Div stats = new Div();
    private final Div sheet = new Div();
    private YearMonth month = YearMonth.now();
    private String departmentId;

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
        MenuBar export = new MenuBar();
        export.addThemeVariants(MenuBarVariant.LUMO_TERTIARY);
        SubMenu items = export.addItem(VaadinIcon.DOWNLOAD.create()).getSubMenu();
        items.addItem("Excel (.xlsx)", e -> Ui.download("/api/hr/attendance/timesheet/export?" + query() + "&format=xlsx"));
        items.addItem("PDF", e -> Ui.download("/api/hr/attendance/timesheet/export?" + query() + "&format=pdf"));
        add(Ui.pageHeader(Ui.grow(Ui.title(Ui.T("Puantaj", "Timesheet")),
                        Ui.muted(Ui.T("Aylık devam durumu; hücreye tıklayarak düzeltme yapabilirsiniz", "Monthly attendance; click a cell to correct it"))),
                department, HrUi.monthSelect(month, m -> {
                    month = m;
                    load();
                }), export));
        stats.addClassName("ep-cards");
        sheet.addClassNames("ep-card", "ep-sheet-wrap");
        Div legend = new Div();
        legend.addClassName("ep-legend");
        for (String s : HrUi.statuses()) {
            Span mark = new Span(HrUi.symbol(s));
            mark.addClassNames("ep-day", HrUi.dayClass(s));
            legend.add(new Span(mark, new Span(" " + HrUi.label(s))));
        }
        add(stats, sheet, legend);
        load();
    }

    private String query() {
        return "month=" + month + (departmentId != null ? "&departmentId=" + departmentId : "");
    }

    private void load() {
        Ui.run(() -> render(Ui.api().get("/api/hr/attendance/timesheet?" + query())));
    }

    private void render(JsonNode data) {
        List<JsonNode> rows = new ArrayList<>();
        data.path("rows").forEach(rows::add);
        stats.removeAll();
        stats.add(Ui.stat(Ui.T("Personel", "Employees"), String.valueOf(rows.size())),
                Ui.stat(Ui.T("Çalışılan saat", "Worked hours"), HrUi.minutes(sum(rows, "workedMinutes"))),
                Ui.stat(Ui.T("Fazla mesai", "Overtime"), HrUi.minutes(sum(rows, "overtimeMinutes"))),
                Ui.stat(Ui.T("Geç / devamsız gün", "Late / absent days"), sum(rows, "lateDays") + " / " + sum(rows, "absentDays")));

        boolean canEdit = Session.current().has("attendance:update");
        String today = data.path("today").asText();
        DateTimeFormatter dow = DateTimeFormatter.ofPattern("EEE", Fmt.locale(Ui.locale()));
        Element table = new Element("table");
        table.getClassList().add("ep-sheet");
        Element headRow = new Element("tr");
        headRow.appendChild(cell("th", Ui.T("Personel", "Employee"), "ep-sheet-name"));
        for (JsonNode raw : data.path("days")) {
            LocalDate d = LocalDate.parse(raw.asText());
            Element th = new Element("th");
            if (HrUi.weekend(d)) th.getClassList().add("ep-weekend");
            if (d.toString().equals(today)) th.getClassList().add("ep-today");
            String name = d.format(dow);
            th.appendChild(new Element("div").setText(String.valueOf(d.getDayOfMonth())), new Element("small").setText(name.substring(0, Math.min(2, name.length()))));
            headRow.appendChild(th);
        }
        headRow.appendChild(cell("th", Ui.T("Gün", "Days"), null).setAttribute("title", Ui.T("Gelinen / planlı gün", "Present / planned days")),
                cell("th", Ui.T("Saat", "Hours"), null), cell("th", Ui.T("FM", "OT"), null));
        table.appendChild(new Element("thead").appendChild(headRow));

        Element body = new Element("tbody");
        for (JsonNode row : rows) {
            Element tr = new Element("tr");
            Element name = cell("td", null, "ep-sheet-name");
            name.appendChild(new Element("div").appendChild(new Element("b").setText(row.path("employeeName").asText())),
                    new Element("small").setText(row.hasNonNull("departmentName") ? row.get("departmentName").asText() : row.path("employeeNo").asText()));
            name.getClassList().add("ep-muted-small");
            tr.appendChild(name);
            for (JsonNode day : row.path("days")) {
                String status = HrUi.text(day.get("status"));
                Element td = cell("td", HrUi.symbol(status), HrUi.dayClass(status));
                td.getClassList().add("ep-day");
                td.setAttribute("title", tip(day));
                if (day.hasNonNull("shiftColor")) td.getStyle().set("box-shadow", "inset 0 -3px 0 " + day.get("shiftColor").asText());
                if (canEdit) {
                    td.getClassList().add("ep-click");
                    td.addEventListener("click", e -> AttendanceRecordDialog.open(row.path("employeeId").asText(), row.path("employeeName").asText(), day, this::load));
                }
                tr.appendChild(td);
            }
            JsonNode t = row.path("totals");
            tr.appendChild(cell("td", t.path("presentDays").asText() + "/" + t.path("plannedDays").asText(), "ep-num"),
                    cell("td", HrUi.minutes(t.path("workedMinutes").asInt()), "ep-num"),
                    cell("td", HrUi.minutes(t.path("overtimeMinutes").asInt()), "ep-num"));
            body.appendChild(tr);
        }
        table.appendChild(body);
        sheet.removeAll();
        sheet.getElement().appendChild(table);
    }

    private static Element cell(String tag, String text, String className) {
        Element e = new Element(tag);
        if (text != null) e.setText(text);
        if (className != null) e.getClassList().add(className);
        return e;
    }

    private static int sum(List<JsonNode> rows, String key) {
        return rows.stream().mapToInt(r -> r.path("totals").path(key).asInt()).sum();
    }

    private static String tip(JsonNode day) {
        List<String> parts = new ArrayList<>();
        parts.add(HrUi.format(day.get("date"), "dd.MM.yyyy"));
        parts.add(HrUi.label(HrUi.text(day.get("status"))));
        if (day.hasNonNull("plannedStart")) parts.add(Ui.T("Plan", "Plan") + " " + HrUi.range(day));
        if (day.hasNonNull("checkIn")) parts.add(day.get("checkIn").asText() + "–" + (day.hasNonNull("checkOut") ? day.get("checkOut").asText() : "?"));
        if (day.path("lateMinutes").asInt() > 0) parts.add(Ui.T("Geç", "Late") + " " + day.get("lateMinutes").asInt() + " " + Ui.T("dk", "min"));
        if (day.path("overtimeMinutes").asInt() > 0) parts.add(Ui.T("FM", "OT") + " " + HrUi.minutes(day.get("overtimeMinutes").asInt()));
        if (day.hasNonNull("note")) parts.add(day.get("note").asText());
        return String.join(" · ", parts);
    }
}
