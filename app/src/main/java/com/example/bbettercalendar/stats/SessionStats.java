package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Estadísticas de sesiones (mejora-visual u5). Puro, sin Android ni I/O.
 *
 * <p>Semántica:
 * <ul>
 *   <li>Cuenta los eventos cuyo día civil LOCAL (del timestamp de fin) cae en [from, to].</li>
 *   <li>Histograma por cubos de duración de las sesiones completadas: 10 (&lt; 15 min), 15, 20, 25,
 *   30 (30-44) y 45+ ({@link #BUCKET_MIN}).</li>
 *   <li>Duración modal: la {@code durationMin} exacta más repetida (empate: la menor); 0 sin sesiones.</li>
 *   <li>Tasa de completadas = TYPE_FOCUS / (TYPE_FOCUS + TYPE_FAIL), en [0, 1]; no existe sin eventos
 *   ({@link Result#hasCompletionRate()}). El delta frente al periodo anterior es en PUNTOS
 *   porcentuales y solo existe si ambos periodos tienen tasa ({@link #hasDelta}).</li>
 *   <li>Por día ({@link Result#perDay}): sesiones, fallos y minutos de cada día con algún evento.</li>
 * </ul>
 */
public final class SessionStats {

    /** Mínimo (inclusive) de cada cubo; el último es "45 o más". */
    public static final int[] BUCKET_MIN = {10, 15, 20, 25, 30, 45};

    private SessionStats() {
    }

    /** Conteos de un día civil. */
    public static final class DayCount {
        public int focus;
        public int fails;
        public int minutes;
    }

    /** Resultado inmutable de {@link #compute}. */
    public static final class Result {
        /** Sesiones completadas por cubo (longitud {@link #BUCKET_MIN}.length). */
        @NonNull public final int[] buckets;
        public final int focusCount;
        public final int failCount;
        public final int totalMinutes;
        /** 0 si no hay sesiones completadas. */
        public final int modalDurationMin;
        @NonNull public final Map<LocalDate, DayCount> perDay;

        Result(int[] buckets, int focusCount, int failCount, int totalMinutes, int modalDurationMin,
               Map<LocalDate, DayCount> perDay) {
            this.buckets = buckets;
            this.focusCount = focusCount;
            this.failCount = failCount;
            this.totalMinutes = totalMinutes;
            this.modalDurationMin = modalDurationMin;
            this.perDay = Collections.unmodifiableMap(perDay);
        }

        public boolean hasCompletionRate() {
            return focusCount + failCount > 0;
        }

        /** Completadas / (completadas + fallos) en [0, 1]; 0 si {@link #hasCompletionRate()} es false. */
        public double completionRate() {
            int all = focusCount + failCount;
            return all == 0 ? 0.0 : (double) focusCount / all;
        }
    }

    /** ¿Hay base para comparar las tasas de los dos periodos? */
    public static boolean hasDelta(@NonNull Result current, @NonNull Result previous) {
        return current.hasCompletionRate() && previous.hasCompletionRate();
    }

    /** current - previous, en puntos porcentuales (p. ej. +3.0). 0 si {@link #hasDelta} es false. */
    public static double deltaPoints(@NonNull Result current, @NonNull Result previous) {
        return hasDelta(current, previous)
                ? (current.completionRate() - previous.completionRate()) * 100.0 : 0.0;
    }

    /** Rango de igual longitud inmediatamente anterior a [from, to]: {inicio, fin}. */
    @NonNull
    public static LocalDate[] previousRange(@NonNull LocalDate from, @NonNull LocalDate to) {
        long n = to.toEpochDay() - from.toEpochDay() + 1;
        return new LocalDate[]{from.minusDays(n), from.minusDays(1)};
    }

    /** Índice del cubo de una duración en minutos (menos de 15 -> cubo 0). */
    public static int bucketIndex(int durationMin) {
        int idx = 0;
        for (int i = 0; i < BUCKET_MIN.length; i++) {
            if (durationMin >= BUCKET_MIN[i]) {
                idx = i;
            }
        }
        return idx;
    }

    @NonNull
    public static Result compute(@NonNull List<FocusEvent> events, @NonNull LocalDate from,
                                 @NonNull LocalDate to, @NonNull ZoneId zone) {
        int[] buckets = new int[BUCKET_MIN.length];
        Map<Integer, Integer> byDuration = new HashMap<>();
        Map<LocalDate, DayCount> perDay = new TreeMap<>();
        int focus = 0;
        int fails = 0;
        int minutes = 0;
        for (FocusEvent e : events) {
            if (e == null || (e.type != FocusEvent.TYPE_FOCUS && e.type != FocusEvent.TYPE_FAIL)) {
                continue;
            }
            LocalDate day = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate();
            if (day.isBefore(from) || day.isAfter(to)) {
                continue;
            }
            DayCount dc = perDay.get(day);
            if (dc == null) {
                dc = new DayCount();
                perDay.put(day, dc);
            }
            if (e.type == FocusEvent.TYPE_FOCUS) {
                focus++;
                dc.focus++;
                dc.minutes += e.durationMin;
                minutes += e.durationMin;
                buckets[bucketIndex(e.durationMin)]++;
                Integer n = byDuration.get(e.durationMin);
                byDuration.put(e.durationMin, n == null ? 1 : n + 1);
            } else {
                fails++;
                dc.fails++;
            }
        }
        int modal = 0;
        int modalCount = 0;
        for (Map.Entry<Integer, Integer> en : byDuration.entrySet()) {
            int d = en.getKey();
            int c = en.getValue();
            if (c > modalCount || (c == modalCount && d < modal)) {
                modal = d;
                modalCount = c;
            }
        }
        return new Result(buckets, focus, fails, minutes, modal, perDay);
    }
}
