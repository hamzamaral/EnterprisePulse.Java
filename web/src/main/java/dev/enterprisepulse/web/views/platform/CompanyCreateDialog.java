package dev.enterprisepulse.web.views.platform;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import java.util.List;

public final class CompanyCreateDialog {
    private CompanyCreateDialog() {}

    private static String opt(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    public static void open(Runnable onSaved) {
        Dialog d = new Dialog();
        d.setHeaderTitle(Ui.T("Yeni şirket", "New company"));
        d.setWidth("min(680px, 96vw)");
        d.add(Ui.alert(Ui.T("Merkez şube, vergi oranları, birimler, numara serileri, roller ve onay akışları otomatik oluşturulur.",
                "HQ branch, taxes, units, number series, roles and approval flows are created automatically."), "info"));
        TextField code = new TextField(Ui.T("Kod", "Code"));
        code.setRequiredIndicatorVisible(true);
        TextField name = new TextField(Ui.T("Ad", "Name"));
        name.setRequiredIndicatorVisible(true);
        TextField legal = new TextField(Ui.T("Ticari unvan", "Legal name"));
        TextField taxNumber = new TextField(Ui.T("Vergi no", "Tax number"));
        TextField taxOffice = new TextField(Ui.T("Vergi dairesi", "Tax office"));
        TextField email = new TextField(Ui.L("email"));
        TextField phone = new TextField(Ui.T("Telefon", "Phone"));
        TextField city = new TextField(Ui.T("Şehir", "City"));
        ComboBox<String> currency = new ComboBox<>(Ui.T("Ana para birimi", "Base currency"), List.of("TRY", "USD", "EUR", "GBP"));
        currency.setValue("TRY");
        TextArea address = new TextArea(Ui.T("Adres", "Address"));
        FormLayout form = new FormLayout(code, name, legal, taxNumber, taxOffice, email, phone, city, currency, address);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));
        form.setColspan(legal, 2);
        form.setColspan(address, 2);
        d.add(form);
        Button save = new Button(Ui.L("save"));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> {
            if (code.getValue().isBlank() || name.getValue().isBlank()) {
                Ui.error(Ui.L("required"));
                return;
            }
            boolean ok = Ui.run(() -> {
                Ui.api().post("/api/companies", ApiClient.obj("code", code.getValue(), "name", name.getValue(), "legalName", opt(legal.getValue()),
                        "taxNumber", opt(taxNumber.getValue()), "taxOffice", opt(taxOffice.getValue()), "email", opt(email.getValue()),
                        "phone", opt(phone.getValue()), "address", opt(address.getValue()), "city", opt(city.getValue()), "baseCurrency", currency.getValue()));
                Session.current().loadMe();
            }, Ui.L("saved"));
            if (ok) {
                d.close();
                onSaved.run();
            }
        });
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), save);
        d.open();
    }
}
