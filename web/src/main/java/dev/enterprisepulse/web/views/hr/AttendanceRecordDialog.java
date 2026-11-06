package dev.enterprisepulse.web.views.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.ui.Ui;

/** Manual correction of one employee-day on the timesheet. */
public final class AttendanceRecordDialog {
    private AttendanceRecordDialog() {}

    public static void open(String employeeId, String employeeName, JsonNode day, Runnable onSaved) {
        Dialog d = new Dialog();
        d.setHeaderTitle(employeeName + " · " + HrUi.format(day.get("date"), "dd MMMM yyyy, EEEE"));
        d.setWidth("min(460px, 96vw)");

        Span plan = new Span(Ui.T("Planlanan", "Planned") + ": ");
        Span planValue = new Span(day.hasNonNull("plannedStart") ? HrUi.range(day) : Ui.T("Tatil", "Off"));
        planValue.addClassName("ep-strong");
        Span status = new Span(" · " + Ui.L("status") + ": ");
        Span statusValue = new Span(HrUi.label(HrUi.text(day.get("status"))));
        statusValue.addClassName("ep-strong");
        Div info = new Div(plan, planValue, status, statusValue);

        TextField in = new TextField(Ui.T("Giriş (SS:dd)", "Check-in (HH:mm)"));
        in.setPlaceholder("09:00");
        in.setClearButtonVisible(true);
        in.setValue(text(day, "checkIn"));
        TextField out = new TextField(Ui.T("Çıkış (SS:dd)", "Check-out (HH:mm)"));
        out.setPlaceholder("18:00");
        out.setClearButtonVisible(true);
        out.setValue(text(day, "checkOut"));
        out.setHelperText(Ui.T("Girişten önceki saat ertesi gün sayılır", "A time before the check-in means the next day"));
        in.getStyle().set("flex", "1");
        out.getStyle().set("flex", "1");
        Div times = new Div(in, out);
        times.addClassName("ep-row");
        times.getStyle().set("align-items", "flex-start");
        TextArea note = new TextArea(Ui.T("Not", "Note"));
        note.setWidthFull();
        note.setValue(text(day, "note"));

        Div body = new Div(info, times, note, Ui.muted(Ui.T("Saatleri ve notu temizlemek kaydı siler.", "Clearing the times and the note removes the record.")));
        body.addClassName("ep-stack");
        d.add(body);

        Button save = new Button(Ui.L("save"));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> {
            boolean ok = Ui.run(() -> Ui.api().put("/api/hr/attendance/records", ApiClient.obj(
                    "employeeId", employeeId,
                    "date", day.path("date").asText(),
                    "checkIn", in.getValue().isBlank() ? null : in.getValue().trim(),
                    "checkOut", out.getValue().isBlank() ? null : out.getValue().trim(),
                    "note", note.getValue().isBlank() ? null : note.getValue())), Ui.L("saved"));
            if (ok) {
                d.close();
                onSaved.run();
            }
        });
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), save);
        d.open();
    }

    private static String text(JsonNode day, String key) {
        String v = HrUi.text(day.get(key));
        return v == null ? "" : v;
    }
}
