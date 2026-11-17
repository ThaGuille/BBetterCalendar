package com.example.bbettercalendar.notifications.nudge;

import com.example.bbettercalendar.notifications.NotificationPrefs;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Reglas de los avisos no transaccionales (plan sistema-notificaciones, «Decisiones»). Java puro:
 * sin Android ni I/O, el reloj y la zona entran como parámetros para poder testearlo con JUnit.
 *
 * <p>Orden de las comprobaciones (la primera que falla decide):
 * <ol>
 *   <li><b>Horas tranquilas</b> 22:00–08:00: ni la hora programada ni el instante real pueden
 *   caer dentro (la pantalla de ajustes limita las horas; aquí se es defensivo).</li>
 *   <li><b>Uno por periodo</b>: un aviso diario no sale dos veces el mismo día; el resumen semanal
 *   tiene su propio presupuesto, 1 por semana (lunes–domingo).</li>
 *   <li><b>Presupuesto diario</b>: como mucho {@link #DAILY_BUDGET} avisos diarios al día entre
 *   «Tu día», «racha en juego» y «planear mañana» (el semanal no cuenta).</li>
 *   <li><b>Retirada</b>: si las {@link #IGNORED_LIMIT} últimas veces que salió un aviso diario
 *   la app no se abrió antes del siguiente, deja de salir hasta que se abra.</li>
 *   <li><b>Concentración en marcha</b>: se aplaza una sola vez al final + {@link #FOCUS_DEFER_MARGIN_MILLIS};
 *   si eso ya no cabe antes de las 22:00 de hoy, se descarta. Un aviso aplazado no se aplaza otra vez.</li>
 * </ol>
 * El contenido («sólo si hay algo que decir») lo comprueba el receiver después: no es política.
 */
public final class NudgePolicy {

    public static final int DAILY_BUDGET = 2;
    public static final int IGNORED_LIMIT = 3;
    public static final int QUIET_START_MINUTES = 22 * 60;
    public static final int QUIET_END_MINUTES = 8 * 60;
    public static final long FOCUS_DEFER_MARGIN_MILLIS = 2L * 60_000L;

    private NudgePolicy() {}

    public enum Action { POST, DEFER, SKIP }

    public enum Reason {
        NONE,
        UNKNOWN_NUDGE,
        QUIET_HOURS,
        ALREADY_POSTED_TODAY,
        WEEKLY_BUDGET,
        DAILY_BUDGET,
        BACKED_OFF,
        FOCUS_ALREADY_DEFERRED,
        FOCUS_ENDS_TOO_LATE
    }

    /** Estado persistido que necesita la política (lo lee {@link NudgeState}). */
    public static final class State {
        /** Avisos diarios publicados hoy (los tres juntos). */
        public final int dailyPostsToday;
        /** Última vez que salió ESTE aviso (0 = nunca). */
        public final long lastPostedMillis;
        /** Ignorados seguidos ANTES de la última publicación de este aviso. */
        public final int ignoredBeforeLastPost;
        /** Última vez que se abrió la app (0 = nunca registrada). */
        public final long lastAppOpenMillis;

        public State(int dailyPostsToday, long lastPostedMillis, int ignoredBeforeLastPost,
                     long lastAppOpenMillis) {
            this.dailyPostsToday = dailyPostsToday;
            this.lastPostedMillis = lastPostedMillis;
            this.ignoredBeforeLastPost = ignoredBeforeLastPost;
            this.lastAppOpenMillis = lastAppOpenMillis;
        }

        public static State empty() {
            return new State(0, 0L, 0, 0L);
        }
    }

    public static final class Decision {
        public final Action action;
        public final Reason reason;
        /** Sólo con {@link Action#DEFER}: cuándo volver a intentarlo. */
        public final long deferUntilMillis;
        /** Ignorados seguidos vistos ahora; con POST el receiver lo guarda junto a la publicación. */
        public final int ignoredCount;

        private Decision(Action action, Reason reason, long deferUntilMillis, int ignoredCount) {
            this.action = action;
            this.reason = reason;
            this.deferUntilMillis = deferUntilMillis;
            this.ignoredCount = ignoredCount;
        }

        static Decision post(int ignored) {
            return new Decision(Action.POST, Reason.NONE, 0L, ignored);
        }

        static Decision defer(long until, int ignored) {
            return new Decision(Action.DEFER, Reason.NONE, until, ignored);
        }

        static Decision skip(Reason reason, int ignored) {
            return new Decision(Action.SKIP, reason, 0L, ignored);
        }

        @Override
        public String toString() {
            return action + (action == Action.SKIP ? "(" + reason + ")"
                    : action == Action.DEFER ? "(" + deferUntilMillis + ")" : "");
        }
    }

    /**
     * @param nudge            clave de {@link NotificationPrefs} (NUDGE_*)
     * @param scheduledMinutes hora programada del aviso, minutos desde medianoche (local)
     * @param deferredFire     true si este disparo ya es el aplazado por una concentración
     * @param focusEndsAtMillis fin de la concentración en marcha (0 = ninguna)
     */
    public static Decision decide(String nudge, int scheduledMinutes, boolean deferredFire,
                                  long nowMillis, ZoneId zone, long focusEndsAtMillis, State state) {
        if (!isKnown(nudge)) return Decision.skip(Reason.UNKNOWN_NUDGE, 0);
        if (state == null) state = State.empty();
        boolean daily = isDaily(nudge);
        int ignored = daily
                ? effectiveIgnored(state.ignoredBeforeLastPost, state.lastPostedMillis, state.lastAppOpenMillis)
                : 0;

        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        int nowMinutes = now.getHour() * 60 + now.getMinute();
        if (isQuiet(scheduledMinutes) || isQuiet(nowMinutes)) {
            return Decision.skip(Reason.QUIET_HOURS, ignored);
        }

        LocalDate today = now.toLocalDate();
        if (state.lastPostedMillis > 0L) {
            LocalDate last = Instant.ofEpochMilli(state.lastPostedMillis).atZone(zone).toLocalDate();
            if (daily && last.equals(today)) {
                return Decision.skip(Reason.ALREADY_POSTED_TODAY, ignored);
            }
            if (!daily && weekStart(last).equals(weekStart(today))) {
                return Decision.skip(Reason.WEEKLY_BUDGET, ignored);
            }
        }

        if (daily && state.dailyPostsToday >= DAILY_BUDGET) {
            return Decision.skip(Reason.DAILY_BUDGET, ignored);
        }

        if (daily && ignored >= IGNORED_LIMIT) {
            return Decision.skip(Reason.BACKED_OFF, ignored);
        }

        if (focusEndsAtMillis > 0L) {
            if (deferredFire) return Decision.skip(Reason.FOCUS_ALREADY_DEFERRED, ignored);
            long until = Math.max(focusEndsAtMillis, nowMillis) + FOCUS_DEFER_MARGIN_MILLIS;
            ZonedDateTime at = Instant.ofEpochMilli(until).atZone(zone);
            int atMinutes = at.getHour() * 60 + at.getMinute();
            if (!at.toLocalDate().equals(today) || atMinutes >= QUIET_START_MINUTES) {
                return Decision.skip(Reason.FOCUS_ENDS_TOO_LATE, ignored);
            }
            return Decision.defer(until, ignored);
        }

        return Decision.post(ignored);
    }

    /**
     * Ignorados seguidos a día de hoy. «Ignorado» = la publicación anterior no tuvo una apertura de
     * la app después; se evalúa al siguiente disparo. Abrir la app después de la última publicación
     * pone la cuenta a 0 (y así termina la retirada).
     */
    static int effectiveIgnored(int ignoredBeforeLastPost, long lastPostedMillis, long lastAppOpenMillis) {
        if (lastPostedMillis <= 0L) return 0;
        if (lastAppOpenMillis >= lastPostedMillis) return 0;
        return Math.max(0, ignoredBeforeLastPost) + 1;
    }

    /** 22:00–08:00 (inicio incluido, fin excluido). */
    public static boolean isQuiet(int minutesOfDay) {
        return minutesOfDay >= QUIET_START_MINUTES || minutesOfDay < QUIET_END_MINUTES;
    }

    public static boolean isDaily(String nudge) {
        return NotificationPrefs.NUDGE_MORNING.equals(nudge)
                || NotificationPrefs.NUDGE_STREAK.equals(nudge)
                || NotificationPrefs.NUDGE_EVENING_PLAN.equals(nudge);
    }

    public static boolean isKnown(String nudge) {
        return isDaily(nudge) || NotificationPrefs.NUDGE_WEEKLY.equals(nudge);
    }

    /**
     * Rotación de copy: un índice en [0, count) distinto de {@code lastIndex} (si hay más de una
     * variante). Determinista dado {@code seed}, para los tests; el receiver pasa algo variable.
     */
    public static int pickVariant(int count, int lastIndex, long seed) {
        if (count <= 1) return 0;
        boolean lastValid = lastIndex >= 0 && lastIndex < count;
        int pool = lastValid ? count - 1 : count;
        int r = (int) Math.floorMod(seed, (long) pool);
        return lastValid && r >= lastIndex ? r + 1 : r;
    }

    static LocalDate weekStart(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
