package com.example.bbettercalendar.notifications.focus;

import android.content.Context;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationSpec;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Singleton;

// Aviso de auto-completado de una tarea al alcanzar su objetivo de minutos (spec focus-attribution).
// Fallback en notificación (por si la app quedó en segundo plano); el feedback in-app háptico/sonoro
// lo dispara HomeFragment cuando el usuario está en primer plano. Reutiliza el canal de alertas de
// concentración (mismo dominio que FocusFailNotifier).
@Singleton
public class FocusCompleteNotifier {

    private static final int FOCUS_COMPLETE_NOTIFICATION_ID = 50_002;

    private final Context appContext;
    private final BBetterNotifier notifier;

    @Inject
    public FocusCompleteNotifier(@ApplicationContext Context appContext, BBetterNotifier notifier) {
        this.appContext = appContext;
        this.notifier = notifier;
    }

    public void fire(String taskTitle) {
        NotificationSpec spec = new NotificationSpec.Builder(
                NotificationChannels.CHANNEL_FOCUS_ALERTS,
                FOCUS_COMPLETE_NOTIFICATION_ID)
                .title(appContext.getString(R.string.notif_focus_complete_title))
                .body(appContext.getString(R.string.notif_focus_complete_body, taskTitle))
                .openMainActivity(appContext)
                .build();

        notifier.notify(spec);
    }
}
