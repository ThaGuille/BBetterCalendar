package com.example.bbettercalendar.ui.calendar.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.example.bbettercalendar.projects.Project;

import org.junit.Test;

/**
 * Tests JVM del mapeo Project -> CalendarItem (spec project-deadlines-progress). Se apoyan en la
 * variante {@code toItem(Project, int)} sin Context: la resolución del color necesita un Context
 * de Android, pero la codificación del id y del instante — que es lo que puede romperse — es pura
 * (mismo truco que FocusAttributionTest).
 */
public class ProjectDeadlineItemMapperTest {

    private static Project project(int id, String name, long deadline) {
        Project p = new Project();
        p.id = id;
        p.name = name;
        p.status = Project.STATUS_ACTIVE;
        p.softDeadlineMillis = deadline;
        return p;
    }

    private static final int ANY_COLOR = 0xFFD9A441;

    @Test
    public void nullProject_mapsToNull() {
        assertNull(ProjectDeadlineItemMapper.toItem(null, ANY_COLOR));
    }

    @Test
    public void projectWithoutDeadline_isSkipped() {
        assertNull(ProjectDeadlineItemMapper.toItem(project(7, "No deadline", 0L), ANY_COLOR));
    }

    @Test
    public void negativeDeadline_isSkipped() {
        assertNull(ProjectDeadlineItemMapper.toItem(project(7, "Bogus", -1L), ANY_COLOR));
    }

    @Test
    public void deadlineItem_isZeroLength() {
        long deadline = 1_700_000_000_000L;
        CalendarItem item = ProjectDeadlineItemMapper.toItem(project(3, "Thesis", deadline), ANY_COLOR);

        assertNotNull(item);
        assertEquals(deadline, item.getStartMillis());
        assertEquals(deadline, item.getEndMillis());
        assertEquals(0L, item.durationMinutes());
        assertEquals(CalendarItem.Type.DEADLINE, item.getType());
        assertEquals("Thesis", item.getTitle());
    }

    @Test
    public void idIsNegated_soItCannotBeMistakenForAnEntry() {
        CalendarItem item = ProjectDeadlineItemMapper.toItem(project(42, "P", 1_000L), ANY_COLOR);
        assertEquals(-42, item.getId());
    }

    @Test
    public void projectIdRoundTrips() {
        for (int id : new int[] { 1, 2, 42, 999, 123_456 }) {
            CalendarItem item = ProjectDeadlineItemMapper.toItem(project(id, "P", 1_000L), ANY_COLOR);
            assertEquals(id, ProjectDeadlineItemMapper.projectIdOf(item));
        }
    }

    @Test
    public void nonDeadlineItem_decodesToZero() {
        CalendarItem entryItem = new CalendarItem(5, "Task", null, 1_000L, 2_000L, ANY_COLOR,
                CalendarItem.Type.TASK);
        assertEquals(0, ProjectDeadlineItemMapper.projectIdOf(entryItem));
        assertEquals(0, ProjectDeadlineItemMapper.projectIdOf(null));
    }
}
