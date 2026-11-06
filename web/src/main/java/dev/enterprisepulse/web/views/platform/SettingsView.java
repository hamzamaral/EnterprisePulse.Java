package dev.enterprisepulse.web.views.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Meta;
import dev.enterprisepulse.web.core.Meta.ResourceMeta;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Ayarlar · EnterprisePulse")
public class SettingsView extends Div implements BeforeEnterObserver {
    private static final Map<String, VaadinIcon> LINKS = new LinkedHashMap<>();

    static {
        LINKS.put("currencies", VaadinIcon.DOLLAR);
        LINKS.put("exchange-rates", VaadinIcon.TRENDING_UP);
        LINKS.put("tax-rates", VaadinIcon.BOOK_PERCENT);
        LINKS.put("units", VaadinIcon.SCALE);
        LINKS.put("number-sequences", VaadinIcon.LIST_OL);
        LINKS.put("workflow-definitions", VaadinIcon.SITEMAP);
        LINKS.put("companies", VaadinIcon.BUILDING);
        LINKS.put("branches", VaadinIcon.SHOP);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Session s = Session.current();
        if (!s.isAuthenticated() || Meta.get() == null) return;
        removeAll();
        add(Ui.pageHeader(Ui.title(Ui.L("settings"))));
        boolean canEdit = s.has("settings:update");
        ComboBox<String> dateFormat = new ComboBox<>(Ui.T("Tarih biçimi", "Date format"), List.of("dd.MM.yyyy", "yyyy-MM-dd", "MM/dd/yyyy"));
        TextField timezone = new TextField(Ui.T("Saat dilimi", "Time zone"));
        IntegerField fiscal = new IntegerField(Ui.T("Mali yıl başlangıç ayı", "Fiscal year start month"));
        fiscal.setMin(1);
        fiscal.setMax(12);
        fiscal.setStepButtonsVisible(true);
        Ui.run(() -> {
            JsonNode g = Ui.api().get("/api/settings/general");
            dateFormat.setValue(g.path("dateFormat").asText("dd.MM.yyyy"));
            timezone.setValue(g.path("timezone").asText("Europe/Istanbul"));
            fiscal.setValue(g.path("fiscalYearStartMonth").asInt(1));
        });
        dateFormat.setEnabled(canEdit);
        timezone.setEnabled(canEdit);
        fiscal.setEnabled(canEdit);
        JsonNode company = s.company();
        Div general = Ui.card(new H3(Ui.T("Genel ayarlar", "General settings") + (company != null ? " · " + company.path("name").asText() : "")),
                dateFormat, timezone, fiscal);
        general.addClassName("ep-stack");
        if (canEdit) {
            Button save = new Button(Ui.L("save"), e -> Ui.run(() -> Ui.api().put("/api/settings/general", ApiClient.obj("dateFormat", dateFormat.getValue(),
                    "timezone", timezone.getValue(), "fiscalYearStartMonth", fiscal.getValue())), Ui.L("saved")));
            save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            general.add(save);
        }
        Div defs = Ui.card(new H3(Ui.T("Tanımlar", "Definitions")));
        defs.addClassName("ep-stack");
        LINKS.forEach((resource, icon) -> {
            ResourceMeta r = Meta.get().get(resource);
            if (r == null || !s.has(r.permissionFor("read", Meta.get().registry()))) return;
            Div item = new Div(icon.create(), new Span(Ui.T(r.label())));
            item.addClassName("ep-list-item");
            item.getElement().addEventListener("click", e -> UI.getCurrent().navigate("r/" + resource));
            defs.add(item);
        });
        Div columns = new Div(general, defs);
        columns.addClassName("ep-columns");
        add(columns);
    }
}
