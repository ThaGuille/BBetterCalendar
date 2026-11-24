package com.example.bbettercalendar.ui.focus;

/**
 * Pure (Android-free) Pomodoro state machine, extracted from {@code HomeFragment} (migración
 * visual, u2). It owns the six timer states, the cycle counter and the countdown arithmetic; it
 * does NOT own a ticker, a Handler, the DB or any UI. {@link FocusSessionViewModel} drives it:
 * it calls {@link #advance()} from its ticker and reacts to {@link Listener} callbacks.
 *
 * <p>The countdown is anchored to an end instant ({@link #getEndsAtMillis()}) on the injected
 * {@link Clock}, never to accumulated ticks: remaining time is always {@code endsAt - now}. That
 * is what lets the ViewModel persist only the end instant and restore a running session after a
 * process death with the real elapsed time.
 *
 * <p>Transitions are a 1:1 port of the old {@code HomeFragment} methods ({@code startTimer},
 * {@code completeTimer}, {@code completeRest}, {@code pauseTimer}, {@code cancelSession},
 * {@code cancelRest}, {@code checkConfigChanges} and the background {@code failTimer}); the
 * comments on each method name the original. Not thread-safe: call it from the main thread only.
 */
public final class FocusTimerStateMachine {

    /** The six states of the old {@code TIMER_*} int constants. */
    public enum State {
        STOPPED, RUNNING, PAUSED,
        STOPPED_REST, RUNNING_REST, PAUSED_REST;

        public boolean isRest() {
            return this == STOPPED_REST || this == RUNNING_REST || this == PAUSED_REST;
        }

        public boolean isRunning() {
            return this == RUNNING || this == RUNNING_REST;
        }

        public boolean isPaused() {
            return this == PAUSED || this == PAUSED_REST;
        }
    }

    /** Which countdown the state belongs to. */
    public enum Phase { FOCUS, REST }

    /** Monotonic time source in millis (prod: {@code SystemClock.elapsedRealtime()}). */
    public interface Clock {
        long now();
    }

    /** Snapshot of the timer part of {@code Configuration}, read at every transition. */
    public static final class Settings {
        public final long focusMillis;
        public final long restMillis;
        public final boolean restEnabled;
        public final int cycles;
        public final boolean infiniteCycles;
        public final boolean autoCycle;

        public Settings(long focusMillis, long restMillis, boolean restEnabled, int cycles,
                        boolean infiniteCycles, boolean autoCycle) {
            this.focusMillis = focusMillis;
            this.restMillis = restMillis;
            this.restEnabled = restEnabled;
            this.cycles = cycles;
            this.infiniteCycles = infiniteCycles;
            this.autoCycle = autoCycle;
        }
    }

    /** Supplies the CURRENT settings (the user can change them between sessions). */
    public interface SettingsProvider {
        Settings get();
    }

    /** Side effects the old fragment ran inline. All are called synchronously, main thread. */
    public interface Listener {
        /** A concentration countdown reached 0 (old {@code homeViewModel.completeTimer}). */
        void onFocusCompleted(long sessionMillis);

        /** The configured N cycles are done (old "home_cycle_finished" MessagePopup). */
        void onAllCyclesCompleted(int cycles);

        /** Background grace expired mid-concentration (old {@code failTimer}). */
        void onFailed();
    }

    /** Everything needed to rebuild the machine after a process death. */
    public static final class Snapshot {
        public final State state;
        /** Frozen remaining time; meaningful only when not running. */
        public final long remainingMillis;
        /** End instant on the {@link Clock}; meaningful only when running. */
        public final long endsAtMillis;
        /** Length of the current countdown (progress-ring denominator). */
        public final long countdownMillis;
        /** Length recorded in stats when the concentration completes (old {@code lastTimerTime}). */
        public final long sessionMillis;
        public final int cyclesCompleted;
        /** Pauses of the current concentration so far (mejora-visual u1, FocusEvent.pauseCount). */
        public final int focusPauseCount;
        /** Focus blocks of a running task plan (rediseño de Proyectos, D-P11); null = plain session. */
        public final long[] planBlocksMillis;
        /** Index of the plan block in progress (or next, between blocks). */
        public final int planBlockIndex;

