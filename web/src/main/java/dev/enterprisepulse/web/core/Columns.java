package dev.enterprisepulse.web.core;

import dev.enterprisepulse.web.core.Meta.FieldMeta;
import dev.enterprisepulse.web.core.Meta.ResourceMeta;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class Columns {
    private Columns() {}

    private static final List<String> PREFERRED = List.of("number", "code", "name", "title", "firstName", "lastName", "status", "date", "type");
    private static final Set<String> SKIPPED = Set.of("id", "companyId", "deletedAt", "updatedAt", "createdById", "passwordHash");

    /** Default list columns: label fields first, then the most informative scalar fields. */
    public static List<FieldMeta> of(ResourceMeta r, String exclude, int max) {
        ResourceViews.View view = ResourceViews.of(r.resource());
        if (view.columns != null) {
            return view.columns.stream().map(r::field).filter(Objects::nonNull).filter(f -> !f.name().equals(exclude)).toList();
        }
        return r.fields().stream()
                .filter(f -> !f.hidden() && !f.kind().equals("json") && !f.kind().equals("text") && !f.name().equals(exclude)
                        && !view.hiddenFields.contains(f.name()) && !SKIPPED.contains(f.name()))
                .sorted(Comparator.<FieldMeta>comparingInt(f -> rank(r, f)).thenComparingInt(f -> f.name().equals("createdAt") ? 1 : 0))
                .limit(max)
                .toList();
    }

    public static List<FieldMeta> of(ResourceMeta r) {
        return of(r, null, 8);
    }

    private static int rank(ResourceMeta r, FieldMeta f) {
        if (r.labelFields().contains(f.name())) return 0;
        int i = PREFERRED.indexOf(f.name());
        return i >= 0 ? 1 + i : 20;
    }
}
