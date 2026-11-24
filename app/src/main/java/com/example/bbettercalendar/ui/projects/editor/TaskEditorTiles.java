package com.example.bbettercalendar.ui.projects.editor;

import com.example.bbettercalendar.projects.domain.DateChoices;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.ui.projects.picker.PickerLabels;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the tiles of the full task editor (D1, spec 3.4) say, computed from the {@link TaskDraft}. Pure
 * Java (no Android) so the JVM tests cover it; the fragment maps the kinds onto string resources.
 */
public final class TaskEditorTiles {

    /** Rows of subtasks the wide tile shows before "+n more". */
    public static final int MAX_SUBTASK_LINES = 3;

    /** Which big word the When tile shows. */
    public enum DayKind { NONE, TODAY, TOMORROW, DATE }

    private TaskEditorTiles() {}

    /** No date, today, tomorrow, or any other day (shown as "Fri 20"). */
    public static DayKind dayKind(Long dayStartMillis, long nowMillis, ZoneId zone) {
        if (dayStartMillis == null) return DayKind.NONE;
        long today = DateChoices.startOfToday(nowMillis, zone);
        if (dayStartMillis == today) return DayKind.TODAY;
        if (dayStartMillis == DateChoices.tomorrow(nowMillis, zone)) return DayKind.TOMORROW;
        return DayKind.DATE;
    }

    /** Weekday and day of month: "Fri 20" / "Vie 20". */
    public static String dayDate(long dayStartMillis, ZoneId zone, Locale locale) {
        LocalDate date = Instant.ofEpochMilli(dayStartMillis).atZone(zone).toLocalDate();
        String text = DateTimeFormatter.ofPattern("EEE d", locale).format(date).replace(".", "");
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }

    /** "50 min", "1 h 30"; null for Free (no duration), which the tile writes as "Free". */
    public static String durationText(TaskDraft draft) {
        int minutes = draft.getDurationMinutes();
        return minutes <= 0 ? null : PickerLabels.durationLabel(minutes);
    }

    /** The Pomodoro marker (amber stopwatch) of the Focus tile. */
    public static boolean showsPomodoro(TaskDraft draft) {
        return draft.isPomodoro() && draft.getDurationMinutes() > 0;
    }

    /** The lock of the Focus tile. */
    public static boolean showsLock(TaskDraft draft) {
        return draft.isBlockOnFocus() && draft.getDurationMinutes() > 0;
    }

    /** "1/2" (done / total) for the header "SUBTASKS · 1/2"; null when there are none. */
    public static String subtasksProgress(TaskDraft draft) {
        int total = draft.subtasks.size();
        if (total == 0) return null;
        int done = 0;
        for (TaskDraft.Subtask s : draft.subtasks) {
            if (s.done) done++;
        }
        return done + "/" + total;
    }

    /** The first lines of subtasks the wide tile draws (at most {@link #MAX_SUBTASK_LINES}). */
    public static List<TaskDraft.Subtask> visibleSubtasks(TaskDraft draft) {
        int count = Math.min(draft.subtasks.size(), MAX_SUBTASK_LINES);
        return new ArrayList<>(draft.subtasks.subList(0, count));
    }

    /** n of "+n more": the subtasks that do not fit in the lines of the tile. */
    public static int moreSubtasks(TaskDraft draft) {
        return Math.max(0, draft.subtasks.size() - MAX_SUBTASK_LINES);
    }

    /**
     * Every user-editable value of the draft as one string. Two drafts with the same signature have nothing
     * to discard between them: the editor compares it with the one taken when it opened.
     */
    public static String signature(TaskDraft draft) {
        StringBuilder sb = new StringBuilder();
        sb.append(draft.title == null ? "" : draft.title.trim()).append('\u0001')
                .append(draft.getDayStartMillis()).append('\u0001')
                .append(draft.getTimeOfDayMinutes()).append('\u0001')
                .append(draft.getDurationMinutes()).append('\u0001')
                .append(draft.isPomodoro()).append('\u0001')
                .append(draft.isBlockOnFocus()).append('\u0001')
                .append(draft.priority).append('\u0001')
                .append(draft.tagId).append('\u0001')
                .append(draft.sectionId).append('\u0001')
                .append(draft.getRepeat()).append('\u0001')
                .append(draft.getReminder()).append('\u0001')
                .append(draft.note == null ? "" : draft.note).append('\u0001')
                .append(draft.done).append('\u0001')
                .append(draft.completionNote == null ? "" : draft.completionNote);
        for (TaskDraft.Subtask s : draft.subtasks) {
            sb.append('\u0002').append(s.title).append(s.done ? '+' : '-');
        }
        return sb.toString();
    }
}
