package com.example.bbettercalendar.ui.projects.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.TaskSubtask;
import com.example.bbettercalendar.projects.domain.DateChoices;
import com.example.bbettercalendar.projects.domain.TaskDraft;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

public class TaskEditorTilesTest {
    private static final ZoneId ZONE = ZoneId.systemDefault();
    // Tuesday 24 November 2026, noon.
    private static final long NOW = LocalDate.of(2026, 11, 24).atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli();

    private static long dayStart(int day) {
        return LocalDate.of(2026, 11, day).atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    private static TaskDraft draft() {
        return new TaskDraft(DateChoices.startOfToday(NOW, ZONE));
    }

    @Test
    public void whenTileSaysTodayTomorrowOrNoDate() {
        assertEquals(TaskEditorTiles.DayKind.NONE, TaskEditorTiles.dayKind(null, NOW, ZONE));
        assertEquals(TaskEditorTiles.DayKind.TODAY, TaskEditorTiles.dayKind(dayStart(24), NOW, ZONE));
        assertEquals(TaskEditorTiles.DayKind.TOMORROW, TaskEditorTiles.dayKind(dayStart(25), NOW, ZONE));
    }

    @Test
    public void whenTileWritesLaterDatesAsWeekdayAndDay() {
        assertEquals(TaskEditorTiles.DayKind.DATE, TaskEditorTiles.dayKind(dayStart(27), NOW, ZONE));
        assertEquals("Fri 20", TaskEditorTiles.dayDate(dayStart(20), ZONE, Locale.ENGLISH));
        assertEquals("Vie 20", TaskEditorTiles.dayDate(dayStart(20), ZONE, new Locale("es", "ES")));
    }

    @Test
    public void focusTileSaysFreeOrTheDuration() {
        TaskDraft d = draft();
        assertNull(TaskEditorTiles.durationText(d));
        d.setDurationMinutes(50);
        assertEquals("50 min", TaskEditorTiles.durationText(d));
        d.setDurationMinutes(90);
        assertEquals("1 h 30", TaskEditorTiles.durationText(d));
    }

    @Test
    public void pomodoroAndLockShowOnlyWhenOnAndTheTaskHasADuration() {
        TaskDraft d = draft();
        assertFalse(TaskEditorTiles.showsPomodoro(d));
        assertFalse(TaskEditorTiles.showsLock(d));
        d.setDurationMinutes(120);
        d.setPomodoro(true);
        d.setBlockOnFocus(true);
        assertTrue(TaskEditorTiles.showsPomodoro(d));
        assertTrue(TaskEditorTiles.showsLock(d));
        d.setDurationMinutes(0); // Free turns both off
        assertFalse(TaskEditorTiles.showsPomodoro(d));
        assertFalse(TaskEditorTiles.showsLock(d));
    }

    @Test
    public void subtasksTileCountsDoneOverTotalAndFoldsTheRestIntoMore() {
        TaskDraft d = draft();
        assertNull(TaskEditorTiles.subtasksProgress(d));
        assertEquals(0, TaskEditorTiles.moreSubtasks(d));
        d.subtasks.add(new TaskDraft.Subtask("a", true));
        d.subtasks.add(new TaskDraft.Subtask("b", false));
        assertEquals("1/2", TaskEditorTiles.subtasksProgress(d));
        assertEquals(2, TaskEditorTiles.visibleSubtasks(d).size());
        assertEquals(0, TaskEditorTiles.moreSubtasks(d));
        d.subtasks.add(new TaskDraft.Subtask("c", false));
        d.subtasks.add(new TaskDraft.Subtask("d", true));
        d.subtasks.add(new TaskDraft.Subtask("e", false));
        assertEquals("2/5", TaskEditorTiles.subtasksProgress(d));
        assertEquals(3, TaskEditorTiles.visibleSubtasks(d).size());
        assertEquals("c", TaskEditorTiles.visibleSubtasks(d).get(2).title);
        assertEquals(2, TaskEditorTiles.moreSubtasks(d));
    }

    @Test
    public void signatureChangesWithAnyEditAndNotWithTheSameValues() {
        TaskDraft a = draft();
        TaskDraft b = draft();
        assertEquals(TaskEditorTiles.signature(a), TaskEditorTiles.signature(b));
        b.title = "Escribir";
        assertNotEquals(TaskEditorTiles.signature(a), TaskEditorTiles.signature(b));
        a.title = "Escribir ";
        assertEquals(TaskEditorTiles.signature(a), TaskEditorTiles.signature(b)); // trailing space is not a change
        a.subtasks.add(new TaskDraft.Subtask("x", false));
        assertNotEquals(TaskEditorTiles.signature(a), TaskEditorTiles.signature(b));
        b.subtasks.add(new TaskDraft.Subtask("x", true));
        assertNotEquals(TaskEditorTiles.signature(a), TaskEditorTiles.signature(b));
    }

    @Test
    public void editorDraftCarriesEveryFieldOfAStoredTask() {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(dayStart(24));
        start.set(Calendar.HOUR_OF_DAY, 17);
        start.set(Calendar.MINUTE, 0);
        CalendarEntry entry = new CalendarEntry.EventBuilder()
                .setEventId(7)
                .setEventType(AddEventActivity.TYPE_TASK)
                .setEventTitle("Escribir el capitulo 3")
                .setEventProjectId(3)
                .setSectionId(11)
                .setPriority(3)
                .setTagId(2)
                .setBlockOnFocus(true)
                .setEventDuration(120 * 60_000)
                .setEventTargetMinutes(120)
                .setEventStartDayAndHour(start)
                .build();
        TaskDraft d = TaskEditorDrafts.fromEntry(entry,
                Arrays.asList(new TaskSubtask(7, "uno", true, 0), new TaskSubtask(7, "dos", false, 1)),
                NOW, ZONE, 10);
        assertEquals(7, d.editId);
        assertEquals(3, d.projectId);
        assertEquals(11, d.sectionId);
        assertEquals(Integer.valueOf(17 * 60), d.getTimeOfDayMinutes());
        assertEquals(Long.valueOf(dayStart(24)), d.getDayStartMillis());
        assertEquals(120, d.getDurationMinutes());
        assertTrue(d.isPomodoro());
        assertTrue(d.isBlockOnFocus());
        assertEquals(3, d.priority);
        assertEquals(2, d.tagId);
        assertEquals("1/2", TaskEditorTiles.subtasksProgress(d));
    }

    @Test
    public void aStoredTaskWithoutSectionOrDateUsesTheFirstSectionAndNoDate() {
        CalendarEntry entry = new CalendarEntry.EventBuilder()
                .setEventId(8)
                .setEventType(AddEventActivity.TYPE_TASK)
                .setEventTitle("Sin fecha")
                .setEventProjectId(3)
                .build();
        TaskDraft d = TaskEditorDrafts.fromEntry(entry, null, NOW, ZONE, 10);
        assertEquals(10, d.sectionId);
        assertNull(d.getDayStartMillis());
        assertNull(d.getTimeOfDayMinutes());
        assertEquals(0, d.getDurationMinutes());
    }
}
