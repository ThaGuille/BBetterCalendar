package com.example.bbettercalendar.ui.projects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectBoard;
import com.example.bbettercalendar.projects.ProjectSection;
import com.example.bbettercalendar.projects.ProjectWithBadge;
import com.example.bbettercalendar.projects.TaskSubtask;
import com.example.bbettercalendar.projects.domain.NextTaskRule;
import com.example.bbettercalendar.projects.domain.TagRef;
import com.example.bbettercalendar.projects.domain.TaskOrdering;
import com.example.bbettercalendar.projects.domain.TaskSnapshot;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectsScreenModelTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final long NOW = at(LocalDate.of(2026, 11, 25), 10);

    private static long at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static long day(int offset, int hour) {
        return at(LocalDate.of(2026, 11, 25).plusDays(offset), hour);
    }

    private static ProjectWithBadge project(int id, String name, int areaId, int done, int total,
                                            int dueToday, int late) {
        ProjectWithBadge p = new ProjectWithBadge();
        p.project = new Project();
        p.project.id = id;
        p.project.name = name;
        p.project.areaId = areaId;
        p.project.colorIndex = id;
        p.doneCount = done;
        p.totalCount = total;
        p.dueTodayCount = dueToday;
        p.lateCount = late;
        return p;
    }

    private static TaskSnapshot task(long id, long sectionId, int sectionPosition, long due,
                                     int priority, boolean done) {
        return new TaskSnapshot(id, sectionId, sectionPosition, "t" + id, due, due != 0, priority,
                done, false, 0, 0, false, 0);
    }

    private static ProjectBoard board(int focusedMinutes, TaskSnapshot... tasks) {
        List<ProjectSection> sections = Arrays.asList(
                section(1, "A", 0), section(2, "B", 1));
        return new ProjectBoard(sections, Arrays.asList(tasks),
                new HashMap<Long, CalendarEntry>(), new HashMap<Long, List<TaskSubtask>>(),
                focusedMinutes);
    }

    private static ProjectSection section(int id, String name, int position) {
        ProjectSection s = new ProjectSection();
        s.id = id;
        s.name = name;
        s.position = position;
        return s;
    }

    private static ProjectsScreenModel build(List<ProjectWithBadge> projects, int preferred, int area,
                                             ProjectBoard board, TaskOrdering.Mode sort,
                                             boolean hideDone, Set<Integer> hiddenTasks) {
        return ProjectsScreenModel.build(projects, preferred, area, Collections.<Integer>emptySet(),
                board, Collections.<TagRef>emptyList(), sort, hideDone,
                Collections.<Long>emptySet(), hiddenTasks, NOW, ZONE);
    }

    private static List<Long> ids(ProjectsScreenModel.Section s) {
        List<Long> out = new ArrayList<>();
        for (ProjectsScreenModel.Row r : s.rows) out.add(r.task.id);
        return out;
    }

    @Test
    public void selectorIsLimitedByTheAreaFilter() {
        List<ProjectWithBadge> all = Arrays.asList(
                project(1, "Tesis", 7, 1, 4, 0, 0),
                project(2, "Fisica", 7, 0, 2, 0, 0),
                project(3, "Entreno", 8, 0, 0, 0, 0));
        ProjectsScreenModel studies = build(all, 1, 7, board(0), TaskOrdering.Mode.UPCOMING, false, null);
        assertEquals(2, studies.bubbles.size());
        ProjectsScreenModel everything = build(all, 1, 0, board(0), TaskOrdering.Mode.UPCOMING, false, null);
        assertEquals(3, everything.bubbles.size());
    }

    @Test
    public void selectionFallsBackToTheFirstVisibleProject() {
        List<ProjectWithBadge> all = Arrays.asList(
                project(1, "Tesis", 7, 0, 1, 0, 0), project(3, "Entreno", 8, 0, 1, 0, 0));
        ProjectsScreenModel m = build(all, 3, 7, board(0), TaskOrdering.Mode.UPCOMING, false, null);
        assertEquals(1, m.selectedProjectId);
        assertTrue(m.bubbles.get(0).selected);
        assertEquals(3, ProjectsScreenModel.resolveSelection(3, all));
        assertEquals(0, ProjectsScreenModel.resolveSelection(3, Collections.<ProjectWithBadge>emptyList()));
    }

    @Test
    public void bubbleCarriesProgressAndTheBadgeWithItsLateFlag() {
        List<ProjectWithBadge> all = Arrays.asList(
                project(1, "Tesis", 0, 1, 4, 3, 1),
                project(2, "Fisica", 0, 2, 2, 2, 0),
                project(3, "Vacio", 0, 0, 0, 0, 0));
        ProjectsScreenModel m = build(all, 1, 0, board(0), TaskOrdering.Mode.UPCOMING, false, null);
        ProjectsScreenModel.Bubble tesis = m.bubbles.get(0);
        assertEquals(0.25f, tesis.progress, 0.001f);
        assertEquals(3, tesis.badgeCount);
        assertTrue(tesis.badgeLate);
        assertFalse(m.bubbles.get(1).badgeLate);
        assertEquals(1f, m.bubbles.get(1).progress, 0.001f);
        assertEquals(0, m.bubbles.get(2).badgeCount);
        assertEquals(0f, m.bubbles.get(2).progress, 0.001f);
    }

    @Test
    public void summaryStatesAndNextTask() {
        List<ProjectWithBadge> all = Collections.singletonList(project(1, "Tesis", 0, 0, 0, 0, 0));
        ProjectsScreenModel empty = build(all, 1, 0, board(0), TaskOrdering.Mode.UPCOMING, false, null);
        assertEquals(NextTaskRule.SummaryState.EMPTY, empty.summaryState);
        assertTrue(empty.noTasks());
        assertNull(empty.next);

        ProjectsScreenModel allDone = build(all, 1, 0,
                board(0, task(10, 1, 0, day(0, 12), 0, true)),
                TaskOrdering.Mode.UPCOMING, false, null);
        assertEquals(NextTaskRule.SummaryState.ALL_DONE, allDone.summaryState);
        assertEquals(100, allDone.percent);
        assertNull(allDone.next);
        assertFalse(allDone.noTasks());

        ProjectsScreenModel next = build(all, 1, 0,
                board(35,
                        task(10, 1, 0, day(2, 9), 3, false),
                        task(11, 1, 0, day(-1, 9), 0, false),
                        task(12, 2, 1, 0, 3, false)),
                TaskOrdering.Mode.UPCOMING, false, null);
        assertEquals(NextTaskRule.SummaryState.NEXT, next.summaryState);
        assertEquals(11, next.next.id);
        assertEquals(35, next.focusedWeekMinutes);
        assertEquals(0, next.percent);
    }

    @Test
    public void daysToDueOnlyWithADeadline() {
        ProjectWithBadge p = project(1, "Tesis", 0, 0, 1, 0, 0);
        List<ProjectWithBadge> all = Collections.singletonList(p);
        assertNull(build(all, 1, 0, board(0), TaskOrdering.Mode.UPCOMING, false, null).daysToDue);
        p.project.softDeadlineMillis = day(24, 18);
        assertEquals(Integer.valueOf(24),
                build(all, 1, 0, board(0), TaskOrdering.Mode.UPCOMING, false, null).daysToDue);
    }

    @Test
    public void sectionRowsFollowEachSortAndDoneGoLast() {
        List<ProjectWithBadge> all = Collections.singletonList(project(1, "Tesis", 0, 1, 4, 0, 0));
        ProjectBoard b = board(0,
                task(1, 1, 0, day(3, 9), 3, false),
                task(2, 1, 0, day(1, 9), 1, false),
                task(3, 1, 0, 0, 3, false),
                task(4, 1, 0, day(0, 9), 0, true));
        assertEquals(Arrays.asList(2L, 1L, 3L, 4L), ids(build(all, 1, 0, b,
                TaskOrdering.Mode.UPCOMING, false, null).sections.get(0)));
        assertEquals(Arrays.asList(1L, 3L, 2L, 4L), ids(build(all, 1, 0, b,
                TaskOrdering.Mode.PRIORITY, false, null).sections.get(0)));
        assertEquals(Arrays.asList(3L, 2L, 1L, 4L), ids(build(all, 1, 0, b,
                TaskOrdering.Mode.RECENT, false, null).sections.get(0)));
    }

    @Test
    public void hideDoneRemovesDoneRowsButKeepsTheCounts() {
        List<ProjectWithBadge> all = Collections.singletonList(project(1, "Tesis", 0, 1, 2, 0, 0));
        ProjectBoard b = board(0, task(1, 1, 0, 0, 0, true), task(2, 1, 0, 0, 0, false));
        ProjectsScreenModel shown = build(all, 1, 0, b, TaskOrdering.Mode.UPCOMING, false, null);
        assertEquals(2, shown.sections.get(0).rows.size());
        ProjectsScreenModel hidden = build(all, 1, 0, b, TaskOrdering.Mode.UPCOMING, true, null);
        assertEquals(Collections.singletonList(2L), ids(hidden.sections.get(0)));
        assertEquals(1, hidden.sections.get(0).done);
        assertEquals(2, hidden.sections.get(0).total);
        assertTrue(hidden.hideDone);
    }

    @Test
    public void tasksPendingDeletionDisappearFromRowsCountsAndSummary() {
        List<ProjectWithBadge> all = Collections.singletonList(project(1, "Tesis", 0, 0, 2, 0, 0));
        ProjectBoard b = board(0, task(1, 1, 0, day(0, 9), 0, false), task(2, 1, 0, day(1, 9), 0, false));
        Set<Integer> pending = new HashSet<>(Collections.singletonList(1));
        ProjectsScreenModel m = build(all, 1, 0, b, TaskOrdering.Mode.UPCOMING, false, pending);
        assertEquals(Collections.singletonList(2L), ids(m.sections.get(0)));
        assertEquals(1, m.sections.get(0).total);
        assertEquals(2, m.next.id);

        Set<Integer> both = new HashSet<>(Arrays.asList(1, 2));
        ProjectsScreenModel gone = build(all, 1, 0, b, TaskOrdering.Mode.UPCOMING, false, both);
        assertEquals(NextTaskRule.SummaryState.EMPTY, gone.summaryState);
        assertTrue(gone.noTasks());
    }

    @Test
    public void projectsPendingDeletionLeaveTheSelectorAndPassTheSelection() {
        List<ProjectWithBadge> all = Arrays.asList(
                project(1, "Tesis", 0, 0, 1, 0, 0), project(2, "Fisica", 0, 0, 1, 0, 0));
        Set<Integer> hidden = new HashSet<>(Collections.singletonList(1));
        ProjectsScreenModel m = ProjectsScreenModel.build(all, 1, 0, hidden, board(0),
                Collections.<TagRef>emptyList(), TaskOrdering.Mode.UPCOMING, false,
                Collections.<Long>emptySet(), null, NOW, ZONE);
        assertEquals(1, m.bubbles.size());
        assertEquals(2, m.selectedProjectId);

        Set<Integer> every = new HashSet<>(Arrays.asList(1, 2));
        ProjectsScreenModel none = ProjectsScreenModel.build(all, 1, 0, every, null,
                Collections.<TagRef>emptyList(), TaskOrdering.Mode.UPCOMING, false,
                Collections.<Long>emptySet(), null, NOW, ZONE);
        assertTrue(none.noProjects());
        assertFalse(none.noTasks());
        assertTrue(none.sections.isEmpty());
    }

    @Test
    public void collapsedSectionsAreFlaggedAndTagsAreResolved() {
        List<ProjectWithBadge> all = Collections.singletonList(project(1, "Tesis", 0, 0, 1, 0, 0));
        TaskSnapshot tagged = new TaskSnapshot(5, 2, 1, "t5", 0, false, 0, false, false, 0, 0, false, 9);
        ProjectBoard b = board(0, tagged);
        TagRef tag = new TagRef(9, "writing", "pen", "primary", 0);
        ProjectsScreenModel m = ProjectsScreenModel.build(all, 1, 0, Collections.<Integer>emptySet(), b,
                Collections.singletonList(tag), TaskOrdering.Mode.UPCOMING, false,
                new HashSet<>(Collections.singletonList(2L)), null, NOW, ZONE);
        assertFalse(m.sections.get(0).collapsed);
        assertTrue(m.sections.get(1).collapsed);
        ProjectsScreenModel.Row row = m.sections.get(1).rows.get(0);
        assertNotNull(row.tag);
        assertEquals("writing", row.tag.name);
    }

    @Test
    public void templatesAreNotRowsAndDoNotCount() {
        List<ProjectWithBadge> all = Collections.singletonList(project(1, "Tesis", 0, 0, 1, 0, 0));
        TaskSnapshot template = new TaskSnapshot(7, 1, 0, "tpl", day(1, 9), true, 0, false, true, 0, 0, false, 0);
        ProjectBoard b = board(0, template, task(8, 1, 0, 0, 0, false));
        ProjectsScreenModel m = build(all, 1, 0, b, TaskOrdering.Mode.UPCOMING, false, null);
        assertEquals(Collections.singletonList(8L), ids(m.sections.get(0)));
        assertEquals(1, m.sections.get(0).total);
    }
}
