package dev.enterprisepulse.web.views.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.html.Span;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.ui.Ui;
import com.vaadin.flow.component.select.Select;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Shared labels and formatting of the HR screens (leave, attendance). */
public final class HrUi {
    private HrUi() {}

    private record Day(String tr, String en, String symbol, String color) {}

    private static final Map<String, Day> DAYS = new LinkedHashMap<>();

    static {
        DAYS.put("PRESENT", new Day("Geldi", "Present", "✓", "success"));
        DAYS.put("LATE", new Day("Geç geldi", "Late", "G", "warning"));
        DAYS.put("ABSENT", new Day("Gelmedi", "Absent", "×", "error"));
        DAYS.put("INCOMPLETE", new Day("Çıkış yok", "No check-out", "?", "warning"));
        DAYS.put("LEAVE", new Day("İzinli", "On leave", "İ", "info"));
        DAYS.put("HOLIDAY", new Day("Resmi tatil", "Holiday", "T", "info"));
        DAYS.put("OFF", new Day("Tatil günü", "Day off", "–", "contrast"));
        DAYS.put("UPCOMING", new Day("Planlı", "Upcoming", "", "contrast"));
    }

    public static Set<String> statuses() {
        return DAYS.keySet();
    }

    public static String label(String status) {
        Day d = status == null ? null : DAYS.get(status);
        return d == null ? (status == null ? "" : status) : Ui.T(d.tr(), d.en());
    }

    public static String symbol(String status) {
        Day d = status == null ? null : DAYS.get(status);
        return d == null ? "" : d.symbol();
    }

    public static Span badge(String status) {
        Day d = status == null ? null : DAYS.get(status);
        return Ui.badge(label(status), d == null ? "contrast" : d.color());
    }

    public static String dayClass(String status) {
        return "ep-day-" + (status == null ? "off" : status.toLowerCase(Locale.ROOT));
    }

    public static String minutes(int minutes) {
        return minutes <= 0 ? "0:00" : (minutes / 60) + ":" + String.format("%02d", minutes % 60);
    }

    public static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    public static String orDash(JsonNode node) {
        String t = text(node);
        return t == null ? "—" : t;
    }

    public static LocalDate date(JsonNode node) {
        return LocalDate.parse(node.asText().substring(0, 10));
    }

    public static String format(JsonNode node, String pattern) {
        return date(node).format(DateTimeFormatter.ofPattern(pattern, Fmt.locale(Ui.locale())));
    }

    public static String range(JsonNode day) {
        return day.hasNonNull("plannedStart") ? day.get("plannedStart").asText() + "–" + day.path("plannedEnd").asText() : "—";
    }

    public static String worked(JsonNode day) {
        int m = day.path("workedMinutes").asInt();
        return m > 0 ? minutes(m) : "—";
    }

    public static boolean weekend(LocalDate d) {
        return d.getDayOfWeek().getValue() >= 6;
    }

    /** Month picker covering the last two years and the next two months. */
    public static Select<YearMonth> monthSelect(YearMonth value, Consumer<YearMonth> onChange) {
        Select<YearMonth> s = new Select<>();
        YearMonth now = YearMonth.now();
        List<YearMonth> months = new ArrayList<>();
        for (int i = 2; i >= -24; i--) months.add(now.plusMonths(i));
        s.setItems(months);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("MMMM yyyy", Fmt.locale(Ui.locale()));
        s.setItemLabelGenerator(m -> m.format(f));
        s.setValue(value);
        s.setWidth("190px");
        s.addValueChangeListener(e -> {
            if (e.getValue() != null) onChange.accept(e.getValue());
        });
        return s;
    }
}
