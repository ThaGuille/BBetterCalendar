package com.example.bbettercalendar.notifications.focus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.notifications.focus.FocusPhaseAlarm.Notice;
import com.example.bbettercalendar.notifications.focus.FocusPhaseAlarm.Plan;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Settings;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

import org.junit.Test;

/**
 * Plan sistema-notificaciones B.1: qué alarma de fin de fase toca para cada estado del timer, y que
 * el aviso que anuncia coincide con lo que de verdad hace {@link FocusTimerStateMachine} al acabar.
 */
public class FocusPhaseAlarmTest {

    private static final long MIN = 60_000L;
    private static final long NOW = 1_700_000_000_000L;

    private static Settings settings(boolean restEnabled, int cycles, boolean infinite, boolean autoCycle) {
        return new Settings(25 * MIN, 5 * MIN, restEnabled, cycles, infinite, autoCycle);
    }

    // ------------------------------------------------------------- sin fase corriendo: sin alarma

    @Test
    public void nonRunningStates_scheduleNothing() {
        Settings s = settings(true, 4, false, true);
        for (State state : new State[]{State.STOPPED, State.PAUSED, State.PAUSED_REST, State.STOPPED_REST}) {
            assertSame(state.name(), Plan.NONE, FocusPhaseAlarm.plan(state, 10 * MIN, NOW, s, 0));
        }
        assertTrue(Plan.NONE.isNone());
    }

    // ------------------------------------------------------------------------- concentración

    @Test
    public void runningFocus_triggersAtWallClockEnd_withBreakLength() {
        Plan plan = FocusPhaseAlarm.plan(State.RUNNING, 12 * MIN + 345, NOW, settings(true, 4, false, false), 0);
        assertFalse(plan.isNone());
        assertEquals(NOW + 12 * MIN + 345, plan.triggerAtWallMillis);
        assertEquals(Notice.FOCUS_DONE, plan.notice);
        assertEquals(5, plan.nextMinutes);
    }

    @Test
    public void runningFocus_restDisabled_isSessionComplete() {
        Plan plan = FocusPhaseAlarm.plan(State.RUNNING, MIN, NOW, settings(false, 4, false, true), 0);
        assertEquals(Notice.SESSION_COMPLETE, plan.notice);
    }

    @Test
    public void runningFocus_lastCycle_isSessionComplete() {
        Plan plan = FocusPhaseAlarm.plan(State.RUNNING, MIN, NOW, settings(true, 4, false, true), 4);
        assertEquals(Notice.SESSION_COMPLETE, plan.notice);
    }

    @Test
    public void runningFocus_infiniteCycles_alwaysBreak() {
        Plan plan = FocusPhaseAlarm.plan(State.RUNNING, MIN, NOW, settings(true, 4, true, true), 40);
        assertEquals(Notice.FOCUS_DONE, plan.notice);
    }

    // ----------------------------------------------------------------------------- descanso

    @Test
    public void runningRest_withAutoCycle_isRoundStarted() {
        Plan plan = FocusPhaseAlarm.plan(State.RUNNING_REST, 3 * MIN, NOW, settings(true, 4, false, true), 1);
        assertEquals(Notice.ROUND_STARTED, plan.notice);
        assertEquals(NOW + 3 * MIN, plan.triggerAtWallMillis);
        assertTrue(plan.nextMinutes >= 1);
    }

    @Test
    public void runningRest_infiniteCycles_isRoundStarted() {
        Plan plan = FocusPhaseAlarm.plan(State.RUNNING_REST, MIN, NOW, settings(true, 4, true, true), 40);
        assertEquals(Notice.ROUND_STARTED, plan.notice);
    }

    @Test
    public void runningRest_lastRoundWithAutoCycle_isSessionComplete() {
        Plan plan = FocusPhaseAlarm.plan(State.RUNNING_REST, MIN, NOW, settings(true, 4, false, true), 3);
        assertEquals(Notice.SESSION_COMPLETE, plan.notice);
    }

