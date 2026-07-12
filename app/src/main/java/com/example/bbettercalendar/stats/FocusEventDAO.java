package com.example.bbettercalendar.stats;

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

    // Minutos de concentración atribuidos a una tarea/item (spec focus-attribution). Sólo TYPE_FOCUS
    // (los fallos aportan 0). COALESCE para devolver 0 en vez de null cuando no hay filas.
    @Query("SELECT COALESCE(SUM(durationMin), 0) FROM focus_event WHERE entryId = :entryId AND type = 0")
    int sumAttributedMinutes(int entryId);

    // Minutos atribuidos agrupados por entryId, en una sola query, para enriquecer una lista de
    // tareas sin un SUM por fila. Excluye las sesiones genéricas (entryId = 0).
    @Query("SELECT entryId, COALESCE(SUM(durationMin), 0) AS minutes FROM focus_event "
            + "WHERE type = 0 AND entryId != 0 GROUP BY entryId")
    List<AttributedMinutes> getAttributedMinutesByEntry();
}
