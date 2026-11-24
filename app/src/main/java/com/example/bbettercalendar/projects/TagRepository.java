package com.example.bbettercalendar.projects;

import androidx.lifecycle.LiveData;

import com.example.bbettercalendar.database.DbWriteExecutor;

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Etiquetas de tarea y areas de proyecto (misma tabla, separadas por {@link Tag#kind}). Escrituras
 * en {@code @DbWriteExecutor}; la lectura es un LiveData de Room.
 */
@Singleton
public class TagRepository {

    private final TagDAO tagDao;
    private final TransactionRunner transaction;
    private final MainThread mainThread;
    private final ExecutorService dbWriteExecutor;

    @Inject
    public TagRepository(TagDAO tagDao, TransactionRunner transaction, MainThread mainThread,
                         @DbWriteExecutor ExecutorService dbWriteExecutor) {
        this.tagDao = tagDao;
        this.transaction = transaction;
        this.mainThread = mainThread;
        this.dbWriteExecutor = dbWriteExecutor;
    }

    /** Etiquetas ({@link Tag#KIND_TAG}) o areas ({@link Tag#KIND_AREA}), en su orden. */
    public LiveData<List<Tag>> observe(int kind) {
        return tagDao.observeByKind(kind);
    }

    /** Crea al final de su tipo; {@code callback} (puede ser null) recibe el id nuevo en el hilo principal. */
    public void create(int kind, String name, String iconKey, String colorKey,
                       Callback<Integer> callback) {
        dbWriteExecutor.execute(() -> {
            Tag tag = new Tag(name, iconKey, colorKey, kind, tagDao.nextPosition(kind));
            int id = (int) tagDao.insert(tag);
            if (callback != null) mainThread.post(() -> callback.onResult(id));
        });
    }

    public void update(Tag tag) {
        dbWriteExecutor.execute(() -> tagDao.update(tag));
    }

    /**
     * Borra la etiqueta o el area y, en la misma transaccion, deja sin ella a lo que la usaba: las
     * tareas pasan a tagId 0 y los proyectos a areaId 0 (spec 3.7).
     */
    public void delete(Tag tag) {
        dbWriteExecutor.execute(() -> transaction.run(() -> {
            if (tag.kind == Tag.KIND_AREA) {
                tagDao.clearAreaFromProjects(tag.id);
            } else {
                tagDao.clearTagFromEntries(tag.id);
            }
            tagDao.delete(tag);
        }));
    }
}
