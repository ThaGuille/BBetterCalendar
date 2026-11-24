package com.example.bbettercalendar.ui.projects.quickadd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.projects.domain.TagRef;
import com.example.bbettercalendar.projects.domain.TaskDraft;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class QuickAddChipsTest {

    private static final ZoneId ZONE = ZoneOffset.UTC;
    private static final Locale LOCALE = Locale.ENGLISH;
    private long today;
    private long now;
    private TaskDraft draft;
    private final List<TagRef> tags = Arrays.asList(
            new TagRef(7, "Writing", "pen", "primary", 0),
            new TagRef(8, "Errand", "bag", "muted", 0));

    private final QuickAddChips.Labels labels = new QuickAddChips.Labels() {
        @Override public String today() { return "Today"; }
        @Override public String tomorrow() { return "Tomorrow"; }
        @Override public String priority(int p) { return new String[]{"", "Low", "Medium", "High"}[p]; }
        @Override public String subtasks(int n) { return n == 1 ? "1 subtask" : n + " subtasks"; }
        @Override public String withNote() { return "With note"; }
    };

    @Before
    public void setUp() {
        LocalDate d = LocalDate.of(2026, 11, 24);
        today = d.atStartOfDay(ZONE).toInstant().toEpochMilli();
        now = today + 10 * 3_600_000L;
        draft = new TaskDraft(today);
    }

    private List<QuickAddChips.Chip> chips(boolean repeatAvailable) {
        return QuickAddChips.derive(draft, tags, repeatAvailable, now, ZONE, LOCALE, labels);
    }

    @Test
    public void emptyDraftHasNoChips() {
        assertTrue(chips(false).isEmpty());
    }

    @Test
    public void onlySetValuesAppearInTheFixedOrder() {
        draft.note = "x";
        draft.subtasks.add(new TaskDraft.Subtask("a", false));
        draft.tagId = 8;
        draft.priority = 3;
        draft.setDurationMinutes(50);
        draft.setDay(today);
        List<QuickAddChips.Chip> list = chips(false);
        assertEquals(Arrays.asList(QuickAddChips.Kind.DATE, QuickAddChips.Kind.DURATION,
                QuickAddChips.Kind.PRIORITY, QuickAddChips.Kind.TAG, QuickAddChips.Kind.SUBTASKS,
                QuickAddChips.Kind.NOTE), kinds(list));

        draft.priority = 0;
        draft.tagId = 0;
        draft.note = "   ";
        assertEquals(Arrays.asList(QuickAddChips.Kind.DATE, QuickAddChips.Kind.DURATION,
                QuickAddChips.Kind.SUBTASKS), kinds(chips(false)));
    }

    @Test
    public void dateLabelIsRelativeAndCarriesTheTime() {
        draft.setDay(today);
        assertEquals("Today", chips(false).get(0).label);
        draft.setDay(today + 86_400_000L);
        draft.setTime(17 * 60);
        assertEquals("Tomorrow · 17:00", chips(false).get(0).label);
        draft.setDay(LocalDate.of(2026, 12, 3).atStartOfDay(ZONE).toInstant().toEpochMilli());
        draft.setTime(9 * 60);
        assertEquals("Thu 3 Dec · 9:00", chips(false).get(0).label);
    }

    @Test
    public void timeWithoutDateGetsTodayFromTheDraftRules() {
        draft.setTime(20 * 60);
        assertEquals("Today · 20:00", chips(false).get(0).label);
    }

    @Test
    public void reminderShowsBellAndRepeatIconOnlyWhenAvailable() {
        draft.setDay(today);
        draft.setReminder(TaskDraft.Reminder.MIN_15);
        draft.setRepeat(TaskDraft.Repeat.WEEKLY);
        QuickAddChips.Chip hidden = chips(false).get(0);
        assertTrue(hidden.reminderIcon);
        assertFalse(hidden.repeatIcon);
        QuickAddChips.Chip shown = chips(true).get(0);
        assertTrue(shown.reminderIcon);
        assertTrue(shown.repeatIcon);
    }

    @Test
    public void durationChipMarksPomodoroAndBlock() {
        draft.setDurationMinutes(90);
        QuickAddChips.Chip plain = chips(false).get(0);
        assertEquals("1 h 30", plain.label);
        assertFalse(plain.pomodoroIcon);
        assertFalse(plain.blockIcon);
        draft.setPomodoro(true);
        draft.setBlockOnFocus(true);
        QuickAddChips.Chip marked = chips(false).get(0);
        assertTrue(marked.pomodoroIcon);
        assertTrue(marked.blockIcon);
    }

    @Test
    public void pomodoroOnFreeDurationShowsTheDefault25() {
        draft.setPomodoro(true);
        QuickAddChips.Chip c = chips(false).get(0);
        assertEquals(QuickAddChips.Kind.DURATION, c.kind);
        assertEquals("25 min", c.label);
        assertTrue(c.pomodoroIcon);
    }

    @Test
    public void subtasksUsePluralAndNoteSaysWithNote() {
        draft.subtasks.add(new TaskDraft.Subtask("a", false));
        assertEquals("1 subtask", chips(false).get(0).label);
        draft.subtasks.add(new TaskDraft.Subtask("b", true));
        draft.subtasks.add(new TaskDraft.Subtask("c", false));
        assertEquals("3 subtasks", chips(false).get(0).label);
        draft.note = "Call first";
        List<QuickAddChips.Chip> list = chips(false);
        assertEquals("With note", list.get(list.size() - 1).label);
    }

    @Test
    public void priorityAndTagChipsCarryTheirValue() {
        draft.priority = 2;
        draft.tagId = 7;
        List<QuickAddChips.Chip> list = chips(false);
        assertEquals("Medium", list.get(0).label);
        assertEquals(2, list.get(0).priority);
        assertEquals("Writing", list.get(1).label);
        assertSame(tags.get(0), list.get(1).tag);
        assertNull(list.get(0).tag);
    }

    @Test
    public void unknownTagGetsNoChip() {
        draft.tagId = 99;
        assertTrue(QuickAddChips.derive(draft, Collections.<TagRef>emptyList(), false, now, ZONE,
                LOCALE, labels).isEmpty());
    }

    @Test
    public void rowDotsFollowTheDraft() {
        assertFalse(QuickAddChips.hasDate(draft));
        assertFalse(QuickAddChips.hasMore(draft, true));
        draft.setReminder(TaskDraft.Reminder.AT_TIME);
        assertTrue(QuickAddChips.hasDate(draft));
        assertTrue(QuickAddChips.hasMore(draft, false));
        draft.setReminder(TaskDraft.Reminder.NONE);
        draft.setRepeat(TaskDraft.Repeat.DAILY);
        assertFalse(QuickAddChips.hasMore(draft, false));
        assertTrue(QuickAddChips.hasMore(draft, true));
    }

    private static List<QuickAddChips.Kind> kinds(List<QuickAddChips.Chip> chips) {
        java.util.ArrayList<QuickAddChips.Kind> out = new java.util.ArrayList<>();
        for (QuickAddChips.Chip c : chips) out.add(c.kind);
        return out;
    }
}
