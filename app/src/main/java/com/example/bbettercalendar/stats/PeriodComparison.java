package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comparación de periodos (migración visual u3). Puro, sin Android ni I/O: suma un valor diario
 * (normalmente minutos de {@code daily_stat}) en N periodos consecutivos que terminan en el
 * periodo del ancla, más los N periodos inmediatamente anteriores, y da el delta absoluto y
 * porcentual entre ambos bloques.
 *
 * <p>Ejemplos: "Focus time esta semana vs la anterior" = {@code compute(v, hoy, WEEK, 1, true)};
 * "Past 12 weeks vs los 12 anteriores" = {@code compute(v, hoy, WEEK, 12, false)}.
 *
 * <p>Semántica:
 * <ul>
 *   <li>Semanas de lunes a domingo; meses y años naturales.</li>
 *   <li>Arrays de buckets ordenados del más antiguo al más reciente: {@code current[n-1]} es el
 *   periodo que contiene el ancla y {@code previous[n-1]} el periodo n posiciones antes que él.</li>
 *   <li>Los días posteriores al ancla se ignoran (el periodo actual está en curso).</li>
 *   <li>{@code toDate=false}: periodos completos (el actual sólo hasta el ancla).
 *   {@code toDate=true}: en TODOS los buckets sólo cuentan los días hasta la misma posición que
 *   el ancla dentro de su periodo (semana: mismo día de la semana o antes; mes: mismo día del mes
 *   o antes; año: mismo mes/día o antes). Así "lunes-miércoles de esta semana" se compara con
 *   "lunes-miércoles de la anterior" y no con una semana entera.</li>
 *   <li>El delta porcentual no existe si el total anterior es 0 ({@link Result#hasDeltaPercent()}).</li>
 * </ul>
 */
public final class PeriodComparison {

    private PeriodComparison() {
    }

    public enum Period { WEEK, MONTH, YEAR }

    /** Resultado inmutable de {@link #compute}. Los arrays no se copian: no los modifiques. */
    public static final class Result {
        @NonNull public final Period period;
        /** N: número de periodos por bloque. */
        public final int periods;
        /** Primer día de cada periodo del bloque actual (N elementos, del más antiguo al ancla). */
        @NonNull public final LocalDate[] currentStarts;
        /** Primer día de cada periodo del bloque anterior (N elementos). */
        @NonNull public final LocalDate[] previousStarts;
        @NonNull public final long[] current;
        @NonNull public final long[] previous;
        public final long currentTotal;
        public final long previousTotal;

        Result(@NonNull Period period, int periods, @NonNull LocalDate[] currentStarts,
               @NonNull LocalDate[] previousStarts, @NonNull long[] current, @NonNull long[] previous) {
            this.period = period;
            this.periods = periods;
            this.currentStarts = currentStarts;
            this.previousStarts = previousStarts;
            this.current = current;
            this.previous = previous;
            this.currentTotal = sum(current);
            this.previousTotal = sum(previous);
        }

        /** currentTotal - previousTotal (negativo si bajó). */
        public long deltaAbsolute() {
            return currentTotal - previousTotal;
        }

        /** false cuando previousTotal == 0: no hay base para un porcentaje. */
        public boolean hasDeltaPercent() {
            return previousTotal != 0;
        }

        /** (current - previous) / previous * 100, sin redondear. 0 si {@link #hasDeltaPercent()} es false. */
        public double deltaPercent() {
            return hasDeltaPercent() ? (currentTotal - previousTotal) * 100.0 / previousTotal : 0.0;
        }
    }

    /**
     * @param valuesByDay valor por día civil (días ausentes = 0, null se ignora)
     * @param anchor      día que define el periodo actual (normalmente hoy)
     * @param n           periodos por bloque, &gt;= 1
     * @param toDate      ver la semántica en el javadoc de la clase
     */
    @NonNull
    public static Result compute(@NonNull Map<LocalDate, Integer> valuesByDay, @NonNull LocalDate anchor,
                                 @NonNull Period period, int n, boolean toDate) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1, was " + n);
        }
        LocalDate anchorStart = periodStart(anchor, period);
        LocalDate[] currentStarts = new LocalDate[n];
        LocalDate[] previousStarts = new LocalDate[n];
        for (int i = 0; i < n; i++) {
            currentStarts[i] = shift(anchorStart, period, i - (n - 1));
            previousStarts[i] = shift(anchorStart, period, i - (2 * n - 1));
        }
        long[] current = new long[n];
        long[] previous = new long[n];
        for (Map.Entry<LocalDate, Integer> e : valuesByDay.entrySet()) {
            LocalDate day = e.getKey();
            Integer value = e.getValue();
            if (day == null || value == null || day.isAfter(anchor)) {
                continue;
            }
            if (toDate && !withinSamePosition(day, anchor, period)) {
                continue;
            }
            long back = periodsBetween(periodStart(day, period), anchorStart, period);
            if (back < 0 || back >= 2L * n) {
                continue;
            }
            if (back < n) {
                current[(int) (n - 1 - back)] += value;
            } else {
                previous[(int) (2 * n - 1 - back)] += value;
            }
        }
        return new Result(period, n, currentStarts, previousStarts, current, previous);
    }

    /** Primer día del periodo que contiene {@code d} (lunes / día 1 / 1 de enero). */
    @NonNull
    public static LocalDate periodStart(@NonNull LocalDate d, @NonNull Period period) {
        switch (period) {
            case WEEK:
                return StreakCalculator.weekStart(d);
            case MONTH:
                return d.withDayOfMonth(1);
            case YEAR:
            default:
                return d.withDayOfYear(1);
        }
    }

    /** Desplaza un inicio de periodo {@code k} periodos (k negativo = hacia atrás). */
    @NonNull
    public static LocalDate shift(@NonNull LocalDate periodStart, @NonNull Period period, long k) {
        switch (period) {
            case WEEK:
                return periodStart.plusWeeks(k);
            case MONTH:
                return periodStart.plusMonths(k);
            case YEAR:
            default:
                return periodStart.plusYears(k);
        }
    }

    /**
     * Primer día del rango que necesita {@link #compute} para (anchor, period, n): el inicio del
     * periodo más antiguo del bloque anterior. Consulta {@code daily_stat} entre este día y el ancla.
     */
    @NonNull
    public static LocalDate queryStart(@NonNull LocalDate anchor, @NonNull Period period, int n) {
        return shift(periodStart(anchor, period), period, -(2L * n - 1));
    }

    /** Mapa día → métrica a partir de filas de {@code daily_stat}. Filas con fecha inválida se ignoran. */
    @NonNull
    public static Map<LocalDate, Integer> byDay(@NonNull List<DailyStat> rows, @NonNull Metric metric) {
        Map<LocalDate, Integer> out = new HashMap<>();
        for (DailyStat row : rows) {
            if (row == null || row.day == null) {
                continue;
            }
            try {
                out.put(LocalDate.parse(row.day), metric.of(row));
            } catch (RuntimeException ignored) {
                // Fila con 'day' corrupto: se trata como ausente.
            }
        }
        return out;
    }

    /**
     * Minutos de foco por día, sustituyendo {@code today} por el valor vivo: hoy todavía no está en
     * {@code daily_stat} (se vuelca en el reset del día siguiente), igual que en
     * {@code ProgressViewModel.buildBundle}.
     */
    @NonNull
    public static Map<LocalDate, Integer> focusMinutesByDay(@NonNull List<DailyStat> rows,
                                                            @Nullable LocalDate today, int liveTodayMinutes) {
        Map<LocalDate, Integer> out = byDay(rows, r -> r.focusMinutes);
        if (today != null) {
            out.put(today, liveTodayMinutes);
        }
        return out;
    }

    /** Métrica de una fila de {@code daily_stat} (focusMinutes, fails, tasksDone…). */
    public interface Metric {
        int of(@NonNull DailyStat row);
    }

    // Distancia en periodos entre dos inicios de periodo (to - from).
    private static long periodsBetween(LocalDate fromStart, LocalDate toStart, Period period) {
        switch (period) {
            case WEEK:
                return (toStart.toEpochDay() - fromStart.toEpochDay()) / 7;
            case MONTH:
                return (toStart.getYear() * 12L + toStart.getMonthValue())
                        - (fromStart.getYear() * 12L + fromStart.getMonthValue());
            case YEAR:
            default:
                return (long) toStart.getYear() - fromStart.getYear();
        }
    }

    // toDate: ¿está 'day' en la misma posición que el ancla, o antes, dentro de su propio periodo?
    private static boolean withinSamePosition(LocalDate day, LocalDate anchor, Period period) {
        switch (period) {
            case WEEK:
                return day.getDayOfWeek().getValue() <= anchor.getDayOfWeek().getValue();
            case MONTH:
                return day.getDayOfMonth() <= anchor.getDayOfMonth();
            case YEAR:
            default:
                return day.getMonthValue() < anchor.getMonthValue()
                        || (day.getMonthValue() == anchor.getMonthValue()
                        && day.getDayOfMonth() <= anchor.getDayOfMonth());
        }
    }

    private static long sum(long[] values) {
        long total = 0;
        for (long v : values) {
            total += v;
        }
        return total;
    }
}
