package com.example.bbettercalendar.projects;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ProjectDAO {

    @Insert
    long insert(Project project);

    @Update
    void update(Project project);

    // Activos primero, más recientes primero dentro de cada status.
    @Query("SELECT * FROM project WHERE status != " + Project.STATUS_ARCHIVED
            + " ORDER BY status ASC, createdAtMillis DESC")
    LiveData<List<Project>> observeAll();

    @Query("SELECT * FROM project WHERE id = :id")
    Project getById(int id);

    // Para ciclar colorIndex al crear un proyecto (ProjectListAdapter.ACCENT_COLORS).
    @Query("SELECT COUNT(*) FROM project")
    int getProjectCount();

    @Query("SELECT * FROM project WHERE id = :id")
    LiveData<Project> observeById(int id);

    // Borrado del proyecto en sí; el cascade sobre sus items vive en
    // CalendarEntryDAO.deleteItemsByProject (paso manual desde el ViewModel, decisión #3).
    @Query("DELETE FROM project WHERE id = :id")
    void deleteById(int id);

    // Denominador/numerador del % (decisión #6 del roadmap: recuento de items, peso igual).
    // isTemplate = 0 AND isDismissed = 0: mismo filtro que el resto de queries de superficie,
    // para que plantillas/retiradas no ensucien el %.
    @Query("SELECT COUNT(*) FROM calendarEntry WHERE projectId = :projectId " +
            "AND isTemplate = 0 AND isDismissed = 0 AND isDone = 1")
    int getDoneItemCount(int projectId);

    @Query("SELECT COUNT(*) FROM calendarEntry WHERE projectId = :projectId " +
            "AND isTemplate = 0 AND isDismissed = 0")
    int getTotalItemCount(int projectId);
}