        public Snapshot(State state, long remainingMillis, long endsAtMillis, long countdownMillis,
                        long sessionMillis, int cyclesCompleted) {
            this(state, remainingMillis, endsAtMillis, countdownMillis, sessionMillis, cyclesCompleted, 0);
        }

        public Snapshot(State state, long remainingMillis, long endsAtMillis, long countdownMillis,
                        long sessionMillis, int cyclesCompleted, int focusPauseCount) {
            this(state, remainingMillis, endsAtMillis, countdownMillis, sessionMillis, cyclesCompleted,
                    focusPauseCount, null, 0);
        }

        public Snapshot(State state, long remainingMillis, long endsAtMillis, long countdownMillis,
                        long sessionMillis, int cyclesCompleted, int focusPauseCount,
                        long[] planBlocksMillis, int planBlockIndex) {
            this.state = state;
            this.remainingMillis = remainingMillis;
            this.endsAtMillis = endsAtMillis;
            this.countdownMillis = countdownMillis;
            this.sessionMillis = sessionMillis;
            this.cyclesCompleted = cyclesCompleted;
            this.focusPauseCount = focusPauseCount;
            this.planBlocksMillis = planBlocksMillis == null || planBlocksMillis.length == 0
                    ? null : planBlocksMillis.clone();
            this.planBlockIndex = this.planBlocksMillis == null ? 0
                    : Math.max(0, Math.min(planBlockIndex, this.planBlocksMillis.length - 1));
        }
    }

    private final Clock clock;
    private final SettingsProvider settings;
    private final Listener listener;

    private State state = State.STOPPED;
    private long remainingMillis;
    private long endsAtMillis;
    private long countdownMillis;
    private long sessionMillis;
    private int cyclesCompleted;
    // mejora-visual u1: pausas de la concentración en curso (las del descanso no cuentan) y minutos
    // de cuenta atrás realmente corridos por la última concentración que terminó (completada o
    // fallida). Los lee el Listener dentro de onFocusCompleted / onFailed para FocusEvent v15.
    private int focusPauseCount;
    private long lastFocusElapsedMillis;
    // Rediseño de Proyectos (D-P11): bloques de la sesión de una tarea (TaskCyclePlan). null = sesión
    // normal. planIndex = bloque en curso; entre bloques (descanso o parada sin auto-ciclo) apunta al
    // que acaba de terminar durante el descanso y al siguiente en cuanto el descanso acaba.
    private long[] planBlocks;
    private int planIndex;

    public FocusTimerStateMachine(Clock clock, SettingsProvider settings, Listener listener) {
        this.clock = clock;
        this.settings = settings;
        this.listener = listener;
        long focus = settings.get().focusMillis;
        this.remainingMillis = focus;
        this.countdownMillis = focus;
        this.sessionMillis = focus;
    }

    // --------------------------------------------------------------------------- queries

    public State getState() {
        return state;
    }

    public Phase getPhase() {
        return state.isRest() ? Phase.REST : Phase.FOCUS;
    }

    /** Remaining time of the current countdown, never negative. */
    public long getRemainingMillis() {
        if (state.isRunning()) {
            return Math.max(0L, endsAtMillis - clock.now());
        }
        return remainingMillis;
    }

    /** End instant on the injected clock while running; 0 otherwise. */
    public long getEndsAtMillis() {
        return state.isRunning() ? endsAtMillis : 0L;
    }

    public long getCountdownMillis() {
        return countdownMillis;
    }

    public long getSessionMillis() {
        return sessionMillis;
    }

    public int getCyclesCompleted() {
        return cyclesCompleted;
    }

    /**
     * Pauses (RUNNING -> PAUSED) of the current concentration; rest pauses do not count. Reset when
     * the next concentration begins, so inside {@link Listener#onFocusCompleted} and
     * {@link Listener#onFailed} it is the count of the concentration that just ended.
     */
    public int getFocusPauseCount() {
        return focusPauseCount;
    }

    /**
     * Countdown time the last finished concentration actually ran, pauses excluded: the whole
     * countdown when it completed, the part run before the background fail when it failed. Valid
     * inside {@link Listener#onFocusCompleted} / {@link Listener#onFailed}.
     */
    public long getLastFocusElapsedMillis() {
        return lastFocusElapsedMillis;
    }

