package com.example.bbettercalendar.projects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.calendarEntries.RecurrenceMaterializer;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.projects.domain.TaskSnapshot;
import com.example.bbettercalendar.stats.AttributedMinutes;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.ProjectMinutes;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectTasksRepositoryTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private CalendarEntryDAO entryDao;
    private ProjectSectionDAO sectionDao;
    private TaskSubtaskDAO subtaskDao;
    private EventReminderScheduler reminders;
    private TaskStartAlarm startAlarm;
    private ProjectTasksRepository repo;

    @Before
    public void setUp() {
        entryDao = mock(CalendarEntryDAO.class);
        sectionDao = mock(ProjectSectionDAO.class);
        subtaskDao = mock(TaskSubtaskDAO.class);
        reminders = mock(EventReminderScheduler.class);
        startAlarm = mock(TaskStartAlarm.class);
        TaskAlarms alarms = new TaskAlarms(reminders, startAlarm, entryDao);
        repo = new ProjectTasksRepository(entryDao, sectionDao, subtaskDao, mock(FocusEventDAO.class),
                alarms, new RecurrenceMaterializer(entryDao, reminders, startAlarm), Runnable::run,
                Runnable::run, new DirectExecutor());
    }

    private static ProjectSection section(int id, int projectId, int position) {
        ProjectSection s = new ProjectSection(projectId, "s" + id, position);
        s.id = id;
        return s;
    }

    private static long dayStart(int y, int m, int d) {
        return LocalDateTime.of(y, m, d, 0, 0).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static TaskDraft draft() {
        TaskDraft d = new TaskDraft(dayStart(2026, 11, 24));
        d.projectId = 3;
        d.sectionId = 12;
        d.title = "  Escribir el capitulo  ";
        return d;
    }

    // ---- secciones ----

    @Test
    public void deletingASectionMovesItsTasksToTheFirstRemainingSection() {
        when(sectionDao.getById(20)).thenReturn(section(20, 3, 0));
        when(sectionDao.getByProject(3)).thenReturn(
                Arrays.asList(section(20, 3, 0), section(21, 3, 1), section(22, 3, 2)));
        AtomicBoolean result = new AtomicBoolean();

        repo.deleteSection(20, result::set);

        assertTrue(result.get());
        verify(entryDao).moveSectionTasks(20, 21);
        verify(sectionDao).delete(any(ProjectSection.class));
    }

    @Test
    public void deletingTheLastSectionIsRefusedAndChangesNothing() {
        when(sectionDao.getById(30)).thenReturn(section(30, 4, 0));
        when(sectionDao.getByProject(4)).thenReturn(Collections.singletonList(section(30, 4, 0)));
        AtomicBoolean result = new AtomicBoolean(true);

        repo.deleteSection(30, result::set);

        assertFalse(result.get());
        verify(sectionDao, never()).delete(any(ProjectSection.class));
        verify(entryDao, never()).moveSectionTasks(anyInt(), anyInt());
    }

    @Test
    public void addSectionGoesAtTheEndOfTheProject() {
        when(sectionDao.nextPosition(3)).thenReturn(2);

        repo.addSection(3, "Revision");

        ArgumentCaptor<ProjectSection> captor = ArgumentCaptor.forClass(ProjectSection.class);
        verify(sectionDao).insert(captor.capture());
        assertEquals(3, captor.getValue().projectId);
        assertEquals("Revision", captor.getValue().name);
        assertEquals(2, captor.getValue().position);
    }

    // ---- subtareas y completado ----

    @Test
    public void checkingTheLastOpenSubtaskCompletesTheTask() {
        TaskSubtask first = new TaskSubtask(50, "a", true, 0);
        first.id = 1;
        TaskSubtask last = new TaskSubtask(50, "b", false, 1);
        last.id = 2;
        when(subtaskDao.getById(2)).thenReturn(last);
        when(subtaskDao.getByEntry(50)).thenReturn(Arrays.asList(first, last));
        CalendarEntry task = new CalendarEntry.EventBuilder().setEventId(50).setEventType(2).build();
        when(entryDao.getEventById(50)).thenReturn(task);

        repo.toggleSubtask(2);

        assertTrue(last.done);
        assertTrue(task.isDone());
        assertTrue(task.getCompletedAtMillis() > 0);
        verify(entryDao).update(task);
        verify(reminders).cancelFor(task);
    }

    @Test
    public void checkingASubtaskWhileOthersAreOpenKeepsTheTaskPending() {
        TaskSubtask a = new TaskSubtask(51, "a", false, 0);
        a.id = 3;
        TaskSubtask b = new TaskSubtask(51, "b", false, 1);
        b.id = 4;
        when(subtaskDao.getById(3)).thenReturn(a);
        when(subtaskDao.getByEntry(51)).thenReturn(Arrays.asList(a, b));

        repo.toggleSubtask(3);

        assertTrue(a.done);
        verify(subtaskDao).update(a);
        verify(entryDao, never()).update(any(CalendarEntry.class));
    }

    @Test
    public void completingATaskMarksItsSubtasksAndStopsItsAlarms() {
        CalendarEntry task = new CalendarEntry.EventBuilder().setEventId(60).setEventType(2).build();
        when(entryDao.getEventById(60)).thenReturn(task);

        repo.setDone(60, true);

        assertTrue(task.isDone());
        assertTrue(task.getCompletedAtMillis() > 0);
        verify(entryDao).update(task);
        verify(subtaskDao).setAllDone(60, true);
        verify(reminders).cancelFor(task);
        verify(reminders, never()).scheduleFor(any(CalendarEntry.class));
    }

    @Test
    public void reopeningATaskClearsTheStampAndSchedulesItAgain() {
        CalendarEntry task = new CalendarEntry.EventBuilder().setEventId(61).setEventType(2)
                .setEventIsDone(true).build();
        task.setCompletedAtMillis(999L);
        when(entryDao.getEventById(61)).thenReturn(task);

        repo.setDone(61, false);

        assertFalse(task.isDone());
        assertEquals(0L, task.getCompletedAtMillis());
        verify(subtaskDao).setAllDone(61, false);
        verify(reminders).scheduleFor(task);
    }

    @Test
    public void deletingATaskRemovesItsSubtasksAndCancelsItsAlarms() {
        CalendarEntry task = new CalendarEntry.EventBuilder().setEventId(70).setEventType(2).build();
        when(entryDao.getEventById(70)).thenReturn(task);

        repo.deleteTask(70);

        verify(reminders).cancelFor(task);
        verify(startAlarm).cancel(task);
        verify(subtaskDao).deleteByEntry(70);
        verify(entryDao).delete(task);
    }

    // ---- saveDraft ----

    @Test
    public void saveDraftMapsEveryDraftFieldIntoTheBuiltEntry() {
        TaskDraft d = draft();
        d.note = "Mirar las notas";
        d.priority = CalendarEntry.PRIORITY_HIGH;
        d.tagId = 9;
        d.setDay(dayStart(2026, 11, 27));
        d.setTime(17 * 60 + 30);
        d.setDurationMinutes(50);
        d.setPomodoro(true);
        d.setBlockOnFocus(true);
        d.setReminder(TaskDraft.Reminder.MIN_15);
        when(entryDao.insert(any(CalendarEntry.class))).thenReturn(77L);
        AtomicInteger received = new AtomicInteger();

        repo.saveDraft(d, received::set);

        ArgumentCaptor<CalendarEntry> captor = ArgumentCaptor.forClass(CalendarEntry.class);
        verify(entryDao).insert(captor.capture());
        CalendarEntry e = captor.getValue();
        assertEquals(2, e.getType());
        assertEquals(3, e.getProjectId());
        assertEquals(12, e.getSectionId());
        assertEquals("Escribir el capitulo", e.getTitle());
        assertEquals("Mirar las notas", e.getDescription());
        assertEquals(CalendarEntry.PRIORITY_HIGH, e.getPriority());
        assertEquals(9, e.getTagId());
        assertTrue(e.isBlockOnFocus());
        assertEquals(50 * 60_000, e.getDuration());
        assertEquals("pomodoro on: the goal is the duration", 50, e.getTargetMinutes());
        boolean[] flags = e.getNotifications();
        assertTrue(flags[2]);
        assertFalse(flags[4]);
        assertFalse(e.isHiddenInCalendar());
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(e.getStartMillis());
        assertEquals(2026, start.get(Calendar.YEAR));
        assertEquals(Calendar.NOVEMBER, start.get(Calendar.MONTH));
        assertEquals(27, start.get(Calendar.DAY_OF_MONTH));
        assertEquals(17, start.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, start.get(Calendar.MINUTE));
        assertFalse(e.isDone());
        assertEquals(77, received.get());
        verify(reminders).scheduleFor(e);
    }

    @Test
    public void saveDraftWithoutDateIsHiddenInTheCalendarAndWithoutPomodoroHasNoGoal() {
        TaskDraft d = draft();
        d.setDurationMinutes(30);

        repo.saveDraft(d, null);

        ArgumentCaptor<CalendarEntry> captor = ArgumentCaptor.forClass(CalendarEntry.class);
        verify(entryDao).insert(captor.capture());
        CalendarEntry e = captor.getValue();
        assertEquals(0L, e.getStartMillis());
        assertTrue(e.isHiddenInCalendar());
        assertEquals("a duration without pomodoro sets no focus goal", 0, e.getTargetMinutes());
        assertEquals(30 * 60_000, e.getDuration());
    }

    @Test
    public void saveDraftWithADayButNoTimeStartsAtMidnight() {
        TaskDraft d = draft();
        d.setDay(dayStart(2026, 12, 1));

        repo.saveDraft(d, null);

        ArgumentCaptor<CalendarEntry> captor = ArgumentCaptor.forClass(CalendarEntry.class);
        verify(entryDao).insert(captor.capture());
        assertEquals(dayStart(2026, 12, 1), captor.getValue().getStartMillis());
        assertFalse(captor.getValue().isHiddenInCalendar());
    }

    @Test
    public void saveDraftWithoutASectionUsesTheFirstSectionOfTheProject() {
        TaskDraft d = draft();
        d.sectionId = 0;
        when(sectionDao.getFirstOfProject(3)).thenReturn(section(14, 3, 0));

        repo.saveDraft(d, null);

        ArgumentCaptor<CalendarEntry> captor = ArgumentCaptor.forClass(CalendarEntry.class);
        verify(entryDao).insert(captor.capture());
        assertEquals(14, captor.getValue().getSectionId());
    }

    @Test
    public void saveDraftReplacesTheSubtasksInOrder() {
        TaskDraft d = draft();
        d.subtasks.add(new TaskDraft.Subtask("uno", true));
        d.subtasks.add(new TaskDraft.Subtask("dos", false));
        when(entryDao.insert(any(CalendarEntry.class))).thenReturn(88L);

        repo.saveDraft(d, null);

        verify(subtaskDao).deleteByEntry(88);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TaskSubtask>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(subtaskDao).insertAll(captor.capture());
        List<TaskSubtask> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals("uno", saved.get(0).title);
        assertTrue(saved.get(0).done);
        assertEquals(0, saved.get(0).position);
        assertEquals(88, saved.get(0).entryId);
        assertEquals("dos", saved.get(1).title);
        assertEquals(1, saved.get(1).position);
    }

    @Test
    public void editingKeepsTheSeriesFieldsAndTheCompletionStampOfTheStoredRow() {
        CalendarEntry stored = new CalendarEntry.EventBuilder().setEventId(90).setEventType(2)
                .setEventIsDone(true).setEventRepetition(2).setEventRepetitionInterval(3)
                .setEventTemplateId(5).build();
        stored.setCompletedAtMillis(4242L);
        stored.setMaterializedUntilMillis(777L);
        when(entryDao.getEventById(90)).thenReturn(stored);
        TaskDraft d = draft();
        d.editId = 90;
        d.done = true;
        d.completionNote = "  hecho  ";

        repo.saveDraft(d, null);

        ArgumentCaptor<CalendarEntry> captor = ArgumentCaptor.forClass(CalendarEntry.class);
        verify(entryDao).update(captor.capture());
        CalendarEntry e = captor.getValue();
        assertEquals(90, e.getId());
        assertEquals(2, e.getRepetition());
        assertEquals(3, e.getRepetitionInterval());
        assertEquals(5, e.getTemplateId());
        assertEquals(4242L, e.getCompletedAtMillis());
        assertEquals(777L, e.getMaterializedUntilMillis());
        assertEquals("hecho", e.getCompletionNote());
        verify(entryDao, never()).insert(any(CalendarEntry.class));
        verify(subtaskDao, never()).insertAll(anyList());
    }

    @Test
    public void saveDraftWithAnEmptyTitleSavesNothing() {
        TaskDraft d = draft();
        d.title = "   ";

        repo.saveDraft(d, null);

        verify(entryDao, never()).insert(any(CalendarEntry.class));
        verify(entryDao, never()).update(any(CalendarEntry.class));
    }

    // ---- tablero ----

    @Test
    public void boardPutsTasksWithoutSectionInTheFirstOneAndCarriesMinutes() {
        CalendarEntry loose = new CalendarEntry.EventBuilder().setEventId(1).setEventTitle("a").build();
        CalendarEntry template = new CalendarEntry.EventBuilder().setEventId(2).setEventTitle("b")
                .setEventIsTemplate(true).setSectionId(22).build();
        AttributedMinutes am = new AttributedMinutes();
        am.entryId = 1;
        am.minutes = 35;
        ProjectMinutes pm = new ProjectMinutes();
        pm.projectId = 3;
        pm.minutes = 120;
        ProjectMinutes other = new ProjectMinutes();
        other.projectId = 4;
        other.minutes = 999;
        TaskSubtask st = new TaskSubtask(1, "x", false, 0);

        ProjectBoard board = ProjectBoard.assemble(3,
                Arrays.asList(section(21, 3, 0), section(22, 3, 1)),
                Arrays.asList(loose, template), Collections.singletonList(st),
                Collections.singletonList(am), Arrays.asList(pm, other));

        TaskSnapshot first = board.tasks.get(0);
        assertEquals(21, first.sectionId);
        assertEquals(35, first.attributedMinutes);
        TaskSnapshot second = board.tasks.get(1);
        assertEquals(22, second.sectionId);
        assertEquals(1, second.sectionPosition);
        assertTrue("templates are flagged", second.isTemplate);
        assertEquals(120, board.focusedWeekMinutes);
        assertEquals(1, board.subtasks.get(1L).size());
        assertNull(board.subtasks.get(2L));
    }

    @Test
    public void weekStartIsMondayAtMidnightLocal() {
        long wednesday = LocalDateTime.of(2026, 11, 25, 15, 30).atZone(ZONE).toInstant().toEpochMilli();
        long sunday = LocalDateTime.of(2026, 11, 29, 23, 59).atZone(ZONE).toInstant().toEpochMilli();

        assertEquals(dayStart(2026, 11, 23), ProjectBoard.weekStartMillis(wednesday, ZONE));
        assertEquals(dayStart(2026, 11, 23), ProjectBoard.weekStartMillis(sunday, ZONE));
        assertEquals(dayStart(2026, 11, 23), ProjectBoard.weekStartMillis(dayStart(2026, 11, 23), ZONE));
    }

    @Test
    public void moveTaskWritesTheNewSection() {
        repo.moveTask(5, 9);

        verify(entryDao).moveToSection(5, 9);
    }
}
