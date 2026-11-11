package com.example.bbettercalendar.ui.common.widget;

/**
 * State of a {@link RingButtonView} (mejora-visual u6). Plain enum, no Android types, so the pure
 * {@code ui/focus/RingStateMapper} (and its JVM test) can produce it.
 *
 * <ul>
 *   <li>{@link #IDLE}: nothing running; full ring, glossy sage disc, play.</li>
 *   <li>{@link #RUNNING}: a concentration ticks; raised disc, pause, the ring empties.</li>
 *   <li>{@link #PAUSED}: the countdown waits; play again (resume), ring dimmed.</li>
 *   <li>{@link #REST}: a rest ticks; raised disc, pause, sage-only ring.</li>
 * </ul>
 */
public enum RingState {
    IDLE, RUNNING, PAUSED, REST;

    /** The primary action of this state is "pause" (a countdown is ticking). */
    public boolean ticking() {
        return this == RUNNING || this == REST;
    }
}
