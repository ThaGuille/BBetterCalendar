package com.example.bbettercalendar.projects.domain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.projects.domain.TaskDraft.Reminder;
import com.example.bbettercalendar.projects.domain.TaskDraft.Repeat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Test;

public class TaskDraftTest {

    private static final long TODAY = 1_790_000_000_000L;
    private static final long OTHER_DAY = TODAY + 3 * 86_400_000L;

    private static TaskDraft draft() {
        return new TaskDraft(TODAY);
    }

    // ---- the seven rules ----

    @Test
    public void rule1_timeWithoutDateSetsToday() {
        TaskDraft d = draft();
        d.setTime(9 * 60);
        assertEquals(Long.valueOf(TODAY), d.getDayStartMillis());
        assertEquals(Integer.valueOf(540), d.getTimeOfDayMinutes());
    }

    @Test
    public void rule1_timeKeepsAnExistingDate() {
        TaskDraft d = draft();
        d.setDay(OTHER_DAY);
        d.setTime(9 * 60);
        assertEquals(Long.valueOf(OTHER_DAY), d.getDayStartMillis());
    }

    @Test
    public void rule2_clearingTheDateClearsTimeReminderAndRepeat() {
        TaskDraft d = draft();
        d.setDay(OTHER_DAY);
        d.setTime(600);
        d.setReminder(Reminder.MIN_15);
        d.setRepeat(Repeat.WEEKLY);
        d.setDay(null);
        assertNull(d.getDayStartMillis());
        assertNull(d.getTimeOfDayMinutes());
        assertEquals(Reminder.NONE, d.getReminder());
        assertEquals(Repeat.NONE, d.getRepeat());
    }

    @Test
    public void rule2_clearingTheDateKeepsOtherFields() {
        TaskDraft d = draft();
        d.setDay(OTHER_DAY);
        d.setDurationMinutes(50);
        d.setPomodoro(true);
        d.priority = 2;
        d.setDay(null);
        assertEquals(50, d.getDurationMinutes());
        assertTrue(d.isPomodoro());
        assertEquals(2, d.priority);
    }

    @Test
    public void rule2b_clearingTheTimeClearsTheReminderButNotTheDate() {
        TaskDraft d = draft();
        d.setDay(OTHER_DAY);
        d.setTime(600);
        d.setReminder(Reminder.HOUR_1);
        d.setTime(null);
        assertEquals(Reminder.NONE, d.getReminder());
        assertEquals(Long.valueOf(OTHER_DAY), d.getDayStartMillis());
    }

    @Test
    public void rule3_reminderWithoutTimeSetsSeventeenHundredToday() {
        TaskDraft d = draft();
        d.setReminder(Reminder.AT_TIME);
        assertEquals(Integer.valueOf(17 * 60), d.getTimeOfDayMinutes());
        assertEquals(Long.valueOf(TODAY), d.getDayStartMillis());
    }

    @Test
    public void rule3_reminderKeepsTheDateAndKeepsAnExistingTime() {
        TaskDraft d = draft();
        d.setDay(OTHER_DAY);
        d.setReminder(Reminder.MIN_15);
        assertEquals(Long.valueOf(OTHER_DAY), d.getDayStartMillis());
        assertEquals(Integer.valueOf(17 * 60), d.getTimeOfDayMinutes());

        TaskDraft e = draft();
        e.setDay(OTHER_DAY);
        e.setTime(8 * 60 + 30);
        e.setReminder(Reminder.HOUR_1);
        assertEquals(Integer.valueOf(8 * 60 + 30), e.getTimeOfDayMinutes());
    }

    @Test
    public void rule3_reminderNoneDoesNotInventTimeOrDate() {
        TaskDraft d = draft();
        d.setReminder(Reminder.NONE);
        assertNull(d.getTimeOfDayMinutes());
        assertNull(d.getDayStartMillis());
    }

    @Test
    public void rule4_repeatWithoutDateSetsToday() {
        TaskDraft d = draft();
        d.setRepeat(Repeat.DAILY);
        assertEquals(Long.valueOf(TODAY), d.getDayStartMillis());
        assertEquals(Repeat.DAILY, d.getRepeat());
    }

    @Test
    public void rule4_repeatNoneLeavesTheDateEmpty() {
        TaskDraft d = draft();
        d.setRepeat(Repeat.NONE);
        assertNull(d.getDayStartMillis());
    }

    @Test
    public void rule5_durationLibreTurnsPomodoroAndBlockOff() {
        TaskDraft d = draft();
        d.setDurationMinutes(90);
        d.setPomodoro(true);
        d.setBlockOnFocus(true);
        d.setDurationMinutes(0);
        assertFalse(d.isPomodoro());
        assertFalse(d.isBlockOnFocus());
        assertEquals(0, d.getDurationMinutes());
    }

    @Test
    public void rule5_aRealDurationLeavesPomodoroAndBlockAlone() {
        TaskDraft d = draft();
        d.setDurationMinutes(90);
        d.setPomodoro(true);
        d.setBlockOnFocus(true);
        d.setDurationMinutes(120);
        assertTrue(d.isPomodoro());
        assertTrue(d.isBlockOnFocus());
    }

