package com.example.bbettercalendar.notifications.nudge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.stats.DayMinutes;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class NudgeDataTest {

    private static final long HOUR = 60L * 60_000L;
    private static final long BASE = 1_800_000_000_000L; // "ahora" en los tests de agenda

    private static CalendarEntry entry(int type, long start, long end, boolean done) {
        Calendar s = Calendar.getInstance();
        s.setTimeInMillis(start);
        Calendar e = Calendar.getInstance();
        e.setTimeInMillis(end);
        return new CalendarEntry.EventBuilder()
                .setEventTitle("t" + start)
                .setEventType(type)
                .setEventStartDayAndHour(s)
                .setEventEndDayAndHour(e)
                .setEventIsDone(done)
                .build();
    }

    private static CalendarEntry task(long start, boolean done) {
        return entry(AddEventActivity.TYPE_TASK, start, start, done);
    }

    private static CalendarEntry event(long start, long end) {
        return entry(AddEventActivity.TYPE_EVENT, start, end, false);
    }

    // ---------------- Agenda ----------------

    @Test
    public void agenda_empty() {
        assertTrue(NudgeData.agenda(null, BASE).isEmpty());
        assertTrue(NudgeData.agenda(Collections.<CalendarEntry>emptyList(), BASE).isEmpty());
    }

    @Test
    public void agenda_countsOpenTasksAndEvents_firstUpcoming() {
        CalendarEntry later = task(BASE + 3 * HOUR, false);
        CalendarEntry first = event(BASE + HOUR, BASE + 2 * HOUR);
        List<CalendarEntry> list = Arrays.asList(
                later,
                task(BASE + 2 * HOUR, true),          // hecha: no cuenta
                first,
                task(BASE - HOUR, false));            // atrasada hoy: cuenta, pero no es "lo primero"
        NudgeData.Agenda a = NudgeData.agenda(list, BASE);
        assertEquals(2, a.openTasks);
        assertEquals(1, a.events);
        assertSame(first, a.firstUpcoming);
        assertEquals(2, a.moreAfterFirst());
    }

    @Test
    public void agenda_endedEventsDoNotCount() {
        NudgeData.Agenda a = NudgeData.agenda(
                Collections.singletonList(event(BASE - 3 * HOUR, BASE - HOUR)), BASE);
        assertTrue(a.isEmpty());
    }

    @Test
    public void agenda_ongoingEventCountsButIsNotUpcoming() {
        NudgeData.Agenda a = NudgeData.agenda(
                Collections.singletonList(event(BASE - HOUR, BASE + HOUR)), BASE);
        assertEquals(1, a.events);
        assertNull(a.firstUpcoming);
        assertEquals(0, a.moreAfterFirst());
    }

    @Test
    public void agenda_ignoresReminderType() {
        NudgeData.Agenda a = NudgeData.agenda(Collections.singletonList(
                entry(AddEventActivity.TYPE_NOTIFICATION, BASE + HOUR, BASE + HOUR, false)), BASE);
        assertTrue(a.isEmpty());
    }

    @Test
    public void tomorrowIsOpen_upToOneEntry() {
        long tomorrow = BASE + 24 * HOUR;
        assertTrue(NudgeData.tomorrowIsOpen(null, tomorrow));
        assertTrue(NudgeData.tomorrowIsOpen(Collections.singletonList(task(tomorrow + HOUR, false)), tomorrow));
        assertFalse(NudgeData.tomorrowIsOpen(
                Arrays.asList(task(tomorrow + HOUR, false), event(tomorrow + 2 * HOUR, tomorrow + 3 * HOUR)),
                tomorrow));
        // Una tarea hecha de antemano no llena el día.
        assertTrue(NudgeData.tomorrowIsOpen(
                Arrays.asList(task(tomorrow + HOUR, true), task(tomorrow + 2 * HOUR, false)), tomorrow));
    }

    // ---------------- Racha ----------------

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Test
    public void streak_countsConsecutiveDaysEndingYesterday() {
        List<String> days = Arrays.asList("2026-11-14", "2026-11-15", "2026-11-16", "2026-11-17");
        assertEquals(4, NudgeData.streakEndingYesterday(days, TODAY));
        assertEquals(4, NudgeData.streakAtStake(days, TODAY));
    }

    @Test
    public void streak_gapBreaksIt() {
        List<String> days = Arrays.asList("2026-11-13", "2026-11-14", "2026-11-16", "2026-11-17");
        assertEquals(2, NudgeData.streakEndingYesterday(days, TODAY));
    }

    @Test
    public void streak_noYesterday_isZero() {
        List<String> days = Arrays.asList("2026-11-15", "2026-11-16");
        assertEquals(0, NudgeData.streakEndingYesterday(days, TODAY));
        assertEquals(0, NudgeData.streakAtStake(days, TODAY));
    }

    @Test
    public void streak_oneDay_isNotAtStake() {
        assertEquals(0, NudgeData.streakAtStake(Collections.singletonList("2026-11-17"), TODAY));
    }

    @Test
    public void streak_alreadyFocusedToday_isNotAtStake() {
        List<String> days = Arrays.asList("2026-11-16", "2026-11-17", "2026-11-18");
        assertEquals(2, NudgeData.streakEndingYesterday(days, TODAY));
        assertEquals(0, NudgeData.streakAtStake(days, TODAY));
    }

    @Test
    public void streak_ignoresGarbage() {
        List<String> days = new ArrayList<>(Arrays.asList("2026-11-16", "nope", null, "2026-11-17"));
        assertEquals(2, NudgeData.streakAtStake(days, TODAY));
    }

    @Test
    public void streak_crossesMonthBoundary() {
        List<String> days = Arrays.asList("2026-10-30", "2026-10-31");
        assertEquals(2, NudgeData.streakEndingYesterday(days, LocalDate.of(2026, 11, 1)));
    }

    // ---------------- Semana ----------------

    private static DayMinutes dm(String day, int minutes) {
        DayMinutes d = new DayMinutes();
        d.day = day;
        d.minutes = minutes;
        return d;
    }

    @Test
    public void week_sumsMinutesAndActiveDays() {
        NudgeData.Week w = NudgeData.week(
                Arrays.asList(dm("2026-11-16", 50), dm("2026-11-17", 25), dm("2026-11-18", 0)), 60, 4);
        assertEquals(75, w.focusMinutes);
        assertEquals(2, w.activeDays);
        assertEquals(4, w.doneTasks);
        assertEquals(Integer.valueOf(25), w.deltaPercent);
        assertTrue(w.hasActivity());
    }

    @Test
    public void week_noLastWeek_noDelta() {
        NudgeData.Week w = NudgeData.week(Collections.singletonList(dm("2026-11-16", 25)), 0, 0);
        assertNull(w.deltaPercent);
        assertTrue(w.hasActivity());
    }

    @Test
    public void week_onlyTasks_isActivity() {
        NudgeData.Week w = NudgeData.week(null, 100, 3);
        assertEquals(0, w.focusMinutes);
        assertEquals(Integer.valueOf(-100), w.deltaPercent);
        assertTrue(w.hasActivity());
    }

    @Test
    public void week_nothing_isNotActivity() {
        assertFalse(NudgeData.week(Collections.<DayMinutes>emptyList(), 120, 0).hasActivity());
    }

    @Test
    public void deltaPercent_rounds() {
        assertEquals(Integer.valueOf(33), NudgeData.deltaPercent(40, 30));
        assertEquals(Integer.valueOf(-33), NudgeData.deltaPercent(20, 30));
        assertEquals(Integer.valueOf(0), NudgeData.deltaPercent(30, 30));
        assertNull(NudgeData.deltaPercent(30, 0));
    }
}
