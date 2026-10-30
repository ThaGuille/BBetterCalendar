package com.example.bbettercalendar.ui.focus;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.blocking.BlockingSettings;
import com.example.bbettercalendar.blocking.FocusBlockState;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.notifications.focus.FocusCompleteNotifier;
import com.example.bbettercalendar.notifications.focus.FocusFailNotifier;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.presence.PresencePublisher;
import com.example.bbettercalendar.stats.FocusAttributionRepository;
import com.example.bbettercalendar.stats.FocusSessionRepository;
import com.example.bbettercalendar.ui.common.SingleEvent;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Settings;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Snapshot;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;
import com.example.bbettercalendar.ui.home.FocusSessionConstants;
import com.example.bbettercalendar.ui.home.FocusTarget;
import com.example.bbettercalendar.ui.home.HomeForegroundService;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * The single Pomodoro session of the app (migración visual, u2). <b>Activity-scoped</b>: get it
 * with {@code new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class)} so Home
 * and the Focus screen share one timer. Owns the {@link FocusTimerStateMachine}, its 1 s ticker,
 * the 🚫 block-mode flag, the background-fail grace period and the {@link SavedStateHandle}
 * persistence (end instant, not ticks). All public methods: main thread only.
 *
 * <p>The grace period ({@link BackgroundGrace}) is fed by {@link FocusBackgroundObserver}, a
 * <b>process-level</b> observer (u14): only the whole app going to background counts, never
 * in-app navigation or opening another of the app's activities.
 */
