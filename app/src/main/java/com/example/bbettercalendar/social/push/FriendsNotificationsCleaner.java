package com.example.bbettercalendar.social.push;

import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Quita de la bandeja del sistema los avisos de Amigos cuando la sesión desaparece (L-u7, decisión r1): un
 * {@link FirebaseAuth.AuthStateListener} que, al pasar el usuario a null (cerrar sesión, borrar la cuenta, token
 * revocado o usuario deshabilitado), cancela las notificaciones activas con id en [40_000, 49_999]
 * ({@link FriendsNotifications}) y ninguna otra. Se registra una vez al arrancar, solo con FEATURE_SOCIAL.
 */
@Singleton
public class FriendsNotificationsCleaner {

    private final Context appContext;
    private final FirebaseAuth auth;
    private boolean started;

    @Inject
    public FriendsNotificationsCleaner(@ApplicationContext Context appContext, FirebaseAuth auth) {
        this.appContext = appContext;
        this.auth = auth;
    }

    /** Registra el listener; llamarlo otra vez no hace nada. */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        auth.addAuthStateListener(a -> {
            if (a.getCurrentUser() == null) {
                cancelFriendsNotifications();
            }
        });
    }

    void cancelFriendsNotifications() {
        NotificationManager nm = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        try {
            StatusBarNotification[] active = nm.getActiveNotifications();
            int[] ids = new int[active.length];
            for (int i = 0; i < active.length; i++) {
                ids[i] = active[i].getId();
            }
            for (int id : FriendsNotifications.idsToCancel(ids)) {
                nm.cancel(id);
            }
        } catch (RuntimeException e) {
            // Sin acceso a la lista no hay nada que quitar; nunca rompe el arranque ni la sesión.
        }
    }
}
