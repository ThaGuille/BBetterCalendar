package com.example.bbettercalendar.ui.screentime;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

// mejora-visual u9: datos planos de "Tiempo de pantalla" (sin Drawables ni Views; el icono lo resuelve
// el Fragment con PackageManager). Lo publica ScreenTimeViewModel desde el executor con postValue.
// Todos los minutos son medias por dia del periodo (7 o 30 dias), salvo donde se dice otra cosa.
public final class ScreenTimeState {

    /** Una fila de "Uso por app": media diaria del periodo y su regla (si esta seguida). */
    public static final class AppRow {
        public final String packageName;
        public final String label;
        public final float avgMinutes;
        public final int limitMinutes;        // 0 = sin limite activo
        public final boolean enforceAtLimit;
        public final boolean suggested;       // muy usada y sin limite

        public AppRow(String packageName, String label, float avgMinutes, int limitMinutes,
                      boolean enforceAtLimit, boolean suggested) {
            this.packageName = packageName;
            this.label = label;
            this.avgMinutes = avgMinutes;
            this.limitMinutes = limitMinutes;
            this.enforceAtLimit = enforceAtLimit;
            this.suggested = suggested;
        }

        public boolean hasLimit() {
            return limitMinutes > 0;
        }
    }

    /** Una app con limite que ya tiene suficientes dias antes y despues. */
    public static final class BeforeAfterRow {
        public final String label;
        public final float before;
        public final float after;

        public BeforeAfterRow(String label, float before, float after) {
            this.label = label;
            this.before = before;
            this.after = after;
        }
    }

    public final boolean usageAccess;
    public final boolean month;
    public final float avgMinutesPerDay;       // media diaria de todas las apps
    public final boolean hasDelta;             // hay periodo anterior con el que comparar
    public final int deltaPercent;             // negativo = menos movil que el periodo anterior
    public final List<AppRow> apps;
    public final LocalDate firstDay;           // primer dia de "Dia a dia"
    public final float[][] dailyValues;        // [dia][serie]: 3 apps principales + otras
    public final String[] dailySeriesNames;    // nombres de las 3 apps principales ("" si faltan)
    public final int peakWeekday;              // 0 = lunes ... 6 = domingo; -1 sin frase
    public final float peakWeekdayMinutes;
    public final List<BeforeAfterRow> beforeAfter;
    public final float recoveredMinutesPerDay;
    public final int allowedCount;             // apps siempre permitidas (las cablea la unidad u10)

    public ScreenTimeState(boolean usageAccess, boolean month, float avgMinutesPerDay,
                           boolean hasDelta, int deltaPercent, List<AppRow> apps,
                           LocalDate firstDay, float[][] dailyValues, String[] dailySeriesNames,
                           int peakWeekday, float peakWeekdayMinutes,
                           List<BeforeAfterRow> beforeAfter, float recoveredMinutesPerDay,
                           int allowedCount) {
        this.usageAccess = usageAccess;
        this.month = month;
        this.avgMinutesPerDay = avgMinutesPerDay;
        this.hasDelta = hasDelta;
        this.deltaPercent = deltaPercent;
        this.apps = apps;
        this.firstDay = firstDay;
        this.dailyValues = dailyValues;
        this.dailySeriesNames = dailySeriesNames;
        this.peakWeekday = peakWeekday;
        this.peakWeekdayMinutes = peakWeekdayMinutes;
        this.beforeAfter = beforeAfter;
        this.recoveredMinutesPerDay = recoveredMinutesPerDay;
        this.allowedCount = allowedCount;
    }

    /** Estado vacio (sin datos): cada tarjeta enseña su vacio. */
    public static ScreenTimeState empty(boolean usageAccess, boolean month, LocalDate firstDay) {
        return new ScreenTimeState(usageAccess, month, 0f, false, 0, Collections.emptyList(),
                firstDay, new float[0][4], new String[3], -1, 0f, Collections.emptyList(), 0f, 0);
    }
}
