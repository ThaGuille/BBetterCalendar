package com.example.bbettercalendar.notifications.focus;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.MainActivity;
import com.example.bbettercalendar.R;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationIds;
import com.example.bbettercalendar.notifications.NotificationPrefs;
import com.example.bbettercalendar.notifications.NotificationSpec;

/**
 * Aviso «fin de fase» del pomodoro (B.1): {@code bb_focus_alerts}, id {@link NotificationIds#FOCUS_PHASE_END}
 * (uno solo: el de la fase siguiente sustituye al anterior). Sólo se publica en segundo plano (quien
 * llama lo comprueba: en primer plano ya avisa la propia app) y si el usuario no lo apagó
 * ({@link NotificationPrefs#isFocusPhaseEndEnabled()}). Tocarlo abre la pantalla Focus.
 *
 * <p>Se retira al volver la app a primer plano ({@link #cancel}): nunca se queda colgado y desfasado.
 */
public final class FocusPhaseEndNotifier {

    private FocusPhaseEndNotifier() {}

    /** Publica el aviso del plan que acaba de terminar; no hace nada con {@link FocusPhaseAlarm.Plan#NONE}. */
    public static void post(@NonNull Context context, @NonNull FocusPhaseAlarm.Plan ended) {
        if (ended.notice != null) {
            post(context, ended.notice, ended.nextMinutes);
        }
    }

    static void post(@NonNull Context context, @NonNull FocusPhaseAlarm.Notice notice, int nextMinutes) {
        Context app = context.getApplicationContext();
        if (!NotificationPrefs.from(app).isFocusPhaseEndEnabled()) {
            return;
        }
        String title;
        String body;
        switch (notice) {
            case FOCUS_DONE:
                title = app.getString(R.string.notif_focus_phase_focus_done_title);
                body = app.getString(R.string.notif_focus_phase_focus_done_body,
                        Math.max(1, nextMinutes));
                break;
            case BREAK_OVER:
                title = app.getString(R.string.notif_focus_phase_break_over_title);
                body = app.getString(R.string.notif_focus_phase_break_over_body);
                break;
            case SESSION_COMPLETE:
            default:
                title = app.getString(R.string.notif_focus_phase_session_complete_title);
                body = app.getString(R.string.notif_focus_phase_session_complete_body);
                break;
        }
        NotificationSpec spec = new NotificationSpec.Builder(
                NotificationChannels.CHANNEL_FOCUS_ALERTS, NotificationIds.FOCUS_PHASE_END)
                .title(title)
                .body(body)
                .contentIntent(openFocus(app, NotificationIds.FOCUS_PHASE_END))
                .autoCancel(true)
                .build();
        new BBetterNotifier(app).notify(spec);
    }

    /** Retira el aviso de fin de fase (la app volvió a primer plano). */
    public static void cancel(@NonNull Context context) {
        new BBetterNotifier(context.getApplicationContext()).cancel(NotificationIds.FOCUS_PHASE_END);
    }

    /**
     * Abre MainActivity y navega a Focus ({@link MainActivity#EXTRA_OPEN_FOCUS}, que la Activity
     * consume una vez). {@code requestCode} distingue los PendingIntent de cada notificación.
     *
     * <p>SINGLE_TOP además de CLEAR_TOP: MainActivity es {@code standard}, y CLEAR_TOP sin SINGLE_TOP
     * la destruye y la recrea, lo que limpiaría el {@code FocusSessionViewModel} (ámbito de la
     * Activity) y con él la sesión en marcha. Así la instancia viva recibe {@code onNewIntent}.
     */
    @NonNull
    static PendingIntent openFocus(@NonNull Context app, int requestCode) {
        Intent intent = new Intent(app, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_FOCUS, true);
        return PendingIntent.getActivity(app, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
