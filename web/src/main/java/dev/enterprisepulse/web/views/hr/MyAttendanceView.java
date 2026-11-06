package dev.enterprisepulse.web.views.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Route(value = "hr/my-attendance", layout = MainLayout.class)
@PageTitle("Mesaim · EnterprisePulse")
public class MyAttendanceView extends Div implements BeforeEnterObserver {
    private final Div today = new Div();
    private final Div summary = new Div();
    private final Grid<JsonNode> grid = new Grid<>();
    private YearMonth month = YearMonth.now();

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(Ui.pageHeader(Ui.grow(Ui.title(Ui.T("Mesaim", "My attendance")), Ui.muted(Ui.T("Giriş-çıkış kaydı ve aylık özet", "Clock in / out and the month at a glance")))));
        if (!Session.current().user().hasNonNull("employeeId")) {
            add(Ui.alert(Ui.T("Hesabınıza bağlı bir personel kaydı yok. İK ile iletişime geçin.", "No employee record is linked to your account. Please contact HR."), "info"));
            return;
        }
        H3 monthTitle = new H3(Ui.T("Ay özeti", "Month summary"));
        monthTitle.addClassName("ep-grow");
        Div monthBar = new Div(monthTitle, HrUi.monthSelect(month, m -> {
            month = m;
            load();
        }));
        monthBar.addClassNames("ep-row", "ep-section-title");
        summary.addClassName("ep-cards");

        grid.removeAllColumns();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.addClassName("ep-grid");
        grid.setAllRowsVisible(true);
        grid.setEmptyStateText(Ui.L("noRecords"));
        grid.addColumn(d -> HrUi.format(d.get("date"), "dd MMM, EEE")).setHeader(Ui.T("Tarih", "Date"));
        grid.addColumn(HrUi::range).setHeader(Ui.T("Planlanan", "Planned"));
        grid.addColumn(d -> HrUi.orDash(d.get("checkIn"))).setHeader(Ui.T("Giriş", "Check-in"));
        grid.addColumn(d -> HrUi.orDash(d.get("checkOut"))).setHeader(Ui.T("Çıkış", "Check-out"));
        grid.addColumn(HrUi::worked).setHeader(Ui.T("Çalışılan", "Worked")).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(d -> d.path("overtimeMinutes").asInt() > 0 ? HrUi.minutes(d.path("overtimeMinutes").asInt()) : "—")
                .setHeader(Ui.T("Fazla mesai", "Overtime")).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(new ComponentRenderer<>(d -> {
            Span s = new Span(HrUi.badge(HrUi.text(d.get("status"))));
            int late = d.path("lateMinutes").asInt();
            if (late > 0) s.add(Ui.muted(" +" + late + " " + Ui.T("dk", "min")));
            return s;
        })).setHeader(Ui.L("status")).setAutoWidth(true);
        add(today, monthBar, summary, grid);
        load();
    }

    private void load() {
        Ui.run(() -> render(Ui.api().get("/api/hr/attendance/me?month=" + month)));
    }

    private void render(JsonNode data) {
        JsonNode day = data.get("today");
        today.removeAll();
        today.addClassNames("ep-card", "ep-today-card");
        H3 date = new H3(HrUi.format(day.get("date"), "dd MMMM yyyy, EEEE"));
        date.addClassName("ep-grow");
        Div head = new Div(date, HrUi.badge(HrUi.text(day.get("status"))));
        head.addClassName("ep-row");

        Button in = new Button(Ui.T("Giriş yap", "Check in"), VaadinIcon.SIGN_IN.create(), e ->
                Ui.run(() -> render(Ui.api().post("/api/hr/attendance/check-in")), Ui.T("Giriş kaydedildi", "Checked in")));
        in.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        in.setEnabled(data.path("canCheckIn").asBoolean());
        in.setDisableOnClick(true);
        Button out = new Button(Ui.T("Çıkış yap", "Check out"), VaadinIcon.SIGN_OUT.create(), e ->
                Ui.run(() -> render(Ui.api().post("/api/hr/attendance/check-out")), Ui.T("Çıkış kaydedildi", "Checked out")));
        out.addThemeVariants(ButtonVariant.LUMO_LARGE);
        out.setEnabled(data.path("canCheckOut").asBoolean());
        out.setDisableOnClick(true);
        Div buttons = new Div(in, out);
        buttons.addClassNames("ep-row", "ep-row-end", "ep-grow");

        Div facts = new Div(fact(Ui.T("Planlanan", "Planned"), HrUi.range(day)), fact(Ui.T("Giriş", "Check-in"), HrUi.orDash(day.get("checkIn"))),
                fact(Ui.T("Çıkış", "Check-out"), HrUi.orDash(day.get("checkOut"))), fact(Ui.T("Çalışılan", "Worked"), HrUi.worked(day)), buttons);
        facts.addClassNames("ep-row", "ep-row-wrap", "ep-today-facts");
        today.add(head, facts);

        JsonNode totals = data.path("row").path("totals");
        summary.removeAll();
        summary.add(Ui.stat(Ui.T("Çalışılan / planlanan", "Worked / planned"),
                        HrUi.minutes(totals.path("workedMinutes").asInt()) + " / " + HrUi.minutes(totals.path("plannedMinutes").asInt())),
                Ui.stat(Ui.T("Fazla mesai", "Overtime"), HrUi.minutes(totals.path("overtimeMinutes").asInt())),
                tone(Ui.stat(Ui.T("Geç kalınan gün", "Late days"), totals.path("lateDays").asText()), totals.path("lateDays").asInt() > 0 ? "warning" : null),
                tone(Ui.stat(Ui.T("Devamsızlık", "Absent days"), totals.path("absentDays").asText()), totals.path("absentDays").asInt() > 0 ? "error" : null));

        String todayDate = day.path("date").asText();
        List<JsonNode> days = new ArrayList<>();
        for (JsonNode d : data.path("row").path("days")) {
            String status = d.path("status").asText();
            if (d.path("date").asText().compareTo(todayDate) <= 0 && !status.equals("UPCOMING") && (!status.equals("OFF") || d.hasNonNull("checkIn"))) days.add(d);
        }
        Collections.reverse(days);
        grid.setItems(days);
    }

    private static Div fact(String caption, String value) {
        Span v = new Span(value);
        v.addClassName("ep-fact-value");
        Div d = new Div(Ui.muted(caption), v);
        d.addClassName("ep-fact");
        return d;
    }

    private static Div tone(Div stat, String color) {
        if (color != null) stat.addClassName("ep-tone-" + color);
        return stat;
    }
}
