package com.example.bbettercalendar.notifications.event;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.notifications.project.ProjectDeadlineScheduler;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDAO;
import com.example.bbettercalendar.usage.limits.UsageLimitScheduler;

import java.util.List;
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
    @Inject @IoExecutor ExecutorService IO;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        IO.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                // El bloque tiene ya tres inquilinos independientes: que uno no encuentre filas no
                // puede saltarse los otros dos (antes un getAllEvents() nulo se llevaba por delante
                // el arm() del monitor de límites).
                List<CalendarEntry> all = calendarEntryDAO.getAllEvents();
                int rescheduled = 0;
                if (all != null) {
                    for (CalendarEntry entry : all) {
                        // Las plantillas de recurrencia no reciben alarmas (spec tasks-recurrence):
                        // sólo sus ocurrencias materializadas, que ya están en 'all' como filas propias.
                        if (!entry.isTemplate() && entry.getStartMillis() > now) {
                            scheduler.scheduleFor(entry);
                            rescheduled++;
                        }
                    }
                }
                Log.i(TAG, "Rescheduled reminders for " + rescheduled + " future events");

                // Tercer inquilino de este bloque (spec project-deadlines-progress): las alarmas de
                // deadline de proyecto también se pierden en el reinicio.
                List<Project> projects = projectDao.getActiveWithDeadlineAfter(now);
                if (projects != null) {
                    for (Project project : projects) {
                        projectDeadlineScheduler.scheduleFor(project);
                    }
                    Log.i(TAG, "Rescheduled deadlines for " + projects.size() + " active projects");
                }

                usageLimitScheduler.arm();
            } catch (Exception e) {
                Log.e(TAG, "Boot reschedule failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
