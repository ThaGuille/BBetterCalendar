package com.example.bbettercalendar.projects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.calendarEntries.RecurrenceMaterializer;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.notifications.project.ProjectDeadlineScheduler;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectsRepositoryTest {

    private ProjectDAO projectDao;
    private ProjectSectionDAO sectionDao;
    private CalendarEntryDAO entryDao;
    private TaskSubtaskDAO subtaskDao;
    private ProjectDeadlineScheduler deadlines;
    private EventReminderScheduler reminders;
    private TaskStartAlarm startAlarm;
    private int transactions;
    private ProjectsRepository repo;

    @Before
    public void setUp() {
        projectDao = mock(ProjectDAO.class);
        sectionDao = mock(ProjectSectionDAO.class);
        entryDao = mock(CalendarEntryDAO.class);
        subtaskDao = mock(TaskSubtaskDAO.class);
        deadlines = mock(ProjectDeadlineScheduler.class);
        reminders = mock(EventReminderScheduler.class);
        startAlarm = mock(TaskStartAlarm.class);
        TaskAlarms taskAlarms = new TaskAlarms(reminders, startAlarm, entryDao);
        transactions = 0;
        TransactionRunner tx = block -> {
            transactions++;
            block.run();
        };
        ProjectLifecycle lifecycle = new ProjectLifecycle(entryDao, taskAlarms,
                new RecurrenceMaterializer(entryDao, reminders, startAlarm));
        repo = new ProjectsRepository(projectDao, sectionDao, entryDao, subtaskDao, deadlines,
                taskAlarms, lifecycle, tx, Runnable::run, resId -> "Tasks", new DirectExecutor());
    }

    private static Project project(int id, int status) {
        Project p = new Project();
        p.id = id;
        p.status = status;
        p.softDeadlineMillis = 5_000L;
        return p;
    }

    @Test
    public void createProjectInsertsAnActiveProjectAndItsFirstSectionInOneTransaction() {
        when(projectDao.insert(any(Project.class))).thenReturn(11L);
        AtomicInteger received = new AtomicInteger();

        repo.createProject("Tesis", "notas", 9_000L, 2, "book", 3, received::set);

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectDao).insert(projectCaptor.capture());
        Project saved = projectCaptor.getValue();
        assertEquals(Project.STATUS_ACTIVE, saved.status);
        assertEquals("Tesis", saved.name);
        assertEquals(2, saved.colorIndex);
        assertEquals("book", saved.iconKey);
        assertEquals(3, saved.areaId);

        ArgumentCaptor<ProjectSection> sectionCaptor = ArgumentCaptor.forClass(ProjectSection.class);
        verify(sectionDao).insert(sectionCaptor.capture());
        assertEquals(11, sectionCaptor.getValue().projectId);
        assertEquals("Tasks", sectionCaptor.getValue().name);
        assertEquals(0, sectionCaptor.getValue().position);

        assertEquals("project and section in one transaction", 1, transactions);
        assertEquals(11, received.get());
        verify(deadlines).scheduleFor(saved);
    }

    @Test
    public void updateProjectReschedulesTheDeadlineAlarms() {
        Project p = project(4, Project.STATUS_ACTIVE);
        when(projectDao.getById(4)).thenReturn(p);

        repo.updateProject(4, "Nuevo", "n", 77_000L, 1, "home", 2);

        assertEquals("Nuevo", p.name);
        assertEquals(77_000L, p.softDeadlineMillis);
        assertEquals("home", p.iconKey);
        assertEquals(2, p.areaId);
        verify(projectDao).update(p);
        InOrder order = inOrder(deadlines);
        order.verify(deadlines).cancelFor(4);
        order.verify(deadlines).scheduleFor(p);
    }

    @Test
    public void archiveSetsStatusAndSilencesTheProject() {
        Project p = project(5, Project.STATUS_ACTIVE);
        when(projectDao.getById(5)).thenReturn(p);

        CalendarEntry task = new CalendarEntry.EventBuilder().setEventId(31).build();
        when(entryDao.getItemsByProjectNow(5)).thenReturn(Arrays.asList(task));

        repo.archive(5);

        assertEquals(Project.STATUS_ARCHIVED, p.status);
        verify(projectDao).update(p);
        verify(deadlines).cancelFor(5);
        verify(reminders).cancelFor(task);
        verify(startAlarm).cancel(task);
    }

    @Test
    public void completeStampsTheCompletionTimeAndSilencesTheProject() {
        Project p = project(6, Project.STATUS_ACTIVE);
        when(projectDao.getById(6)).thenReturn(p);
        long before = System.currentTimeMillis();

        CalendarEntry task = new CalendarEntry.EventBuilder().setEventId(32).build();
        when(entryDao.getItemsByProjectNow(6)).thenReturn(Arrays.asList(task));

        repo.complete(6);

        assertEquals(Project.STATUS_COMPLETED, p.status);
        assertTrue(p.completedAtMillis >= before);
        verify(reminders).cancelFor(task);
    }

    @Test
    public void reopenGoesBackToActiveClearsTheStampAndRearmsAlarms() {
        Project p = project(7, Project.STATUS_COMPLETED);
        p.completedAtMillis = 123L;
        when(projectDao.getById(7)).thenReturn(p);

        CalendarEntry task = new CalendarEntry.EventBuilder().setEventId(33).build();
        when(entryDao.getItemsByProjectNow(7)).thenReturn(Arrays.asList(task));

        repo.reopen(7);

        assertEquals(Project.STATUS_ACTIVE, p.status);
        assertEquals(0L, p.completedAtMillis);
        verify(deadlines).scheduleFor(p);
        verify(reminders).scheduleFor(task);
        verify(startAlarm).schedule(task);
    }

    @Test
    public void deleteProjectRemovesSectionsTasksAndSubtasksAndCancelsTheirAlarms() {
        CalendarEntry a = new CalendarEntry.EventBuilder().setEventId(21).build();
        CalendarEntry b = new CalendarEntry.EventBuilder().setEventId(22).build();
        when(entryDao.getItemsByProjectNow(8)).thenReturn(Arrays.asList(a, b));

        repo.deleteProject(8);

        verify(reminders).cancelFor(a);
        verify(reminders).cancelFor(b);
        verify(subtaskDao).deleteByEntry(21);
        verify(subtaskDao).deleteByEntry(22);
        verify(entryDao).deleteItemsByProject(8);
        verify(sectionDao).deleteByProject(8);
        verify(projectDao).deleteById(8);
        verify(deadlines).cancelFor(8);
        assertEquals("all deletes in one transaction", 1, transactions);
    }

    @Test
    public void regressionF8_dayBoundsFollowTheDayOfTheInstant() {
        java.time.ZoneId zone = java.time.ZoneId.of("Europe/Madrid");
        long d1 = java.time.LocalDate.of(2026, 11, 25).atTime(23, 30).atZone(zone).toInstant().toEpochMilli();
        long d2 = java.time.LocalDate.of(2026, 11, 26).atTime(0, 30).atZone(zone).toInstant().toEpochMilli();
        long[] a = ProjectsRepository.dayBounds(d1, zone);
        long[] b = ProjectsRepository.dayBounds(d2, zone);
        assertTrue(a[0] != b[0]);
        assertEquals(a[1], b[0]);
        assertEquals(java.time.LocalDate.of(2026, 11, 25).atStartOfDay(zone).toInstant().toEpochMilli(), a[0]);
    }

    @Test
    public void regressionF8_dayBoundsSpanTwentyThreeHoursOnTheSpringDstDay() {
        java.time.ZoneId zone = java.time.ZoneId.of("Europe/Madrid");
        long noon = java.time.LocalDate.of(2026, 3, 29).atTime(12, 0).atZone(zone).toInstant().toEpochMilli();
        long[] bounds = ProjectsRepository.dayBounds(noon, zone);
        assertEquals(23L * 3_600_000L, bounds[1] - bounds[0]);
        long autumn = java.time.LocalDate.of(2026, 10, 25).atTime(12, 0).atZone(zone).toInstant().toEpochMilli();
        long[] fall = ProjectsRepository.dayBounds(autumn, zone);
        assertEquals(25L * 3_600_000L, fall[1] - fall[0]);
    }
}
