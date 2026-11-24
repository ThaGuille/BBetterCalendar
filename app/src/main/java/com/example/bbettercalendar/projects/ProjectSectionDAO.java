package com.example.bbettercalendar.projects;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * rediseno-proyectos (DB v16): secciones de un proyecto. Todo método síncrono va fuera del hilo
 * principal (regla #3: @DbWriteExecutor / @IoExecutor); las variantes LiveData las ejecuta Room.
 */
@Dao
public interface ProjectSectionDAO {

    @Insert
    long insert(ProjectSection section);

    @Update
    void update(ProjectSection section);

    @Delete
    void delete(ProjectSection section);

    @Query("SELECT * FROM project_section WHERE id = :id")
    ProjectSection getById(int id);

    // Por proyecto, en su orden (position; a igualdad, la más antigua primero).
    @Query("SELECT * FROM project_section WHERE projectId = :projectId ORDER BY position ASC, id ASC")
    List<ProjectSection> getByProject(int projectId);

    @Query("SELECT * FROM project_section WHERE projectId = :projectId ORDER BY position ASC, id ASC")
    LiveData<List<ProjectSection>> observeByProject(int projectId);

    // La primera sección (donde se pintan las tareas con sectionId 0); null si el proyecto no tiene.
    @Query("SELECT * FROM project_section WHERE projectId = :projectId ORDER BY position ASC, id ASC LIMIT 1")
    ProjectSection getFirstOfProject(int projectId);

    @Query("SELECT COUNT(*) FROM project_section WHERE projectId = :projectId")
    int countByProject(int projectId);

    // Al borrar un proyecto (sus tareas las borra CalendarEntryDAO.deleteItemsByProject).
    @Query("DELETE FROM project_section WHERE projectId = :projectId")
    void deleteByProject(int projectId);
}
