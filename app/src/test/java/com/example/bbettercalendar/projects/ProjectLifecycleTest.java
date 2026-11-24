package com.example.bbettercalendar.projects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.InMemoryEntries;
import com.example.bbettercalendar.calendarEntries.RecurrenceMaterializer;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.notifications.project.ProjectDeadlineScheduler;
import com.example.bbettercalendar.popups.RepetitionOptions;
import com.example.bbettercalendar.projects.domain.NextTaskRule;
import com.example.bbettercalendar.projects.domain.ProjectProgress;
import com.example.bbettercalendar.projects.domain.TaskSnapshot;
import com.example.bbettercalendar.projects.domain.TodayBadge;

import org.junit.Before;
import org.junit.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Closing a project (archive, complete) cuts its repeating series and silences its tasks; reopening it
 * (unarchive, reopen, Undo) brings them back from now on (spec 3.8). Driven through
 * {@link ProjectsRepository}, the entry point the screen uses, over an in-memory calendarEntry table.
 */
public class ProjectLifecycleTest {

    private static final int PROJECT = 5;
    private static final long HOUR = 60 * 60 * 1000L;
    private static final long DAY = 24 * HOUR;

    private InMemoryEntries table;
    private EventReminderScheduler reminders;
    private TaskStartAlarm startAlarm;
    private RecurrenceMaterializer materializer;
    private Project project;
    private ProjectsRepository repo;

    @Before
    public void setUp() {
        table = new InMemoryEntries();
        reminders = mock(EventReminderScheduler.class);
        startAlarm = mock(TaskStartAlarm.class);
        project = new Project();
        project.id = PROJECT;
        project.status = Project.STATUS_ACTIVE;
        table.statusOf = id -> id == PROJECT ? project.status : null;
        ProjectDAO projectDao = mock(ProjectDAO.class);
        when(projectDao.getById(PROJECT)).thenReturn(project);

        TaskAlarms taskAlarms = new TaskAlarms(reminders, startAlarm, table.dao);
        materializer = new RecurrenceMaterializer(table.dao, reminders, startAlarm);
        ProjectLifecycle lifecycle = new ProjectLifecycle(table.dao, taskAlarms, materializer);
        repo = new ProjectsRepository(projectDao, mock(ProjectSectionDAO.class), table.dao,
                mock(TaskSubtaskDAO.class), mock(ProjectDeadlineScheduler.class), taskAlarms, lifecycle,
                Runnable::run, Runnable::run, resId -> "Tasks", new DirectExecutor());
    }

    /** A daily series of the project anchored at {@code anchor}, filled up to the horizon; returns its id. */
    private int dailySeries(long anchor) {
        return series(anchor, RepetitionOptions.DAILY);
    }

