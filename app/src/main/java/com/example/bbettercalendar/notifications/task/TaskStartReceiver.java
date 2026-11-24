package com.example.bbettercalendar.notifications.task;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.bbettercalendar.BuildConfig;
import com.example.bbettercalendar.MainActivity;
import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationSpec;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDAO;

import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Aviso de empezar una tarea de proyecto. Recibe tres cosas, todas en el executor de IO (goAsync):
 * la alarma de {@link TaskStartScheduler} (entry_id), el boton «En 10 min» ({@link #ACTION_SNOOZE}) y,
 * solo en debug, el disparo a mano sin accion con {@link #EXTRA_DEBUG_FIRST_ELIGIBLE}. La tarea se
 * vuelve a leer porque la alarma se programo en el pasado: una tarea hecha, borrada o de un proyecto
 * archivado ya no avisa.
 *
 * <p>Canal por {@link TaskStartPolicy#channelFor}: el de las tareas que bloquean lleva la vibracion
 * larga; es la unica vibracion fuerte. Con permiso de notificaciones lo decide {@link BBetterNotifier}.
 * <pre>
 * adb shell am broadcast -f 32 -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.notifications.task.TaskStartReceiver --ez debug_first_eligible true
 * </pre>
 */
@AndroidEntryPoint
public class TaskStartReceiver extends BroadcastReceiver {

    private static final String TAG = "TaskStartReceiver";

    public static final String ACTION_SNOOZE = "com.example.bbettercalendar.taskstart.SNOOZE";
    /** Solo depuracion: avisa ya de la primera tarea pendiente que bloquea. */
    public static final String EXTRA_DEBUG_FIRST_ELIGIBLE = "debug_first_eligible";

    @Inject CalendarEntryDAO entryDao;
    @Inject ProjectDAO projectDao;
    @Inject BBetterNotifier notifier;
    @Inject TaskStartScheduler scheduler;
    @Inject @IoExecutor ExecutorService io;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        final String action = intent.getAction();
        final boolean snooze = ACTION_SNOOZE.equals(action);
        final boolean debug = action == null && BuildConfig.DEBUG
                && intent.getBooleanExtra(EXTRA_DEBUG_FIRST_ELIGIBLE, false);
        final int entryId = intent.getIntExtra(TaskStartScheduler.EXTRA_ENTRY_ID, -1);
        if (!snooze && !debug && (action != null || entryId < 0)) return;

        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        io.execute(() -> {
            try {
                if (snooze) {
                    snooze(entryId);
                } else if (debug) {
                    CalendarEntry first = firstEligibleBlocking();
                    if (first == null) {
                        Log.i(TAG, "debug: no eligible task");
                    } else {
                        post(app, first);
                    }
                } else {
                    CalendarEntry entry = entryDao.getEventById(entryId);
                    if (entry != null && isPending(entry)) post(app, entry);
                    else Log.i(TAG, "Dropping stale task-start alarm for entry " + entryId);
                }
            } catch (Exception e) {
                Log.e(TAG, "task start failed", e);
            } finally {
                pending.finish();
            }
        });
    }

    private void snooze(int entryId) {
        notifier.cancel(TaskStartIds.notificationId(entryId));
        CalendarEntry entry = entryDao.getEventById(entryId);
        if (entry == null || !isPending(entry)) return;
        scheduler.snooze(entryId, System.currentTimeMillis());
    }

    /** Sigue siendo una tarea pendiente de un proyecto activo (la hora ya pasó cuando se pospone). */
    private boolean isPending(CalendarEntry entry) {
        if (entry.getProjectId() == 0) return false;
        Project project = projectDao.getById(entry.getProjectId());
        return project != null && project.status == Project.STATUS_ACTIVE
                && TaskStartPolicy.isEligible(true, entry.getDuration(), entry.getTargetMinutes(),
                entry.isDone(), entry.isTemplate(), entry.isDismissed(), true, true);
    }

    private CalendarEntry firstEligibleBlocking() {
        List<CalendarEntry> all = entryDao.getAllEvents();
        if (all == null) return null;
        CalendarEntry best = null;
        for (CalendarEntry e : all) {
            if (e.getType() != AddEventActivity.TYPE_TASK || !e.isBlockOnFocus()) continue;
            if (!TaskStartPolicy.hasTimeOfDay(e.getStartMillis(), TimeZone.getDefault())) continue;
            if (!isPending(e)) continue;
            if (best == null || e.getStartMillis() < best.getStartMillis()) best = e;
        }
        return best;
    }

    private void post(Context app, CalendarEntry entry) {
        Project project = projectDao.getById(entry.getProjectId());
        TaskStartPolicy.Content content = TaskStartPolicy.content(
                project == null ? null : project.name, entry.getDuration(), entry.getTargetMinutes(),
                entry.isBlockOnFocus());
        int id = entry.getId();

        StringBuilder body = new StringBuilder();
        if (content.showProject) body.append(content.projectName);
        if (content.durationMinutes > 0) {
            if (body.length() > 0) body.append(" · ");
            body.append(app.getString(R.string.notif_task_start_duration, content.durationMinutes));
        }
        if (content.showBlock) {
            if (body.length() > 0) body.append(" · ");
            body.append(app.getString(R.string.notif_task_start_block));
        }

        String title = app.getString(R.string.notif_task_start_title,
                entry.getTitle() == null ? "" : entry.getTitle());
        NotificationSpec.Builder spec = new NotificationSpec.Builder(
                TaskStartPolicy.channelFor(content.showBlock), TaskStartIds.notificationId(id))
                .title(title)
                .body(body.toString())
                .category(NotificationCompat.CATEGORY_REMINDER);
        boolean canStart = TaskStartPolicy.hasStartAction(entry.getDuration(), entry.getTargetMinutes());
        // Tocar el aviso hace lo mismo que Empezar (el aviso dice "Empieza: ..."); sin nada que enfocar abre la app.
        if (canStart) spec.contentIntent(startIntent(app, id));
        else spec.openMainActivity(app);
        if (content.showBlock) spec.vibrate(NotificationChannels.ROUND_START_VIBRATION);

        if (canStart) {
            spec.addAction(new NotificationCompat.Action(R.drawable.ic_clock_v2_24,
                    app.getString(R.string.notif_task_start_action_start), startIntent(app, id)));
        }
        spec.addAction(new NotificationCompat.Action(R.drawable.ic_clock_v2_24,
                app.getString(R.string.notif_task_start_action_snooze), snoozeIntent(app, id)));
        notifier.notify(spec.build());
    }

    private static PendingIntent startIntent(Context app, int entryId) {
        Intent intent = new Intent(app, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(MainActivity.EXTRA_START_TASK_ID, entryId);
        return PendingIntent.getActivity(app, TaskStartIds.REQ_ACTION_START_BASE + entryId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent snoozeIntent(Context app, int entryId) {
        Intent intent = new Intent(app, TaskStartReceiver.class);
        intent.setAction(ACTION_SNOOZE);
        intent.putExtra(TaskStartScheduler.EXTRA_ENTRY_ID, entryId);
        return PendingIntent.getBroadcast(app, TaskStartIds.REQ_ACTION_SNOOZE_BASE + entryId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
