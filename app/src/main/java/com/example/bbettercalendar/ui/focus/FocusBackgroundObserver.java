package com.example.bbettercalendar.ui.focus;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

/**
 * Feeds the background-fail grace period of {@link FocusSessionViewModel} from the host
 * activity's lifecycle (replaces the {@code Application.ActivityLifecycleCallbacks} that
 * {@code HomeFragment} registered in onAttach). Registered once by {@code MainActivity}:
 * {@code getLifecycle().addObserver(new FocusBackgroundObserver(this))}.
 *
 * <p>ON_STOP during a configuration change (rotation) is not "leaving the app" and is ignored;
 * the recreated activity registers a fresh observer on the same activity-scoped ViewModel.
 * Navigating between fragments never stops the activity, so it never arms the grace period.
 *
 * <p>The ViewModel is resolved lazily, on the first lifecycle callback, on purpose: creating it
 * reads {@code ConfigurationManager.getConfiguration()} on the main thread, which is only safe
 * once the manager's async load has landed. By ON_START the start destination (Home) already
 * created it during its onCreateView — the same moment the old HomeFragment read the config.
 */
public final class FocusBackgroundObserver implements DefaultLifecycleObserver {

    private final ComponentActivity activity;

    public FocusBackgroundObserver(@NonNull ComponentActivity activity) {
        this.activity = activity;
    }

    private FocusSessionViewModel session() {
        return new ViewModelProvider(activity).get(FocusSessionViewModel.class);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        session().onAppForegrounded();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        if (!activity.isChangingConfigurations()) {
            session().onAppBackgrounded();
        }
    }
}
