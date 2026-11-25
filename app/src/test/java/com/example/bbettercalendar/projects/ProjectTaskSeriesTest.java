package com.example.bbettercalendar.projects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.InMemoryEntries;
import com.example.bbettercalendar.calendarEntries.RecurrenceMaterializer;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.popups.RepetitionOptions;
import com.example.bbettercalendar.projects.domain.DateChoices;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.stats.FocusEventDAO;

import org.junit.Before;
import org.junit.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Repeat inside a project (spec 3.8, user decision d5): a draft with Repeat becomes a series like the
 * Calendar's (template plus materialized occurrences); editing an occurrence changes only it, changing
 * Repeat changes the series from there and turning it off ends it.
 */
public class ProjectTaskSeriesTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final long DAY = 24 * 60 * 60 * 1000L;

    private InMemoryEntries table;
    private TaskSubtaskDAO subtaskDao;
    private ProjectTasksRepository repo;

    @Before
    public void setUp() {
        table = new InMemoryEntries();
        table.statusOf = id -> Project.STATUS_ACTIVE;
        subtaskDao = mock(TaskSubtaskDAO.class);
        EventReminderScheduler reminders = mock(EventReminderScheduler.class);
        TaskStartAlarm startAlarm = mock(TaskStartAlarm.class);
        repo = new ProjectTasksRepository(table.dao, mock(ProjectSectionDAO.class), subtaskDao,
                mock(FocusEventDAO.class), new TaskAlarms(reminders, startAlarm, table.dao),
                new RecurrenceMaterializer(table.dao, reminders, startAlarm), Runnable::run, Runnable::run,
                new DirectExecutor());
    }

    private static TaskDraft weeklyDraft() {
        long today = DateChoices.startOfToday(System.currentTimeMillis(), ZONE);
        TaskDraft d = new TaskDraft(today);
        d.projectId = 3;
        d.sectionId = 12;
        d.title = "Regar el huerto";
        d.priority = CalendarEntry.PRIORITY_MEDIUM;
        d.tagId = 9;
        d.setDay(today);
        d.setTime(9 * 60);
        d.setDurationMinutes(15);
        d.setRepeat(TaskDraft.Repeat.WEEKLY);
        d.subtasks.add(new TaskDraft.Subtask("Abrir el grifo", false));
        return d;
    }

    private int save(TaskDraft d) {
        AtomicInteger id = new AtomicInteger();
        repo.saveDraft(d, id::set);
        return id.get();
    }

    private CalendarEntry onlyTemplate() {
        List<CalendarEntry> templates = table.where(CalendarEntry::isTemplate);
        assertEquals(1, templates.size());
        return templates.get(0);
    }

    /** The editor's draft of a stored occurrence (what TaskEditorViewModel builds). */
    private TaskDraft editorDraftOf(int entryId) {
        CalendarEntry e = table.rows.get(entryId);
        TaskDraft d = com.example.bbettercalendar.ui.projects.editor.TaskEditorDrafts.fromEntry(e, null,
                System.currentTimeMillis(), ZONE, 12);
        d.setRepeat(ProjectTasksRepository.seriesRepeat(e, table.rows.get(e.getTemplateId())));
        return d;
    }

    @Test
    public void aNewRepeatingTaskBecomesATemplateWithWeeklyOccurrencesOfTheProject() {
        int savedId = save(weeklyDraft());

        CalendarEntry template = onlyTemplate();
        assertEquals(RepetitionOptions.WEEKLY, template.getRepetition());
        assertEquals(3, template.getProjectId());
        List<CalendarEntry> occurrences = table.occurrencesOf(template.getId());
        assertTrue("got " + occurrences.size(), occurrences.size() >= 5);
        CalendarEntry first = occurrences.get(0);
        assertEquals("the task written is the first occurrence", savedId, first.getId());
        assertEquals(template.getStartMillis(), first.getStartMillis());
        verify(subtaskDao).insertAll(anyList());
        verify(subtaskDao).deleteByEntry(eq(savedId));
        for (int i = 0; i < occurrences.size(); i++) {
            CalendarEntry o = occurrences.get(i);
            assertEquals(first.getStartMillis() + i * 7 * DAY, o.getStartMillis(), 60 * 60 * 1000L);
            assertEquals(3, o.getProjectId());
            assertEquals(12, o.getSectionId());
            assertEquals(CalendarEntry.PRIORITY_MEDIUM, o.getPriority());
            assertEquals(9, o.getTagId());
            assertEquals(15 * 60_000, o.getDuration());
            assertTrue("new project series are hidden in the Calendar, like the Calendar editor's",
                    o.isHiddenInCalendar());
        }
        assertEquals(TaskDraft.Repeat.WEEKLY, editorDraftOf(savedId).getRepeat());
    }

    @Test
    public void editingAnOccurrenceChangesOnlyThatOccurrence() {
        save(weeklyDraft());
        CalendarEntry template = onlyTemplate();
        List<CalendarEntry> before = table.occurrencesOf(template.getId());
        int second = before.get(1).getId();

        TaskDraft d = editorDraftOf(second);
        d.title = "Regar los tomates";
        d.priority = CalendarEntry.PRIORITY_HIGH;
        save(d);

        assertEquals("Regar los tomates", table.rows.get(second).getTitle());
        assertEquals(CalendarEntry.PRIORITY_HIGH, table.rows.get(second).getPriority());
        assertEquals(template.getId(), table.rows.get(second).getTemplateId());
        assertEquals("Regar el huerto", template.getTitle());
        assertEquals(RepetitionOptions.WEEKLY, template.getRepetition());
        List<CalendarEntry> after = table.occurrencesOf(template.getId());
        assertEquals(before.size(), after.size());
        for (CalendarEntry o : after) {
            if (o.getId() != second) assertEquals("Regar el huerto", o.getTitle());
        }
    }

    @Test
    public void turningRepeatOffOnAnOccurrenceEndsTheSeries() {
        save(weeklyDraft());
        CalendarEntry template = onlyTemplate();
        List<CalendarEntry> before = table.occurrencesOf(template.getId());
        CalendarEntry first = before.get(0);
        CalendarEntry second = before.get(1);

        TaskDraft d = editorDraftOf(second.getId());
        d.setRepeat(TaskDraft.Repeat.NONE);
        save(d);

        assertEquals(RepetitionOptions.NONE, template.getRepetition());
        List<CalendarEntry> left = table.occurrencesOf(template.getId());
        assertEquals("only the earlier occurrence stays in the series", 1, left.size());
        assertEquals(first.getId(), left.get(0).getId());
        CalendarEntry edited = table.rows.get(second.getId());
        assertNotNull(edited);
        assertEquals("the edited one is a plain task now", 0, edited.getTemplateId());
        assertEquals(TaskDraft.Repeat.NONE, editorDraftOf(second.getId()).getRepeat());
        new RecurrenceMaterializer(table.dao, mock(EventReminderScheduler.class), mock(TaskStartAlarm.class))
                .materializeAll();
        assertEquals("an ended series is not refilled", 1, table.occurrencesOf(template.getId()).size());
    }

    @Test
    public void changingRepeatOnAnOccurrenceChangesTheSeriesFromThere() {
        save(weeklyDraft());
        CalendarEntry template = onlyTemplate();
        List<CalendarEntry> before = table.occurrencesOf(template.getId());
        CalendarEntry second = before.get(1);

        TaskDraft d = editorDraftOf(second.getId());
        d.setRepeat(TaskDraft.Repeat.DAILY);
        save(d);

        CalendarEntry changed = table.rows.get(template.getId());
        assertEquals(RepetitionOptions.DAILY, changed.getRepetition());
        assertTrue(changed.isTemplate());
        assertEquals("anchored at the edited occurrence", second.getStartMillis(), changed.getStartMillis());
        List<CalendarEntry> after = table.occurrencesOf(template.getId());
        assertEquals(before.get(0).getId(), after.get(0).getId());
        assertEquals(second.getId(), after.get(1).getId());
        // From the edited occurrence on, one a day.
        for (int i = 2; i < after.size(); i++) {
            assertEquals(second.getStartMillis() + (i - 1) * DAY, after.get(i).getStartMillis(), 60 * 60 * 1000L);
        }
        assertTrue(after.size() > before.size());
    }
}
