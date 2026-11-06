package dev.enterprisepulse.hr;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

public final class HrRules {
    private HrRules() {}

    public static final class WorkingDays {
        private WorkingDays() {}

        /** Days in [start, end] (inclusive) that are not Saturday, Sunday or a public holiday. */
        public static int count(LocalDate start, LocalDate end, Set<LocalDate> holidays) {
            int days = 0;
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                if (isWeekend(d) || holidays.contains(d)) continue;
                days++;
            }
            return days;
        }

        public static boolean isWeekend(LocalDate d) {
            return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
        }

        /** Annual leave entitlement (Labour Law art. 53) by completed years of service. */
        public static int annualLeaveDays(LocalDate hireDate, int year) {
            int years = year - hireDate.getYear();
            return years >= 15 ? 26 : years >= 5 ? 20 : 14;
        }
    }

    public record ShiftTimes(String code, String startTime, String endTime, int breakMinutes, String color) {
        public ShiftTimes(String code, String startTime, String endTime, int breakMinutes) {
            this(code, startTime, endTime, breakMinutes, null);
        }
    }

    public enum DayStatus { PRESENT, LATE, ABSENT, INCOMPLETE, LEAVE, HOLIDAY, OFF, UPCOMING }

    public record DayResult(DayStatus status, int plannedMinutes, int workedMinutes, int lateMinutes, int overtimeMinutes) {}

    public record Window(int start, int end) {}

    /** Puantaj rules: one employee day from its planned shift and check-in / check-out. */
    public static final class Timesheet {
        private Timesheet() {}

        public static final int DAY_MINUTES = 24 * 60;
        /** Check-ins up to this many minutes after the shift start are not late. */
        public static final int LATE_GRACE_MINUTES = 5;
        /** Extra time below this is not overtime (leaving a few minutes late is routine). */
        public static final int OVERTIME_THRESHOLD_MINUTES = 30;

        /** Schedule of weekdays without a planned shift. */
        public static final ShiftTimes DEFAULT_SHIFT = new ShiftTimes(null, "09:00", "18:00", 60);

        public static int parseClock(String value) {
            String[] parts = value.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        }

        public static boolean isClock(String value) {
            if (value == null || value.length() != 5 || value.charAt(2) != ':') return false;
            try {
                int h = Integer.parseInt(value.substring(0, 2));
                int m = Integer.parseInt(value.substring(3));
                return h >= 0 && h < 24 && m >= 0 && m < 60;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        public static String formatClock(int minutes) {
            int v = ((minutes % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES;
            return String.format("%02d:%02d", v / 60, v % 60);
        }

        /** Start and end in minutes since midnight of the work date; an end before the start is the next day. */
        public static Window window(ShiftTimes s) {
            int start = parseClock(s.startTime());
            int end = parseClock(s.endTime());
            if (end <= start) end += DAY_MINUTES;
            return new Window(start, end);
        }

        public static int plannedMinutes(ShiftTimes s) {
            Window w = window(s);
            return Math.max(0, w.end() - w.start() - s.breakMinutes());
        }

        /** The break is deducted only from spans longer than twice the break. */
        public static int workedMinutes(int checkIn, int checkOut, int breakMinutes) {
            int span = checkOut - checkIn;
            if (span <= 0) return 0;
            return span > breakMinutes * 2 ? span - breakMinutes : span;
        }

        /** @param now current time in minutes since local midnight of the work date (negative for future days) */
        public static DayResult evaluate(ShiftTimes shift, boolean holiday, boolean leave, Integer checkIn, Integer checkOut, int now) {
            int planned = shift != null && !holiday && !leave ? plannedMinutes(shift) : 0;
            int breakMinutes = shift != null ? shift.breakMinutes() : DEFAULT_SHIFT.breakMinutes();
            int worked = checkIn != null && checkOut != null ? workedMinutes(checkIn, checkOut, breakMinutes) : 0;
            int extra = worked - planned;
            int overtime = extra >= OVERTIME_THRESHOLD_MINUTES ? extra : 0;

            if (holiday) return new DayResult(DayStatus.HOLIDAY, planned, worked, 0, overtime);
            if (leave) return new DayResult(DayStatus.LEAVE, planned, worked, 0, overtime);
            if (shift == null) return new DayResult(DayStatus.OFF, planned, worked, 0, overtime);
            Window w = window(shift);
            if (checkIn == null) return new DayResult(now < w.end() ? DayStatus.UPCOMING : DayStatus.ABSENT, planned, worked, 0, overtime);
            int lateBy = checkIn - w.start() > LATE_GRACE_MINUTES ? checkIn - w.start() : 0;
            if (checkOut == null) return new DayResult(DayStatus.INCOMPLETE, planned, worked, lateBy, overtime);
            return new DayResult(lateBy > 0 ? DayStatus.LATE : DayStatus.PRESENT, planned, worked, lateBy, overtime);
        }

        /** Minutes between local midnight of {@code workDate} and the instant. */
        public static int minutesSince(Instant instant, LocalDate workDate, int offsetMinutes) {
            long seconds = Duration.between(workDate.atStartOfDay(ZoneOffset.UTC).toInstant(), instant).getSeconds();
            return (int) Math.floorDiv(seconds, 60) + offsetMinutes;
        }

        public static Instant instantOf(LocalDate workDate, int minutes, int offsetMinutes) {
            return workDate.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds((long) (minutes - offsetMinutes) * 60);
        }
    }
}
