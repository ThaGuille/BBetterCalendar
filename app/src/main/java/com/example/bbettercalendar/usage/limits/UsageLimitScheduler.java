package com.example.bbettercalendar.usage.limits;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

// Arma/desarma el chequeo periódico de límites: una única alarma global auto-reprogramable, igual
// patrón que EventReminderScheduler (exact-with-inexact-fallback). Sólo se arma mientras quede al
// menos una app con límite que no haya disparado ya su notificación de "límite alcanzado" hoy
// (ver design.md §1) — así el coste de batería queda acotado a periodos de uso activo.
@Singleton
public class UsageLimitScheduler {

    private static final String TAG = "UsageLimitScheduler";
    private static final long CHECK_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final int REQUEST_CODE = 1;

    private static final String PREFS_NAME = "usage_limit_scheduler";
    private static final String KEY_NEXT_TRIGGER_AT = "next_trigger_at";

    private final Context appContext;
    private final AlarmManager alarmManager;
    private final AppRuleDAO appRuleDao;
    private final WarnedTodayStore warnedStore;
    private final SharedPreferences prefs;

    @Inject
    public UsageLimitScheduler(@ApplicationContext Context context, AlarmManager alarmManager,
                                AppRuleDAO appRuleDao, WarnedTodayStore warnedStore) {
        this.appContext = context;
        this.alarmManager = alarmManager;
        this.appRuleDao = appRuleDao;
        this.warnedStore = warnedStore;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Reevalúa si hace falta seguir armado. Llamar tras cualquier cambio de límites, en el
    // arranque de la app y tras el boot; el propio receiver la vuelve a llamar en cada disparo.
    // Idempotente vía KEY_NEXT_TRIGGER_AT (no vía PendingIntent.FLAG_NO_CREATE: el registro de un
    // PendingIntent sobrevive a que la alarma dispare, así que esa comprobación daría siempre
    // "ya armado" tras el primer tick y dejaría de reprogramar para siempre). Sólo reprograma si no
    // hay un trigger futuro ya guardado — evita que llamadas frecuentes (p.ej.
    // ProgressFragment.onResume()) pospongan el chequeo indefinidamente y salten la ventana de aviso.
    public void arm() {
        List<AppRule> limited = appRuleDao.getLimited();
        if (limited.isEmpty()) {
            disarm();
            return;
        }
        if (prefs.getLong(KEY_NEXT_TRIGGER_AT, 0L) > System.currentTimeMillis()) return;

        // Si toda app con límite ya disparó "reached" hoy, no hace falta seguir sondeando cada 5
        // min — pero tampoco desarmar del todo: sin un disparo futuro, el monitor de mañana sólo
        // se reactivaría si el usuario reabre Progress o reinicia el dispositivo. Se deja una
        // única alarma para la próxima medianoche local que reevalúe el día nuevo.
        if (allReachedToday(limited)) {
            scheduleAt(nextLocalMidnightMillis());
        } else {
            scheduleAt(System.currentTimeMillis() + CHECK_INTERVAL_MILLIS);
        }
    }

    public void disarm() {
        PendingIntent pi = buildPendingIntent(PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            alarmManager.cancel(pi);
            pi.cancel();
        }
        prefs.edit().remove(KEY_NEXT_TRIGGER_AT).apply();
    }

    private boolean allReachedToday(List<AppRule> limited) {
        for (AppRule rule : limited) {
            if (!warnedStore.hasReached(rule.packageName)) return false;
        }
        return true;
    }

    private long nextLocalMidnightMillis() {
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    private void scheduleAt(long triggerAt) {
        PendingIntent pi = buildPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            // Dispositivo denegó alarmas exactas — caer a inexacta para que el chequeo igual corra.
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
        prefs.edit().putLong(KEY_NEXT_TRIGGER_AT, triggerAt).apply();
    }

    private PendingIntent buildPendingIntent(int extraFlags) {
        Intent intent = new Intent(appContext, UsageLimitReceiver.class);
        int flags = PendingIntent.FLAG_IMMUTABLE | extraFlags;
        return PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, flags);
    }
}
