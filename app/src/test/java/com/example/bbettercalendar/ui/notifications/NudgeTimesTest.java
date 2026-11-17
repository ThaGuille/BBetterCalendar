package com.example.bbettercalendar.ui.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NudgeTimesTest {

    @Test
    public void clampsBelowWindowToEight() {
        assertEquals(8 * 60, NudgeTimes.clamp(NudgeTimes.toMinutes(6, 30)));
    }

    @Test
    public void clampsAboveWindowTo2159() {
        assertEquals(21 * 60 + 59, NudgeTimes.clamp(NudgeTimes.toMinutes(23, 15)));
        assertEquals(21 * 60 + 59, NudgeTimes.clamp(NudgeTimes.toMinutes(22, 0)));
    }

    @Test
    public void keepsTimesInsideWindow() {
        assertEquals(20 * 60 + 30, NudgeTimes.clamp(20 * 60 + 30));
        assertTrue(NudgeTimes.isAllowed(8 * 60));
        assertTrue(NudgeTimes.isAllowed(21 * 60 + 59));
        assertFalse(NudgeTimes.isAllowed(22 * 60));
        assertFalse(NudgeTimes.isAllowed(7 * 60 + 59));
    }

    @Test
    public void splitsMinutes() {
        assertEquals(20, NudgeTimes.hourOf(20 * 60 + 30));
        assertEquals(30, NudgeTimes.minuteOf(20 * 60 + 30));
    }
}
