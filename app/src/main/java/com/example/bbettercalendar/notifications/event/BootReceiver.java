package com.example.bbettercalendar.notifications.event;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.usage.limits.UsageLimitScheduler;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    @Inject CalendarEntryDAO calendarEntryDAO;
    @Inject EventReminderScheduler scheduler;
    @Inject UsageLimitScheduler usageLimitScheduler;

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
                List<CalendarEntry> all = calendarEntryDAO.getAllEvents();
                if (all == null) return;
                long now = System.currentTimeMillis();
                int rescheduled = 0;
                for (CalendarEntry entry : all) {
                    // Las plantillas de recurrencia no reciben alarmas (spec tasks-recurrence):
                    // sólo sus ocurrencias materializadas, que ya están en 'all' como filas propias.
                    if (!entry.isTemplate() && entry.getStartMillis() > now) {
                        scheduler.scheduleFor(entry);
                        rescheduled++;
                    }
                }
                Log.i(TAG, "Rescheduled reminders for " + rescheduled + " future events");

                usageLimitScheduler.arm();
            } catch (Exception e) {
                Log.e(TAG, "Boot reschedule failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
