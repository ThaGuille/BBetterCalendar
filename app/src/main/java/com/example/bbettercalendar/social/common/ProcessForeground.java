package com.example.bbettercalendar.social.common;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

/**
 * Aviso de «la app vuelve a primer plano» ({@link ProcessLifecycleOwner}, evento {@code ON_START}) para
 * los {@link WatchedListener} que esperan tras un error no reintentable (F2-u1). Se registra en el hilo
 * principal aunque se llame desde otro (los repositorios se crean donde Hilt los pida).
 */
public final class ProcessForeground {

    private ProcessForeground() {
    }

    /** Llama a {@code onStart} en el hilo principal cada vez que la app pasa a primer plano. */
    public static void observe(@NonNull Runnable onStart) {
        DefaultLifecycleObserver observer = new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                onStart.run();
            }
        };
        new Handler(Looper.getMainLooper()).post(
                () -> ProcessLifecycleOwner.get().getLifecycle().addObserver(observer));
    }
}
