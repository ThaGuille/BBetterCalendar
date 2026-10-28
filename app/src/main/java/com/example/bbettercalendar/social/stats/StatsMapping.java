package com.example.bbettercalendar.social.stats;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * De {@code stats/{uid}} (mapa de Firestore) a {@link FriendStats} (F3-u3). Puro. Devuelve null para un mapa
 * null o malformado (falta una clave, tipo equivocado o valor fuera del rango de las reglas): un documento
 * así cuenta como «sin stats». Firestore entrega los enteros como Long; también se acepta Integer. Las
 * claves y rangos son los de {@code validStats()} en {@code firestore.rules}.
 */
public final class StatsMapping {

    public static final String DAY_KEY = "dayKey";
    public static final String WEEK_KEY = "weekKey";
    public static final String MINUTES_TODAY = "minutesToday";
    public static final String MINUTES_WEEK_CAPPED = "minutesWeekCapped";
    public static final String DAYS_THIS_WEEK = "daysThisWeek";
    public static final String WEEKLY_GOAL = "weeklyGoal";
    public static final String WEEK_STREAK = "weekStreak";
    public static final String BEST_WEEK_STREAK = "bestWeekStreak";
    public static final String TOTAL_MINUTES = "totalMinutes";
    public static final String UPDATED_AT = "updatedAt";

    private static final Pattern DAY_KEY_RE = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    private static final Pattern WEEK_KEY_RE = Pattern.compile("^[0-9]{4}-W[0-9]{2}$");

    private StatsMapping() {
    }

    @Nullable
    public static FriendStats fromMap(@Nullable Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        Object day = m.get(DAY_KEY);
        Object week = m.get(WEEK_KEY);
        if (!(day instanceof String) || !DAY_KEY_RE.matcher((String) day).matches()
                || !(week instanceof String) || !WEEK_KEY_RE.matcher((String) week).matches()) {
            return null;
        }
        Integer today = integer(m.get(MINUTES_TODAY));
        Integer weekMin = integer(m.get(MINUTES_WEEK_CAPPED));
        Integer days = integer(m.get(DAYS_THIS_WEEK));
        Integer goal = integer(m.get(WEEKLY_GOAL));
        Integer streak = integer(m.get(WEEK_STREAK));
        Integer best = integer(m.get(BEST_WEEK_STREAK));
        Integer total = integer(m.get(TOTAL_MINUTES));
        if (today == null || weekMin == null || days == null || goal == null || streak == null
                || best == null || total == null || !m.containsKey(UPDATED_AT)) {
            return null;
        }
        if (today < 0 || today > 1440 || days < 0 || days > 7 || weekMin < 0 || weekMin > 1680
                || weekMin > 240 * days || goal < 1 || goal > 7 || streak < 0 || best < streak
                || total < 0) {
            return null;
        }
        Object at = m.get(UPDATED_AT);
        Long millis = null;
        if (at != null) {
            millis = millis(at);
            if (millis == null) {
                return null;
            }
        }
        return new FriendStats((String) day, (String) week, today, weekMin, days, goal, streak, best,
                total, millis);
    }

    /** Long, Integer o Short; nada de Double, String ni null. */
    @Nullable
    private static Integer integer(@Nullable Object o) {
        if (o instanceof Long) {
            long v = (Long) o;
            return v < Integer.MIN_VALUE || v > Integer.MAX_VALUE ? null : (int) v;
        }
        if (o instanceof Integer || o instanceof Short) {
            return ((Number) o).intValue();
        }
        return null;
    }

    /** Timestamp de Firestore, Date o epoch en milisegundos; null si es de otro tipo. */
    @Nullable
    private static Long millis(Object o) {
        if (o instanceof Timestamp) {
            return ((Timestamp) o).toDate().getTime();
        }
        if (o instanceof Date) {
            return ((Date) o).getTime();
        }
        if (o instanceof Long) {
            return (Long) o;
        }
        return null;
    }
}
