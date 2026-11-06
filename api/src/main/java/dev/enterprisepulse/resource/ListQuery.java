package dev.enterprisepulse.resource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListQuery {
    private static final Set<String> RESERVED = Set.of("page", "pageSize", "search", "sort", "order", "format", "ids", "take");

    public int page = 1;
    public int pageSize = 25;
    public String search;
    public String sort;
    public String order;
    /** field=value, field__gte, field__lte, field__gt, field__lt, field__ne, field__in (comma separated), field__null (true/false). */
    public Map<String, String> filters = new LinkedHashMap<>();

    public static ListQuery from(Map<String, List<String>> params) {
        ListQuery q = new ListQuery();
        params.forEach((key, values) -> {
            String v = values.isEmpty() ? "" : values.get(0);
            switch (key) {
                case "page" -> q.page = parse(v, 1);
                case "pageSize" -> q.pageSize = parse(v, 25);
                case "search" -> q.search = v;
                case "sort" -> q.sort = v;
                case "order" -> q.order = v;
                default -> {
                    if (!RESERVED.contains(key) && !v.isEmpty()) q.filters.put(key, v);
                }
            }
        });
        return q;
    }

    private static int parse(String v, int fallback) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
