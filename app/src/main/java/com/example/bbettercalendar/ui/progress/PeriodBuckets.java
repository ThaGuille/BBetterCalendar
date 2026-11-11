package com.example.bbettercalendar.ui.progress;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Columnas de los graficos de «Todo tu progreso» que siguen al selector de periodo (mejora-visual u8):
 * dia = 24 horas, semana = 7 dias, mes = semanas (lunes a domingo, la primera y la ultima pueden ser
 * parciales), ano = 12 meses. Pura (sin Android): el mismo reparto sirve para el periodo y para el
 * anterior, asi las columnas quedan alineadas por indice.
 */
final class PeriodBuckets {

    final Granularity granularity;
    /** Primer dia del periodo del que se miden las columnas. */
    final LocalDate start;
    final int count;

    private PeriodBuckets(Granularity granularity, LocalDate start, int count) {
        this.granularity = granularity;
        this.start = start;
        this.count = count;
    }

    @NonNull
    static PeriodBuckets of(@NonNull TimeRange range) {
        LocalDate start = range.startDay();
        switch (range.granularity) {
            case WEEK:
                return new PeriodBuckets(Granularity.WEEK, start, 7);
            case MONTH:
                return new PeriodBuckets(Granularity.MONTH, start,
                        weekIndex(start, range.endDay()) + 1);
            case YEAR:
                return new PeriodBuckets(Granularity.YEAR, start, 12);
            case DAY:
            default:
                return new PeriodBuckets(Granularity.DAY, start, 24);
        }
    }

    /** Las mismas columnas con otro numero (el periodo anterior se recorta o se rellena al del actual). */
    @NonNull
    PeriodBuckets withCount(int newCount) {
        return new PeriodBuckets(granularity, start, newCount);
    }

    /** Columna de un instante dentro del periodo, o -1 si cae fuera de las columnas. */
    int indexOf(@NonNull ZonedDateTime when) {
        int idx;
        switch (granularity) {
            case WEEK:
                idx = when.getDayOfWeek().getValue() - 1;
                break;
            case MONTH:
                idx = weekIndex(start, when.toLocalDate());
                break;
            case YEAR:
                idx = when.getMonthValue() - 1;
                break;
            case DAY:
            default:
                idx = when.getHour();
                break;
        }
        return idx >= 0 && idx < count ? idx : -1;
    }

    /** Columna de un dia civil (para granularidad dia siempre -1: un dia no se reparte por dia). */
    int indexOfDay(@NonNull LocalDate day) {
        if (granularity == Granularity.DAY) {
            return -1;
        }
        return indexOf(day.atStartOfDay(java.time.ZoneOffset.UTC));
    }

    /** Numero de columnas ya empezadas hoy (las futuras no cuentan para la media). */
    int elapsed(@NonNull LocalDate today, int nowHour) {
        if (today.isBefore(start)) {
            return 0;
        }
        int idx;
        switch (granularity) {
            case WEEK:
                idx = today.getDayOfWeek().getValue() - 1;
                break;
            case MONTH:
                idx = weekIndex(start, today);
                break;
            case YEAR:
                idx = today.getMonthValue() - 1;
                break;
            case DAY:
            default:
                idx = today.equals(start) ? nowHour : count - 1;
                break;
        }
        return Math.min(count, idx + 1);
    }

    /**
     * Etiquetas del eje: letras de dia (de {@code dayLetters}, lunes primero: el narrow del JDK/ICU da «M» para
     * martes y miercoles), «S1..», letras de mes o horas cada 6 h.
     */
    @NonNull
    String[] labels(@NonNull Locale locale, @NonNull String weekPrefix, @NonNull String[] dayLetters) {
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            switch (granularity) {
                case WEEK:
                    out[i] = i < dayLetters.length ? dayLetters[i] : "";
                    break;
                case MONTH:
                    out[i] = weekPrefix + (i + 1);
                    break;
                case YEAR:
                    out[i] = java.time.Month.of(i + 1).getDisplayName(TextStyle.NARROW, locale)
                            .toUpperCase(locale);
                    break;
                case DAY:
                default:
                    out[i] = i % 6 == 0 ? String.valueOf(i) : "";
                    break;
            }
        }
        return out;
    }

    /** Semanas (lunes a domingo) entre el lunes de {@code from} y el lunes de {@code day}. */
    static int weekIndex(@NonNull LocalDate from, @NonNull LocalDate day) {
        LocalDate a = from.minusDays(from.getDayOfWeek().getValue() - 1);
        LocalDate b = day.minusDays(day.getDayOfWeek().getValue() - 1);
        return (int) ChronoUnit.WEEKS.between(a, b);
    }
}
