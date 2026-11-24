package com.example.bbettercalendar.projects;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * rediseno-proyectos (DB v16): una sección de un proyecto (tarjeta plegable de la pantalla de
 * Proyectos). Las tareas apuntan a ella con {@code CalendarEntry.sectionId}; sectionId 0 = sin
 * sección, que se pinta en la primera (la de menor {@link #position}).
 *
 * <p>La migración 15 -> 16 crea una sección «Tareas» (en el idioma del móvil) por proyecto
 * existente, en la posición 0, y mete en ella todas sus tareas.
 */
@Entity(tableName = "project_section")
public class ProjectSection {

    @PrimaryKey(autoGenerate = true)
    public int id;
    public int projectId;
    public String name;
    // Orden dentro del proyecto: 0 = primera sección.
    public int position;

    public ProjectSection() {
    }

    public ProjectSection(int projectId, String name, int position) {
        this.projectId = projectId;
        this.name = name;
        this.position = position;
    }
}
