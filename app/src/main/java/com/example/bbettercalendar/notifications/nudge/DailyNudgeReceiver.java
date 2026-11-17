package com.example.bbettercalendar.notifications.nudge;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.example.bbettercalendar.BuildConfig;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.FocusRunningFlag;
import com.example.bbettercalendar.notifications.NotificationPrefs;
import com.example.bbettercalendar.notifications.PermissionHelper;
import com.example.bbettercalendar.stats.FocusEventDAO;

import java.time.ZoneId;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Disparo de un aviso diario o del resumen semanal ({@link DailyNudgeScheduler}). En el executor de
 * IO (goAsync): programa la siguiente ocurrencia, pasa {@link NudgePolicy}, y si toca publicar
 * construye el contenido desde Room ({@link NudgeContent}) y lo publica por {@link BBetterNotifier}.
 *
 * <p>Sólo cuenta como publicación (presupuesto, ignorados, variante) lo que de verdad sale: sin
 * permiso de notificaciones o con el canal bloqueado no se publica nada ni se registra nada.
 *
 * <p>Depuración (sólo {@code BuildConfig.DEBUG}): un broadcast explícito SIN acción dispara el aviso
 * ya, sin esperar a su hora y aunque esté apagado, pero con la política y el contenido intactos.
 * El manifest de debug exporta el receiver para que el shell de adb pueda alcanzarlo:
 * <pre>
 * adb shell am broadcast -f 32 -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.notifications.nudge.DailyNudgeReceiver --es nudge morning [--ez reset_state true]
 * </pre>
 * {@code reset_state} borra antes el estado de la política (presupuesto, ignorados, «ya salió hoy»).
 */
@AndroidEntryPoint
public class DailyNudgeReceiver extends BroadcastReceiver {

    private static final String TAG = "DailyNudgeReceiver";

    static final String ACTION_FIRE = "com.example.bbettercalendar.nudge.FIRE";
    static final String ACTION_FIRE_DEFERRED = "com.example.bbettercalendar.nudge.FIRE_DEFERRED";
    public static final String EXTRA_NUDGE = "nudge";
    /** Sólo depuración: borrar el estado de la política antes de evaluar. */
    static final String EXTRA_DEBUG_RESET_STATE = "reset_state";

    /** El pool de IO tiene 4 hilos: dos avisos a la vez no deben gastar el mismo hueco del presupuesto. */
    private static final Object LOCK = new Object();

    @Inject CalendarEntryDAO entryDao;
    @Inject FocusEventDAO focusEventDao;
    @Inject BBetterNotifier notifier;
    @Inject @IoExecutor ExecutorService io;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        final boolean regular = ACTION_FIRE.equals(action);
        final boolean deferred = ACTION_FIRE_DEFERRED.equals(action);
        final boolean debug = !regular && !deferred && action == null && BuildConfig.DEBUG;
        if (!regular && !deferred && !debug) return;
        final String nudge = intent.getStringExtra(EXTRA_NUDGE);
        if (!NudgePolicy.isKnown(nudge)) return;
        final boolean resetState = debug && intent.getBooleanExtra(EXTRA_DEBUG_RESET_STATE, false);

        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        io.execute(() -> {
            try {
                handle(app, nudge, regular, deferred, debug, resetState);
            } catch (Exception e) {
                Log.e(TAG, "nudge " + nudge + " failed", e);
            } finally {
                pending.finish();
            }
        });
    }

    private void handle(Context app, String nudge, boolean regular, boolean deferred, boolean debug,
                        boolean resetState) {
        NudgeState state = NudgeState.from(app);
        long now = System.currentTimeMillis();
        if (regular) {
            // Primero la cadena: un fallo más abajo no debe dejar el aviso sin próxima alarma.
            state.recordRegularFire(nudge, now);
            DailyNudgeScheduler.scheduleNext(app, nudge);
        }
        NotificationPrefs prefs = NotificationPrefs.from(app);
        if (!debug && !prefs.isNudgeEnabled(nudge)) return;

        synchronized (LOCK) {
            if (resetState) state.clearForDebug();
            ZoneId zone = ZoneId.systemDefault();
            NudgePolicy.Decision decision = NudgePolicy.decide(nudge, prefs.getNudgeMinutes(nudge),
                    deferred, now, zone, FocusRunningFlag.endsAtWallMillis(app),
                    state.snapshot(nudge, now, zone));
            Log.i(TAG, nudge + (deferred ? " (deferred)" : debug ? " (debug)" : "") + " -> " + decision);

            switch (decision.action) {
                case DEFER:
                    DailyNudgeScheduler.scheduleDeferred(app, nudge, decision.deferUntilMillis);
                    return;
                case SKIP:
                    return;
                case POST:
                default:
                    break;
            }

            String channel = NudgeContent.channelFor(nudge);
            if (!canPost(app, channel)) {
                Log.i(TAG, nudge + ": notifications blocked, nothing posted");
                return;
            }
            NudgeContent.Built built = NudgeContent.build(app, nudge, entryDao, focusEventDao, now, zone,
                    state.lastVariant(nudge), System.nanoTime());
            if (built == null) {
                Log.i(TAG, nudge + ": nothing to say, skipped");
                return;
            }
            notifier.notify(built.spec);
            state.recordPost(nudge, now, zone, decision.ignoredCount, built.variant);
        }
    }

    /** Permiso + notificaciones de la app activadas + canal no bloqueado (lo que BBetterNotifier descartaría en silencio). */
    private static boolean canPost(Context app, String channelId) {
        if (!PermissionHelper.notificationsGranted(app)) return false;
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            NotificationChannel ch = nm != null ? nm.getNotificationChannel(channelId) : null;
            if (ch != null && ch.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
        }
        return true;
    }
}