    /** 1-based cycle in progress ("Cycle x of N"); a rest belongs to the cycle it follows. */
    public int getCurrentCycle() {
        return cyclesCompleted + 1;
    }

    /**
     * Countdown time run so far by the concentration in progress (running or paused), pauses
     * excluded; 0 in a rest or when idle. What "end early" keeps for a task (rediseño de Proyectos).
     */
    public long getCurrentFocusElapsedMillis() {
        if (state != State.RUNNING && state != State.PAUSED) {
            return 0L;
        }
        return Math.max(0L, countdownMillis - getRemainingMillis());
    }

    /** A task plan ({@link #startPlan(long[])}) is in progress or pending its next block. */
    public boolean hasPlan() {
        return planBlocks != null;
    }

    /** Number of focus blocks of the plan; 0 without one. */
    public int getPlanBlockCount() {
        return planBlocks == null ? 0 : planBlocks.length;
    }

    /** 0-based block in progress (during a rest: the block it follows); 0 without a plan. */
    public int getPlanBlockIndex() {
        return planBlocks == null ? 0 : planIndex;
    }

    /** Another focus block follows the current one. */
    public boolean planHasNextBlock() {
        return planBlocks != null && planIndex < planBlocks.length - 1;
    }

    /** Length of the plan block in progress or next; 0 without a plan. */
    public long getPlanBlockMillis() {
        return planBlocks == null ? 0L : planBlocks[planIndex];
    }

    // ----------------------------------------------------------------------- transitions

    /**
     * Play/pause button (old {@code onClick(homePlayButton)}): STOPPED starts a concentration
     * with the configured length, STOPPED_REST starts the configured rest, PAUSED* resumes and
     * RUNNING* pauses.
     *
     * @return true if the timer is running afterwards (start sound), false if it paused.
     */
    public boolean toggle() {
        switch (state) {
            case STOPPED:
                if (planBlocks != null) {
                    // Entre bloques de una tarea (sin auto-ciclo): play sigue con el siguiente bloque.
                    beginPlanBlock();
                } else {
                    startFocus(settings.get().focusMillis);
                }
                return true;
            case STOPPED_REST:
                begin(settings.get().restMillis);
                return true;
            case PAUSED:
            case PAUSED_REST:
                resume();
                return true;
            case RUNNING:
            case RUNNING_REST:
            default:
                pause();
                return false;
        }
    }

    /**
     * Starts a concentration of {@code durationMillis} if idle (old play-from-STOPPED and
     * {@code startBoundConcentrationIfIdle}). The length is what {@link Listener#onFocusCompleted}
     * reports. No-op outside STOPPED. A plain start drops a task plan still pending between blocks.
     */
    public boolean startFocus(long durationMillis) {
        if (state != State.STOPPED) {
            return false;
        }
        clearPlan();
        sessionMillis = durationMillis;
        begin(durationMillis);
        return true;
    }

    /**
     * Rediseño de Proyectos (D-P11): starts a task session of several focus blocks if idle (see
     * {@link TaskCyclePlan}). Each block is a concentration of its own length, reported by
     * {@link Listener#onFocusCompleted}; rests between blocks and auto-cycle follow the settings as
     * in a plain session; after the last block the session ends through
     * {@link Listener#onAllCyclesCompleted} (payload = blocks) whatever the configured cycles say.
     *
     * @return false (nothing changes) outside STOPPED or with no blocks
     */
    public boolean startPlan(long[] focusBlocksMillis) {
        if (state != State.STOPPED || focusBlocksMillis == null || focusBlocksMillis.length == 0) {
            return false;
        }
        planBlocks = focusBlocksMillis.clone();
        planIndex = 0;
        cyclesCompleted = 0;
        beginPlanBlock();
        return true;
    }

    /** Old {@code pauseTimer}: freezes the remaining time. No-op unless running. */
    public boolean pause() {
        if (!state.isRunning()) {
            return false;
        }
        remainingMillis = Math.max(0L, endsAtMillis - clock.now());
        if (state == State.RUNNING) {
            focusPauseCount++;
        }
        state = state == State.RUNNING_REST ? State.PAUSED_REST : State.PAUSED;
        return true;
    }

