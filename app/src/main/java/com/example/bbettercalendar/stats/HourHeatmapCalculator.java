package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mapa de calor "cuándo me concentro" (mejora-visual u5). Puro, sin Android ni I/O: convierte las
 * sesiones completadas ({@link FocusEvent#TYPE_FOCUS}) en una matriz día-de-semana x hora local con
 * los MINUTOS MEDIOS por ocurrencia de ese día de la semana en el rango.
 *
 * <p>Semántica:
 * <ul>
 *   <li>El timestamp de un evento es el FIN de la sesión; el inicio es {@code timestamp - durationMin}.
 *   Los minutos se reparten por el solape real con cada hora local, así que una sesión de 23:30 a
 *   00:30 cuenta 30 min en la hora 23 de un día y 30 min en la hora 0 del siguiente (medianoche), y
 *   en un cambio de hora (DST) la hora que no existe queda a 0 (se itera sobre instantes, no sobre
 *   relojes de pared).</li>
 *   <li>Solo cuenta el trozo cuyo día civil local cae en [from, to] (inclusive).</li>
 *   <li>Media = minutos acumulados de la celda / nº de veces que ese día de la semana aparece en el
 *   rango (un martes sin sesiones también cuenta como ocurrencia).</li>
 *   <li>Filas 0..6 = lunes..domingo (ISO); columnas 0..23.</li>
 * </ul>
 */
public final class HourHeatmapCalculator {

    /** Ancho en horas de la franja de {@link Result#bestSlot()} ("de 9 a 11"). */
    public static final int SLOT_HOURS = 2;

    private HourHeatmapCalculator() {
    }

    /** Franja ganadora: un día de la semana y {@link #SLOT_HOURS} horas seguidas. */
    public static final class Slot {
        @NonNull public final DayOfWeek dayOfWeek;
        public final int startHour;
        /** Hora de fin (exclusiva): startHour + SLOT_HOURS. */
        public final int endHour;
        /** Suma de los minutos medios de las horas de la franja. */
        public final double averageMinutes;

        Slot(@NonNull DayOfWeek dayOfWeek, int startHour, double averageMinutes) {
            this.dayOfWeek = dayOfWeek;
            this.startHour = startHour;
            this.endHour = startHour + SLOT_HOURS;
            this.averageMinutes = averageMinutes;
        }
    }

    /** Resultado inmutable de {@link #compute}. Los arrays no se copian: no los modifiques. */
    public static final class Result {
        /** [0..6 lunes..domingo][0..23] minutos medios por ocurrencia del día. */
        @NonNull public final double[][] averageMinutes;
        /** [0..23] minutos TOTALES (sin promediar) por hora local, sumando todos los días. */
        @NonNull public final double[] totalMinutesByHour;
        /** Días civiles distintos del rango con >= 1 sesión completada. */
        public final int activeDays;
        public final double totalMinutes;

        Result(double[][] averageMinutes, double[] totalMinutesByHour, int activeDays, double totalMinutes) {
            this.averageMinutes = averageMinutes;
            this.totalMinutesByHour = totalMinutesByHour;
            this.activeDays = activeDays;
            this.totalMinutes = totalMinutes;
        }

        public boolean isEmpty() {
            return totalMinutes <= 0;
        }

        /** Hora local con más minutos en total, o -1 si no hay datos (empate: la más temprana). */
        public int peakHour() {
            int best = -1;
            double max = 0;
            for (int h = 0; h < 24; h++) {
                if (totalMinutesByHour[h] > max) {
                    max = totalMinutesByHour[h];
                    best = h;
                }
            }
            return best;
        }

        /**
         * La mejor franja de {@link #SLOT_HOURS} horas seguidas dentro de un mismo día de la semana,
         * o null si no hay datos. Empate: el día más temprano; a igual suma, la franja cuya primera hora tiene más minutos; luego la más temprana.
         */
        @Nullable
        public Slot bestSlot() {
            Slot best = null;
            double bestFirst = 0;
            for (int d = 0; d < 7; d++) {
                for (int h = 0; h + SLOT_HOURS <= 24; h++) {
                    double sum = 0;
                    for (int k = 0; k < SLOT_HOURS; k++) {
                        sum += averageMinutes[d][h + k];
                    }
                    double first = averageMinutes[d][h];
                    // Empate de suma: gana la franja que ya empieza con minutos (no arrancar en una hora vacía).
                    if (sum > 0 && (best == null || sum > best.averageMinutes
                            || (sum == best.averageMinutes && first > bestFirst))) {
                        best = new Slot(DayOfWeek.of(d + 1), h, sum);
                        bestFirst = first;
                    }
                }
            }
            return best;
        }
    }

    /**
     * @param events eventos de foco (se ignoran los que no son TYPE_FOCUS, null y los de duración &lt;= 0)
     * @param from   primer día civil local contado (inclusive)
     * @param to     último día civil local contado (inclusive)
     * @param zone   zona horaria local del usuario
     */
    @NonNull
    public static Result compute(@NonNull List<FocusEvent> events, @NonNull LocalDate from,
                                 @NonNull LocalDate to, @NonNull ZoneId zone) {
        double[][] totals = new double[7][24];
        double[] byHour = new double[24];
        Set<LocalDate> active = new HashSet<>();
        double sum = 0;
        for (FocusEvent e : events) {
            if (e == null || e.type != FocusEvent.TYPE_FOCUS || e.durationMin <= 0) {
                continue;
            }
            long endMs = e.timestamp;
            long cursor = endMs - e.durationMin * 60_000L;
            while (cursor < endMs) {
                ZonedDateTime z = Instant.ofEpochMilli(cursor).atZone(zone);
                long nextHourMs = z.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli();
                long segEnd = Math.min(endMs, nextHourMs);
                LocalDate day = z.toLocalDate();
                if (!day.isBefore(from) && !day.isAfter(to)) {
                    double min = (segEnd - cursor) / 60_000.0;
                    totals[z.getDayOfWeek().getValue() - 1][z.getHour()] += min;
                    byHour[z.getHour()] += min;
                    sum += min;
                    active.add(day);
                }
                cursor = segEnd;
            }
        }
        double[][] avg = new double[7][24];
        int[] occurrences = occurrencesPerWeekday(from, to);
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                avg[d][h] = occurrences[d] == 0 ? 0 : totals[d][h] / occurrences[d];
            }
        }
        return new Result(avg, byHour, active.size(), sum);
    }

    /** Cuántas veces aparece cada día de la semana (0 = lunes) en [from, to]. */
    @NonNull
    static int[] occurrencesPerWeekday(@NonNull LocalDate from, @NonNull LocalDate to) {
        int[] out = new int[7];
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            out[d.getDayOfWeek().getValue() - 1]++;
        }
        return out;
    }
}
