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
 *   STOPPED / STOPPED_REST   IDLE     the start stub (MIN_VISIBLE_PROGRESS)
 *   RUNNING                  RUNNING  stub + (1 - stub) * elapsed
 *   RUNNING_REST             REST     same, rest palette
 *   PAUSED / PAUSED_REST     PAUSED   same, frozen (rest palette for the rest one)
 * </pre>
 */
public final class RingStateMapper {

    /** A running countdown younger than this was "just started": a screen opening on it sweeps the ring in. */
    public static final long JUST_STARTED_MILLIS = 1500L;

    /**
     * The ring never starts empty: at 0 elapsed (and idle) it already shows this stub, and grows to 1.
     * The stub lives only here, never in the painter (task rows pass attributed/target as they are).
     */
    public static final float MIN_VISIBLE_PROGRESS = 0.05f;

    private RingStateMapper() {}

    /** What a ring shows. */
    public static final class Ring {
        @NonNull public final RingState state;
        /** 0..1 of the ring drawn: grows from {@link #MIN_VISIBLE_PROGRESS} (start) to 1 (end). */
        public final float progress;
        /** Rest palette (sage only) instead of amber to sage. */
        public final boolean rest;

        Ring(@NonNull RingState state, float progress, boolean rest) {
            this.state = state;
            this.progress = progress;
            this.rest = rest;
        }
    }

    @NonNull
    public static Ring map(@Nullable FocusUiState ui) {
        if (ui == null) {
            return new Ring(RingState.IDLE, MIN_VISIBLE_PROGRESS, false);
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
        float elapsed = state == RingState.IDLE ? 0f : ui.getProgress();
        float progress = MIN_VISIBLE_PROGRESS + (1f - MIN_VISIBLE_PROGRESS) * elapsed;
        return new Ring(state, Math.max(MIN_VISIBLE_PROGRESS, Math.min(1f, progress)), rest);
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
