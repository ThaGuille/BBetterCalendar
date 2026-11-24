package com.example.bbettercalendar.notifications.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.notifications.project.ProjectDeadlineScheduler;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class TaskStartPolicyTest {

    private static final long HOUR = 3_600_000L;

    private static boolean eligible(boolean hasTime, long duration, int target, boolean done,
                                    boolean template, boolean dismissed, boolean active, boolean atTime) {
        return TaskStartPolicy.isEligible(hasTime, duration, target, done, template, dismissed, active, atTime);
    }

    @Test
    public void aTimedTaskWithDurationIsEligible() {
        assertTrue(eligible(true, HOUR, 0, false, false, false, true, false));
    }

    @Test
    public void aTimedTaskWithPomodoroOnlyIsEligible() {
        assertTrue(eligible(true, 0, 25, false, false, false, true, false));
    }

    @Test
    public void aTimedTaskWithNeitherDurationNorPomodoroIsNotEligible() {
        assertFalse(eligible(true, 0, 0, false, false, false, true, false));
    }

    @Test
    public void theAtTimeReminderMakesATimedTaskWithoutDurationEligible() {
        assertTrue(eligible(true, 0, 0, false, false, false, true, true));
    }

    @Test
    public void theAtTimeReminderNeverMakesAnUntimedTaskEligible() {
        assertFalse(eligible(false, HOUR, 25, false, false, false, true, true));
    }

    @Test
    public void doneTemplateDismissedAndArchivedProjectTasksAreNotEligible() {
        assertFalse(eligible(true, HOUR, 25, true, false, false, true, true));
        assertFalse(eligible(true, HOUR, 25, false, true, false, true, true));
        assertFalse(eligible(true, HOUR, 25, false, false, true, true, true));
        assertFalse(eligible(true, HOUR, 25, false, false, false, false, true));
    }

    @Test
    public void midnightCountsAsNoTimeAndOtherHoursAsTime() {
        TimeZone zone = TimeZone.getTimeZone("Europe/Madrid");
        Calendar c = Calendar.getInstance(zone);
        c.set(2026, Calendar.NOVEMBER, 24, 0, 0, 0);
        assertFalse(TaskStartPolicy.hasTimeOfDay(c.getTimeInMillis(), zone));
        c.set(Calendar.MINUTE, 5);
        assertTrue(TaskStartPolicy.hasTimeOfDay(c.getTimeInMillis(), zone));
        assertFalse(TaskStartPolicy.hasTimeOfDay(0L, zone));
    }

    @Test
    public void theAlarmFiresExactlyAtTheStartAndSnoozeIsTenMinutesLater() {
        assertEquals(1_700_000_000_000L, TaskStartPolicy.triggerMillis(1_700_000_000_000L));
        assertEquals(1_000L + 10 * 60_000L, TaskStartPolicy.snoozeMillis(1_000L));
    }

    @Test
    public void blockingTasksUseTheVibratingChannel() {
        assertEquals(NotificationChannels.CHANNEL_TASK_START_BLOCK, TaskStartPolicy.channelFor(true));
        assertEquals(NotificationChannels.CHANNEL_TASK_START, TaskStartPolicy.channelFor(false));
        assertNotEquals(TaskStartPolicy.channelFor(true), TaskStartPolicy.channelFor(false));
    }

    @Test
    public void contentWithBlockShowsProjectDurationAndBlock() {
        TaskStartPolicy.Content c = TaskStartPolicy.content(" Tesis final ", 2 * HOUR, 0, true);
        assertTrue(c.showProject);
        assertEquals("Tesis final", c.projectName);
        assertEquals(120, c.durationMinutes);
        assertTrue(c.showBlock);
    }

    @Test
    public void contentWithoutBlockOrProjectOmitsThem() {
        TaskStartPolicy.Content c = TaskStartPolicy.content(null, 0, 25, false);
        assertFalse(c.showProject);
        assertNull(c.projectName);
        assertEquals(25, c.durationMinutes);
        assertFalse(c.showBlock);
        assertFalse(TaskStartPolicy.content("  ", 0, 0, false).showProject);
    }

    @Test
    public void shownMinutesPreferTheGoalAndRoundDurationDownToAtLeastOne() {
        assertEquals(50, TaskStartPolicy.durationMinutes(HOUR, 50));
        assertEquals(1, TaskStartPolicy.durationMinutes(30_000L, 0));
        assertEquals(0, TaskStartPolicy.durationMinutes(0L, 0));
    }

    @Test
    public void theStartButtonNeedsSomethingToFocus() {
        assertTrue(TaskStartPolicy.hasStartAction(HOUR, 0));
        assertTrue(TaskStartPolicy.hasStartAction(0, 25));
        assertFalse(TaskStartPolicy.hasStartAction(0, 0));
    }

    @Test
    public void requestCodesNeverCollideWithTheOtherAlarmNamespaces() {
        for (int id = 1; id <= 2_000; id++) {
            int start = TaskStartScheduler.requestCodeFor(id, 0);
            int snoozed = TaskStartScheduler.snoozeRequestCodeFor(id, 0);
            assertNotEquals(start, snoozed);
            for (int offset = 0; offset < 7; offset++) {
                assertNotEquals(EventReminderScheduler.requestCodeFor(id, offset), start);
                assertNotEquals(EventReminderScheduler.requestCodeFor(id, offset), snoozed);
            }
            for (int offset = 0; offset < ProjectDeadlineScheduler.OFFSET_MILLIS.length; offset++) {
                assertNotEquals(ProjectDeadlineScheduler.requestCodeFor(id, offset), start);
                assertNotEquals(ProjectDeadlineScheduler.requestCodeFor(id, offset), snoozed);
            }
            assertNotEquals(TaskStartIds.REQ_ACTION_SNOOZE_BASE + id, start);
            assertNotEquals(TaskStartIds.REQ_ACTION_START_BASE + id, snoozed);
        }
    }
}
