package com.example.bbettercalendar.ui.focus;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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

import com.example.bbettercalendar.blocking.AccessibilityAccess;
import com.example.bbettercalendar.blocking.BlockingSettings;
import com.example.bbettercalendar.blocking.FocusBlockState;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.notifications.FocusRunningFlag;
import com.example.bbettercalendar.notifications.focus.FocusCompleteNotifier;
import com.example.bbettercalendar.notifications.focus.FocusFailNotifier;
import com.example.bbettercalendar.notifications.focus.FocusOngoingNotification;
import com.example.bbettercalendar.notifications.focus.FocusPhaseAlarm;
import com.example.bbettercalendar.notifications.focus.FocusPhaseEndNotifier;
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
        FocusBackgroundObserver.Listener, FocusPhaseAlarm.LiveSession {

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
    private static final String KEY_ARMED_BEFORE_TASK = "bb_focus_armed_before_task";
    private static final String KEY_FOCUS_PAUSES = "bb_focus_pause_count";
    // Rediseño de Proyectos (u4): bloques del plan de una tarea y la tarea ligada (FocusTarget).
    private static final String KEY_PLAN_BLOCKS = "bb_focus_plan_blocks";
    private static final String KEY_PLAN_INDEX = "bb_focus_plan_index";
    private static final String KEY_TARGET = "bb_focus_target";
    private static final String TARGET_ENTRY = "entryId";
    private static final String TARGET_TITLE = "title";
    private static final String TARGET_GOAL = "goal";
    private static final String TARGET_ATTRIBUTED = "attributed";
    private static final String TARGET_BLOCK = "blockOnFocus";
    private static final String TARGET_PROJECT = "projectId";
    private static final String TARGET_PROJECT_NAME = "projectName";
    private static final String TARGET_PROJECT_ICON = "projectIconKey";
    private static final String TARGET_PROJECT_COLOR = "projectColorIndex";
    private static final long MINUTE_MILLIS = 60_000L;

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
    // Rediseño de Proyectos (D-P12, decisión d3): una tarea con «Bloqueo al enfocar» empezó sin el
    // permiso de accesibilidad; la pantalla Focus enseña una vez la explicación existente.
    private final MutableLiveData<SingleEvent<Boolean>> blockUnavailable = new MutableLiveData<>();

    // Rediseño de Proyectos (§3.5, D-P12): arranca las sesiones de tarea y es el dueño del 🚫 (el que
    // armó una tarea vuelve a como estaba al acabar su sesión). Java puro, con tests JVM.
    private final TaskFocusStarter starter;
    // Arranque diferido de «Focus this» de Home mientras se leen los minutos de la tarea: cualquier
    // otro arranque o cancelación lo invalida.
    private int startToken;
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
        this.starter = new TaskFocusStarter(machine, this::currentSettings,
                new TaskFocusStarter.BlockAccess() {
                    @Override
                    public boolean isAvailable() {
                        return AccessibilityAccess.isEnabled(appContext);
                    }

                    @Override
                    public void onUnavailable() {
                        blockUnavailable.setValue(new SingleEvent<>(Boolean.TRUE));
                    }
                });
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
        // Plan sistema-notificaciones (B.1): la alarma de fin de fase hace avanzar ESTA sesión.
        FocusPhaseAlarm.attach(this);
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

    /**
     * One-shot (rediseño de Proyectos, D-P12): a task with «Bloqueo al enfocar» started while the
     * accessibility service is off, so the session runs unarmed; Focus shows the explanation once.
     */
    public LiveData<SingleEvent<Boolean>> getBlockUnavailableEvents() {
        return blockUnavailable;
    }

    /**
     * Rediseño de Proyectos (§3.5): enfoca una tarea. Sólo con la sesión parada: liga
     * {@link FocusTarget} a la tarea y arranca sus ciclos (D-P11, {@link TaskCyclePlan} con la
     * configuración del temporizador) desde lo que ya lleva; sin meta, o con la meta ya alcanzada, una
     * sesión de la duración configurada ligada a ella. Con «Bloqueo al enfocar» arma el bloqueo si el
     * servicio de accesibilidad está activo; si no, empieza sin él y emite
     * {@link #getBlockUnavailableEvents()}.
     *
     * @return false, sin cambiar nada, si ya hay una sesión en marcha
     */
    public boolean startTaskFocus(@NonNull TaskFocusRequest request) {
        // TaskFocusStarter: con la sesión en marcha devuelve false sin tocar nada.
        if (!starter.startTaskFocus(request)) {
            return false;
        }
        startToken++; // invalida un «Focus this» de Home aún leyendo minutos
        onTransition();
        return true;
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
     * "Focus this": starts a session bound to {@link FocusTarget} if idle. A target with a goal runs
     * its cycles from the minutes it already has (rediseño de Proyectos, D-P11): when FocusTarget
     * does not carry them (the old {@code set(id, title, targetMinutes)}), they are read off the main
     * thread and the session starts as soon as they arrive (tens of ms; returns true meanwhile).
     * Without a goal, a concentration of the configured length. A running session is not touched
     * (it is attributed to the target when it completes).
     */
    public boolean startBoundFocusIfIdle() {
        if (machine.getState() != State.STOPPED) {
            return false;
        }
        int entryId = FocusTarget.getEntryId();
        int goal = FocusTarget.getTargetMinutes();
        boolean blockOnFocus = FocusTarget.isBlockOnFocus();
        if (entryId == 0 || goal <= 0 || FocusTarget.isAttributedMinutesKnown()) {
            return beginTaskSession(goal, FocusTarget.getAttributedMinutes(), blockOnFocus);
        }
        int token = ++startToken;
        repository.loadAttributedMinutes(entryId, attributed -> handler.post(() -> {
            // Nada cambió entretanto: ni otro arranque, ni cancelar, ni otra tarea ligada.
            if (token != startToken || machine.getState() != State.STOPPED
                    || FocusTarget.getEntryId() != entryId) {
                return;
            }
            beginTaskSession(goal, attributed, blockOnFocus);
        }));
        return true;
    }

    /**
     * Arranca la sesión de la tarea ya ligada en {@link FocusTarget}: sus ciclos si le falta meta, si
     * no una concentración de la duración configurada; arma el bloqueo si la tarea lo pide.
     */
    private boolean beginTaskSession(int goalMinutes, int attributedMinutes, boolean blockOnFocus) {
        startToken++;
        boolean started = starter.begin(goalMinutes, attributedMinutes, blockOnFocus);
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
     * Stop button ("end early"): drops the whole session (cycles reset) and unbinds
     * {@link FocusTarget}. Not a failure. For a session bound to a task, the whole minutes focused
     * in the current block are kept for that task (rediseño de Proyectos §3.5, decisión d4) and,
     * if the task armed the block mode, the 🚫 goes back to how it was. @return false if there was
     * nothing to cancel.
     */
    public boolean cancelSession() {
        int entryId = FocusTarget.getEntryId();
        long focusedMillis = machine.getCurrentFocusElapsedMillis();
        int pauses = machine.getFocusPauseCount();
        startToken++;
        if (!machine.cancel()) {
            return false;
        }
        repository.recordFocusEndedEarly(entryId, (int) (focusedMillis / MINUTE_MILLIS), pauses);
        // Desvincular: dejar el banner "Focusing: X" colgado sin sesión viva confunde sobre si la
        // siguiente cuenta atrás se atribuiría sola a esa tarea.
        FocusTarget.clear();
        starter.onTaskSessionEnded();
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
        starter.setBlockModeArmedByUser(armed);
        onTransition();
    }

    public boolean isBlockModeArmed() {
        return starter.isBlockModeArmed();
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
        FocusBlockState.setActive(appContext,
                starter.isBlockModeArmed() && machine.getState() == State.RUNNING);
    }

    // ===================================================== background grace (FocusBackgroundObserver)

    /**
     * The whole APP went to background (process ON_STOP, u14). Mirrors the old
     * {@code onActivityStopped}: only a running concentration without block mode arms the grace
     * period — with block mode on the accessibility cover already keeps the user from leaving.
     */
    @Override
    public void onAppBackgrounded() {
        if (grace.onBackgrounded(machine.shouldArmBackgroundGrace(starter.isBlockModeArmed()))) {
            Log.i(TAG, "App enters background: grace period armed");
        }
    }

    /** The app is visible again: disarm a pending grace period. */
    @Override
    public void onAppForegrounded() {
        grace.onForegrounded();
        // B.1: el aviso de fin de fase sólo tiene sentido con la app fuera; al volver, se retira.
        FocusPhaseEndNotifier.cancel(appContext);
    }

    // ============================================================ FocusPhaseAlarm.LiveSession

    /**
     * B.1: sonó la alarma de fin de fase (FocusPhaseEndReceiver, hilo principal). Con la pantalla
     * apagada el tick del Handler va con retraso (uptimeMillis no avanza dormido); la alarma despierta
     * el móvil y la transición ocurre aquí, a su hora. Si el reloj de pared se movió y la fase aún no
     * acabó (o ya no corre), sólo se vuelve a sincronizar la alarma.
     */
    @Override
    public void onPhaseAlarm() {
        if (!machine.getState().isRunning() || !advanceAndNotify()) {
            syncPhaseAlarm();
        }
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
        // v15 (mejora-visual u1): minutos corridos y pausas de esta concentración, de la máquina.
        repository.recordFocusCompleted((int) sessionMillis, FocusTarget.getEntryId(),
                FormatHelper.millisToMinutes((int) machine.getLastFocusElapsedMillis()),
                machine.getFocusPauseCount());
        focusCompleted.setValue(new SingleEvent<>(sessionMillis));
    }

    @Override
    public void onAllCyclesCompleted(int cycles) {
        starter.onTaskSessionEnded(); // lo aplica el onTransition() que sigue a advance()
        cycleCompleted.setValue(new SingleEvent<>(cycles));
    }

    @Override
    public void onFailed() {
        Log.i(TAG, "Timer failed (background grace expired)");
        repository.recordFail(FocusTarget.getEntryId(),
                FormatHelper.millisToMinutes((int) machine.getLastFocusElapsedMillis()),
                machine.getFocusPauseCount());
        focusFailNotifier.fire();
        sessionFailed.setValue(new SingleEvent<>(Boolean.TRUE));
    }

    // =============================================================================== internals

    private void onTick() {
        if (!machine.getState().isRunning()) {
            return;
        }
        if (advanceAndNotify()) {
            return;
        }
        publish();
        scheduleTick();
    }

    /**
     * Fin de la cuenta atrás en curso, venga del tick o de la alarma (B.1). Con la app fuera publica el
     * aviso de la fase que acaba de terminar: el plan se calcula ANTES de avanzar (es el mismo que
     * llevaba la alarma), y como quien llegue segundo ya no encuentra nada que avanzar, sale una vez.
     *
     * @return true si hubo transición
     */
    private boolean advanceAndNotify() {
        FocusPhaseAlarm.Plan ending = currentPhasePlan();
        if (!machine.advance()) {
            return false;
        }
        onTransition();
        if (!FocusPhaseAlarm.isAppInForeground()) {
            FocusPhaseEndNotifier.post(appContext, ending);
        }
        return true;
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
        syncRunningFlag();
        syncPhaseAlarm();
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
     * B.3: {@link FocusRunningFlag} (lo leen los avisos de amigos y los diarios) dice «concentración
     * corriendo» sólo en RUNNING, con su fin de reloj; cualquier otro estado lo borra.
     */
    private void syncRunningFlag() {
        if (machine.getState() == State.RUNNING) {
            FocusRunningFlag.setRunning(appContext,
                    System.currentTimeMillis() + machine.getRemainingMillis());
        } else {
            FocusRunningFlag.clear(appContext);
        }
    }

    /**
     * B.1: una alarma exacta al fin de la fase que corre, ninguna en cualquier otro estado (pausa,
     * parada, cancelación, saltar descanso, fallo, fin). Con la app a la vista, además, se retira un
     * aviso de fin de fase que siguiera ahí.
     */
    private void syncPhaseAlarm() {
        FocusPhaseAlarm.sync(appContext, currentPhasePlan());
        if (FocusPhaseAlarm.isAppInForeground()) {
            FocusPhaseEndNotifier.cancel(appContext);
        }
    }

    private FocusPhaseAlarm.Plan currentPhasePlan() {
        Settings settings = currentSettings();
        if (machine.hasPlan()) {
            // Plan de una tarea: tras un bloque hay descanso sólo si queda otro bloque, y tras un
            // descanso siempre empieza otro (el siguiente, con su duración). Unos ajustes de un
            // ciclo que lo describen, para que FocusPhaseAlarm.plan siga siendo quien decide el aviso.
            boolean next = machine.planHasNextBlock();
            long nextBlock = machine.getState().isRest()
                    ? planBlockAfterRest() : settings.focusMillis;
            Settings planSettings = new Settings(nextBlock, settings.restMillis,
                    settings.restEnabled && next, 1, machine.getState().isRest() || next,
                    settings.autoCycle);
            return FocusPhaseAlarm.plan(machine.getState(), machine.getRemainingMillis(),
                    System.currentTimeMillis(), planSettings, 0);
        }
        return FocusPhaseAlarm.plan(machine.getState(), machine.getRemainingMillis(),
                System.currentTimeMillis(), settings, machine.getCyclesCompleted());
    }

    /** Durante un descanso del plan: la duración del bloque que lo sigue. */
    private long planBlockAfterRest() {
        long[] blocks = machine.snapshot().planBlocksMillis;
        int next = Math.min(machine.getPlanBlockIndex() + 1, blocks.length - 1);
        return blocks[next];
    }

    /**
     * Arranca {@link HomeForegroundService} cuando empieza una sesión y lo para cuando vuelve a
     * STOPPED (fin de ciclos, cancelación, fallo). Las transiciones a "activa" sólo ocurren por
     * un toque del usuario o al restaurar la Activity, siempre en primer plano; aun así se
     * captura el rechazo del sistema (arranque de FGS desde segundo plano, API 31+).
     */
    private void syncForegroundService() {
        boolean active = machine.getState() != State.STOPPED;
        // B.2: fase + cuenta atrás en la notificación del servicio. Con el servicio ya en marcha se
        // vuelve a publicar el mismo id (nada de startForegroundService desde segundo plano).
        FocusOngoingNotification.setLatest(active ? FocusOngoingNotification.Content.forState(
                machine.getState(), machine.getRemainingMillis(), System.currentTimeMillis()) : null);
        if (active == foregroundServiceOn) {
            if (active) {
                FocusOngoingNotification.repost(appContext);
            }
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
            FocusOngoingNotification.cancel(appContext);
            foregroundServiceOn = false;
        }
    }

    private void publish() {
        Settings settings = currentSettings();
        long remaining = machine.getRemainingMillis();
        long endsAtWall = machine.getState().isRunning()
                ? System.currentTimeMillis() + remaining : 0L;
        boolean plan = machine.hasPlan();
        uiState.setValue(new FocusUiState(
                machine.getState(),
                remaining,
                machine.getCountdownMillis(),
                endsAtWall,
                // Plan de una tarea: «x de N» son sus bloques, no los ciclos configurados.
                plan ? machine.getPlanBlockIndex() + 1 : machine.getCurrentCycle(),
                plan ? machine.getPlanBlockCount() : settings.cycles,
                !plan && settings.infiniteCycles,
                machine.getCyclesCompleted(),
                starter.isBlockModeArmed(),
                plan));
    }

    private void saveState() {
        Snapshot s = machine.snapshot();
        savedState.set(KEY_STATE, s.state.name());
        savedState.set(KEY_REMAINING, s.remainingMillis);
        savedState.set(KEY_ENDS_AT, s.endsAtMillis);
        savedState.set(KEY_COUNTDOWN, s.countdownMillis);
        savedState.set(KEY_SESSION, s.sessionMillis);
        savedState.set(KEY_CYCLES, s.cyclesCompleted);
        savedState.set(KEY_FOCUS_PAUSES, s.focusPauseCount);
        savedState.set(KEY_BLOCK_ARMED, starter.isBlockModeArmed());
        savedState.set(KEY_ARMED_BEFORE_TASK, starter.getArmedBeforeTask());
        savedState.set(KEY_PLAN_BLOCKS, s.planBlocksMillis);
        savedState.set(KEY_PLAN_INDEX, s.planBlockIndex);
        boolean live = s.state != State.STOPPED || s.planBlocksMillis != null;
        savedState.set(KEY_TARGET, live ? targetBundle(FocusTarget.toRequest()) : null);
    }

    /** La tarea ligada a una sesión viva, para devolverla a FocusTarget tras la muerte del proceso. */
    private static Bundle targetBundle(TaskFocusRequest r) {
        if (r == null) {
            return null;
        }
        Bundle b = new Bundle();
        b.putInt(TARGET_ENTRY, r.getEntryId());
        b.putString(TARGET_TITLE, r.getTitle());
        b.putInt(TARGET_GOAL, r.getGoalMinutes());
        b.putInt(TARGET_ATTRIBUTED, r.getAttributedMinutes());
        b.putBoolean(TARGET_BLOCK, r.isBlockOnFocus());
        b.putInt(TARGET_PROJECT, r.getProjectId());
        b.putString(TARGET_PROJECT_NAME, r.getProjectName());
        b.putString(TARGET_PROJECT_ICON, r.getProjectIconKey());
        b.putInt(TARGET_PROJECT_COLOR, r.getProjectColorIndex());
        return b;
    }

    /** Tras la muerte del proceso FocusTarget está vacío: se le devuelve la tarea de la sesión. */
    private void restoreTarget() {
        Bundle b = savedState.get(KEY_TARGET);
        if (b == null || FocusTarget.isSet() || b.getInt(TARGET_ENTRY) == 0) {
            return;
        }
        FocusTarget.set(new TaskFocusRequest(b.getInt(TARGET_ENTRY), b.getString(TARGET_TITLE),
                b.getInt(TARGET_GOAL), b.getInt(TARGET_ATTRIBUTED), b.getBoolean(TARGET_BLOCK),
                b.getInt(TARGET_PROJECT), b.getString(TARGET_PROJECT_NAME),
                b.getString(TARGET_PROJECT_ICON), b.getInt(TARGET_PROJECT_COLOR)));
        FocusTarget.consumePendingAutoStart(); // la sesión ya existe: nada que arrancar en Home
    }

    private void restoreFromSavedState() {
        String stateName = savedState.get(KEY_STATE);
        Boolean armed = savedState.get(KEY_BLOCK_ARMED);
        // u19: sin estado guardado (sesion nueva) arranca como diga "Focus shield" de Screen time.
        starter.restoreBlockModeArmed(
                armed != null ? armed : BlockingSettings.isFocusShieldEnabled(appContext));
        starter.restoreArmedBeforeTask(savedState.<Boolean>get(KEY_ARMED_BEFORE_TASK));
        if (stateName != null) {
            try {
                machine.restore(new Snapshot(
                        State.valueOf(stateName),
                        longOr(KEY_REMAINING, machine.getRemainingMillis()),
                        longOr(KEY_ENDS_AT, 0L),
                        longOr(KEY_COUNTDOWN, machine.getCountdownMillis()),
                        longOr(KEY_SESSION, machine.getSessionMillis()),
                        intOr(KEY_CYCLES, 0),
                        intOr(KEY_FOCUS_PAUSES, 0),
                        savedState.<long[]>get(KEY_PLAN_BLOCKS),
                        intOr(KEY_PLAN_INDEX, 0)));
                // Antes del advance() de abajo: un bloque que acabó con el proceso muerto se
                // atribuye a su tarea.
                restoreTarget();
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
        // B.1 / B.3: sin timer vivo no hay fin de fase que avisar ni concentración en marcha.
        FocusPhaseAlarm.detach(this);
        FocusPhaseAlarm.sync(appContext, FocusPhaseAlarm.Plan.NONE);
        FocusRunningFlag.clear(appContext);
        FocusOngoingNotification.setLatest(null);
        // The ticker dies with the ViewModel, but leaving the app with back mid-session (activity
        // finishing) still counts as a fail, as before. The process ON_STOP arrives ~700 ms AFTER
        // this (and nobody listens any more), so the grace is armed here with the same rule; one
        // that was already pending is simply left to fire.
        if (!grace.isArmed()) {
            grace.onBackgrounded(machine.shouldArmBackgroundGrace(starter.isBlockModeArmed()));
        }
        handler.removeCallbacks(tick);
        if (foregroundServiceOn) {
            appContext.stopService(new Intent(appContext, HomeForegroundService.class));
            FocusOngoingNotification.cancel(appContext);
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
