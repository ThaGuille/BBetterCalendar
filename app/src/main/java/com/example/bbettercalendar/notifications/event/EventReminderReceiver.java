package com.example.bbettercalendar.notifications.event;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationSpec;
import com.example.bbettercalendar.popups.NotificationOffsets;
import com.example.bbettercalendar.popups.RepetitionOptions;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class EventReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "EventReminderReceiver";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    @Inject CalendarEntryDAO calendarEntryDAO;
    @Inject BBetterNotifier notifier;
    @Inject EventReminderScheduler scheduler;

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
                fireNotification(appContext, entry, offsetIndex);
                rescheduleIfRepeating(entry, offsetIndex);
            } catch (Exception e) {
                Log.e(TAG, "onReceive failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void fireNotification(Context context, CalendarEntry entry, int offsetIndex) {
        String title = entry.getTitle() != null ? entry.getTitle()
                : context.getString(R.string.notif_event_default_title);
        String offsetLabel = NotificationOffsets.labelFor(context, offsetIndex);
        String body = context.getString(R.string.notif_event_body_format, offsetLabel);

        NotificationSpec spec = new NotificationSpec.Builder(
                NotificationChannels.CHANNEL_EVENT_REMINDERS,
                eventNotificationId(entry.getId(), offsetIndex))
                .title(title)
                .body(body)
                .openMainActivity(context)
                .build();

        notifier.notify(spec);
    }

    private void rescheduleIfRepeating(CalendarEntry entry, int offsetIndex) {
        int rep = entry.getRepetition();
        if (rep == RepetitionOptions.NONE) return;

        long currentStart = entry.getStartMillis();
        long nextStart = advanceStart(currentStart, rep);
        if (nextStart <= 0L) return;

        entry.setStartMillis(nextStart);
        if (entry.getStartDayAndHour() != null) {
            entry.getStartDayAndHour().setTimeInMillis(nextStart);
        }
        long endDelta = entry.getEndMillis() - currentStart;
        if (endDelta > 0L) {
            entry.setEndMillis(nextStart + endDelta);
            if (entry.getEndDayAndHour() != null) {
                entry.getEndDayAndHour().setTimeInMillis(nextStart + endDelta);
            }
        }
        calendarEntryDAO.update(entry);

        scheduler.scheduleFor(entry);
    }

    private static long advanceStart(long startMillis, int repetition) {
        if (startMillis <= 0L) return 0L;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startMillis);
        switch (repetition) {
            case RepetitionOptions.DAILY: cal.add(Calendar.DAY_OF_YEAR, 1); break;
            case RepetitionOptions.WEEKLY: cal.add(Calendar.WEEK_OF_YEAR, 1); break;
            case RepetitionOptions.MONTHLY: cal.add(Calendar.MONTH, 1); break;
            default: return 0L;
        }
        return cal.getTimeInMillis();
    }

    private static int eventNotificationId(int entryId, int offsetIndex) {
        return 100_000 + entryId * 10 + offsetIndex;
    }
}
