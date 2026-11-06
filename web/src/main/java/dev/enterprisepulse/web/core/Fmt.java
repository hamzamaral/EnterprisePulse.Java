package dev.enterprisepulse.web.core;

import com.fasterxml.jackson.databind.JsonNode;
import dev.enterprisepulse.web.core.Meta.FieldMeta;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Fmt {
    private Fmt() {}

    public static final Locale TR = Locale.forLanguageTag("tr-TR");
    public static final ZoneId ZONE = ZoneId.systemDefault();
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static Locale locale(String locale) {
        return "en".equals(locale) ? Locale.US : TR;
    }

    public static String number(BigDecimal value, int scale, String locale) {
        if (value == null) return "";
        NumberFormat nf = NumberFormat.getNumberInstance(locale(locale));
        nf.setMinimumFractionDigits(scale);
        nf.setMaximumFractionDigits(scale);
        return nf.format(value);
    }

    public static String money(BigDecimal value, String currency, String locale) {
        return value == null ? "" : number(value, 2, locale) + (currency != null ? " " + currency : "");
    }

    public static Instant instant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(raw).atZone(ZoneId.of("UTC")).toInstant();
            } catch (DateTimeParseException ex2) {
                return null;
            }
        }
    }

    public static LocalDate date(String raw) {
        if (raw == null || raw.length() < 10) return null;
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public static String date(JsonNode node) {
        LocalDate d = node == null || node.isNull() ? null : date(node.asText());
        return d == null ? "" : d.format(DATE);
    }

    public static String dateTime(JsonNode node) {
        Instant i = node == null || node.isNull() ? null : instant(node.asText());
        return i == null ? "" : i.atZone(ZONE).format(DATE_TIME);
    }

    public static String raw(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return node.isValueNode() ? node.asText() : node.toString();
    }

    public static String value(FieldMeta f, JsonNode node, Meta.Document meta, String locale, Map<String, Map<String, String>> labels) {
        String raw = raw(node);
        if (raw == null) return "";
        if (f.ref() != null && labels != null && labels.containsKey(f.name())) {
            String label = labels.get(f.name()).get(raw);
            if (label != null) return label;
        }
        switch (f.kind()) {
            case "enum":
                return meta.enumLabel(f.enumName(), raw, locale);
            case "bool":
                return Texts.get(node.asBoolean() ? "yes" : "no", locale);
            case "date":
                return date(node);
            case "datetime":
                return dateTime(node);
            case "decimal":
                try {
                    int scale = f.scale() != null && f.scale() > 2 ? f.scale() : 2;
                    return number(new BigDecimal(raw), scale, locale);
                } catch (NumberFormatException ex) {
                    return raw;
                }
            default:
                return raw;
        }
    }

    private static final Set<String> SUCCESS = Set.of("APPROVED", "ACTIVE", "COMPLETED", "PAID", "POSTED", "DONE", "DELIVERED", "RECEIVED",
            "CLOSED", "ACCEPTED", "HIRED", "IN_USE", "SENT");
    private static final Set<String> WARNING = Set.of("PENDING", "SUBMITTED", "IN_PROGRESS", "OPEN", "PARTIAL", "PARTIALLY_PAID",
            "PARTIALLY_RECEIVED", "PARTIALLY_DELIVERED", "SCHEDULED", "IN_REVIEW");
    private static final Set<String> ERROR = Set.of("REJECTED", "CANCELLED", "OVERDUE", "LOST", "FAILED", "RETIRED", "DISPOSED",
            "TERMINATED", "EXPIRED", "VOID");

    /** CSS modifier for status badges: success, warning, error, contrast or info. */
    public static String statusColor(String value) {
        if (value == null) return "info";
        if (SUCCESS.contains(value)) return "success";
        if (WARNING.contains(value)) return "warning";
        if (ERROR.contains(value)) return "error";
        if (value.equals("DRAFT")) return "contrast";
        return "info";
    }
}
