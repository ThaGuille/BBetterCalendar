package com.example.bbettercalendar.calendarEntries;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.bbettercalendar.projects.Project;

import java.util.List;

@Dao
public interface CalendarEntryDAO {

    // rediseno-proyectos (spec 4): las tareas de un proyecto ARCHIVED salen del Calendario, de Home y de los
    // avisos. La subconsulta referencia la tabla project, asi Room invalida estas LiveData al archivar o
    // desarchivar. Las de un proyecto COMPLETED siguen saliendo.
    String NOT_IN_ARCHIVED_PROJECT = "(projectId = 0 OR projectId NOT IN "
            + "(SELECT id FROM project WHERE status = " + Project.STATUS_ARCHIVED + "))";
    @Insert
    long insert(CalendarEntry calendarEntry);

    @Update
    void update(CalendarEntry calendarEntry);

    @Delete
    void delete(CalendarEntry calendarEntry);

    @Query("SELECT * FROM CalendarEntry")
    List<CalendarEntry> getAllEvents();

    @Query("SELECT * FROM CalendarEntry")
    LiveData<List<CalendarEntry>> observeAllEvents();

    @Query("SELECT * FROM CalendarEntry WHERE id = :id")
    CalendarEntry getEventById(int id);

    @Query("SELECT * FROM CalendarEntry WHERE limitDate = :date")
    List<CalendarEntry> getEventsByDate(String date);

    @Query("SELECT * FROM CalendarEntry WHERE title = :title")
    List<CalendarEntry> getEventsByTitle(String title);

    // Returns events whose start lies in [startMillis, endMillis] OR whose end lies in the range
    // OR which span across the entire range (multi-day events that started before and end after).
    // isTemplate = 0: las plantillas de recurrencia (spec tasks-recurrence) tienen fecha ancla
    // pero no deben pintarse — sólo sus ocurrencias materializadas se ven en el calendario/Home.
    // isDismissed = 0: las ocurrencias retiradas por el usuario ("quitar" en atrasadas) también
    // salen del calendario, coherente con las listas accionables (los datos siguen en la BD).
    @Query("SELECT * FROM CalendarEntry WHERE isTemplate = 0 AND isDismissed = 0 AND (" +
            "(startMillis BETWEEN :startMillis AND :endMillis) OR " +
            "(endMillis BETWEEN :startMillis AND :endMillis) OR " +
            "(startMillis <= :startMillis AND endMillis >= :endMillis)) AND " + NOT_IN_ARCHIVED_PROJECT)
    LiveData<List<CalendarEntry>> getEventsBetween(long startMillis, long endMillis);

    // Igual que getEventsBetween pero síncrona, para los avisos diarios (notifications/nudge:
    // agenda de hoy / mañana), que leen desde el @IoExecutor de un receiver. Sin cambio de esquema.
    @Query("SELECT * FROM CalendarEntry WHERE isTemplate = 0 AND isDismissed = 0 AND (" +
            "(startMillis BETWEEN :startMillis AND :endMillis) OR " +
            "(endMillis BETWEEN :startMillis AND :endMillis) OR " +
            "(startMillis <= :startMillis AND endMillis >= :endMillis)) AND " + NOT_IN_ARCHIVED_PROJECT +
            " ORDER BY startMillis ASC")
    List<CalendarEntry> getEventsBetweenSync(long startMillis, long endMillis);

    // Igual que getEventsBetween pero además sin las filas marcadas "no pintar en el calendario"
    // (spec recurrence-calendar-visibility). Es la query de la PANTALLA DE CALENDARIO; Home sigue
    // usando getEventsBetween, porque ocultar es sólo visual y la tarea debe seguir siendo
    // accionable en su lista.
    @Query("SELECT * FROM CalendarEntry WHERE isTemplate = 0 AND isDismissed = 0 " +
            "AND hiddenInCalendar = 0 AND (" +
            "(startMillis BETWEEN :startMillis AND :endMillis) OR " +
            "(endMillis BETWEEN :startMillis AND :endMillis) OR " +
            "(startMillis <= :startMillis AND endMillis >= :endMillis)) AND " + NOT_IN_ARCHIVED_PROJECT)
    LiveData<List<CalendarEntry>> getVisibleEventsBetween(long startMillis, long endMillis);

