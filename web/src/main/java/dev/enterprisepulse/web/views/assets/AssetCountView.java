package dev.enterprisepulse.web.views.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.ApiException;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.PromptDialog;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Asset count (demirbaş sayımı): scan QR labels, asset tags or serial numbers; mark lines found or missing; complete. */
@Route(value = "assets/counts/:id", layout = MainLayout.class)
public class AssetCountView extends Div implements BeforeEnterObserver, HasDynamicTitle {
    private String id;
    private JsonNode count;
    private String filter = "all";
    private String lastScanned;
    private final Grid<ObjectNode> grid = new Grid<>();
    private final Div stats = new Div();
    private final Div header = new Div();
    private TextField scanField;

    @Override
    public String getPageTitle() {
        return Ui.T("Sayım", "Count") + " " + (count == null ? "" : count.path("number").asText()) + " · EnterprisePulse";
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("");
        removeAll();
        if (!load()) {
            add(Ui.alert(Ui.L("notFound"), "warning"));
            return;
        }
        build();
        refresh();
    }

    private boolean load() {
        return Ui.run(() -> count = Ui.api().get("/api/assets/counts/" + id));
    }

    private String status() {
        return count.path("status").asText();
    }

    private boolean editable() {
        return "DRAFT".equals(status()) && Session.current().has("assets:count");
    }

    private int n(String field) {
        return count.path(field).asInt();
    }

