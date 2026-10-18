package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Racha SEMANAL de concentración (plan racha-semanal-y-constancia, sustituye a la racha de días
 * consecutivos de u3). Puro, sin Android ni I/O: recibe los días con >= 1 pomodoro completado (lo
 * que devuelve {@link FocusEventDAO#observeAllActiveDays()}), el objetivo semanal del usuario
 * ({@link WeeklyGoalStore}) y el "hoy" del llamador, para poder testearlo con JUnit puro.
 *
 * <p>Por qué semanal: la gente no abre la app todos los días (sobre todo en finde); una racha diaria
 * se rompía con un sábado libre aunque la semana se hubiera cumplido de sobra.
 *
 * <p>Semántica:
 * <ul>
 *   <li><b>Semana cumplida</b>: lunes-domingo (ISO-8601) con >= {@code goal} días con sesión.</li>
 *   <li><b>Racha actual</b>: semanas cumplidas consecutivas que terminan en la semana actual. Si la
 *   semana actual todavía no está cumplida, la racha sigue viva y termina en la anterior (la semana
 *   no ha acabado). Si la anterior tampoco se cumplió, es 0.</li>
 *   <li><b>Mejor racha</b>: la serie de semanas cumplidas más larga del histórico. Siempre
 *   &gt;= racha actual.</li>
 *   <li><b>Fila de la semana</b>: 7 días de lunes a domingo; un día pasado sin sesión es
 *   {@link DayState#REST} (descanso neutro, no un fallo).</li>
 *   <li>El objetivo se aplica a todo el histórico (no se guarda por semana).</li>
 *   <li>Los días posteriores a hoy (reloj cambiado) se ignoran en todo el cálculo.</li>
 * </ul>
 */
public final class StreakCalculator {

    public static final int MIN_GOAL = 1;
    public static final int MAX_GOAL = 7;

    private StreakCalculator() {
    }

    /** Estado de un día en la fila semanal. */
    public enum DayState {
        /** Pasado u hoy, con >= 1 sesión completada. */
        DONE,
        /** Pasado sin sesión: descanso, se pinta neutro. */
        REST,
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
        /** Días por semana que el usuario se ha propuesto (1-7). */
        public final int goal;
        /** Días con sesión en la semana actual (0-7). */
        public final int daysThisWeek;
        public final boolean goalMetThisWeek;
        /** Semanas cumplidas seguidas (ver semántica en la clase). */
        public final int weekStreak;
        public final int bestWeekStreak;
        public final boolean activeToday;
        /** Siempre 7 elementos, lunes (0) a domingo (6). */
        @NonNull public final List<WeekDay> week;

        Result(int goal, int daysThisWeek, int weekStreak, int bestWeekStreak, boolean activeToday,
               @NonNull List<WeekDay> week) {
            this.goal = goal;
            this.daysThisWeek = daysThisWeek;
            this.goalMetThisWeek = daysThisWeek >= goal;
            this.weekStreak = weekStreak;
            this.bestWeekStreak = bestWeekStreak;
            this.activeToday = activeToday;
            this.week = Collections.unmodifiableList(week);
        }
    }

    /**
     * Calcula la racha semanal a partir de fechas ISO "YYYY-MM-DD" (formato de
     * {@code date(..., 'localtime')} en SQLite). Las cadenas nulas o mal formadas se ignoran.
     */
    @NonNull
    public static Result compute(@NonNull Collection<String> isoDays, int goal, @NonNull LocalDate today) {
        return computeFromDates(parseIsoDays(isoDays), goal, today);
    }

    /** Igual que {@link #compute} pero con fechas ya parseadas. */
    @NonNull
    public static Result computeFromDates(@NonNull Collection<LocalDate> days, int goal,
                                          @NonNull LocalDate today) {
        int g = clampGoal(goal);
        Set<LocalDate> set = new HashSet<>();
        for (LocalDate d : days) {
            if (d != null && !d.isAfter(today)) {
                set.add(d);
            }
        }
        Map<LocalDate, Integer> perWeek = countPerWeek(set);
        int current = currentWeekStreak(perWeek, g, today);
        int best = Math.max(current, bestWeekStreak(perWeek, g));
        Integer thisWeek = perWeek.get(weekStart(today));
        return new Result(g, thisWeek == null ? 0 : thisWeek, current, best, set.contains(today),
                weekRow(set, today));
    }

    public static int clampGoal(int goal) {
        return Math.max(MIN_GOAL, Math.min(MAX_GOAL, goal));
    }

    /** Lunes de cada semana -> nº de días con sesión en ella. */
    @NonNull
    static Map<LocalDate, Integer> countPerWeek(@NonNull Set<LocalDate> days) {
        Map<LocalDate, Integer> out = new HashMap<>();
        for (LocalDate d : days) {
            LocalDate w = weekStart(d);
            Integer n = out.get(w);
            out.put(w, n == null ? 1 : n + 1);
        }
        return out;
    }

    private static boolean met(@NonNull Map<LocalDate, Integer> perWeek, LocalDate week, int goal) {
        Integer n = perWeek.get(week);
        return n != null && n >= goal;
    }

    /** Semanas cumplidas seguidas que terminan en la actual (o en la anterior, si la actual no se ha cumplido aún). */
    static int currentWeekStreak(@NonNull Map<LocalDate, Integer> perWeek, int goal,
                                 @NonNull LocalDate today) {
        LocalDate thisWeek = weekStart(today);
        LocalDate cursor = met(perWeek, thisWeek, goal) ? thisWeek : thisWeek.minusWeeks(1);
        int streak = 0;
        while (met(perWeek, cursor, goal)) {
            streak++;
            cursor = cursor.minusWeeks(1);
        }
        return streak;
    }

    /** Serie más larga de semanas cumplidas consecutivas. */
    static int bestWeekStreak(@NonNull Map<LocalDate, Integer> perWeek, int goal) {
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate w : new TreeSet<>(perWeek.keySet())) {
            if (!met(perWeek, w, goal)) {
                run = 0;
                prev = null;
                continue;
            }
            run = (prev != null && prev.plusWeeks(1).equals(w)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = w;
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
                state = DayState.REST;
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
