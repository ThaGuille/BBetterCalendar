package com.example.bbettercalendar.ui.progress;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Rango temporal seleccionado en el navegador de Progress. Inmutable: cada cambio
// (paso adelante/atrás, o cambio de granularidad) produce un TimeRange nuevo. Una sola
// instancia es la fuente de verdad que observan los gráficos (LocalDate -> desugaring on).
public final class TimeRange {

    public final LocalDate anchor;          // día representativo dentro del rango
    public final Granularity granularity;

    public TimeRange(LocalDate anchor, Granularity granularity) {
        this.anchor = anchor;
        this.granularity = granularity;
    }

    // Por defecto: la semana actual (varios puntos en los gráficos al abrir la pantalla).
    public static TimeRange currentWeek() {
        return new TimeRange(LocalDate.now(), Granularity.WEEK);
    }

    public LocalDate startDay() {
        switch (granularity) {
            case WEEK:
                return anchor.minusDays(anchor.getDayOfWeek().getValue() - 1); // lunes
            case MONTH:
                return anchor.withDayOfMonth(1);
            case DAY:
            default:
                return anchor;
        }
    }

    public LocalDate endDay() {
        switch (granularity) {
            case WEEK:
                return startDay().plusDays(6); // domingo
            case MONTH:
                return anchor.withDayOfMonth(anchor.lengthOfMonth());
            case DAY:
            default:
                return anchor;
        }
    }

    public TimeRange withGranularity(Granularity g) {
        return new TimeRange(anchor, g);
    }

    // dir = -1 (atrás) / +1 (adelante), una unidad de la granularidad actual.
    public TimeRange stepped(int dir) {
        switch (granularity) {
            case WEEK:
                return new TimeRange(anchor.plusWeeks(dir), granularity);
            case MONTH:
                return new TimeRange(anchor.plusMonths(dir), granularity);
            case DAY:
            default:
                return new TimeRange(anchor.plusDays(dir), granularity);
        }
    }

    // No se puede avanzar si el rango ya alcanza hoy (no hay datos futuros que mostrar).
    public boolean canStepForward(LocalDate today) {
        return endDay().isBefore(today);
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(startDay()) && !day.isAfter(endDay());
    }

    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault());
    private static final DateTimeFormatter SPAN_FMT =
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());
    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault());

    // Etiqueta para el centro del stepper ("Today" / "This week" / "June 2026" ...).
    public String label() {
        LocalDate today = LocalDate.now();
        switch (granularity) {
            case WEEK:
                if (contains(today)) return "This week";
                return startDay().format(SPAN_FMT) + " – " + endDay().format(SPAN_FMT);
            case MONTH:
                if (contains(today)) return "This month";
                return anchor.format(MONTH_FMT);
            case DAY:
            default:
                if (anchor.equals(today)) return "Today";
                if (anchor.equals(today.minusDays(1))) return "Yesterday";
                return anchor.format(DAY_FMT);
        }
    }
}
