package com.example.bbettercalendar.ui.focus;

import androidx.annotation.NonNull;

/**
 * The background-fail grace period, as pure Java (u14; extracted from
 * {@link FocusSessionViewModel} so it can be unit-tested). When the APP goes to background with a
 * running, unarmed concentration, a {@code graceMillis} timeout is scheduled; coming back to the
 * foreground cancels it; if it fires, {@link Callback#onGraceExpired()} runs once.
 *
 * <p>Scheduling is injected ({@link Scheduler}): production posts on the main-looper Handler,
 * tests use a virtual clock. Main thread only, like the rest of the session.
 */
public final class BackgroundGrace {

    /** postDelayed / removeCallbacks on whatever executes the timeout. */
    public interface Scheduler {
        void postDelayed(@NonNull Runnable task, long delayMillis);

        void cancel(@NonNull Runnable task);
    }

    public interface Callback {
        void onGraceExpired();
    }

    private final Scheduler scheduler;
    private final long graceMillis;
    private final Callback callback;
    private final Runnable expire = this::expire;
    // Old HomeFragment.isBackground: true between "app went to background with a running,
    // unarmed concentration" and either the return to foreground or the grace expiring.
    private boolean armed;

    public BackgroundGrace(@NonNull Scheduler scheduler, long graceMillis,
                           @NonNull Callback callback) {
        this.scheduler = scheduler;
        this.graceMillis = graceMillis;
        this.callback = callback;
    }

    /**
     * The app went to background. @param shouldArm the session decides (a running concentration
     * without block mode). Re-arming restarts the full grace period.
     * @return true if the grace period is now pending.
     */
    public boolean onBackgrounded(boolean shouldArm) {
        if (!shouldArm) {
            return false;
        }
        armed = true;
        scheduler.cancel(expire);
        scheduler.postDelayed(expire, graceMillis);
        return true;
    }

    /** The app is visible again: a pending grace period is cancelled (no fail). */
    public void onForegrounded() {
        armed = false;
        scheduler.cancel(expire);
    }

    public boolean isArmed() {
        return armed;
    }

    /** Stops a pending timeout without firing it (the session is going away for good). */
    public void cancel() {
        onForegrounded();
    }

    private void expire() {
        if (!armed) {
            return;
        }
        armed = false;
        callback.onGraceExpired();
    }
}
