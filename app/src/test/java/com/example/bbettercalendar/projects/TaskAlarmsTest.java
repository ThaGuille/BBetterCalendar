package com.example.bbettercalendar.projects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class TaskAlarmsTest {

    private EventReminderScheduler reminders;
    private TaskStartAlarm startAlarm;
    private CalendarEntryDAO dao;
    private TaskAlarms alarms;

    @Before
    public void setUp() {
        reminders = mock(EventReminderScheduler.class);
        startAlarm = mock(TaskStartAlarm.class);
        dao = mock(CalendarEntryDAO.class);
        alarms = new TaskAlarms(reminders, startAlarm, dao);
    }

    private static CalendarEntry task(int id) {
        CalendarEntry e = new CalendarEntry.EventBuilder().setEventId(id).setEventType(2)
                .setEventTitle("t" + id).build();
        return e;
    }

    @Test
    public void savingAPendingTaskSchedulesItsRemindersAndStartAlarm() {
        CalendarEntry e = task(1);

        alarms.onTaskSaved(e);

        verify(reminders).cancelFor(e);
        verify(reminders).scheduleFor(e);
        verify(startAlarm).cancel(e);
        verify(startAlarm).schedule(e);
    }

    @Test
    public void savingADoneTaskOnlyCancels() {
        CalendarEntry e = new CalendarEntry.EventBuilder().setEventId(2).setEventIsDone(true).build();

        alarms.onTaskSaved(e);

        verify(reminders).cancelFor(e);
        verify(startAlarm).cancel(e);
        verify(reminders, never()).scheduleFor(any());
        verify(startAlarm, never()).schedule(any());
    }

    @Test
    public void closingATaskCancelsWithoutScheduling() {
        CalendarEntry e = task(3);

        alarms.onTaskClosed(e);

        verify(reminders).cancelFor(e);
        verify(startAlarm).cancel(e);
        verify(reminders, never()).scheduleFor(any());
    }

    @Test
    public void closingAProjectCancelsEveryTaskOfTheProject() {
        CalendarEntry a = task(4);
        CalendarEntry b = task(5);
        when(dao.getItemsByProjectNow(7)).thenReturn(Arrays.asList(a, b));

        alarms.onProjectClosed(7);

        verify(reminders).cancelFor(a);
        verify(reminders).cancelFor(b);
        verify(startAlarm).cancel(a);
        verify(startAlarm).cancel(b);
        verify(reminders, never()).scheduleFor(any());
    }

    @Test
    public void reopeningAProjectReschedulesItsPendingTasks() {
        CalendarEntry pending = task(6);
        CalendarEntry done = new CalendarEntry.EventBuilder().setEventId(8).setEventIsDone(true).build();
        when(dao.getItemsByProjectNow(9)).thenReturn(Arrays.asList(pending, done));

        alarms.onProjectReopened(9);

        verify(reminders).scheduleFor(pending);
        verify(reminders, never()).scheduleFor(done);
    }
}