    // Tareas (type = 2 = TYPE_TASK) sin completar anteriores a un instante — sección
    // "older uncompleted" de la lista de tareas de Home. startMillis > 0 excluye filas
    // legacy que nunca recibieron el espejo de fecha en millis. isTemplate = 0 excluye
    // plantillas; isDismissed = 0 excluye las tareas retiradas por el usuario (spec tasks-recurrence).
    @Query("SELECT * FROM CalendarEntry WHERE type = 2 AND isDone = 0 " +
            "AND isTemplate = 0 AND isDismissed = 0 " +
            "AND startMillis > 0 AND startMillis < :beforeMillis AND " + NOT_IN_ARCHIVED_PROJECT +
            " ORDER BY startMillis ASC")
    LiveData<List<CalendarEntry>> getUndoneTasksBefore(long beforeMillis);

    // --- Recurrencia (spec tasks-recurrence) ---

    // Todas las plantillas de serie (para el top-up del materializador en cada arranque).
    @Query("SELECT * FROM CalendarEntry WHERE isTemplate = 1")
    List<CalendarEntry> getTemplates();

    // Filas repetitivas legacy anteriores al modelo plantilla/ocurrencia: se adoptan como
    // plantillas en el primer arranque tras la migración (repetition != NONE). type = 2 (TYPE_TASK):
    // la recurrencia es sólo de tareas — no adoptar eventos/recordatorios repetidos legacy, que
    // desaparecerían del calendario al volverse plantillas y re-emergerían como tareas.
    @Query("SELECT * FROM CalendarEntry WHERE isTemplate = 0 AND templateId = 0 " +
            "AND repetition != 0 AND type = 2")
    List<CalendarEntry> getLegacyRepeatingRows();

    // Retira (sin borrar) todas las ocurrencias pasadas no hechas de una serie — el botón
    // "quitar" de la fila colapsada de atrasadas. Los datos quedan para stats/gráficas.
    @Query("UPDATE CalendarEntry SET isDismissed = 1 WHERE templateId = :templateId " +
            "AND isDone = 0 AND startMillis < :beforeMillis")
    void dismissSeriesBefore(int templateId, long beforeMillis);

    // rediseno-proyectos 3.8: estado del proyecto de una plantilla o tarea (null = el proyecto no existe).
    // El materializador no rellena series de proyectos que no estan ACTIVE y los avisos no saltan en ellos.
    @Query("SELECT status FROM project WHERE id = :projectId")
    Integer getProjectStatus(int projectId);

    // Ocurrencias materializadas antes de que buildOccurrence copiara projectId: lo heredan de su plantilla
    // (el materializador lo hace en cada arranque; sin filas que arreglar no cambia nada).
    @Query("UPDATE CalendarEntry SET projectId = (SELECT t.projectId FROM CalendarEntry t " +
            "WHERE t.id = CalendarEntry.templateId) WHERE isTemplate = 0 AND templateId != 0 " +
            "AND projectId = 0 AND templateId IN (SELECT id FROM CalendarEntry WHERE projectId != 0)")
    void adoptOccurrenceProjects();

    // Las plantillas que repiten de un proyecto (ProjectLifecycle).
    @Query("SELECT * FROM CalendarEntry WHERE isTemplate = 1 AND repetition != 0 AND projectId = :projectId")
    List<CalendarEntry> getRepeatingTemplatesOfProject(int projectId);

    // Cerrar un proyecto corta sus series: sus ocurrencias futuras sin hacer se retiran (los datos quedan).
    @Query("UPDATE CalendarEntry SET isDismissed = 1 WHERE isTemplate = 0 AND isDone = 0 " +
            "AND isDismissed = 0 AND startMillis > :afterMillis AND templateId IN " +
            "(SELECT id FROM CalendarEntry WHERE isTemplate = 1 AND repetition != 0 AND projectId = :projectId)")
    void dismissProjectSeriesAfter(int projectId, long afterMillis);

