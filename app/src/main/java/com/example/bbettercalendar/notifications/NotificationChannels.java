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
    public static final String CHANNEL_PROJECT_DEADLINES = "bb_project_deadlines";
    // F5-u6 (D-034): ánimos, empujones y solicitudes de amigos. Es el único control para silenciarlos (DIF-01).
    public static final String CHANNEL_FRIENDS = "bb_friends";

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

        // Canal propio, no CHANNEL_EVENT_REMINDERS: un aviso de deadline de proyecto se silencia
        // (o no) independientemente de los recordatorios de eventos (spec project-deadlines-progress).
        NotificationChannel projectDeadlines = new NotificationChannel(
                CHANNEL_PROJECT_DEADLINES,
                context.getString(R.string.notif_channel_project_deadlines_name),
                NotificationManager.IMPORTANCE_HIGH);
        projectDeadlines.setDescription(context.getString(R.string.notif_channel_project_deadlines_desc));

        // F5-u6: avisos de amigos (ánimo, empujón, solicitud). Canal propio: el usuario lo silencia en los
        // ajustes del sistema sin tocar el resto (02 §8, DIF-01).
        NotificationChannel friends = new NotificationChannel(
                CHANNEL_FRIENDS,
                context.getString(R.string.notif_channel_friends_name),
                NotificationManager.IMPORTANCE_HIGH);
        friends.setDescription(context.getString(R.string.notif_channel_friends_desc));

        nm.createNotificationChannel(foreground);
        nm.createNotificationChannel(events);
        nm.createNotificationChannel(focus);
        nm.createNotificationChannel(usageLimits);
        nm.createNotificationChannel(projectDeadlines);
        nm.createNotificationChannel(friends);
    }

    private NotificationChannels() {}
}
