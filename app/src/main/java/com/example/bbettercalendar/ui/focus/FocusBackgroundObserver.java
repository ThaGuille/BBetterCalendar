package com.example.bbettercalendar.ui.focus;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * App-level foreground/background signal for the focus session's fail grace period.
 *
 * <p><b>u14: process level.</b> Until u13 this observed {@code MainActivity}'s own ON_START/ON_STOP,
 * so opening ANOTHER activity of the app (AddEventActivity, AppPickerActivity…) with a running
 * concentration counted as "leaving the app" and failed the session after the grace period. It
 * now observes {@link ProcessLifecycleOwner}: ON_STOP only when NO activity of the app is started
 * (HOME key, recents, another app, screen off), ON_START when any of them is visible again.
 * In-app navigation, dialogs, sheets and the app's own activities never fire it; rotation does not
 * either (ProcessLifecycleOwner delays ON_STOP by ~700 ms precisely to absorb configuration
 * changes). The effective delay before a fail is therefore ~700 ms + {@code FAIL_GRACE_MILLIS}.
 *
 * <p>Singleton registered lazily on the process lifecycle the first time {@link #get()} is called
 * (by {@link FocusSessionViewModel}'s constructor, main thread). Listeners (one per live session
 * ViewModel) add themselves there and remove themselves in {@code onCleared()}.
 */
public final class FocusBackgroundObserver implements DefaultLifecycleObserver {

    /** Receives app foreground/background transitions. Main thread. */
    public interface Listener {
        void onAppForegrounded();

        void onAppBackgrounded();
    }

    private static FocusBackgroundObserver instance;

    private final Set<Listener> listeners = new LinkedHashSet<>();
    // Hasta el primer ON_START se asume primer plano: quien crea la sesión es una pantalla visible.
    private boolean foreground = true;

    @VisibleForTesting
    FocusBackgroundObserver() {
    }

    /** The process-wide instance, registered on {@link ProcessLifecycleOwner} on first use. */
    @MainThread
    @NonNull
    public static FocusBackgroundObserver get() {
        if (instance == null) {
            instance = new FocusBackgroundObserver();
            ProcessLifecycleOwner.get().getLifecycle().addObserver(instance);
        }
        return instance;
    }

    @MainThread
    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
    }

    @MainThread
    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /** False between the app's ON_STOP and its next ON_START. */
    public boolean isAppInForeground() {
        return foreground;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        foreground = true;
        for (Listener l : snapshot()) {
            l.onAppForegrounded();
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        foreground = false;
        for (Listener l : snapshot()) {
            l.onAppBackgrounded();
        }
    }

    // Copia: un listener puede quitarse a sí mismo durante el aviso.
    private List<Listener> snapshot() {
        return new ArrayList<>(listeners);
    }
}
