package dev.enterprisepulse.web.views.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import java.util.List;

@Route("login")
@PageTitle("Giriş · EnterprisePulse")
public class LoginView extends Div implements BeforeEnterObserver {
    private String returnUrl = "";

    public LoginView() {
        addClassName("ep-auth-bg");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> r = event.getLocation().getQueryParameters().getParameters().getOrDefault("returnUrl", List.of());
        String candidate = r.isEmpty() ? "" : r.get(0);
        returnUrl = candidate.startsWith("/") && !candidate.startsWith("//") && !candidate.startsWith("/login") ? candidate.substring(1) : "";
        if (Session.current().isAuthenticated()) {
            event.forwardTo(returnUrl);
            return;
        }
        build();
    }

    private void build() {
        removeAll();
        Session s = Session.current();
        Div card = new Div();
        card.addClassName("ep-auth-card");

        Div brand = new Div(VaadinIcon.CHART_TIMELINE.create(), new H1("EnterprisePulse"),
                Ui.muted(Ui.T("Kurumsal kaynak planlama", "Enterprise resource planning") + " · Java"));
        brand.addClassName("ep-auth-brand");

        EmailField email = new EmailField(Ui.L("email"));
        email.setWidthFull();
        email.getElement().setAttribute("autocomplete", "username");
        PasswordField password = new PasswordField(Ui.L("password"));
        password.setWidthFull();
        password.getElement().setAttribute("autocomplete", "current-password");
        TextField code = new TextField(Ui.L("code2fa"));
        code.setWidthFull();
        code.setMaxLength(6);
        code.setHelperText(Ui.T("Kimlik doğrulayıcı uygulamadaki 6 haneli kod", "6-digit code from your authenticator app"));
        code.setVisible(false);

        Button submit = new Button(Ui.L("login"));
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        submit.setWidthFull();
        submit.addClickShortcut(Key.ENTER);
        submit.addClickListener(e -> {
            submit.setEnabled(false);
            Ui.run(() -> {
                JsonNode res = s.api().post("/api/auth/login", ApiClient.obj("email", email.getValue(), "password", password.getValue(),
                        "code", code.getValue().isBlank() ? null : code.getValue()));
                if (res.path("requiresTwoFactor").asBoolean()) {
                    code.setVisible(true);
                    code.focus();
                    return;
                }
                s.signIn(res.path("accessToken").asText(), res.path("refreshToken").asText());
                UI.getCurrent().navigate(returnUrl);
            });
            submit.setEnabled(true);
        });

        Anchor forgot = new Anchor("forgot-password", Ui.L("forgot"));
        Button lang = new Button("tr".equals(s.locale) ? "English" : "Türkçe", e -> {
            s.locale = "tr".equals(s.locale) ? "en" : "tr";
            build();
        });
        lang.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        Div links = new Div(forgot, lang);
        links.addClassName("ep-row-between");

        Div demo = new Div();
        demo.addClassName("ep-row");
        demo.add(demoChip("Admin", "admin@enterprisepulse.dev", "Admin123!", email, password),
                demoChip(Ui.T("Müdür", "Manager"), "manager@enterprisepulse.dev", "Manager123!", email, password),
                demoChip(Ui.T("Personel", "Employee"), "employee@enterprisepulse.dev", "Employee123!", email, password));

        Div divider = new Div();
        divider.addClassName("ep-divider");
        card.add(brand, email, password, code, submit, links, divider, Ui.muted(Ui.L("demoAccounts")), demo);
        add(card);
        email.focus();
    }

    private static Span demoChip(String label, String mail, String pass, EmailField email, PasswordField password) {
        Span chip = new Span(label);
        chip.addClassName("ep-chip");
        chip.getElement().addEventListener("click", e -> {
            email.setValue(mail);
            password.setValue(pass);
        });
        return chip;
    }
}
