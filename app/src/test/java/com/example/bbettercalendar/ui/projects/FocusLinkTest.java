package com.example.bbettercalendar.ui.projects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.projects.domain.TaskSnapshot;
import com.example.bbettercalendar.ui.focus.TaskFocusRequest;

import org.junit.Test;

public class FocusLinkTest {

    private static TaskSnapshot task(int target, int attributed, boolean block) {
        return new TaskSnapshot(7, 1, 0, "Escribir el capitulo 3", 0L, false, 0, false, false,
                target, attributed, block, 0);
    }

    @Test
    public void requestCarriesTaskAndProjectFields() {
        TaskFocusRequest r = FocusLink.request(task(120, 35, true), 120, "Tesis final", "book", 2, 3);
        assertEquals(7, r.getEntryId());
        assertEquals("Escribir el capitulo 3", r.getTitle());
        assertEquals(120, r.getGoalMinutes());
        assertEquals(3, r.getProjectId());
        assertEquals("Tesis final", r.getProjectName());
        assertEquals("book", r.getProjectIconKey());
        assertEquals(2, r.getProjectColorIndex());
    }

    @Test
    public void requestCarriesAttributedMinutes() {
        TaskFocusRequest r = FocusLink.request(task(120, 35, false), 120, "P", "box", 0, 1);
        assertEquals(35, r.getAttributedMinutes());
    }

    @Test
    public void requestCarriesBlockOnFocus() {
        assertTrue(FocusLink.request(task(60, 0, true), 60, "P", "box", 0, 1).isBlockOnFocus());
        assertFalse(FocusLink.request(task(60, 0, false), 60, "P", "box", 0, 1).isBlockOnFocus());
    }

    @Test
    public void noGoalKeepsTheDurationAsGoal() {
        assertEquals(45, FocusLink.goalForTask(45 * 60_000L));
        assertEquals(1, FocusLink.goalForTask(20_000L));
    }

    @Test
    public void noGoalNorDurationDefaultsTo25() {
        assertEquals(25, FocusLink.goalForTask(0L));
        assertEquals(FocusLink.DEFAULT_POMODORO_MINUTES, FocusLink.goalForTask(-5L));
    }

    @Test
    public void runningSessionLeadsToOpenOnly() {
        assertEquals(FocusLink.Action.OPEN_ONLY, FocusLink.decide(task(60, 0, false), true));
        assertEquals(FocusLink.Action.OPEN_ONLY, FocusLink.decide(task(0, 0, false), true));
    }

    @Test
    public void idleTaskWithGoalStartsAndWithoutGoalEnablesPomodoro() {
        assertEquals(FocusLink.Action.START, FocusLink.decide(task(60, 0, false), false));
        assertEquals(FocusLink.Action.ENABLE_POMODORO_THEN_START,
                FocusLink.decide(task(0, 0, false), false));
    }
}
