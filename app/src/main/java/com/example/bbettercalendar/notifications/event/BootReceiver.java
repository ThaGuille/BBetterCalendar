package com.example.bbettercalendar.notifications.event;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.notifications.nudge.DailyNudgeScheduler;
import com.example.bbettercalendar.notifications.project.ProjectDeadlineScheduler;
import com.example.bbettercalendar.notifications.task.TaskStartScheduler;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDAO;
import com.example.bbettercalendar.usage.limits.UsageLimitScheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Inject CalendarEntryDAO calendarEntryDAO;
    @Inject EventReminderScheduler scheduler;
    @Inject UsageLimitScheduler usageLimitScheduler;
    @Inject ProjectDAO projectDao;
    @Inject ProjectDeadlineScheduler projectDeadlineScheduler;
    @Inject TaskStartScheduler taskStartScheduler;
    @Inject @IoExecutor ExecutorService IO;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        // Reinicio, actualización de la app (borra las alarmas) y cambio de hora / zona (las alarmas
        // RTC quedan desplazadas): en todos hay que reprogramar. Todo es idempotente.
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            return;
        }

        // Un cambio de hora o de zona no mueve las alarmas de entradas y deadlines (son instantes
        // absolutos), pero sí las que dependen de la hora local: avisos diarios y la medianoche de
        // los límites. La sincronización de red puede disparar TIME_SET a menudo: sólo lo necesario.
        final boolean clockOnly = Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action);
        // Antes del primer desbloqueo las SharedPreferences (almacenamiento cifrado por credencial)
        // no se pueden leer: los avisos diarios esperan al BOOT_COMPLETED que llega después.
        final boolean locked = Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action);

        final PendingResult pendingResult = goAsync();
        IO.execute(() -> {
            try {
                if (clockOnly) {
                    usageLimitScheduler.forceRearm();
                    DailyNudgeScheduler.rescheduleAll(context.getApplicationContext());
                    return;
                }
                long now = System.currentTimeMillis();
                // El bloque tiene ya tres inquilinos independientes: que uno no encuentre filas no
                // puede saltarse los otros dos (antes un getAllEvents() nulo se llevaba por delante
                // el arm() del monitor de límites).
                List<CalendarEntry> all = calendarEntryDAO.getAllEvents();
                int rescheduled = 0;
                int startAlarms = 0;
                Map<Integer, Boolean> activeProject = new HashMap<>();
                if (all != null) {
                    for (CalendarEntry entry : all) {
                        // Aviso de empezar de las tareas de proyectos activos (rediseno-proyectos 3.8):
                        // el scheduler descarta las no elegibles y el core las que ya pasaron.
                        if (entry.getProjectId() != 0 && !entry.isTemplate() && entry.getStartMillis() > now) {
                            Boolean active = activeProject.get(entry.getProjectId());
                            if (active == null) {
                                Project p = projectDao.getById(entry.getProjectId());
                                active = p != null && p.status == Project.STATUS_ACTIVE;
                                activeProject.put(entry.getProjectId(), active);
                            }
                            if (active) {
                                taskStartScheduler.schedule(entry);
                                startAlarms++;
                            }
                        }
                        // Las plantillas de recurrencia no reciben alarmas (spec tasks-recurrence):
                        // sólo sus ocurrencias materializadas, que ya están en 'all' como filas propias.
                        if (!entry.isTemplate() && entry.getStartMillis() > now) {
                            scheduler.scheduleFor(entry);
                            rescheduled++;
                        }
                    }
                }
                Log.i(TAG, "Rescheduled reminders for " + rescheduled + " future events, "
                        + startAlarms + " task-start candidates");

                // Tercer inquilino de este bloque (spec project-deadlines-progress): las alarmas de
                // deadline de proyecto también se pierden en el reinicio.
                List<Project> projects = projectDao.getActiveWithDeadlineAfter(now);
                if (projects != null) {
                    for (Project project : projects) {
                        projectDeadlineScheduler.scheduleFor(project);
                    }
                    Log.i(TAG, "Rescheduled deadlines for " + projects.size() + " active projects");
                }

                // next_trigger_at guardado sigue en el futuro tras reinicio/actualización pero la
                // alarma ya no existe: sin borrarlo arm() saldría sin reprogramar.
                usageLimitScheduler.forceRearm();
                if (!locked) {
                    DailyNudgeScheduler.rescheduleAll(context.getApplicationContext());
                }
            } catch (Exception e) {
                Log.e(TAG, "Boot reschedule failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
