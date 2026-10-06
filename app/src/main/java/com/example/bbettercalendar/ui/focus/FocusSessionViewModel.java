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

import com.example.bbettercalendar.blocking.FocusBlockState;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.notifications.focus.FocusFailNotifier;
import com.example.bbettercalendar.stats.FocusSessionRepository;
import com.example.bbettercalendar.ui.common.SingleEvent;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Settings;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Snapshot;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;
import com.example.bbettercalendar.ui.home.FocusSessionConstants;
import com.example.bbettercalendar.ui.home.FocusTarget;
import com.example.bbettercalendar.ui.home.HomeForegroundService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * The single Pomodoro session of the app (migración visual, u2). <b>Activity-scoped</b>: get it
 * with {@code new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class)} so Home
 * and the Focus screen share one timer. Owns the {@link FocusTimerStateMachine}, its 1 s ticker,
 * the 🚫 block-mode flag, the background-fail grace period and the {@link SavedStateHandle}
 * persistence (end instant, not ticks). All public methods: main thread only.
 *
 * <p>The grace period is fed by {@link FocusBackgroundObserver}, which {@code MainActivity}
 * registers on its own lifecycle.
 */
@HiltViewModel
public class FocusSessionViewModel extends ViewModel implements FocusTimerStateMachine.Listener {

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
    private final Context appContext;
    private final FocusTimerStateMachine machine;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<FocusUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<SingleEvent<Long>> focusCompleted = new MutableLiveData<>();
    private final MutableLiveData<SingleEvent<Integer>> cycleCompleted = new MutableLiveData<>();
    private final MutableLiveData<SingleEvent<Boolean>> sessionFailed = new MutableLiveData<>();

    private boolean blockModeArmed;
    // Old HomeFragment.isBackground: true between "app went to background with a running,
    // unarmed concentration" and either the return to foreground or the grace expiring.
    private boolean graceArmed;
    // u13: el servicio en primer plano sigue a la SESIÓN (≠ STOPPED), no a la pantalla visible.
    // Antes lo arrancaba/paraba HomeFragment en onStart/onStop; Home ya no está a la vista
    // mientras corre la sesión (Play navega a Focus).
    private boolean foregroundServiceOn;

    private final Runnable tick = this::onTick;
    private final Runnable graceExpired = this::onGraceExpired;

    @Inject
    public FocusSessionViewModel(SavedStateHandle savedState,
                                 ConfigurationManager configurationManager,
                                 FocusSessionRepository repository,
                                 FocusFailNotifier focusFailNotifier,
                                 @ApplicationContext Context appContext) {
        this.savedState = savedState;
        this.configurationManager = configurationManager;
        this.repository = repository;
        this.focusFailNotifier = focusFailNotifier;
        this.appContext = appContext;
        this.machine = new FocusTimerStateMachine(
                SystemClock::elapsedRealtime, this::currentSettings, this);
        repository.refreshToday();
        restoreFromSavedState();
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

    /** Today's fail count (shared with Home's fails chip). */
    public LiveData<Integer> getTodayFails() {
        return repository.getTodayFails();
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
     * The host activity stopped for real (not a configuration change). Mirrors the old
     * {@code onActivityStopped}: only a running concentration without block mode arms the grace
     * period — with block mode on the accessibility cover already keeps the user from leaving.
     */
    void onAppBackgrounded() {
        if (machine.shouldArmBackgroundGrace(blockModeArmed)) {
            Log.i(TAG, "App enters background: grace period armed");
            graceArmed = true;
            handler.removeCallbacks(graceExpired);
            handler.postDelayed(graceExpired, FocusSessionConstants.FAIL_GRACE_MILLIS);
        }
    }

    /** The host activity started again: disarm a pending grace period. */
    void onAppForegrounded() {
        graceArmed = false;
        handler.removeCallbacks(graceExpired);
    }

    private void onGraceExpired() {
        if (!graceArmed) {
            return;
        }
        graceArmed = false;
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
        scheduleTick();
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
        blockModeArmed = armed != null && armed;
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

    private Settings currentSettings() {
        return toSettings(configurationManager.getConfiguration());
    }

    private static Settings toSettings(@NonNull Configuration c) {
        return new Settings(c.getHomeTimerTime(), c.getHomeRestTime(), c.isHomeIsRestEnabled(),
                c.getHomeNumberOfCycles(), c.isHomeIsInfiniteCycleEnabled(), c.isHomeIsAutoCycle());
    }

    @Override
    protected void onCleared() {
        // The ticker dies with the ViewModel, but a pending grace period is left to fire: leaving
        // the app with back mid-session (activity finishing) still counts as a fail, as before.
        handler.removeCallbacks(tick);
        if (foregroundServiceOn) {
            appContext.stopService(new Intent(appContext, HomeForegroundService.class));
            foregroundServiceOn = false;
        }
        // Nada podrá volver a apagar el bloqueo total sin un timer vivo (kill-trap): se suelta aquí.
        FocusBlockState.setActive(appContext, false);
        super.onCleared();
    }
}
