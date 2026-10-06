package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.stats.PeriodComparison.Period;
import com.example.bbettercalendar.stats.PeriodComparison.Result;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PeriodComparisonTest {

    private static final double EPS = 1e-9;
    // Miércoles 2026-10-07: semana actual = lun 2026-10-05 .. dom 2026-10-11.
    private static final LocalDate WED = LocalDate.of(2026, 10, 7);

    private static Map<LocalDate, Integer> values(Object... dayValuePairs) {
        Map<LocalDate, Integer> m = new HashMap<>();
        for (int i = 0; i < dayValuePairs.length; i += 2) {
            m.put((LocalDate) dayValuePairs[i], (Integer) dayValuePairs[i + 1]);
        }
        return m;
    }

    private static DailyStat stat(String day, int focus, int fails) {
        DailyStat s = new DailyStat();
        s.day = day;
        s.focusMinutes = focus;
        s.fails = fails;
        return s;
    }

    @Test
    public void week_fullPeriod_deltaAbsoluteAndPercent() {
        Map<LocalDate, Integer> v = values(
                LocalDate.of(2026, 10, 5), 60,   // lun actual
                LocalDate.of(2026, 10, 7), 30,   // mié actual (hoy)
                LocalDate.of(2026, 9, 28), 40,   // lun anterior
                LocalDate.of(2026, 10, 4), 20);  // dom anterior
        Result r = PeriodComparison.compute(v, WED, Period.WEEK, 1, false);
        assertEquals(90, r.currentTotal);
        assertEquals(60, r.previousTotal);
        assertEquals(30, r.deltaAbsolute());
        assertTrue(r.hasDeltaPercent());
        assertEquals(50.0, r.deltaPercent(), EPS);
        assertEquals(LocalDate.of(2026, 10, 5), r.currentStarts[0]);
        assertEquals(LocalDate.of(2026, 9, 28), r.previousStarts[0]);
    }

    @Test
    public void week_toDate_excludesLaterWeekdaysOfPreviousWeek() {
        Map<LocalDate, Integer> v = values(
                LocalDate.of(2026, 10, 6), 50,   // mar actual
                LocalDate.of(2026, 9, 29), 40,   // mar anterior: cuenta
                LocalDate.of(2026, 10, 1), 100,  // jue anterior: fuera (después de "miércoles")
                LocalDate.of(2026, 10, 4), 100); // dom anterior: fuera
        Result r = PeriodComparison.compute(v, WED, Period.WEEK, 1, true);
        assertEquals(50, r.currentTotal);
        assertEquals(40, r.previousTotal);
        assertEquals(25.0, r.deltaPercent(), EPS);

        Result full = PeriodComparison.compute(v, WED, Period.WEEK, 1, false);
        assertEquals(240, full.previousTotal);
        assertEquals(-190, full.deltaAbsolute());
    }

    @Test
    public void gaps_noPreviousData_noPercent() {
        Map<LocalDate, Integer> v = values(LocalDate.of(2026, 10, 5), 25);
        Result r = PeriodComparison.compute(v, WED, Period.WEEK, 1, false);
        assertEquals(25, r.currentTotal);
        assertEquals(0, r.previousTotal);
        assertEquals(25, r.deltaAbsolute());
        assertFalse(r.hasDeltaPercent());
        assertEquals(0.0, r.deltaPercent(), EPS);
    }

    @Test
    public void emptyInput_allZero() {
        Result r = PeriodComparison.compute(new HashMap<LocalDate, Integer>(), WED, Period.MONTH, 3, false);
        assertArrayEquals(new long[]{0, 0, 0}, r.current);
        assertArrayEquals(new long[]{0, 0, 0}, r.previous);
        assertEquals(0, r.deltaAbsolute());
        assertFalse(r.hasDeltaPercent());
    }

    @Test
    public void daysAfterAnchorAndNullsIgnored() {
        Map<LocalDate, Integer> v = values(
                LocalDate.of(2026, 10, 7), 10,
                LocalDate.of(2026, 10, 8), 999,  // mañana
                LocalDate.of(2026, 10, 6), null);
        Result r = PeriodComparison.compute(v, WED, Period.WEEK, 1, false);
        assertEquals(10, r.currentTotal);
    }

    @Test
    public void yearChange_week() {
        LocalDate thu = LocalDate.of(2025, 1, 2); // semana lun 2024-12-30 .. dom 2025-01-05
        Map<LocalDate, Integer> v = values(
                LocalDate.of(2024, 12, 31), 30,  // misma semana, otro año
                LocalDate.of(2025, 1, 1), 20,
                LocalDate.of(2024, 12, 29), 15,  // dom anterior
                LocalDate.of(2024, 12, 23), 5);  // lun anterior
        Result r = PeriodComparison.compute(v, thu, Period.WEEK, 1, false);
        assertEquals(LocalDate.of(2024, 12, 30), r.currentStarts[0]);
        assertEquals(50, r.currentTotal);
        assertEquals(20, r.previousTotal);
    }

    @Test
    public void yearChange_month() {
        LocalDate jan15 = LocalDate.of(2026, 1, 15);
        Map<LocalDate, Integer> v = values(
                LocalDate.of(2026, 1, 3), 10,
                LocalDate.of(2025, 12, 31), 7,
                LocalDate.of(2025, 12, 1), 3,
                LocalDate.of(2025, 11, 30), 1000); // fuera del bloque anterior
        Result r = PeriodComparison.compute(v, jan15, Period.MONTH, 1, false);
        assertEquals(LocalDate.of(2026, 1, 1), r.currentStarts[0]);
        assertEquals(LocalDate.of(2025, 12, 1), r.previousStarts[0]);
        assertEquals(10, r.currentTotal);
        assertEquals(10, r.previousTotal);
        assertEquals(0.0, r.deltaPercent(), EPS);
    }

    @Test
    public void month_toDate_clampsToShorterMonth() {
        Map<LocalDate, Integer> v = values(
                LocalDate.of(2026, 3, 10), 10,
                LocalDate.of(2026, 2, 10), 4,
                LocalDate.of(2026, 2, 28), 6);
        // Ancla 31 de marzo: todo febrero (28 días) cuenta.
        Result end = PeriodComparison.compute(v, LocalDate.of(2026, 3, 31), Period.MONTH, 1, true);
        assertEquals(10, end.previousTotal);
        // Ancla 15 de marzo: sólo hasta el 15 de febrero.
        Result mid = PeriodComparison.compute(v, LocalDate.of(2026, 3, 15), Period.MONTH, 1, true);
        assertEquals(4, mid.previousTotal);
        assertEquals(10, mid.currentTotal);
    }

    @Test
    public void year_toDate() {
        Map<LocalDate, Integer> v = values(
                LocalDate.of(2026, 2, 1), 1,
                LocalDate.of(2025, 3, 1), 2,
                LocalDate.of(2025, 3, 2), 4,   // después del 1 de marzo: fuera en toDate
                LocalDate.of(2025, 12, 31), 8);
        Result r = PeriodComparison.compute(v, LocalDate.of(2026, 3, 1), Period.YEAR, 1, true);
        assertEquals(1, r.currentTotal);
        assertEquals(2, r.previousTotal);
        Result full = PeriodComparison.compute(v, LocalDate.of(2026, 3, 1), Period.YEAR, 1, false);
        assertEquals(14, full.previousTotal);
    }

    @Test
    public void twelveWeeks_bucketOrderAndBounds() {
        LocalDate anchorMonday = LocalDate.of(2026, 10, 5);
        Map<LocalDate, Integer> v = values(
                WED, 1,                              // bucket actual más reciente
                anchorMonday.minusWeeks(11), 2,      // bucket actual más antiguo
                anchorMonday.minusWeeks(12), 3,      // bucket anterior más reciente
                anchorMonday.minusWeeks(23), 4,      // bucket anterior más antiguo
                anchorMonday.minusWeeks(24), 1000);  // fuera
        Result r = PeriodComparison.compute(v, WED, Period.WEEK, 12, false);
        assertEquals(12, r.current.length);
        assertEquals(1, r.current[11]);
        assertEquals(2, r.current[0]);
        assertEquals(3, r.previous[11]);
        assertEquals(4, r.previous[0]);
        assertEquals(3, r.currentTotal);
        assertEquals(7, r.previousTotal);
        assertEquals(anchorMonday, r.currentStarts[11]);
        assertEquals(anchorMonday.minusWeeks(11), r.currentStarts[0]);
        assertEquals(anchorMonday.minusWeeks(12), r.previousStarts[11]);
        assertEquals(anchorMonday.minusWeeks(23), r.previousStarts[0]);
        assertEquals(anchorMonday.minusWeeks(23), PeriodComparison.queryStart(WED, Period.WEEK, 12));
    }

    @Test
    public void todayWithoutSession_liveZeroOverridesAndNoCrash() {
        List<DailyStat> rows = new ArrayList<>(Arrays.asList(
                stat("2026-10-05", 30, 0),
                stat("2026-09-29", 30, 1),
                stat("not-a-date", 99, 0)));
        Map<LocalDate, Integer> v = PeriodComparison.focusMinutesByDay(rows, WED, 0);
        assertEquals(Integer.valueOf(0), v.get(WED));
        Result r = PeriodComparison.compute(v, WED, Period.WEEK, 1, true);
        assertEquals(30, r.currentTotal);
        assertEquals(30, r.previousTotal);
        assertEquals(0, r.deltaAbsolute());
    }

    @Test
    public void liveTodayReplacesStaleRow() {
        List<DailyStat> rows = Arrays.asList(stat("2026-10-07", 5, 0));
        Map<LocalDate, Integer> v = PeriodComparison.focusMinutesByDay(rows, WED, 45);
        assertEquals(Integer.valueOf(45), v.get(WED));
    }

    @Test
    public void byDay_otherMetric() {
        Map<LocalDate, Integer> v = PeriodComparison.byDay(
                Arrays.asList(stat("2026-10-05", 30, 2), stat("2026-09-28", 30, 4)), row -> row.fails);
        Result r = PeriodComparison.compute(v, WED, Period.WEEK, 1, false);
        assertEquals(2, r.currentTotal);
        assertEquals(4, r.previousTotal);
        assertEquals(-50.0, r.deltaPercent(), EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nMustBePositive() {
        PeriodComparison.compute(new HashMap<LocalDate, Integer>(), WED, Period.WEEK, 0, false);
    }
}
