package com.example.bbettercalendar.ui.focus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.ui.common.widget.RingRamp;
import com.example.bbettercalendar.ui.common.widget.RingState;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

import org.junit.Test;

/** The ring grows from a visible stub to full (RingStateMapper) and its colour ramps up (RingRamp). */
public class RingProgressTest {

    private static final long TOTAL = 25L * 60_000L;

    private static FocusUiState ui(State state, int percentElapsed) {
        long remaining = TOTAL - TOTAL * percentElapsed / 100L;
        return new FocusUiState(state, remaining, TOTAL, 0L, 1, 4, false, 0, false);
    }

    private static float progress(State state, int percentElapsed) {
        return RingStateMapper.map(ui(state, percentElapsed)).progress;
    }

    @Test
    public void minimumIsASmallVisibleStub() {
        assertTrue(RingStateMapper.MIN_VISIBLE_PROGRESS >= 0.02f);
        assertTrue(RingStateMapper.MIN_VISIBLE_PROGRESS <= 0.08f);
    }

    @Test
    public void runningAtZeroElapsedIsTheStub() {
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS, progress(State.RUNNING, 0), 1e-6f);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS, progress(State.RUNNING_REST, 0), 1e-6f);
    }

    @Test
    public void halfwayIsAboutHalf() {
        float p = progress(State.RUNNING, 50);
        assertTrue("was " + p, p >= 0.45f && p <= 0.6f);
    }

    @Test
    public void endIsFull() {
        assertEquals(1f, progress(State.RUNNING, 100), 1e-6f);
        assertEquals(1f, progress(State.RUNNING_REST, 100), 1e-6f);
    }

    @Test
    public void neverDecreasesAndGrowsOverTheWholeRun() {
        for (State s : new State[]{State.RUNNING, State.RUNNING_REST}) {
            float prev = -1f;
            for (int pct = 0; pct <= 100; pct++) {
                float p = progress(s, pct);
                assertTrue(s + " at " + pct + "% went back: " + p + " < " + prev, p >= prev);
                prev = p;
            }
            assertTrue(progress(s, 100) > progress(s, 0));
        }
    }

    @Test
    public void pausedMatchesRunningAtTheSameElapsedTime() {
        for (int pct = 0; pct <= 100; pct += 10) {
            assertEquals(progress(State.RUNNING, pct), progress(State.PAUSED, pct), 0f);
            assertEquals(progress(State.RUNNING_REST, pct), progress(State.PAUSED_REST, pct), 0f);
        }
        assertEquals(RingState.PAUSED, RingStateMapper.map(ui(State.PAUSED, 30)).state);
    }

    @Test
    public void idleRingShowsTheStub() {
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS, progress(State.STOPPED, 0), 1e-6f);
        assertEquals(RingStateMapper.MIN_VISIBLE_PROGRESS, RingStateMapper.map(null).progress, 1e-6f);
    }

    @Test
    public void rampStartsSoftAndEndsFullyIntense() {
        assertTrue(RingRamp.intensity(0f) <= 0.35f);
        assertEquals(1f, RingRamp.intensity(1f), 0f);
    }

    @Test
    public void rampIsStrictlyIncreasing() {
        float prev = RingRamp.intensity(0f);
        for (int i = 1; i <= 100; i++) {
            float w = RingRamp.intensity(i / 100f);
            assertTrue("not increasing at " + i, w > prev);
            prev = w;
        }
    }

    @Test
    public void blendReachesBothEndsAndMixesInBetween() {
        int soft = 0x40102030;
        int intense = 0xFFF0A060;
        assertEquals(soft, RingRamp.blend(soft, intense, 0f));
        assertEquals(intense, RingRamp.blend(soft, intense, 1f));
        assertEquals(0x80808080, RingRamp.blend(0x00000000, 0xFFFFFFFF, 0.5f));
    }

    @Test
    public void colourAtTheEndOfTheTurnIsTheIntenseOne() {
        assertEquals(0xFFF5B445, RingRamp.colorAt(0x73F5B445, 0xFFF5B445, 1f));
        assertTrue(RingRamp.colorAt(0x00000000, 0xFF000000, 0.2f) >>> 24
                < RingRamp.colorAt(0x00000000, 0xFF000000, 0.9f) >>> 24);
    }
}
