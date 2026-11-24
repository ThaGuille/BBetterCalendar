package com.example.bbettercalendar.projects.domain;

import java.util.List;

/** The "next task" of a project and the summary card state (spec 3.1 item 3). Pure Java. */
public final class NextTaskRule {

    public enum SummaryState {
        /** No non-template tasks: "Sin tareas aun". */
        EMPTY,
        /** Tasks exist and none is pending: "Todo hecho". */
        ALL_DONE,
        /** There is a next task to show. */
        NEXT
    }

    private NextTaskRule() {}

    /**
     * Pending non-template task with the nearest due date (no date last); ties by higher priority,
     * then lower section position, then lower id. Null when there is none.
     */
    public static TaskSnapshot next(List<TaskSnapshot> tasks) {
        TaskSnapshot best = null;
        for (TaskSnapshot t : tasks) {
            if (t.done || t.isTemplate) continue;
            if (best == null || isBefore(t, best)) best = t;
        }
        return best;
    }

    private static boolean isBefore(TaskSnapshot a, TaskSnapshot b) {
        int c = TaskOrdering.compareDue(a, b);
        if (c == 0) c = Integer.compare(b.priority, a.priority);
        if (c == 0) c = Integer.compare(a.sectionPosition, b.sectionPosition);
        if (c == 0) c = Long.compare(a.id, b.id);
        return c < 0;
    }

    public static SummaryState summaryState(List<TaskSnapshot> tasks) {
        boolean any = false;
        for (TaskSnapshot t : tasks) {
            if (t.isTemplate) continue;
            any = true;
            if (!t.done) return SummaryState.NEXT;
        }
        return any ? SummaryState.ALL_DONE : SummaryState.EMPTY;
    }
}
