package com.example.bbettercalendar.ui.common;

/**
 * One-shot payload for a {@code LiveData<SingleEvent<T>>}: LiveData re-delivers its last value to
 * every new observer (rotation, a second screen observing the same activity-scoped ViewModel), so
 * an "open this dialog / navigate" signal must be consumable exactly once.
 *
 * <p>Typical use: {@code if (event == null || event.isHandled() || isStateSaved()) return;}
 * then {@code event.consume()} and act. Main thread only.
 */
public final class SingleEvent<T> {

    private final T content;
    private boolean handled;

    public SingleEvent(T content) {
        this.content = content;
    }

    /** Returns the payload and marks it handled, or null if someone already consumed it. */
    public T consume() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }

    /** The payload regardless of whether it was handled. */
    public T peek() {
        return content;
    }

    public boolean isHandled() {
        return handled;
    }
}
