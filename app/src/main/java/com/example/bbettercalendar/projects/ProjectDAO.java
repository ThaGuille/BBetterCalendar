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

    // Mismo SELECT que observeAll() más los recuentos done/total resueltos con subqueries
    // correladas sobre calendarEntry, en la misma fila (spec repository-layer-consolidation, T2:
    // sustituye el Observer manual + recompute de ProjectsViewModel). Como la query referencia
    // project Y calendarEntry, el InvalidationTracker de Room la re-dispara ante una escritura en
    // cualquiera de las dos tablas -- ya no hace falta refresh()/onResume(). Predicados idénticos,
    // campo a campo, a getDoneItemCount/getTotalItemCount (sin filtro por type: eventos y
    // recordatorios de un proyecto también cuentan para el %, igual que antes).
    @Query("SELECT project.*, "
            + "(SELECT COUNT(*) FROM calendarEntry WHERE calendarEntry.projectId = project.id "
            + "AND calendarEntry.isTemplate = 0 AND calendarEntry.isDismissed = 0 "
            + "AND calendarEntry.isDone = 1) AS doneCount, "
            + "(SELECT COUNT(*) FROM calendarEntry WHERE calendarEntry.projectId = project.id "
            + "AND calendarEntry.isTemplate = 0 AND calendarEntry.isDismissed = 0) AS totalCount "
            + "FROM project WHERE status != " + Project.STATUS_ARCHIVED
            + " ORDER BY status ASC, createdAtMillis DESC")
    LiveData<List<ProjectWithCounts>> observeAllWithCounts();

    @Query("SELECT * FROM project WHERE id = :id")
    Project getById(int id);

    // Deadlines pintados en el calendario (spec project-deadlines-progress): sólo proyectos
    // ACTIVE -- un proyecto completado con deadline futuro no debe seguir marcando el mes.
    // LiveData de Room: editar el deadline en Projects repinta el calendario sin refresh().
    @Query("SELECT * FROM project WHERE status = " + Project.STATUS_ACTIVE
            + " AND softDeadlineMillis BETWEEN :start AND :end"
            + " ORDER BY softDeadlineMillis ASC")
    LiveData<List<Project>> observeDeadlinesBetween(long start, long end);

    // Re-armado de alarmas tras un reinicio (BootReceiver): los deadlines ya pasados no se
    // reprograman (el core los descartaría igual, pero así no se recorren).
    @Query("SELECT * FROM project WHERE status = " + Project.STATUS_ACTIVE
            + " AND softDeadlineMillis > :now")
    List<Project> getActiveWithDeadlineAfter(long now);

    // Para ciclar colorIndex al crear un proyecto (ProjectListAdapter.ACCENT_COLORS).
    @Query("SELECT COUNT(*) FROM project")
    int getProjectCount();

    @Query("SELECT * FROM project WHERE id = :id")
    LiveData<Project> observeById(int id);

    // Borrado del proyecto en sí; el cascade sobre sus items vive en
    // CalendarEntryDAO.deleteItemsByProject (paso manual desde el ViewModel, decisión #3).
    @Query("DELETE FROM project WHERE id = :id")
    void deleteById(int id);
}
