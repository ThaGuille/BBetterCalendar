package com.example.bbettercalendar.projects.domain;

import java.util.List;

/** Progress percentage of a project. Pure Java. */
public final class ProjectProgress {
    private ProjectProgress() {}

    /** round(100 * done / total) over non-template tasks; 0 when there are none. */
    public static int percent(List<TaskSnapshot> tasks) {
        int total = 0;
        int done = 0;
        for (TaskSnapshot t : tasks) {
            if (t.isTemplate) continue;
            total++;
            if (t.done) done++;
        }
        if (total == 0) return 0;
        return (int) Math.round(100.0 * done / total);
    }
}
