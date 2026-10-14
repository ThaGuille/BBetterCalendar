package com.example.bbettercalendar.ui.projects;

import static org.junit.Assert.assertEquals;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.Project;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// u16: derivaciones puras de la tarjeta de proyecto (pomodoros desde minutos objetivo, pomodoros de
// hoy, estimacion restante, orden por urgencia).
public class ProjectListItemTest {

    private static CalendarEntry entry(int id, int targetMinutes, boolean done) {
        return new CalendarEntry.EventBuilder()
                .setEventId(id)
                .setEventTitle("t" + id)
                .setEventTargetMinutes(targetMinutes)
                .setEventIsDone(done)
                .build();
    }

    @Test
    public void pomodorosFor_roundsUp_andIsZeroWithoutTarget() {
        assertEquals(0, ProjectListItem.pomodorosFor(0, 25));
        assertEquals(1, ProjectListItem.pomodorosFor(1, 25));
        assertEquals(1, ProjectListItem.pomodorosFor(25, 25));
        assertEquals(2, ProjectListItem.pomodorosFor(26, 25));
        assertEquals(3, ProjectListItem.pomodorosFor(60, 25));
    }

    @Test
    public void pomodorosFor_fallsBackToDefaultWhenPomodoroSizeInvalid() {
        assertEquals(2, ProjectListItem.pomodorosFor(40, 0));
    }

    @Test
    public void pomodorosToday_floorsTodayMinutes() {
        ProjectListItem item = new ProjectListItem(new Project(), 0, 0,
                Collections.<CalendarEntry>emptyList(), 25, 80);
        assertEquals(3, item.pomodorosToday());
    }

    @Test
    public void estimatedRemaining_sumsOnlyOpenItemsWithTarget() {
        ProjectListItem item = new ProjectListItem(new Project(), 1, 4,
                Arrays.asList(entry(1, 25, true), entry(2, 50, false),
                        entry(3, 0, false), entry(4, 30, false)), 25, 0);
        assertEquals(80, item.estimatedRemainingMinutes());
    }

    @Test
    public void percent_isZeroWithoutItems() {
        assertEquals(0, new ProjectListItem(new Project(), 0, 0).percent());
        assertEquals(40, new ProjectListItem(new Project(), 2, 5).percent());
    }

    @Test
    public void estimatedRemaining_discountsAttributedMinutes() {
        CalendarEntry half = entry(1, 100, false);
        half.setAttributedMinutes(50);
        CalendarEntry over = entry(2, 30, false);
        over.setAttributedMinutes(45);
        ProjectListItem item = new ProjectListItem(new Project(), 0, 2,
                Arrays.asList(half, over), 25, 0);
        assertEquals(50, ProjectListItem.remainingMinutes(half));
        assertEquals(0, ProjectListItem.remainingMinutes(over));
        assertEquals(50, item.estimatedRemainingMinutes());
    }

    private static ProjectListItem project(int id, int status, long deadline, long createdAt) {
        Project p = new Project();
        p.id = id;
        p.status = status;
        p.softDeadlineMillis = deadline;
        p.createdAtMillis = createdAt;
        return new ProjectListItem(p, 0, 0);
    }

    @Test
    public void byUrgency_activeFirst_thenNearestDeadline_thenNoDeadlineNewestFirst() {
        List<ProjectListItem> items = new ArrayList<>(Arrays.asList(
                project(1, Project.STATUS_ACTIVE, 0L, 500L),
                project(2, Project.STATUS_COMPLETED, 100L, 900L),
                project(3, Project.STATUS_ACTIVE, 3000L, 100L),
                project(4, Project.STATUS_ACTIVE, 1000L, 200L),
                project(5, Project.STATUS_ACTIVE, 0L, 800L)));
        Collections.sort(items, ProjectListItem.BY_URGENCY);
        int[] order = new int[items.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = items.get(i).project.id;
        }
        org.junit.Assert.assertArrayEquals(new int[]{4, 3, 5, 1, 2}, order);
    }
}
