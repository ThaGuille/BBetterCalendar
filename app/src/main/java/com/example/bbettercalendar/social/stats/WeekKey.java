package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Locale;

/**
 * Claves de día y de semana de {@code stats/{uid}} (03 §5, F3-u3). Puro: solo {@code java.time}, nunca lee
 * el reloj; quien llama pasa {@code LocalDate.now()} de la zona del dispositivo. Se comparan claves, nunca
 * horas, así que un cambio de zona o de reloj no rompe el ranking.
 *
 * <ul>
 *   <li>{@code dayKey}: {@code YYYY-MM-DD}, la fecha civil local de quien publica.</li>
 *   <li>{@code weekKey}: {@code YYYY-Www}, año y semana ISO-8601 (la semana empieza en lunes) de esa fecha;
 *       el año es el del año-semana, no el civil (2027-01-01 es {@code 2026-W53}).</li>
 * </ul>
 */
public final class WeekKey {

    private WeekKey() {
    }

    @NonNull
    public static String dayKey(@NonNull LocalDate date) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", date.getYear(), date.getMonthValue(),
                date.getDayOfMonth());
    }

    @NonNull
    public static String weekKey(@NonNull LocalDate date) {
        return String.format(Locale.ROOT, "%04d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR),
                date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Las tres claves que un cálculo necesita para «hoy»: el día, la semana y la semana anterior. */
    public static final class Keys {
        @NonNull
        public final String day;
        @NonNull
        public final String week;
        /** {@code weekKey} de hoy menos 7 días. */
        @NonNull
        public final String previousWeek;

        public Keys(@NonNull String day, @NonNull String week, @NonNull String previousWeek) {
            this.day = day;
            this.week = week;
            this.previousWeek = previousWeek;
        }

        @NonNull
        public static Keys of(@NonNull LocalDate today) {
            return new Keys(dayKey(today), weekKey(today), weekKey(today.minusDays(7)));
        }
    }

    @NonNull
    public static Keys of(@NonNull LocalDate today) {
        return Keys.of(today);
    }
}
