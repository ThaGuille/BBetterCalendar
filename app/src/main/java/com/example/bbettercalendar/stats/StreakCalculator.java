package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Racha de concentración (migración visual u3). Puro, sin Android ni I/O: recibe los días con >= 1
 * pomodoro completado (lo que devuelve {@link FocusEventDAO#getActiveDaysSince(long)} /
 * {@link FocusEventDAO#observeAllActiveDays()}) y el "hoy" del llamador, para poder testearlo con
 * JUnit puro.
 *
 * <p>Sustituye a "días activos en los últimos 30" y al heredado {@code Stats.currentStreak}, que
 * mide aperturas de la app, no sesiones.
 *
 * <p>Semántica:
 * <ul>
 *   <li><b>Racha actual</b>: días consecutivos con sesión que terminan hoy. Si hoy todavía no hay
 *   sesión, la racha sigue viva y termina ayer (el día no ha acabado): con sesiones lun-mar-mié y
 *   hoy = jueves sin sesión, la racha es 3. Si ayer tampoco hubo, es 0.</li>
 *   <li><b>Mejor racha</b>: la serie consecutiva más larga de todo el histórico recibido. Siempre
 *   &gt;= racha actual.</li>
 *   <li><b>Fila de la semana</b>: 7 días de lunes a domingo de la semana de hoy, cada uno
 *   {@link DayState}.</li>
 *   <li>Los días posteriores a hoy (reloj cambiado) se ignoran en todo el cálculo.</li>
 * </ul>
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    /** Estado de un día en la fila semanal. */
    public enum DayState {
        /** Pasado u hoy, con >= 1 sesión completada. */
        DONE,
        /** Pasado sin sesión. */
        MISSED,
        /** Hoy, todavía sin sesión. */
        PENDING,
        /** Posterior a hoy. */
        FUTURE
    }

    /** Un círculo de la fila semanal. */
    public static final class WeekDay {
        @NonNull public final LocalDate date;
        @NonNull public final DayState state;
        public final boolean isToday;

        WeekDay(@NonNull LocalDate date, @NonNull DayState state, boolean isToday) {
            this.date = date;
            this.state = state;
            this.isToday = isToday;
        }

        @NonNull
        @Override
        public String toString() {
            return date + ":" + state + (isToday ? "*" : "");
        }
    }

    /** Resultado inmutable de {@link #compute}. */
    public static final class Result {
        public final int currentStreak;
        public final int bestStreak;
        public final boolean activeToday;
        /** Siempre 7 elementos, lunes (0) a domingo (6). */
        @NonNull public final List<WeekDay> week;

        Result(int currentStreak, int bestStreak, boolean activeToday, @NonNull List<WeekDay> week) {
            this.currentStreak = currentStreak;
            this.bestStreak = bestStreak;
            this.activeToday = activeToday;
            this.week = Collections.unmodifiableList(week);
        }
    }

    /**
     * Calcula racha actual, mejor racha y fila semanal a partir de fechas ISO "YYYY-MM-DD" (formato
     * de {@code date(..., 'localtime')} en SQLite). Las cadenas nulas o mal formadas se ignoran.
     */
    @NonNull
    public static Result compute(@NonNull Collection<String> isoDays, @NonNull LocalDate today) {
        return computeFromDates(parseIsoDays(isoDays), today);
    }

    /** Igual que {@link #compute} pero con fechas ya parseadas. */
    @NonNull
    public static Result computeFromDates(@NonNull Collection<LocalDate> days, @NonNull LocalDate today) {
        Set<LocalDate> set = new HashSet<>(days);
        set.remove(null);
        return new Result(currentStreak(set, today), bestStreak(set, today), set.contains(today),
                weekRow(set, today));
    }

    /** Racha consecutiva que termina hoy (o ayer, si hoy aún no hay sesión). */
    public static int currentStreak(@NonNull Set<LocalDate> days, @NonNull LocalDate today) {
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** Serie consecutiva más larga entre los días &lt;= hoy. */
    public static int bestStreak(@NonNull Set<LocalDate> days, @NonNull LocalDate today) {
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : new TreeSet<>(days)) {
            if (d.isAfter(today)) {
                break;
            }
            run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        return best;
    }

    /** Lunes a domingo de la semana que contiene {@code today}. */
    @NonNull
    public static List<WeekDay> weekRow(@NonNull Set<LocalDate> days, @NonNull LocalDate today) {
        LocalDate monday = weekStart(today);
        List<WeekDay> row = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            DayState state;
            if (d.isAfter(today)) {
                state = DayState.FUTURE;
            } else if (days.contains(d)) {
                state = DayState.DONE;
            } else if (d.equals(today)) {
                state = DayState.PENDING;
            } else {
                state = DayState.MISSED;
            }
            row.add(new WeekDay(d, state, d.equals(today)));
        }
        return row;
    }

    /** Lunes de la semana de {@code d} (la semana empieza en lunes, ISO-8601). */
    @NonNull
    public static LocalDate weekStart(@NonNull LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    @NonNull
    static Set<LocalDate> parseIsoDays(@NonNull Collection<String> isoDays) {
        Set<LocalDate> out = new HashSet<>();
        for (String iso : isoDays) {
            if (iso == null) {
                continue;
            }
            try {
                out.add(LocalDate.parse(iso));
            } catch (RuntimeException ignored) {
                // Una fila con timestamp corrupto no debe tumbar el cálculo entero.
            }
        }
        return out;
    }
}