@HiltViewModel
public class FocusSessionViewModel extends ViewModel implements FocusTimerStateMachine.Listener,
        FocusBackgroundObserver.Listener {

    private static final String TAG = "FocusSessionViewModel";
    private static final long TICK_MILLIS = 1000L;

    // SavedStateHandle keys (survive process death). The countdown is stored as its end instant
    // on SystemClock.elapsedRealtime(), never as accumulated ticks.
    private static final String KEY_STATE = "bb_focus_state";
    private static final String KEY_REMAINING = "bb_focus_remaining_millis";
    private static final String KEY_ENDS_AT = "bb_focus_ends_at_elapsed";
    private static final String KEY_COUNTDOWN = "bb_focus_countdown_millis";
    private static final String KEY_SESSION = "bb_focus_session_millis";
    private static final String KEY_CYCLES = "bb_focus_cycles_completed";
    private static final String KEY_BLOCK_ARMED = "bb_focus_block_mode_armed";

    private final SavedStateHandle savedState;
    private final ConfigurationManager configurationManager;
    private final FocusSessionRepository repository;
    private final FocusFailNotifier focusFailNotifier;
    private final FocusAttributionRepository focusAttributionRepository;
    private final FocusCompleteNotifier focusCompleteNotifier;
    private final Context appContext;
    // F4-u3: presencia en directo (03 §8). Lazy y tras el flag: en release nunca se crea ni toca Firebase.
    private final Lazy<PresencePublisher> presence;
    private final FocusTimerStateMachine machine;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<FocusUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<SingleEvent<Long>> focusCompleted = new MutableLiveData<>();
    private final MutableLiveData<SingleEvent<Integer>> cycleCompleted = new MutableLiveData<>();
    private final MutableLiveData<SingleEvent<Boolean>> sessionFailed = new MutableLiveData<>();

    private boolean blockModeArmed;
    private final BackgroundGrace grace;
    private final FocusBackgroundObserver backgroundObserver;
    // u13: el servicio en primer plano sigue a la SESIÓN (≠ STOPPED), no a la pantalla visible.
    // Antes lo arrancaba/paraba HomeFragment en onStart/onStop; Home ya no está a la vista
    // mientras corre la sesión (Play navega a Focus).
    private boolean foregroundServiceOn;

    private final Runnable tick = this::onTick;

    @Inject
    public FocusSessionViewModel(SavedStateHandle savedState,
                                 ConfigurationManager configurationManager,
                                 FocusSessionRepository repository,
                                 FocusFailNotifier focusFailNotifier,
                                 FocusAttributionRepository focusAttributionRepository,
                                 FocusCompleteNotifier focusCompleteNotifier,
                                 @ApplicationContext Context appContext,
                                 Lazy<PresencePublisher> presence) {
        this.savedState = savedState;
        this.presence = presence;
        this.configurationManager = configurationManager;
        this.repository = repository;
        this.focusFailNotifier = focusFailNotifier;
        this.focusAttributionRepository = focusAttributionRepository;
        this.focusCompleteNotifier = focusCompleteNotifier;
        this.appContext = appContext;
        this.machine = new FocusTimerStateMachine(
                SystemClock::elapsedRealtime, this::currentSettings, this);
        this.grace = new BackgroundGrace(new BackgroundGrace.Scheduler() {
            @Override
            public void postDelayed(@NonNull Runnable task, long delayMillis) {
                handler.postDelayed(task, delayMillis);
            }

            @Override
            public void cancel(@NonNull Runnable task) {
                handler.removeCallbacks(task);
            }
        }, FocusSessionConstants.FAIL_GRACE_MILLIS, this::onGraceExpired);
        repository.refreshToday();
        restoreFromSavedState();
        // u14: gracia a nivel de PROCESO (ver FocusBackgroundObserver): sólo cuenta que la app
        // entera pase a segundo plano, no navegar dentro de ella ni abrir otra de sus Activities.
        backgroundObserver = FocusBackgroundObserver.get();
        backgroundObserver.addListener(this);
    }

    // ============================================================================ public API

    /** Timer render state; emits every second while running and on every transition. */
    public LiveData<FocusUiState> getUiState() {
        return uiState;
    }

    /** One-shot: a concentration countdown completed (payload = its length in millis). */
    public LiveData<SingleEvent<Long>> getFocusCompletedEvents() {
        return focusCompleted;
    }

    /** One-shot: all N configured cycles are done (payload = cycles). Old "cycle finished" popup. */
    public LiveData<SingleEvent<Integer>> getCycleCompletedEvents() {
        return cycleCompleted;
    }

    /** One-shot: the session failed because the app stayed in background past the grace period. */
    public LiveData<SingleEvent<Boolean>> getSessionFailedEvents() {
        return sessionFailed;
    }

    /** Today's fail count (Focus screen). */
    public LiveData<Integer> getTodayFails() {
        return repository.getTodayFails();
    }

    /** Today's studied time in millis (Focus "Today" strip); null until the first read. */
    public LiveData<Integer> getTodayTimeStudiedMillis() {
        return repository.getTodayTimeStudiedMillis();
    }

    /** Days with a pomodoro this month, emitted only when today's first one completes. */
    public LiveData<Integer> getFirstFocusOfDay() {
        return repository.getFirstFocusOfDay();
    }

    /** Main thread: after showing the first-focus celebration. */
    public void clearFirstFocusOfDay() {
        repository.clearFirstFocusOfDay();
    }

    /** Title of a bound task that just reached its target (null when nothing is pending). */
    public LiveData<String> getAutoCompletedTaskTitle() {
        return focusAttributionRepository.getAutoCompletedTaskTitle();
    }

    /**
     * The screen gave the in-app feedback for an auto-completed task: fire the fallback
     * notification, unbind {@link FocusTarget} and clear the pending title (the body of the old
     * HomeFragment observer).
     */
    public void onAutoCompletedTaskShown(@NonNull String title) {
        focusCompleteNotifier.fire(title);
        FocusTarget.clear();
        focusAttributionRepository.clearAutoCompletedTaskTitle();
    }

    /** Play/pause button. @return true if running afterwards (play the start sound), else paused. */
    public boolean toggle() {
        boolean running = machine.toggle();
        onTransition();
        return running;
    }

    /** Starts a concentration of the configured length. No-op unless idle. */
    public boolean start() {
        boolean started = machine.startFocus(currentSettings().focusMillis);
        onTransition();
        return started;
    }

    /**
     * "Focus this": starts a concentration bound to {@link FocusTarget} if idle — its length is
     * the target's minutes when it has one, else the configured length. A running session is not
     * touched (it is attributed to the target when it completes).
     */
    public boolean startBoundFocusIfIdle() {
        if (machine.getState() != State.STOPPED) {
            return false;
        }
        int targetMinutes = FocusTarget.getTargetMinutes();
        long duration = targetMinutes > 0
                ? FormatHelper.minutesToMillis(targetMinutes)
                : currentSettings().focusMillis;
        boolean started = machine.startFocus(duration);
        onTransition();
        return started;
    }

    public boolean pause() {
        boolean paused = machine.pause();
        onTransition();
        return paused;
    }

    public boolean resume() {
        boolean resumed = machine.resume();
        onTransition();
        return resumed;
    }

    /**
     * Stop button: drops the whole session (cycles reset) and unbinds {@link FocusTarget}. Not a
     * failure. @return false if there was nothing to cancel.
     */
    public boolean cancelSession() {
        if (!machine.cancel()) {
            return false;
        }
        // Desvincular: dejar el banner "Focusing: X" colgado sin sesión viva confunde sobre si la
        // siguiente cuenta atrás se atribuiría sola a esa tarea.
        FocusTarget.clear();
        onTransition();
        return true;
    }

    /** Skip rest: only in rest states; the cycle still counts. */
    public boolean skipRest() {
        boolean skipped = machine.skipRest();
        onTransition();
        return skipped;
    }

    /** Arms/disarms the 🚫 total-block mode (the caller checks the accessibility grant). */
    public void setBlockModeArmed(boolean armed) {
        blockModeArmed = armed;
        onTransition();
    }

    public boolean isBlockModeArmed() {
        return blockModeArmed;
    }

    /** Old fail-alert close -> resetTimer(): show the configured length again when idle. */
    public void resetIdleDisplay() {
        machine.resetIdleDisplay();
        onTransition();
    }

    /**
     * Timer settings popup closed with a changed {@link Configuration} (old
     * {@code checkConfigChanges}). Persisting it is still the caller's job.
     */
    public void onConfigurationChanged(Configuration config) {
        machine.onSettingsChanged(toSettings(config));
        onTransition();
    }

    /**
     * Re-pushes the live {@link FocusBlockState} flag. Call from onResume: the accessibility
     * service clears it on (re)connect, so a running armed session must re-assert it.
     */
    public void syncBlockState() {
        FocusBlockState.setActive(appContext, blockModeArmed && machine.getState() == State.RUNNING);
    }

    // ===================================================== background grace (FocusBackgroundObserver)

    /**
     * The whole APP went to background (process ON_STOP, u14). Mirrors the old
     * {@code onActivityStopped}: only a running concentration without block mode arms the grace
     * period — with block mode on the accessibility cover already keeps the user from leaving.
     */
    @Override
    public void onAppBackgrounded() {
        if (grace.onBackgrounded(machine.shouldArmBackgroundGrace(blockModeArmed))) {
            Log.i(TAG, "App enters background: grace period armed");
        }
    }

    /** The app is visible again: disarm a pending grace period. */
    @Override
    public void onAppForegrounded() {
        grace.onForegrounded();
    }

    private void onGraceExpired() {
        if (machine.fail()) {
            onTransition();
        }
    }

    // ============================================================ FocusTimerStateMachine.Listener

    @Override
    public void onFocusCompleted(long sessionMillis) {
        // Atribuye la sesión a la tarea vinculada (0 = genérica). El auto-completado, si procede,
        // lo evalúa FocusAttributionRepository y avisa por getAutoCompletedTaskTitle().
        repository.recordFocusCompleted((int) sessionMillis, FocusTarget.getEntryId());
        focusCompleted.setValue(new SingleEvent<>(sessionMillis));
    }

    @Override
    public void onAllCyclesCompleted(int cycles) {
        cycleCompleted.setValue(new SingleEvent<>(cycles));
    }

    @Override
    public void onFailed() {
        Log.i(TAG, "Timer failed (background grace expired)");
        repository.recordFail(FocusTarget.getEntryId());
        focusFailNotifier.fire();
        sessionFailed.setValue(new SingleEvent<>(Boolean.TRUE));
    }

    // =============================================================================== internals

    private void onTick() {
        if (!machine.getState().isRunning()) {
            return;
        }
        if (machine.advance()) {
            onTransition();
            return;
        }
        publish();
        scheduleTick();
    }

    /** Next tick right after the displayed second changes (display = floor of remaining). */
    private void scheduleTick() {
        handler.removeCallbacks(tick);
        if (!machine.getState().isRunning()) {
            return;
        }
        long remaining = machine.getRemainingMillis();
        long delay = remaining <= 0 ? 0 : (remaining % TICK_MILLIS) + 1;
        handler.postDelayed(tick, Math.min(delay, TICK_MILLIS));
    }

    /** After any transition: persist, push the block flag, publish and (re)arm the ticker. */
    private void onTransition() {
        saveState();
        syncBlockState();
        syncForegroundService();
        publish();
        publishPresence();
        scheduleTick();
    }

    /**
     * F4-u3: cuenta el estado del timer a los amigos (status/ en RTDB). El publicador ignora lo que no
     * cambia, así que se le llama en cada transición; lo transcurrido de la cuenta atrás fija el inicio de
     * una racha restaurada tras la muerte del proceso.
     */
    private void publishPresence() {
        if (!SocialFeature.isAccountEnabled()) {
            return;
        }
        long elapsed = Math.max(0L, machine.getCountdownMillis() - machine.getRemainingMillis());
        presence.get().onTimerState(machine.getState(), elapsed);
    }

    /**
     * Arranca {@link HomeForegroundService} cuando empieza una sesión y lo para cuando vuelve a
     * STOPPED (fin de ciclos, cancelación, fallo). Las transiciones a "activa" sólo ocurren por
     * un toque del usuario o al restaurar la Activity, siempre en primer plano; aun así se
     * captura el rechazo del sistema (arranque de FGS desde segundo plano, API 31+).
     */
    private void syncForegroundService() {
        boolean active = machine.getState() != State.STOPPED;
        if (active == foregroundServiceOn) {
            return;
        }
        Intent intent = new Intent(appContext, HomeForegroundService.class);
        if (active) {
            try {
                ContextCompat.startForegroundService(appContext, intent);
                foregroundServiceOn = true;
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not start the foreground service", e);
            }
        } else {
            appContext.stopService(intent);
            foregroundServiceOn = false;
        }
    }

    private void publish() {
        Settings settings = currentSettings();
        long remaining = machine.getRemainingMillis();
        long endsAtWall = machine.getState().isRunning()
                ? System.currentTimeMillis() + remaining : 0L;
        uiState.setValue(new FocusUiState(
                machine.getState(),
                remaining,
                machine.getCountdownMillis(),
                endsAtWall,
                machine.getCurrentCycle(),
                settings.cycles,
                settings.infiniteCycles,
                machine.getCyclesCompleted(),
                blockModeArmed));
    }

    private void saveState() {
        Snapshot s = machine.snapshot();
        savedState.set(KEY_STATE, s.state.name());
        savedState.set(KEY_REMAINING, s.remainingMillis);
        savedState.set(KEY_ENDS_AT, s.endsAtMillis);
        savedState.set(KEY_COUNTDOWN, s.countdownMillis);
        savedState.set(KEY_SESSION, s.sessionMillis);
        savedState.set(KEY_CYCLES, s.cyclesCompleted);
        savedState.set(KEY_BLOCK_ARMED, blockModeArmed);
    }

    private void restoreFromSavedState() {
        String stateName = savedState.get(KEY_STATE);
        Boolean armed = savedState.get(KEY_BLOCK_ARMED);
        // u19: sin estado guardado (sesion nueva) arranca como diga "Focus shield" de Screen time.
        blockModeArmed = armed != null ? armed : BlockingSettings.isFocusShieldEnabled(appContext);
        if (stateName != null) {
            try {
                machine.restore(new Snapshot(
                        State.valueOf(stateName),
                        longOr(KEY_REMAINING, machine.getRemainingMillis()),
                        longOr(KEY_ENDS_AT, 0L),
                        longOr(KEY_COUNTDOWN, machine.getCountdownMillis()),
                        longOr(KEY_SESSION, machine.getSessionMillis()),
                        intOr(KEY_CYCLES, 0)));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unknown saved timer state " + stateName, e);
            }
        }
        // A countdown that expired while the process was dead finishes right here.
        if (machine.getState().isRunning() && machine.advance()) {
            Log.i(TAG, "Restored countdown had already expired: finished on restore");
        }
        onTransition();
    }

    private long longOr(String key, long fallback) {
        Long value = savedState.get(key);
        return value != null ? value : fallback;
    }

    private int intOr(String key, int fallback) {
        Integer value = savedState.get(key);
        return value != null ? value : fallback;
    }

    /** u15: configured concentration length (Cycle complete multiplies it by the cycles done). */
    public long getConfiguredFocusMillis() {
        return currentSettings().focusMillis;
    }

    private Settings currentSettings() {
        return toSettings(configurationManager.getConfiguration());
    }

    private static Settings toSettings(@NonNull Configuration c) {
        return new Settings(c.getHomeTimerTime(), c.getHomeRestTime(), c.isHomeIsRestEnabled(),
                c.getHomeNumberOfCycles(), c.isHomeIsInfiniteCycleEnabled(), c.isHomeIsAutoCycle());
    }

    @Override
    protected void onCleared() {
        backgroundObserver.removeListener(this);
        // The ticker dies with the ViewModel, but leaving the app with back mid-session (activity
        // finishing) still counts as a fail, as before. The process ON_STOP arrives ~700 ms AFTER
        // this (and nobody listens any more), so the grace is armed here with the same rule; one
        // that was already pending is simply left to fire.
        if (!grace.isArmed()) {
            grace.onBackgrounded(machine.shouldArmBackgroundGrace(blockModeArmed));
        }
        handler.removeCallbacks(tick);
        if (foregroundServiceOn) {
            appContext.stopService(new Intent(appContext, HomeForegroundService.class));
            foregroundServiceOn = false;
        }
        // Nada podrá volver a apagar el bloqueo total sin un timer vivo (kill-trap): se suelta aquí.
        FocusBlockState.setActive(appContext, false);
        // F4-u3: sin timer vivo los amigos te ven ausente.
        if (SocialFeature.isAccountEnabled()) {
            presence.get().onTimerCleared();
        }
        super.onCleared();
    }
}
