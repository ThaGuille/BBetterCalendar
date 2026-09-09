package com.example.bbettercalendar.stats;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FocusEventDAO {

    @Insert
    void insert(FocusEvent event);

    @Query("SELECT * FROM focus_event WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp")
    List<FocusEvent> getRange(long start, long end);

    // ---------------- Racha "días con pomodoro" (spec focus-mode-and-streak) ----------------
    // La racha ya no es "días seguidos abriendo la app" (Stats.currentStreak) sino "de los últimos
    // 30 días, en cuántos completé un pomodoro entero". Se deriva de esta tabla, que ya guarda una
    // fila por sesión completada -- sin columna nueva ni bump de esquema (regla #6).
    //
    // date(ts/1000, 'unixepoch', 'localtime') convierte el epoch en la fecha CIVIL del dispositivo:
    // sin 'localtime' una sesión de las 00:30 contaría en el día anterior en cualquier TZ al este
    // de UTC. El COUNT(DISTINCT ...) sobre esa fecha es lo que hace que dos pomodoros del mismo día
    // cuenten como un día, no como dos.

    /** Nº de días distintos con >= 1 sesión completada desde :since. Reactiva: se reemite sola al insertar. */
    @Query("SELECT COUNT(DISTINCT date(timestamp / 1000, 'unixepoch', 'localtime')) FROM focus_event "
            + "WHERE type = 0 AND timestamp >= :since")
    LiveData<Integer> observeActiveDaysSince(long since);

    /** Días (ISO "YYYY-MM-DD") con >= 1 sesión completada desde :since -- alimenta el calendario del popup. */
    @Query("SELECT DISTINCT date(timestamp / 1000, 'unixepoch', 'localtime') AS day FROM focus_event "
            + "WHERE type = 0 AND timestamp >= :since ORDER BY day")
    List<String> getActiveDaysSince(long since);

    /** Días distintos con sesión completada dentro del rango (se usa para "este mes"). Síncrona. */
    @Query("SELECT COUNT(DISTINCT date(timestamp / 1000, 'unixepoch', 'localtime')) FROM focus_event "
            + "WHERE type = 0 AND timestamp BETWEEN :start AND :end")
    int countActiveDaysBetween(long start, long end);

    /** Sesiones completadas en el rango. Con rango = hoy, == 1 identifica el primer pomodoro del día. */
    @Query("SELECT COUNT(*) FROM focus_event WHERE type = 0 AND timestamp BETWEEN :start AND :end")
    int countFocusBetween(long start, long end);

    // Minutos de concentración atribuidos a una tarea/item (spec focus-attribution). Sólo TYPE_FOCUS
    // (los fallos aportan 0). COALESCE para devolver 0 en vez de null cuando no hay filas.
    @Query("SELECT COALESCE(SUM(durationMin), 0) FROM focus_event WHERE entryId = :entryId AND type = 0")
    int sumAttributedMinutes(int entryId);

    // Minutos atribuidos agrupados por entryId, en una sola query, para enriquecer una lista de
    // tareas/items sin un SUM por fila. Excluye las sesiones genéricas (entryId = 0). LiveData de
    // Room (repository-layer-consolidation T2, FocusAttributionRepository.enrich()): se invalida
    // sola cuando se inserta un FocusEvent, así que la atribución ya no depende de que la lista de
    // tareas/items vuelva a emitir primero.
    @Query("SELECT entryId, COALESCE(SUM(durationMin), 0) AS minutes FROM focus_event "
            + "WHERE type = 0 AND entryId != 0 GROUP BY entryId")
    LiveData<List<AttributedMinutes>> observeAttributedMinutesByEntry();

    // Minutos atribuidos agrupados por PROYECTO y acotados al rango (spec
    // project-deadlines-progress): focus_event ⨝ calendarEntry ⨝ project. Misma unidad y mismos
    // filtros que observeAttributedMinutesByEntry (type = 0, entryId != 0) pero con dos
    // diferencias deliberadas: sube un nivel hasta el proyecto y SÍ acota por timestamp -- la
    // query por item es ilimitada en el tiempo a propósito (progreso de por vida de una tarea),
    // mientras que la gráfica y la banda de Progress viven dentro del TimeRange seleccionado.
    // Síncrona: la llama ProgressViewModel desde el executor, no es una fuente reactiva.
    @Query("SELECT project.id AS projectId, project.name AS projectName, "
            + "COALESCE(SUM(focus_event.durationMin), 0) AS minutes "
            + "FROM focus_event "
            + "INNER JOIN calendarEntry ON calendarEntry.id = focus_event.entryId "
            + "INNER JOIN project ON project.id = calendarEntry.projectId "
            + "WHERE focus_event.type = 0 AND focus_event.entryId != 0 "
            + "AND focus_event.timestamp BETWEEN :start AND :end "
            + "GROUP BY project.id, project.name "
            + "ORDER BY minutes DESC, project.name ASC")
    List<ProjectMinutes> getMinutesByProject(long start, long end);
}
