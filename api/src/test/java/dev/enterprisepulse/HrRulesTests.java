package dev.enterprisepulse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.enterprisepulse.hr.HrRules.DayStatus;
import dev.enterprisepulse.hr.HrRules.ShiftTimes;
import dev.enterprisepulse.hr.HrRules.Timesheet;
import dev.enterprisepulse.hr.HrRules.Window;
import dev.enterprisepulse.hr.HrRules.WorkingDays;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HrRulesTests {
    private static final ShiftTimes DAY = new ShiftTimes("GND", "09:00", "18:00", 60);
    private static final ShiftTimes NIGHT = new ShiftTimes("GCE", "22:00", "06:00", 30);

    private static int at(String clock) {
        return Timesheet.parseClock(clock);
    }

    @Test
    void workingDaysSkipWeekendsAndHolidays() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 10, 29));
        // Mon 26 Oct – Sun 1 Nov 2026: five weekdays, one of them Republic Day.
        assertThat(WorkingDays.count(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1), holidays)).isEqualTo(4);
        assertThat(WorkingDays.count(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8), holidays)).isZero();
        assertThat(WorkingDays.count(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 2), holidays)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"2024,14", "2021,20", "2011,26"})
    void annualLeaveFollowsSeniority(int hireYear, int days) {
        assertThat(WorkingDays.annualLeaveDays(LocalDate.of(hireYear, 3, 1), 2026)).isEqualTo(days);
    }

    @Test
    void plannedMinutesHandleOvernightShifts() {
        assertThat(Timesheet.plannedMinutes(DAY)).isEqualTo(480);
        assertThat(Timesheet.plannedMinutes(NIGHT)).isEqualTo(450);
        assertThat(Timesheet.window(NIGHT)).isEqualTo(new Window(at("22:00"), at("06:00") + Timesheet.DAY_MINUTES));
    }

    @Test
    void presentWithinGraceAndLateAfter() {
        assertThat(Timesheet.evaluate(DAY, false, false, at("09:05"), at("18:00"), at("20:00")).status()).isEqualTo(DayStatus.PRESENT);
        var late = Timesheet.evaluate(DAY, false, false, at("09:20"), at("18:00"), at("20:00"));
        assertThat(late.status()).isEqualTo(DayStatus.LATE);
        assertThat(late.lateMinutes()).isEqualTo(20);
    }

    @Test
    void overtimeNeedsThreshold() {
        assertThat(Timesheet.evaluate(DAY, false, false, at("09:00"), at("18:20"), at("20:00")).overtimeMinutes()).isZero();
        assertThat(Timesheet.evaluate(DAY, false, false, at("09:00"), at("19:30"), at("20:00")).overtimeMinutes()).isEqualTo(90);
    }

    @Test
    void missingRecordsAreAbsentUpcomingOrIncomplete() {
        assertThat(Timesheet.evaluate(DAY, false, false, null, null, at("19:00")).status()).isEqualTo(DayStatus.ABSENT);
        assertThat(Timesheet.evaluate(DAY, false, false, null, null, at("10:00")).status()).isEqualTo(DayStatus.UPCOMING);
        assertThat(Timesheet.evaluate(DAY, false, false, at("09:00"), null, at("12:00")).status()).isEqualTo(DayStatus.INCOMPLETE);
        assertThat(Timesheet.evaluate(DAY, true, false, null, null, at("19:00")).status()).isEqualTo(DayStatus.HOLIDAY);
        assertThat(Timesheet.evaluate(DAY, false, true, null, null, at("19:00")).status()).isEqualTo(DayStatus.LEAVE);
        assertThat(Timesheet.evaluate(null, false, false, null, null, at("19:00")).status()).isEqualTo(DayStatus.OFF);
    }

    @Test
    void breakIsDeductedOnlyFromLongSpans() {
        assertThat(Timesheet.workedMinutes(at("09:00"), at("10:30"), 60)).isEqualTo(90);
        assertThat(Timesheet.workedMinutes(at("09:00"), at("18:00"), 60)).isEqualTo(480);
    }

    @Test
    void clockHelpers() {
        assertThat(Timesheet.isClock("23:59")).isTrue();
        assertThat(Timesheet.isClock("24:00")).isFalse();
        assertThat(Timesheet.isClock("9:00")).isFalse();
        assertThat(Timesheet.formatClock(Timesheet.DAY_MINUTES + 90)).isEqualTo("01:30");
        assertThat(Timesheet.minutesSince(Instant.parse("2026-11-06T21:00:00Z"), LocalDate.of(2026, 11, 7), 180)).isZero();
    }
}
