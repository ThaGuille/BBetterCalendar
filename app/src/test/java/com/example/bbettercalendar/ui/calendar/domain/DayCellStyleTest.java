package com.example.bbettercalendar.ui.calendar.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** pomodoro-calendario-ui u5: today and selected are different marks that can coexist, over the focus tint. */
public class DayCellStyleTest {

    @Test
    public void theFourTodaySelectedCombinationsArePairwiseDifferent() {
        for (int level = 0; level <= CalendarHeatLevel.MAX_LEVEL; level++) {
            List<DayCellStyle> styles = new ArrayList<>();
            styles.add(DayCellStyle.of(false, false, level));
            styles.add(DayCellStyle.of(true, false, level));
            styles.add(DayCellStyle.of(false, true, level));
            styles.add(DayCellStyle.of(true, true, level));
            for (int i = 0; i < styles.size(); i++) {
                for (int j = i + 1; j < styles.size(); j++) {
                    assertNotEquals("level " + level + ": " + styles.get(i) + " vs " + styles.get(j),
                            styles.get(i), styles.get(j));
                }
            }
        }
    }

    @Test
    public void todayAndSelectedShowsBothMarks() {
        DayCellStyle both = DayCellStyle.of(true, true, 2);
        assertTrue("today circle", both.todayCircle);
        assertTrue("selection outline", both.selectionOutline);
        assertTrue("number on the circle", both.numberOnCircle);

        DayCellStyle todayOnly = DayCellStyle.of(true, false, 2);
        assertTrue(todayOnly.todayCircle);
        assertFalse(todayOnly.selectionOutline);

        DayCellStyle selectedOnly = DayCellStyle.of(false, true, 2);
        assertFalse(selectedOnly.todayCircle);
        assertFalse("number stays the surface colour off the circle", selectedOnly.numberOnCircle);
        assertTrue(selectedOnly.selectionOutline);

        DayCellStyle plain = DayCellStyle.of(false, false, 2);
        assertFalse(plain.todayCircle);
        assertFalse(plain.selectionOutline);
    }

    @Test
    public void selectingADayKeepsItsFocusLevel() {
        for (int level = 0; level <= CalendarHeatLevel.MAX_LEVEL; level++) {
            for (boolean today : new boolean[]{false, true}) {
                assertEquals(level, DayCellStyle.of(today, false, level).focusLevel);
                assertEquals("selected must not hide the focus tint",
                        level, DayCellStyle.of(today, true, level).focusLevel);
            }
        }
    }

    @Test
    public void focusLevelComesFromTheHeatLevelAndIsClamped() {
        assertEquals(CalendarHeatLevel.MAX_LEVEL,
                DayCellStyle.of(false, true, CalendarHeatLevel.levelFor(240)).focusLevel);
        assertEquals(0, DayCellStyle.of(false, true, CalendarHeatLevel.levelFor(0)).focusLevel);
        assertEquals(CalendarHeatLevel.MAX_LEVEL, DayCellStyle.of(false, false, 9).focusLevel);
        assertEquals(0, DayCellStyle.of(false, false, -1).focusLevel);
    }
}
