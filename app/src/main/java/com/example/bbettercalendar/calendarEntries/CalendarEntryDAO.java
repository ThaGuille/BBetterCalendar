package com.example.bbettercalendar.calendarEntries;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CalendarEntryDAO {
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
            "(startMillis <= :startMillis AND endMillis >= :endMillis))")
    LiveData<List<CalendarEntry>> getEventsBetween(long startMillis, long endMillis);

    // Tareas (type = 2 = TYPE_TASK) sin completar anteriores a un instante — sección
    // "older uncompleted" de la lista de tareas de Home. startMillis > 0 excluye filas
    // legacy que nunca recibieron el espejo de fecha en millis. isTemplate = 0 excluye
    // plantillas; isDismissed = 0 excluye las tareas retiradas por el usuario (spec tasks-recurrence).
    @Query("SELECT * FROM CalendarEntry WHERE type = 2 AND isDone = 0 " +
            "AND isTemplate = 0 AND isDismissed = 0 " +
            "AND startMillis > 0 AND startMillis < :beforeMillis ORDER BY startMillis ASC")
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

    // --- Proyectos (spec projects-mvp) ---

    // Items (dated o undated) de un proyecto, para la pantalla de detalle. isTemplate/isDismissed
    // se filtran igual que en el resto de queries de superficie — plantillas/retiradas no cuentan.
    @Query("SELECT * FROM CalendarEntry WHERE projectId = :projectId " +
            "AND isTemplate = 0 AND isDismissed = 0 ORDER BY startMillis ASC")
    LiveData<List<CalendarEntry>> observeItemsByProject(int projectId);

    // Cascade manual del borrado de proyecto (decisión #3 del proposal — sin Room FK onDelete).
    @Query("DELETE FROM CalendarEntry WHERE projectId = :projectId")
    void deleteItemsByProject(int projectId);
}