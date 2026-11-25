package com.example.bbettercalendar.ui.projects;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectBoard;
import com.example.bbettercalendar.projects.ProjectSection;
import com.example.bbettercalendar.projects.ProjectWithBadge;
import com.example.bbettercalendar.projects.TaskSubtask;
import com.example.bbettercalendar.projects.domain.DueDays;
import com.example.bbettercalendar.projects.domain.NextTaskRule;
import com.example.bbettercalendar.projects.domain.ProjectProgress;
import com.example.bbettercalendar.projects.domain.TagRef;
import com.example.bbettercalendar.projects.domain.TaskOrdering;
import com.example.bbettercalendar.projects.domain.TaskSnapshot;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the Projects screen draws, as one immutable value (rediseno-proyectos, spec 3.1). Pure Java:
 * {@link #build} applies the domain rules (area filter, badge, summary, section order for each sort, hide
 * done, items pending deletion) so the ViewModel can run it off the main thread and the JVM can test it.
 */
public final class ProjectsScreenModel {

    /** One bubble of the selector. */
    public static final class Bubble {
        public final int projectId;
        public final String name;
        public final int colorIndex;
        public final String iconKey;
        /** Done over total, 0..1. */
        public final float progress;
        public final int badgeCount;
        public final boolean badgeLate;
        public final boolean selected;

        Bubble(int projectId, String name, int colorIndex, String iconKey, float progress,
               int badgeCount, boolean badgeLate, boolean selected) {
            this.projectId = projectId;
            this.name = name;
            this.colorIndex = colorIndex;
            this.iconKey = iconKey;
            this.progress = progress;
            this.badgeCount = badgeCount;
            this.badgeLate = badgeLate;
            this.selected = selected;
        }
    }

    /** One task row with what its icon row needs. */
    public static final class Row {
        public final TaskSnapshot task;
        public final boolean hasDuration;
        public final boolean repeats;
        public final boolean hasReminder;
        public final boolean hasNote;
        public final TagRef tag;
        public final List<TaskSubtask> subtasks;
        public final int subtasksDone;

        Row(TaskSnapshot task, boolean hasDuration, boolean repeats, boolean hasReminder,
            boolean hasNote, TagRef tag, List<TaskSubtask> subtasks, int subtasksDone) {
            this.task = task;
            this.hasDuration = hasDuration;
            this.repeats = repeats;
            this.hasReminder = hasReminder;
            this.hasNote = hasNote;
            this.tag = tag;
            this.subtasks = subtasks;
            this.subtasksDone = subtasksDone;
        }
    }

    /** One section card. {@code done}/{@code total} count every visible task, also the ones hidden by Hide done. */
    public static final class Section {
        public final int id;
        public final String name;
        public final int done;
        public final int total;
        public final boolean collapsed;
        public final List<Row> rows;

        Section(int id, String name, int done, int total, boolean collapsed, List<Row> rows) {
            this.id = id;
            this.name = name;
            this.done = done;
            this.total = total;
            this.collapsed = collapsed;
            this.rows = rows;
        }
    }

    public final List<Bubble> bubbles;
    /** 0 when there is no project to show. */
    public final int selectedProjectId;
    public final Project selectedProject;
    public final NextTaskRule.SummaryState summaryState;
    public final int percent;
    /** The "next task" of the summary card; null unless {@link #summaryState} is NEXT. */
    public final TaskSnapshot next;
    public final int focusedWeekMinutes;
    /** Days to the deadline (negative when past); null without deadline. */
    public final Integer daysToDue;
    public final List<Section> sections;
    public final TaskOrdering.Mode sort;
    public final boolean hideDone;
    /** The area filter hides every active project while some exist (F4): not the "first project" state. */
    private final boolean filterHidesAll;

    private ProjectsScreenModel(List<Bubble> bubbles, int selectedProjectId, Project selectedProject,
                                NextTaskRule.SummaryState summaryState, int percent, TaskSnapshot next,
                                int focusedWeekMinutes, Integer daysToDue, List<Section> sections,
                                TaskOrdering.Mode sort, boolean hideDone,
                                boolean filterHidesAll) {
        this.filterHidesAll = filterHidesAll;
        this.bubbles = bubbles;
        this.selectedProjectId = selectedProjectId;
        this.selectedProject = selectedProject;
        this.summaryState = summaryState;
        this.percent = percent;
        this.next = next;
        this.focusedWeekMinutes = focusedWeekMinutes;
        this.daysToDue = daysToDue;
        this.sections = sections;
        this.sort = sort;
        this.hideDone = hideDone;
    }

    /** No active project (after the area filter and the pending deletions): the "Your first project" state. */
    public boolean noProjects() {
        return selectedProjectId == 0;
    }

    /**
     * Active projects exist but the area filter hides all of them (F4). The screen keeps the menu (the way to
     * the filter) and offers to clear it; with no projects at all this is false.
     */
    public boolean filterHidesAll() {
        return filterHidesAll;
    }

    /** A project is selected and it has no tasks at all: the "Project ready" state. */
    public boolean noTasks() {
        return selectedProjectId != 0 && summaryState == NextTaskRule.SummaryState.EMPTY;
    }

    // ---------------------------------------------------------------- rules

