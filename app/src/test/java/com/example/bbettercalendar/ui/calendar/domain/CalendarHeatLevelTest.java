package com.example.bbettercalendar.ui.calendar.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CalendarHeatLevelTest {

    @Test
    public void zeroAndNegativeMinutesAreLevelZero() {
        assertEquals(0, CalendarHeatLevel.levelFor(0));
        assertEquals(0, CalendarHeatLevel.levelFor(-5));
    }

    @Test
    public void fewMinutesAreLevelOne() {
        assertEquals(1, CalendarHeatLevel.levelFor(1));
        assertEquals(1, CalendarHeatLevel.levelFor(CalendarHeatLevel.LEVEL_2_MIN - 1));
    }

    @Test
    public void mediumMinutesAreLevelTwo() {
        assertEquals(2, CalendarHeatLevel.levelFor(CalendarHeatLevel.LEVEL_2_MIN));
        assertEquals(2, CalendarHeatLevel.levelFor(CalendarHeatLevel.LEVEL_3_MIN - 1));
    }

    @Test
    public void manyMinutesAreLevelThree() {
        assertEquals(3, CalendarHeatLevel.levelFor(CalendarHeatLevel.LEVEL_3_MIN));
        assertEquals(3, CalendarHeatLevel.levelFor(600));
    }

    @Test
    public void levelNeverExceedsMax() {
        assertEquals(CalendarHeatLevel.MAX_LEVEL, CalendarHeatLevel.levelFor(Integer.MAX_VALUE));
    }
}
