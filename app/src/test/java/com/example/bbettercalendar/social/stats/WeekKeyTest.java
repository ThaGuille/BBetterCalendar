package com.example.bbettercalendar.social.stats;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;

/** F3-u3: claves de día y semana ISO-8601 (03 §5). */
public class WeekKeyTest {

    @Test
    public void normalDate() {
        LocalDate d = LocalDate.of(2026, 10, 28);
        assertEquals("2026-10-28", WeekKey.dayKey(d));
        assertEquals("2026-W44", WeekKey.weekKey(d));
    }

    @Test
    public void mondayAndSundayShareTheWeek() {
        assertEquals("2026-W44", WeekKey.weekKey(LocalDate.of(2026, 10, 26)));
        assertEquals("2026-W44", WeekKey.weekKey(LocalDate.of(2026, 11, 1)));
        assertEquals("2026-W45", WeekKey.weekKey(LocalDate.of(2026, 11, 2)));
        assertEquals("2026-W43", WeekKey.weekKey(LocalDate.of(2026, 10, 25)));
    }

    @Test
    public void isoYearChange_usesTheWeekBasedYear() {
        assertEquals("2026-12-31", WeekKey.dayKey(LocalDate.of(2026, 12, 31)));
        assertEquals("2026-W53", WeekKey.weekKey(LocalDate.of(2026, 12, 31)));
        // 2027-01-01 es del año civil 2027 pero de la semana 53 de 2026
        assertEquals("2027-01-01", WeekKey.dayKey(LocalDate.of(2027, 1, 1)));
        assertEquals("2026-W53", WeekKey.weekKey(LocalDate.of(2027, 1, 1)));
        assertEquals("2027-W01", WeekKey.weekKey(LocalDate.of(2027, 1, 4)));
    }

    @Test
    public void fiftyThreeWeekYear_coversMondayToSunday() {
        assertEquals("2026-W52", WeekKey.weekKey(LocalDate.of(2026, 12, 27)));
        assertEquals("2026-W53", WeekKey.weekKey(LocalDate.of(2026, 12, 28)));
        assertEquals("2026-W53", WeekKey.weekKey(LocalDate.of(2027, 1, 3)));
    }

    @Test
    public void earlyJanuaryBelongsToThePreviousIsoYear() {
        // 2025-12-29 (lunes) ya es 2026-W01
        assertEquals("2026-W01", WeekKey.weekKey(LocalDate.of(2025, 12, 29)));
        assertEquals("2026-W01", WeekKey.weekKey(LocalDate.of(2026, 1, 1)));
    }

    @Test
    public void keysOf_exposesDayWeekAndPreviousWeek() {
        WeekKey.Keys k = WeekKey.Keys.of(LocalDate.of(2026, 10, 28));
        assertEquals("2026-10-28", k.day);
        assertEquals("2026-W44", k.week);
        assertEquals("2026-W43", k.previousWeek);
    }

    @Test
    public void previousWeek_crossesTheIsoYear() {
        assertEquals("2026-W53", WeekKey.Keys.of(LocalDate.of(2027, 1, 4)).previousWeek);
        assertEquals("2026-W52", WeekKey.Keys.of(LocalDate.of(2026, 12, 28)).previousWeek);
        assertEquals("2025-W52", WeekKey.Keys.of(LocalDate.of(2025, 12, 29)).previousWeek);
    }
}
