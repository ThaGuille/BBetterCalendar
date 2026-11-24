package com.example.bbettercalendar.ui.projects.quickadd;

import com.example.bbettercalendar.projects.domain.TagRef;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.ui.projects.picker.PickerLabels;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The value chips of the quick-add box (spec 3.3 contents 3), derived from a {@link TaskDraft}: only the
 * values that are set, always in the same order (date and time, duration, priority, tag, subtasks, note).
 * Pure Java (no Android), so the JVM test covers it; the dialog turns each {@link Chip} into a view.
 */
public final class QuickAddChips {
    private QuickAddChips() {}

    public enum Kind { DATE, DURATION, PRIORITY, TAG, SUBTASKS, NOTE }

    /** The words the chips need from the resources (English / Spanish, plurals). */
    public interface Labels {
        String today();

        String tomorrow();

        String priority(int priority);

        String subtasks(int count);

        String withNote();
    }

    /** One chip: its kind, its text and the little icons it carries. */
    public static final class Chip {
        public final Kind kind;
        public final String label;
        /** Reminder bell (date chip). */
        public final boolean reminderIcon;
        /** Repeat icon (date chip); never true while Repeat is not available. */
        public final boolean repeatIcon;
        /** Amber stopwatch (duration chip with pomodoro). */
        public final boolean pomodoroIcon;
        /** Lock (duration chip with soft block). */
        public final boolean blockIcon;
        /** Priority 1..3 (priority chip), else 0. */
        public final int priority;
        /** The tag of a tag chip, else null. */
        public final TagRef tag;

        Chip(Kind kind, String label, boolean reminderIcon, boolean repeatIcon, boolean pomodoroIcon,
             boolean blockIcon, int priority, TagRef tag) {
            this.kind = kind;
            this.label = label;
            this.reminderIcon = reminderIcon;
            this.repeatIcon = repeatIcon;
            this.pomodoroIcon = pomodoroIcon;
            this.blockIcon = blockIcon;
            this.priority = priority;
            this.tag = tag;
        }
    }

    /**
     * @param repeatAvailable whether the Repeat option exists in this build (RepeatAvailability): when
     *                        false no repeat icon is ever shown
     * @param tags            the project's tags, to resolve the tag of the draft (a tag that is not in the
     *                        list gets no chip)
     */
    public static List<Chip> derive(TaskDraft draft, List<TagRef> tags, boolean repeatAvailable,
                                    long nowMillis, ZoneId zone, Locale locale, Labels labels) {
        List<Chip> chips = new ArrayList<>();
        if (draft == null) return chips;

        String date = dateLabel(draft, nowMillis, zone, locale, labels);
        if (date != null) {
            chips.add(new Chip(Kind.DATE, date,
                    draft.getReminder() != TaskDraft.Reminder.NONE,
                    repeatAvailable && draft.getRepeat() != TaskDraft.Repeat.NONE,
                    false, false, 0, null));
        }
        if (draft.getDurationMinutes() > 0) {
            chips.add(new Chip(Kind.DURATION, PickerLabels.durationLabel(draft.getDurationMinutes()),
                    false, false, draft.isPomodoro(), draft.isBlockOnFocus(), 0, null));
        }
        if (draft.priority > 0 && draft.priority <= 3) {
            chips.add(new Chip(Kind.PRIORITY, labels.priority(draft.priority),
                    false, false, false, false, draft.priority, null));
        }
        if (draft.tagId != 0) {
            TagRef tag = find(tags, draft.tagId);
            if (tag != null) {
                chips.add(new Chip(Kind.TAG, tag.name, false, false, false, false, 0, tag));
            }
        }
        if (!draft.subtasks.isEmpty()) {
            chips.add(new Chip(Kind.SUBTASKS, labels.subtasks(draft.subtasks.size()),
                    false, false, false, false, 0, null));
        }
        if (draft.note != null && !draft.note.trim().isEmpty()) {
            chips.add(new Chip(Kind.NOTE, labels.withNote(), false, false, false, false, 0, null));
        }
        return chips;
    }

    /** "Today", "Tomorrow" or "Thu 3 Dec", then " · 17:00" when there is a time; null without date and time. */
    static String dateLabel(TaskDraft draft, long nowMillis, ZoneId zone, Locale locale, Labels labels) {
        Long day = draft.getDayStartMillis();
        Integer time = draft.getTimeOfDayMinutes();
        if (day == null && time == null) return null;
        String text = null;
        if (day != null) {
            LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
            LocalDate date = Instant.ofEpochMilli(day).atZone(zone).toLocalDate();
            if (date.equals(today)) {
                text = labels.today();
            } else if (date.equals(today.plusDays(1))) {
                text = labels.tomorrow();
            } else {
                text = PickerLabels.dayLabel(day, zone, locale);
            }
        }
        if (time != null) {
            String t = PickerLabels.timeLabel(time);
            text = text == null ? t : text + " · " + t;
        }
        return text;
    }

    /** Which icons of the fixed row show a value (a dot under them), in the order of {@link Kind}-less row. */
    public static boolean hasDate(TaskDraft d) {
        return d.getDayStartMillis() != null || d.getTimeOfDayMinutes() != null;
    }

    public static boolean hasDuration(TaskDraft d) {
        return d.getDurationMinutes() > 0;
    }

    public static boolean hasPriority(TaskDraft d) {
        return d.priority > 0;
    }

    public static boolean hasTag(TaskDraft d) {
        return d.tagId != 0;
    }

    public static boolean hasSubtasks(TaskDraft d) {
        return !d.subtasks.isEmpty();
    }

    /** The More icon: reminder, note, or repeat when Repeat is available. */
    public static boolean hasMore(TaskDraft d, boolean repeatAvailable) {
        return d.getReminder() != TaskDraft.Reminder.NONE
                || (d.note != null && !d.note.trim().isEmpty())
                || (repeatAvailable && d.getRepeat() != TaskDraft.Repeat.NONE);
    }

    private static TagRef find(List<TagRef> tags, long id) {
        for (TagRef t : tags == null ? Collections.<TagRef>emptyList() : tags) {
            if (t.id == id) return t;
        }
        return null;
    }
}