    @Test
    public void rule6_pomodoroOnWithLibreSetsTwentyFiveMinutes() {
        TaskDraft d = draft();
        d.togglePomodoro();
        assertTrue(d.isPomodoro());
        assertEquals(25, d.getDurationMinutes());
    }

    @Test
    public void rule6_pomodoroOnKeepsAnExistingDuration() {
        TaskDraft d = draft();
        d.setDurationMinutes(120);
        d.togglePomodoro();
        assertEquals(120, d.getDurationMinutes());
    }

    @Test
    public void rule7_blockOnWithLibreSetsFiftyMinutes() {
        TaskDraft d = draft();
        d.toggleBlockOnFocus();
        assertTrue(d.isBlockOnFocus());
        assertEquals(50, d.getDurationMinutes());
    }

    @Test
    public void rule7_blockOnKeepsAnExistingDuration() {
        TaskDraft d = draft();
        d.setDurationMinutes(25);
        d.toggleBlockOnFocus();
        assertEquals(25, d.getDurationMinutes());
    }

    @Test
    public void togglingPomodoroOffKeepsTheDuration() {
        TaskDraft d = draft();
        d.togglePomodoro();
        d.togglePomodoro();
        assertFalse(d.isPomodoro());
        assertEquals(25, d.getDurationMinutes());
    }

    // ---- save and mapping ----

    @Test
    public void canSave_onlyWithANonBlankTitle() {
        TaskDraft d = draft();
        assertFalse(d.canSave());
        d.title = "   ";
        assertFalse(d.canSave());
        d.title = " Comprar pan ";
        assertTrue(d.canSave());
    }

    @Test
    public void targetMinutes_isTheDurationOnlyWithPomodoro() {
        TaskDraft d = draft();
        d.setDurationMinutes(90);
        assertEquals(0, d.targetMinutes());
        d.setPomodoro(true);
        assertEquals(90, d.targetMinutes());
        assertEquals(90L * 60_000L, d.durationMillis());
    }

    @Test
    public void notificationFlags_marksOnlyTheOffsetOfTheReminder() {
        TaskDraft d = draft();
        d.setReminder(Reminder.MIN_15);
        assertArrayEquals(new boolean[]{false, false, true, false, false, false, false, false}, d.toNotificationFlags());
        d.setReminder(Reminder.HOUR_1);
        assertArrayEquals(new boolean[]{false, false, false, false, true, false, false, false}, d.toNotificationFlags());
    }

    @Test
    public void notificationFlags_atTimeSetsOnlyTheExtraSlotAndNoneSetsNothing() {
        TaskDraft d = draft();
        assertArrayEquals(new boolean[8], d.toNotificationFlags());
        assertFalse(TaskDraft.hasAtTimeFlag(d.toNotificationFlags()));
        d.setReminder(Reminder.AT_TIME);
        assertArrayEquals(new boolean[]{false, false, false, false, false, false, false, true},
                d.toNotificationFlags());
        assertTrue(TaskDraft.hasAtTimeFlag(d.toNotificationFlags()));
    }

    @Test
    public void notificationFlags_otherRemindersAndShortArraysAreNotAtTime() {
        for (Reminder r : new Reminder[]{Reminder.MIN_15, Reminder.HOUR_1}) {
            TaskDraft d = draft();
            d.setReminder(r);
            assertFalse(TaskDraft.hasAtTimeFlag(d.toNotificationFlags()));
        }
        assertFalse(TaskDraft.hasAtTimeFlag(null));
        assertFalse(TaskDraft.hasAtTimeFlag(new boolean[7]));
    }

    @Test
    public void notificationFlags_roundTripsMin15Hour1AndNone() {
        for (Reminder r : new Reminder[]{Reminder.NONE, Reminder.AT_TIME, Reminder.MIN_15, Reminder.HOUR_1}) {
            TaskDraft d = draft();
            d.setReminder(r);
            assertEquals(r, TaskDraft.fromNotificationFlags(d.toNotificationFlags()));
        }
    }

    @Test
    public void fromNotificationFlags_ignoresOffsetsThatHaveNoReminderChoice() {
        assertEquals(Reminder.NONE, TaskDraft.fromNotificationFlags(
                new boolean[]{true, true, false, true, false, true, true}));
        assertEquals(Reminder.NONE, TaskDraft.fromNotificationFlags(null));
        assertEquals(Reminder.NONE, TaskDraft.fromNotificationFlags(new boolean[2]));
    }

    @Test
    public void draftSurvivesSerialization() throws Exception {
        TaskDraft d = draft();
        d.title = "Informe";
        d.setReminder(Reminder.MIN_15);
        d.setPomodoro(true);
        d.subtasks.add(new TaskDraft.Subtask("Esquema", true));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeObject(d);
        out.close();
        TaskDraft back = (TaskDraft) new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())).readObject();
        assertEquals("Informe", back.title);
        assertEquals(Reminder.MIN_15, back.getReminder());
        assertEquals(Integer.valueOf(17 * 60), back.getTimeOfDayMinutes());
        assertEquals(25, back.getDurationMinutes());
        assertEquals("Esquema", back.subtasks.get(0).title);
        assertTrue(back.subtasks.get(0).done);
    }
}
