package com.example.bbettercalendar.ui.screentime;

import java.util.List;

// u19: datos planos de "Screen time & blocking" (sin Drawables ni Views; el icono lo resuelve el
// Fragment con PackageManager). Lo publica ScreenTimeViewModel desde el executor con postValue.
public final class ScreenTimeState {

    /** Una app seguida con su uso de HOY y su regla. */
    public static final class AppRow {
        public final String packageName;
        public final String label;
        public final long todayMillis;
        public final int dailyLimitMinutes;   // 0 = sin limite
        public final boolean enforceAtLimit;

        public AppRow(String packageName, String label, long todayMillis,
                      int dailyLimitMinutes, boolean enforceAtLimit) {
            this.packageName = packageName;
            this.label = label;
            this.todayMillis = todayMillis;
            this.dailyLimitMinutes = dailyLimitMinutes;
            this.enforceAtLimit = enforceAtLimit;
        }

        public boolean hasLimit() {
            return dailyLimitMinutes > 0;
        }

        public long limitMillis() {
            return dailyLimitMinutes * 60_000L;
        }

        /** 0..100 del limite consumido (0 sin limite). */
        public int percentOfLimit() {
            if (!hasLimit()) return 0;
            return (int) Math.min(100L, todayMillis * 100L / limitMillis());
        }

        public boolean isOverLimit() {
            return hasLimit() && todayMillis >= limitMillis();
        }

        /** >= 75 % del limite y todavia por debajo. */
        public boolean isNearLimit() {
            return hasLimit() && !isOverLimit() && todayMillis * 100L >= limitMillis() * 75L;
        }
    }

    public final boolean usageAccess;
    public final long trackedMillis;          // uso de hoy de las apps seguidas
    public final boolean hasAverage;          // false = sin historial previo para comparar
    public final int deltaPercentVsAverage;   // hoy frente a la media diaria de los 7 dias previos
    public final int totalLimitMinutes;       // suma de limites de las apps seguidas con limite
    public final long limitedMillis;          // uso de hoy de esas apps
    public final long[] hourMillis;           // 24 franjas, uso de hoy
    public final boolean[] focusHours;        // 24 franjas con minutos de concentracion
    public final List<AppRow> apps;
    public final int enforcedCount;           // apps con candado y limite ("N apps protected")

    public ScreenTimeState(boolean usageAccess, long trackedMillis, boolean hasAverage,
                           int deltaPercentVsAverage, int totalLimitMinutes, long limitedMillis,
                           long[] hourMillis, boolean[] focusHours, List<AppRow> apps,
                           int enforcedCount) {
        this.usageAccess = usageAccess;
        this.trackedMillis = trackedMillis;
        this.hasAverage = hasAverage;
        this.deltaPercentVsAverage = deltaPercentVsAverage;
        this.totalLimitMinutes = totalLimitMinutes;
        this.limitedMillis = limitedMillis;
        this.hourMillis = hourMillis;
        this.focusHours = focusHours;
        this.apps = apps;
        this.enforcedCount = enforcedCount;
    }

    /** % del limite diario total consumido (0..100); 0 si no hay limites. */
    public int limitPercent() {
        if (totalLimitMinutes <= 0) return 0;
        return (int) Math.min(100L, limitedMillis * 100L / (totalLimitMinutes * 60_000L));
    }
}
