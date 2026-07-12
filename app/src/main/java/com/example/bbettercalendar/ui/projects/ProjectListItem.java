package com.example.bbettercalendar.ui.projects;

import com.example.bbettercalendar.projects.Project;

// Fila de la lista de proyectos: el Project más su % ya calculado (recuento de items,
// decisión #6 del roadmap). No es una entidad Room — sólo una composición para la UI.
public class ProjectListItem {
    public final Project project;
    public final int doneCount;
    public final int totalCount;

    public ProjectListItem(Project project, int doneCount, int totalCount) {
        this.project = project;
        this.doneCount = doneCount;
        this.totalCount = totalCount;
    }

    public int percent() {
        return totalCount == 0 ? 0 : Math.round(100f * doneCount / totalCount);
    }
}
