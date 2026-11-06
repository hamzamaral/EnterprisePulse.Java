package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.enterprisepulse.assets.Depreciation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepreciationTests {
    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static Depreciation.Result calc(int cost, int salvage, int life, String from, String asOf) {
        return Depreciation.calculate(BigDecimal.valueOf(cost), BigDecimal.valueOf(salvage), life, d(from), d(asOf));
    }

    @Test
    void monthsBetweenCountsWholeMonthsOnly() {
        assertThat(Depreciation.monthsBetween(d("2026-01-15"), d("2026-03-14"))).isEqualTo(1);
        assertThat(Depreciation.monthsBetween(d("2026-01-15"), d("2026-03-15"))).isEqualTo(2);
        assertThat(Depreciation.monthsBetween(d("2025-11-01"), d("2026-02-01"))).isEqualTo(3);
    }

    @Test
    void depreciatesLinearlyPerMonth() {
        var r = calc(36000, 0, 36, "2025-01-01", "2026-01-01");
        assertThat(r.monthlyAmount()).isEqualByComparingTo("1000");
        assertThat(r.elapsedMonths()).isEqualTo(12);
        assertThat(r.accumulated()).isEqualByComparingTo("12000");
        assertThat(r.bookValue()).isEqualByComparingTo("24000");
        assertThat(r.fullyDepreciated()).isFalse();
    }

    @Test
    void stopsAtSalvageValueAfterUsefulLife() {
        var r = calc(36000, 6000, 36, "2025-01-01", "2030-06-01");
        assertThat(r.elapsedMonths()).isEqualTo(36);
        assertThat(r.bookValue()).isEqualByComparingTo("6000");
        assertThat(r.fullyDepreciated()).isTrue();
    }

    @Test
    void isZeroBeforePurchaseDate() {
        var r = calc(36000, 0, 36, "2025-01-01", "2024-06-01");
        assertThat(r.accumulated()).isEqualByComparingTo("0");
        assertThat(r.bookValue()).isEqualByComparingTo("36000");
    }

    @Test
    void scheduleSplitsAcrossCalendarYearsAndSumsToBase() {
        var r = calc(10000, 0, 24, "2025-10-01", "2026-01-01");
        assertThat(r.schedule().stream().map(y -> y.year() + "/" + y.months()).toList()).isEqualTo(List.of("2025/3", "2026/12", "2027/9"));
        assertThat(r.schedule().stream().map(Depreciation.Year::depreciation).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10000");
        assertThat(r.schedule().get(r.schedule().size() - 1).bookValueEnd()).isEqualByComparingTo("0");
    }

    @Test
    void lastMonthAbsorbsRounding() {
        var r = calc(1000, 0, 3, "2025-01-01", "2026-01-01");
        assertThat(r.monthlyAmount()).isEqualByComparingTo("333.33");
        assertThat(r.bookValue()).isEqualByComparingTo("0");
    }
}
