package com.example.bbettercalendar.ui.focus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.ui.common.widget.RingState;

/**
 * The one translation from the session state to a ring (mejora-visual u6): Home's play button, the
 * Focus play button and the big Focus ring all read it, so they always show the same state and the
 * same fraction. Pure: no Android, no timer logic (it only reads {@link FocusUiState}).
 *
 * <pre>
 *   STOPPED / STOPPED_REST   IDLE     full ring (1)
 *   RUNNING                  RUNNING  time left = 1 - progress
 *   RUNNING_REST             REST     time left, rest palette
 *   PAUSED / PAUSED_REST     PAUSED   time left frozen (rest palette for the rest one)
 * </pre>
 */
public final class RingStateMapper {

    /** A running countdown younger than this was "just started": a screen opening on it sweeps the ring in. */
    public static final long JUST_STARTED_MILLIS = 1500L;

    private RingStateMapper() {}

    /** What a ring shows. */
    public static final class Ring {
        @NonNull public final RingState state;
        /** 0..1 of the ring drawn: the part of the current countdown still to go. */
        public final float remaining;
        /** Rest palette (sage only) instead of amber to sage. */
        public final boolean rest;

        Ring(@NonNull RingState state, float remaining, boolean rest) {
            this.state = state;
            this.remaining = remaining;
            this.rest = rest;
        }
    }

    @NonNull
    public static Ring map(@Nullable FocusUiState ui) {
        if (ui == null) {
            return new Ring(RingState.IDLE, 1f, false);
        }
        boolean rest = ui.isRest();
        RingState state;
        if (ui.isRunning()) {
            state = rest ? RingState.REST : RingState.RUNNING;
        } else if (ui.isPaused()) {
            state = RingState.PAUSED;
        } else {
            state = RingState.IDLE;
        }
        float remaining = state == RingState.IDLE ? 1f : 1f - ui.getProgress();
        return new Ring(state, Math.max(0f, Math.min(1f, remaining)), rest);
    }

    /** True for a countdown that is running and began less than {@link #JUST_STARTED_MILLIS} ago. */
    public static boolean justStarted(@Nullable FocusUiState ui) {
        if (ui == null || !ui.isRunning() || ui.getCountdownMillis() <= 0) {
            return false;
        }
        long elapsed = ui.getCountdownMillis() - ui.getRemainingMillis();
        return elapsed >= 0 && elapsed < JUST_STARTED_MILLIS;
    }
}
