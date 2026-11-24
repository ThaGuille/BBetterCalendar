package com.example.bbettercalendar.ui.projects.agenda;

/** One pending task as the agenda needs it. Pure Java (no Android), so the JVM tests cover the grouping. */
public final class AgendaTask {
    public final String title;
    /** Start instant in millis; 0 = no date. */
    public final long dueMillis;
    /** False when the task is dated but has no time (stored at 00:00). */
    public final boolean hasTime;
    /** 0 = no duration. */
    public final int durationMinutes;
    public final boolean blockOnFocus;

    public AgendaTask(String title, long dueMillis, boolean hasTime, int durationMinutes,
                      boolean blockOnFocus) {
        this.title = title;
        this.dueMillis = dueMillis;
        this.hasTime = hasTime;
        this.durationMinutes = durationMinutes;
        this.blockOnFocus = blockOnFocus;
    }
}
