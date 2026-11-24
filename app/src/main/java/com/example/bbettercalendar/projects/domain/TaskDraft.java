package com.example.bbettercalendar.projects.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The task being written: shared state of the quick-add box and the full-screen editor
 * (spec 3.3 and 3.4). Pure Java. The setters apply the seven rules between fields.
 * "Today" is fixed at construction (start of the day, millis) so the rules are deterministic.
 */
public class TaskDraft implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Repeat { NONE, DAILY, WEEKLY, MONTHLY }

    public enum Reminder { NONE, AT_TIME, MIN_15, HOUR_1 }

    /** Time given to a reminder set on a task that has no time (17:00). */
    public static final int DEFAULT_REMINDER_TIME_MINUTES = 17 * 60;
    public static final int POMODORO_DEFAULT_MINUTES = 25;
    public static final int BLOCK_DEFAULT_MINUTES = 50;

    /** Index of "15 min antes" in NotificationOffsets. */
    static final int FLAG_MIN_15 = 2;
    /** Index of "1 h antes" in NotificationOffsets. */
    static final int FLAG_HOUR_1 = 4;
    static final int FLAG_COUNT = 7;

    public static final class Subtask implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String title;
        public final boolean done;

        public Subtask(String title, boolean done) {
            this.title = title;
            this.done = done;
        }
    }

    private final long todayStartMillis;

    public long editId;
    public long projectId;
    public long sectionId;
    public String title = "";
    private Long dayStartMillis;
    private Integer timeOfDayMinutes;
    private int durationMinutes;
    private boolean pomodoro;
    private boolean blockOnFocus;
    public int priority;
    public long tagId;
    public final List<Subtask> subtasks = new ArrayList<>();
    private Repeat repeat = Repeat.NONE;
    private Reminder reminder = Reminder.NONE;
    public String note = "";
    public boolean done;
    public String completionNote = "";

    public TaskDraft(long todayStartMillis) {
        this.todayStartMillis = todayStartMillis;
    }

    // ---- getters ----

    public Long getDayStartMillis() { return dayStartMillis; }
    public Integer getTimeOfDayMinutes() { return timeOfDayMinutes; }
    public int getDurationMinutes() { return durationMinutes; }
    public boolean isPomodoro() { return pomodoro; }
    public boolean isBlockOnFocus() { return blockOnFocus; }
    public Repeat getRepeat() { return repeat; }
    public Reminder getReminder() { return reminder; }

    // ---- setters with the seven rules ----

    /** Rule: clearing the date (null) clears time, reminder and repeat. */
    public void setDay(Long dayStartMillis) {
        this.dayStartMillis = dayStartMillis;
        if (dayStartMillis == null) {
            timeOfDayMinutes = null;
            reminder = Reminder.NONE;
            repeat = Repeat.NONE;
        }
    }

    /** Rule: a time without a date sets today; clearing the time (null) clears the reminder. */
    public void setTime(Integer timeOfDayMinutes) {
        this.timeOfDayMinutes = timeOfDayMinutes;
        if (timeOfDayMinutes == null) {
            reminder = Reminder.NONE;
        } else if (dayStartMillis == null) {
            dayStartMillis = todayStartMillis;
        }
    }

    /** Rule: a reminder without a time sets 17:00 today (the date is kept when there is one). */
    public void setReminder(Reminder reminder) {
        this.reminder = reminder == null ? Reminder.NONE : reminder;
        if (this.reminder != Reminder.NONE && timeOfDayMinutes == null) {
            timeOfDayMinutes = DEFAULT_REMINDER_TIME_MINUTES;
            if (dayStartMillis == null) dayStartMillis = todayStartMillis;
        }
    }

    /** Rule: a repeat without a date sets today. */
    public void setRepeat(Repeat repeat) {
        this.repeat = repeat == null ? Repeat.NONE : repeat;
        if (this.repeat != Repeat.NONE && dayStartMillis == null) {
            dayStartMillis = todayStartMillis;
        }
    }

    /** Rule: duration Libre (0) turns pomodoro and block off. */
    public void setDurationMinutes(int minutes) {
        this.durationMinutes = Math.max(0, minutes);
        if (this.durationMinutes == 0) {
            pomodoro = false;
            blockOnFocus = false;
        }
    }

    /** Rule: turning pomodoro on with duration Libre sets 25 min. */
    public void setPomodoro(boolean on) {
        pomodoro = on;
        if (on && durationMinutes == 0) durationMinutes = POMODORO_DEFAULT_MINUTES;
    }

    public void togglePomodoro() {
        setPomodoro(!pomodoro);
    }

    /** Rule: turning block on with duration Libre sets 50 min. */
    public void setBlockOnFocus(boolean on) {
        blockOnFocus = on;
        if (on && durationMinutes == 0) durationMinutes = BLOCK_DEFAULT_MINUTES;
    }

    public void toggleBlockOnFocus() {
        setBlockOnFocus(!blockOnFocus);
    }

    // ---- derived ----

    /** True only for a non-blank title. */
    public boolean canSave() {
        return title != null && !title.trim().isEmpty();
    }

    /** Focus goal in minutes: the duration when pomodoro is on, else 0. */
    public int targetMinutes() {
        return pomodoro ? durationMinutes : 0;
    }

    public long durationMillis() {
        return durationMinutes * 60_000L;
    }

    /**
     * The boolean[7] of NotificationOffsets: MIN_15 at index 2, HOUR_1 at index 4. AT_TIME sets no
     * flag (user decision d1: the task-start notification serves it).
     */
    public boolean[] toNotificationFlags() {
        boolean[] flags = new boolean[FLAG_COUNT];
        if (reminder == Reminder.MIN_15) flags[FLAG_MIN_15] = true;
        if (reminder == Reminder.HOUR_1) flags[FLAG_HOUR_1] = true;
        return flags;
    }

    /** Inverse of {@link #toNotificationFlags()}: the smallest offset set wins; none gives NONE. */
    public static Reminder fromNotificationFlags(boolean[] flags) {
        if (flags != null) {
            if (flags.length > FLAG_MIN_15 && flags[FLAG_MIN_15]) return Reminder.MIN_15;
            if (flags.length > FLAG_HOUR_1 && flags[FLAG_HOUR_1]) return Reminder.HOUR_1;
        }
        return Reminder.NONE;
    }
}
