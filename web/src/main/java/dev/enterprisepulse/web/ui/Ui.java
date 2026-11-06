package dev.enterprisepulse.web.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.ApiException;
import dev.enterprisepulse.web.core.Meta;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.core.Texts;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared helpers for views: texts, API calls with error notifications, badges and dialogs. */
public final class Ui {
    private Ui() {}

    @FunctionalInterface
    public interface ApiCall {
        void run();
    }

    public static Session session() {
        return Session.current();
    }

    public static ApiClient api() {
        return Session.current().api();
    }

    public static Meta.Document meta() {
        return Meta.get();
    }

    public static String locale() {
        return Session.current().locale;
    }

    public static String L(String key) {
        return Texts.get(key, locale());
    }

    public static String T(Meta.Text text) {
        return text == null ? "" : text.of(locale());
    }

    public static String T(String tr, String en) {
        return "en".equals(locale()) ? en : tr;
    }

    /** Runs an API call, shows its error as a notification and returns whether it succeeded. */
    public static boolean run(ApiCall action, String success) {
        try {
            action.run();
            if (success != null) ok(success);
            return true;
        } catch (ApiException ex) {
            if (ex.status != 401) error(ex.getMessage());
            return false;
        }
    }

    public static boolean run(ApiCall action) {
        return run(action, null);
    }

    public static void ok(String message) {
        Notification n = Notification.show(message, 3000, Notification.Position.BOTTOM_END);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        Notification n = Notification.show(message, 5000, Notification.Position.BOTTOM_END);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    public static void confirm(String message, Runnable onYes) {
        Dialog d = new Dialog();
        d.setHeaderTitle(L("confirm"));
        d.add(new Span(message));
        Button yes = new Button(L("yes"), e -> {
            d.close();
            onYes.run();
        });
        yes.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        d.getFooter().add(new Button(L("cancel"), e -> d.close()), yes);
        d.open();
    }

    public static Span badge(String text, String color) {
        Span s = new Span(text);
        s.addClassNames("ep-badge", color);
        return s;
    }

    public static Div pageHeader(Component... parts) {
        Div d = new Div(parts);
        d.addClassName("ep-page-header");
        return d;
    }

    public static H2 title(String text) {
        H2 h = new H2(text);
        h.addClassName("ep-title");
        return h;
    }

    public static Div grow(Component... parts) {
        Div d = new Div(parts);
        d.addClassName("ep-grow");
        return d;
    }

    public static Div card(Component... parts) {
        Div d = new Div(parts);
        d.addClassName("ep-card");
        return d;
    }

    public static Div alert(String text, String color) {
        Div d = new Div(new Span(text));
        d.addClassNames("ep-alert", color);
        return d;
    }

    public static Span muted(String text) {
        Span s = new Span(text);
        s.addClassName("ep-muted");
        return s;
    }

    public static Button iconButton(VaadinIcon icon, String tooltip, Runnable click) {
        Button b = new Button(new Icon(icon), e -> click.run());
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON);
        if (tooltip != null) b.setTooltipText(tooltip);
        return b;
    }

    /** Navigates to an app link such as "/r/users?isActive=true" (API links carry a leading slash). */
    public static void go(String link) {
        if (link == null) return;
        String l = link.startsWith("/") ? link.substring(1) : link;
        int q = l.indexOf('?');
        if (q < 0) UI.getCurrent().navigate(l);
        else UI.getCurrent().navigate(l.substring(0, q), QueryParameters.fromString(l.substring(q + 1)));
    }

