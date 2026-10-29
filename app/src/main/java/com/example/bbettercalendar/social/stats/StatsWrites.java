package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * El mapa que {@link StatsPublisher} escribe con un {@code set()} completo en {@code stats/{uid}} (F3-u4, 03 §5).
 * Lado de escritura; la lectura es {@link StatsMapping} (como {@code FriendRequestWrites} frente a
 * {@code FriendsMapping} en F2).
 *
 * <ul>
 *   <li>Exactamente las 10 claves del contrato, ni una más: nada de fallos, sesiones abandonadas, calendario,
 *       tareas, proyectos ni pantalla (P5). {@code updatedAt} va con {@code FieldValue.serverTimestamp()}: las
 *       reglas lo comparan con {@code request.time}.</li>
 *   <li>Decisión del usuario (r1): antes de publicar, <b>cada campo se recorta al rango que acepta
 *       {@code validStats()}</b>, así que una entrada fuera de rango (un reloj cambiado, 2000 minutos en un día,
 *       un objetivo 0 o 9) nunca produce un documento que las reglas rechacen. Es el único sitio que recorta.</li>
 * </ul>
 */
public final class StatsWrites {

    public static final String STATS = "stats";

    // Rangos de validStats() en firebase/firestore.rules (F3-u2).
    static final int MAX_MINUTES_TODAY = 1440;
    static final int MAX_DAYS_THIS_WEEK = 7;
    static final int MAX_MINUTES_WEEK_CAPPED = StatsComputer.DAY_CAP_MINUTES * MAX_DAYS_THIS_WEEK; // 1680
    static final int MIN_WEEKLY_GOAL = 1;
    static final int MAX_WEEKLY_GOAL = 7;

    private StatsWrites() {
    }

    @NonNull
    public static String statsPath(@NonNull String uid) {
        return STATS + "/" + uid;
    }

    /**
     * Los mismos valores, recortados al rango de las reglas: {@code minutesToday} 0..1440, {@code daysThisWeek}
     * 0..7, {@code minutesWeekCapped} 0..1680 y como mucho {@code 240 * daysThisWeek}, {@code weeklyGoal} 1..7,
     * {@code weekStreak} &gt;= 0, {@code bestWeekStreak} &gt;= {@code weekStreak} y {@code totalMinutes} &gt;= 0.
     */
    @NonNull
    public static FriendStats clamp(@NonNull FriendStats v) {
        int days = clamp(v.daysThisWeek, 0, MAX_DAYS_THIS_WEEK);
        int weekCap = Math.min(MAX_MINUTES_WEEK_CAPPED, StatsComputer.DAY_CAP_MINUTES * days);
        int streak = Math.max(0, v.weekStreak);
        return new FriendStats(v.dayKey, v.weekKey,
                clamp(v.minutesToday, 0, MAX_MINUTES_TODAY),
                clamp(v.minutesWeekCapped, 0, weekCap),
                days,
                clamp(v.weeklyGoal, MIN_WEEKLY_GOAL, MAX_WEEKLY_GOAL),
                streak,
                Math.max(streak, v.bestWeekStreak),
                Math.max(0, v.totalMinutes),
                v.updatedAtMillis);
    }

    /** El documento completo, ya recortado, con {@code updatedAt = serverTimestamp()}. */
    @NonNull
    public static Map<String, Object> toMap(@NonNull FriendStats values) {
        return toMap(values, FieldValue.serverTimestamp());
    }

    /** Igual que {@link #toMap(FriendStats)} con otro {@code updatedAt} (un centinela en los tests). */
    @NonNull
    static Map<String, Object> toMap(@NonNull FriendStats values, @Nullable Object updatedAt) {
        FriendStats v = clamp(values);
        Map<String, Object> m = new HashMap<>();
        m.put(StatsMapping.DAY_KEY, v.dayKey);
        m.put(StatsMapping.WEEK_KEY, v.weekKey);
        m.put(StatsMapping.MINUTES_TODAY, v.minutesToday);
        m.put(StatsMapping.MINUTES_WEEK_CAPPED, v.minutesWeekCapped);
        m.put(StatsMapping.DAYS_THIS_WEEK, v.daysThisWeek);
        m.put(StatsMapping.WEEKLY_GOAL, v.weeklyGoal);
        m.put(StatsMapping.WEEK_STREAK, v.weekStreak);
        m.put(StatsMapping.BEST_WEEK_STREAK, v.bestWeekStreak);
        m.put(StatsMapping.TOTAL_MINUTES, v.totalMinutes);
        m.put(StatsMapping.UPDATED_AT, updatedAt);
        return m;
    }

    /**
     * Huella de lo que se publica (valores ya recortados, sin {@code updatedAt}), empezando por {@code dayKey} y
     * {@code weekKey}. {@link StatsPublisher} la guarda por uid en {@code SocialPrefs} para no repetir una
     * publicación idéntica al arrancar.
     */
    @NonNull
    public static String fingerprint(@NonNull FriendStats values) {
        FriendStats v = clamp(values);
        return String.format(Locale.ROOT, "%s|%s|%d|%d|%d|%d|%d|%d|%d", v.dayKey, v.weekKey, v.minutesToday,
                v.minutesWeekCapped, v.daysThisWeek, v.weeklyGoal, v.weekStreak, v.bestWeekStreak,
                v.totalMinutes);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
