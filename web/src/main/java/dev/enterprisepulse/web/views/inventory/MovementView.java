package dev.enterprisepulse.web.views.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Posts a stock movement or shows one that was already posted. */
@Route(value = "inventory/movements/new", layout = MainLayout.class)
@RouteAlias(value = "inventory/movements/:id", layout = MainLayout.class)
public class MovementView extends Div implements BeforeEnterObserver {
    private String id;

    public MovementView() {
        addClassName("ep-page");
        setSizeFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("new");
        removeAll();
        if ("new".equals(id)) form();
        else detail();
    }

    private void form() {
        H2 title = new H2(Ui.T("Yeni stok hareketi", "New stock movement"));
        Select<String> type = new Select<>();
        type.setLabel(Ui.T("Tür", "Type"));
        type.setItems("RECEIPT", "ISSUE", "TRANSFER", "ADJUSTMENT");
        type.setItemLabelGenerator(v -> switch (v) {
            case "RECEIPT" -> Ui.T("Giriş", "Receipt");
            case "ISSUE" -> Ui.T("Çıkış", "Issue");
            case "TRANSFER" -> Ui.T("Transfer", "Transfer");
            default -> Ui.T("Düzeltme", "Adjustment");
        });
        type.setValue("RECEIPT");
        DatePicker date = new DatePicker(Ui.T("Tarih", "Date"), LocalDate.now());
        date.setI18n(Ui.datePickerI18n());
        RefPicker source = new RefPicker("warehouses", Ui.T("Çıkış deposu", "Source warehouse"));
        RefPicker target = new RefPicker("warehouses", Ui.T("Giriş deposu", "Target warehouse"));
        TextField reference = new TextField(Ui.T("Belge no", "Reference"));
        TextArea notes = new TextArea(Ui.T("Not", "Notes"));
        TextField sku = new TextField(Ui.T("Stok kodu", "SKU"));
        BigDecimalField qty = new BigDecimalField(Ui.T("Miktar", "Quantity"));
        qty.setValue(BigDecimal.ONE);
        BigDecimalField cost = new BigDecimalField(Ui.T("Birim maliyet", "Unit cost"));
        TextField lot = new TextField(Ui.T("Lot", "Lot"));
        TextField serials = new TextField(Ui.T("Seri numaraları", "Serial numbers"));
        serials.setHelperText(Ui.T("Virgülle ayırın", "Comma separated"));
        Runnable sync = () -> {
            String t = type.getValue();
            source.setVisible("ISSUE".equals(t) || "TRANSFER".equals(t));
            target.setVisible(!"ISSUE".equals(t));
            cost.setVisible("RECEIPT".equals(t) || "ADJUSTMENT".equals(t));
        };
        type.addValueChangeListener(e -> sync.run());
        sync.run();
        Button save = new Button(Ui.T("İşle", "Post"), e -> {
            JsonNode item = Ui.api().get("/api/stock/items?search=" + java.net.URLEncoder.encode(sku.getValue() == null ? "" : sku.getValue().trim(), java.nio.charset.StandardCharsets.UTF_8));
            if (item == null || item.isEmpty()) {
                Ui.error(Ui.T("Ürün bulunamadı", "Item not found"));
                return;
            }
            String variantId = item.get(0).path("variantId").asText();
            Map<String, Object> line = new java.util.LinkedHashMap<>();
            line.put("variantId", variantId);
            line.put("quantity", qty.getValue());
            if (cost.getValue() != null) line.put("unitCost", cost.getValue());
            if (lot.getValue() != null && !lot.getValue().isBlank()) line.put("lotNo", lot.getValue().trim());
            if (serials.getValue() != null && !serials.getValue().isBlank()) {
                line.put("serialNos", List.of(serials.getValue().split("\\s*,\\s*")));
            }
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("type", type.getValue());
            body.put("movementDate", date.getValue() == null ? LocalDate.now().toString() : date.getValue().toString());
            if (source.getValue() != null) body.put("sourceWarehouseId", source.getValue().id);
            if (target.getValue() != null) body.put("targetWarehouseId", target.getValue().id);
            body.put("reference", reference.getValue());
            body.put("notes", notes.getValue());
            body.put("lines", List.of(line));
            if (Ui.run(() -> {
                JsonNode created = Ui.api().post("/api/stock/movements", body);
                id = created.path("id").asText();
            }, Ui.T("Hareket işlendi", "Movement posted"))) Ui.go("inventory/movements/" + id);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div form = new Div(type, date, source, target, reference, notes, sku, qty, cost, lot, serials, save);
        form.addClassName("ep-stack");
        add(title, form);
    }

    private void detail() {
        JsonNode m = Ui.api().get("/api/stock/movements/" + id);
        if (m == null) {
            add(Ui.alert(Ui.L("notFound"), "warning"));
            return;
        }
        H2 title = new H2(m.path("number").asText() + " · " + m.path("type").asText());
        Div meta = new Div(Ui.muted(m.path("movementDate").asText() + " · " + m.path("sourceWarehouseName").asText("") + " → " + m.path("targetWarehouseName").asText("")));
        if (!m.path("stockCountId").asText("").isBlank()) {
            meta.add(new Button(Ui.T("Sayım", "Count") + " " + m.path("stockCountNumber").asText(), e -> Ui.go("inventory/counts/" + m.path("stockCountId").asText())));
        }
        if (!m.path("goodsReceiptId").asText("").isBlank()) {
            meta.add(new Button(Ui.T("Mal kabul", "Receipt") + " " + m.path("goodsReceiptNumber").asText(), e -> Ui.go("purchasing/receipts/" + m.path("goodsReceiptId").asText())));
        }
        Grid<JsonNode> grid = new Grid<>();
        grid.addColumn(n -> n.path("sku").asText()).setHeader("SKU");
        grid.addColumn(n -> n.path("productName").asText()).setHeader(Ui.T("Ürün", "Product")).setFlexGrow(1);
        grid.addColumn(n -> Fmt.number(n.path("quantity").decimalValue(), 3, "tr")).setHeader(Ui.T("Miktar", "Qty"));
        grid.addColumn(n -> n.path("lotNo").asText("")).setHeader("Lot");
        grid.addColumn(n -> n.path("serialNos").isArray() ? join(n.path("serialNos")) : "").setHeader(Ui.T("Seri", "Serial"));
        List<JsonNode> lines = new ArrayList<>();
        m.path("lines").forEach(lines::add);
        grid.setItems(lines);
        grid.setAllRowsVisible(true);
        add(title, meta, grid);
    }

    private static String join(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asText()));
        return String.join(", ", values);
    }
}
