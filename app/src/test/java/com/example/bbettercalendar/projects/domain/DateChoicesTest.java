package com.example.bbettercalendar.projects.domain;

import static com.example.bbettercalendar.projects.domain.Tasks.ZONE;
import static com.example.bbettercalendar.projects.domain.Tasks.at;
import static com.example.bbettercalendar.projects.domain.Tasks.day;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DateChoicesTest {

    // Tuesday 2026-11-24
    private static final long TUESDAY = at(2026, 11, 24, 15, 30);

    @Test
    public void startOfToday_dropsTheTimeOfDay() {
        assertEquals(day(2026, 11, 24), DateChoices.startOfToday(TUESDAY, ZONE));
    }

    @Test
    public void tomorrow_isNextDayAtMidnight() {
        assertEquals(day(2026, 11, 25), DateChoices.tomorrow(TUESDAY, ZONE));
    }

    @Test
    public void tomorrow_crossesTheMonthEnd() {
        assertEquals(day(2026, 12, 1), DateChoices.tomorrow(at(2026, 11, 30, 23, 59), ZONE));
    }

    @Test
    public void nextSaturday_fromTuesdayIsThisWeeksSaturday() {
        assertEquals(day(2026, 11, 28), DateChoices.nextSaturday(TUESDAY, ZONE));
    }

    @Test
    public void nextSaturday_calledOnASaturdayIsTheFollowingWeek() {
        assertEquals(day(2026, 12, 5), DateChoices.nextSaturday(at(2026, 11, 28, 10, 0), ZONE));
    }

    @Test
    public void nextSaturday_fromSundayIsSixDaysAhead() {
        assertEquals(day(2026, 12, 5), DateChoices.nextSaturday(at(2026, 11, 29, 10, 0), ZONE));
    }

    @Test
    public void endOfMonth_isTheLastDay() {
        assertEquals(day(2026, 11, 30), DateChoices.endOfMonth(TUESDAY, ZONE));
        assertEquals(day(2028, 2, 29), DateChoices.endOfMonth(at(2028, 2, 3, 9, 0), ZONE));
    }

    @Test
    public void inOneMonth_sameDayNextMonth() {
        assertEquals(day(2026, 12, 24), DateChoices.inOneMonth(TUESDAY, ZONE));
    }

    @Test
    public void inOneMonth_clampsToTheShorterMonth() {
        assertEquals(day(2027, 2, 28), DateChoices.inOneMonth(at(2027, 1, 31, 9, 0), ZONE));
    }

    @Test
    public void choicesAreLocalMidnightsAcrossADstChange() {
        // Madrid leaves summer time on 2026-10-25: that day has 25 hours.
        long start = DateChoices.startOfToday(at(2026, 10, 25, 12, 0), ZONE);
        long next = DateChoices.tomorrow(at(2026, 10, 25, 12, 0), ZONE);
        assertEquals(25L * 3_600_000L, next - start);
    }
}
