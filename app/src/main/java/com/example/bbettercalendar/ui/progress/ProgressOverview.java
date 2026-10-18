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

    public ProgressOverview(long focusMinutes, boolean hasFocusDelta, double focusDeltaPercent,
                            int sessions, double sessionsPerDay, int tasksDone, int tasksDoneDelta,
                            float[] trendCurrentHours, float[] trendPreviousHours, String[] trendLabels,
                            long trendCurrentMinutes, long trendDeltaMinutes,
                            ConsistencyCalculator.Result consistency, boolean hasConsistencyDelta,
                            int consistencyDelta) {
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
    }

    // Tarjeta "Screen time & blocking" (html 13): uso de HOY frente a la suma de límites diarios.
    // No depende del rango seleccionado.
    public static final class BlockingCard {

        public static final class AppChip {
            public final String packageName;
            public final String label;
            public final long millis;
            public final boolean blocked;    // límite superado con "enforce" activo
            public final boolean nearLimit;  // >= 75 % del límite (punto ámbar)

            public AppChip(String packageName, String label, long millis, boolean blocked,
                           boolean nearLimit) {
                this.packageName = packageName;
                this.label = label;
                this.millis = millis;
                this.blocked = blocked;
                this.nearLimit = nearLimit;
            }
        }

        public final boolean locked;          // sin acceso a uso: no hay datos que mostrar
        public final int trackedCount;
        public final long usedMillis;         // uso de hoy de las apps con límite (o de todas si no hay límites)
        public final int goalMinutes;         // suma de límites diarios; 0 = ninguno
        public final List<AppChip> chips;     // hasta 3, más usadas primero

        public BlockingCard(boolean locked, int trackedCount, long usedMillis, int goalMinutes,
                            List<AppChip> chips) {
            this.locked = locked;
            this.trackedCount = trackedCount;
            this.usedMillis = usedMillis;
            this.goalMinutes = goalMinutes;
            this.chips = chips;
        }

        public int percentOfGoal() {
            if (goalMinutes <= 0) return 0;
            return (int) Math.min(100L, usedMillis * 100L / (goalMinutes * 60_000L));
        }

        public long remainingMillis() {
            return Math.max(0L, goalMinutes * 60_000L - usedMillis);
        }
    }
}
