package com.example.bbettercalendar.ui.progress;

import com.example.bbettercalendar.projects.ProjectDeadlineState;

// Fila de la banda de proyectos de Progress (spec project-deadlines-progress). Datos planos, sin
// tipos de entidad — igual que AppUsageRow: el ViewModel no retiene Projects vivos, sólo lo que la
// fila pinta. minutesInRange son los minutos de concentración atribuidos DENTRO del rango
// seleccionado, mientras que done/total son de por vida (recuento de items del proyecto).
public class ProjectProgressRow {

    public final int projectId;
    public final String name;
    public final int colorIndex;
    public final int doneCount;
    public final int totalCount;
    public final int minutesInRange;
    public final ProjectDeadlineState deadlineState;

    public ProjectProgressRow(int projectId, String name, int colorIndex, int doneCount,
                              int totalCount, int minutesInRange, ProjectDeadlineState deadlineState) {
        this.projectId = projectId;
        this.name = name;
        this.colorIndex = colorIndex;
        this.doneCount = doneCount;
        this.totalCount = totalCount;
        this.minutesInRange = minutesInRange;
        this.deadlineState = deadlineState;
    }

    // Guarda de división por cero, igual que ProjectListItem: un proyecto sin items es 0%, no NaN.
    public int percent() {
        return totalCount == 0 ? 0 : Math.round(100f * doneCount / totalCount);
    }
}
