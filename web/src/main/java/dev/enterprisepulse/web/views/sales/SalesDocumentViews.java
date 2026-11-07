package dev.enterprisepulse.web.views.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Route(value = "sales/invoices/:id", layout = MainLayout.class)
@RouteAlias(value = "sales/invoices/new", layout = MainLayout.class)
class SalesInvoiceView extends Div implements BeforeEnterObserver {
    private String id;

    SalesInvoiceView() { addClassName("ep-page"); setSizeFull(); }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("new");
        removeAll();
        if ("new".equals(id)) { add(new H2(Ui.T("Yeni satış faturası", "New sales invoice"))); return; }
        JsonNode row = Ui.api().get("/api/sales/invoices/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        HorizontalLayout title = new HorizontalLayout(new H2(row.path("number").asText()), new Span(row.path("paymentStatus").asText()));
        if (row.path("isOverdue").asBoolean()) title.add(new Span(Ui.T("Vadesi geçti", "Overdue")));
        add(title);
        add(new Div(Ui.muted(row.path("partnerTitle").asText() + " · " + row.path("invoiceDate").asText() + " → " + row.path("dueDate").asText()
                + " · " + Ui.T("Açık", "Open") + " " + Fmt.money(row.path("openAmount").decimalValue(), row.path("currencyCode").asText(), "tr"))));
        add(SalesOrderView.lines(row.path("lines"), false));
        if (row.path("canCancel").asBoolean()) add(new Button(Ui.T("İptal", "Cancel"), e -> {
            if (Ui.run(() -> Ui.api().post("/api/sales/invoices/" + id + "/cancel", Map.of("reason", "İptal")))) getUI().ifPresent(ui -> ui.getPage().reload());
        }));
        add(new Button(Ui.T("İade", "Return"), e -> Ui.go("sales/returns/new?invoice=" + id)));
    }
}

@Route(value = "sales/deliveries/:id", layout = MainLayout.class)
@RouteAlias(value = "sales/deliveries/new", layout = MainLayout.class)
class DeliveryView extends Div implements BeforeEnterObserver {
    DeliveryView() { addClassName("ep-page"); setSizeFull(); }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        String id = event.getRouteParameters().get("id").orElse("new");
        removeAll();
        if ("new".equals(id)) { add(new H2(Ui.T("Yeni irsaliye", "New delivery"))); return; }
        JsonNode row = Ui.api().get("/api/sales/deliveries/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("number").asText()));
        add(new Div(Ui.muted(row.path("orderNumber").asText() + " · " + row.path("partnerTitle").asText() + " · " + row.path("warehouseName").asText())));
        if (!row.path("stockMovementId").isMissingNode()) add(new Button(Ui.T("Stok hareketi", "Stock movement") + " " + row.path("stockMovementNumber").asText(),
                e -> Ui.go("inventory/movements/" + row.path("stockMovementId").asText())));
        add(SalesOrderView.lines(row.path("lines"), false));
    }
}

@Route(value = "sales/returns/:id", layout = MainLayout.class)
@RouteAlias(value = "sales/returns/new", layout = MainLayout.class)
class SalesReturnView extends Div implements BeforeEnterObserver {
    SalesReturnView() { addClassName("ep-page"); setSizeFull(); }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        String id = event.getRouteParameters().get("id").orElse("new");
        removeAll();
        if ("new".equals(id)) { add(new H2(Ui.T("Yeni satış iadesi", "New sales return"))); return; }
        JsonNode row = Ui.api().get("/api/sales/returns/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("number").asText()));
        add(new Div(Ui.muted(row.path("invoiceNumber").asText() + " · " + row.path("partnerTitle").asText() + " · " + row.path("reason").asText()
                + " · " + Fmt.money(row.path("grandTotal").decimalValue(), row.path("currencyCode").asText(), "tr"))));
    }
}

