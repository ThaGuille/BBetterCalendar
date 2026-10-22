package com.example.bbettercalendar.social.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * Ejecuta las tareas de una en una y en el orden en que llegan, encima de otro {@link Executor} (el
 * {@code @IoExecutor}) y sin hilo propio: cola + {@code scheduleNext} (F2-u1).
 *
 * <p>Patrón de F2: <b>un {@code SerialExecutor} por repositorio</b>, compartido por todos sus listeners y
 * callbacks de Firestore. Así los eventos de un mismo flujo nunca se adelantan entre sí y los
 * {@code postValue} salen en orden (regla #3).
 */
public final class SerialExecutor implements Executor {

    private final Executor backing;
    // Guardados por "this".
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    @Nullable
    private Runnable active;

    public SerialExecutor(@NonNull Executor backing) {
        this.backing = backing;
    }

    @Override
    public synchronized void execute(@NonNull Runnable task) {
        tasks.offer(() -> {
            try {
                task.run();
            } finally {
                scheduleNext();
            }
        });
        if (active == null) {
            scheduleNext();
        }
    }

    private synchronized void scheduleNext() {
        active = tasks.poll();
        if (active != null) {
            backing.execute(active);
        }
    }
}