    /** Old play-from-PAUSED: continues from the frozen remaining time. No-op unless paused. */
    public boolean resume() {
        if (!state.isPaused()) {
            return false;
        }
        endsAtMillis = clock.now() + remainingMillis;
        state = state == State.PAUSED_REST ? State.RUNNING_REST : State.RUNNING;
        return true;
    }

    /**
     * Old {@code cancelSession}: drops the whole cycle and goes idle. Does NOT count as a failure
     * (a fail means "left the app mid-session", this is an explicit user decision).
     *
     * @return false if it was already STOPPED (the old method returned early, no sound) and no task
     *     plan was waiting for its next block (cancelling drops that plan).
     */
    public boolean cancel() {
        if (state == State.STOPPED && planBlocks == null) {
            return false;
        }
        state = State.STOPPED;
        cyclesCompleted = 0;
        clearPlan();
        resetIdle();
        return true;
    }

    /**
     * Old {@code cancelRest} ("skip rest"): ends a rest (running, paused or pending) and returns
     * to an idle concentration. The cycle that triggered the rest still counts; auto-cycle does
     * NOT start the next concentration here.
     */
    public boolean skipRest() {
        if (!state.isRest()) {
            return false;
        }
        cyclesCompleted++;
        state = State.STOPPED;
        advancePlanBlock();
        resetIdle();
        return true;
    }

    /**
     * Old {@code failTimer} (background grace expired): stops a running concentration. The
     * remaining time stays frozen on screen until {@link #resetIdleDisplay()} (the old fail alert's
     * close reset it); the cycle counter is NOT reset.
     *
     * @return false (and no side effect) unless a concentration is running.
     */
    public boolean fail() {
        if (state != State.RUNNING) {
            return false;
        }
        remainingMillis = Math.max(0L, endsAtMillis - clock.now());
        lastFocusElapsedMillis = Math.max(0L, countdownMillis - remainingMillis);
        state = State.STOPPED;
        listener.onFailed();
        return true;
    }

    /**
     * Whether leaving the app now must arm the background-fail grace period (old
     * {@code onActivityStopped} guard): only a running concentration, and never with the 🚫 block
     * mode armed — the accessibility cover already keeps the user from really leaving.
     */
    public boolean shouldArmBackgroundGrace(boolean blockModeArmed) {
        return state == State.RUNNING && !blockModeArmed;
    }

    /** Old {@code resetTimer} when idle: show the configured concentration length again. */
    public void resetIdleDisplay() {
        if (state == State.STOPPED) {
            resetIdle();
        }
    }

    /**
     * Old {@code checkConfigChanges}: called with the NEW settings when the timer settings popup
     * closes. Disabling rest kills any rest (and resets cycles); disabling auto-cycle resets the
     * cycle counter. When idle the displayed length follows the new concentration length.
     */
    public void onSettingsChanged(Settings newSettings) {
        if (!newSettings.restEnabled && state.isRest()) {
            state = State.STOPPED;
            cyclesCompleted = 0;
            advancePlanBlock(); // el bloque que precedía al descanso ya está hecho
        }
        if (!newSettings.autoCycle) {
            cyclesCompleted = 0;
        }
        if (state == State.STOPPED) {
            // Con un plan de tarea pendiente se sigue mostrando su siguiente bloque.
            long idle = planBlocks != null ? planBlocks[planIndex] : newSettings.focusMillis;
            remainingMillis = idle;
            countdownMillis = idle;
        }
    }

    /**
     * Ticker hook: if the running countdown has reached 0, runs the finish transition (old
     * {@code CountDownTimer.onFinish}).
     *
     * @return true if a transition happened.
     */
    public boolean advance() {
        if (!state.isRunning() || clock.now() < endsAtMillis) {
            return false;
        }
        if (state == State.RUNNING) {
            completeFocus();
        } else {
            completeRest();
        }
        return true;
    }

    // ---------------------------------------------------------------------- persistence

    public Snapshot snapshot() {
        return new Snapshot(state, getRemainingMillis(), endsAtMillis, countdownMillis,
                sessionMillis, cyclesCompleted, focusPauseCount, planBlocks, planIndex);
    }

