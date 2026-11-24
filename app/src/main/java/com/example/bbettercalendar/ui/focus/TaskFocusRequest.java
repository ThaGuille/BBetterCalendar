package com.example.bbettercalendar.ui.focus;

import androidx.annotation.Nullable;

/**
 * Lo que una pantalla pasa a {@link FocusSessionViewModel#startTaskFocus(TaskFocusRequest)} para
 * enfocar una tarea (rediseño de Proyectos, §3.5): la tarea, su meta y lo que ya lleva, si arma el
 * bloqueo sola (D-P12) y el proyecto que Focus enseña (círculo con su icono, nombre y color).
 * Inmutable y puro; {@code FocusTarget} guarda una copia de sus campos mientras dura la sesión.
 */
public final class TaskFocusRequest {

    private final int entryId;
    @Nullable
    private final String title;
    private final int goalMinutes;
    private final int attributedMinutes;
    private final boolean blockOnFocus;
    private final int projectId;
    @Nullable
    private final String projectName;
    @Nullable
    private final String projectIconKey;
    private final int projectColorIndex;

    /**
     * @param entryId           id de la {@code CalendarEntry} (0 = ninguna)
     * @param goalMinutes       {@code targetMinutes} de la tarea (0 = sin meta: una sesión de la
     *                          duración configurada ligada a la tarea)
     * @param attributedMinutes minutos ya atribuidos (la cuenta empieza desde ahí)
     * @param blockOnFocus      «Bloqueo al enfocar»: arma el modo bloqueo al empezar
     * @param projectId         proyecto de la tarea (0 = suelta)
     * @param projectColorIndex {@code Project.colorIndex}
     */
    public TaskFocusRequest(int entryId, @Nullable String title, int goalMinutes,
                            int attributedMinutes, boolean blockOnFocus, int projectId,
                            @Nullable String projectName, @Nullable String projectIconKey,
                            int projectColorIndex) {
        this.entryId = entryId;
        this.title = title;
        this.goalMinutes = Math.max(0, goalMinutes);
        this.attributedMinutes = Math.max(0, attributedMinutes);
        this.blockOnFocus = blockOnFocus;
        this.projectId = projectId;
        this.projectName = projectName;
        this.projectIconKey = projectIconKey;
        this.projectColorIndex = projectColorIndex;
    }

    public int getEntryId() {
        return entryId;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    public int getGoalMinutes() {
        return goalMinutes;
    }

    public int getAttributedMinutes() {
        return attributedMinutes;
    }

    public boolean isBlockOnFocus() {
        return blockOnFocus;
    }

    public int getProjectId() {
        return projectId;
    }

    @Nullable
    public String getProjectName() {
        return projectName;
    }

    @Nullable
    public String getProjectIconKey() {
        return projectIconKey;
    }

    public int getProjectColorIndex() {
        return projectColorIndex;
    }
}
