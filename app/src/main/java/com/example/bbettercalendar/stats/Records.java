package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Récords personales (mejora-visual u5). Puro, sin Android ni I/O. Sobre TODO el histórico de
 * sesiones completadas ({@link FocusEvent#TYPE_FOCUS}) hasta {@code today}.
 *
 * <p>Semántica:
 * <ul>
 *   <li><b>Mejor día</b>: el día civil local con más minutos (empate: el más reciente).</li>
 *   <li><b>Sesión más larga</b>: la {@code durationMin} mayor y el día en que acabó (empate: la más reciente).</li>
 *   <li><b>Hora punta</b>: la hora local con más minutos acumulados ({@link HourHeatmapCalculator}).</li>
 *   <li><b>Semanas por encima de la media</b>: semanas (lunes-domingo) seguidas cuyo total supera la
 *   media de TODAS las semanas anteriores (las semanas vacías cuentan como 0, desde la primera
 *   con datos). Como en la racha semanal, la semana en curso solo cuenta si ya supera la media; si no,
 *   la cuenta termina en la anterior. La primera semana nunca cuenta (no tiene media previa).</li>
 *   <li>Los eventos posteriores a hoy (reloj cambiado) se ignoran.</li>
 * </ul>
 */
public final class Records {

    private Records() {
    }

    /** Resultado inmutable de {@link #compute}. */
    public static final class Result {
        @Nullable public final LocalDate bestDay;
        public final int bestDayMinutes;
        public final int longestSessionMin;
        @Nullable public final LocalDate longestSessionDay;
        /** Hora local 0-23 o -1 sin datos. */
        public final int peakHour;
        public final int weeksAboveAverage;

        Result(LocalDate bestDay, int bestDayMinutes, int longestSessionMin, LocalDate longestSessionDay,
               int peakHour, int weeksAboveAverage) {
            this.bestDay = bestDay;
            this.bestDayMinutes = bestDayMinutes;
            this.longestSessionMin = longestSessionMin;
            this.longestSessionDay = longestSessionDay;
            this.peakHour = peakHour;
            this.weeksAboveAverage = weeksAboveAverage;
        }

        public boolean isEmpty() {
            return bestDay == null;
        }
    }

    @NonNull
    public static Result compute(@NonNull List<FocusEvent> events, @NonNull LocalDate today,
                                 @NonNull ZoneId zone) {
        Map<LocalDate, Integer> perDay = new HashMap<>();
        List<FocusEvent> valid = new ArrayList<>();
        int longest = 0;
        LocalDate longestDay = null;
        LocalDate first = null;
        for (FocusEvent e : events) {
            if (e == null || e.type != FocusEvent.TYPE_FOCUS || e.durationMin <= 0) {
                continue;
            }
            LocalDate day = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate();
            if (day.isAfter(today)) {
                continue;
            }
            valid.add(e);
            Integer n = perDay.get(day);
            perDay.put(day, (n == null ? 0 : n) + e.durationMin);
            if (e.durationMin > longest
                    || (e.durationMin == longest && longestDay != null && day.isAfter(longestDay))) {
                longest = e.durationMin;
                longestDay = day;
            }
            if (first == null || day.isBefore(first)) {
                first = day;
            }
        }
        if (first == null) {
            return new Result(null, 0, 0, null, -1, 0);
        }
        LocalDate bestDay = null;
        int bestMin = 0;
        for (Map.Entry<LocalDate, Integer> en : perDay.entrySet()) {
            int m = en.getValue();
            if (m > bestMin || (m == bestMin && bestDay != null && en.getKey().isAfter(bestDay))) {
                bestMin = m;
                bestDay = en.getKey();
            }
        }
        int peak = HourHeatmapCalculator.compute(valid, first.minusDays(1), today, zone).peakHour(); // -1 día: una sesión que cruza la medianoche empezó el día anterior
        return new Result(bestDay, bestMin, longest, longestDay, peak,
                weeksAboveAverage(perDay, first, today));
    }

    private static int weeksAboveAverage(Map<LocalDate, Integer> perDay, LocalDate first, LocalDate today) {
        LocalDate firstWeek = StreakCalculator.weekStart(first);
        LocalDate thisWeek = StreakCalculator.weekStart(today);
        int n = (int) ((thisWeek.toEpochDay() - firstWeek.toEpochDay()) / 7) + 1;
        long[] weekly = new long[n];
        for (Map.Entry<LocalDate, Integer> en : perDay.entrySet()) {
            int idx = (int) ((StreakCalculator.weekStart(en.getKey()).toEpochDay() - firstWeek.toEpochDay()) / 7);
            weekly[idx] += en.getValue();
        }
        boolean[] above = new boolean[n];
        long running = weekly[0];
        for (int i = 1; i < n; i++) {
            above[i] = weekly[i] * i > running; // weekly[i] > running / i sin dividir
            running += weekly[i];
        }
        int cursor = above[n - 1] ? n - 1 : n - 2;
        int streak = 0;
        while (cursor >= 1 && above[cursor]) {
            streak++;
            cursor--;
        }
        return streak;
    }
}
