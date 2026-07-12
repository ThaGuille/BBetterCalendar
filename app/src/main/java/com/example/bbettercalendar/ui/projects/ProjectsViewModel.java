package com.example.bbettercalendar.ui.projects;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDAO;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Lista de proyectos (spec projects-mvp). El % de cada proyecto exige leer calendarEntry además
// de project, así que no se puede resolver con un Transformations.map puro sobre el LiveData de
// Room (correría en el hilo principal) — en su lugar se observa projectDao.observeAll() con un
// Observer manual que recalcula los recuentos en el executor y postea la lista compuesta (regla #3).
public class ProjectsViewModel extends AndroidViewModel {

    private final ExecutorService executorService;
    private final ProjectDAO projectDao;
    private final LiveData<List<Project>> projectsSource;
    private final MutableLiveData<List<ProjectListItem>> projectItems = new MutableLiveData<>();
    private final Observer<List<Project>> sourceObserver = this::onProjectsChanged;

    public ProjectsViewModel(@NonNull Application application) {
        super(application);
        executorService = Executors.newSingleThreadExecutor();
        AppDatabase db = AppDatabase.getDatabase(application);
        projectDao = db.projectDao();
        projectsSource = projectDao.observeAll();
        projectsSource.observeForever(sourceObserver);
    }

    private void onProjectsChanged(List<Project> projects) {
        executorService.execute(() -> {
            List<ProjectListItem> items = new ArrayList<>();
            if (projects != null) {
                for (Project project : projects) {
                    int done = projectDao.getDoneItemCount(project.id);
                    int total = projectDao.getTotalItemCount(project.id);
                    items.add(new ProjectListItem(project, done, total));
                }
            }
            projectItems.postValue(items);
        });
    }

    public LiveData<List<ProjectListItem>> getProjects() {
        return projectItems;
    }

    /**
     * Recalcula los recuentos con el último snapshot de proyectos. projectDao.observeAll() sólo
     * se invalida cuando cambia la tabla `project` — un item (calendarEntry) marcado como hecho
     * o añadido/borrado NO la toca, así que sin este refresh manual el % se queda obsoleto hasta
     * el próximo cambio de la tabla project. Mismo workaround de "requery en onResume" que
     * HomeViewModel.refreshToday()/CalendarViewModel.refresh() usan para el lag del
     * InvalidationTracker — llamar desde onResume (hilo principal).
     */
    public void refresh() {
        onProjectsChanged(projectsSource.getValue());
    }

    public void createProject(String name, String notes, long softDeadlineMillis) {
        executorService.execute(() -> {
            Project project = new Project();
            project.name = name;
            project.notes = notes;
            project.status = Project.STATUS_ACTIVE;
            project.softDeadlineMillis = softDeadlineMillis;
            project.colorIndex = projectDao.getProjectCount();
            project.createdAtMillis = System.currentTimeMillis();
            projectDao.insert(project);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        projectsSource.removeObserver(sourceObserver);
        executorService.shutdown();
    }
}
