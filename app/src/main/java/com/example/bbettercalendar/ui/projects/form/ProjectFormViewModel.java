package com.example.bbettercalendar.ui.projects.form;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.projects.Callback;
import com.example.bbettercalendar.projects.ProjectsRepository;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.TagRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Holds the {@link ProjectFormModel} of the create / edit project form so it survives a rotation, loads
 * it (a new one needs the project count for its colour, an existing one is read from the database) and
 * writes it through {@link ProjectsRepository}.
 */
@HiltViewModel
public class ProjectFormViewModel extends ViewModel {

    private final ProjectsRepository projects;
    private final TagRepository tags;
    private final MutableLiveData<ProjectFormModel> ready = new MutableLiveData<>();
    private final MutableLiveData<Boolean> missing = new MutableLiveData<>(false);

    @Nullable private ProjectFormModel model;
    private int projectId = -1;
    private boolean saving;

    @Inject
    public ProjectFormViewModel(ProjectsRepository projects, TagRepository tags) {
        this.projects = projects;
        this.tags = tags;
    }

    /** Loads once; {@code id} 0 = new project. A rotation keeps the model and its edits. */
    public void init(int id) {
        if (projectId == id) return;
        projectId = id;
        if (id == 0) {
            projects.countProjects(count -> publish(ProjectFormModel.forNew(count)));
        } else {
            projects.loadProject(id, project -> {
                if (project == null) {
                    missing.postValue(true);
                } else {
                    publish(ProjectFormModel.forEdit(project));
                }
            });
        }
    }

    private void publish(ProjectFormModel loaded) {
        model = loaded;
        ready.postValue(loaded);
    }

    public int getProjectId() {
        return projectId;
    }

    public boolean isEditing() {
        return projectId > 0;
    }

    /** Emits the model once it is loaded (the same instance for the whole life of the form). */
    public LiveData<ProjectFormModel> getReady() {
        return ready;
    }

    /** True when the project to edit does not exist any more. */
    public LiveData<Boolean> getMissing() {
        return missing;
    }

    @Nullable
    public ProjectFormModel getModel() {
        return model;
    }

    public LiveData<List<Tag>> getAreas() {
        return tags.observe(Tag.KIND_AREA);
    }

    /**
     * Creates or updates the project. {@code done} receives the project id (the new one when created) on the
     * main thread; a second call while saving is ignored.
     */
    public void save(@NonNull Callback<Integer> done) {
        ProjectFormModel m = model;
        if (m == null || !m.isSaveEnabled() || saving) return;
        saving = true;
        String name = m.getName().trim();
        String notes = m.getNotes().trim();
        if (isEditing()) {
            int id = projectId;
            projects.updateProject(id, name, notes, m.getDeadlineMillis(), m.getColorIndex(),
                    m.getIconKey(), m.getAreaId());
            done.onResult(id);
        } else {
            projects.createProject(name, notes, m.getDeadlineMillis(), m.getColorIndex(),
                    m.getIconKey(), m.getAreaId(), done);
        }
    }
}
