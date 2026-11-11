package com.example.bbettercalendar.ui.progress;

import com.example.bbettercalendar.stats.ConsistencyCalculator;

import java.util.List;

// Datos ya calculados de las tarjetas rediseñadas de Progress (u17): tiles del periodo y gráfico
// "Past 12 weeks". Plano y sin tipos de Android ni de MPAndroidChart: lo construye el ViewModel en
// el executor y el Fragment sólo lo formatea.
public final class ProgressOverview {

    // --- tiles (html 13 "This week") ---
    public final long focusMinutes;         // Focus time del rango
    public final boolean hasFocusDelta;     // false si el periodo anterior no tiene base (0)
    public final double focusDeltaPercent;  // vs. periodo anterior (a la misma altura si el actual está en curso)
    public final int sessions;              // countFocusBetween
    public final double sessionsPerDay;     // sesiones / días transcurridos del rango
    public final int tasksDone;             // aproximado: CalendarEntry.isDone en el rango
    public final int tasksDoneDelta;        // vs. el periodo anterior completo

    // --- "Past 12 weeks" (siempre 12 semanas que terminan en el rango seleccionado) ---
    public final float[] trendCurrentHours;   // 12 valores, de la más antigua a la actual
    public final float[] trendPreviousHours;  // las 12 anteriores, alineadas por índice
    public final String[] trendLabels;        // etiqueta del eje X por índice ("" salvo en cambios de mes)
    public final long trendCurrentMinutes;
    public final long trendDeltaMinutes;      // current - previous

    // --- constancia (plan racha-semanal-y-constancia): pie de la tarjeta de tiles ---
    public final ConsistencyCalculator.Result consistency;
    public final boolean hasConsistencyDelta; // false si el periodo anterior no tiene días con foco
    public final int consistencyDelta;        // días activos vs. el periodo anterior (a la misma altura si está en curso)

    // --- u7: mini barras de la tarjeta resumen (una por dia; por mes en el ano) ---
    public final float[] sparkValues;         // minutos de foco
    public final String[] sparkLabels;
    public final int sparkHighlight;          // indice de hoy dentro del rango, o -1
    public final int avgSessionMinutes;       // minutos de foco / sesiones; 0 sin sesiones

    public ProgressOverview(long focusMinutes, boolean hasFocusDelta, double focusDeltaPercent,
                            int sessions, double sessionsPerDay, int tasksDone, int tasksDoneDelta,
                            float[] trendCurrentHours, float[] trendPreviousHours, String[] trendLabels,
                            long trendCurrentMinutes, long trendDeltaMinutes,
                            ConsistencyCalculator.Result consistency, boolean hasConsistencyDelta,
                            int consistencyDelta, float[] sparkValues, String[] sparkLabels,
                            int sparkHighlight, int avgSessionMinutes) {
        this.focusMinutes = focusMinutes;
        this.hasFocusDelta = hasFocusDelta;
        this.focusDeltaPercent = focusDeltaPercent;
        this.sessions = sessions;
        this.sessionsPerDay = sessionsPerDay;
        this.tasksDone = tasksDone;
        this.tasksDoneDelta = tasksDoneDelta;
        this.trendCurrentHours = trendCurrentHours;
        this.trendPreviousHours = trendPreviousHours;
        this.trendLabels = trendLabels;
        this.trendCurrentMinutes = trendCurrentMinutes;
        this.trendDeltaMinutes = trendDeltaMinutes;
        this.consistency = consistency;
        this.hasConsistencyDelta = hasConsistencyDelta;
        this.consistencyDelta = consistencyDelta;
        this.sparkValues = sparkValues;
        this.sparkLabels = sparkLabels;
        this.sparkHighlight = sparkHighlight;
        this.avgSessionMinutes = avgSessionMinutes;
    }

    // Tarjeta "Tus apps" (mejora-visual u7): media diaria de los ultimos 7 dias frente a los 7
    // anteriores y las 3 apps con mas uso. No depende del rango seleccionado.
    public static final class AppsCard {

        public static final class AppRow {
            public final String packageName;
            public final String label;
            public final float averageMinutes;  // media diaria de los ultimos 7 dias
            public final int limitMinutes;      // 0 = sin limite
            public final boolean enforce;       // candado: se cubre al llegar al limite
            public final boolean suggested;     // UsageInsights.suggested

            public AppRow(String packageName, String label, float averageMinutes, int limitMinutes,
                          boolean enforce, boolean suggested) {
                this.packageName = packageName;
                this.label = label;
                this.averageMinutes = averageMinutes;
                this.limitMinutes = limitMinutes;
                this.enforce = enforce;
                this.suggested = suggested;
            }
        }

        public final boolean locked;           // sin acceso a uso: no hay datos que mostrar
        public final float averageMinutes;     // suma de las medias diarias de todas las apps
        public final boolean hasDelta;         // hay media en los 7 dias anteriores
        public final int deltaPercent;         // negativo = menos uso
        public final List<AppRow> rows;        // hasta 3, mas usadas primero

        public AppsCard(boolean locked, float averageMinutes, boolean hasDelta, int deltaPercent,
                        List<AppRow> rows) {
            this.locked = locked;
            this.averageMinutes = averageMinutes;
            this.hasDelta = hasDelta;
            this.deltaPercent = deltaPercent;
            this.rows = rows;
        }
    }
}
