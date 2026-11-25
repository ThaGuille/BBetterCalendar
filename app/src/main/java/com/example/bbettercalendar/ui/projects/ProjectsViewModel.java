package com.example.bbettercalendar.ui.projects;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.projects.Callback;
import com.example.bbettercalendar.projects.ProjectBoard;
import com.example.bbettercalendar.projects.ProjectTasksRepository;
import com.example.bbettercalendar.projects.ProjectWithBadge;
import com.example.bbettercalendar.projects.ProjectsRepository;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.TagRepository;
import com.example.bbettercalendar.projects.domain.TagRef;
import com.example.bbettercalendar.projects.domain.TaskOrdering;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * State of the single Projects screen (rediseno-proyectos): one {@link ProjectsScreenModel} built off the
 * main thread from the repositories and the user's settings. The settings (selection, sort, area filter,
 * Hide done, collapsed sections) live here and in {@link ProjectsPrefs}; items pending deletion (D-P17) are
 * hidden here until the screen commits or cancels them.
 */
@HiltViewModel
public class ProjectsViewModel extends AndroidViewModel {

    /** The board of one project, tagged with the project it belongs to (a stale board is never drawn). */
    private static final class BoardFor {
        final int projectId;
        final ProjectBoard board;

        BoardFor(int projectId, ProjectBoard board) {
            this.projectId = projectId;
            this.board = board;
        }
    }

    private final ProjectsRepository projectsRepository;
    private final ProjectTasksRepository tasksRepository;
    private final TagRepository tagRepository;
    private final ProjectsPrefs prefs;
    private final ExecutorService ioExecutor;

    private final LiveData<List<ProjectWithBadge>> allProjects;
    private final LiveData<List<Tag>> tags;
    private final LiveData<List<Tag>> areas;
    // Local day the selector badges were computed for (F8): a new day re-observes them.
    private final MutableLiveData<Long> badgeDay = new MutableLiveData<>();
    private final MutableLiveData<Integer> boardId = new MutableLiveData<>();
    private final LiveData<BoardFor> board;
    private final MediatorLiveData<ProjectsScreenModel> model = new MediatorLiveData<>();

    // Main-thread state.
    private int preferredId;
    private int areaFilter;
    private TaskOrdering.Mode sort;
    private final Set<Integer> hideDoneProjects;
    private final Set<Long> collapsed;
    private final Set<Integer> hiddenProjects = new HashSet<>();
    private final Set<Integer> hiddenTasks = new HashSet<>();
    // A selection asked by a link (Progress row, Calendar marker, notification): it may need the area filter off.
    private boolean linkSelection;

    private final Object postLock = new Object();
    private int generation;

    @Inject
    public ProjectsViewModel(@NonNull Application application, ProjectsRepository projectsRepository,
                             ProjectTasksRepository tasksRepository, TagRepository tagRepository,
                             @IoExecutor ExecutorService ioExecutor) {
        super(application);
        this.projectsRepository = projectsRepository;
        this.tasksRepository = tasksRepository;
        this.tagRepository = tagRepository;
        this.ioExecutor = ioExecutor;
        this.prefs = new ProjectsPrefs(application);
        this.preferredId = prefs.getSelectedProject();
        this.areaFilter = prefs.getAreaFilter();
        this.sort = prefs.getSort();
        this.hideDoneProjects = prefs.getHideDoneProjects();
        this.collapsed = prefs.getCollapsedSections();

        badgeDay.setValue(LocalDate.now().toEpochDay());
        allProjects = Transformations.switchMap(badgeDay, d -> projectsRepository.observeActiveProjects(0));
        tags = tagRepository.observe(Tag.KIND_TAG);
        areas = tagRepository.observe(Tag.KIND_AREA);
        board = Transformations.switchMap(boardId, id -> {
            if (id == null || id <= 0) {
                return new MutableLiveData<BoardFor>(null);
            }
            return Transformations.map(tasksRepository.observeBoard(id), b -> new BoardFor(id, b));
        });

        model.addSource(allProjects, v -> refresh());
        model.addSource(tags, v -> refresh());
        model.addSource(areas, v -> refresh());
        model.addSource(board, v -> refresh());
    }

    // ---------------------------------------------------------------- observe

    public LiveData<ProjectsScreenModel> getModel() {
        return model;
    }

    /** Areas for the filter dialog. */
    public LiveData<List<Tag>> getAreas() {
        return areas;
    }

    /** Call on resume: when the local day changed since the badges were observed, observes them again (F8). */
    public void onDayMayHaveChanged() {
        long today = LocalDate.now().toEpochDay();
        Long seen = badgeDay.getValue();
        if (seen == null || seen != today) badgeDay.setValue(today);
    }

    public int getAreaFilter() {
        return areaFilter;
    }

    // ---------------------------------------------------------------- settings

    /** The user picked a project in the selector. */
    public void selectProject(int projectId) {
        preferredId = projectId;
        linkSelection = false;
        prefs.setSelectedProject(projectId);
        refresh();
    }

    /** A link asks for a project (consumed once by the fragment); it also drops the area filter if it hides it. */
    public void selectFromLink(int projectId) {
        preferredId = projectId;
        linkSelection = true;
        prefs.setSelectedProject(projectId);
        refresh();
    }

    /** Upcoming first -> Priority first -> Recent -> Upcoming first. */
    public TaskOrdering.Mode cycleSort() {
        TaskOrdering.Mode[] modes = TaskOrdering.Mode.values();
        sort = modes[(sort.ordinal() + 1) % modes.length];
        prefs.setSort(sort);
        refresh();
        return sort;
    }

    public void toggleHideDone(int projectId) {
        boolean hide = !hideDoneProjects.contains(projectId);
        if (hide) hideDoneProjects.add(projectId); else hideDoneProjects.remove(projectId);
        prefs.setHideDone(projectId, hide);
        refresh();
    }

