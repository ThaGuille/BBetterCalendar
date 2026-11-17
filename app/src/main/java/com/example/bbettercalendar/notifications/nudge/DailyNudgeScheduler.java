package com.example.bbettercalendar.notifications.nudge;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.notifications.NotificationIds;
import com.example.bbettercalendar.notifications.NotificationPrefs;

import java.time.ZoneId;

/**
 * Programa los avisos diarios (Tu día, racha en juego, planear mañana) y el resumen semanal
 * (plan sistema-notificaciones). Punto único de reprogramación: lo llaman el arranque del proceso
 * ({@code DBMigration.onCreate}), {@code BootReceiver} (arranque, actualización de la app, cambio
 * de hora o zona) y la pantalla Notificaciones tras cambiar una preferencia.
 *
 * <p>Una alarma INEXACTA por aviso activo ({@code setWindow}, ventana de ~10 min que nunca entra en
 * las horas tranquilas) a su próxima hora local; {@link DailyNudgeReceiver} programa la siguiente
 * en cada disparo. Desactivado → se cancela. Todo es idempotente: mismo request code
 * ({@link NotificationIds}) y {@code FLAG_UPDATE_CURRENT}, así que llamarlo N veces deja una alarma.
 *
 * <p>El reintento tras una concentración usa otra acción en el Intent, así que es otro
 * PendingIntent con el mismo request code y no pisa la alarma regular.
 */
public final class DailyNudgeScheduler {

    private static final String TAG = "DailyNudgeScheduler";

    static final String[] ALL_NUDGES = {
            NotificationPrefs.NUDGE_MORNING,
            NotificationPrefs.NUDGE_STREAK,
            NotificationPrefs.NUDGE_EVENING_PLAN,
            NotificationPrefs.NUDGE_WEEKLY
    };

    private DailyNudgeScheduler() {}

    /** (Re)programa todos los avisos diarios y el semanal según {@code NotificationPrefs}. Idempotente. */
    public static void rescheduleAll(@NonNull Context context) {
        for (String nudge : ALL_NUDGES) {
            schedule(context, nudge, true);
        }
    }

    /** Tras un disparo regular: la próxima ocurrencia estrictamente posterior a ahora. */
    static void scheduleNext(@NonNull Context context, @NonNull String nudge) {
        schedule(context, nudge, false);
    }

    /**
     * Programa (o cancela, si está apagado) la próxima ocurrencia regular de {@code nudge}. Con
     * {@code keepRecent}, una ocurrencia de hoy que aún estaba en su ventana y no se disparó no se
     * pierde ({@link NudgeSchedule#nextTriggerWithGrace}).
     */
    private static void schedule(@NonNull Context context, @NonNull String nudge, boolean keepRecent) {
        Context app = context.getApplicationContext();
        AlarmManager am = alarmManager(app);
        if (am == null) return;
        NotificationPrefs prefs = NotificationPrefs.from(app);
        int minutes = prefs.getNudgeMinutes(nudge);
        if (!prefs.isNudgeEnabled(nudge) || NudgePolicy.isQuiet(minutes)) {
            cancel(app, am, nudge, DailyNudgeReceiver.ACTION_FIRE);
            cancel(app, am, nudge, DailyNudgeReceiver.ACTION_FIRE_DEFERRED);
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        long now = System.currentTimeMillis();
        boolean weekly = NotificationPrefs.NUDGE_WEEKLY.equals(nudge);
        long at = keepRecent
                ? NudgeSchedule.nextTriggerWithGrace(now, zone, minutes, weekly, NudgeSchedule.WINDOW_MILLIS,
                        NudgeState.from(app).lastRegularFire(nudge))
                : NudgeSchedule.nextTrigger(now, zone, minutes, weekly);
        long window = NudgeSchedule.windowFor(at, zone, NudgeSchedule.WINDOW_MILLIS);
        setWindow(am, at, window, pendingIntent(app, nudge, DailyNudgeReceiver.ACTION_FIRE,
                PendingIntent.FLAG_UPDATE_CURRENT));
    }

    /** Reintento único tras una concentración ({@link NudgePolicy.Action#DEFER}). */
    static void scheduleDeferred(@NonNull Context context, @NonNull String nudge, long atMillis) {
        Context app = context.getApplicationContext();
        AlarmManager am = alarmManager(app);
        if (am == null) return;
        long window = NudgeSchedule.windowFor(atMillis, ZoneId.systemDefault(),
                NudgeSchedule.DEFER_WINDOW_MILLIS);
        setWindow(am, atMillis, window, pendingIntent(app, nudge, DailyNudgeReceiver.ACTION_FIRE_DEFERRED,
                PendingIntent.FLAG_UPDATE_CURRENT));
    }

    static int requestCodeFor(@NonNull String nudge) {
        switch (nudge) {
            case NotificationPrefs.NUDGE_MORNING: return NotificationIds.REQ_NUDGE_MORNING;
            case NotificationPrefs.NUDGE_STREAK: return NotificationIds.REQ_NUDGE_STREAK;
            case NotificationPrefs.NUDGE_EVENING_PLAN: return NotificationIds.REQ_NUDGE_EVENING_PLAN;
            default: return NotificationIds.REQ_WEEKLY_SUMMARY;
        }
    }

    private static void setWindow(AlarmManager am, long at, long window, PendingIntent pi) {
        try {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, window, pi);
        } catch (RuntimeException e) {
            // No debería pasar (setWindow no pide permiso de alarmas exactas); por si acaso, inexacta simple.
            Log.w(TAG, "setWindow failed, falling back to set()", e);
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static void cancel(Context app, AlarmManager am, String nudge, String action) {
        PendingIntent pi = pendingIntent(app, nudge, action, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    @Nullable
    private static PendingIntent pendingIntent(Context app, String nudge, String action, int extraFlags) {
        Intent intent = new Intent(app, DailyNudgeReceiver.class)
                .setAction(action)
                .putExtra(DailyNudgeReceiver.EXTRA_NUDGE, nudge);
        return PendingIntent.getBroadcast(app, requestCodeFor(nudge), intent,
                PendingIntent.FLAG_IMMUTABLE | extraFlags);
    }

    @Nullable
    private static AlarmManager alarmManager(Context app) {
        return (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
    }
}
