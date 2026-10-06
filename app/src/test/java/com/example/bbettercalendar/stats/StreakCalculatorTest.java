package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.stats.StreakCalculator.DayState;
import com.example.bbettercalendar.stats.StreakCalculator.Result;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StreakCalculatorTest {

    // Miércoles 2026-10-07 (comprobado en weekRow_midWeek).
    private static final LocalDate WED = LocalDate.of(2026, 10, 7);

    private static List<String> days(LocalDate... ds) {
        List<String> out = new ArrayList<>();
        for (LocalDate d : ds) {
            out.add(d.toString());
        }
        return out;
    }

    @Test
    public void empty_isZeroEverywhere() {
        Result r = StreakCalculator.compute(Collections.<String>emptyList(), WED);
        assertEquals(0, r.currentStreak);
        assertEquals(0, r.bestStreak);
        assertFalse(r.activeToday);
        assertEquals(7, r.week.size());
    }

    @Test
    public void consecutiveEndingToday() {
        Result r = StreakCalculator.compute(days(WED, WED.minusDays(1), WED.minusDays(2)), WED);
        assertEquals(3, r.currentStreak);
        assertEquals(3, r.bestStreak);
        assertTrue(r.activeToday);
    }

    @Test
    public void todayWithoutSession_streakStillAliveThroughYesterday() {
        Result r = StreakCalculator.compute(
                days(WED.minusDays(1), WED.minusDays(2), WED.minusDays(3)), WED);
        assertEquals(3, r.currentStreak);
        assertFalse(r.activeToday);
        assertEquals(DayState.PENDING, r.week.get(2).state);
        assertTrue(r.week.get(2).isToday);
    }

    @Test
    public void todayAndYesterdayWithoutSession_streakBroken_bestKept() {
        Result r = StreakCalculator.compute(
                days(WED.minusDays(2), WED.minusDays(3), WED.minusDays(4)), WED);
        assertEquals(0, r.currentStreak);
        assertEquals(3, r.bestStreak);
    }

    @Test
    public void gaps_bestFromOlderRun() {
        // Racha antigua de 5, hueco, racha actual de 2.
        LocalDate t = WED;
        Result r = StreakCalculator.compute(days(
                t, t.minusDays(1),
                t.minusDays(10), t.minusDays(11), t.minusDays(12), t.minusDays(13), t.minusDays(14),
                t.minusDays(20)), t);
        assertEquals(2, r.currentStreak);
        assertEquals(5, r.bestStreak);
    }

    @Test
    public void duplicatesAndUnorderedInput() {
        Result r = StreakCalculator.compute(Arrays.asList(
                WED.toString(), WED.minusDays(2).toString(), WED.toString(), WED.minusDays(1).toString()), WED);
        assertEquals(3, r.currentStreak);
        assertEquals(3, r.bestStreak);
    }

    @Test
    public void yearChange_streakCrossesNewYear() {
        LocalDate jan2 = LocalDate.of(2025, 1, 2);
        Result r = StreakCalculator.compute(days(
                LocalDate.of(2024, 12, 30), LocalDate.of(2024, 12, 31),
                LocalDate.of(2025, 1, 1), jan2), jan2);
        assertEquals(4, r.currentStreak);
        assertEquals(4, r.bestStreak);
        // La semana de 2025-01-02 (jueves) empieza el lunes 2024-12-30.
        assertEquals(LocalDate.of(2024, 12, 30), r.week.get(0).date);
        assertEquals(DayState.DONE, r.week.get(0).state);
        assertEquals(DayState.DONE, r.week.get(3).state);
        assertEquals(DayState.FUTURE, r.week.get(4).state);
    }

    @Test
    public void yearChange_leapDay() {
        LocalDate mar1 = LocalDate.of(2024, 3, 1);
        Result r = StreakCalculator.compute(days(
                LocalDate.of(2024, 2, 28), LocalDate.of(2024, 2, 29), mar1), mar1);
        assertEquals(3, r.currentStreak);
    }

    @Test
    public void weekRow_midWeek() {
        assertEquals(DayOfWeek.WEDNESDAY, WED.getDayOfWeek());
        Result r = StreakCalculator.compute(days(WED.minusDays(2), WED), WED); // lunes y miércoles
        List<StreakCalculator.WeekDay> w = r.week;
        assertEquals(DayOfWeek.MONDAY, w.get(0).date.getDayOfWeek());
        assertEquals(DayOfWeek.SUNDAY, w.get(6).date.getDayOfWeek());
        assertEquals(DayState.DONE, w.get(0).state);
        assertEquals(DayState.MISSED, w.get(1).state);
        assertEquals(DayState.DONE, w.get(2).state);
        assertTrue(w.get(2).isToday);
        assertFalse(w.get(1).isToday);
        for (int i = 3; i < 7; i++) {
            assertEquals(DayState.FUTURE, w.get(i).state);
        }
    }

    @Test
    public void weekRow_mondayAndSundayEdges() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        Result m = StreakCalculator.compute(Collections.<String>emptyList(), monday);
        assertEquals(monday, m.week.get(0).date);
        assertEquals(DayState.PENDING, m.week.get(0).state);
        assertEquals(DayState.FUTURE, m.week.get(1).state);

        LocalDate sunday = LocalDate.of(2026, 10, 11);
        Result s = StreakCalculator.compute(days(sunday), sunday);
        assertEquals(monday, s.week.get(0).date);
        assertEquals(DayState.MISSED, s.week.get(5).state);
        assertEquals(DayState.DONE, s.week.get(6).state);
        assertTrue(s.week.get(6).isToday);
    }

    @Test
    public void malformedAndNullDaysIgnored() {
        Result r = StreakCalculator.compute(Arrays.asList(null, "garbage", "", WED.toString()), WED);
        assertEquals(1, r.currentStreak);
        assertEquals(1, r.bestStreak);
    }

    @Test
    public void futureDaysIgnored() {
        Result r = StreakCalculator.compute(days(
                WED, WED.plusDays(1), WED.plusDays(2), WED.plusDays(3)), WED);
        assertEquals(1, r.currentStreak);
        assertEquals(1, r.bestStreak);
        assertEquals(DayState.FUTURE, r.week.get(3).state);
    }

    @Test
    public void weekStart_isMonday() {
        assertEquals(LocalDate.of(2026, 10, 5), StreakCalculator.weekStart(LocalDate.of(2026, 10, 5)));
        assertEquals(LocalDate.of(2026, 10, 5), StreakCalculator.weekStart(LocalDate.of(2026, 10, 11)));
        assertEquals(LocalDate.of(2026, 10, 12), StreakCalculator.weekStart(LocalDate.of(2026, 10, 12)));
    }
}
