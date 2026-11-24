package com.example.bbettercalendar.projects;

import androidx.lifecycle.LiveData;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.notifications.project.ProjectDeadlineScheduler;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

// Repositorio de proyectos (tranche T2, spec repository-layer-consolidation, finding F3): antes
// ProjectsViewModel observaba projectDao.observeAll() con un Observer manual y recalculaba los
// recuentos done/total en el executor, porque projectDao.observeAll() sólo se invalida cuando
// cambia la tabla project (nunca por una escritura en calendarEntry). observeAllWithCounts()
// resuelve el % con una única query que referencia ambas tablas, así que Room ya se encarga solo
// de la invalidación -- ver data-model.md para el detalle de por qué esto es un fix estructural y
// no el mismo "lag del InvalidationTracker" que Calendar/Home.
//
// rediseno-proyectos: crear con su primera sección, editar, archivar/completar y borrar un proyecto.
// Toda escritura va en @DbWriteExecutor; el resultado de crear vuelve por Callback en el hilo principal.
@Singleton
public class ProjectsRepository {

    private final ProjectDAO projectDao;
    private final ProjectSectionDAO sectionDao;
    private final CalendarEntryDAO calendarEntryDao;
    private final TaskSubtaskDAO subtaskDao;
    private final ProjectDeadlineScheduler deadlineScheduler;
    private final TaskAlarms taskAlarms;
    private final TransactionRunner transaction;
    private final MainThread mainThread;
    private final TagSeeds.Names names;
    private final ExecutorService dbWriteExecutor;

    @Inject
    public ProjectsRepository(ProjectDAO projectDao, ProjectSectionDAO sectionDao,
                              CalendarEntryDAO calendarEntryDao, TaskSubtaskDAO subtaskDao,
                              ProjectDeadlineScheduler deadlineScheduler, TaskAlarms taskAlarms,
                              TransactionRunner transaction, MainThread mainThread,
                              TagSeeds.Names names,
                              @DbWriteExecutor ExecutorService dbWriteExecutor) {
        this.projectDao = projectDao;
        this.sectionDao = sectionDao;
        this.calendarEntryDao = calendarEntryDao;
        this.subtaskDao = subtaskDao;
        this.deadlineScheduler = deadlineScheduler;
        this.taskAlarms = taskAlarms;
        this.transaction = transaction;
        this.mainThread = mainThread;
        this.names = names;
        this.dbWriteExecutor = dbWriteExecutor;
    }

    public LiveData<List<ProjectWithCounts>> observeAllWithCounts() {
        return projectDao.observeAllWithCounts();
    }

