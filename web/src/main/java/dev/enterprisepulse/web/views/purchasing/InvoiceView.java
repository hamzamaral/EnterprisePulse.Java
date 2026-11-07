package dev.enterprisepulse.web.views.purchasing;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Purchase invoice matched to received quantities, with cancel while unpaid. */
@Route(value = "purchasing/invoices/new", layout = MainLayout.class)
@RouteAlias(value = "purchasing/invoices/:id", layout = MainLayout.class)
public class InvoiceView extends Div implements BeforeEnterObserver {
    private String id;
    private String orderId;

    public InvoiceView() {
        addClassName("ep-page");
        setSizeFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("new");
        orderId = event.getLocation().getQueryParameters().getSingleParameter("order").orElse(null);
        removeAll();
        if ("new".equals(id)) form();
        else detail();
    }

    private void form() {
        H2 title = new H2(Ui.T("Alış faturası", "Purchase invoice"));
        JsonNode order = orderId == null ? null : Ui.api().get("/api/purchasing/orders/" + orderId);
        if (order == null) {
            add(title, Ui.alert(Ui.T("Sipariş seçilmedi", "No order selected"), "warning"));
            return;
        }
        TextField supplierNo = new TextField(Ui.T("Tedarikçi fatura no", "Supplier invoice no"));
        DatePicker date = new DatePicker(Ui.T("Tarih", "Date"), LocalDate.now());
        date.setI18n(Ui.datePickerI18n());
        DatePicker due = new DatePicker(Ui.T("Vade", "Due"));
        due.setI18n(Ui.datePickerI18n());
        List<JsonNode> pending = new ArrayList<>();
        Map<String, BigDecimalField> qty = new LinkedHashMap<>();
        for (JsonNode line : order.path("lines")) {
            if (line.path("invoiceableQty").decimalValue().signum() <= 0) continue;
            pending.add(line);
            BigDecimalField field = new BigDecimalField();
            field.setValue(line.path("invoiceableQty").decimalValue());
            qty.put(line.path("id").asText(), field);
        }
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("description").asText()).setHeader(Ui.T("Açıklama", "Description")).setFlexGrow(1);
        grid.addColumn(n -> Fmt.number(n.path("invoiceableQty").decimalValue(), 3, "tr")).setHeader(Ui.T("Faturalanabilir", "Invoiceable"));
        grid.addComponentColumn(n -> qty.get(n.path("id").asText())).setHeader(Ui.T("Miktar", "Qty"));
        grid.setItems(pending);
        grid.setAllRowsVisible(true);
        Button save = new Button(Ui.T("Kaydet", "Save"), e -> {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (JsonNode line : pending) {
                BigDecimal amount = qty.get(line.path("id").asText()).getValue();
                if (amount == null || amount.signum() <= 0) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orderLineId", line.path("id").asText());
                row.put("variantId", line.path("variantId").asText());
                row.put("description", line.path("description").asText());
                row.put("quantity", amount);
                row.put("unitPrice", line.path("unitPrice").decimalValue());
                row.put("discountRate", line.path("discountRate").decimalValue());
                row.put("vatRate", line.path("vatRate").decimalValue());
                lines.add(row);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("partnerId", order.path("partnerId").asText());
            body.put("orderId", order.path("id").asText());
            body.put("supplierInvoiceNo", supplierNo.getValue());
            body.put("invoiceDate", date.getValue().toString());
            if (due.getValue() != null) body.put("dueDate", due.getValue().toString());
            body.put("currencyCode", order.path("currencyCode").asText());
            body.put("exchangeRate", order.path("exchangeRate").decimalValue());
            body.put("lines", lines);
            if (Ui.run(() -> id = Ui.api().post("/api/purchasing/invoices", body).path("id").asText(), Ui.T("Fatura kaydedildi", "Invoice saved"))) {
                Ui.go("purchasing/invoices/" + id);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(title, new Div(Ui.muted(order.path("number").asText() + " · " + order.path("partnerTitle").asText())), supplierNo, date, due, grid, save);
    }

    private void detail() {
        JsonNode i = Ui.api().get("/api/purchasing/invoices/" + id);
        if (i == null) {
            add(Ui.alert(Ui.L("notFound"), "warning"));
            return;
        }
        H2 title = new H2(i.path("number").asText() + " · " + i.path("partnerTitle").asText());
        String due = i.path("overdue").asBoolean() ? " · " + Ui.T("Vadesi geçmiş", "Overdue") : "";
        Div meta = new Div(Ui.muted(i.path("supplierInvoiceNo").asText() + " · " + i.path("status").asText() + " · " + i.path("dueDate").asText() + due
                + " · " + Fmt.money(i.path("totals").path("grandTotalBase").decimalValue(), "TRY", "tr")));
        add(title, meta, OrderView.lines(i.path("lines")));
        if (i.path("canCancel").asBoolean()) {
            Button cancel = new Button(Ui.T("İptal et", "Cancel"), e -> {
                if (Ui.run(() -> Ui.api().post("/api/purchasing/invoices/" + id + "/cancel", Map.of("reason", "İptal")), Ui.T("Fatura iptal edildi", "Invoice cancelled"))) {
                    removeAll();
                    detail();
                }
            });
            cancel.addThemeVariants(ButtonVariant.LUMO_ERROR);
            add(cancel);
        }
        if (!i.path("orderId").asText("").isBlank()) add(new Button(Ui.T("Sipariş", "Order"), e -> Ui.go("purchasing/orders/" + i.path("orderId").asText())));
    }
}
