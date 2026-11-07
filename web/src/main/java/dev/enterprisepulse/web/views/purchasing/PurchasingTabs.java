package dev.enterprisepulse.web.views.purchasing;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.ResourceViews.TabContext;
import dev.enterprisepulse.web.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/** Request lines, quote comparison, quote lines and the partner statement. */
public final class PurchasingTabs {
    private PurchasingTabs() {}

    public static Div requestLines(TabContext ctx) {
        JsonNode detail = Ui.api().get("/api/purchasing/requests/" + ctx.record().path("id").asText());
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("lineNo").asInt()).setHeader("#");
        grid.addColumn(n -> n.path("sku").asText("")).setHeader("SKU");
        grid.addColumn(n -> n.path("description").asText()).setHeader(Ui.T("Açıklama", "Description")).setFlexGrow(1);
        grid.addColumn(n -> Fmt.number(n.path("quantity").decimalValue(), 3, "tr")).setHeader(Ui.T("Miktar", "Qty"));
        grid.addColumn(n -> n.path("estimatedPrice").isNull() ? "" : Fmt.money(n.path("estimatedPrice").decimalValue(), "TRY", "tr")).setHeader(Ui.T("Tahmini", "Estimate"));
        return gridOf(grid, detail == null ? null : detail.path("lines"));
    }

    public static Div compare(TabContext ctx) {
        JsonNode data = Ui.api().get("/api/purchasing/requests/" + ctx.record().path("id").asText() + "/compare");
        Div box = new Div();
        box.addClassName("ep-stack");
        if (data == null) return box;
        String best = data.path("bestQuoteId").asText("");
        box.add(Ui.muted(Ui.T("En uygun teklif", "Best quote") + ": " + (best.isBlank() ? "—" : best)));
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("number").asText()).setHeader(Ui.T("Teklif", "Quote"));
        grid.addColumn(n -> n.path("partnerTitle").asText()).setHeader(Ui.T("Tedarikçi", "Supplier")).setFlexGrow(1);
        grid.addColumn(n -> n.path("status").asText()).setHeader(Ui.T("Durum", "Status"));
        grid.addColumn(n -> Fmt.money(n.path("grandTotalBase").decimalValue(), data.path("baseCurrency").asText("TRY"), "tr")).setHeader(Ui.T("Toplam", "Total"));
        grid.addColumn(n -> n.path("complete").asBoolean() ? Ui.T("Tam", "Complete") : "—").setHeader(Ui.T("Kapsam", "Coverage"));
        grid.addItemClickListener(e -> Ui.go("r/supplier-quotes/" + e.getItem().path("id").asText()));
        return gridOf(grid, data.path("quotes"), box);
    }

    public static Div quoteLines(TabContext ctx) {
        JsonNode detail = Ui.api().get("/api/purchasing/quotes/" + ctx.record().path("id").asText());
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("sku").asText("")).setHeader("SKU");
        grid.addColumn(n -> n.path("description").asText()).setHeader(Ui.T("Açıklama", "Description")).setFlexGrow(1);
        grid.addColumn(n -> Fmt.number(n.path("quantity").decimalValue(), 3, "tr")).setHeader(Ui.T("Miktar", "Qty"));
        grid.addColumn(n -> Fmt.money(n.path("unitPrice").decimalValue(), "TRY", "tr")).setHeader(Ui.T("Fiyat", "Price"));
        grid.addColumn(n -> Fmt.money(n.path("lineTotal").decimalValue(), "TRY", "tr")).setHeader(Ui.T("Tutar", "Amount"));
        Div box = new Div();
        if (detail != null) {
            box.add(new Span(Fmt.money(detail.path("totals").path("grandTotal").decimalValue(), detail.path("currencyCode").asText(), "tr")));
        }
        return gridOf(grid, detail == null ? null : detail.path("lines"), box);
    }

    public static Div statement(TabContext ctx) {
        JsonNode data = Ui.api().get("/api/partners/" + ctx.record().path("id").asText() + "/statement");
        Div box = new Div();
        box.addClassName("ep-stack");
        if (data == null) return box;
        String currency = data.path("baseCurrency").asText("TRY");
        Div stats = new Div(
                Ui.stat(Ui.T("Borç", "Debit"), Fmt.money(data.path("totalDebit").decimalValue(), currency, "tr")),
                Ui.stat(Ui.T("Alacak", "Credit"), Fmt.money(data.path("totalCredit").decimalValue(), currency, "tr")),
                Ui.stat(Ui.T("Bakiye", "Balance"), Fmt.money(data.path("closingBalance").decimalValue(), currency, "tr")));
        stats.addClassName("ep-stat-row");
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("entryDate").asText()).setHeader(Ui.T("Tarih", "Date"));
        grid.addColumn(n -> n.path("documentNumber").asText("")).setHeader(Ui.T("Belge", "Document"));
        grid.addColumn(n -> n.path("description").asText("")).setHeader(Ui.T("Açıklama", "Description")).setFlexGrow(1);
        grid.addColumn(n -> Fmt.money(n.path("debitBase").decimalValue(), currency, "tr")).setHeader(Ui.T("Borç", "Debit"));
        grid.addColumn(n -> Fmt.money(n.path("creditBase").decimalValue(), currency, "tr")).setHeader(Ui.T("Alacak", "Credit"));
        grid.addColumn(n -> Fmt.money(n.path("balance").decimalValue(), currency, "tr")).setHeader(Ui.T("Bakiye", "Balance"));
        box.add(stats);
        return gridOf(grid, data.path("entries"), box);
    }

    private static Div gridOf(Grid<JsonNode> grid, JsonNode array) {
        return gridOf(grid, array, new Div());
    }

    private static Div gridOf(Grid<JsonNode> grid, JsonNode array, Div box) {
        List<JsonNode> rows = new ArrayList<>();
        if (array != null) array.forEach(rows::add);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        box.add(grid);
        box.addClassName("ep-stack");
        return box;
    }
}
