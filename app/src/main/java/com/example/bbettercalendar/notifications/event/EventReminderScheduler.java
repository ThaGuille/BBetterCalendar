package com.example.bbettercalendar.notifications.event;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.popups.NotificationOffsets;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EventReminderScheduler {

    private static final String TAG = "EventReminderScheduler";

    public static final String EXTRA_ENTRY_ID = "entry_id";
    public static final String EXTRA_OFFSET_INDEX = "offset_index";

    private final Context appContext;
    private final AlarmManager alarmManager;

    @Inject
    public EventReminderScheduler(@ApplicationContext Context context, AlarmManager alarmManager) {
        this.appContext = context;
        this.alarmManager = alarmManager;
    }

    public void scheduleFor(CalendarEntry entry) {
        if (entry == null || entry.getNotifications() == null) return;
        long start = entry.getStartMillis();
        if (start <= 0L) return;

        boolean[] offsets = entry.getNotifications();
        long now = System.currentTimeMillis();

        for (int i = 0; i < offsets.length && i < NotificationOffsets.OFFSET_MILLIS.length; i++) {
            if (!offsets[i]) continue;
            long triggerAt = start - NotificationOffsets.OFFSET_MILLIS[i];
            if (triggerAt <= now) continue;
            scheduleOne(entry.getId(), i, triggerAt);
        }
    }

    public void cancelFor(CalendarEntry entry) {
        if (entry == null) return;
        for (int i = 0; i < NotificationOffsets.OFFSET_MILLIS.length; i++) {
            PendingIntent pi = buildPendingIntent(entry.getId(), i, PendingIntent.FLAG_NO_CREATE);
            if (pi != null) {
                alarmManager.cancel(pi);
                pi.cancel();
            }
        }
    }

    private void scheduleOne(int entryId, int offsetIndex, long triggerAt) {
        PendingIntent pi = buildPendingIntent(entryId, offsetIndex,
                PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            // Device denied exact alarms — fall back to inexact so the reminder still fires.
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    private PendingIntent buildPendingIntent(int entryId, int offsetIndex, int extraFlags) {
        Intent intent = new Intent(appContext, EventReminderReceiver.class);
        intent.putExtra(EXTRA_ENTRY_ID, entryId);
        intent.putExtra(EXTRA_OFFSET_INDEX, offsetIndex);
        int requestCode = entryId * 10 + offsetIndex;
        int flags = PendingIntent.FLAG_IMMUTABLE | extraFlags;
        return PendingIntent.getBroadcast(appContext, requestCode, intent, flags);
    }
}
