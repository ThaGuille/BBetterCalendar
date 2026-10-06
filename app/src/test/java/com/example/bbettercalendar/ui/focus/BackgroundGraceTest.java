package com.example.bbettercalendar.ui.focus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * u14: {@link BackgroundGrace} (the background-fail timeout) and {@link FocusBackgroundObserver}
 * (the process-level foreground/background signal that feeds it), on a virtual clock — no
 * Android, no Handler.
 *
 * <p>Semantics under test: only the whole APP going to background arms the grace period (and
 * only when the session says so: a running concentration without block mode); coming back to the
 * foreground before it expires cancels it (no fail); expiring fires exactly once.
 */
public class BackgroundGraceTest {

    private static final long GRACE = 4000L;

    /** postDelayed / removeCallbacks over a virtual clock. */
    private static final class FakeScheduler implements BackgroundGrace.Scheduler {
        private static final class Pending {
            final Runnable task;
            final long at;

            Pending(Runnable task, long at) {
                this.task = task;
                this.at = at;
            }
        }

        private final List<Pending> pending = new ArrayList<>();
        long now;

        @Override
        public void postDelayed(@NonNull Runnable task, long delayMillis) {
            pending.add(new Pending(task, now + delayMillis));
        }

        @Override
        public void cancel(@NonNull Runnable task) {
            for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
                if (it.next().task == task) {
                    it.remove();
                }
            }
        }

        /** Advances the clock, running every task that falls due (in order). */
        void advance(long millis) {
            long target = now + millis;
            while (true) {
                Pending next = null;
                for (Pending p : pending) {
                    if (p.at <= target && (next == null || p.at < next.at)) {
                        next = p;
                    }
                }
                if (next == null) {
                    break;
                }
                pending.remove(next);
                now = next.at;
                next.task.run();
            }
            now = target;
        }

