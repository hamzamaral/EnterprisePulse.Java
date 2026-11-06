package dev.enterprisepulse.web.views.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Profil · EnterprisePulse")
public class ProfileView extends Div implements BeforeEnterObserver {
    private final Div twoFactor = new Div();

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        render();
    }

    private void render() {
        removeAll();
        Session s = Session.current();
        JsonNode u = s.user();
        add(Ui.pageHeader(Ui.title(Ui.L("profile"))));

        TextField first = new TextField(Ui.T("Ad", "First name"), u.path("firstName").asText(), "");
        TextField last = new TextField(Ui.T("Soyad", "Last name"), u.path("lastName").asText(), "");
        TextField phone = new TextField(Ui.T("Telefon", "Phone"), u.path("phone").asText(""), "");
        TextField email = new TextField(Ui.L("email"), u.path("email").asText(), "");
        email.setEnabled(false);
        ComboBox<String> locale = new ComboBox<>(Ui.L("language"), List.of("tr", "en"));
        locale.setItemLabelGenerator(l -> l.equals("tr") ? "Türkçe" : "English");
        locale.setValue(u.path("locale").asText("tr"));
        Button saveProfile = new Button(Ui.L("save"), e -> {
            if (Ui.run(() -> Ui.api().patch("/api/auth/profile", ApiClient.obj("firstName", first.getValue(), "lastName", last.getValue(),
                    "phone", phone.getValue().isBlank() ? null : phone.getValue(), "locale", locale.getValue())), Ui.L("saved"))) {
                boolean localeChanged = !locale.getValue().equals(s.locale);
                s.locale = locale.getValue();
                Ui.run(s::loadMe);
                if (localeChanged) UI.getCurrent().getPage().reload();
            }
        });
        saveProfile.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div personal = Ui.card(new H3(Ui.T("Kişisel bilgiler", "Personal details")), first, last, phone, email, locale, saveProfile);
        personal.addClassName("ep-stack");

        PasswordField current = new PasswordField(Ui.L("currentPassword"));
        PasswordField next = new PasswordField(Ui.L("newPassword"));
        next.setHelperText(Ui.T("En az 8 karakter, büyük/küçük harf ve rakam", "At least 8 chars with upper, lower case and a digit"));
        current.setValueChangeMode(ValueChangeMode.EAGER);
        next.setValueChangeMode(ValueChangeMode.EAGER);
        Button changePassword = new Button(Ui.L("save"));
        changePassword.setEnabled(false);
        Runnable validate = () -> changePassword.setEnabled(!current.getValue().isEmpty() && next.getValue().length() >= 8);
        current.addValueChangeListener(e -> validate.run());
        next.addValueChangeListener(e -> validate.run());
        changePassword.addClickListener(e -> {
            if (Ui.run(() -> Ui.api().post("/api/auth/change-password", ApiClient.obj("currentPassword", current.getValue(), "newPassword", next.getValue())),
                    Ui.L("saved"))) {
                current.clear();
                next.clear();
            }
        });
        Div password = Ui.card(new H3(Ui.T("Şifre değiştir", "Change password")), current, next, changePassword);
        password.addClassName("ep-stack");

        twoFactor.addClassNames("ep-card", "ep-stack");
        renderTwoFactor(null, null);

        Div right = new Div(password, twoFactor);
        right.addClassName("ep-stack");
        Div columns = new Div(personal, right);
        columns.addClassName("ep-columns");
        add(columns);
    }

    private void renderTwoFactor(String qr, String secret) {
        twoFactor.removeAll();
        Session s = Session.current();
        boolean enabled = s.user().path("twoFactorEnabled").asBoolean();
        Div head = new Div(Ui.grow(new H3(Ui.L("twoFactor"))), Ui.badge(enabled ? Ui.T("Açık", "On") : Ui.T("Kapalı", "Off"), enabled ? "success" : "contrast"));
        head.addClassName("ep-row");
        twoFactor.add(head);
        if (enabled) {
            PasswordField pw = new PasswordField(Ui.L("password"));
            Button disable = new Button(Ui.L("disable"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/auth/2fa/disable", ApiClient.obj("password", pw.getValue())),
                        Ui.T("İki adımlı doğrulama kapatıldı", "Two-factor disabled"))) {
                    Ui.run(s::loadMe);
                    renderTwoFactor(null, null);
                }
            });
            disable.addThemeVariants(ButtonVariant.LUMO_ERROR);
            twoFactor.add(pw, disable);
        } else if (qr == null) {
            Button setup = new Button(Ui.T("Kurulumu başlat", "Start setup"), e -> Ui.run(() -> {
                JsonNode res = Ui.api().post("/api/auth/2fa/setup");
                renderTwoFactor(qrDataUri(res.path("otpAuthUri").asText()), res.path("secret").asText());
            }));
            twoFactor.add(new Paragraph(Ui.T("Google Authenticator, Microsoft Authenticator vb. bir uygulama ile hesabınızı koruyun.",
                    "Protect your account with an authenticator app.")), setup);
        } else {
            Image image = new Image(qr, "QR");
            image.setWidth("180px");
            image.setHeight("180px");
            Span secretText = new Span(secret);
            secretText.addClassName("ep-mono");
            TextField code = new TextField(Ui.L("code2fa"));
            code.setMaxLength(6);
            code.setValueChangeMode(ValueChangeMode.EAGER);
            Button enable = new Button(Ui.L("enable"));
            enable.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            enable.setEnabled(false);
            code.addValueChangeListener(e -> enable.setEnabled(e.getValue().length() == 6));
            enable.addClickListener(e -> {
                if (Ui.run(() -> Ui.api().post("/api/auth/2fa/enable", ApiClient.obj("code", code.getValue())),
                        Ui.T("İki adımlı doğrulama açıldı", "Two-factor enabled"))) {
                    Ui.run(s::loadMe);
                    renderTwoFactor(null, null);
                }
            });
            twoFactor.add(image, secretText, code, enable);
        }
    }

    private static String qrDataUri(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 220, 220);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
