package com.example.bbettercalendar.ui.projects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.projects.domain.TaskSnapshot;
import com.example.bbettercalendar.ui.focus.TaskFocusRequest;

/**
 * Rediseno de Proyectos (3.5, D-P04, D-P10, D-P11): lo que une una tarea de Proyectos con Focus.
 * Java puro: decide que hacer al tocar un cronometro ({@link #decide}), construye el
 * {@link TaskFocusRequest} de la tarea y fija la meta de una tarea sin ella (decision d9: como el
 * prototipo, pomodoro con su duracion, o 25 min si no tiene).
 */
public final class FocusLink {

    /** Meta de una tarea sin pomodoro ni duracion. */
    public static final int DEFAULT_POMODORO_MINUTES = 25;

    /** Lo que hace un toque en el cronometro de una tarea. */
    public enum Action {
        /** Ya hay una sesion en marcha: Focus se abre sin tocarla. */
        OPEN_ONLY,
        /** La tarea tiene meta: se enfoca tal cual. */
        START,
        /** Sin meta: primero se guarda su pomodoro ({@link #goalForTask}) y luego se enfoca. */
        ENABLE_POMODORO_THEN_START
    }

    private FocusLink() { }

    /** Que hacer con la tarea segun si hay una sesion en marcha. */
    @NonNull
    public static Action decide(@NonNull TaskSnapshot task, boolean sessionRunning) {
        if (sessionRunning) return Action.OPEN_ONLY;
        return hasGoal(task) ? Action.START : Action.ENABLE_POMODORO_THEN_START;
    }

    public static boolean hasGoal(@NonNull TaskSnapshot task) {
        return task.targetMinutes > 0;
    }

    /**
     * Meta en minutos al activar el pomodoro de una tarea sin ella: su duracion en minutos enteros
     * (al menos 1 si es positiva), o 25 min si es «Libre».
     */
    public static int goalForTask(long durationMillis) {
        if (durationMillis <= 0L) return DEFAULT_POMODORO_MINUTES;
        return (int) Math.max(1L, durationMillis / 60_000L);
    }

    /** La peticion de Focus de una tarea con la meta ya fijada (la suya, o la de {@link #goalForTask}). */
    @NonNull
    public static TaskFocusRequest request(@NonNull TaskSnapshot task, int goalMinutes,
                                           @Nullable String projectName, @Nullable String projectIconKey,
                                           int projectColorIndex, int projectId) {
        return new TaskFocusRequest((int) task.id, task.title, goalMinutes, task.attributedMinutes,
                task.blockOnFocus, projectId, projectName, projectIconKey, projectColorIndex);
    }
}
