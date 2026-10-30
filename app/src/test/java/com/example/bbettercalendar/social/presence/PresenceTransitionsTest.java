package com.example.bbettercalendar.social.presence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

import org.junit.Test;

/**
 * F4-u3: del temporizador a la presencia propia (03 §8, D-033): {@code break} propio (d1), racha continua (d2 = A),
 * pausa con tiempo congelado que sigue al reanudar (d3), {@code STOPPED_REST} = {@code idle}. Los valores esperados
 * son ejemplos trabajados a mano (minutos sobre un reloj de pared fijo), no la fórmula del código.
 */
public class PresenceTransitionsTest {

    private static final long MIN = 60_000L;
    /** 2026-10-30 10:00:00 UTC. */
    private static final long T0 = 1_793_354_400_000L;

    private static OwnPresence step(OwnPresence prev, State state, long now) {
        return PresenceTransitions.next(prev, state, 0L, now);
    }

    @Test
    public void stoppedFromNothingIsIdle() {
        assertSame(OwnPresence.IDLE, step(null, State.STOPPED, T0));
    }

    @Test
    public void runningStartsAFocusRunNow() {
        assertEquals(OwnPresence.focus(T0), step(OwnPresence.IDLE, State.RUNNING, T0));
    }

    @Test
    public void restAfterFocusIsBreakWithTheSameStart() {
        OwnPresence focus = step(null, State.RUNNING, T0);
        assertEquals(OwnPresence.onBreak(T0), step(focus, State.RUNNING_REST, T0 + 25 * MIN));
    }

    @Test
    public void autoCycledFocusAfterRestKeepsTheRunStart() {
        OwnPresence focus = step(null, State.RUNNING, T0);
        OwnPresence rest = step(focus, State.RUNNING_REST, T0 + 25 * MIN);
        assertEquals(OwnPresence.focus(T0), step(rest, State.RUNNING, T0 + 30 * MIN));
    }

    @Test
    public void pausingFreezesTheTimeWithPausedAtNow() {
        OwnPresence focus = step(null, State.RUNNING, T0);
        OwnPresence paused = step(focus, State.PAUSED, T0 + 12 * MIN);
        assertEquals(OwnPresence.paused(T0, T0 + 12 * MIN), paused);
        // tiempo congelado que verá un amigo: 12 min
        assertEquals(12 * MIN, paused.pausedAt - paused.sessionStart);
    }

    @Test
    public void pausingARestIsPausedToo() {
        OwnPresence rest = step(step(null, State.RUNNING, T0), State.RUNNING_REST, T0 + 25 * MIN);
        assertEquals(OwnPresence.paused(T0, T0 + 27 * MIN), step(rest, State.PAUSED_REST, T0 + 27 * MIN));
    }

    @Test
    public void stayingPausedDoesNotMoveThePause() {
        OwnPresence paused = step(step(null, State.RUNNING, T0), State.PAUSED, T0 + 12 * MIN);
        assertEquals(OwnPresence.paused(T0, T0 + 12 * MIN), step(paused, State.PAUSED, T0 + 40 * MIN));
    }

    @Test
    public void resumingShiftsTheStartByThePauseSoTheTimeContinues() {
        OwnPresence paused = step(step(null, State.RUNNING, T0), State.PAUSED, T0 + 12 * MIN);
        // 5 min de pausa: al reanudar a las 10:17 el amigo ve 12 min, no 17
        OwnPresence resumed = step(paused, State.RUNNING, T0 + 17 * MIN);
        assertEquals(OwnPresence.focus(T0 + 5 * MIN), resumed);
        assertEquals(12 * MIN, (T0 + 17 * MIN) - resumed.sessionStart);
    }

    @Test
    public void resumingAPausedRestIsABreakWithTheShiftedStart() {
        OwnPresence rest = step(step(null, State.RUNNING, T0), State.RUNNING_REST, T0 + 25 * MIN);
        OwnPresence paused = step(rest, State.PAUSED_REST, T0 + 27 * MIN);
        assertEquals(OwnPresence.onBreak(T0 + 3 * MIN), step(paused, State.RUNNING_REST, T0 + 30 * MIN));
    }

    @Test
    public void cancelFromFocusIsIdle() {
        OwnPresence focus = step(null, State.RUNNING, T0);
        assertSame(OwnPresence.IDLE, step(focus, State.STOPPED, T0 + 3 * MIN));
    }

    @Test
    public void aPauseThatEndsInCancelIsIdle() {
        OwnPresence paused = step(step(null, State.RUNNING, T0), State.PAUSED, T0 + 12 * MIN);
        assertSame(OwnPresence.IDLE, step(paused, State.STOPPED, T0 + 20 * MIN));
    }

    @Test
    public void stoppedRestIsIdle() {
        OwnPresence focus = step(null, State.RUNNING, T0);
        assertSame(OwnPresence.IDLE, step(focus, State.STOPPED_REST, T0 + 25 * MIN));
    }

    @Test
    public void aNewStartAfterAStopIsANewRun() {
        OwnPresence stopped = step(step(null, State.RUNNING, T0), State.STOPPED, T0 + 10 * MIN);
        assertEquals(OwnPresence.focus(T0 + 60 * MIN), step(stopped, State.RUNNING, T0 + 60 * MIN));
    }

    @Test
    public void restoredRunningAfterProcessDeathStartsNoLaterThanItsCountdown() {
        // El proceso murió; el timer se restaura con 10 min ya corridos de la cuenta atrás actual.
        OwnPresence restored = PresenceTransitions.next(null, State.RUNNING, 10 * MIN, T0 + 30 * MIN);
        assertEquals(OwnPresence.focus(T0 + 20 * MIN), restored);
    }

    @Test
    public void restoredPausedAfterProcessDeathFreezesTheElapsedCountdown() {
        OwnPresence restored = PresenceTransitions.next(null, State.PAUSED, 8 * MIN, T0 + 30 * MIN);
        assertEquals(OwnPresence.paused(T0 + 22 * MIN, T0 + 30 * MIN), restored);
    }

    @Test
    public void elapsedIsIgnoredWhileTheRunContinues() {
        OwnPresence focus = step(null, State.RUNNING, T0);
        assertEquals(OwnPresence.focus(T0),
                PresenceTransitions.next(focus, State.RUNNING, 4 * MIN, T0 + 4 * MIN));
    }
}