        int pendingCount() {
            return pending.size();
        }
    }

    private FakeScheduler scheduler;
    private int expired;
    private BackgroundGrace grace;

    @Before
    public void setUp() {
        scheduler = new FakeScheduler();
        expired = 0;
        grace = new BackgroundGrace(scheduler, GRACE, () -> expired++);
    }

    // ------------------------------------------------------------------ BackgroundGrace

    @Test
    public void backgroundPastGrace_firesOnce() {
        assertTrue(grace.onBackgrounded(true));
        assertTrue(grace.isArmed());

        scheduler.advance(GRACE - 1);
        assertEquals(0, expired);

        scheduler.advance(1);
        assertEquals(1, expired);
        assertFalse(grace.isArmed());

        scheduler.advance(GRACE * 3);
        assertEquals("fires exactly once", 1, expired);
    }

    @Test
    public void foregroundBeforeGrace_cancelsWithoutFail() {
        grace.onBackgrounded(true);
        scheduler.advance(GRACE - 1);

        grace.onForegrounded();

        assertFalse(grace.isArmed());
        assertEquals(0, scheduler.pendingCount());
        scheduler.advance(GRACE * 2);
        assertEquals(0, expired);
    }

    @Test
    public void sessionSaysNo_neverArms() {
        // Paused / rest / block mode armed / idle: shouldArmBackgroundGrace() == false.
        assertFalse(grace.onBackgrounded(false));

        assertFalse(grace.isArmed());
        assertEquals(0, scheduler.pendingCount());
        scheduler.advance(GRACE * 2);
        assertEquals(0, expired);
    }

    @Test
    public void rearming_restartsTheFullGrace() {
        grace.onBackgrounded(true);
        scheduler.advance(GRACE - 1000);
        grace.onBackgrounded(true);

        assertEquals("the first timeout was replaced", 1, scheduler.pendingCount());
        scheduler.advance(1000);
        assertEquals(0, expired);
        scheduler.advance(GRACE - 1000);
        assertEquals(1, expired);
    }

    @Test
    public void quickOutInOut_usesOnlyTheLastBackgrounding() {
        // u2 deviation 1: out, back and out again within the grace must not fail early with the
        // first (stale) timeout.
        grace.onBackgrounded(true);
        scheduler.advance(3000);
        grace.onForegrounded();
        scheduler.advance(500);
        grace.onBackgrounded(true);

        scheduler.advance(GRACE - 1);
        assertEquals(0, expired);
        scheduler.advance(1);
        assertEquals(1, expired);
    }

    @Test
    public void cancel_dropsPendingTimeout() {
        grace.onBackgrounded(true);
        grace.cancel();

        assertFalse(grace.isArmed());
        scheduler.advance(GRACE * 2);
        assertEquals(0, expired);
    }

    @Test
    public void staleRunnable_afterForeground_doesNotFire() {
        // A scheduler that cannot cancel (e.g. a task already dequeued) must still not fail the
        // session once the app is back: the armed flag guards the expiry.
        List<Runnable> leaked = new ArrayList<>();
        BackgroundGrace leaky = new BackgroundGrace(new BackgroundGrace.Scheduler() {
            @Override
            public void postDelayed(@NonNull Runnable task, long delayMillis) {
                leaked.add(task);
            }

            @Override
            public void cancel(@NonNull Runnable task) {
                // no-op on purpose
            }
        }, GRACE, () -> expired++);

        leaky.onBackgrounded(true);
        leaky.onForegrounded();
        leaked.get(0).run();

        assertEquals(0, expired);
    }

    @Test
    public void foregroundWithoutBackground_isHarmless() {
        grace.onForegrounded();
        grace.onForegrounded();

        assertFalse(grace.isArmed());
        assertEquals(0, expired);
    }

    // ------------------------------------------------------------- FocusBackgroundObserver

    private static final LifecycleOwner OWNER = new LifecycleOwner() {
        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            throw new UnsupportedOperationException("not used by the observer");
        }
    };

    /** Records the observer's signals and drives a grace with them, like FocusSessionViewModel. */
    private final class SessionLike implements FocusBackgroundObserver.Listener {
        boolean concentrationRunning = true;
        final List<String> calls = new ArrayList<>();

        @Override
        public void onAppForegrounded() {
            calls.add("fg");
            grace.onForegrounded();
        }

        @Override
        public void onAppBackgrounded() {
            calls.add("bg");
            grace.onBackgrounded(concentrationRunning);
        }
    }

    @Test
    public void processStopPastGrace_failsTheSession() {
        // HOME key / recents / another app: the PROCESS stops.
        FocusBackgroundObserver observer = new FocusBackgroundObserver();
        SessionLike session = new SessionLike();
        observer.addListener(session);

        observer.onStop(OWNER);
        assertFalse(observer.isAppInForeground());
        scheduler.advance(GRACE);

        assertEquals(1, expired);
    }

    @Test
    public void inAppActivityOrDialog_neverReachesTheListener() {
        // Opening AddEventActivity / AppPickerActivity / a dialog keeps the process STARTED:
        // ProcessLifecycleOwner emits nothing, so the observer never calls the session and no
        // grace is armed however long the user stays there.
        FocusBackgroundObserver observer = new FocusBackgroundObserver();
        SessionLike session = new SessionLike();
        observer.addListener(session);

        scheduler.advance(GRACE * 10);

        assertTrue(observer.isAppInForeground());
        assertTrue(session.calls.isEmpty());
        assertEquals(0, expired);
    }

    @Test
    public void processBackAndForthWithinGrace_noFail() {
        FocusBackgroundObserver observer = new FocusBackgroundObserver();
        SessionLike session = new SessionLike();
        observer.addListener(session);

        observer.onStop(OWNER);
        scheduler.advance(GRACE - 500);
        observer.onStart(OWNER);
        scheduler.advance(GRACE * 2);

        assertTrue(observer.isAppInForeground());
        assertEquals(0, expired);
        assertEquals(2, session.calls.size());
    }

    @Test
    public void removedListener_isNotCalled() {
        FocusBackgroundObserver observer = new FocusBackgroundObserver();
        SessionLike session = new SessionLike();
        observer.addListener(session);
        observer.removeListener(session);

        observer.onStop(OWNER);
        scheduler.advance(GRACE * 2);

        assertTrue(session.calls.isEmpty());
        assertEquals(0, expired);
    }

    @Test
    public void listenerRemovingItselfDuringDispatch_isSafe() {
        FocusBackgroundObserver observer = new FocusBackgroundObserver();
        List<String> calls = new ArrayList<>();
        FocusBackgroundObserver.Listener selfRemoving = new FocusBackgroundObserver.Listener() {
            @Override
            public void onAppForegrounded() {
            }

            @Override
            public void onAppBackgrounded() {
                calls.add("bg");
                observer.removeListener(this);
            }
        };
        observer.addListener(selfRemoving);
        observer.addListener(new SessionLike());

        observer.onStop(OWNER);
        observer.onStop(OWNER);

        assertEquals(1, calls.size());
    }

    @Test
    public void pausedSessionInBackground_noFail() {
        FocusBackgroundObserver observer = new FocusBackgroundObserver();
        SessionLike session = new SessionLike();
        session.concentrationRunning = false;
        observer.addListener(session);

        observer.onStop(OWNER);
        scheduler.advance(GRACE * 2);

        assertEquals(0, expired);
    }
}
