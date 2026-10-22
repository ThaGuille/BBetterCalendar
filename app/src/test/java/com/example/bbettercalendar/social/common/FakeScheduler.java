package com.example.bbettercalendar.social.common;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/** {@link Scheduler} con reloj falso: nada corre hasta {@link #advance(long)}. */
final class FakeScheduler implements Scheduler {

    static final class Entry {
        final long at;
        final long delay;
        final Runnable task;
        boolean cancelled;

        Entry(long at, long delay, Runnable task) {
            this.at = at;
            this.delay = delay;
            this.task = task;
        }
    }

    private long now;
    final List<Entry> entries = new ArrayList<>();

    long now() {
        return now;
    }

    @NonNull
    @Override
    public Cancellable schedule(long delayMs, @NonNull Runnable task) {
        Entry e = new Entry(now + delayMs, delayMs, task);
        entries.add(e);
        return () -> e.cancelled = true;
    }

    /** Tareas aún por correr (ni corridas ni anuladas). */
    List<Entry> pending() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (!e.cancelled) {
                out.add(e);
            }
        }
        return out;
    }

    /** Avanza el reloj y corre, en orden de hora, lo que vence (también lo que se programe mientras). */
    void advance(long ms) {
        long target = now + ms;
        while (true) {
            Entry next = null;
            for (Entry e : entries) {
                if (!e.cancelled && e.at <= target && (next == null || e.at < next.at)) {
                    next = e;
                }
            }
            if (next == null) {
                break;
            }
            now = next.at;
            next.cancelled = true; // corrida
            next.task.run();
        }
        now = target;
    }
}
