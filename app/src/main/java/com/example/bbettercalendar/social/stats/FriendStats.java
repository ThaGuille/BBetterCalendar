package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lo que dice {@code stats/{uid}} (03 §5): los 10 campos del contrato, inmutable. {@code updatedAtMillis} es
 * la hora del servidor en milisegundos de época, o null mientras la escritura sigue pendiente. Nada de fallos,
 * calendario, tareas ni pantalla (P5).
 */
public final class FriendStats {
    @NonNull
    public final String dayKey;
    @NonNull
    public final String weekKey;
    public final int minutesToday;
    public final int minutesWeekCapped;
    public final int daysThisWeek;
    public final int weeklyGoal;
    public final int weekStreak;
    public final int bestWeekStreak;
    public final int totalMinutes;
    @Nullable
    public final Long updatedAtMillis;

    public FriendStats(@NonNull String dayKey, @NonNull String weekKey, int minutesToday,
                       int minutesWeekCapped, int daysThisWeek, int weeklyGoal, int weekStreak,
                       int bestWeekStreak, int totalMinutes, @Nullable Long updatedAtMillis) {
        this.dayKey = dayKey;
        this.weekKey = weekKey;
        this.minutesToday = minutesToday;
        this.minutesWeekCapped = minutesWeekCapped;
        this.daysThisWeek = daysThisWeek;
        this.weeklyGoal = weeklyGoal;
        this.weekStreak = weekStreak;
        this.bestWeekStreak = bestWeekStreak;
        this.totalMinutes = totalMinutes;
        this.updatedAtMillis = updatedAtMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FriendStats)) {
            return false;
        }
        FriendStats s = (FriendStats) o;
        return dayKey.equals(s.dayKey) && weekKey.equals(s.weekKey)
                && minutesToday == s.minutesToday && minutesWeekCapped == s.minutesWeekCapped
                && daysThisWeek == s.daysThisWeek && weeklyGoal == s.weeklyGoal
                && weekStreak == s.weekStreak && bestWeekStreak == s.bestWeekStreak
                && totalMinutes == s.totalMinutes
                && (updatedAtMillis == null ? s.updatedAtMillis == null
                : updatedAtMillis.equals(s.updatedAtMillis));
    }

    @Override
    public int hashCode() {
        int h = dayKey.hashCode();
        h = 31 * h + weekKey.hashCode();
        h = 31 * h + minutesToday;
        h = 31 * h + minutesWeekCapped;
        h = 31 * h + daysThisWeek;
        h = 31 * h + weeklyGoal;
        h = 31 * h + weekStreak;
        h = 31 * h + bestWeekStreak;
        h = 31 * h + totalMinutes;
        h = 31 * h + (updatedAtMillis == null ? 0 : updatedAtMillis.hashCode());
        return h;
    }

    @NonNull
    @Override
    public String toString() {
        return "FriendStats{" + dayKey + ", " + weekKey + ", today=" + minutesToday + ", week="
                + minutesWeekCapped + ", days=" + daysThisWeek + "/" + weeklyGoal + ", streak="
                + weekStreak + ", best=" + bestWeekStreak + ", total=" + totalMinutes + "}";
    }
}
