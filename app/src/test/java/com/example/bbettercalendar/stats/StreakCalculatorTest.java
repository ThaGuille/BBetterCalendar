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

/** Racha semanal (plan racha-semanal-y-constancia). */
public class StreakCalculatorTest {

    // Miércoles 2026-10-07; su semana empieza el lunes 2026-10-05.
    private static final LocalDate WED = LocalDate.of(2026, 10, 7);
    private static final LocalDate MON = WED.minusDays(2);

    private static List<String> days(LocalDate... ds) {
        List<String> out = new ArrayList<>();
        for (LocalDate d : ds) {
            out.add(d.toString());
        }
        return out;
    }

    /** {@code n} días (lunes, martes...) de la semana que empieza {@code weeksAgo} semanas antes de la de WED. */
    private static List<String> week(int weeksAgo, int n) {
        List<String> out = new ArrayList<>();
        LocalDate monday = MON.minusWeeks(weeksAgo);
        for (int i = 0; i < n; i++) {
            out.add(monday.plusDays(i).toString());
        }
        return out;
    }

    @SafeVarargs
    private static List<String> concat(List<String>... parts) {
        List<String> out = new ArrayList<>();
        for (List<String> p : parts) {
            out.addAll(p);
        }
        return out;
    }

    @Test
    public void empty_isZeroEverywhere() {
        Result r = StreakCalculator.compute(Collections.<String>emptyList(), 3, WED);
        assertEquals(3, r.goal);
        assertEquals(0, r.daysThisWeek);
        assertFalse(r.goalMetThisWeek);
        assertEquals(0, r.weekStreak);
        assertEquals(0, r.bestWeekStreak);
        assertFalse(r.activeToday);
        assertEquals(7, r.week.size());
    }

    @Test
    public void currentWeekMet_countsTowardsStreak() {
        Result r = StreakCalculator.compute(week(0, 2), 2, WED); // lunes y martes
        assertEquals(2, r.daysThisWeek);
        assertTrue(r.goalMetThisWeek);
        assertEquals(1, r.weekStreak);
        assertEquals(1, r.bestWeekStreak);
    }

    @Test
    public void currentWeekNotMetYet_streakAliveThroughLastWeek() {
        Result r = StreakCalculator.compute(concat(week(2, 3), week(1, 3), week(0, 1)), 3, WED);
        assertEquals(1, r.daysThisWeek);
        assertFalse(r.goalMetThisWeek);
        assertEquals(2, r.weekStreak);
    }

    @Test
    public void currentWeekMet_extendsPreviousRun() {
        Result r = StreakCalculator.compute(concat(week(2, 3), week(1, 3), week(0, 3)), 3, WED);
        assertEquals(3, r.weekStreak);
        assertEquals(3, r.bestWeekStreak);
        assertTrue(r.activeToday);
    }

    @Test
    public void lastWeekUnderGoal_streakBroken_bestKept() {
        Result r = StreakCalculator.compute(concat(week(3, 3), week(2, 4), week(1, 1)), 3, WED);
        assertEquals(0, r.weekStreak);
        assertEquals(2, r.bestWeekStreak);
    }

    @Test
    public void weekendsOff_doNotBreakTheStreak() {
        // Lunes-viernes cada semana, nunca fin de semana; objetivo 5.
        Result r = StreakCalculator.compute(concat(week(3, 5), week(2, 5), week(1, 5)), 5, WED);
        assertEquals(3, r.weekStreak);
    }

    @Test
    public void bestRun_survivesALaterGap() {
        Result r = StreakCalculator.compute(
                concat(week(5, 2), week(4, 2), week(3, 2), week(2, 1), week(1, 2)), 2, WED);
        assertEquals(1, r.weekStreak);
        assertEquals(3, r.bestWeekStreak);
    }

    @Test
    public void goalAppliesRetroactively() {
        List<String> history = concat(week(2, 3), week(1, 3));
        assertEquals(2, StreakCalculator.compute(history, 3, WED).weekStreak);
        assertEquals(0, StreakCalculator.compute(history, 4, WED).weekStreak);
    }

    @Test
    public void goalIsClamped() {
        assertEquals(1, StreakCalculator.compute(Collections.<String>emptyList(), 0, WED).goal);
        assertEquals(7, StreakCalculator.compute(Collections.<String>emptyList(), 9, WED).goal);
    }

    @Test
    public void weekRow_pastDayWithoutSessionIsRest() {
        Result r = StreakCalculator.compute(days(MON), 3, WED);
        assertEquals(DayState.DONE, r.week.get(0).state);
        assertEquals(DayState.REST, r.week.get(1).state);
        assertEquals(DayState.PENDING, r.week.get(2).state);
        assertTrue(r.week.get(2).isToday);
        for (int i = 3; i < 7; i++) {
            assertEquals(DayState.FUTURE, r.week.get(i).state);
        }
    }

    @Test
    public void weekRow_mondayFirstSundayLast() {
        Result r = StreakCalculator.compute(Collections.<String>emptyList(), 3, WED);
        assertEquals(DayOfWeek.MONDAY, r.week.get(0).date.getDayOfWeek());
        assertEquals(DayOfWeek.SUNDAY, r.week.get(6).date.getDayOfWeek());
        LocalDate sunday = MON.plusDays(6);
        assertEquals(MON, StreakCalculator.weekStart(sunday));
        assertEquals(MON, StreakCalculator.weekStart(MON));
    }

    @Test
    public void futureDaysIgnored() {
        Result r = StreakCalculator.compute(days(WED.plusDays(1), WED.plusDays(2), WED.plusDays(3)), 1, WED);
        assertEquals(0, r.daysThisWeek);
        assertEquals(0, r.weekStreak);
        assertEquals(DayState.FUTURE, r.week.get(3).state);
    }

    @Test
    public void malformedAndNullDaysIgnored() {
        Result r = StreakCalculator.compute(Arrays.asList(null, "garbage", "", WED.toString()), 1, WED);
        assertEquals(1, r.daysThisWeek);
        assertEquals(1, r.weekStreak);
    }

    @Test
    public void duplicatesCountOnce() {
        Result r = StreakCalculator.compute(days(WED, WED, MON, MON), 3, WED);
        assertEquals(2, r.daysThisWeek);
    }
}
