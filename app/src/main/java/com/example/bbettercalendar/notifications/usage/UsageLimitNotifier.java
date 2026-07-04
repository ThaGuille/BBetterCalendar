package com.example.bbettercalendar.notifications.usage;

import android.content.Context;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationSpec;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Singleton;

// Las dos notificaciones de Phase 3 (warn-only, nunca bloquean): aviso previo y límite alcanzado.
// IDs por paquete para que cada app tenga su propia notificación (no se pisan entre sí ni con las
// de eventos [100_000+] o focus [50_001]).
@Singleton
public class UsageLimitNotifier {

    private static final int WARN_ID_BASE = 60_000;
    private static final int REACHED_ID_BASE = 61_000;

    private final Context appContext;
    private final BBetterNotifier notifier;

    @Inject
    public UsageLimitNotifier(@ApplicationContext Context appContext, BBetterNotifier notifier) {
        this.appContext = appContext;
        this.notifier = notifier;
    }

    public void warn(String packageName, String label, int minutesLeft) {
        NotificationSpec spec = new NotificationSpec.Builder(
                NotificationChannels.CHANNEL_USAGE_LIMITS,
                WARN_ID_BASE + idFor(packageName))
                .title(appContext.getString(R.string.notif_app_limit_warn_title, label))
                .body(appContext.getString(R.string.notif_app_limit_warn_body, minutesLeft))
                .openMainActivity(appContext)
                .build();

        notifier.notify(spec);
    }

    public void reached(String packageName, String label) {
        NotificationSpec spec = new NotificationSpec.Builder(
                NotificationChannels.CHANNEL_USAGE_LIMITS,
                REACHED_ID_BASE + idFor(packageName))
                .title(appContext.getString(R.string.notif_app_limit_reached_title, label))
                .body(appContext.getString(R.string.notif_app_limit_reached_body))
                .openMainActivity(appContext)
                .build();

        notifier.notify(spec);
    }

    private static int idFor(String packageName) {
        return Math.abs(packageName.hashCode() % 1000);
    }
}
