package com.example.bbettercalendar.notifications.event;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.calendarEntries.CompletionStamp;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.NotificationIds;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Botones de la notificación de recordatorio (plan sistema-notificaciones A2): «Hecho» (sólo
 * tareas) y «Posponer 10 min». No exportado: sólo lo alcanzan los PendingIntent de la propia app.
 */
@AndroidEntryPoint
public class ReminderActionReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderActionReceiver";

    public static final String ACTION_DONE = "com.example.bbettercalendar.action.REMINDER_DONE";
    public static final String ACTION_SNOOZE = "com.example.bbettercalendar.action.REMINDER_SNOOZE";
    public static final long SNOOZE_MILLIS = 10L * 60_000L;

    @Inject CalendarEntryDAO calendarEntryDAO;
    @Inject EventReminderScheduler scheduler;
    @Inject BBetterNotifier notifier;
    @Inject AlarmManager alarmManager;
    @Inject @IoExecutor ExecutorService IO;

    // Request codes por entrada (ver NotificationIds): base + entryId * 10 + offsetIndex.
    public static int doneRequestCode(int entryId, int offsetIndex) {
        return NotificationIds.REQ_ENTRY_ACTION_DONE_BASE + entryId * 10 + offsetIndex;
    }

    public static int snoozeRequestCode(int entryId, int offsetIndex) {
        return NotificationIds.REQ_ENTRY_ACTION_SNOOZE_BASE + entryId * 10 + offsetIndex;
    }

    public static int snoozedAlarmRequestCode(int entryId, int offsetIndex) {
        return NotificationIds.REQ_ENTRY_SNOOZED_ALARM_BASE + entryId * 10 + offsetIndex;
    }

    /** PendingIntent de un botón de acción hacia este receiver. */
    public static PendingIntent actionIntent(Context context, String action, int entryId,
                                             int offsetIndex, int requestCode) {
        Intent intent = new Intent(context, ReminderActionReceiver.class).setAction(action);
        intent.putExtra(EventReminderScheduler.EXTRA_ENTRY_ID, entryId);
        intent.putExtra(EventReminderScheduler.EXTRA_OFFSET_INDEX, offsetIndex);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        final String action = intent.getAction();
        final int entryId = intent.getIntExtra(EventReminderScheduler.EXTRA_ENTRY_ID, -1);
        final int offsetIndex = intent.getIntExtra(EventReminderScheduler.EXTRA_OFFSET_INDEX, -1);
        if (entryId < 0 || offsetIndex < 0) return;

        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();
        IO.execute(() -> {
            try {
                notifier.cancel(EventReminderReceiver.notificationIdFor(entryId, offsetIndex));
                if (ACTION_DONE.equals(action)) {
                    markDone(entryId);
                } else if (ACTION_SNOOZE.equals(action)) {
                    snooze(appContext, entryId, offsetIndex);
                }
            } catch (Exception e) {
                Log.e(TAG, "action failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void markDone(int entryId) {
        CalendarEntry entry = calendarEntryDAO.getEventById(entryId);
        if (entry == null) return;
        // Mismo camino que Home / Projects: CompletionStamp + update, y fuera los avisos pendientes.
        CompletionStamp.apply(entry, true, System.currentTimeMillis());
        calendarEntryDAO.update(entry);
        scheduler.cancelFor(entry);
    }

    private void snooze(Context context, int entryId, int offsetIndex) {
        long triggerAt = System.currentTimeMillis() + SNOOZE_MILLIS;
        Intent intent = new Intent(context, EventReminderReceiver.class);
        intent.putExtra(EventReminderScheduler.EXTRA_ENTRY_ID, entryId);
        intent.putExtra(EventReminderScheduler.EXTRA_OFFSET_INDEX, offsetIndex);
        PendingIntent pi = PendingIntent.getBroadcast(context,
                snoozedAlarmRequestCode(entryId, offsetIndex), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } catch (SecurityException e) {
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }
}
