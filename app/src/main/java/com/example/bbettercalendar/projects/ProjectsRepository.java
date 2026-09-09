package com.example.bbettercalendar.projects;

import androidx.lifecycle.LiveData;

import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.notifications.project.ProjectDeadlineScheduler;

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
@Singleton
public class ProjectsRepository {

    private final ProjectDAO projectDao;
    private final ProjectDeadlineScheduler deadlineScheduler;
    private final ExecutorService dbWriteExecutor;

    @Inject
    public ProjectsRepository(ProjectDAO projectDao, ProjectDeadlineScheduler deadlineScheduler,
                              @DbWriteExecutor ExecutorService dbWriteExecutor) {
        this.projectDao = projectDao;
        this.deadlineScheduler = deadlineScheduler;
        this.dbWriteExecutor = dbWriteExecutor;
    }

    public LiveData<List<ProjectWithCounts>> observeAllWithCounts() {
        return projectDao.observeAllWithCounts();
    }

    public void createProject(String name, String notes, long softDeadlineMillis) {
        dbWriteExecutor.execute(() -> {
            Project project = new Project();
            project.name = name;
            project.notes = notes;
            project.status = Project.STATUS_ACTIVE;
            project.softDeadlineMillis = softDeadlineMillis;
            project.colorIndex = projectDao.getProjectCount();
            project.createdAtMillis = System.currentTimeMillis();
            // Un proyecto puede nacer ya con deadline (CreateProjectDialog lo ofrece), así que las
            // alarmas se programan aquí mismo. El id autogenerado sólo existe tras el insert.
            project.id = (int) projectDao.insert(project);
            deadlineScheduler.scheduleFor(project);
        });
    }
}
