package com.example.bbettercalendar.notifications.nudge;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Estado de {@link NudgePolicy} en SharedPreferences propias ({@code bb_nudge_state}): cuántos
 * avisos diarios salieron hoy, cuándo salió cada aviso, los ignorados seguidos, la última variante
 * de copy y la última apertura de la app. Nada de Room: son contadores sueltos (regla #6).
 *
 * <p>Los escritores del receiver usan {@code commit()} (corren en el executor de IO, dentro de un
 * {@code goAsync}: el proceso puede morir justo después). {@link #markAppOpened} usa
 * {@code apply()} porque lo llama el hilo principal.
 */
public final class NudgeState {

    private static final String FILE = "bb_nudge_state";
    private static final String KEY_APP_OPEN = "last_app_open";
    private static final String KEY_POSTS_DAY = "daily_posts_epoch_day";
    private static final String KEY_POSTS_COUNT = "daily_posts_count";
    private static final String PREFIX_LAST_POSTED = "last_posted_";
    private static final String PREFIX_IGNORED = "ignored_before_last_";
    private static final String PREFIX_LAST_VARIANT = "last_variant_";
    private static final String PREFIX_LAST_FIRE = "last_regular_fire_";

    private final SharedPreferences prefs;

    private NudgeState(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public static NudgeState from(@NonNull Context context) {
        return new NudgeState(prefs(context));
    }

    /** «La app se abrió»: corta los ignorados (y termina una retirada). Desde {@code MainActivity.onStart}. */
    public static void markAppOpened(@NonNull Context context) {
        prefs(context).edit().putLong(KEY_APP_OPEN, System.currentTimeMillis()).apply();
    }

    /** Lo que necesita la política para decidir sobre {@code nudge} hoy. */
    @NonNull
    public NudgePolicy.State snapshot(@NonNull String nudge, long nowMillis, @NonNull ZoneId zone) {
        return new NudgePolicy.State(
                dailyPostsToday(nowMillis, zone),
                prefs.getLong(PREFIX_LAST_POSTED + nudge, 0L),
                prefs.getInt(PREFIX_IGNORED + nudge, 0),
                prefs.getLong(KEY_APP_OPEN, 0L));
    }

    public int lastVariant(@NonNull String nudge) {
        return prefs.getInt(PREFIX_LAST_VARIANT + nudge, -1);
    }

    /**
     * Registra una publicación que de verdad salió. {@code ignoredCount} es el de la decisión: los
     * ignorados seguidos que había antes de esta publicación.
     */
    public void recordPost(@NonNull String nudge, long nowMillis, @NonNull ZoneId zone,
                           int ignoredCount, int variant) {
        SharedPreferences.Editor e = prefs.edit()
                .putLong(PREFIX_LAST_POSTED + nudge, nowMillis)
                .putInt(PREFIX_IGNORED + nudge, ignoredCount)
                .putInt(PREFIX_LAST_VARIANT + nudge, variant);
        if (NudgePolicy.isDaily(nudge)) {
            long today = epochDay(nowMillis, zone);
            e.putLong(KEY_POSTS_DAY, today)
                    .putInt(KEY_POSTS_COUNT, dailyPostsToday(nowMillis, zone) + 1);
        }
        e.commit();
    }

    /** Disparo regular (no aplazado ni de depuración) de {@code nudge}, haya publicado o no. */
    public void recordRegularFire(@NonNull String nudge, long nowMillis) {
        prefs.edit().putLong(PREFIX_LAST_FIRE + nudge, nowMillis).commit();
    }

    public long lastRegularFire(@NonNull String nudge) {
        return prefs.getLong(PREFIX_LAST_FIRE + nudge, 0L);
    }

    /** Sólo depuración: vuelve al estado de una instalación nueva (salvo la última apertura). */
    public void clearForDebug() {
        long appOpen = prefs.getLong(KEY_APP_OPEN, 0L);
        prefs.edit().clear().putLong(KEY_APP_OPEN, appOpen).commit();
    }

    private int dailyPostsToday(long nowMillis, ZoneId zone) {
        if (prefs.getLong(KEY_POSTS_DAY, Long.MIN_VALUE) != epochDay(nowMillis, zone)) return 0;
        return prefs.getInt(KEY_POSTS_COUNT, 0);
    }

    private static long epochDay(long millis, ZoneId zone) {
        LocalDate d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
        return d.toEpochDay();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
