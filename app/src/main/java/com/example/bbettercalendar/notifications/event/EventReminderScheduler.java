package com.example.bbettercalendar.notifications.event;

import android.app.AlarmManager;
import android.content.Context;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.notifications.NotificationOffsets;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Thin client of {@link AlarmReminderCore} for entry reminders (spec reminder-generalization /
 * roadmap finding F7). All the actual schedule/cancel/exact-alarm-fallback mechanics live in the
 * shared core; this class only supplies the entry-reminder specifics: the receiver, the
 * {@code entityId * 10 + offsetIndex} request-code formula (kept byte-for-byte identical to the
 * pre-refactor formula so already-scheduled real-device PendingIntents stay cancellable), and the
 * {@code EXTRA_ENTRY_ID}/{@code EXTRA_OFFSET_INDEX} intent extras.
 *
 * Public API is unchanged on purpose: {@code RecurrenceMaterializer}-style callers construct this
 * directly outside Hilt ({@code new EventReminderScheduler(app, am)} —
 * see {@code calendarEntries/RecurrenceMaterializer.java}), so the constructor signature is a hard
 * compatibility constraint.
 */
@Singleton
public class EventReminderScheduler {

    public static final String EXTRA_ENTRY_ID = "entry_id";
    public static final String EXTRA_OFFSET_INDEX = "offset_index";

    // Reproduces the original requestCode formula exactly (no behavior change). A future
    // reminder flavor (e.g. project deadlines) picks its own disjoint namespace here instead of
    // colliding with CalendarEntry ids in the same PendingIntent.getBroadcast() space.
    private static final AlarmReminderCore.RequestCodeNamespace NAMESPACE =
            (entityId, offsetIndex) -> entityId * 10 + offsetIndex;

    private static final AlarmReminderCore.IntentPopulator EXTRAS =
            (intent, entityId, offsetIndex) -> {
                intent.putExtra(EXTRA_ENTRY_ID, entityId);
                intent.putExtra(EXTRA_OFFSET_INDEX, offsetIndex);
            };

    private final AlarmReminderCore core;

    @Inject
    public EventReminderScheduler(@ApplicationContext Context context, AlarmManager alarmManager) {
        this.core = new AlarmReminderCore(context, alarmManager, EventReminderReceiver.class, NAMESPACE);
    }

    public void scheduleFor(CalendarEntry entry) {
        if (entry == null || entry.getNotifications() == null) return;
        long start = entry.getStartMillis();
        if (start <= 0L) return;

        core.schedule(entry.getId(), entry.getNotifications(), NotificationOffsets.OFFSET_MILLIS, start, EXTRAS);
    }

    public void cancelFor(CalendarEntry entry) {
        if (entry == null) return;
        core.cancel(entry.getId(), NotificationOffsets.OFFSET_MILLIS.length, EXTRAS);
    }
}
