package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.stats.ConsistencyCalculator.Result;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Constancia (plan racha-semanal-y-constancia). */
public class ConsistencyCalculatorTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 5);
    private static final LocalDate WED = MON.plusDays(2);

    private static Set<LocalDate> set(LocalDate... ds) {
        Set<LocalDate> out = new HashSet<>();
        Collections.addAll(out, ds);
        return out;
    }

    @Test
    public void weekInProgress_todayWithoutSessionDoesNotCount() {
        Result r = ConsistencyCalculator.compute(set(MON, MON.plusDays(1)), MON, MON.plusDays(6), WED, false);
        assertEquals(2, r.activeDays);
        assertEquals(2, r.countedDays);
        assertEquals(7, r.totalDays);
        assertEquals(7, r.cells.size());
        assertTrue(r.cells.get(2).pendingToday);
        assertFalse(r.cells.get(2).future);
        assertTrue(r.cells.get(3).future);
        assertEquals(100, r.percent());
    }

    @Test
    public void weekInProgress_todayWithSessionCounts() {
        Result r = ConsistencyCalculator.compute(set(MON, WED), MON, MON.plusDays(6), WED, false);
        assertEquals(2, r.activeDays);
        assertEquals(3, r.countedDays);
        assertFalse(r.cells.get(2).pendingToday);
        assertEquals(0, r.cells.get(1).active);
        assertEquals(1, r.cells.get(1).possible);
    }

    @Test
    public void pastMonth_countsEveryDay() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        Set<LocalDate> days = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            days.add(start.plusDays(i * 3));
        }
        days.add(LocalDate.of(2026, 10, 2)); // fuera del rango
        Result r = ConsistencyCalculator.compute(days, start, end, WED, false);
        assertEquals(10, r.activeDays);
        assertEquals(30, r.countedDays);
        assertEquals(30, r.cells.size());
        assertEquals(33, r.percent());
    }

    @Test
    public void year_oneCellPerWeekClippedToTheYear() {
        LocalDate start = LocalDate.of(2026, 1, 1); // jueves
        LocalDate end = LocalDate.of(2026, 12, 31);
        Result r = ConsistencyCalculator.compute(set(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3)),
                start, end, LocalDate.of(2027, 2, 1), true);
        assertTrue(r.weekly);
        assertEquals(53, r.cells.size());
        assertEquals(4, r.cells.get(0).possible); // jue 1 - dom 4
        assertEquals(2, r.cells.get(0).active);
        assertEquals(0.5f, r.cells.get(0).ratio(), 0.0001f);
        assertEquals(365, r.countedDays);
        assertEquals(365, r.totalDays);
    }

    @Test
    public void emptyPeriodStart_zeroCounted() {
        Result r = ConsistencyCalculator.compute(Collections.<LocalDate>emptySet(), MON, MON.plusDays(6), MON, false);
        assertEquals(0, r.countedDays);
        assertEquals(0, r.percent());
        assertTrue(r.cells.get(0).pendingToday);
    }

    @Test
    public void isoOverload_ignoresGarbage() {
        Result r = ConsistencyCalculator.compute(java.util.Arrays.asList(null, "x", MON.toString()),
                MON, MON.plusDays(6), WED, false);
        assertEquals(1, r.activeDays);
    }

    @Test
    public void activeDaysBetween_inclusiveBounds() {
        Set<LocalDate> days = set(MON, WED, WED.plusDays(10));
        assertEquals(2, ConsistencyCalculator.activeDaysBetween(days, MON, WED));
        assertEquals(1, ConsistencyCalculator.activeDaysBetween(days, MON.plusDays(1), WED));
    }
}
