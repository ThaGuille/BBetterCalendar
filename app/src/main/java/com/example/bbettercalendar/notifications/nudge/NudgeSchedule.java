package com.example.bbettercalendar.notifications.nudge;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Cálculo puro de cuándo toca el próximo disparo de un aviso (JUnit, sin Android). Va por fecha y
 * hora LOCALES y sólo al final se pasa a epoch con la zona: así un cambio de horario (DST) no
 * desplaza el aviso una hora, y si la hora cae en el hueco del cambio de primavera
 * {@code atZone} la empuja hacia delante (nunca la pierde).
 */
public final class NudgeSchedule {

    /** Ventana de entrega de la alarma inexacta ({@code AlarmManager.setWindow}). */
    public static final long WINDOW_MILLIS = 10L * 60_000L;
    /** Ventana más corta para el reintento tras una concentración (ya llega tarde). */
    public static final long DEFER_WINDOW_MILLIS = 5L * 60_000L;
    private static final long MIN_WINDOW_MILLIS = 60_000L;

    private NudgeSchedule() {}

    /**
     * Próxima vez, estrictamente posterior a {@code nowMillis}, en que el reloj local marca
     * {@code minutesOfDay}: hoy si aún no ha pasado, si no mañana. Con {@code weekly}, el próximo
     * domingo a esa hora (hoy si es domingo y aún no ha pasado).
     */
    public static long nextTrigger(long nowMillis, ZoneId zone, int minutesOfDay, boolean weekly) {
        int clamped = Math.max(0, Math.min(24 * 60 - 1, minutesOfDay));
        LocalTime time = LocalTime.of(clamped / 60, clamped % 60);
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        LocalDate day = now.toLocalDate();
        if (weekly) {
            day = day.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        }
        long candidate = day.atTime(time).atZone(zone).toInstant().toEpochMilli();
        if (candidate <= nowMillis) {
            day = weekly ? day.plusWeeks(1) : day.plusDays(1);
            candidate = day.atTime(time).atZone(zone).toInstant().toEpochMilli();
        }
        return candidate;
    }

    /**
     * Como {@link #nextTrigger} pero sin perder la ocurrencia que acaba de pasar: si la hora de hoy
     * cayó en los últimos {@code graceMillis} y todavía no se disparó ({@code lastFireMillis} es
     * anterior), devuelve esa hora (ya pasada: la alarma sale enseguida). Lo usa
     * {@code rescheduleAll}, que puede llegar (arranque del proceso, cambio de hora) mientras la
     * alarma de hoy estaba aún dentro de su ventana de entrega y la reemplazaría por la de mañana.
     * El receiver, tras dispararse, usa {@link #nextTrigger} estricto.
     */
    public static long nextTriggerWithGrace(long nowMillis, ZoneId zone, int minutesOfDay, boolean weekly,
                                            long graceMillis, long lastFireMillis) {
        long recent = nextTrigger(nowMillis - graceMillis, zone, minutesOfDay, weekly);
        if (recent <= nowMillis && lastFireMillis < recent) return recent;
        return nextTrigger(nowMillis, zone, minutesOfDay, weekly);
    }

    /**
     * Longitud de la ventana de entrega: {@code preferred}, recortada para que la alarma no pueda
     * entregarse ya dentro de las horas tranquilas (22:00) de ese día. Mínimo un minuto.
     */
    public static long windowFor(long triggerMillis, ZoneId zone, long preferred) {
        ZonedDateTime at = Instant.ofEpochMilli(triggerMillis).atZone(zone);
        long quietStart = at.toLocalDate()
                .atTime(NudgePolicy.QUIET_START_MINUTES / 60, NudgePolicy.QUIET_START_MINUTES % 60)
                .atZone(zone).toInstant().toEpochMilli();
        long untilQuiet = quietStart - triggerMillis;
        if (untilQuiet <= 0L) return preferred; // ya en horas tranquilas: la política lo descartará
        return Math.max(MIN_WINDOW_MILLIS, Math.min(preferred, untilQuiet));
    }
}
