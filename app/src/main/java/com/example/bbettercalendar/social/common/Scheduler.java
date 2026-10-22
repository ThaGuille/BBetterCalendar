package com.example.bbettercalendar.social.common;

import androidx.annotation.NonNull;

/**
 * Programa una tarea con retraso (F2-u1). Interfaz propia para que los reintentos de
 * {@link WatchedListener} se prueben con un reloj falso; en la app es {@link MainLooperScheduler}.
 */
public interface Scheduler {

    /** Algo programado que aún se puede anular. */
    interface Cancellable {
        void cancel();
    }

    /** Ejecuta {@code task} dentro de {@code delayMs} milisegundos. */
    @NonNull
    Cancellable schedule(long delayMs, @NonNull Runnable task);
}
