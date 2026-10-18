package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Constancia (plan racha-semanal-y-constancia): en cuántos de los días transcurridos de un periodo
 * se completó al menos un pomodoro. A diferencia de una racha, nunca cae a 0 por un mal finde.
 * Puro, sin Android ni I/O (JUnit).
 *
 * <p>Semántica:
 * <ul>
 *   <li>Sólo cuentan los días transcurridos: los futuros no restan.</li>
 *   <li>Hoy cuenta en el denominador sólo si ya tiene sesión (el día no ha acabado): con sesiones
 *   lun-mar y hoy = miércoles sin sesión, el resultado es 2/2, no 2/3.</li>
 *   <li>Celdas de la tira: una por día, o una por semana (lunes) para periodos largos como el año;
 *   cada celda lleva {@code active}/{@code possible} para pintar la intensidad.</li>
 * </ul>
 */
public final class ConsistencyCalculator {

    private ConsistencyCalculator() {
    }

    /** Una celda de la tira: un día o una semana del periodo. */
    public static final class Cell {
        /** Días con sesión dentro de la celda. */
        public final int active;
        /** Días de la celda que cuentan (transcurridos; hoy sólo si tiene sesión). */
        public final int possible;
        /** La celda empieza después de hoy. */
        public final boolean future;
        /** La celda contiene hoy y hoy aún no tiene sesión. */
        public final boolean pendingToday;

        Cell(int active, int possible, boolean future, boolean pendingToday) {
            this.active = active;
            this.possible = possible;
            this.future = future;
            this.pendingToday = pendingToday;
        }

        /** 0..1; 0 si la celda no tiene días que cuenten. */
        public float ratio() {
            return possible <= 0 ? 0f : Math.min(1f, active / (float) possible);
        }
    }

    /** Resultado inmutable de {@link #compute}. */
    public static final class Result {
        public final int activeDays;
        /** Denominador: días transcurridos que cuentan. */
        public final int countedDays;
        /** Días totales del periodo (incluidos los futuros). */
        public final int totalDays;
        public final boolean weekly;
        @NonNull public final List<Cell> cells;

        Result(int activeDays, int countedDays, int totalDays, boolean weekly, @NonNull List<Cell> cells) {
            this.activeDays = activeDays;
            this.countedDays = countedDays;
            this.totalDays = totalDays;
            this.weekly = weekly;
            this.cells = Collections.unmodifiableList(cells);
        }

        /** 0..100, redondeado; 0 sin días que cuenten. */
        public int percent() {
            return countedDays <= 0 ? 0 : Math.round(activeDays * 100f / countedDays);
        }
    }

    /** Igual que {@link #compute(Set, LocalDate, LocalDate, LocalDate, boolean)} con fechas ISO. */
    @NonNull
    public static Result compute(@NonNull Collection<String> isoDays, @NonNull LocalDate start,
                                 @NonNull LocalDate end, @NonNull LocalDate today, boolean weekly) {
        return compute(StreakCalculator.parseIsoDays(isoDays), start, end, today, weekly);
    }

    /**
     * @param weekly {@code true} = una celda por semana (lunes-domingo, recortada al periodo);
     *               {@code false} = una celda por día.
     */
    @NonNull
    public static Result compute(@NonNull Set<LocalDate> days, @NonNull LocalDate start,
                                 @NonNull LocalDate end, @NonNull LocalDate today, boolean weekly) {
        List<Cell> cells = new ArrayList<>();
        int active = 0;
        int counted = 0;
        int total = 0;
        LocalDate cellStart = start;
        while (!cellStart.isAfter(end)) {
            LocalDate cellEnd = weekly ? StreakCalculator.weekStart(cellStart).plusDays(6) : cellStart;
            if (cellEnd.isAfter(end)) {
                cellEnd = end;
            }
            int cellActive = 0;
            int cellPossible = 0;
            boolean pending = false;
            for (LocalDate d = cellStart; !d.isAfter(cellEnd); d = d.plusDays(1)) {
                total++;
                if (d.isAfter(today)) {
                    continue;
                }
                boolean on = days.contains(d);
                if (on) {
                    cellActive++;
                    cellPossible++;
                } else if (d.isBefore(today)) {
                    cellPossible++;
                } else {
                    pending = true;
                }
            }
            cells.add(new Cell(cellActive, cellPossible, cellStart.isAfter(today), pending));
            active += cellActive;
            counted += cellPossible;
            cellStart = cellEnd.plusDays(1);
        }
        return new Result(active, counted, total, weekly, cells);
    }

    /** Fechas ISO "YYYY-MM-DD" de {@link FocusEventDAO} a un set; nulas o mal formadas se ignoran. */
    @NonNull
    public static Set<LocalDate> parseIsoDays(@NonNull Collection<String> isoDays) {
        return StreakCalculator.parseIsoDays(isoDays);
    }

    /** Días con sesión en [from, to] (ambos incluidos). */
    public static int activeDaysBetween(@NonNull Set<LocalDate> days, @NonNull LocalDate from,
                                        @NonNull LocalDate to) {
        int n = 0;
        for (LocalDate d : days) {
            if (!d.isBefore(from) && !d.isAfter(to)) {
                n++;
            }
        }
        return n;
    }
}
