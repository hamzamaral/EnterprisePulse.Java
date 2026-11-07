package dev.enterprisepulse.accounting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Calendar days of the accounting books: Istanbul time. */
public final class AccountingClock {
    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private AccountingClock() {}

    /** Calendar day of a UTC timestamp in Istanbul. */
    public static LocalDate localDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
