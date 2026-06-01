package com.example.bbettercalendar.notifications;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BBetterNotifier {

    private final Context appContext;

    @Inject
    public BBetterNotifier(@ApplicationContext Context appContext) {
        this.appContext = appContext;
    }

    public void notify(NotificationSpec spec) {
        if (!canPost()) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, spec.channelId)
                .setSmallIcon(spec.smallIconRes)
                .setContentTitle(spec.title)
                .setContentText(spec.body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(spec.body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(spec.autoCancel);

        if (spec.contentIntent != null) {
            builder.setContentIntent(spec.contentIntent);
        }

        NotificationManagerCompat.from(appContext).notify(spec.notificationId, builder.build());
    }

    public void cancel(int notificationId) {
        NotificationManagerCompat.from(appContext).cancel(notificationId);
    }

    private boolean canPost() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(appContext).areNotificationsEnabled();
        }
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
