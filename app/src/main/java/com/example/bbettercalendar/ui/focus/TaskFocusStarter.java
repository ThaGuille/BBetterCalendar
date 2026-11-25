package com.example.bbettercalendar.ui.focus;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Settings;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;
import com.example.bbettercalendar.ui.home.FocusTarget;

/**
 * Rediseño de Proyectos (§3.5, D-P11, D-P12): arranca la sesión de una tarea sobre el
 * {@link FocusTimerStateMachine} y es el dueño del 🚫 (modo bloqueo) de {@link FocusSessionViewModel}.
 * Java puro, sin Android: el permiso de accesibilidad y el aviso de una vez le llegan por
 * {@link BlockAccess}. Hilo principal, como el ViewModel.
 *
 * <ul>
 *   <li>Con la sesión en marcha (cualquier estado que no sea STOPPED) no cambia nada.</li>
 *   <li>Con meta pendiente, los ciclos de {@link TaskCyclePlan} desde lo que ya lleva; sin meta o
 *       con la meta alcanzada, una concentración de la duración configurada ligada a la tarea.</li>
 *   <li>«Bloqueo al enfocar»: arma el 🚫 si el servicio de accesibilidad está activo; si no, la
 *       sesión empieza sin él y se avisa una vez (decisión d3).</li>
 *   <li>El 🚫 que armó la tarea vuelve a como estaba al acabar su sesión, salvo que el usuario lo
 *       tocara entretanto.</li>
 * </ul>
 */
public final class TaskFocusStarter {

    private static final long MINUTE_MILLIS = 60_000L;

    /** El permiso que necesita el bloqueo (prod: {@code AccessibilityAccess}) y su aviso. */
    public interface BlockAccess {
        /** El servicio de accesibilidad que bloquea las apps está activo. */
        boolean isAvailable();

        /** Una tarea pidió el bloqueo sin el permiso: la sesión va sin él (aviso de una vez). */
        void onUnavailable();
    }

    private final FocusTimerStateMachine machine;
    private final FocusTimerStateMachine.SettingsProvider settings;
    private final BlockAccess blockAccess;

    private boolean blockModeArmed;
    // Valor del 🚫 antes de que una tarea lo armara sola; se devuelve al acabar su sesión (terminar
    // antes o último bloque). null = la sesión no lo armó, o el usuario lo tocó después.
    private Boolean armedBeforeTask;

    public TaskFocusStarter(@NonNull FocusTimerStateMachine machine,
                            @NonNull FocusTimerStateMachine.SettingsProvider settings,
                            @NonNull BlockAccess blockAccess) {
        this.machine = machine;
        this.settings = settings;
        this.blockAccess = blockAccess;
    }

    /**
     * Enfoca una tarea: sólo con la sesión parada liga {@link FocusTarget} a ella y arranca.
     *
     * @return false, sin cambiar nada (ni la tarea ligada ni el 🚫), si ya hay una sesión en marcha
     */
    public boolean startTaskFocus(@NonNull TaskFocusRequest request) {
        if (machine.getState() != State.STOPPED) {
            return false;
        }
        FocusTarget.set(request);
        // Se arranca aquí mismo: que Home no lo vuelva a intentar (y abra Focus) en su onResume.
        FocusTarget.consumePendingAutoStart();
        return begin(request.getGoalMinutes(), request.getAttributedMinutes(),
                request.isBlockOnFocus());
    }

    /**
     * Arranca la sesión de la tarea ya ligada en {@link FocusTarget} (el «Focus this» de Home, con
     * los minutos leídos de la base): sus ciclos si le falta meta, si no una concentración de la
     * duración configurada; arma el bloqueo si la tarea lo pide. No-op fuera de STOPPED.
     */
    public boolean begin(int goalMinutes, int attributedMinutes, boolean blockOnFocus) {
        Settings s = settings.get();
        TaskCyclePlan plan = TaskCyclePlan.of(goalMinutes, attributedMinutes,
                wholeMinutes(s.focusMillis), wholeMinutes(s.restMillis), s.restEnabled);
        boolean started = plan.isEmpty()
                ? machine.startFocus(s.focusMillis)
                : machine.startPlan(plan.getFocusBlocksMillis());
        if (started && blockOnFocus) {
            armForTask();
        }
        return started;
    }

    /** D-P12 / d3: arma el 🚫 con el permiso; sin él la sesión sigue sin bloqueo y se avisa una vez. */
    private void armForTask() {
        if (!blockAccess.isAvailable()) {
            blockAccess.onUnavailable();
            return;
        }
        if (!blockModeArmed) {
            armedBeforeTask = Boolean.FALSE;
            blockModeArmed = true;
        }
    }

    /** Fin de la sesión de una tarea (terminar antes o último bloque): el 🚫 vuelve a como estaba. */
    public void onTaskSessionEnded() {
        if (armedBeforeTask != null) {
            blockModeArmed = armedBeforeTask;
            armedBeforeTask = null;
        }
    }

    /** El usuario arma o desarma el 🚫: su elección no se deshace al acabar la tarea. */
    public void setBlockModeArmedByUser(boolean armed) {
        blockModeArmed = armed;
        armedBeforeTask = null;
    }

    /** Valor guardado (SavedStateHandle) o el de «Focus shield» al crear el ViewModel. */
    public void restoreBlockModeArmed(boolean armed) {
        blockModeArmed = armed;
    }

    /**
     * Valor del 🚫 antes de que la tarea lo armara (null = no lo armó la tarea); se guarda con el
     * resto de la sesion para sobrevivir a la muerte del proceso.
     */
    public Boolean getArmedBeforeTask() {
        return armedBeforeTask;
    }

    /** Devuelve lo guardado por {@link #getArmedBeforeTask()} tras la muerte del proceso. */
    public void restoreArmedBeforeTask(Boolean value) {
        armedBeforeTask = value;
    }

    /**
     * El 🚫 vigente. Si lo armo una tarea y su sesion ya acabo sin pasar por el aviso de ciclos
     * (concentracion simple sin descanso, cancelar, fallo), se suelta aqui.
     */
    public boolean isBlockModeArmed() {
        if (armedBeforeTask != null && machine.getState() == State.STOPPED && !machine.hasPlan()) {
            onTaskSessionEnded();
        }
        return blockModeArmed;
    }

    /** Minutos de una duración de la configuración (en ms), al menos 1 si es positiva. */
    private static int wholeMinutes(long millis) {
        if (millis <= 0) {
            return 0;
        }
        return (int) Math.max(1L, millis / MINUTE_MILLIS);
    }
}
