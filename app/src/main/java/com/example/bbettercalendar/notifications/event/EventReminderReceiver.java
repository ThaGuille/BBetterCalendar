package com.example.bbettercalendar.notifications.event;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationSpec;
import com.example.bbettercalendar.projects.Project;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class EventReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "EventReminderReceiver";

    @Inject CalendarEntryDAO calendarEntryDAO;
    @Inject BBetterNotifier notifier;
    @Inject @IoExecutor ExecutorService IO;

    @Override
    public void onReceive(Context context, Intent intent) {
        final int entryId = intent.getIntExtra(EventReminderScheduler.EXTRA_ENTRY_ID, -1);
        final int offsetIndex = intent.getIntExtra(EventReminderScheduler.EXTRA_OFFSET_INDEX, -1);
        if (entryId < 0 || offsetIndex < 0) return;

        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();

        IO.execute(() -> {
            try {
                CalendarEntry entry = calendarEntryDAO.getEventById(entryId);
                if (entry == null) {
                    Log.w(TAG, "Entry " + entryId + " missing, skipping");
                    return;
                }
                // Hecha o retirada: el aviso ya no tiene sentido (la alarma pudo sobrevivir al cambio).
                if (entry.isDone() || entry.isDismissed()) return;
                // Tarea de un proyecto archivado o completado (rediseno-proyectos 3.8): sin aviso, aunque
                // la alarma (o un "posponer") sobreviviera al cierre.
                if (!isInOpenProject(entry)) return;
                fireNotification(appContext, entry, offsetIndex);
            } catch (Exception e) {
                Log.e(TAG, "onReceive failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private boolean isInOpenProject(CalendarEntry entry) {
        if (entry.getProjectId() == 0) return true;
        Integer status = calendarEntryDAO.getProjectStatus(entry.getProjectId());
        return status == null || status == Project.STATUS_ACTIVE;
    }

    private void fireNotification(Context context, CalendarEntry entry, int offsetIndex) {
        String title = entry.getTitle() != null ? entry.getTitle()
                : context.getString(R.string.notif_event_default_title);
        String body = bodyFor(context, entry.getStartMillis(), System.currentTimeMillis());

        NotificationSpec.Builder spec = new NotificationSpec.Builder(
                NotificationChannels.CHANNEL_EVENT_REMINDERS,
                notificationIdFor(entry.getId(), offsetIndex))
                .title(title)
                .body(body)
                .openMainActivity(context);

        int id = entry.getId();
        if (entry.getType() == AddEventActivity.TYPE_TASK) {
            spec.addAction(new NotificationCompat.Action(R.drawable.ic_check_24,
                    context.getString(R.string.notif_reminder_action_done),
                    ReminderActionReceiver.actionIntent(context, ReminderActionReceiver.ACTION_DONE,
                            id, offsetIndex, ReminderActionReceiver.doneRequestCode(id, offsetIndex))));
        }
        spec.addAction(new NotificationCompat.Action(R.drawable.ic_clock_v2_24,
                context.getString(R.string.notif_reminder_action_snooze),
                ReminderActionReceiver.actionIntent(context, ReminderActionReceiver.ACTION_SNOOZE,
                        id, offsetIndex, ReminderActionReceiver.snoozeRequestCode(id, offsetIndex))));

        notifier.notify(spec.build());
    }

    /** Cuerpo según la hora real de inicio (ReminderTiming), no la etiqueta del offset. */
    static String bodyFor(Context context, long startMillis, long now) {
        if (startMillis <= 0L) return context.getString(R.string.notif_event_default_title);
        java.util.Date start = new java.util.Date(startMillis);
        String time = android.text.format.DateFormat.getTimeFormat(context).format(start);
        ReminderTiming timing = ReminderTiming.of(startMillis, now, java.util.TimeZone.getDefault());
        switch (timing.kind) {
            case NOW:
                return context.getString(R.string.notif_reminder_body_now, time);
            case IN_MINUTES:
                return context.getResources().getQuantityString(
                        R.plurals.notif_reminder_body_in_minutes, timing.minutes, timing.minutes, time);
            case TODAY_AT:
                return context.getString(R.string.notif_reminder_body_today, time);
            case TOMORROW_AT:
                return context.getString(R.string.notif_reminder_body_tomorrow, time);
            default:
                String date = android.text.format.DateFormat.getMediumDateFormat(context).format(start);
                return context.getString(R.string.notif_reminder_body_on_date, date, time);
        }
    }

    // La recurrencia ya no se avanza aquí (spec tasks-recurrence): las ocurrencias se
    // pre-materializan como filas propias con sus recordatorios (ver RecurrenceMaterializer),
    // así que el receiver sólo notifica.

    // Público para que el test de particiones de ids compare contra la fórmula real y no una copia
    // (spec project-deadlines-progress). Ver la tabla de rangos en notifications.md.
    public static int notificationIdFor(int entryId, int offsetIndex) {
        return 100_000 + entryId * 10 + offsetIndex;
    }
}