    private void build() {
        stats.addClassName("ep-card");
        Tabs tabs = new Tabs(new Tab(Ui.T("Tümü", "All")), new Tab(Ui.T("Bulunanlar", "Found")), new Tab(Ui.T("Bulunamayanlar", "Missing")),
                new Tab(Ui.T("Beklenmeyenler", "Unexpected")));
        String[] keys = {"all", "found", "missing", "unexpected"};
        tabs.addSelectedChangeListener(e -> {
            filter = keys[tabs.getSelectedIndex()];
            refreshGrid();
        });
        Div bar = new Div(tabs);
        bar.addClassNames("ep-row-between", "ep-no-print", "ep-count-bar");
        if (editable()) {
            scanField = new TextField();
            scanField.setPlaceholder(Ui.T("QR, demirbaş no veya seri no okutun", "Scan a QR, asset tag or serial number"));
            scanField.setPrefixComponent(VaadinIcon.QRCODE.create());
            scanField.setValueChangeMode(ValueChangeMode.EAGER);
            scanField.setAutofocus(true);
            scanField.setWidth("320px");
            Button scan = new Button(Ui.T("Okut", "Scan"), e -> scan());
            scan.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            scanField.addKeyPressListener(Key.ENTER, e -> scan());
            Div box = new Div(scanField, scan);
            box.addClassName("ep-scan");
            bar.add(box);
        }

        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.addClassName("ep-grid");
        grid.setAllRowsVisible(true);
        grid.setEmptyStateText(Ui.L("noRecords"));
        boolean readAssets = Session.current().has("assets:read");
        grid.addColumn(new ComponentRenderer<>(l -> {
            Component c = readAssets ? new Anchor("r/assets/" + l.path("assetId").asText(), l.path("assetTag").asText()) : new Span(l.path("assetTag").asText());
            c.getElement().getClassList().add("ep-mono");
            return c;
        })).setHeader(Ui.T("Demirbaş no", "Asset tag")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(l -> new Div(new Span(l.path("assetName").asText()), new Div(Ui.muted(AssetUi.text(l.get("categoryName")))))))
                .setHeader(Ui.T("Demirbaş", "Asset")).setAutoWidth(true);
        grid.addColumn(l -> AssetUi.text(l.get("serialNumber"))).setHeader(Ui.T("Seri no", "Serial no")).setAutoWidth(true);
        grid.addColumn(l -> AssetUi.text(l.get("expectedHolder"))).setHeader(Ui.T("Beklenen yer / sorumlu", "Expected holder")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::result)).setHeader(Ui.T("Sonuç", "Result")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::condition)).setHeader(Ui.T("Gözlenen durum", "Observed condition")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(l -> {
            Div d = new Div(new Span(AssetUi.text(l.get("foundByName"))));
            if (l.hasNonNull("foundAt")) d.add(new Div(Ui.muted(AssetUi.stamp(l.get("foundAt")))));
            if (l.hasNonNull("note")) d.add(new Div(new Span(l.get("note").asText())));
            return d;
        })).setHeader(Ui.T("Okutan", "Scanned by")).setFlexGrow(1);
        if (editable()) grid.addColumn(new ComponentRenderer<>(this::lineActions)).setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.setPartNameGenerator(l -> l.path("id").asText().equals(lastScanned) ? "ep-row-scanned" : null);
        add(header, stats, bar, grid);
    }

    private void refresh() {
        UI.getCurrent().getPage().setTitle(getPageTitle());
        header.removeAll();
        header.addClassName("ep-page-header");
        Div titleRow = new Div(Ui.title(Ui.T("Sayım", "Count") + " " + count.path("number").asText()),
                Ui.badge(AssetUi.enumLabel("AssetCountStatus", count.get("status")), Fmt.statusColor(status())));
        titleRow.addClassName("ep-row");
        String scope = Stream.of(count.path("locationName").asText(""), count.path("categoryName").asText("")).filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" · "));
        Div titleBox = Ui.grow(titleRow, Ui.muted((scope.isEmpty() ? Ui.T("Tüm demirbaşlar", "All assets") : scope) + " · " + AssetUi.day(count.get("countDate"))));
        Component back = Ui.iconButton(VaadinIcon.ARROW_LEFT, Ui.L("back"), () -> UI.getCurrent().navigate("r/asset-counts"));
        back.getElement().getClassList().add("ep-no-print");
        Div buttons = new Div(new Button(Ui.T("Fark raporu", "Variance report"), VaadinIcon.PRINT.create(),
                e -> UI.getCurrent().getPage().executeJs("window.print()")));
        if (editable()) {
            Button cancel = new Button(Ui.T("Sayımı iptal et", "Cancel count"), VaadinIcon.CLOSE_CIRCLE.create(), e -> cancel());
            cancel.addThemeVariants(ButtonVariant.LUMO_ERROR);
            Button complete = new Button(Ui.T("Sayımı tamamla", "Complete count"), VaadinIcon.CHECK_SQUARE_O.create(), e -> complete());
            complete.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            buttons.add(cancel, complete);
        }
        buttons.addClassNames("ep-row", "ep-no-print");
        header.add(back, titleBox, buttons);

        stats.removeAll();
        int expected = n("expectedCount"), found = n("foundCount"), missing = n("missingCount"), unexpected = n("unexpectedCount");
        ProgressBar bar = new ProgressBar(0, 1, expected == 0 ? 0 : (double) found / expected);
        bar.addThemeVariants(com.vaadin.flow.component.progressbar.ProgressBarVariant.LUMO_SUCCESS);
        Div by = new Div(new Span(count.path("createdByName").asText()));
        if (count.hasNonNull("completedAt")) by.add(new Div(Ui.muted(AssetUi.stamp(count.get("completedAt")))));
        Div grid4 = new Div(AssetUi.fact(Ui.T("İlerleme", "Progress"), new Span(found + " / " + expected), bar),
                AssetUi.fact(Ui.T("Bulunan", "Found"), value(found, null)),
                AssetUi.fact(Ui.T("Bulunamayan", "Missing"), value(missing, missing > 0 ? "error" : null)),
                AssetUi.fact(Ui.T("Beklenmeyen", "Unexpected"), value(unexpected, unexpected > 0 ? "warning" : null)),
                AssetUi.fact(Ui.T("Sayımı başlatan", "Started by"), by));
        grid4.addClassName("ep-count-stats");
        stats.add(grid4);
        if (count.hasNonNull("notes")) stats.add(new Div(Ui.muted(Ui.T("Notlar", "Notes") + ": "), new Span(count.get("notes").asText())));
        refreshGrid();
    }

    private static Span value(int v, String color) {
        Span s = new Span(String.valueOf(v));
        s.addClassName("ep-stat-value");
        if (color != null) s.addClassName("ep-text-" + color);
        return s;
    }

    private void refreshGrid() {
        List<ObjectNode> rows = new ArrayList<>();
        for (JsonNode l : count.path("lines")) {
            boolean expected = l.path("expected").asBoolean(), found = l.path("found").asBoolean();
            boolean keep = switch (filter) {
                case "found" -> expected && found;
                case "missing" -> expected && !found;
                case "unexpected" -> !expected;
                default -> true;
            };
            if (keep) rows.add((ObjectNode) l);
        }
        grid.setItems(rows);
    }

    private Span result(ObjectNode l) {
        if (!l.path("expected").asBoolean()) return Ui.badge(Ui.T("Beklenmeyen", "Unexpected"), "warning");
        return l.path("found").asBoolean() ? Ui.badge(Ui.T("Bulundu", "Found"), "success") : Ui.badge(Ui.T("Bulunamadı", "Missing"), "error");
    }

    private Component condition(ObjectNode l) {
        if (editable() && l.path("found").asBoolean()) {
            Select<String> s = new Select<>();
            List<String> items = new ArrayList<>(List.of(""));
            items.addAll(AssetUi.CONDITIONS);
            s.setItems(items);
            s.setItemLabelGenerator(v -> v.isEmpty() ? Ui.T("Değişiklik yok", "No change") : Ui.meta().enumLabel("AssetCondition", v, Ui.locale()));
            s.setValue(l.hasNonNull("condition") ? l.get("condition").asText() : "");
            s.setWidth("170px");
            s.addValueChangeListener(e -> {
                if (!e.isFromClient()) return;
                patch(l, e.getValue() == null || e.getValue().isEmpty() ? ApiClient.obj("clearCondition", true) : ApiClient.obj("condition", e.getValue()));
            });
            return s;
        }
        return l.hasNonNull("condition") ? AssetUi.conditionBadge(l.get("condition")) : Ui.muted("—");
    }

    private Div lineActions(ObjectNode l) {
        Div box = new Div();
        if (!l.path("expected").asBoolean()) {
            Button remove = Ui.iconButton(VaadinIcon.TRASH, Ui.T("Sayımdan çıkar", "Remove from count"), () -> {
                if (Ui.run(() -> Ui.api().delete("/api/assets/counts/" + id + "/lines/" + l.path("id").asText()))) reload();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR);
            box.add(remove);
        } else if (l.path("found").asBoolean()) {
            box.add(Ui.iconButton(VaadinIcon.CLOSE_CIRCLE_O, Ui.T("Bulunamadı olarak işaretle", "Mark as missing"), () -> patch(l, ApiClient.obj("found", false))));
        } else {
            Button found = Ui.iconButton(VaadinIcon.CHECK_CIRCLE_O, Ui.T("Bulundu olarak işaretle", "Mark as found"), () -> patch(l, ApiClient.obj("found", true)));
            found.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
            box.add(found);
        }
        box.add(Ui.iconButton(VaadinIcon.COMMENT_O, Ui.T("Not", "Note"), () ->
                PromptDialog.ask(l.path("assetTag").asText() + " · " + Ui.T("Not", "Note"), Ui.T("Not", "Note"), false, null, Ui.L("save"), "primary",
                        note -> patch(l, ApiClient.obj("note", note)))));
        box.addClassName("ep-row-end");
        return box;
    }

    private void patch(ObjectNode line, ObjectNode body) {
        if (Ui.run(() -> Ui.api().patch("/api/assets/counts/" + id + "/lines/" + line.path("id").asText(), body))) reload();
    }

    private void reload() {
        if (load()) refresh();
    }

    private void scan() {
        String code = scanField.getValue() == null ? "" : scanField.getValue().trim();
        if (code.isEmpty()) return;
        try {
            JsonNode res = Ui.api().post("/api/assets/counts/" + id + "/scan", ApiClient.obj("code", code));
            String tag = res.path("line").path("assetTag").asText();
            switch (res.path("result").asText()) {
                case "FOUND" -> Ui.ok(Ui.T(tag + " bulundu", tag + " found"));
                case "ALREADY_FOUND" -> notify(Ui.T(tag + " zaten sayıldı", tag + " was already counted"), NotificationVariant.LUMO_CONTRAST);
                default -> notify(Ui.T(tag + " bu sayımın kapsamında değil; beklenmeyen olarak eklendi", tag + " is outside this count's scope; added as unexpected"),
                        NotificationVariant.LUMO_WARNING);
            }
            lastScanned = res.path("line").path("id").asText();
            scanField.clear();
            reload();
        } catch (ApiException ex) {
            if (ex.status != 401) Ui.error(ex.status == 404 ? Ui.T("\"" + code + "\" ile eşleşen demirbaş yok", "No asset matches \"" + code + "\"") : ex.getMessage());
        }
        scanField.focus();
    }

    private static void notify(String text, NotificationVariant variant) {
        Notification n = Notification.show(text, 3500, Notification.Position.BOTTOM_START);
        n.addThemeVariants(variant);
    }

    private void complete() {
        int missing = n("missingCount");
        Ui.confirm(Ui.T("Bulunamayan " + missing + " demirbaş fark raporunda kalacak. Gözlenen fiziksel durumlar demirbaş kartlarına işlenecek.",
                missing + " missing asset(s) stay on the variance report. Observed conditions are written to the asset cards."), () -> {
            if (Ui.run(() -> count = Ui.api().post("/api/assets/counts/" + id + "/complete", ApiClient.obj("applyConditions", true)),
                    Ui.T("Sayım tamamlandı", "Count completed"))) rebuild();
        });
    }

    private void cancel() {
        Ui.confirm(Ui.T("Okutulan kayıtlar saklanır, sayım kapatılır ve demirbaşlar değişmez.", "Scanned lines are kept, the count is closed and assets stay unchanged."),
                () -> {
                    if (Ui.run(() -> count = Ui.api().post("/api/assets/counts/" + id + "/cancel"), Ui.L("saved"))) rebuild();
                });
    }

    /** Editing controls disappear once the count is closed. */
    private void rebuild() {
        removeAll();
        grid.removeAllColumns();
        build();
        refresh();
    }
}
