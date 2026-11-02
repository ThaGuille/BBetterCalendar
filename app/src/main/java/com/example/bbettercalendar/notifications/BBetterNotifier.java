package com.example.bbettercalendar.notifications;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;

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

    // canPost() comprueba POST_NOTIFICATIONS (o areNotificationsEnabled en <33) antes de notificar;
    // lint no puede seguir esa guarda entre métodos, de ahí la supresión.
    @SuppressLint("MissingPermission")
    public void notify(NotificationSpec spec) {
        if (!canPost()) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, spec.channelId)
                .setSmallIcon(spec.smallIconRes)
                // Acento de marca (círculo del icono / nombre de la app en la cabecera de la notificación).
                .setColor(ContextCompat.getColor(appContext, R.color.bb_primary))
                .setContentTitle(spec.title)
                .setContentText(spec.body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(spec.body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(spec.autoCancel);

        if (spec.contentIntent != null) {
            builder.setContentIntent(spec.contentIntent);
        }

        // F5-u6 (D-034 d7): en pantalla de bloqueo segura se ve la versión pública, sin el contenido privado.
        if (spec.publicTitle != null) {
            NotificationCompat.Builder publicVersion = new NotificationCompat.Builder(appContext, spec.channelId)
                    .setSmallIcon(spec.smallIconRes)
                    .setColor(ContextCompat.getColor(appContext, R.color.bb_primary))
                    .setContentTitle(spec.publicTitle)
                    .setContentText(spec.publicBody);
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion.build());
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
