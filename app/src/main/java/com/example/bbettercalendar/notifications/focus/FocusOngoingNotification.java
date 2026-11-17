package com.example.bbettercalendar.notifications.focus;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.notifications.NotificationIds;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

import java.util.Locale;

/**
 * Notificación del servicio en primer plano del pomodoro ({@code HomeForegroundService}, id 1, canal
 * heredado {@code "channelId"}), con la fase y una cuenta atrás en vivo (B.2):
 * «Concentrándote» / «En descanso» con cronómetro descendente hasta el fin de reloj, o «En pausa»
 * sin cronómetro y con el tiempo que queda. Tocarla abre Focus.
 *
 * <p>El contenido lo fija el {@code FocusSessionViewModel} en cada transición ({@link #setLatest});
 * el servicio lo lee al arrancar ({@link #build}) y, con el servicio ya en marcha, se vuelve a
 * publicar el mismo id ({@link #repost}). Así ninguna actualización necesita
 * {@code startForegroundService}, que desde segundo plano está prohibido en API 31+ (una fase puede
 * acabar con la app fuera). Como el servicio lee siempre el último contenido, un
 * {@code onStartCommand} que llega tarde no publica uno viejo.
 */
public final class FocusOngoingNotification {

    /** Id de la notificación del servicio (el de siempre). */
    public static final int ID = 1;
    /** Canal heredado del servicio (literal; ver notifications.md). */
    private static final String CHANNEL = "channelId";

    public enum Mode { FOCUS, REST, PAUSED }

    /** Qué enseñar. Inmutable. */
    public static final class Content {
        @NonNull public final Mode mode;
        /** Fin de reloj de la fase si corre; 0 en pausa. */
        public final long endsAtWallMillis;
        /** Tiempo que queda (lo que se enseña en pausa). */
        public final long remainingMillis;

        Content(@NonNull Mode mode, long endsAtWallMillis, long remainingMillis) {
            this.mode = mode;
            this.endsAtWallMillis = endsAtWallMillis;
            this.remainingMillis = remainingMillis;
        }

        /** Cronómetro descendente sólo mientras corre. */
        public boolean usesChronometer() {
            return mode != Mode.PAUSED;
        }

        /**
         * Contenido para un estado del timer; null en STOPPED (sin sesión, sin servicio).
         * STOPPED_REST (descanso pendiente, sólo al restaurar) se enseña como pausa.
         */
        @Nullable
        public static Content forState(@NonNull State state, long remainingMillis, long nowWallMillis) {
            long remaining = Math.max(0L, remainingMillis);
            switch (state) {
                case RUNNING:
                    return new Content(Mode.FOCUS, nowWallMillis + remaining, remaining);
                case RUNNING_REST:
                    return new Content(Mode.REST, nowWallMillis + remaining, remaining);
                case PAUSED:
                case PAUSED_REST:
                case STOPPED_REST:
                    return new Content(Mode.PAUSED, 0L, remaining);
                case STOPPED:
                default:
                    return null;
            }
        }
    }

    @Nullable private static volatile Content latest;

    private FocusOngoingNotification() {}

    /** El ViewModel fija lo que hay que enseñar (null = sin sesión). */
    @MainThread
    public static void setLatest(@Nullable Content content) {
        latest = content;
    }

    /**
     * Vuelve a publicar el id del servicio con el último contenido (el servicio ya está en marcha).
     * Sin permiso de notificaciones no hace nada: el servicio sigue vivo igual.
     */
    @SuppressLint("MissingPermission") // comprobado en canPost()
    public static void repost(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (latest == null || !canPost(app)) {
            return;
        }
        NotificationManagerCompat.from(app).notify(ID, build(app));
    }

    /** Retira la notificación del servicio (por si un repost llegó después de pararlo). */
    public static void cancel(@NonNull Context context) {
        NotificationManagerCompat.from(context.getApplicationContext()).cancel(ID);
    }

    /** La notificación para el último contenido; sin contenido (reinicio STICKY), la genérica de siempre. */
    @NonNull
    public static Notification build(@NonNull Context context) {
        Context app = context.getApplicationContext();
        Content content = latest;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_bbetter)
                .setColor(ContextCompat.getColor(app, R.color.bb_primary))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(FocusPhaseEndNotifier.openFocus(app, NotificationIds.REQ_FOCUS_PHASE_ACTION));
        if (content == null) {
            return builder
                    .setContentTitle(app.getString(R.string.notif_foreground_service_title))
                    .setContentText(app.getString(R.string.notif_foreground_service_body))
                    .build();
        }
        switch (content.mode) {
            case FOCUS:
                builder.setContentTitle(app.getString(R.string.notif_focus_ongoing_title_focus))
                        .setContentText(app.getString(R.string.notif_focus_ongoing_body_running));
                break;
            case REST:
                builder.setContentTitle(app.getString(R.string.notif_focus_ongoing_title_rest))
                        .setContentText(app.getString(R.string.notif_focus_ongoing_body_running));
                break;
            case PAUSED:
            default:
                builder.setContentTitle(app.getString(R.string.notif_focus_ongoing_title_paused))
                        .setContentText(app.getString(R.string.notif_focus_ongoing_body_paused,
                                formatRemaining(content.remainingMillis)));
                break;
        }
        if (content.usesChronometer()) {
            builder.setShowWhen(true)
                    .setWhen(content.endsAtWallMillis)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true);
        } else {
            builder.setShowWhen(false).setUsesChronometer(false);
        }
        return builder.build();
    }

    /** {@code mm:ss}, o {@code h:mm:ss} desde una hora (sesiones ligadas a una tarea larga). Suelo, como la app. */
    @NonNull
    static String formatRemaining(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static boolean canPost(Context app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(app).areNotificationsEnabled();
        }
        return ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
