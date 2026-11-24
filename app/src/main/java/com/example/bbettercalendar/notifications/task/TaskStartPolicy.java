package com.example.bbettercalendar.notifications.task;

import com.example.bbettercalendar.notifications.NotificationChannels;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Reglas puras del aviso de empezar una tarea de proyecto (rediseno-proyectos 3.8): quien lo recibe,
 * a que hora, que canal usa y que dice. Sin Android: el receiver y el scheduler solo le pasan datos.
 */
public final class TaskStartPolicy {

    /** «En 10 min»: cuanto se pospone el aviso. */
    public static final long SNOOZE_MILLIS = 10L * 60_000L;

    private TaskStartPolicy() { }

    /**
     * La tarea recibe aviso de empezar si sigue pendiente (ni hecha, ni plantilla, ni retirada, y su
     * proyecto activo), tiene hora, y ademas duracion o pomodoro, o su recordatorio es «A la hora»
     * (decision d1: ese recordatorio lo sirve este mismo aviso).
     */
    public static boolean isEligible(boolean hasTime, long durationMillis, int targetMinutes,
                                     boolean done, boolean template, boolean dismissed,
                                     boolean projectActive, boolean atTimeReminder) {
        if (done || template || dismissed || !projectActive) return false;
        if (!hasTime) return false;
        return durationMillis > 0L || targetMinutes > 0 || atTimeReminder;
    }

    /** Las tareas con fecha pero sin hora se guardan a las 00:00: esa hora exacta cuenta como «sin hora». */
    public static boolean hasTimeOfDay(long startMillis, TimeZone zone) {
        if (startMillis <= 0L) return false;
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(startMillis);
        return c.get(Calendar.HOUR_OF_DAY) != 0 || c.get(Calendar.MINUTE) != 0;
    }

    /** El aviso sale exactamente a la hora de empezar. */
    public static long triggerMillis(long startMillis) {
        return startMillis;
    }

    /** Cuando vuelve a sonar un aviso pospuesto ahora. */
    public static long snoozeMillis(long nowMillis) {
        return nowMillis + SNOOZE_MILLIS;
    }

    /** El canal con vibracion larga es el de las tareas que bloquean; la vibracion fuerte solo es suya. */
    public static String channelFor(boolean blockOnFocus) {
        return blockOnFocus ? NotificationChannels.CHANNEL_TASK_START_BLOCK
                : NotificationChannels.CHANNEL_TASK_START;
    }

    /** Minutos que se ensenan: la meta de la tarea, o su duracion en minutos enteros (al menos 1); 0 = ninguno. */
    public static int durationMinutes(long durationMillis, int targetMinutes) {
        if (targetMinutes > 0) return targetMinutes;
        if (durationMillis <= 0L) return 0;
        return (int) Math.max(1L, durationMillis / 60_000L);
    }

    /** Lo que dice el aviso: el receiver lo traduce a textos. */
    public static final class Content {
        public final boolean showProject;
        public final String projectName;
        public final int durationMinutes;
        public final boolean showBlock;

        Content(boolean showProject, String projectName, int durationMinutes, boolean showBlock) {
            this.showProject = showProject;
            this.projectName = projectName;
            this.durationMinutes = durationMinutes;
            this.showBlock = showBlock;
        }
    }

    public static Content content(String projectName, long durationMillis, int targetMinutes,
                                  boolean blockOnFocus) {
        boolean hasName = projectName != null && !projectName.trim().isEmpty();
        return new Content(hasName, hasName ? projectName.trim() : null,
                durationMinutes(durationMillis, targetMinutes), blockOnFocus);
    }

    /** El boton «Empezar» solo existe si hay algo que enfocar: duracion o pomodoro. */
    public static boolean hasStartAction(long durationMillis, int targetMinutes) {
        return durationMinutes(durationMillis, targetMinutes) > 0;
    }
}
