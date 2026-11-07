package dev.enterprisepulse.web.views.purchasing;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ResourceViews.ActionContext;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Supplier quote entry, and the dialog that turns an open quote into a draft order. */
@Route(value = "purchasing/quotes/new", layout = MainLayout.class)
public class QuoteFormView extends Div {
    public QuoteFormView() {
        addClassName("ep-page");
        H2 title = new H2(Ui.T("Tedarikçi teklifi", "Supplier quote"));
        RefPicker partner = new RefPicker("business-partners", Ui.T("Tedarikçi", "Supplier"));
        partner.setFilters(Map.of("type__in", "SUPPLIER,BOTH"));
        TextField requestId = new TextField(Ui.T("Talep no (isteğe bağlı)", "Request id (optional)"));
        DatePicker date = new DatePicker(Ui.T("Tarih", "Date"), LocalDate.now());
        date.setI18n(Ui.datePickerI18n());
        IntegerField days = new IntegerField(Ui.T("Teslim günü", "Delivery days"));
        days.setValue(7);
        TextField sku = new TextField("SKU");
        BigDecimalField qty = new BigDecimalField(Ui.T("Miktar", "Qty"));
        qty.setValue(BigDecimal.ONE);
        BigDecimalField price = new BigDecimalField(Ui.T("Birim fiyat", "Unit price"));
        BigDecimalField discount = new BigDecimalField(Ui.T("İskonto %", "Discount %"));
        discount.setValue(BigDecimal.ZERO);
        BigDecimalField vat = new BigDecimalField("KDV %");
        vat.setValue(new BigDecimal("20"));
        Button save = new Button(Ui.T("Kaydet", "Save"), e -> {
            if (partner.getValue() == null) {
                Ui.error(Ui.T("Tedarikçi seçin", "Choose a supplier"));
                return;
            }
            JsonNode items = Ui.api().get("/api/stock/items?includeServices=true&search=" + java.net.URLEncoder.encode(sku.getValue() == null ? "" : sku.getValue().trim(), java.nio.charset.StandardCharsets.UTF_8));
            if (items == null || items.isEmpty()) {
                Ui.error(Ui.T("Ürün bulunamadı", "Item not found"));
                return;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("variantId", items.get(0).path("variantId").asText());
            line.put("quantity", qty.getValue());
            line.put("unitPrice", price.getValue() == null ? BigDecimal.ZERO : price.getValue());
            line.put("discountRate", discount.getValue());
            line.put("vatRate", vat.getValue());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("partnerId", partner.getValue().id);
            if (requestId.getValue() != null && !requestId.getValue().isBlank()) body.put("requestId", requestId.getValue().trim());
            body.put("quoteDate", date.getValue().toString());
            body.put("deliveryDays", days.getValue());
            body.put("currencyCode", "TRY");
            body.put("exchangeRate", 1);
            body.put("lines", List.of(line));
            if (Ui.run(() -> {
                JsonNode created = Ui.api().post("/api/purchasing/quotes", body);
                Ui.go("r/supplier-quotes/" + created.path("id").asText());
            }, Ui.T("Teklif kaydedildi", "Quote saved"))) {
                // navigated
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div box = new Div(partner, requestId, date, days, sku, qty, price, discount, vat, save);
        box.addClassName("ep-stack");
        add(title, box);
    }

    public static Consumer<ActionContext> accept() {
        return ctx -> {
            Dialog d = new Dialog();
            d.setHeaderTitle(Ui.T("Teklifi siparişe çevir", "Turn the quote into an order"));
            RefPicker warehouse = new RefPicker("warehouses", Ui.T("Depo", "Warehouse"));
            warehouse.setWidthFull();
            DatePicker expected = new DatePicker(Ui.T("Beklenen teslim", "Expected"), LocalDate.now().plusDays(7));
            expected.setI18n(Ui.datePickerI18n());
            Button ok = new Button(Ui.T("Kabul et", "Accept"), e -> {
                if (warehouse.getValue() == null) {
                    Ui.error(Ui.T("Depo seçin", "Choose a warehouse"));
                    return;
                }
                if (Ui.run(() -> {
                    JsonNode orderId = Ui.api().post("/api/purchasing/quotes/" + ctx.id() + "/accept",
                            Map.of("warehouseId", warehouse.getValue().id, "expectedDate", expected.getValue().toString()));
                    d.close();
                    Ui.go("purchasing/orders/" + orderId.asText());
                }, Ui.T("Sipariş taslağı oluştu", "Draft order created"))) ctx.reload().run();
            });
            ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            d.add(warehouse, expected);
            d.getFooter().add(new Button(Ui.T("Vazgeç", "Cancel"), ev -> d.close()), ok);
            d.open();
        };
    }
}
