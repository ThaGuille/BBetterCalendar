package com.example.bbettercalendar.social.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.stats.DayMinutes;
import com.example.bbettercalendar.stats.StreakCalculator;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * F3-u4: {@link StatsComputer} saca los valores de {@code stats/{uid}} (03 §5) de las filas por día de
 * {@code FocusEventDAO}, el total, la racha de {@link StreakCalculator} y el objetivo. 2026-10-28 es miércoles
 * de la semana ISO 2026-W44 (lunes 26 de octubre, domingo 1 de noviembre).
 */
public class StatsComputerTest {

    private static final LocalDate WED = LocalDate.of(2026, 10, 28);

    private static DayMinutes day(String iso, int minutes) {
        DayMinutes d = new DayMinutes();
        d.day = iso;
        d.minutes = minutes;
        return d;
    }

    private static List<String> daysOf(List<DayMinutes> rows) {
        List<String> out = new ArrayList<>();
        for (DayMinutes r : rows) {
            out.add(r.day);
        }
        return out;
    }

    /** Como lo hace StatsPublisher: la racha con los días de las mismas filas. */
    private static FriendStats compute(List<DayMinutes> rows, int total, int goal, LocalDate today) {
        StreakCalculator.Result streak = StreakCalculator.compute(daysOf(rows), goal, today);
        return StatsComputer.compute(rows, total, streak, goal, today);
    }

    @Test
    public void aDayOver240CountsUncappedTodayButCappedInTheWeek() {
        List<DayMinutes> rows = Arrays.asList(day("2026-10-26", 300), day("2026-10-27", 100),
                day("2026-10-28", 250));
        FriendStats s = compute(rows, 650, 3, WED);
        assertEquals(250, s.minutesToday);
        assertEquals(240 + 100 + 240, s.minutesWeekCapped);
        assertEquals(3, s.daysThisWeek);
        assertEquals("2026-10-28", s.dayKey);
        assertEquals("2026-W44", s.weekKey);
        assertEquals(650, s.totalMinutes);
        assertNull(s.updatedAtMillis);
    }

    @Test
    public void aWeekAcrossAMonthChangeSumsMondayToTodayOnly() {
        LocalDate sunday = LocalDate.of(2026, 11, 1);
        List<DayMinutes> rows = Arrays.asList(day("2026-10-25", 100), // domingo de la semana anterior
                day("2026-10-30", 60), day("2026-10-31", 30), day("2026-11-01", 45));
        FriendStats s = compute(rows, 235, 3, sunday);
        assertEquals(45, s.minutesToday);
        assertEquals(60 + 30 + 45, s.minutesWeekCapped);
        assertEquals(3, s.daysThisWeek);
        assertEquals("2026-11-01", s.dayKey);
        assertEquals("2026-W44", s.weekKey);
    }

    @Test
    public void anEmptyWeekIsAllZeroButKeepsTheTotalAndGoal() {
        FriendStats s = StatsComputer.compute(Collections.emptyList(), 500,
                StreakCalculator.compute(Collections.singletonList("2026-10-01"), 4, WED), 4, WED);
        assertEquals(0, s.minutesToday);
        assertEquals(0, s.minutesWeekCapped);
        assertEquals(0, s.daysThisWeek);
        assertEquals(0, s.weekStreak);
        assertEquals(4, s.weeklyGoal);
        assertEquals(500, s.totalMinutes);
    }

    @Test
    public void theSundayMondayBoundaryStartsANewWeek() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        List<DayMinutes> rows = Arrays.asList(day("2026-11-01", 200), day("2026-11-02", 30));
        FriendStats s = compute(rows, 230, 3, monday);
        assertEquals(30, s.minutesToday);
        assertEquals(30, s.minutesWeekCapped); // el domingo es de la semana anterior
        assertEquals(1, s.daysThisWeek);
        assertEquals("2026-11-02", s.dayKey);
        assertEquals("2026-W45", s.weekKey);
    }

    @Test
    public void bestWeekStreakIsNeverBelowWeekStreakAndComesFromAllHistory() {
        // Objetivo 2: W40, W41, W42 cumplidas (racha antigua de 3), W43 no, W44 cumplida (racha actual 1).
        List<DayMinutes> rows = Arrays.asList(
                day("2026-09-28", 25), day("2026-09-29", 25),
                day("2026-10-05", 25), day("2026-10-06", 25),
                day("2026-10-12", 25), day("2026-10-13", 25),
                day("2026-10-19", 25),
                day("2026-10-26", 25), day("2026-10-27", 25));
        FriendStats s = compute(rows, 225, 2, WED);
        assertEquals(1, s.weekStreak);
        assertEquals(3, s.bestWeekStreak);
        assertTrue(s.bestWeekStreak >= s.weekStreak);
        assertEquals(2, s.daysThisWeek);
        assertEquals(50, s.minutesWeekCapped);
    }

    @Test
    public void futureAndMalformedRowsAreIgnored() {
        List<DayMinutes> rows = new ArrayList<>(Arrays.asList(day("2026-10-28", 20), day("2026-10-29", 90),
                day("no-date", 50), day(null, 10)));
        rows.add(null);
        FriendStats s = StatsComputer.compute(rows, 20,
                StreakCalculator.compute(Collections.singletonList("2026-10-28"), 3, WED), 3, WED);
        assertEquals(20, s.minutesToday);
        assertEquals(20, s.minutesWeekCapped);
    }

    @Test
    public void twoRowsOfTheSameDayAddUpBeforeTheCap() {
        List<DayMinutes> rows = Arrays.asList(day("2026-10-28", 200), day("2026-10-28", 100));
        FriendStats s = compute(rows, 300, 3, WED);
        assertEquals(300, s.minutesToday);
        assertEquals(240, s.minutesWeekCapped);
    }
}
