package dev.enterprisepulse.web.views.auth;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.ui.Ui;
import java.util.List;

public final class PasswordViews {
    private PasswordViews() {}

    private static Div card(String title) {
        Div card = new Div(new H2(title));
        card.addClassName("ep-auth-card");
        return card;
    }

    @Route("forgot-password")
    @PageTitle("EnterprisePulse")
    public static class ForgotPasswordView extends Div {
        public ForgotPasswordView() {
            addClassName("ep-auth-bg");
            Div card = card(Ui.L("forgot"));
            EmailField email = new EmailField(Ui.L("email"));
            email.setWidthFull();
            email.setValueChangeMode(ValueChangeMode.EAGER);
            Button send = new Button(Ui.L("sendLink"));
            send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            send.setEnabled(false);
            email.addValueChangeListener(e -> send.setEnabled(!e.getValue().isBlank()));
            send.addClickListener(e -> {
                if (Ui.run(() -> Ui.api().post("/api/auth/forgot-password", ApiClient.obj("email", email.getValue())))) {
                    card.remove(email, send);
                    card.addComponentAtIndex(1, Ui.alert(Ui.T("Hesap varsa sıfırlama bağlantısı e-posta ile gönderildi.",
                            "If the account exists a reset link was e-mailed."), "success"));
                }
            });
            card.add(email, send, new Anchor("login", Ui.L("backToLogin")));
            add(card);
        }
    }

    @Route("reset-password")
    @PageTitle("EnterprisePulse")
    public static class ResetPasswordView extends Div implements BeforeEnterObserver {
        private String token;

        public ResetPasswordView() {
            addClassName("ep-auth-bg");
        }

        @Override
        public void beforeEnter(BeforeEnterEvent event) {
            List<String> t = event.getLocation().getQueryParameters().getParameters().getOrDefault("token", List.of());
            token = t.isEmpty() ? null : t.get(0);
            removeAll();
            Div card = card(Ui.L("resetPassword"));
            PasswordField password = new PasswordField(Ui.L("newPassword"));
            password.setHelperText(Ui.T("En az 8 karakter, büyük/küçük harf ve rakam", "At least 8 chars with upper, lower case and a digit"));
            PasswordField repeat = new PasswordField(Ui.T("Yeni şifre (tekrar)", "Repeat new password"));
            password.setWidthFull();
            repeat.setWidthFull();
            password.setValueChangeMode(ValueChangeMode.EAGER);
            repeat.setValueChangeMode(ValueChangeMode.EAGER);
            Button save = new Button(Ui.L("save"));
            save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            save.setEnabled(false);
            Runnable validate = () -> save.setEnabled(token != null && password.getValue().length() >= 8 && password.getValue().equals(repeat.getValue()));
            password.addValueChangeListener(e -> validate.run());
            repeat.addValueChangeListener(e -> validate.run());
            save.addClickListener(e -> {
                if (Ui.run(() -> Ui.api().post("/api/auth/reset-password", ApiClient.obj("token", token, "password", password.getValue())))) {
                    card.remove(password, repeat, save);
                    card.addComponentAtIndex(1, Ui.alert(Ui.T("Şifreniz güncellendi, giriş yapabilirsiniz.", "Your password was changed, you can sign in."), "success"));
                }
            });
            card.add(password, repeat, save, new Anchor("login", Ui.L("backToLogin")));
            add(card);
        }
    }
}
