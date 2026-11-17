package com.example.bbettercalendar.notifications.focus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.bbettercalendar.notifications.NotificationIds;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Settings;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

/**
 * Alarma exacta al final de la fase en curso del pomodoro (plan sistema-notificaciones, B.1).
 *
 * <p>El contador del {@code FocusSessionViewModel} vive en un Handler del hilo principal, que con la
 * pantalla apagada / Doze se retrasa (uptimeMillis no avanza dormido). Mientras una fase CORRE se
 * mantiene UNA alarma {@code RTC_WAKEUP} exacta (request code {@link NotificationIds#REQ_FOCUS_PHASE_END})
 * a su fin de reloj; cualquier otro estado la cancela. El único escritor es la sincronización por
 * transición del ViewModel (como {@code syncBlockState} / {@code syncForegroundService}), nunca el tick.
 *
 * <p>Al sonar, {@link FocusPhaseEndReceiver}: si hay una sesión viva en este proceso ({@link #liveSession()})
 * le pide que avance (la transición, el aviso y la alarma siguiente salen de ahí, sin duplicados);
 * si no (el proceso había muerto), publica el aviso con los extras de la alarma.
 *
 * <p>La decisión «qué programar para este estado» es {@link #plan} (Java puro, JUnit).
 */
public final class FocusPhaseAlarm {

    private static final String TAG = "FocusPhaseAlarm";

    static final String EXTRA_NOTICE = "bb_focus_phase_notice";
    static final String EXTRA_NEXT_MINUTES = "bb_focus_phase_next_minutes";

    private static final long MINUTE_MILLIS = 60_000L;

    /** Qué aviso toca cuando acaba la fase que corre. */
    public enum Notice {
        /** Acaba una concentración y empieza un descanso ({@link Plan#nextMinutes} = su duración). */
        FOCUS_DONE,
        /** Acaba un descanso (con o sin auto-ciclo, la siguiente ronda está por empezar). */
        BREAK_OVER,
        /** Último ciclo o descanso desactivado: se acabó la sesión. */
        SESSION_COMPLETE
    }

    /** Lo que hay que programar: nada ({@link #NONE}) o una alarma en {@link #triggerAtWallMillis}. */
    public static final class Plan {
        public static final Plan NONE = new Plan(0L, null, 0);

        public final long triggerAtWallMillis;
        @Nullable public final Notice notice;
        public final int nextMinutes;

        Plan(long triggerAtWallMillis, @Nullable Notice notice, int nextMinutes) {
            this.triggerAtWallMillis = triggerAtWallMillis;
            this.notice = notice;
            this.nextMinutes = nextMinutes;
        }

        public boolean isNone() {
            return notice == null;
        }
    }

    /** La sesión del proceso (el ViewModel), para que el receiver la haga avanzar. Hilo principal. */
    public interface LiveSession {
        void onPhaseAlarm();
    }

    @Nullable private static LiveSession liveSession;

    private FocusPhaseAlarm() {}

    // ================================================================ decisión pura

    /**
     * Qué programar para este estado. Replica las reglas de {@code FocusTimerStateMachine}
     * ({@code completeFocus} / {@code completeRest}) para saber qué viene después.
     *
     * @param remainingMillis lo que le queda a la cuenta atrás en curso
     * @param nowWallMillis   reloj de pared (System.currentTimeMillis)
     * @param cyclesCompleted ciclos cerrados hasta ahora (se incrementa al acabar un descanso)
     */
    @NonNull
    public static Plan plan(@NonNull State state, long remainingMillis, long nowWallMillis,
                            @NonNull Settings settings, int cyclesCompleted) {
        long endsAt = nowWallMillis + Math.max(0L, remainingMillis);
        if (state == State.RUNNING) {
            boolean restFollows = settings.restEnabled
                    && (cyclesCompleted < settings.cycles || settings.infiniteCycles);
            return restFollows
                    ? new Plan(endsAt, Notice.FOCUS_DONE, minutesOf(settings.restMillis))
                    : new Plan(endsAt, Notice.SESSION_COMPLETE, 0);
        }
        if (state == State.RUNNING_REST) {
            boolean lastRound = settings.autoCycle && !settings.infiniteCycles
                    && cyclesCompleted + 1 >= settings.cycles;
            return new Plan(endsAt, lastRound ? Notice.SESSION_COMPLETE : Notice.BREAK_OVER, 0);
        }
        return Plan.NONE;
    }

    /** Minutos redondeados, al menos 1 (un descanso de 30 s no dice «0 min»). */
    static int minutesOf(long millis) {
        return (int) Math.max(1L, Math.round(millis / (double) MINUTE_MILLIS));
    }

    // ================================================================ sesión viva

    @MainThread
    public static void attach(@NonNull LiveSession session) {
        liveSession = session;
    }

    @MainThread
    public static void detach(@NonNull LiveSession session) {
        if (liveSession == session) {
            liveSession = null;
        }
    }

    @MainThread
    @Nullable
    static LiveSession liveSession() {
        return liveSession;
    }

    /**
     * La app está a la vista (alguna Activity en STARTED), a nivel de proceso. Si el proceso arrancó
     * por la alarma no hay ninguna: CREATED, no primer plano.
     */
    @MainThread
    public static boolean isAppInForeground() {
        return ProcessLifecycleOwner.get().getLifecycle().getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }

    // ================================================================ AlarmManager

    /** Programa (o sustituye) la alarma de {@code plan}, o la cancela si es {@link Plan#NONE}. */
    public static void sync(@NonNull Context context, @NonNull Plan plan) {
        Context app = context.getApplicationContext();
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        if (plan.isNone()) {
            PendingIntent existing = pendingIntent(app, null, PendingIntent.FLAG_NO_CREATE);
            if (existing != null) {
                alarms.cancel(existing);
                existing.cancel();
            }
            return;
        }
        PendingIntent pi = pendingIntent(app, plan, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAtWallMillis, pi);
        } catch (SecurityException e) {
            // Sin permiso de alarmas exactas: inexacta, pero sigue saliendo (como AlarmReminderCore).
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e);
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAtWallMillis, pi);
        }
    }

    /** Los extras no cuentan para la identidad del PendingIntent: cancelar no necesita el plan. */
    private static PendingIntent pendingIntent(Context app, @Nullable Plan plan, int extraFlags) {
        Intent intent = new Intent(app, FocusPhaseEndReceiver.class);
        if (plan != null && plan.notice != null) {
            intent.putExtra(EXTRA_NOTICE, plan.notice.name());
            intent.putExtra(EXTRA_NEXT_MINUTES, plan.nextMinutes);
        }
        return PendingIntent.getBroadcast(app, NotificationIds.REQ_FOCUS_PHASE_END, intent,
                PendingIntent.FLAG_IMMUTABLE | extraFlags);
    }

    /** Lee el aviso de los extras de la alarma; null si faltan o no se reconocen. */
    @Nullable
    static Notice noticeFrom(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return Notice.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