@Route(value = "finance/payments/:id", layout = MainLayout.class)
@RouteAlias(value = "finance/payments/new", layout = MainLayout.class)
class PaymentView extends Div implements BeforeEnterObserver {
    PaymentView() { addClassName("ep-page"); setSizeFull(); }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        String id = event.getRouteParameters().get("id").orElse("new");
        removeAll();
        if ("new".equals(id)) { add(new H2(Ui.T("Yeni tahsilat veya ödeme", "New collection or payment"))); return; }
        JsonNode row = Ui.api().get("/api/finance/payments/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("number").asText()));
        add(new Div(Ui.muted(row.path("direction").asText() + " · " + row.path("method").asText() + " · " + row.path("partnerTitle").asText()
                + " · " + Fmt.money(row.path("amount").decimalValue(), row.path("currencyCode").asText(), "tr"))));
        if (!row.path("chequeId").isMissingNode() && !row.path("chequeId").asText().isEmpty()) {
            add(new Button(Ui.T("Çek", "Cheque") + " " + row.path("chequeNumber").asText(), e -> Ui.go("finance/cheques/" + row.path("chequeId").asText())));
        }
    }
}

@Route(value = "finance/cheques/:id", layout = MainLayout.class)
class ChequeView extends Div implements BeforeEnterObserver {
    private String id;

    ChequeView() { addClassName("ep-page"); setSizeFull(); }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("");
        show();
    }

    private void show() {
        removeAll();
        JsonNode row = Ui.api().get("/api/finance/cheques/" + id);
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new H2(row.path("number").asText() + " · " + row.path("status").asText()));
        add(new Div(Ui.muted(row.path("partnerTitle").asText() + " · " + row.path("serialNo").asText() + " · " + row.path("bankName").asText()
                + " · " + Fmt.money(row.path("amount").decimalValue(), row.path("currencyCode").asText(), "tr") + " · " + row.path("dueDate").asText())));
        if ("PORTFOLIO".equals(row.path("status").asText())) {
            RefPicker account = new RefPicker("treasury-accounts", Ui.T("Banka hesabı", "Bank account"));
            Button deposit = new Button(Ui.T("Bankaya ver", "Deposit"), e -> {
                if (account.getValue() == null) return;
                if (Ui.run(() -> Ui.api().post("/api/finance/cheques/" + id + "/deposit", Map.of("accountId", account.getValue().id)), Ui.T("Bankaya verildi", "Deposited"))) show();
            });
            deposit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            add(account, deposit);
        }
        if ("DEPOSITED".equals(row.path("status").asText())) {
            add(new Button(Ui.T("Tahsil et", "Collect"), e -> { if (Ui.run(() -> Ui.api().post("/api/finance/cheques/" + id + "/collect", Map.of()))) show(); }));
            add(new Button(Ui.T("Karşılıksız", "Bounce"), e -> { if (Ui.run(() -> Ui.api().post("/api/finance/cheques/" + id + "/bounce", Map.of("note", "Karşılıksız")))) show(); }));
        }
        if ("ISSUED".equals(row.path("status").asText())) {
            add(new Button(Ui.T("Öde", "Pay"), e -> { if (Ui.run(() -> Ui.api().post("/api/finance/cheques/" + id + "/pay", Map.of()))) show(); }));
        }
        Grid<JsonNode> events = new Grid<>();
        events.addColumn(n -> n.path("eventDate").asText()).setHeader(Ui.T("Tarih", "Date"));
        events.addColumn(n -> n.path("toStatus").asText()).setHeader(Ui.T("Durum", "Status"));
        events.addColumn(n -> n.path("note").asText("")).setHeader(Ui.T("Not", "Note"));
        List<JsonNode> rows = new ArrayList<>();
        row.path("events").forEach(rows::add);
        events.setItems(rows);
        events.setAllRowsVisible(true);
        add(events);
    }
}

