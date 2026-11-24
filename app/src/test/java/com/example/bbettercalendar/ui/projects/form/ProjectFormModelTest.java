package com.example.bbettercalendar.ui.projects.form;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.domain.DateChoices;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

public class ProjectFormModelTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    // 15 January 2026, midday.
    private static final long NOW = LocalDate.of(2026, 1, 15).atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli();

    private static Project project(String name, long deadline, int color, String icon, int area) {
        Project p = new Project();
        p.name = name;
        p.notes = "";
        p.softDeadlineMillis = deadline;
        p.colorIndex = color;
        p.iconKey = icon;
        p.areaId = area;
        return p;
    }

    @Test
    public void endOfMonthIsTheLastDayOfThisMonth() {
        ProjectFormModel m = ProjectFormModel.forNew(0);
        m.chooseDeadline(ProjectFormModel.DeadlineChoice.END_OF_MONTH, NOW, ZONE);
        long expected = LocalDate.of(2026, 1, 31).atStartOfDay(ZONE).toInstant().toEpochMilli();
        assertEquals(expected, m.getDeadlineMillis());
        assertEquals(ProjectFormModel.DeadlineChoice.END_OF_MONTH, m.deadlineChoice(NOW, ZONE));
    }

    @Test
    public void inAMonthIsOneCalendarMonthAfterToday() {
        ProjectFormModel m = ProjectFormModel.forNew(0);
        m.chooseDeadline(ProjectFormModel.DeadlineChoice.IN_A_MONTH, NOW, ZONE);
        long expected = LocalDate.of(2026, 2, 15).atStartOfDay(ZONE).toInstant().toEpochMilli();
        assertEquals(expected, m.getDeadlineMillis());
        assertEquals(ProjectFormModel.DeadlineChoice.IN_A_MONTH, m.deadlineChoice(NOW, ZONE));
    }

    @Test
    public void noneClearsTheDeadlineAndAnotherDateIsOther() {
        ProjectFormModel m = ProjectFormModel.forNew(0);
        assertEquals(ProjectFormModel.DeadlineChoice.NONE, m.deadlineChoice(NOW, ZONE));
        m.setDeadlineMillis(DateChoices.tomorrow(NOW, ZONE));
        assertEquals(ProjectFormModel.DeadlineChoice.OTHER, m.deadlineChoice(NOW, ZONE));
        m.chooseDeadline(ProjectFormModel.DeadlineChoice.NONE, NOW, ZONE);
        assertEquals(0L, m.getDeadlineMillis());
        assertEquals(ProjectFormModel.DeadlineChoice.NONE, m.deadlineChoice(NOW, ZONE));
    }

    @Test
    public void saveIsEnabledOnlyWithANonBlankName() {
        ProjectFormModel m = ProjectFormModel.forNew(0);
        assertFalse(m.isSaveEnabled());
        m.setName("   ");
        assertFalse(m.isSaveEnabled());
        m.setName(" Huerto ");
        assertTrue(m.isSaveEnabled());
        m.setName(null);
        assertFalse(m.isSaveEnabled());
    }

    @Test
    public void aFreshFormIsCleanAndAnyChangeMakesItDirty() {
        ProjectFormModel m = ProjectFormModel.forNew(2);
        assertFalse(m.isDirty());
        m.setName("x");
        assertTrue(m.isDirty());
        m.setName("");
        assertFalse(m.isDirty());
        m.setIconKey("plane");
        assertTrue(m.isDirty());
        m.setIconKey("box");
        assertFalse(m.isDirty());
        m.setColorIndex(0);
        assertTrue(m.isDirty());
    }

    @Test
    public void editingStartsCleanAndRestoringTheOldValuesIsCleanAgain() {
        ProjectFormModel m = ProjectFormModel.forEdit(project("Tesis", 5000L, 2, "cap", 7));
        assertFalse(m.isDirty());
        assertTrue(m.isSaveEnabled());
        assertEquals("cap", m.getIconKey());
        m.toggleArea(7); // the project's own area: tapping it clears it
        assertEquals(0, m.getAreaId());
        assertTrue(m.isDirty());
        m.toggleArea(7); // picked again: back to what it was
        assertFalse(m.isDirty());
        m.setDeadlineMillis(6000L);
        assertTrue(m.isDirty());
        m.setDeadlineMillis(5000L);
        assertFalse(m.isDirty());
    }

    @Test
    public void defaultColourIsTheNextInTheCycleByProjectCount() {
        assertEquals(0, ProjectFormModel.defaultColorIndex(0));
        assertEquals(1, ProjectFormModel.defaultColorIndex(1));
        assertEquals(3, ProjectFormModel.defaultColorIndex(3));
        assertEquals(0, ProjectFormModel.defaultColorIndex(4));
        assertEquals(2, ProjectFormModel.defaultColorIndex(10));
        assertEquals(2, ProjectFormModel.forNew(6).getColorIndex());
    }

    @Test
    public void tappingTheChosenAreaClearsItAndAnotherReplacesIt() {
        ProjectFormModel m = ProjectFormModel.forNew(0);
        assertEquals(0, m.getAreaId());
        m.toggleArea(3);
        assertEquals(3, m.getAreaId());
        m.toggleArea(5);
        assertEquals(5, m.getAreaId());
        m.toggleArea(5);
        assertEquals(0, m.getAreaId());
    }
}
