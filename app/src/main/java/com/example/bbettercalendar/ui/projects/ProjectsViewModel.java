package com.example.bbettercalendar.ui.projects;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.bbettercalendar.projects.ProjectWithCounts;
import com.example.bbettercalendar.projects.ProjectsRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

// Lista de proyectos (spec projects-mvp; repository-layer-consolidation T2). El % de cada
// proyecto llega ya resuelto en ProjectsRepository.observeAllWithCounts() (una única query Room
// con JOIN vía subqueries correladas), así que esto es un Transformations.map puro -- Room
// invalida sola ante cualquier escritura en project O calendarEntry, sin Observer manual ni
// refresh()/onResume().
@HiltViewModel
public class ProjectsViewModel extends AndroidViewModel {

    private final ProjectsRepository repository;
    private final LiveData<List<ProjectListItem>> projectItems;

    @Inject
    public ProjectsViewModel(@NonNull Application application, ProjectsRepository repository) {
        super(application);
        this.repository = repository;
        projectItems = Transformations.map(repository.observeAllWithCounts(),
                ProjectsViewModel::toListItems);
    }

    private static List<ProjectListItem> toListItems(List<ProjectWithCounts> withCounts) {
        List<ProjectListItem> items = new ArrayList<>();
        if (withCounts != null) {
            for (ProjectWithCounts pwc : withCounts) {
                items.add(new ProjectListItem(pwc.project, pwc.doneCount, pwc.totalCount));
            }
        }
        return items;
    }

    public LiveData<List<ProjectListItem>> getProjects() {
        return projectItems;
    }

    public void createProject(String name, String notes, long softDeadlineMillis) {
        repository.createProject(name, notes, softDeadlineMillis);
    }
}
