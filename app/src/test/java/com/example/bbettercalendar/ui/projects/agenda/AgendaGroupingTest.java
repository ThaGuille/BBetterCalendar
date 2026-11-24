package com.example.bbettercalendar.ui.projects.agenda;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AgendaGroupingTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    // Thursday 19 Nov 2026, 10:00.
    private static final long NOW = at(2026, 11, 19, 10, 0);

    private static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static AgendaTask task(String title, long due, boolean hasTime, int minutes, boolean lock) {
        return new AgendaTask(title, due, hasTime, minutes, lock);
    }

    private static List<AgendaRow> build(List<AgendaTask> tasks, long deadline) {
        return AgendaGrouping.build(tasks, deadline, NOW, ZONE, Locale.ENGLISH);
    }

    @Test
    public void groupsByDayInDateOrderWithRelativeHeaders() {
        List<AgendaRow> rows = build(Arrays.asList(
                task("later", at(2026, 11, 21, 9, 0), true, 0, false),
                task("today b", at(2026, 11, 19, 17, 0), true, 120, true),
                task("yesterday", at(2026, 11, 18, 9, 30), true, 0, false),
                task("tomorrow", at(2026, 11, 20, 0, 0), false, 0, false),
                task("today a", at(2026, 11, 19, 8, 0), true, 25, false)), 0L);

        assertEquals(AgendaRow.Day.YESTERDAY, rows.get(0).day);
        assertEquals("yesterday", rows.get(1).title);
        assertEquals(AgendaRow.Day.TODAY, rows.get(2).day);
        assertEquals("today a", rows.get(3).title);
        assertEquals("today b", rows.get(4).title);
        assertEquals(AgendaRow.Day.TOMORROW, rows.get(5).day);
        assertEquals("tomorrow", rows.get(6).title);
        assertEquals(AgendaRow.Day.DATE, rows.get(7).day);
        assertEquals("Sat 21", rows.get(7).label);
        assertEquals("later", rows.get(8).title);
        assertEquals(9, rows.size());
    }

    @Test
    public void headerTonesAreEmberForOverdueAndSageForToday() {
        List<AgendaRow> rows = build(Arrays.asList(
                task("y", at(2026, 11, 18, 9, 0), true, 0, false),
                task("t", at(2026, 11, 19, 9, 0), true, 0, false),
                task("m", at(2026, 11, 20, 9, 0), true, 0, false)), 0L);
        assertEquals(AgendaRow.Tone.OVERDUE, rows.get(0).tone);
        assertEquals(AgendaRow.Tone.TODAY, rows.get(2).tone);
        assertEquals(AgendaRow.Tone.NORMAL, rows.get(4).tone);
    }

    @Test
    public void severalOverdueDaysUseTheirDateLabelInEmber() {
        List<AgendaRow> rows = build(Arrays.asList(
                task("a", at(2026, 11, 16, 9, 0), true, 0, false),
                task("b", at(2026, 11, 17, 9, 0), true, 0, false),
                task("c", at(2026, 11, 18, 9, 0), true, 0, false)), 0L);
        assertEquals(AgendaRow.Day.DATE, rows.get(0).day);
        assertEquals("Mon 16", rows.get(0).label);
        assertEquals(AgendaRow.Tone.OVERDUE, rows.get(0).tone);
        assertEquals("Tue 17", rows.get(2).label);
        assertEquals(AgendaRow.Tone.OVERDUE, rows.get(2).tone);
        assertEquals(AgendaRow.Day.YESTERDAY, rows.get(4).day);
        assertEquals(AgendaRow.Tone.OVERDUE, rows.get(4).tone);
    }

    @Test
    public void taskRowCarriesTimeDurationAndLock() {
        List<AgendaRow> rows = build(Collections.singletonList(
                task("write", at(2026, 11, 19, 17, 5), true, 90, true)), 0L);
        AgendaRow row = rows.get(1);
        assertEquals(AgendaRow.Kind.TASK, row.kind);
        assertEquals("17:05", row.time);
        assertEquals("1 h 30", row.duration);
        assertTrue(row.blocks);
    }

    @Test
    public void taskWithoutTimeOrDurationHasEmptyTexts() {
        List<AgendaRow> rows = build(Collections.singletonList(
                task("plain", at(2026, 11, 19, 0, 0), false, 0, false)), 0L);
        assertEquals("", rows.get(1).time);
        assertEquals("", rows.get(1).duration);
        assertFalse(rows.get(1).blocks);
    }

    @Test
    public void deadlineRowComesAfterTasksAndBeforeUndated() {
        List<AgendaRow> rows = build(Arrays.asList(
                task("dated", at(2026, 11, 19, 9, 0), true, 0, false),
                task("none", 0L, false, 0, false)), at(2026, 11, 22, 18, 0));
        assertEquals(AgendaRow.Kind.HEADER, rows.get(0).kind);
        assertEquals(AgendaRow.Kind.TASK, rows.get(1).kind);
        assertEquals(AgendaRow.Kind.DEADLINE, rows.get(2).kind);
        assertEquals("Sun 22 Nov", rows.get(2).label);
        assertEquals(AgendaRow.Kind.UNDATED, rows.get(3).kind);
        assertEquals(4, rows.size());
    }

    @Test
    public void undatedCountIsOneRowAtTheEnd() {
        List<AgendaTask> tasks = new ArrayList<>();
        tasks.add(task("a", 0L, false, 0, false));
        tasks.add(task("b", 0L, false, 0, false));
        tasks.add(task("c", 0L, false, 0, false));
        List<AgendaRow> rows = build(tasks, 0L);
        assertEquals(1, rows.size());
        assertEquals(AgendaRow.Kind.UNDATED, rows.get(0).kind);
        assertEquals(3, rows.get(0).count);
    }

    @Test
    public void nothingAtAllIsEmpty() {
        assertTrue(build(Collections.<AgendaTask>emptyList(), 0L).isEmpty());
    }

    @Test
    public void spanishWeekdayLabelStartsWithACapital() {
        List<AgendaRow> rows = AgendaGrouping.build(Collections.singletonList(
                task("x", at(2026, 11, 23, 9, 0), true, 0, false)), 0L, NOW, ZONE, new Locale("es", "ES"));
        assertEquals("Lun 23", rows.get(0).label);
    }
}
