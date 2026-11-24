package com.example.bbettercalendar.ui.projects.quickadd;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.projects.Callback;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectSection;
import com.example.bbettercalendar.projects.ProjectTasksRepository;
import com.example.bbettercalendar.projects.ProjectWithBadge;
import com.example.bbettercalendar.projects.ProjectsRepository;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.TagRepository;
import com.example.bbettercalendar.projects.domain.SectionRef;
import com.example.bbettercalendar.projects.domain.TagRef;
import com.example.bbettercalendar.projects.domain.TaskDraft;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * State of the quick-add box (rediseno-proyectos u8): the {@link TaskDraft} being written (it survives a
 * rotation), which picker is open, and the project, sections and tags the box shows. Saving goes through
 * {@link ProjectTasksRepository#saveDraft}.
 */
@HiltViewModel
public class QuickAddViewModel extends ViewModel {

    /** The picker shown in the keyboard's place, or NONE (keyboard up). */
    public enum Picker { NONE, DATE, DURATION, PRIORITY, TAG, SUBTASKS, MORE, SECTION }

    private final ProjectsRepository projectsRepository;
    private final ProjectTasksRepository tasksRepository;
    private final TagRepository tagRepository;

    private TaskDraft draft;
    private Picker picker = Picker.NONE;
    private boolean sending;
    private int projectId;

    private LiveData<Project> project;
    private LiveData<List<SectionRef>> sections;
    private LiveData<List<TagRef>> tags;

    @Inject
    public QuickAddViewModel(ProjectsRepository projectsRepository,
                             ProjectTasksRepository tasksRepository, TagRepository tagRepository) {
        this.projectsRepository = projectsRepository;
        this.tasksRepository = tasksRepository;
        this.tagRepository = tagRepository;
    }

    /** First call creates the draft for the project and section; later calls (a rotation) change nothing. */
    public void init(int projectId, int sectionId) {
        if (draft != null) return;
        this.projectId = projectId;
        draft = new TaskDraft(startOfToday());
        draft.projectId = projectId;
        draft.sectionId = sectionId;

        project = Transformations.map(projectsRepository.observeActiveProjects(0), list -> {
            if (list != null) {
                for (ProjectWithBadge p : list) {
                    if (p.project.id == projectId) return p.project;
                }
            }
            return null;
        });
        sections = Transformations.map(tasksRepository.observeBoard(projectId), board -> {
            List<SectionRef> out = new ArrayList<>();
            if (board != null) {
                for (ProjectSection s : board.sections) out.add(new SectionRef(s.id, s.name, s.position));
            }
            return out;
        });
        tags = Transformations.map(tagRepository.observe(Tag.KIND_TAG), list -> {
            List<TagRef> out = new ArrayList<>();
            if (list != null) {
                for (Tag t : list) out.add(new TagRef(t.id, t.name, t.iconKey, t.colorKey, t.kind));
            }
            return out;
        });
    }

    @NonNull
    public TaskDraft getDraft() {
        return draft;
    }

    public int getProjectId() {
        return projectId;
    }

    public LiveData<Project> getProject() {
        return project;
    }

    public LiveData<List<SectionRef>> getSections() {
        return sections;
    }

    public LiveData<List<TagRef>> getTags() {
        return tags;
    }

    @NonNull
    public Picker getPicker() {
        return picker;
    }

    public void setPicker(@NonNull Picker picker) {
        this.picker = picker;
    }

    /** True the first time it is called after {@link #init}; false once a save is under way (no double send). */
    public boolean beginSend() {
        if (sending) return false;
        sending = true;
        return true;
    }

    /** Saves the draft; {@code callback} gets the new task id on the main thread. */
    public void save(@Nullable Callback<Integer> callback) {
        tasksRepository.saveDraft(draft, callback);
    }

    @NonNull
    public static List<SectionRef> orEmpty(@Nullable List<SectionRef> list) {
        return list == null ? Collections.<SectionRef>emptyList() : list;
    }

    static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
