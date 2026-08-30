package com.tweakdapp.backend.business.internal;

import com.tweakdapp.backend.business.internal.entities.BusinessHoursEntity;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The open/closed derivation behind the map's "open now" badge. The cases that matter are the ones
 * a naive {@code opening <= now < closing} comparison gets wrong: shifts that run past midnight,
 * and businesses in a timezone other than the server's.
 */
class OpeningHoursTest {

    private static final String BUCHAREST = "Europe/Bucharest";

    /** A Monday, chosen so weekday arithmetic and the Monday/Sunday wrap-around are both exercised. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3);

    private static BusinessHoursEntity open(DayOfWeek day, String from, String to) {
        BusinessHoursEntity hours = new BusinessHoursEntity();
        hours.setWeekday((short) day.getValue());
        hours.setOpeningHour(LocalTime.parse(from));
        hours.setClosingHour(LocalTime.parse(to));
        hours.setClosed(false);
        return hours;
    }

    private static BusinessHoursEntity closed(DayOfWeek day) {
        BusinessHoursEntity hours = new BusinessHoursEntity();
        hours.setWeekday((short) day.getValue());
        hours.setClosed(true);
        return hours;
    }

    /** An instant at the given local wall-clock time in Bucharest, on the given day of that week. */
    private static Instant at(DayOfWeek day, String time) {
        return MONDAY.with(java.time.temporal.TemporalAdjusters.nextOrSame(day))
                .atTime(LocalTime.parse(time))
                .atZone(ZoneId.of(BUCHAREST))
                .toInstant();
    }

    // ---- ordinary same-day shifts -------------------------------------------

    @Test
    void openDuringItsHours() {
        var week = List.of(open(DayOfWeek.MONDAY, "09:00", "18:00"));

        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.MONDAY, "12:00"))).isTrue();
    }

    @Test
    void closedBeforeOpeningAndAfterClosing() {
        var week = List.of(open(DayOfWeek.MONDAY, "09:00", "18:00"));

        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.MONDAY, "08:59"))).isFalse();
        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.MONDAY, "18:00"))).isFalse();
    }

    @Test
    void openAtTheOpeningMinuteButNotTheClosingMinute() {
        var week = List.of(open(DayOfWeek.MONDAY, "09:00", "18:00"));

        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.MONDAY, "09:00"))).isTrue();
        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.MONDAY, "17:59"))).isTrue();
    }

    @Test
    void aDayWithNoRowIsClosed() {
        var week = List.of(open(DayOfWeek.MONDAY, "09:00", "18:00"));

        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.TUESDAY, "12:00"))).isFalse();
    }

    @Test
    void anExplicitlyClosedDayIsClosed() {
        var week = List.of(closed(DayOfWeek.SUNDAY));

        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.SUNDAY, "12:00"))).isFalse();
    }

    @Test
    void anEmptyScheduleIsClosed() {
        assertThat(OpeningHours.isOpenAt(List.of(), BUCHAREST, at(DayOfWeek.MONDAY, "12:00"))).isFalse();
    }

    // ---- shifts running past midnight ---------------------------------------

    @Test
    void overnightShiftIsOpenBeforeMidnightOnItsOwnDay() {
        var week = List.of(open(DayOfWeek.MONDAY, "22:00", "06:00"));

        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.MONDAY, "23:30"))).isTrue();
    }

    @Test
    void overnightShiftIsOpenAfterMidnightOnTheFollowingDay() {
        var week = List.of(open(DayOfWeek.MONDAY, "22:00", "06:00"));

        // 02:00 Tuesday is covered by Monday's row, not by a Tuesday row.
        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.TUESDAY, "02:00"))).isTrue();
        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.TUESDAY, "06:00"))).isFalse();
    }

    @Test
    void overnightShiftIsClosedInTheGapBetweenShifts() {
        var week = List.of(open(DayOfWeek.MONDAY, "22:00", "06:00"));

        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.MONDAY, "20:00"))).isFalse();
    }

    @Test
    void sundayOvernightShiftSpillsIntoMonday() {
        // The weekday wrap-around: Monday's "yesterday" is Sunday (7), not day 0.
        var week = List.of(open(DayOfWeek.SUNDAY, "23:00", "03:00"));

        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.MONDAY, "01:00"))).isTrue();
    }

    @Test
    void equalOpeningAndClosingMeansOpenAroundTheClock() {
        var week = List.of(open(DayOfWeek.MONDAY, "00:00", "00:00"));

        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.MONDAY, "03:00"))).isTrue();
        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, at(DayOfWeek.MONDAY, "23:59"))).isTrue();
    }

    // ---- timezones -----------------------------------------------------------

    @Test
    void hoursAreInterpretedInTheBusinessTimezoneNotTheServerOne() {
        var week = List.of(open(DayOfWeek.MONDAY, "09:00", "18:00"));
        // 07:00 UTC on Monday = 10:00 in Bucharest (open) but 08:00 in London (still closed).
        Instant sevenUtc = MONDAY.atTime(LocalTime.of(7, 0)).atZone(ZoneId.of("UTC")).toInstant();

        assertThat(OpeningHours.isOpenAt(week, BUCHAREST, sevenUtc)).isTrue();
        assertThat(OpeningHours.isOpenAt(week, "Europe/London", sevenUtc)).isFalse();
    }

    @Test
    void anUnrecognisedTimezoneFallsBackToUtcRatherThanFailing() {
        var week = List.of(open(DayOfWeek.MONDAY, "09:00", "18:00"));
        Instant noonUtc = MONDAY.atTime(LocalTime.NOON).atZone(ZoneId.of("UTC")).toInstant();

        // A bad zone id should be impossible (a CHECK constraint rejects one), but the map must
        // still render if one ever appears.
        assertThat(OpeningHours.isOpenAt(week, "Mars/Olympus_Mons", noonUtc)).isTrue();
        assertThat(OpeningHours.isOpenAt(week, null, noonUtc)).isTrue();
    }
}
