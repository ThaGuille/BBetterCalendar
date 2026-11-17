package com.example.bbettercalendar.ui.focus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.ui.common.widget.RingState;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

import org.junit.Test;

/** RingStateMapper (mejora-visual u6): one session state -> one ring, shared by Home and Focus. */
public class RingStateMapperTest {

    private static final long MIN25 = 25L * 60_000L;
    private static final float EPS = 1e-4f;

    private static FocusUiState ui(State state, long remaining, long countdown) {
        return new FocusUiState(state, remaining, countdown, 0L, 1, 4, false, 0, false);
    }

    @Test
    public void nullStateIsIdleWithFullRing() {
        RingStateMapper.Ring ring = RingStateMapper.map(null);
        assertEquals(RingState.IDLE, ring.state);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS, ring.progress, EPS);
        assertFalse(ring.rest);
    }

    @Test
    public void stoppedIsIdleWithFullRing() {
        RingStateMapper.Ring ring = RingStateMapper.map(ui(State.STOPPED, MIN25, MIN25));
        assertEquals(RingState.IDLE, ring.state);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS, ring.progress, EPS);
        assertFalse(ring.rest);
    }

    @Test
    public void runningShowsElapsedProgress() {
        RingStateMapper.Ring ring = RingStateMapper.map(ui(State.RUNNING, MIN25 / 4, MIN25));
        assertEquals(RingState.RUNNING, ring.state);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS + (1f - RingStateMapper.MIN_VISIBLE_PROGRESS) * 0.75f, ring.progress, EPS);
        assertFalse(ring.rest);
    }

    @Test
    public void pausedKeepsTheFrozenFraction() {
        RingStateMapper.Ring ring = RingStateMapper.map(ui(State.PAUSED, MIN25 * 3 / 5, MIN25));
        assertEquals(RingState.PAUSED, ring.state);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS + (1f - RingStateMapper.MIN_VISIBLE_PROGRESS) * 0.4f, ring.progress, EPS);
        assertFalse(ring.rest);
    }

    @Test
    public void runningRestIsRestWithRestPalette() {
        long fiveMin = 5L * 60_000L;
        RingStateMapper.Ring ring = RingStateMapper.map(ui(State.RUNNING_REST, fiveMin / 2, fiveMin));
        assertEquals(RingState.REST, ring.state);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS + (1f - RingStateMapper.MIN_VISIBLE_PROGRESS) * 0.5f, ring.progress, EPS);
        assertTrue(ring.rest);
    }

    @Test
    public void pausedRestIsPausedWithRestPalette() {
        long fiveMin = 5L * 60_000L;
        RingStateMapper.Ring ring = RingStateMapper.map(ui(State.PAUSED_REST, fiveMin / 5, fiveMin));
        assertEquals(RingState.PAUSED, ring.state);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS + (1f - RingStateMapper.MIN_VISIBLE_PROGRESS) * 0.8f, ring.progress, EPS);
        assertTrue(ring.rest);
    }

    @Test
    public void pendingRestIsIdleWithFullRestRing() {
        long fiveMin = 5L * 60_000L;
        RingStateMapper.Ring ring = RingStateMapper.map(ui(State.STOPPED_REST, fiveMin, fiveMin));
        assertEquals(RingState.IDLE, ring.state);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS, ring.progress, EPS);
        assertTrue(ring.rest);
    }

    @Test
    public void fractionIsClampedToZeroAndOne() {
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS, RingStateMapper.map(ui(State.RUNNING, MIN25 * 2, MIN25)).progress, EPS);
        assertEquals(1f, RingStateMapper.map(ui(State.RUNNING, -1_000L, MIN25)).progress, EPS);
    }

    @Test
    public void zeroCountdownDrawsTheStartStub() {
        RingStateMapper.Ring ring = RingStateMapper.map(ui(State.RUNNING, 0L, 0L));
        assertEquals(RingState.RUNNING, ring.state);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS, ring.progress, EPS);
    }

    @Test
    public void homeAndFocusGetTheSameRingForTheSameState() {
        FocusUiState shared = ui(State.RUNNING, 17L * 60_000L + 15_000L, MIN25);
        RingStateMapper.Ring home = RingStateMapper.map(shared);
        RingStateMapper.Ring focus = RingStateMapper.map(shared);
        assertEquals(home.state, focus.state);
        assertEquals(home.progress, focus.progress, 0f);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS + (1f - RingStateMapper.MIN_VISIBLE_PROGRESS) * shared.getProgress(), home.progress, EPS);
    }

    @Test
    public void justStartedOnlyForAFreshRunningCountdown() {
        assertTrue(RingStateMapper.justStarted(ui(State.RUNNING, MIN25 - 400L, MIN25)));
        assertTrue(RingStateMapper.justStarted(ui(State.RUNNING_REST, 60_000L, 60_000L)));
        assertFalse(RingStateMapper.justStarted(ui(State.RUNNING, MIN25 - 5_000L, MIN25)));
        assertFalse(RingStateMapper.justStarted(ui(State.PAUSED, MIN25 - 400L, MIN25)));
        assertFalse(RingStateMapper.justStarted(ui(State.STOPPED, MIN25, MIN25)));
        assertFalse(RingStateMapper.justStarted(null));
    }
}
