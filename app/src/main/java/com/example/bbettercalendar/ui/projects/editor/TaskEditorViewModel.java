package com.example.bbettercalendar.ui.projects.editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.projects.ProjectSection;
import com.example.bbettercalendar.projects.ProjectSectionDAO;
import com.example.bbettercalendar.projects.ProjectTasksRepository;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.TagRepository;
import com.example.bbettercalendar.projects.TaskSubtask;
import com.example.bbettercalendar.projects.TaskSubtaskDAO;
import com.example.bbettercalendar.projects.domain.DateChoices;
import com.example.bbettercalendar.projects.domain.SectionRef;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.stats.FocusEventDAO;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * State of the full task editor (D1): the draft being edited (it survives a rotation), the sections and
 * tags it can pick from, and the minutes already focused on the task. Loading runs on {@code @IoExecutor}
 * and posts the result; saving goes through {@link ProjectTasksRepository#saveDraft}.
 */
@HiltViewModel
public class TaskEditorViewModel extends ViewModel {

    /** Everything the screen needs once the task is loaded. */
    public static final class State {
        public final TaskDraft draft;
        public final String baselineSignature;
        public final List<SectionRef> sections;
        public final int attributedMinutes;
        /** Edit mode and the task no longer exists. */
        public final boolean missing;

        State(TaskDraft draft, String baselineSignature, List<SectionRef> sections, int attributedMinutes,
              boolean missing) {
            this.draft = draft;
            this.baselineSignature = baselineSignature;
            this.sections = sections;
            this.attributedMinutes = attributedMinutes;
            this.missing = missing;
        }

        public boolean isEdit() {
            return draft.editId != 0;
        }
    }

    private final ProjectTasksRepository tasks;
    private final TagRepository tagRepository;
    private final CalendarEntryDAO entryDao;
    private final ProjectSectionDAO sectionDao;
    private final TaskSubtaskDAO subtaskDao;
    private final FocusEventDAO focusDao;
    private final ExecutorService ioExecutor;

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private boolean started;

    @Inject
    public TaskEditorViewModel(ProjectTasksRepository tasks, TagRepository tagRepository,
                               CalendarEntryDAO entryDao, ProjectSectionDAO sectionDao,
                               TaskSubtaskDAO subtaskDao, FocusEventDAO focusDao,
                               @IoExecutor ExecutorService ioExecutor) {
        this.tasks = tasks;
        this.tagRepository = tagRepository;
        this.entryDao = entryDao;
        this.sectionDao = sectionDao;
        this.subtaskDao = subtaskDao;
        this.focusDao = focusDao;
        this.ioExecutor = ioExecutor;
    }

    public LiveData<State> getState() {
        return state;
    }

    public LiveData<List<Tag>> getTags() {
        return tagRepository.observe(Tag.KIND_TAG);
    }

    /**
     * Loads once (later calls, as after a rotation, do nothing). {@code entryId} 0 = a new task, in which
     * case {@code given} (the quick-add draft, may be null) is the starting point.
     */
    public void start(int projectId, int entryId, @Nullable TaskDraft given) {
        if (started) return;
        started = true;
        ioExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            List<ProjectSection> rows = sectionDao.getByProject(projectId);
            List<SectionRef> sections = new ArrayList<>();
            for (ProjectSection s : rows) sections.add(new SectionRef(s.id, s.name, s.position));
            long firstSection = sections.isEmpty() ? 0 : sections.get(0).id;

            if (entryId != 0) {
                CalendarEntry entry = entryDao.getEventById(entryId);
                if (entry == null) {
                    TaskDraft blank = new TaskDraft(DateChoices.startOfToday(now, zone));
                    state.postValue(new State(blank, TaskEditorTiles.signature(blank), sections, 0, true));
                    return;
                }
                List<TaskSubtask> subs = subtaskDao.getByEntry(entryId);
                TaskDraft draft = TaskEditorDrafts.fromEntry(entry, subs, now, zone, firstSection);
                int minutes = focusDao.sumAttributedMinutes(entryId);
                state.postValue(new State(draft, TaskEditorTiles.signature(draft), sections, minutes, false));
                return;
            }

            TaskDraft blank = new TaskDraft(DateChoices.startOfToday(now, zone));
            blank.projectId = projectId;
            blank.sectionId = firstSection;
            // The baseline of a new task is the blank draft: whatever the quick add already wrote is a change.
            String baseline = TaskEditorTiles.signature(blank);
            TaskDraft draft = given != null ? given : blank;
            draft.projectId = projectId;
            if (draft.sectionId == 0) draft.sectionId = firstSection;
            state.postValue(new State(draft, baseline, sections, 0, false));
        });
    }

    /** Writes the draft (a section change moves the task); the board refreshes through Room LiveData. */
    public void save(@NonNull TaskDraft draft) {
        tasks.saveDraft(draft, null);
    }
}