    private int series(long anchor, int repetition) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(anchor);
        CalendarEntry template = new CalendarEntry.EventBuilder()
                .setEventType(2)
                .setEventTitle("Regar el huerto")
                .setEventProjectId(PROJECT)
                .setEventDuration(15 * 60_000)
                .setEventStartDayAndHour(start)
                .setEventRepetition(repetition)
                .setEventRepetitionInterval(1)
                .setEventIsTemplate(true)
                .build();
        int id = table.put(template);
        materializer.materializeTemplate(id);
        return id;
    }

    private List<CalendarEntry> futureOccurrences(int templateId, long now) {
        List<CalendarEntry> out = new ArrayList<>();
        for (CalendarEntry e : table.occurrencesOf(templateId)) {
            if (e.getStartMillis() > now) out.add(e);
        }
        return out;
    }

    /** What the materializer would do on a later start-up, once its watermark is behind the horizon again. */
    private void laterTopUp(int templateId, long watermark) {
        table.rows.get(templateId).setMaterializedUntilMillis(watermark);
        materializer.materializeAll();
    }

    @Test
    public void archivingCancelsTheAlarmsOfEveryTaskAndCutsTheSeries() {
        long now = System.currentTimeMillis();
        int templateId = dailySeries(now - 3 * HOUR);
        CalendarEntry pastOccurrence = table.occurrencesOf(templateId).get(0);
        List<CalendarEntry> future = futureOccurrences(templateId, now);
        assertFalse(future.isEmpty());
        int before = table.occurrencesOf(templateId).size();

        repo.archive(PROJECT);

        assertEquals(Project.STATUS_ARCHIVED, project.status);
        for (CalendarEntry e : table.occurrencesOf(templateId)) {
            verify(reminders).cancelFor(e);
            verify(startAlarm).cancel(e);
        }
        for (CalendarEntry e : future) assertTrue("future occurrence retired", e.isDismissed());
        assertFalse("the one already started stays", pastOccurrence.isDismissed());
        laterTopUp(templateId, now);
        assertEquals("no new occurrences while archived", before, table.occurrencesOf(templateId).size());
    }

    @Test
    public void completingCancelsTheAlarmsOfEveryTaskAndCutsTheSeries() {
        long now = System.currentTimeMillis();
        int templateId = dailySeries(now - 3 * HOUR);
        List<CalendarEntry> future = futureOccurrences(templateId, now);
        int before = table.occurrencesOf(templateId).size();
        clearInvocations(reminders, startAlarm);

        repo.complete(PROJECT);

        assertEquals(Project.STATUS_COMPLETED, project.status);
        for (CalendarEntry e : future) {
            assertTrue(e.isDismissed());
            verify(reminders).cancelFor(e);
            verify(startAlarm).cancel(e);
        }
        verify(reminders, never()).scheduleFor(any());
        laterTopUp(templateId, now);
        assertEquals("no new occurrences while completed", before, table.occurrencesOf(templateId).size());
    }

    @Test
    public void undoingTheArchiveBringsTheSameOccurrencesBackAndRearmsThem() {
        long now = System.currentTimeMillis();
        int templateId = dailySeries(now - 3 * HOUR);
        List<CalendarEntry> future = futureOccurrences(templateId, now);
        int before = table.occurrencesOf(templateId).size();
        repo.archive(PROJECT);
        clearInvocations(reminders, startAlarm);

        repo.unarchive(PROJECT);

        assertEquals(Project.STATUS_ACTIVE, project.status);
        assertEquals("no duplicates", before, table.occurrencesOf(templateId).size());
        for (CalendarEntry e : future) {
            assertFalse(e.isDismissed());
            verify(reminders).scheduleFor(e);
            verify(startAlarm).schedule(e);
        }
    }

    @Test
    public void reopeningAfterALongClosureResumesTheSeriesFromNow() {
        long now = System.currentTimeMillis();
        project.status = Project.STATUS_COMPLETED;
        // Completed 20 days ago: the series stopped there and nothing was generated since.
        long anchor = now - 30 * DAY;
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(anchor);
        CalendarEntry template = new CalendarEntry.EventBuilder()
                .setEventType(2)
                .setEventTitle("Regar el huerto")
                .setEventProjectId(PROJECT)
                .setEventStartDayAndHour(start)
                .setEventRepetition(RepetitionOptions.DAILY)
                .setEventRepetitionInterval(1)
                .setEventIsTemplate(true)
                .build();
        template.setMaterializedUntilMillis(now - 20 * DAY);
        int templateId = table.put(template);

        repo.reopen(PROJECT);

        List<CalendarEntry> occurrences = table.occurrencesOf(templateId);
        assertTrue("got " + occurrences.size(), occurrences.size() >= RecurrenceMaterializer.HORIZON_DAYS - 1);
        for (CalendarEntry e : occurrences) {
            assertTrue("nothing generated for the closed days", e.getStartMillis() > now);
            assertEquals(PROJECT, e.getProjectId());
        }
        assertTrue(table.rows.get(templateId).getMaterializedUntilMillis() > now);
    }

    @Test
    public void progressBadgeAndNextTaskNeverCountTheSeriesTemplate() {
        // Today at 00:00: the template and its first occurrence are both due today.
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        int templateId = series(midnight.getTimeInMillis(), RepetitionOptions.WEEKLY);
        List<CalendarEntry> occurrences = table.occurrencesOf(templateId);
        assertEquals("today and the next 5 weeks", 6, occurrences.size());
        occurrences.get(0).setDone(true);
        List<CalendarEntry> rows = new ArrayList<>(table.rows.values());

        ProjectBoard board = ProjectBoard.assemble(PROJECT, Collections.<ProjectSection>emptyList(), rows,
                Collections.<TaskSubtask>emptyList(), null, null);
        List<TaskSnapshot> tasks = board.tasks;

        assertEquals(occurrences.size() + 1, tasks.size());
        // 1 of the 6 occurrences done (17 %); counting the template would give 1 of 7 (14 %).
        assertEquals(17, ProjectProgress.percent(tasks));
        TodayBadge badge = TodayBadge.of(tasks, System.currentTimeMillis(), ZoneId.systemDefault());
        assertEquals("today's occurrence is done; the template due today does not count", 0, badge.count);
        TaskSnapshot next = NextTaskRule.next(tasks);
        assertNotNull(next);
        assertFalse(next.isTemplate);
        assertEquals(occurrences.get(1).getId(), next.id);
    }
}