    // Reabrir (o Deshacer) devuelve las ocurrencias futuras retiradas al cerrar. "Quitar" de Home solo
    // retira ocurrencias pasadas, asi que una futura retirada solo puede venir del cierre del proyecto.
    @Query("UPDATE CalendarEntry SET isDismissed = 0 WHERE isTemplate = 0 AND isDone = 0 " +
            "AND isDismissed = 1 AND startMillis > :afterMillis AND templateId IN " +
            "(SELECT id FROM CalendarEntry WHERE isTemplate = 1 AND repetition != 0 AND projectId = :projectId)")
    void restoreProjectSeriesAfter(int projectId, long afterMillis);

    // Ocurrencias sin hacer de una serie posteriores a un instante: las que se borran al cambiar o quitar
    // Repetir desde una ocurrencia (decision d5).
    @Query("SELECT * FROM CalendarEntry WHERE templateId = :templateId AND isTemplate = 0 " +
            "AND isDone = 0 AND startMillis > :afterMillis")
    List<CalendarEntry> getUndoneOccurrencesAfter(int templateId, long afterMillis);

    // --- Proyectos (spec projects-mvp) ---

    // Items (dated o undated) de un proyecto, para la pantalla de detalle. isTemplate/isDismissed
    // se filtran igual que en el resto de queries de superficie — plantillas/retiradas no cuentan.
    @Query("SELECT * FROM CalendarEntry WHERE projectId = :projectId " +
            "AND isTemplate = 0 AND isDismissed = 0 ORDER BY startMillis ASC")
    LiveData<List<CalendarEntry>> observeItemsByProject(int projectId);

    // rediseno-proyectos: tablero de un proyecto. A diferencia de observeItemsByProject INCLUYE las
    // plantillas (el repositorio las marca en el TaskSnapshot); excluye solo las retiradas.
    @Query("SELECT * FROM CalendarEntry WHERE projectId = :projectId AND isDismissed = 0 "
            + "ORDER BY startMillis ASC")
    LiveData<List<CalendarEntry>> observeTasksByProject(int projectId);

    // rediseno-proyectos: mover una tarea de seccion, y mover todas las de una seccion a otra.
    @Query("UPDATE CalendarEntry SET sectionId = :sectionId WHERE id = :entryId")
    void moveToSection(int entryId, int sectionId);

    @Query("UPDATE CalendarEntry SET sectionId = :toSectionId WHERE sectionId = :fromSectionId")
    void moveSectionTasks(int fromSectionId, int toSectionId);

    // u16: items de TODOS los proyectos en una sola fuente reactiva, para las tarjetas desplegables
    // de la lista de Projects (agrupa por projectId en el ViewModel). Mismos filtros que arriba.
    @Query("SELECT * FROM CalendarEntry WHERE projectId != 0 " +
            "AND isTemplate = 0 AND isDismissed = 0 ORDER BY startMillis ASC")
    LiveData<List<CalendarEntry>> observeAllProjectItems();

    // Cascade manual del borrado de proyecto (decisión #3 del proposal — sin Room FK onDelete).
    // Lectura síncrona (hilo de IO) para cancelar los recordatorios antes del borrado en cascada.
    @Query("SELECT * FROM CalendarEntry WHERE projectId = :projectId")
    List<CalendarEntry> getItemsByProjectNow(int projectId);

    @Query("DELETE FROM CalendarEntry WHERE projectId = :projectId")
    void deleteItemsByProject(int projectId);

    // u17 (Progress, tile "Tasks done"): tareas marcadas como hechas DENTRO del rango. Desde v15
    // (mejora-visual u1) cuenta por la fecha real de completado (completedAtMillis, que pone
    // CompletionStamp), no por la fecha de la tarea; una tarea sin fecha también cuenta.
    @Query("SELECT COUNT(*) FROM CalendarEntry WHERE type = 2 AND isDone = 1 " +
            "AND isTemplate = 0 AND isDismissed = 0 " +
            "AND completedAtMillis BETWEEN :startMillis AND :endMillis")
    int countDoneTasksBetween(long startMillis, long endMillis);
}
