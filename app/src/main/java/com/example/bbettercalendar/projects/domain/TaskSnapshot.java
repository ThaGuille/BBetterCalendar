package com.example.bbettercalendar.projects.domain;

/** Immutable, Room-free view of a project task, as the pure rules need it. */
public final class TaskSnapshot {
    public final long id;
    public final long sectionId;
    public final int sectionPosition;
    public final String title;
    /** Due instant in millis; 0 = no date. */
    public final long dueMillis;
    public final boolean hasTime;
    /** 0 (none) .. 3 (high). */
    public final int priority;
    public final boolean done;
    public final boolean isTemplate;
    public final int targetMinutes;
    public final int attributedMinutes;
    public final boolean blockOnFocus;
    public final long tagId;

    public TaskSnapshot(long id, long sectionId, int sectionPosition, String title, long dueMillis,
                        boolean hasTime, int priority, boolean done, boolean isTemplate,
                        int targetMinutes, int attributedMinutes, boolean blockOnFocus, long tagId) {
        this.id = id;
        this.sectionId = sectionId;
        this.sectionPosition = sectionPosition;
        this.title = title;
        this.dueMillis = dueMillis;
        this.hasTime = hasTime;
        this.priority = priority;
        this.done = done;
        this.isTemplate = isTemplate;
        this.targetMinutes = targetMinutes;
        this.attributedMinutes = attributedMinutes;
        this.blockOnFocus = blockOnFocus;
        this.tagId = tagId;
    }

    public boolean hasDate() {
        return dueMillis != 0L;
    }
}
