package dev.enterprisepulse.web.core;

import dev.enterprisepulse.web.core.Meta.NavGroup;
import dev.enterprisepulse.web.core.Meta.NavItem;
import java.util.Set;

/** Which navigation areas are live; every phase switches on its modules. */
public final class Features {
    private Features() {}

    public static final Set<String> GROUPS = Set.of(
            "Genel", "İnsan Kaynakları", "Zimmet ve Demirbaş", "Onay Süreçleri", "Organizasyon", "Sistem");

    public static final Set<String> PAGES = Set.of(
            "", "workflow/inbox", "roles", "settings",
            "hr/my-leave", "hr/my-attendance", "hr/org-chart", "hr/timesheet",
            "assets/my");

    public static boolean enabled(NavGroup g) {
        return GROUPS.contains(g.label().tr());
    }

    public static boolean enabled(NavItem i) {
        return i.resource() != null || PAGES.contains(i.page() == null ? "" : i.page());
    }
}