    public boolean isHideDone(int projectId) {
        return hideDoneProjects.contains(projectId);
    }

    /** D-P14: limits the selector only. 0 = all areas. */
    public void setAreaFilter(int areaId) {
        areaFilter = areaId;
        prefs.setAreaFilter(areaId);
        refresh();
    }

    public void toggleCollapsed(long sectionId) {
        boolean now = !collapsed.contains(sectionId);
        if (now) collapsed.add(sectionId); else collapsed.remove(sectionId);
        prefs.setCollapsed(sectionId, now);
        refresh();
    }

    // ---------------------------------------------------------------- task writes

    /** Focus on a task without a goal: turns its pomodoro on (duration or 25 min); the callback gets the goal. */
    public void ensurePomodoro(int entryId, Callback<Integer> callback) {
        tasksRepository.ensurePomodoro(entryId, callback);
    }

    public void setTaskDone(int entryId, boolean done) {
        tasksRepository.setDone(entryId, done);
    }

    public void toggleSubtask(int subtaskId) {
        tasksRepository.toggleSubtask(subtaskId);
    }

    public void setCompletionNote(int entryId, String text) {
        tasksRepository.setCompletionNote(entryId, text);
    }

    // ---------------------------------------------------------------- section writes

    public void addSection(int projectId, String name) {
        tasksRepository.addSection(projectId, name);
    }

    public void renameSection(int sectionId, String name) {
        tasksRepository.renameSection(sectionId, name);
    }

    public void deleteSection(int sectionId, Callback<Boolean> callback) {
        tasksRepository.deleteSection(sectionId, callback);
    }

    // ---------------------------------------------------------------- project writes

    public void archiveProject(int projectId) {
        projectsRepository.archive(projectId);
    }

    public void unarchiveProject(int projectId) {
        projectsRepository.unarchive(projectId);
    }

    public void completeProject(int projectId) {
        projectsRepository.complete(projectId);
    }

    public void reopenProject(int projectId) {
        projectsRepository.reopen(projectId);
    }

    // ---------------------------------------------------------------- pending deletions (D-P17)

    public void hideProject(int projectId) {
        hiddenProjects.add(projectId);
        refresh();
    }

    public void restoreProject(int projectId) {
        hiddenProjects.remove(projectId);
        refresh();
    }

    public void commitProjectDelete(int projectId) {
        if (hiddenProjects.remove(projectId)) {
            projectsRepository.deleteProject(projectId);
        }
    }

    public void hideTask(int entryId) {
        hiddenTasks.add(entryId);
        refresh();
    }

    public void restoreTask(int entryId) {
        hiddenTasks.remove(entryId);
        refresh();
    }

    public void commitTaskDelete(int entryId) {
        if (hiddenTasks.remove(entryId)) {
            tasksRepository.deleteTask(entryId);
        }
    }

    /** Deletes for real everything still pending (the screen is left). */
    public void flushPendingDeletes() {
        for (Integer id : new ArrayList<>(hiddenTasks)) commitTaskDelete(id);
        for (Integer id : new ArrayList<>(hiddenProjects)) commitProjectDelete(id);
    }

    @Override
    protected void onCleared() {
        flushPendingDeletes();
    }

    // ---------------------------------------------------------------- model

    private void refresh() {
        List<ProjectWithBadge> all = allProjects.getValue();
        List<Tag> tagRows = tags.getValue();
        if (all == null || tagRows == null) return;

        if (linkSelection && areaFilter != 0) {
            boolean visible = false;
            boolean exists = false;
            for (ProjectWithBadge p : all) {
                if (p.project.id != preferredId) continue;
                exists = true;
                visible = p.project.areaId == areaFilter;
            }
            if (exists && !visible) {
                areaFilter = 0;
                prefs.setAreaFilter(0);
            }
        }
        linkSelection = false;

        List<ProjectWithBadge> visibleProjects =
                ProjectsScreenModel.visibleProjects(all, areaFilter, hiddenProjects);
        int resolved = ProjectsScreenModel.resolveSelection(preferredId, visibleProjects);
        Integer current = boardId.getValue();
        if (current == null || current != resolved) {
            boardId.setValue(resolved);
        }
        BoardFor bf = board.getValue();
        ProjectBoard readyBoard = null;
        if (resolved > 0) {
            if (bf == null || bf.projectId != resolved || bf.board == null) return; // waiting for its board
            readyBoard = bf.board;
        }

        // Snapshot the main-thread inputs; the rules run on the I/O executor.
        final ProjectBoard boardInput = readyBoard;
        final int preferred = preferredId;
        final int area = areaFilter;
        final Set<Integer> hiddenP = new HashSet<>(hiddenProjects);
        final Set<Integer> hiddenT = new HashSet<>(hiddenTasks);
        final Set<Long> collapsedInput = new HashSet<>(collapsed);
        final TaskOrdering.Mode sortInput = sort;
        final boolean hideDone = hideDoneProjects.contains(resolved);
        final List<TagRef> refs = new ArrayList<>();
        for (Tag t : tagRows) refs.add(new TagRef(t.id, t.name, t.iconKey, t.colorKey, t.kind));
        final int mine;
        synchronized (postLock) {
            mine = ++generation;
        }
        ioExecutor.execute(() -> {
            ProjectsScreenModel built = ProjectsScreenModel.build(all, preferred, area, hiddenP,
                    boardInput, refs, sortInput, hideDone, collapsedInput, hiddenT,
                    System.currentTimeMillis(), ZoneId.systemDefault());
            synchronized (postLock) {
                if (mine == generation) model.postValue(built);
            }
        });
    }
}
