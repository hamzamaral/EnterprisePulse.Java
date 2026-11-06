package dev.enterprisepulse.web.views.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import java.util.ArrayList;
import java.util.List;

public final class UserCreateDialog {
    private UserCreateDialog() {}

    /** Branch choices of the active company as lookup items. */
    static List<RefPicker.Item> branches() {
        List<RefPicker.Item> items = new ArrayList<>();
        JsonNode c = Session.current().company();
        if (c != null) for (JsonNode b : c.path("branches")) items.add(new RefPicker.Item(b.path("id").asText(), b.path("name").asText()));
        return items;
    }

    static List<RefPicker.Item> roles() {
        List<RefPicker.Item> items = new ArrayList<>();
        Ui.run(() -> items.addAll(RefPicker.lookup("roles", "take=100")));
        return items;
    }

    public static void open(Runnable onSaved) {
        Dialog d = new Dialog();
        d.setHeaderTitle(Ui.T("Yeni kullanıcı", "New user"));
        d.setWidth("min(640px, 96vw)");
        EmailField email = new EmailField(Ui.L("email"));
        email.setRequiredIndicatorVisible(true);
        email.setHelperText(Ui.T("E-posta başka şirkette kayıtlıysa kullanıcı bu şirkete eklenir.", "If the e-mail exists in another company the user is added here."));
        TextField first = new TextField(Ui.T("Ad", "First name"));
        first.setRequiredIndicatorVisible(true);
        TextField last = new TextField(Ui.T("Soyad", "Last name"));
        last.setRequiredIndicatorVisible(true);
        TextField phone = new TextField(Ui.T("Telefon", "Phone"));
        PasswordField password = new PasswordField(Ui.L("password"));
        password.setHelperText(Ui.T("Boş bırakılırsa davet e-postası gönderilir", "Leave empty to send an invitation e-mail"));
        MultiSelectComboBox<RefPicker.Item> roles = new MultiSelectComboBox<>(Ui.L("roles"), roles());
        roles.setItemLabelGenerator(i -> i.label);
        ComboBox<RefPicker.Item> branch = new ComboBox<>(Ui.L("branch"), branches());
        branch.setItemLabelGenerator(i -> i.label);
        branch.setClearButtonVisible(true);
        Checkbox superAdmin = new Checkbox(Ui.T("Süper yönetici", "Super admin"));
        FormLayout form = new FormLayout(email, first, last, phone, password, roles, branch);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));
        form.setColspan(email, 2);
        form.setColspan(roles, 2);
        form.setColspan(branch, 2);
        if (Session.current().isSuperAdmin()) form.add(superAdmin, 2);
        d.add(form);
        Button save = new Button(Ui.L("save"));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> {
            if (email.getValue().isBlank() || first.getValue().isBlank()) {
                Ui.error(Ui.L("required"));
                return;
            }
            boolean ok = Ui.run(() -> Ui.api().post("/api/users", ApiClient.obj(
                    "email", email.getValue(), "firstName", first.getValue(), "lastName", last.getValue(),
                    "phone", phone.getValue().isBlank() ? null : phone.getValue(),
                    "password", password.getValue().isEmpty() ? null : password.getValue(),
                    "roleIds", roles.getValue().stream().map(i -> i.id).toList(),
                    "branchId", branch.getValue() == null ? null : branch.getValue().id,
                    "isSuperAdmin", superAdmin.getValue())), Ui.L("saved"));
            if (ok) {
                d.close();
                onSaved.run();
            }
        });
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), save);
        d.open();
    }
}
