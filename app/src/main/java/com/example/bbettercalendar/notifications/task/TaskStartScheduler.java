package com.example.bbettercalendar.notifications.task;

import android.app.AlarmManager;
import android.content.Context;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.notifications.event.AlarmReminderCore;
import com.example.bbettercalendar.projects.TaskStartAlarm;
import com.example.bbettercalendar.projects.domain.TaskDraft;

import java.util.TimeZone;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Tercer cliente de {@link AlarmReminderCore}: una alarma exacta a la hora de empezar de cada tarea de
 * proyecto elegible ({@link TaskStartPolicy}). Namespace de request codes propio, disjunto del de
 * EventReminderScheduler (entryId*10), ProjectDeadlineScheduler (500_000+) y de las acciones de los
 * recordatorios (1M..3M): ver {@link TaskStartIds}. Siempre se llama desde un executor.
 */
@Singleton
public class TaskStartScheduler implements TaskStartAlarm {

    public static final String EXTRA_ENTRY_ID = "task_entry_id";
    public static final String EXTRA_OFFSET_INDEX = "task_offset_index";

    private static final long[] NO_OFFSET = {0L};
    private static final boolean[] ENABLED = {true};

    private static final AlarmReminderCore.IntentPopulator EXTRAS =
            (intent, entityId, offsetIndex) -> {
                intent.putExtra(EXTRA_ENTRY_ID, entityId);
                intent.putExtra(EXTRA_OFFSET_INDEX, offsetIndex);
            };

    private final AlarmReminderCore core;
    private final AlarmReminderCore snoozeCore;

    @Inject
    public TaskStartScheduler(@ApplicationContext Context context, AlarmManager alarmManager) {
        this.core = new AlarmReminderCore(context, alarmManager, TaskStartReceiver.class,
                TaskStartScheduler::requestCodeFor);
        this.snoozeCore = new AlarmReminderCore(context, alarmManager, TaskStartReceiver.class,
                TaskStartScheduler::snoozeRequestCodeFor);
    }

    public static int requestCodeFor(int entryId, int offsetIndex) {
        return TaskStartIds.REQ_ALARM_BASE + entryId * 10 + offsetIndex;
    }

    public static int snoozeRequestCodeFor(int entryId, int offsetIndex) {
        return TaskStartIds.REQ_SNOOZED_ALARM_BASE + entryId * 10 + offsetIndex;
    }

    @Override
    public void schedule(CalendarEntry entry) {
        if (entry == null) return;
        long start = entry.getStartMillis();
        // "A la hora" se guarda en el slot extra de los flags (TaskDraft.FLAG_AT_TIME).
        boolean eligible = TaskStartPolicy.isEligible(
                TaskStartPolicy.hasTimeOfDay(start, TimeZone.getDefault()), entry.getDuration(),
                entry.getTargetMinutes(), entry.isDone(), entry.isTemplate(), entry.isDismissed(),
                true, TaskDraft.hasAtTimeFlag(entry.getNotifications()));
        if (!eligible) return;
        core.schedule(entry.getId(), ENABLED, NO_OFFSET, TaskStartPolicy.triggerMillis(start), EXTRAS);
    }

    @Override
    public void cancel(CalendarEntry entry) {
        if (entry == null) return;
        core.cancel(entry.getId(), 1, EXTRAS);
        snoozeCore.cancel(entry.getId(), 1, EXTRAS);
    }

    /** "En 10 min": vuelve a avisar dentro de {@link TaskStartPolicy#SNOOZE_MILLIS}. */
    public void snooze(int entryId, long nowMillis) {
        snoozeCore.schedule(entryId, ENABLED, NO_OFFSET, TaskStartPolicy.snoozeMillis(nowMillis), EXTRAS);
    }
}
