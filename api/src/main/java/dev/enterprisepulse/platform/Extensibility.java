package dev.enterprisepulse.platform;

import java.util.List;

public final class Extensibility {
    private Extensibility() {}

    public record DashboardCard(String key, String title, String value, String icon, String hint, String link, String color) {
        public DashboardCard(String key, String title, String value, String icon, String link) {
            this(key, title, value, icon, null, link, "primary");
        }
    }

    public record DashboardSection(String key, String title, List<DashboardCard> cards) {}

    /** A module's contribution to the home dashboard; return null when the user lacks the permission. */
    public interface DashboardProvider {
        int order();

        DashboardSection section();
    }

    /** Recurring job (reminders, SLA checks, depreciation); runs without a user, across all companies. */
    public interface ScheduledJob {
        String id();

        /** Spring cron (6 fields), evaluated in Europe/Istanbul. */
        String cron();

        void run();
    }
}
