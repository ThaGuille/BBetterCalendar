package com.example.bbettercalendar.social.presence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

/**
 * Del temporizador a la presencia propia (F4-u3, 03 §8, D-033). Función pura: sin Android, sin Firebase,
 * sin reloj propio.
 *
 * <ul>
 *   <li>{@code RUNNING} → {@code focus}; {@code RUNNING_REST} → {@code break} (d1: estado propio).</li>
 *   <li>{@code PAUSED} y {@code PAUSED_REST} → {@code paused} con el mismo {@code sessionStart} y
 *       {@code pausedAt = now} (d3: «En pausa», tiempo congelado).</li>
 *   <li>{@code STOPPED} y {@code STOPPED_REST} → {@code idle}.</li>
 *   <li>Racha continua (d2 = A): mientras no se pare, foco, descanso y el foco siguiente que arranca solo
 *       comparten {@code sessionStart}. Al reanudar una pausa, {@code sessionStart} avanza lo que duró la pausa:
 *       el tiempo en vivo sigue donde se congeló.</li>
 *   <li>Una racha nueva (desde {@code idle}) empieza al inicio de la cuenta atrás actual: {@code now} menos lo
 *       que ya lleva corrido. Así un {@code RUNNING} restaurado tras la muerte del proceso no empieza más tarde
 *       que su cuenta atrás.</li>
 * </ul>
 */
public final class PresenceTransitions {

    private PresenceTransitions() { }

    /**
     * @param previous           lo último calculado (null = {@code idle})
     * @param state              estado nuevo del temporizador
     * @param countdownElapsedMs lo que lleva corrido la cuenta atrás actual (0 al empezar); solo se usa al
     *                           empezar una racha nueva
     * @param now                reloj de pared del móvil
     */
    @NonNull
    public static OwnPresence next(@Nullable OwnPresence previous, @NonNull State state,
                                   long countdownElapsedMs, long now) {
        OwnPresence prev = previous == null ? OwnPresence.IDLE : previous;
        switch (state) {
            case RUNNING:
                return OwnPresence.focus(runStart(prev, countdownElapsedMs, now));
            case RUNNING_REST:
                return OwnPresence.onBreak(runStart(prev, countdownElapsedMs, now));
            case PAUSED:
            case PAUSED_REST:
                if (prev.kind == OwnPresence.Kind.PAUSED) {
                    return prev; // sigue en la misma pausa: nada cambia
                }
                long start = prev.isRunning() ? prev.sessionStart : newRunStart(countdownElapsedMs, now);
                return OwnPresence.paused(start, Math.max(start, now));
            case STOPPED:
            case STOPPED_REST:
            default:
                return OwnPresence.IDLE;
        }
    }

    /** Inicio de la racha para un estado que corre ({@code focus} o {@code break}). */
    private static long runStart(@NonNull OwnPresence prev, long countdownElapsedMs, long now) {
        switch (prev.kind) {
            case FOCUS:
            case BREAK:
                return prev.sessionStart;
            case PAUSED:
                // Reanudar: el tiempo congelado (pausedAt - sessionStart) sigue contando desde ahora.
                return Math.min(now, prev.sessionStart + Math.max(0L, now - prev.pausedAt));
            case IDLE:
            default:
                return newRunStart(countdownElapsedMs, now);
        }
    }

    private static long newRunStart(long countdownElapsedMs, long now) {
        return now - Math.max(0L, countdownElapsedMs);
    }
}