    @Test
    public void runningRest_withoutAutoCycle_isAlwaysBreakOver() {
        Plan plan = FocusPhaseAlarm.plan(State.RUNNING_REST, MIN, NOW, settings(true, 4, false, false), 3);
        assertEquals(Notice.BREAK_OVER, plan.notice);
    }

    @Test
    public void negativeRemaining_triggersNow() {
        Plan plan = FocusPhaseAlarm.plan(State.RUNNING, -500, NOW, settings(true, 4, false, false), 0);
        assertEquals(NOW, plan.triggerAtWallMillis);
    }

    @Test
    public void minutesOf_roundsAndNeverZero() {
        assertEquals(1, FocusPhaseAlarm.minutesOf(20_000L));
        assertEquals(5, FocusPhaseAlarm.minutesOf(5 * MIN));
        assertEquals(10, FocusPhaseAlarm.minutesOf(10 * MIN - 1_000L));
    }

    @Test
    public void noticeFrom_parsesNamesAndRejectsJunk() {
        assertEquals(Notice.BREAK_OVER, FocusPhaseAlarm.noticeFrom("BREAK_OVER"));
        assertNull(FocusPhaseAlarm.noticeFrom("nope"));
        assertNull(FocusPhaseAlarm.noticeFrom(null));
    }

    // ------------------------------------------ el aviso anunciado == lo que hace la máquina al acabar

    /** Avanza la máquina hasta el fin de la fase y compara con el aviso que se habría programado. */
    @Test
    public void announcedNotice_matchesStateMachineOutcome() {
        boolean[] bools = {false, true};
        for (boolean restEnabled : bools) {
            for (boolean autoCycle : bools) {
                for (boolean infinite : bools) {
                    for (int cycles = 1; cycles <= 3; cycles++) {
                        checkWholeSession(settings(restEnabled, cycles, infinite, autoCycle));
                    }
                }
            }
        }
    }

    private static void checkWholeSession(Settings s) {
        long[] clock = {0L};
        Outcome outcome = new Outcome();
        FocusTimerStateMachine m = new FocusTimerStateMachine(() -> clock[0], () -> s, outcome);
        m.startFocus(s.focusMillis);
        // Unas cuantas fases bastan para recorrer concentración -> descanso -> ... -> fin.
        for (int phase = 0; phase < 10 && m.getState().isRunning(); phase++) {
            State before = m.getState();
            Plan plan = FocusPhaseAlarm.plan(before, m.getRemainingMillis(), NOW, s, m.getCyclesCompleted());
            outcome.allCycles = false;
            clock[0] += m.getRemainingMillis();
            assertTrue(m.advance());
            String where = describe(s) + " phase " + phase + " from " + before;
            if (plan.notice == Notice.FOCUS_DONE) {
                assertEquals(where, State.RUNNING_REST, m.getState());
            } else if (plan.notice == Notice.BREAK_OVER) {
                // Sin auto-ciclo: el descanso acaba y el temporizador espera.
                assertEquals(where, State.RUNNING_REST, before);
                assertEquals(where, State.STOPPED, m.getState());
                assertFalse(where, outcome.allCycles);
            } else if (plan.notice == Notice.ROUND_STARTED) {
                // Con auto-ciclo: la siguiente concentración ya corre.
                assertEquals(where, State.RUNNING_REST, before);
                assertEquals(where, State.RUNNING, m.getState());
                assertFalse(where, outcome.allCycles);
            } else {
                // SESSION_COMPLETE: acaba en STOPPED, o con «todos los ciclos» si había ciclos.
                assertEquals(where, State.STOPPED, m.getState());
                assertTrue(where, outcome.allCycles || !s.restEnabled);
            }
        }
    }

    private static String describe(Settings s) {
        return "rest=" + s.restEnabled + " auto=" + s.autoCycle + " inf=" + s.infiniteCycles + " n=" + s.cycles;
    }

    private static final class Outcome implements FocusTimerStateMachine.Listener {
        boolean allCycles;

        @Override
        public void onFocusCompleted(long sessionMillis) {
        }

        @Override
        public void onAllCyclesCompleted(int cycles) {
            allCycles = true;
        }

        @Override
        public void onFailed() {
        }
    }
}
