package com.example.bbettercalendar.social.presence;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.social.common.Scheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link Scheduler} y reloj falsos para los tests de la presencia: nada corre hasta {@link #advance(long)}, y
 * {@link #now()} es el reloj de pared que ve el publicador.
 */
final class FakeClockScheduler implements Scheduler {

    private static final class Entry {
        final long at;
        final Runnable task;
        boolean done;

        Entry(long at, Runnable task) {
            this.at = at;
            this.task = task;
        }
    }

    private long now;
    private final List<Entry> entries = new ArrayList<>();

    FakeClockScheduler(long start) {
        this.now = start;
    }

    long now() {
        return now;
    }

    @NonNull
    @Override
    public Cancellable schedule(long delayMs, @NonNull Runnable task) {
        Entry e = new Entry(now + delayMs, task);
        entries.add(e);
        return () -> e.done = true;
    }

    /** Avanza el reloj y corre, en orden de hora, lo que vence (también lo que se programe mientras). */
    void advance(long ms) {
        long target = now + ms;
        while (true) {
            Entry next = null;
            for (Entry e : new ArrayList<>(entries)) {
                if (!e.done && e.at <= target && (next == null || e.at < next.at)) {
                    next = e;
                }
            }
            if (next == null) {
                break;
            }
            now = next.at;
            next.done = true;
            next.task.run();
        }
        now = target;
    }
}
