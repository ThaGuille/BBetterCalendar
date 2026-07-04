package com.example.bbettercalendar.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.example.bbettercalendar.R;

public final class NotificationChannels {

    public static final String CHANNEL_FOREGROUND_SERVICE = "channelId";
    public static final String CHANNEL_EVENT_REMINDERS = "bb_event_reminders";
    public static final String CHANNEL_FOCUS_ALERTS = "bb_focus_alerts";
    public static final String CHANNEL_USAGE_LIMITS = "bb_usage_limits";

    public static void createAll(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel foreground = new NotificationChannel(
                CHANNEL_FOREGROUND_SERVICE,
                context.getString(R.string.notif_channel_foreground_service_name),
                NotificationManager.IMPORTANCE_LOW);
        foreground.setDescription(context.getString(R.string.notif_channel_foreground_service_desc));

        NotificationChannel events = new NotificationChannel(
                CHANNEL_EVENT_REMINDERS,
                context.getString(R.string.notif_channel_event_reminders_name),
                NotificationManager.IMPORTANCE_HIGH);
        events.setDescription(context.getString(R.string.notif_channel_event_reminders_desc));

        NotificationChannel focus = new NotificationChannel(
                CHANNEL_FOCUS_ALERTS,
                context.getString(R.string.notif_channel_focus_alerts_name),
                NotificationManager.IMPORTANCE_HIGH);
        focus.setDescription(context.getString(R.string.notif_channel_focus_alerts_desc));

        NotificationChannel usageLimits = new NotificationChannel(
                CHANNEL_USAGE_LIMITS,
                context.getString(R.string.notif_channel_usage_limits_name),
                NotificationManager.IMPORTANCE_HIGH);
        usageLimits.setDescription(context.getString(R.string.notif_channel_usage_limits_desc));

        nm.createNotificationChannel(foreground);
        nm.createNotificationChannel(events);
        nm.createNotificationChannel(focus);
        nm.createNotificationChannel(usageLimits);
    }

    private NotificationChannels() {}
}
