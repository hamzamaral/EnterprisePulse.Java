package dev.enterprisepulse.assets;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Straight-line depreciation (doğrusal amortisman) from the purchase month over the useful life. */
public final class Depreciation {
    private Depreciation() {}

    public record Year(int year, int months, BigDecimal depreciation, BigDecimal bookValueEnd) {}

    public record Result(int usefulLifeMonths, BigDecimal monthlyAmount, int elapsedMonths, BigDecimal accumulated, BigDecimal bookValue,
                         boolean fullyDepreciated, List<Year> schedule) {}

    /** Whole months between two dates; a month counts once its day-of-month has been reached. */
    public static int monthsBetween(LocalDate from, LocalDate to) {
        int months = (to.getYear() - from.getYear()) * 12 + (to.getMonthValue() - from.getMonthValue());
        return to.getDayOfMonth() < from.getDayOfMonth() ? months - 1 : months;
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static Result calculate(BigDecimal cost, BigDecimal salvage, int usefulLifeMonths, LocalDate purchaseDate, LocalDate asOf) {
        int life = Math.max(1, usefulLifeMonths);
        BigDecimal depreciable = cost.subtract(salvage).max(BigDecimal.ZERO);
        BigDecimal monthly = depreciable.divide(BigDecimal.valueOf(life), MathContext.DECIMAL128);
        int elapsed = Math.min(life, Math.max(0, monthsBetween(purchaseDate, asOf)));

        List<Year> schedule = new ArrayList<>();
        int consumed = 0;
        for (int year = purchaseDate.getYear(); consumed < life; year++) {
            int available = year == purchaseDate.getYear() ? 13 - purchaseDate.getMonthValue() : 12;
            int months = Math.min(available, life - consumed);
            BigDecimal before = accumulatedFor(consumed, life, depreciable, monthly);
            consumed += months;
            BigDecimal after = accumulatedFor(consumed, life, depreciable, monthly);
            schedule.add(new Year(year, months, after.subtract(before), cost.subtract(after)));
        }
        BigDecimal accumulated = accumulatedFor(elapsed, life, depreciable, monthly);
        return new Result(life, round(monthly), elapsed, accumulated, cost.subtract(accumulated), elapsed >= life, schedule);
    }

    /** The last month absorbs rounding so the book value lands exactly on the salvage value. */
    private static BigDecimal accumulatedFor(int months, int life, BigDecimal depreciable, BigDecimal monthly) {
        return months >= life ? depreciable : round(monthly.multiply(BigDecimal.valueOf(months)));
    }
}
