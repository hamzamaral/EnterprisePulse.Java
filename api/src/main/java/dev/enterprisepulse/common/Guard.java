package dev.enterprisepulse.common;

import java.util.Optional;

public final class Guard {
    private Guard() {}

    public static <T> T found(T value) {
        if (value == null) throw AppException.notFound();
        return value;
    }

    public static <T> T found(Optional<T> value) {
        return value.orElseThrow(AppException::notFound);
    }

    public static <T> T found(Optional<T> value, String message) {
        return value.orElseThrow(() -> AppException.notFound(message));
    }

    /** Invalid input: 400. */
    public static void that(boolean condition, String message) {
        if (!condition) throw AppException.badRequest(message);
    }

    /** Invalid state for the operation: 409. */
    public static void state(boolean condition, String message) {
        if (!condition) throw AppException.conflict(message);
    }

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
