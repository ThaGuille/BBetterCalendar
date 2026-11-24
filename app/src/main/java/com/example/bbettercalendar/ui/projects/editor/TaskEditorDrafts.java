package com.example.bbettercalendar.ui.projects.editor;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.TaskSubtask;
import com.example.bbettercalendar.projects.domain.DateChoices;
import com.example.bbettercalendar.projects.domain.TaskDraft;

import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;

/** Builds the {@link TaskDraft} the full editor edits from a stored task. Pure Java apart from the entity. */
public final class TaskEditorDrafts {

    private TaskEditorDrafts() {}

    /**
     * The draft of an existing task. Repeat is not read here: it belongs to the series template, so
     * TaskEditorViewModel sets it (ProjectTasksRepository.seriesRepeat); the reminder
     * comes from the stored offsets (the at-the-time reminder has no stored flag, see TaskDraft).
     *
     * @param firstSectionId section that holds the tasks stored with sectionId 0
     */
    public static TaskDraft fromEntry(CalendarEntry entry, List<TaskSubtask> subtasks, long nowMillis,
                                      ZoneId zone, long firstSectionId) {
        TaskDraft draft = new TaskDraft(DateChoices.startOfToday(nowMillis, zone));
        draft.editId = entry.getId();
        draft.projectId = entry.getProjectId();
        draft.sectionId = entry.getSectionId() != 0 ? entry.getSectionId() : firstSectionId;
        draft.title = entry.getTitle() == null ? "" : entry.getTitle();
        draft.note = entry.getDescription() == null ? "" : entry.getDescription();
        draft.priority = entry.getPriority();
        draft.tagId = entry.getTagId();

        long start = entry.getStartMillis();
        if (start > 0L) {
            draft.setDay(DateChoices.startOfToday(start, zone));
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(start);
            int minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            // A date without a time is stored at 00:00: that exact time counts as "no time".
            if (minutes != 0) draft.setTime(minutes);
        }

        int durationMinutes = (int) (entry.getDuration() / 60_000L);
        int target = entry.getTargetMinutes();
        if (durationMinutes == 0 && target > 0) durationMinutes = target;
        draft.setDurationMinutes(durationMinutes);
        if (target > 0) draft.setPomodoro(true);
        if (entry.isBlockOnFocus()) draft.setBlockOnFocus(true);

        if (draft.getTimeOfDayMinutes() != null) {
            draft.setReminder(TaskDraft.fromNotificationFlags(entry.getNotifications()));
        }
        if (subtasks != null) {
            for (TaskSubtask s : subtasks) draft.subtasks.add(new TaskDraft.Subtask(s.title, s.done));
        }
        draft.done = entry.isDone();
        draft.completionNote = entry.getCompletionNote() == null ? "" : entry.getCompletionNote();
        return draft;
    }
}
