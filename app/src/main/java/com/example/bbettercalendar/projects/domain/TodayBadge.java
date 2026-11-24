package com.example.bbettercalendar.projects.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Badge of a project in the selector (D-P05). Pure Java. */
public final class TodayBadge {
    /** Pending non-template tasks due today or earlier. */
    public final int count;
    /** At least one of them is due before today. */
    public final boolean late;

    private TodayBadge(int count, boolean late) {
        this.count = count;
        this.late = late;
    }

    /** Count 0 means no badge. */
    public boolean isVisible() {
        return count > 0;
    }

    public static TodayBadge of(List<TaskSnapshot> tasks, long nowMillis, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        int count = 0;
        boolean late = false;
        for (TaskSnapshot t : tasks) {
            if (t.done || t.isTemplate || !t.hasDate()) continue;
            LocalDate due = Instant.ofEpochMilli(t.dueMillis).atZone(zone).toLocalDate();
            if (due.isAfter(today)) continue;
            count++;
            if (due.isBefore(today)) late = true;
        }
        return new TodayBadge(count, late);
    }
}
