package com.example.bbettercalendar.projects;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * rediseno-proyectos (DB v16): subtareas de una tarea. Todo método síncrono va fuera del hilo
 * principal (regla #3: @DbWriteExecutor / @IoExecutor); las variantes LiveData las ejecuta Room.
 */
@Dao
public interface TaskSubtaskDAO {

    @Insert
    long insert(TaskSubtask subtask);

    @Insert
    List<Long> insertAll(List<TaskSubtask> subtasks);

    @Update
    void update(TaskSubtask subtask);

    @Delete
    void delete(TaskSubtask subtask);

    @Query("SELECT * FROM task_subtask WHERE id = :id")
    TaskSubtask getById(int id);

    // Por tarea, en su orden (position; a igualdad, la más antigua primero).
    @Query("SELECT * FROM task_subtask WHERE entryId = :entryId ORDER BY position ASC, id ASC")
    List<TaskSubtask> getByEntry(int entryId);

    @Query("SELECT * FROM task_subtask WHERE entryId = :entryId ORDER BY position ASC, id ASC")
    LiveData<List<TaskSubtask>> observeByEntry(int entryId);

    // Todas las subtareas de las tareas de un proyecto (para el circulito n/m de cada fila). Referencia
    // task_subtask y calendarEntry: Room la re-dispara ante una escritura en cualquiera de las dos.
    @Query("SELECT task_subtask.* FROM task_subtask "
            + "INNER JOIN calendarEntry ON calendarEntry.id = task_subtask.entryId "
            + "WHERE calendarEntry.projectId = :projectId "
            + "ORDER BY task_subtask.entryId ASC, task_subtask.position ASC, task_subtask.id ASC")
    LiveData<List<TaskSubtask>> observeByProject(int projectId);

    // Completar una tarea marca sus subtareas (spec §3.1.1).
    @Query("UPDATE task_subtask SET done = :done WHERE entryId = :entryId")
    void setAllDone(int entryId, boolean done);

    // Al borrar una tarea se borran sus subtareas (spec §4).
    @Query("DELETE FROM task_subtask WHERE entryId = :entryId")
    void deleteByEntry(int entryId);
}