    /**
     * Restores a {@link #snapshot()}. A running countdown keeps its original end instant, so the
     * time spent dead counts; if it already expired, the caller's next {@link #advance()} finishes
     * it.
     */
    public void restore(Snapshot s) {
        state = s.state;
        remainingMillis = s.remainingMillis;
        endsAtMillis = s.endsAtMillis;
        countdownMillis = s.countdownMillis;
        sessionMillis = s.sessionMillis;
        cyclesCompleted = s.cyclesCompleted;
        focusPauseCount = s.focusPauseCount;
        planBlocks = s.planBlocksMillis == null ? null : s.planBlocksMillis.clone();
        planIndex = s.planBlockIndex;
    }

    // -------------------------------------------------------------------------- internals

    /** Old {@code startTimer}: STOPPED/PAUSED -> RUNNING, *_REST -> RUNNING_REST. */
    private void begin(long durationMillis) {
        countdownMillis = durationMillis;
        remainingMillis = durationMillis;
        endsAtMillis = clock.now() + durationMillis;
        state = state.isRest() ? State.RUNNING_REST : State.RUNNING;
        if (state == State.RUNNING) {
            focusPauseCount = 0; // una concentración nueva empieza sin pausas
        }
    }

    /** Old {@code completeTimer}. */
    private void completeFocus() {
        lastFocusElapsedMillis = countdownMillis;
        state = State.STOPPED;
        listener.onFocusCompleted(sessionMillis);
        Settings s = settings.get();
        if (planBlocks != null) {
            completePlanFocus(s);
            return;
        }
        if (s.restEnabled) {
            if (cyclesCompleted < s.cycles || s.infiniteCycles) {
                // Old setRestTimer(): STOPPED_REST and immediately start the rest countdown.
                state = State.STOPPED_REST;
                begin(s.restMillis);
            } else {
                listener.onAllCyclesCompleted(cyclesCompleted);
                cyclesCompleted = 0;
                resetIdle();
            }
        } else {
            resetIdle();
        }
    }

    /**
     * Fin de un bloque de un plan de tarea: tras el último, la sesión acaba por el camino de "todos
     * los ciclos" sin mirar cycles / infiniteCycles; si quedan bloques, descanso como en una sesión
     * normal o, sin descanso, el siguiente bloque enseguida (parar entre bloques dejaría la sesión
     * como terminada).
     */
    private void completePlanFocus(Settings s) {
        if (!planHasNextBlock()) {
            int blocks = planBlocks.length;
            clearPlan();
            listener.onAllCyclesCompleted(blocks);
            cyclesCompleted = 0;
            resetIdle();
            return;
        }
        if (s.restEnabled) {
            state = State.STOPPED_REST;
            begin(s.restMillis);
        } else {
            planIndex++;
            beginPlanBlock();
        }
    }

    /** Old {@code completeRest}. */
    private void completeRest() {
        state = State.STOPPED;
        cyclesCompleted++;
        if (planBlocks != null) {
            // Plan de tarea: el descanso siempre va seguido de otro bloque (nunca hay uno tras el
            // último); el auto-ciclo decide si arranca solo, como en una sesión normal.
            advancePlanBlock();
            resetIdle();
            if (settings.get().autoCycle) {
                beginPlanBlock();
            }
            return;
        }
        resetIdle();
        Settings s = settings.get();
        if (s.autoCycle) {
            if (cyclesCompleted < s.cycles || s.infiniteCycles) {
                // Igual que el original: la siguiente concentración usa la duración configurada
                // pero NO actualiza sessionMillis (el antiguo lastTimerTime) -- ver
                // migracion-visual-stitch-questions.md (u2).
                begin(s.focusMillis);
            } else {
                listener.onAllCyclesCompleted(cyclesCompleted);
                cyclesCompleted = 0;
            }
        }
    }

    private void resetIdle() {
        long focus = planBlocks != null ? planBlocks[planIndex] : settings.get().focusMillis;
        remainingMillis = focus;
        countdownMillis = focus;
    }

    /** STOPPED -> RUNNING with the current plan block; its length is what gets recorded. */
    private void beginPlanBlock() {
        long block = planBlocks[planIndex];
        sessionMillis = block;
        begin(block);
    }

    /** After a rest of a plan: the next block becomes the current one. */
    private void advancePlanBlock() {
        if (planBlocks != null && planIndex < planBlocks.length - 1) {
            planIndex++;
        }
    }

    private void clearPlan() {
        planBlocks = null;
        planIndex = 0;
    }
}
