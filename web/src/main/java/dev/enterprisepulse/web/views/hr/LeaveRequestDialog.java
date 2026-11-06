package dev.enterprisepulse.web.views.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** New leave request; HR (leave:create) may pick the employee. */
public final class LeaveRequestDialog {
    private LeaveRequestDialog() {}

    /** @param onBehalf null = decide by the leave:create permission */
    public static void open(Boolean onBehalf, Runnable onSaved) {
        boolean behalf = onBehalf != null ? onBehalf : Session.current().has("leave:create");
        Dialog d = new Dialog();
        d.setHeaderTitle(Ui.T("Yeni izin talebi", "New leave request"));
        d.setWidth("min(560px, 96vw)");

        RefPicker employee = new RefPicker("employees", Ui.T("Personel", "Employee"));
        employee.setHelperText(Ui.T("Boş bırakılırsa kendiniz için talep oluşturulur", "Leave empty to request for yourself"));
        employee.setWidthFull();

        List<JsonNode> types = new ArrayList<>();
        Ui.run(() -> Ui.api().get("/api/hr/leave-types").forEach(types::add));
        ComboBox<JsonNode> type = new ComboBox<>(Ui.T("İzin türü", "Leave type"), types);
        type.setItemLabelGenerator(t -> t.path("name").asText());
        type.setRenderer(new ComponentRenderer<>(t -> new Span(Ui.dot(t.path("color").asText()), new Span(t.path("name").asText()))));
        type.setRequired(true);
        type.setWidthFull();

        DatePicker start = new DatePicker(Ui.T("Başlangıç", "Start"), LocalDate.now());
        DatePicker end = new DatePicker(Ui.T("Bitiş", "End"), LocalDate.now());
        start.setI18n(Ui.datePickerI18n());
        end.setI18n(Ui.datePickerI18n());
        Div dates = new Div(start, end);
        dates.addClassName("ep-row");
        start.getStyle().set("flex", "1");
        end.getStyle().set("flex", "1");

        Div info = Ui.alert("", "info");
        TextArea reason = new TextArea(Ui.T("Açıklama", "Reason"));
        reason.setWidthFull();

        Button send = new Button(Ui.L("send"));
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        int[] days = {0};
        Runnable recount = () -> {
            days[0] = 0;
            if (start.getValue() != null && end.getValue() != null) {
                if (end.getValue().isBefore(start.getValue())) end.setValue(start.getValue());
                Ui.run(() -> days[0] = Ui.api().get("/api/hr/leave-requests/working-days?startDate=" + start.getValue() + "&endDate=" + end.getValue())
                        .path("days").asInt());
            }
            info.removeAll();
            info.add(VaadinIcon.CALENDAR.create(), new Span(" " + (start.getValue() == null || end.getValue() == null
                    ? Ui.T("Tarih aralığı seçin", "Pick a date range")
                    : Ui.T(days[0] + " iş günü (hafta sonu ve resmi tatiller hariç)", days[0] + " working day(s), excluding weekends and holidays"))));
            send.setEnabled(type.getValue() != null && days[0] > 0);
        };
        start.addValueChangeListener(e -> {
            end.setMin(e.getValue());
            recount.run();
        });
        end.addValueChangeListener(e -> recount.run());
        type.addValueChangeListener(e -> send.setEnabled(e.getValue() != null && days[0] > 0));
        recount.run();

        send.addClickListener(e -> {
            send.setEnabled(false);
            boolean ok = Ui.run(() -> Ui.api().post("/api/hr/leave-requests", ApiClient.obj(
                    "employeeId", behalf ? employee.getRefId() : null,
                    "leaveTypeId", type.getValue().path("id").asText(),
                    "startDate", start.getValue().toString(),
                    "endDate", end.getValue().toString(),
                    "reason", reason.getValue())), Ui.T("İzin talebiniz oluşturuldu", "Leave request created"));
            if (ok) {
                d.close();
                if (onSaved != null) onSaved.run();
            } else {
                send.setEnabled(true);
            }
        });

        Div body = new Div();
        body.addClassName("ep-stack");
        if (behalf) body.add(employee);
        body.add(type, dates, info, reason);
        d.add(body);
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), send);
        d.open();
    }
}
