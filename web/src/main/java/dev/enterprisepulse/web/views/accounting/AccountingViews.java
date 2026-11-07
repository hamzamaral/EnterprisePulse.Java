package dev.enterprisepulse.web.views.accounting;

import com.fasterxml.jackson.databind.JsonNode;
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

@Route(value = "accounting", layout = MainLayout.class)
class AccountingOverviewView extends Div implements BeforeEnterObserver {
    AccountingOverviewView() { addClassName("ep-page"); setSizeFull(); }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Muhasebe", "Accounting")));
        JsonNode summary = Ui.api().get("/api/accounting/summary");
        if (summary == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        String currency = summary.path("currencyCode").asText();
        add(new Div(Ui.muted(Ui.T("Kasa ve banka", "Cash & bank") + " " + Fmt.money(summary.path("cash").decimalValue(), currency, Ui.locale())
                + " · " + Ui.T("Net kâr", "Net profit") + " " + Fmt.money(summary.path("netProfit").decimalValue(), currency, Ui.locale())
                + " · " + Ui.T("Bekleyen", "Pending") + " " + summary.path("pendingCount").asText())));
        if (Session.current().has("accounting:approve")) {
            Button run = new Button(Ui.T("Muhasebeleştir", "Post pending"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/accounting/run"), Ui.T("Muhasebeleştirildi", "Posted"))) getUI().ifPresent(ui -> ui.getPage().reload());
            });
            run.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            add(run);
        }
        JsonNode pending = Ui.api().get("/api/accounting/pending");
        add(AccountingUi.grid(pending, Ui.T("Kaynak", "Source"), "source", Ui.T("Belge", "Document"), "number",
                Ui.T("Tarih", "Date"), "date", Ui.T("Tutar", "Amount"), "amount"));
    }
}

@Route(value = "accounting/trial-balance", layout = MainLayout.class)
class TrialBalanceView extends Div implements BeforeEnterObserver {
    TrialBalanceView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Mizan", "Trial balance")));
        JsonNode row = Ui.api().get("/api/accounting/trial-balance");
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(AccountingUi.grid(row.path("rows"), Ui.T("Hesap", "Account"), "code", Ui.T("Ad", "Name"), "name",
                Ui.T("Borç", "Debit"), "balanceDebit", Ui.T("Alacak", "Credit"), "balanceCredit"));
    }
}

@Route(value = "accounting/balance-sheet", layout = MainLayout.class)
class BalanceSheetView extends Div implements BeforeEnterObserver {
    BalanceSheetView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Bilanço", "Balance sheet")));
        JsonNode row = Ui.api().get("/api/accounting/balance-sheet");
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new Div(Ui.muted(Ui.T("Aktif", "Assets") + " " + row.path("totalAssets").asText()
                + " · " + Ui.T("Pasif", "Liabilities") + " " + row.path("totalLiabilities").asText())));
    }
}

@Route(value = "accounting/income-statement", layout = MainLayout.class)
class IncomeStatementView extends Div implements BeforeEnterObserver {
    IncomeStatementView() { addClassName("ep-page"); setSizeFull(); }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(new H2(Ui.T("Gelir tablosu", "Income statement")));
        JsonNode row = Ui.api().get("/api/accounting/income-statement");
        if (row == null) { add(Ui.alert(Ui.L("notFound"), "warning")); return; }
        add(new Div(Ui.muted(Ui.T("Net satış", "Net sales") + " " + row.path("netSales").asText()
                + " · " + Ui.T("Net kâr", "Net profit") + " " + row.path("netProfit").asText())));
    }
}

final class AccountingUi {
    private AccountingUi() {}

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
