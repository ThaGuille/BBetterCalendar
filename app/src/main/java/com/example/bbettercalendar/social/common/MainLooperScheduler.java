package com.example.bbettercalendar.social.common;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

/**
 * {@link Scheduler} de la app: {@code postDelayed} en el hilo principal. La tarea solo encola trabajo en
 * el {@link SerialExecutor} del repositorio, así que el hilo principal no hace E/S.
 */
public final class MainLooperScheduler implements Scheduler {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @NonNull
    @Override
    public Cancellable schedule(long delayMs, @NonNull Runnable task) {
        handler.postDelayed(task, delayMs);
        return () -> handler.removeCallbacks(task);
    }
}
