package com.example.bbettercalendar.social.presence;

import androidx.annotation.NonNull;

/**
 * Lo que este móvil quiere publicar en {@code status/{uid}} (F4-u3), antes de pasarlo a hora del servidor.
 * Valor inmutable: {@link PresenceTransitions} lo calcula a partir del temporizador y {@link PresenceWrites}
 * lo convierte en el mapa del contrato (03 §5, D-033).
 *
 * <p>Los tiempos van en el reloj de pared del móvil ({@code System.currentTimeMillis()}); el desfase con el
 * servidor ({@code .info/serverTimeOffset}) se suma al escribir. {@link #sessionStart} existe con
 * {@code FOCUS}, {@code BREAK} y {@code PAUSED}; {@link #pausedAt} solo con {@code PAUSED}; con {@code IDLE}
 * los dos valen 0.
 */
public final class OwnPresence {

    /** Los cuatro estados del contrato: {@code focus}, {@code break}, {@code paused} e {@code idle}. */
    public enum Kind { FOCUS, BREAK, PAUSED, IDLE }

    public static final OwnPresence IDLE = new OwnPresence(Kind.IDLE, 0L, 0L);

    @NonNull
    public final Kind kind;
    /** Inicio de la racha continua (d2 = A), reloj del móvil; 0 con {@code IDLE}. */
    public final long sessionStart;
    /** Cuándo empezó la pausa, reloj del móvil; 0 si no es {@code PAUSED}. */
    public final long pausedAt;

    private OwnPresence(@NonNull Kind kind, long sessionStart, long pausedAt) {
        this.kind = kind;
        this.sessionStart = sessionStart;
        this.pausedAt = pausedAt;
    }

    @NonNull
    public static OwnPresence focus(long sessionStart) {
        return new OwnPresence(Kind.FOCUS, sessionStart, 0L);
    }

    @NonNull
    public static OwnPresence onBreak(long sessionStart) {
        return new OwnPresence(Kind.BREAK, sessionStart, 0L);
    }

    @NonNull
    public static OwnPresence paused(long sessionStart, long pausedAt) {
        return new OwnPresence(Kind.PAUSED, sessionStart, pausedAt);
    }

    /** {@code focus} o {@code break}: el temporizador corre (lo que se refresca cada 2 h). */
    public boolean isRunning() {
        return kind == Kind.FOCUS || kind == Kind.BREAK;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OwnPresence)) {
            return false;
        }
        OwnPresence other = (OwnPresence) o;
        return kind == other.kind && sessionStart == other.sessionStart && pausedAt == other.pausedAt;
    }

    @Override
    public int hashCode() {
        int h = kind.hashCode();
        h = 31 * h + Long.hashCode(sessionStart);
        return 31 * h + Long.hashCode(pausedAt);
    }

    @NonNull
    @Override
    public String toString() {
        return "OwnPresence{" + kind + ", sessionStart=" + sessionStart + ", pausedAt=" + pausedAt + '}';
    }
}