    /** Active projects of the selector: area filter (0 = all) and not pending deletion. */
    public static List<ProjectWithBadge> visibleProjects(List<ProjectWithBadge> all, int areaFilter,
                                                         Set<Integer> hiddenProjects) {
        List<ProjectWithBadge> out = new ArrayList<>();
        if (all == null) return out;
        for (ProjectWithBadge p : all) {
            if (hiddenProjects != null && hiddenProjects.contains(p.project.id)) continue;
            if (areaFilter != 0 && p.project.areaId != areaFilter) continue;
            out.add(p);
        }
        return out;
    }

    /** The preferred project when it is in the list, else the first one, else 0. */
    public static int resolveSelection(int preferredId, List<ProjectWithBadge> visible) {
        if (visible == null || visible.isEmpty()) return 0;
        for (ProjectWithBadge p : visible) {
            if (p.project.id == preferredId) return preferredId;
        }
        return visible.get(0).project.id;
    }

    /**
     * Builds the screen. {@code board} must be the board of the resolved selection (null when none is
     * selected). Pending-deletion tasks are removed before every rule, so counts and the summary ignore them.
     */
    public static ProjectsScreenModel build(List<ProjectWithBadge> allProjects, int preferredId,
                                            int areaFilter, Set<Integer> hiddenProjects,
                                            ProjectBoard board, List<TagRef> tags,
                                            TaskOrdering.Mode sort, boolean hideDone,
                                            Set<Long> collapsedSections, Set<Integer> hiddenTasks,
                                            long nowMillis, ZoneId zone) {
        List<ProjectWithBadge> visible = visibleProjects(allProjects, areaFilter, hiddenProjects);
        int selected = resolveSelection(preferredId, visible);
        boolean filterHidesAll = visible.isEmpty() && areaFilter != 0
                && !visibleProjects(allProjects, 0, hiddenProjects).isEmpty();

        List<Bubble> bubbles = new ArrayList<>();
        Project selectedProject = null;
        for (ProjectWithBadge p : visible) {
            boolean isSelected = p.project.id == selected;
            if (isSelected) selectedProject = p.project;
            float progress = p.totalCount <= 0 ? 0f : (float) p.doneCount / p.totalCount;
            bubbles.add(new Bubble(p.project.id, p.project.name, p.project.colorIndex,
                    p.project.iconKey, progress, p.dueTodayCount, p.lateCount > 0, isSelected));
        }
        if (selected == 0 || board == null) {
            return new ProjectsScreenModel(bubbles, selected, selectedProject,
                    NextTaskRule.SummaryState.EMPTY, 0, null, 0, null,
                    Collections.<Section>emptyList(), sort, hideDone, filterHidesAll);
        }

        // Tasks without the ones pending deletion; templates stay out of rows (their occurrences are the tasks).
        List<TaskSnapshot> tasks = new ArrayList<>();
        for (TaskSnapshot t : board.tasks) {
            if (hiddenTasks != null && hiddenTasks.contains((int) t.id)) continue;
            tasks.add(t);
        }
        Map<Long, TagRef> tagById = new HashMap<>();
        if (tags != null) {
            for (TagRef t : tags) tagById.put(t.id, t);
        }

        List<Section> sections = new ArrayList<>();
        for (ProjectSection s : board.sections) {
            List<TaskSnapshot> inSection = new ArrayList<>();
            int done = 0;
            for (TaskSnapshot t : tasks) {
                if (t.isTemplate || t.sectionId != s.id) continue;
                inSection.add(t);
                if (t.done) done++;
            }
            int total = inSection.size();
            List<TaskSnapshot> shown = new ArrayList<>();
            for (TaskSnapshot t : TaskOrdering.sort(inSection, sort, zone)) {
                if (hideDone && t.done) continue;
                shown.add(t);
            }
            List<Row> rows = new ArrayList<>();
            for (TaskSnapshot t : shown) rows.add(rowFor(t, board, tagById));
            boolean collapsed = collapsedSections != null && collapsedSections.contains((long) s.id);
            sections.add(new Section(s.id, s.name, done, total, collapsed, rows));
        }

        NextTaskRule.SummaryState state = NextTaskRule.summaryState(tasks);
        TaskSnapshot next = state == NextTaskRule.SummaryState.NEXT ? NextTaskRule.next(tasks) : null;
        Integer days = selectedProject == null ? null
                : DueDays.until(selectedProject.softDeadlineMillis, nowMillis, zone);
        return new ProjectsScreenModel(bubbles, selected, selectedProject, state,
                ProjectProgress.percent(tasks), next, board.focusedWeekMinutes, days,
                sections, sort, hideDone, filterHidesAll);
    }

    private static Row rowFor(TaskSnapshot t, ProjectBoard board, Map<Long, TagRef> tagById) {
        CalendarEntry entry = board.entries.get(t.id);
        boolean hasDuration = false;
        boolean repeats = false;
        boolean hasReminder = false;
        boolean hasNote = false;
        if (entry != null) {
            hasDuration = entry.getDuration() > 0;
            repeats = entry.getRepetition() != 0 || entry.getTemplateId() != 0;
            boolean[] notifications = entry.getNotifications();
            if (notifications != null) {
                for (boolean b : notifications) hasReminder |= b;
            }
            hasNote = notEmpty(entry.getDescription()) || notEmpty(entry.getCompletionNote());
        }
        List<TaskSubtask> subs = board.subtasks.get(t.id);
        if (subs == null) subs = Collections.emptyList();
        int subsDone = 0;
        for (TaskSubtask s : subs) {
            if (s.done) subsDone++;
        }
        return new Row(t, hasDuration, repeats, hasReminder, hasNote,
                t.tagId == 0 ? null : tagById.get(t.tagId), subs, subsDone);
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
