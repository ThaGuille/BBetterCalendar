package com.example.bbettercalendar.ui.focus;

import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Phase;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

/**
 * Immutable render state of the focus session, published by {@link FocusSessionViewModel}
 * every second while running and on every transition. Everything a timer screen (Home card,
 * Focus screen) needs; no Android types, so any screen can render it.
 */
public final class FocusUiState {

    private final State state;
    private final long remainingMillis;
    private final long countdownMillis;
    private final long endsAtWallMillis;
    private final int currentCycle;
    private final int totalCycles;
    private final boolean infiniteCycles;
    private final int cyclesCompleted;
    private final boolean blockModeArmed;

    public FocusUiState(State state, long remainingMillis, long countdownMillis,
                        long endsAtWallMillis, int currentCycle, int totalCycles,
                        boolean infiniteCycles, int cyclesCompleted, boolean blockModeArmed) {
        this.state = state;
        this.remainingMillis = remainingMillis;
        this.countdownMillis = countdownMillis;
        this.endsAtWallMillis = endsAtWallMillis;
        this.currentCycle = currentCycle;
        this.totalCycles = totalCycles;
        this.infiniteCycles = infiniteCycles;
        this.cyclesCompleted = cyclesCompleted;
        this.blockModeArmed = blockModeArmed;
    }

    /** One of the six machine states. */
    public State getState() {
        return state;
    }

    /** FOCUS (concentration) or REST. */
    public Phase getPhase() {
        return state.isRest() ? Phase.REST : Phase.FOCUS;
    }

    public boolean isRest() {
        return state.isRest();
    }

    /** A countdown is ticking (concentration or rest). */
    public boolean isRunning() {
        return state.isRunning();
    }

    public boolean isPaused() {
        return state.isPaused();
    }

    /** STOPPED: no session; the play button starts a concentration. */
    public boolean isIdle() {
        return state == State.STOPPED;
    }

    /** Any state but STOPPED: a session is in progress (old "focus mode" condition). */
    public boolean isSessionActive() {
        return state != State.STOPPED;
    }

    /** "Skip rest" is offered in every rest state (running, paused or pending). */
    public boolean canSkipRest() {
        return state.isRest();
    }

    /** Remaining time of the current countdown ({@code mm:ss} via FormatHelper, floor seconds). */
    public long getRemainingMillis() {
        return remainingMillis;
    }

    /** Full length of the current countdown — denominator for a progress ring. */
    public long getCountdownMillis() {
        return countdownMillis;
    }

    /** 0..1 elapsed fraction of the current countdown. */
    public float getProgress() {
        if (countdownMillis <= 0) {
            return 0f;
        }
        float elapsed = (countdownMillis - remainingMillis) / (float) countdownMillis;
        return Math.max(0f, Math.min(1f, elapsed));
    }

    /**
     * Wall-clock instant ({@code System.currentTimeMillis()} base) at which the running countdown
     * ends — for an "Ends at 10:45" label. 0 when not running.
     */
    public long getEndsAtMillis() {
        return endsAtWallMillis;
    }

    /** 1-based cycle in progress; a rest belongs to the cycle it follows. */
    public int getCurrentCycle() {
        return currentCycle;
    }

    /** Configured number of cycles (meaningless when {@link #isInfiniteCycles()}). */
    public int getTotalCycles() {
        return totalCycles;
    }

    public boolean isInfiniteCycles() {
        return infiniteCycles;
    }

    public int getCyclesCompleted() {
        return cyclesCompleted;
    }

    /** The user armed the 🚫 total-block toggle (stays armed across sessions). */
    public boolean isBlockModeArmed() {
        return blockModeArmed;
    }

    /** Block mode is actually enforcing: armed AND a concentration is running. */
    public boolean isBlockingActive() {
        return blockModeArmed && state == State.RUNNING;
    }

    /** True when anything but the countdown text changed (controls need re-rendering). */
    public boolean controlsDifferFrom(FocusUiState other) {
        return other == null
                || other.state != state
                || other.blockModeArmed != blockModeArmed
                || other.currentCycle != currentCycle
                || other.totalCycles != totalCycles
                || other.infiniteCycles != infiniteCycles;
    }
}
