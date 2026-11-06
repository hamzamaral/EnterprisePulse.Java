package dev.enterprisepulse.web.views.platform;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import java.util.ArrayList;
import java.util.List;

public final class AnnouncementDialog {
    private AnnouncementDialog() {}

    public static void open() {
        Dialog d = new Dialog();
        d.setHeaderTitle(Ui.T("Duyuru gönder", "Send announcement"));
        d.setWidth("min(600px, 96vw)");
        List<RefPicker.Item> options = new ArrayList<>();
        Ui.run(() -> options.addAll(RefPicker.lookup("users", "take=200")));
        MultiSelectComboBox<RefPicker.Item> users = new MultiSelectComboBox<>(Ui.L("users"), options);
        users.setItemLabelGenerator(i -> i.label);
        users.setSelectedItemsOnTop(true);
        Button selectAll = new Button(Ui.L("all"), e -> users.setValue(new java.util.HashSet<>(options)));
        selectAll.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        TextField title = new TextField(Ui.T("Başlık", "Title"));
        TextArea body = new TextArea(Ui.T("Mesaj", "Message"));
        body.setMinHeight("120px");
        Checkbox email = new Checkbox(Ui.T("E-posta olarak da gönder", "Also send as e-mail"));
        Div box = new Div(users, selectAll, title, body, email);
        box.addClassName("ep-stack");
        users.setWidthFull();
        title.setWidthFull();
        body.setWidthFull();
        d.add(box);
        Button send = new Button(Ui.L("send"), e -> {
            if (users.getValue().isEmpty() || title.getValue().isBlank()) {
                Ui.error(Ui.L("required"));
                return;
            }
            if (Ui.run(() -> Ui.api().post("/api/notifications/send", ApiClient.obj("userIds", users.getValue().stream().map(i -> i.id).toList(),
                    "title", title.getValue(), "body", body.getValue(), "email", email.getValue())), Ui.T("Gönderildi", "Sent"))) d.close();
        });
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), send);
        d.open();
    }
}
