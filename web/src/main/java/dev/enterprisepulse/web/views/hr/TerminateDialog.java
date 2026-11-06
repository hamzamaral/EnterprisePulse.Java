package dev.enterprisepulse.web.views.hr;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextArea;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.ResourceViews.ActionContext;
import dev.enterprisepulse.web.ui.Ui;
import java.time.LocalDate;

public final class TerminateDialog {
    private TerminateDialog() {}

    public static void open(ActionContext ctx) {
        Dialog d = new Dialog();
        d.setHeaderTitle(Ui.T("İşten çıkış", "Terminate employment") + " · " + ctx.record().path("firstName").asText() + " " + ctx.record().path("lastName").asText());
        d.setWidth("min(480px, 96vw)");
        DatePicker date = new DatePicker(Ui.T("Çıkış tarihi", "Termination date"), LocalDate.now());
        date.setI18n(Ui.datePickerI18n());
        date.setRequired(true);
        date.setWidthFull();
        TextArea reason = new TextArea(Ui.T("Çıkış nedeni", "Reason"));
        reason.setWidthFull();
        Div body = new Div(Ui.alert(Ui.T("Zimmetli demirbaşı olan personelin çıkışı yapılamaz.", "Employees holding assets cannot be terminated."), "warning"), date, reason);
        body.addClassName("ep-stack");
        d.add(body);

        Button ok = new Button(Ui.T("Çıkışı yap", "Terminate"));
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        date.addValueChangeListener(e -> ok.setEnabled(e.getValue() != null));
        ok.addClickListener(e -> {
            boolean saved = Ui.run(() -> Ui.api().post("/api/hr/employees/" + ctx.id() + "/terminate",
                    ApiClient.obj("terminationDate", date.getValue().toString(), "reason", reason.getValue())), Ui.L("saved"));
            if (saved) {
                d.close();
                ctx.reload().run();
            }
        });
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), ok);
        d.open();
    }
}