    /**
     * Proyectos ACTIVE del selector, con hechas/total y lo que necesita el aviso de hoy. {@code
     * areaId} 0 = todas las areas. "Hoy" se fija al llamar: quien observe tras medianoche vuelve a llamar.
     */
    public LiveData<List<ProjectWithBadge>> observeActiveProjects(int areaId) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long todayStart = today.getTimeInMillis();
        today.add(Calendar.DAY_OF_MONTH, 1);
        return projectDao.observeActiveWithBadge(areaId, todayStart, today.getTimeInMillis());
    }

    /** Alta sencilla (color por rotacion, icono por defecto, sin area): CreateProjectDialog. */
    public void createProject(String name, String notes, long softDeadlineMillis) {
        dbWriteExecutor.execute(() -> insertProject(name, notes, softDeadlineMillis,
                projectDao.getProjectCount(), Project.DEFAULT_ICON_KEY, 0, null));
    }

    /**
     * Crea un proyecto ACTIVE y su primera seccion (Tareas / Tareas, en el idioma del movil) en la
     * posicion 0, en una sola transaccion, y programa los avisos de su entrega. {@code callback}
     * (puede ser null) recibe el id nuevo en el hilo principal.
     */
    public void createProject(String name, String notes, long softDeadlineMillis, int colorIndex,
                              String iconKey, int areaId, Callback<Integer> callback) {
        dbWriteExecutor.execute(() -> insertProject(name, notes, softDeadlineMillis, colorIndex,
                iconKey, areaId, callback));
    }

    private void insertProject(String name, String notes, long softDeadlineMillis, int colorIndex,
                               String iconKey, int areaId, Callback<Integer> callback) {
        Project project = new Project();
        project.name = name;
        project.notes = notes;
        project.status = Project.STATUS_ACTIVE;
        project.softDeadlineMillis = softDeadlineMillis;
        project.colorIndex = colorIndex;
        project.iconKey = iconKey == null ? Project.DEFAULT_ICON_KEY : iconKey;
        project.areaId = areaId;
        project.createdAtMillis = System.currentTimeMillis();
        String sectionName = TagSeeds.sectionName(names);
        transaction.run(() -> {
            project.id = (int) projectDao.insert(project);
            sectionDao.insert(new ProjectSection(project.id, sectionName, 0));
        });
        // Un proyecto puede nacer ya con deadline: las alarmas se programan aqui mismo.
        deadlineScheduler.scheduleFor(project);
        if (callback != null) {
            int id = project.id;
            mainThread.post(() -> callback.onResult(id));
        }
    }

    /** Guarda los datos editables del proyecto y reprograma los avisos de su entrega. */
    public void updateProject(int projectId, String name, String notes, long softDeadlineMillis,
                              int colorIndex, String iconKey, int areaId) {
        dbWriteExecutor.execute(() -> {
            Project fresh = projectDao.getById(projectId);
            if (fresh == null) return;
            fresh.name = name;
            fresh.notes = notes;
            fresh.softDeadlineMillis = softDeadlineMillis;
            fresh.colorIndex = colorIndex;
            fresh.iconKey = iconKey == null ? Project.DEFAULT_ICON_KEY : iconKey;
            fresh.areaId = areaId;
            projectDao.update(fresh);
            // Cancelar SIEMPRE antes de reprogramar: las alarmas del deadline anterior seguirian armadas.
            deadlineScheduler.cancelFor(projectId);
            deadlineScheduler.scheduleFor(fresh);
        });
    }

    public void archive(int projectId) {
        close(projectId, Project.STATUS_ARCHIVED);
    }

    public void complete(int projectId) {
        close(projectId, Project.STATUS_COMPLETED);
    }

    public void unarchive(int projectId) {
        reopenWith(projectId);
    }

    public void reopen(int projectId) {
        reopenWith(projectId);
    }

    // Archivar o completar: cambia el status (nunca borra filas) y apaga todos los avisos del proyecto.
    private void close(int projectId, int status) {
        dbWriteExecutor.execute(() -> {
            Project fresh = projectDao.getById(projectId);
            if (fresh == null) return;
            fresh.status = status;
            if (status == Project.STATUS_COMPLETED) {
                fresh.completedAtMillis = System.currentTimeMillis();
            }
            projectDao.update(fresh);
            deadlineScheduler.cancelFor(projectId);
            taskAlarms.onProjectClosed(projectId);
        });
    }

    private void reopenWith(int projectId) {
        dbWriteExecutor.execute(() -> {
            Project fresh = projectDao.getById(projectId);
            if (fresh == null) return;
            fresh.status = Project.STATUS_ACTIVE;
            fresh.completedAtMillis = 0L;
            projectDao.update(fresh);
            deadlineScheduler.scheduleFor(fresh);
            taskAlarms.onProjectReopened(projectId);
        });
    }

    /**
     * Borra de verdad el proyecto, sus secciones, sus tareas y las subtareas de estas, y cancela sus
     * alarmas. La pantalla lo llama solo cuando termina su ventana de Deshacer (D-P17).
     */
    public void deleteProject(int projectId) {
        dbWriteExecutor.execute(() -> {
            List<CalendarEntry> doomed = calendarEntryDao.getItemsByProjectNow(projectId);
            // Las alarmas se cancelan antes de que las filas desaparezcan.
            if (doomed != null) {
                for (CalendarEntry task : doomed) {
                    taskAlarms.onTaskClosed(task);
                }
            }
            transaction.run(() -> {
                if (doomed != null) {
                    for (CalendarEntry task : doomed) {
                        subtaskDao.deleteByEntry(task.getId());
                    }
                }
                calendarEntryDao.deleteItemsByProject(projectId);
                sectionDao.deleteByProject(projectId);
                projectDao.deleteById(projectId);
            });
            deadlineScheduler.cancelFor(projectId);
        });
    }
}
