package com.example.bbettercalendar.projects;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Alarmas de las tareas de un proyecto: los recordatorios (reutilizan {@link EventReminderScheduler},
 * asi funcionan los de las tareas) y el aviso de empezar ({@link TaskStartAlarm}). Es sincrona: la
 * llaman los repositorios desde el executor de escritura, nunca desde el hilo principal.
 */
@Singleton
public class TaskAlarms {

    private final EventReminderScheduler reminders;
    private final TaskStartAlarm startAlarm;
    private final CalendarEntryDAO calendarEntryDao;

    @Inject
    public TaskAlarms(EventReminderScheduler reminders, TaskStartAlarm startAlarm,
                      CalendarEntryDAO calendarEntryDao) {
        this.reminders = reminders;
        this.startAlarm = startAlarm;
        this.calendarEntryDao = calendarEntryDao;
    }

    /** Cancela y vuelve a programar (una tarea hecha, plantilla o retirada solo se cancela). */
    public void onTaskSaved(CalendarEntry entry) {
        if (entry == null) return;
        reminders.cancelFor(entry);
        startAlarm.cancel(entry);
        if (entry.isDone() || entry.isTemplate() || entry.isDismissed()) return;
        reminders.scheduleFor(entry);
        startAlarm.schedule(entry);
    }

    /** Tarea hecha o borrada: fuera todas sus alarmas. */
    public void onTaskClosed(CalendarEntry entry) {
        if (entry == null) return;
        reminders.cancelFor(entry);
        startAlarm.cancel(entry);
    }

    /** Proyecto archivado o completado: fuera las alarmas de todas sus tareas. */
    public void onProjectClosed(int projectId) {
        List<CalendarEntry> entries = calendarEntryDao.getItemsByProjectNow(projectId);
        if (entries == null) return;
        for (CalendarEntry entry : entries) {
            onTaskClosed(entry);
        }
    }

    /** Proyecto desarchivado o reabierto: se rearman las alarmas de sus tareas pendientes. */
    public void onProjectReopened(int projectId) {
        List<CalendarEntry> entries = calendarEntryDao.getItemsByProjectNow(projectId);
        if (entries == null) return;
        for (CalendarEntry entry : entries) {
            onTaskSaved(entry);
        }
    }
}
