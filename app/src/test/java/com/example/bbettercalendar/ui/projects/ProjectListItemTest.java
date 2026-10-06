package com.example.bbettercalendar.ui.projects;

import static org.junit.Assert.assertEquals;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.Project;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

// u16: derivaciones puras de la tarjeta de proyecto (pomodoros desde minutos objetivo, pomodoros de
// hoy, estimacion restante).
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
}
