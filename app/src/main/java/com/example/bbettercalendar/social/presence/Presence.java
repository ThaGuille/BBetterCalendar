package com.example.bbettercalendar.social.presence;

import androidx.annotation.NonNull;

/**
 * Lo que ve quien mira de la presencia de un amigo (F4-u4; 02 §7, D-033), ya con la regla de 4 h aplicada por
 * {@link PresenceMapping}. Inmutable. Todas las horas van en <b>hora del servidor</b> (milisegundos).
 *
 * <ul>
 *   <li>{@link State#STUDYING}: {@code focus} o {@code break} frescos (el descanso no se distingue); el tiempo en
 *       vivo es {@code ahora − sessionStart}.</li>
 *   <li>{@link State#PAUSED}: {@code paused} fresco; cuenta como presente y su tiempo está congelado en
 *       {@code pausedAt − sessionStart} (d3).</li>
 *   <li>{@link State#AWAY}: {@code idle}, más de 4 h desde {@code since}, ilegible, sin estado o denegado.</li>
 * </ul>
 */
public final class Presence {

    public enum State { STUDYING, PAUSED, AWAY }

    public static final Presence AWAY = new Presence(State.AWAY, 0L, 0L, 0L);

    @NonNull
    public final State state;
    /** Inicio de la racha continua (d2); 0 en {@code AWAY}. */
    public final long sessionStart;
    /** Solo en {@code PAUSED}: cuándo se pausó; 0 en los demás. */
    public final long pausedAt;
    /** Última escritura del dueño ({@code since}); la regla de 4 h se mide desde aquí. 0 en {@code AWAY}. */
    public final long since;

    private Presence(@NonNull State state, long sessionStart, long pausedAt, long since) {
        this.state = state;
        this.sessionStart = sessionStart;
        this.pausedAt = pausedAt;
        this.since = since;
    }

    /** Estudiando ({@code focus} o {@code break}) desde {@code sessionStart}; lo normal es que lo dé {@link PresenceMapping}. */
    @NonNull
    public static Presence studying(long sessionStart, long since) {
        return new Presence(State.STUDYING, sessionStart, 0L, since);
    }

    /** En pausa con el tiempo congelado en {@code pausedAt − sessionStart}. */
    @NonNull
    public static Presence paused(long sessionStart, long pausedAt, long since) {
        return new Presence(State.PAUSED, sessionStart, pausedAt, since);
    }

    /** Estudiando o en pausa: el amigo va en el grupo de quien estudia (02 §7). */
    public boolean isPresent() {
        return state != State.AWAY;
    }

    /**
     * Tiempo de la sesión con la hora del servidor {@code serverNow}: en vivo si estudia, congelado si está en
     * pausa, 0 si está ausente; nunca negativo (un {@code sessionStart} adelantado da 0).
     */
    public long elapsedMs(long serverNow) {
        switch (state) {
            case STUDYING:
                return Math.max(0L, serverNow - sessionStart);
            case PAUSED:
                return frozenElapsedMs();
            default:
                return 0L;
        }
    }

    /** {@code pausedAt − sessionStart} con suelo en 0; 0 si no está en pausa. */
    public long frozenElapsedMs() {
        return state == State.PAUSED ? Math.max(0L, pausedAt - sessionStart) : 0L;
    }

    /**
     * Última hora del servidor en la que este estado sigue fresco (regla de 4 h): un milisegundo después pasa a
     * ausente sin evento nuevo. {@code Long.MAX_VALUE} si ya está ausente.
     */
    public long awayAfter() {
        return state == State.AWAY ? Long.MAX_VALUE : since + PresenceMapping.MAX_AGE_MS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Presence)) {
            return false;
        }
        Presence p = (Presence) o;
        return state == p.state && sessionStart == p.sessionStart && pausedAt == p.pausedAt && since == p.since;
    }

    @Override
    public int hashCode() {
        int h = state.hashCode();
        h = 31 * h + Long.hashCode(sessionStart);
        h = 31 * h + Long.hashCode(pausedAt);
        return 31 * h + Long.hashCode(since);
    }

    @NonNull
    @Override
    public String toString() {
        return "Presence{" + state + ", start=" + sessionStart + ", pausedAt=" + pausedAt + ", since=" + since + "}";
    }
}