    private static final Map<String, VaadinIcon> ICONS = Map.ofEntries(
            Map.entry("TaskAlt", VaadinIcon.CHECK_SQUARE_O), Map.entry("People", VaadinIcon.USERS), Map.entry("Person", VaadinIcon.USER),
            Map.entry("Notifications", VaadinIcon.BELL), Map.entry("NotificationsActive", VaadinIcon.BELL), Map.entry("AccountTree", VaadinIcon.SITEMAP),
            Map.entry("HourglassTop", VaadinIcon.HOURGLASS), Map.entry("Badge", VaadinIcon.USER_CARD), Map.entry("EventBusy", VaadinIcon.CALENDAR_USER),
            Map.entry("Event", VaadinIcon.CALENDAR), Map.entry("Cake", VaadinIcon.GIFT), Map.entry("Schedule", VaadinIcon.CLOCK),
            Map.entry("Inventory2", VaadinIcon.PACKAGE), Map.entry("Inventory", VaadinIcon.PACKAGE), Map.entry("Warehouse", VaadinIcon.STORAGE),
            Map.entry("Build", VaadinIcon.WRENCH), Map.entry("Handyman", VaadinIcon.TOOLS), Map.entry("ShoppingCart", VaadinIcon.CART),
            Map.entry("PointOfSale", VaadinIcon.CASH), Map.entry("AccountBalance", VaadinIcon.INSTITUTION), Map.entry("Payments", VaadinIcon.MONEY),
            Map.entry("ReceiptLong", VaadinIcon.FILE_TEXT_O), Map.entry("Receipt", VaadinIcon.FILE_TEXT_O), Map.entry("RequestQuote", VaadinIcon.FILE_TEXT),
            Map.entry("Handshake", VaadinIcon.HANDSHAKE), Map.entry("TrendingUp", VaadinIcon.TRENDING_UP), Map.entry("TrendingDown", VaadinIcon.TRENDING_DOWN),
            Map.entry("Warning", VaadinIcon.WARNING), Map.entry("ErrorOutline", VaadinIcon.EXCLAMATION_CIRCLE_O), Map.entry("SupportAgent", VaadinIcon.HEADSET),
            Map.entry("ViewKanban", VaadinIcon.TASKS), Map.entry("School", VaadinIcon.ACADEMY_CAP), Map.entry("PersonSearch", VaadinIcon.SEARCH),
            Map.entry("Gavel", VaadinIcon.GAVEL), Map.entry("PieChart", VaadinIcon.PIE_CHART), Map.entry("DirectionsCar", VaadinIcon.CAR),
            Map.entry("LocalShipping", VaadinIcon.TRUCK), Map.entry("PrecisionManufacturing", VaadinIcon.FACTORY), Map.entry("Calculate", VaadinIcon.CALC_BOOK),
            Map.entry("BarChart", VaadinIcon.BAR_CHART), Map.entry("CreditCard", VaadinIcon.CREDIT_CARD), Map.entry("Savings", VaadinIcon.PIGGY_BANK),
            Map.entry("Store", VaadinIcon.SHOP), Map.entry("Business", VaadinIcon.BUILDING), Map.entry("QrCode", VaadinIcon.QRCODE),
            Map.entry("Flight", VaadinIcon.AIRPLANE), Map.entry("Category", VaadinIcon.CUBES), Map.entry("Insights", VaadinIcon.CHART));

    public static VaadinIcon icon(String name) {
        return name == null ? VaadinIcon.CHART : ICONS.getOrDefault(name, VaadinIcon.CHART);
    }

    /** Stops a click inside a grid cell from also triggering the row click. */
    public static <T extends Component> T stopClick(T c) {
        c.getElement().addEventListener("click", e -> {}).addEventData("event.stopPropagation()");
        return c;
    }

    /** Sends downloaded bytes to the browser as a file. */
    public static void save(ApiClient.Download file) {
        StreamResource res = new StreamResource(file.fileName(), () -> new ByteArrayInputStream(file.bytes()));
        res.setContentType(file.contentType());
        StreamRegistration reg = VaadinSession.getCurrent().getResourceRegistry().registerResource(res);
        UI.getCurrent().getPage().executeJs(
                "const a=document.createElement('a');a.href=$0;a.download=$1;document.body.appendChild(a);a.click();a.remove();",
                reg.getResourceUri().toString(), file.fileName());
    }

    public static void download(String path) {
        run(() -> save(api().download(path)));
    }

    public static List<ObjectNode> rows(JsonNode array) {
        List<ObjectNode> list = new ArrayList<>();
        if (array != null) for (JsonNode n : array) list.add((ObjectNode) n);
        return list;
    }

    public static Map<String, Map<String, String>> labels(JsonNode node) {
        Map<String, Map<String, String>> map = new HashMap<>();
        if (node == null || !node.isObject()) return map;
        node.fields().forEachRemaining(e -> {
            Map<String, String> inner = new HashMap<>();
            e.getValue().fields().forEachRemaining(x -> inner.put(x.getKey(), x.getValue().asText()));
            map.put(e.getKey(), inner);
        });
        return map;
    }

    public static DatePicker.DatePickerI18n datePickerI18n() {
        DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
        i18n.setDateFormat("dd.MM.yyyy");
        i18n.setFirstDayOfWeek(1);
        if (!"en".equals(locale())) {
            i18n.setMonthNames(List.of("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"));
            i18n.setWeekdays(List.of("Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi"));
            i18n.setWeekdaysShort(List.of("Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"));
            i18n.setToday("Bugün");
            i18n.setCancel("Vazgeç");
        }
        return i18n;
    }
}
