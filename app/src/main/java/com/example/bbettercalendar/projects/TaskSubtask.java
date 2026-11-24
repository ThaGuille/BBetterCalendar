package com.example.bbettercalendar.projects;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * rediseno-proyectos (DB v16): una subtarea de una tarea ({@code CalendarEntry} con id
 * {@link #entryId}). Es una lista de comprobación dentro de la tarea, no una tarea propia: las
 * «subtareas» antiguas (tareas sueltas del proyecto) siguen siendo tareas y no se convierten.
 * Al borrar una tarea se borran sus subtareas ({@link TaskSubtaskDAO#deleteByEntry(int)}).
 */
@Entity(tableName = "task_subtask")
public class TaskSubtask {

    @PrimaryKey(autoGenerate = true)
    public int id;
    public int entryId;
    public String title;
    public boolean done;
    // Orden dentro de la tarea: 0 = primera.
    public int position;

    public TaskSubtask() {
    }

    public TaskSubtask(int entryId, String title, boolean done, int position) {
        this.entryId = entryId;
        this.title = title;
        this.done = done;
        this.position = position;
    }
}
