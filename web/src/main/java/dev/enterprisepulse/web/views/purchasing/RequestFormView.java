package dev.enterprisepulse.web.views.purchasing;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opens a purchase request that then follows the approval workflow. */
@Route(value = "purchasing/requests/new", layout = MainLayout.class)
public class RequestFormView extends Div {
    public RequestFormView() {
        addClassName("ep-page");
        H2 title = new H2(Ui.T("Satın alma talebi", "Purchase request"));
        TextField heading = new TextField(Ui.T("Başlık", "Title"));
        heading.setWidthFull();
        DatePicker needed = new DatePicker(Ui.T("İhtiyaç tarihi", "Needed by"));
        needed.setI18n(Ui.datePickerI18n());
        TextArea notes = new TextArea(Ui.T("Not", "Notes"));
        notes.setWidthFull();
        TextField sku = new TextField("SKU");
        TextField description = new TextField(Ui.T("Serbest açıklama", "Free-text description"));
        BigDecimalField qty = new BigDecimalField(Ui.T("Miktar", "Quantity"));
        qty.setValue(BigDecimal.ONE);
        BigDecimalField price = new BigDecimalField(Ui.T("Tahmini fiyat", "Estimated price"));
        Button save = new Button(Ui.T("Gönder", "Submit"), e -> {
            Map<String, Object> line = new LinkedHashMap<>();
            if (sku.getValue() != null && !sku.getValue().isBlank()) {
                JsonNode items = Ui.api().get("/api/stock/items?includeServices=true&search=" + java.net.URLEncoder.encode(sku.getValue().trim(), java.nio.charset.StandardCharsets.UTF_8));
                if (items == null || items.isEmpty()) {
                    Ui.error(Ui.T("Ürün bulunamadı", "Item not found"));
                    return;
                }
                line.put("variantId", items.get(0).path("variantId").asText());
            }
            if (description.getValue() != null && !description.getValue().isBlank()) line.put("description", description.getValue().trim());
            line.put("quantity", qty.getValue());
            if (price.getValue() != null) line.put("estimatedPrice", price.getValue());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", heading.getValue());
            if (needed.getValue() != null) body.put("neededBy", needed.getValue().toString());
            body.put("notes", notes.getValue());
            body.put("lines", List.of(line));
            if (Ui.run(() -> {
                JsonNode created = Ui.api().post("/api/purchasing/requests", body);
                Ui.go("r/purchase-requests/" + created.path("id").asText());
            }, Ui.T("Talep oluşturuldu", "Request created"))) {
                // navigated
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div box = new Div(heading, needed, notes, sku, description, qty, price, save);
        box.addClassName("ep-stack");
        add(title, box);
    }
}