@Route(value = "finance/aging", layout = MainLayout.class)
class AgingView extends Div implements BeforeEnterObserver {
    AgingView() { addClassName("ep-page"); setSizeFull(); }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Finans raporları", "Finance reports")));
        Tabs tabs = new Tabs(new Tab(Ui.T("Yaşlandırma", "Aging")), new Tab(Ui.T("Satış", "Sales")), new Tab(Ui.T("Alış", "Purchases")), new Tab(Ui.T("Nakit akışı", "Cash flow")));
        Div body = new Div();
        tabs.addSelectedChangeListener(e -> render(body, tabs.getSelectedIndex()));
        add(tabs, body);
        render(body, 0);
    }

    private void render(Div body, int index) {
        body.removeAll();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(2);
        if (index == 0) aging(body, "sales");
        else if (index == 1) documents(body, "/api/finance/reports/sales?from=" + from + "&to=" + to);
        else if (index == 2) documents(body, "/api/finance/reports/purchases?from=" + from + "&to=" + to);
        else cash(body, from, to);
    }

    private void aging(Div body, String kind) {
        JsonNode report = Ui.api().get("/api/finance/reports/aging?kind=" + kind);
        if (report == null) return;
        HorizontalLayout buckets = new HorizontalLayout();
        report.path("buckets").forEach(b -> buckets.add(new Div(new Div(Ui.muted(b.path("label").asText())), new H2(Fmt.number(b.path("amount").decimalValue(), 2, "tr")))));
        body.add(new HorizontalLayout(new Button(Ui.T("Alacak", "Receivables"), e -> { body.removeAll(); aging(body, "sales"); }),
                new Button(Ui.T("Borç", "Payables"), e -> { body.removeAll(); aging(body, "purchase"); })), buckets);
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("number").asText()).setHeader(Ui.T("Belge", "Document"));
        grid.addColumn(n -> n.path("partner").asText()).setHeader(Ui.T("Cari", "Partner"));
        grid.addColumn(n -> n.path("dueDate").asText()).setHeader(Ui.T("Vade", "Due"));
        grid.addColumn(n -> n.path("days").asInt()).setHeader(Ui.T("Gün", "Days"));
        grid.addColumn(n -> Fmt.number(n.path("openBase").decimalValue(), 2, "tr")).setHeader(Ui.T("Açık (baz)", "Open (base)"));
        List<JsonNode> rows = new ArrayList<>();
        report.path("lines").forEach(rows::add);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        body.add(grid);
    }

    private void documents(Div body, String url) {
        JsonNode report = Ui.api().get(url);
        if (report == null) return;
        body.add(new Div(Ui.muted(Ui.T("Baz toplam", "Base total") + " " + Fmt.number(report.path("grandTotalBase").decimalValue(), 2, "tr"))));
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("number").asText()).setHeader(Ui.T("Belge", "Document"));
        grid.addColumn(n -> n.path("partner").asText()).setHeader(Ui.T("Cari", "Partner"));
        grid.addColumn(n -> n.path("date").asText()).setHeader(Ui.T("Tarih", "Date"));
        grid.addColumn(n -> Fmt.number(n.path("grandTotalBase").decimalValue(), 2, "tr")).setHeader(Ui.T("Baz", "Base"));
        List<JsonNode> rows = new ArrayList<>();
        report.path("lines").forEach(rows::add);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        body.add(grid);
    }

    private void cash(Div body, LocalDate from, LocalDate to) {
        JsonNode report = Ui.api().get("/api/finance/reports/cashflow?from=" + from + "&to=" + to);
        if (report == null) return;
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("code").asText() + " " + n.path("name").asText()).setHeader(Ui.T("Hesap", "Account"));
        grid.addColumn(n -> n.path("currencyCode").asText()).setHeader(Ui.T("Para", "Currency"));
        grid.addColumn(n -> Fmt.number(n.path("amountIn").decimalValue(), 2, "tr")).setHeader(Ui.T("Giriş", "In"));
        grid.addColumn(n -> Fmt.number(n.path("amountOut").decimalValue(), 2, "tr")).setHeader(Ui.T("Çıkış", "Out"));
        grid.addColumn(n -> Fmt.number(n.path("net").decimalValue(), 2, "tr")).setHeader(Ui.T("Net", "Net"));
        List<JsonNode> rows = new ArrayList<>();
        report.path("accounts").forEach(rows::add);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        body.add(grid);
    }
}
