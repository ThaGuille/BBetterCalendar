package com.example.bbettercalendar.projects;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * rediseno-proyectos (DB v16): etiquetas de tarea y áreas de proyecto (misma tabla, separadas por
 * {@link Tag#kind}). Todo método síncrono va fuera del hilo principal (regla #3: @DbWriteExecutor /
 * @IoExecutor); las variantes LiveData las ejecuta Room.
 */
@Dao
public interface TagDAO {

    @Insert
    long insert(Tag tag);

    @Update
    void update(Tag tag);

    @Delete
    void delete(Tag tag);

    @Query("SELECT * FROM tag WHERE id = :id")
    Tag getById(int id);

    // Por tipo (Tag.KIND_TAG o Tag.KIND_AREA), en su orden.
    @Query("SELECT * FROM tag WHERE kind = :kind ORDER BY position ASC, id ASC")
    List<Tag> getByKind(int kind);

    @Query("SELECT * FROM tag WHERE kind = :kind ORDER BY position ASC, id ASC")
    LiveData<List<Tag>> observeByKind(int kind);

    @Query("SELECT * FROM tag ORDER BY kind ASC, position ASC, id ASC")
    List<Tag> getAll();

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM tag WHERE kind = :kind")
    int nextPosition(int kind);

    // Eliminar una etiqueta deja sus tareas sin etiqueta (spec §3.7); el llamador lo hace en la misma
    // tarea del executor de escritura, antes de borrar la fila.
    @Query("UPDATE calendarEntry SET tagId = 0 WHERE tagId = :tagId")
    void clearTagFromEntries(int tagId);

    // Eliminar un área deja sus proyectos sin área.
    @Query("UPDATE project SET areaId = 0 WHERE areaId = :areaId")
    void clearAreaFromProjects(int areaId);
}
