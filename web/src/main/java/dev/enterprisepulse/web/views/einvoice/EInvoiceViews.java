package dev.enterprisepulse.web.views.einvoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Route(value = "e-invoices", layout = MainLayout.class)
class EInvoiceOverviewView extends Div implements BeforeEnterObserver {
    EInvoiceOverviewView() { addClassName("ep-page"); setSizeFull(); }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("e-Fatura / e-Arşiv", "e-Invoice / e-Archive")));
        JsonNode summary = Ui.api().get("/api/einvoices/summary");
        if (summary == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new Div(Ui.muted(Ui.T("Gönderilmemiş", "Unsent") + " " + summary.path("unsent").asText()
                + " · " + Ui.T("Yolda", "In progress") + " " + summary.path("inProgress").asText()
                + " · " + Ui.T("Hata", "Errors") + " " + summary.path("errors").asText()
                + " · " + Ui.T("Yanıtlanacak", "To respond") + " " + summary.path("toRespond").asText())));
        Div actions = new Div();
        actions.getStyle().set("display", "flex").set("gap", "0.5rem");
        if (Session.current().has("einvoice:send")) {
            Button sync = new Button(Ui.T("Entegratörü çalıştır", "Run integrator"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/einvoices/sync"), Ui.T("Senkronize edildi", "Synced"))) getUI().ifPresent(ui -> ui.getPage().reload());
            });
            actions.add(sync);
        }
        if (Session.current().has("einvoice:update")) {
            Button simulate = new Button(Ui.T("Gelen fatura simüle et", "Simulate incoming"), e -> {
                JsonNode created = Ui.api().post("/api/einvoices/simulate-incoming", Map.of());
                if (created != null) UI.getCurrent().navigate("e-invoices/" + created.path("id").asText());
            });
            actions.add(simulate);
        }
        add(actions);
        add(new H2(Ui.T("Gönderilmemiş satış faturaları", "Unsent sales invoices")));
        JsonNode unsent = Ui.api().get("/api/einvoices/unsent");
        Grid<JsonNode> grid = EInvoiceUi.grid(unsent, Ui.T("Fatura", "Invoice"), "number", Ui.T("Cari", "Partner"), "partnerTitle",
                Ui.T("Tarih", "Date"), "invoiceDate", Ui.T("Tutar", "Amount"), "grandTotal", Ui.T("Senaryo", "Profile"), "profile");
        if (Session.current().has("einvoice:send")) {
            grid.addComponentColumn(row -> {
                Button send = new Button(Ui.T("Gönder", "Send"), e -> {
                    JsonNode created = Ui.api().post("/api/einvoices", Map.of("salesInvoiceId", row.path("id").asText()));
                    if (created != null) UI.getCurrent().navigate("e-invoices/" + created.path("id").asText());
                });
                send.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                return send;
            }).setHeader("");
        }
        add(grid);
    }
}

@Route(value = "e-invoices/:id", layout = MainLayout.class)
class EInvoiceDetailView extends Div implements BeforeEnterObserver {
    EInvoiceDetailView() { addClassName("ep-page"); setSizeFull(); }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        String id = event.getRouteParameters().get("id").orElse("");
        JsonNode row = Ui.api().get("/api/einvoices/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("number").asText()));
        add(Ui.badge(row.path("status").asText(), Fmt.statusColor(row.path("status").asText())));
        add(new Div(Ui.muted(row.path("direction").asText() + " · " + row.path("profile").asText() + " · " + row.path("partnerTitle").asText()
                + " · " + Fmt.money(row.path("payableAmount").decimalValue(), row.path("currencyCode").asText(), Ui.locale()))));
        if (row.path("canRespond").asBoolean() && Session.current().has("einvoice:update")) {
            Button accept = new Button(Ui.T("Kabul", "Accept"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/einvoices/" + id + "/respond", Map.of("action", "ACCEPT")), Ui.T("Kabul edildi", "Accepted")))
                    getUI().ifPresent(ui -> ui.getPage().reload());
            });
            accept.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            add(accept);
        }
        if (row.path("canRecord").asBoolean() && Session.current().hasAny(List.of("einvoice:update", "purchasing:create"))) {
            add(new Button(Ui.T("Alış faturası kaydet", "Record purchase invoice"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/einvoices/" + id + "/record"), Ui.T("Kaydedildi", "Recorded")))
                    getUI().ifPresent(ui -> ui.getPage().reload());
            }));
        }
        add(EInvoiceUi.grid(row.path("events"), Ui.T("Durum", "Status"), "status", Ui.T("Mesaj", "Message"), "message",
                Ui.T("Zaman", "Time"), "createdAt"));
    }
}

final class EInvoiceUi {
    private EInvoiceUi() {}

    static Grid<JsonNode> grid(JsonNode rows, String... headerKeyPairs) {
        Grid<JsonNode> grid = new Grid<>();
        for (int i = 0; i + 1 < headerKeyPairs.length; i += 2) {
            String key = headerKeyPairs[i + 1];
            grid.addColumn(n -> n.path(key).asText()).setHeader(headerKeyPairs[i]);
        }
        List<JsonNode> items = new ArrayList<>();
        if (rows != null) rows.forEach(items::add);
        grid.setItems(items);
        grid.setAllRowsVisible(true);
        return grid;
    }
}
