package com.example.bbettercalendar.projects.domain;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Ordering of the tasks inside a project (spec 3.1 item 1). Pure Java. */
public final class TaskOrdering {

    public enum Mode {
        /** Due date ascending (no date last), then priority descending, then id. Default. */
        UPCOMING,
        /** Priority descending, then due date ascending (no date last), then id. */
        PRIORITY,
        /** Newest first: id descending. */
        RECENT
    }

    private TaskOrdering() {}

    /**
     * Returns a new sorted list; the input is not modified. Done tasks always go after pending
     * ones. The zone is accepted for API symmetry with the other rules: ordering compares instants.
     */
    public static List<TaskSnapshot> sort(List<TaskSnapshot> tasks, Mode mode, ZoneId zone) {
        List<TaskSnapshot> out = new ArrayList<>(tasks);
        final Comparator<TaskSnapshot> inner;
        switch (mode) {
            case PRIORITY:
                inner = new Comparator<TaskSnapshot>() {
                    @Override public int compare(TaskSnapshot a, TaskSnapshot b) {
                        int c = Integer.compare(b.priority, a.priority);
                        if (c != 0) return c;
                        c = compareDue(a, b);
                        if (c != 0) return c;
                        return Long.compare(a.id, b.id);
                    }
                };
                break;
            case RECENT:
                inner = new Comparator<TaskSnapshot>() {
                    @Override public int compare(TaskSnapshot a, TaskSnapshot b) {
                        return Long.compare(b.id, a.id);
                    }
                };
                break;
            case UPCOMING:
            default:
                inner = new Comparator<TaskSnapshot>() {
                    @Override public int compare(TaskSnapshot a, TaskSnapshot b) {
                        int c = compareDue(a, b);
                        if (c != 0) return c;
                        c = Integer.compare(b.priority, a.priority);
                        if (c != 0) return c;
                        return Long.compare(a.id, b.id);
                    }
                };
                break;
        }
        Collections.sort(out, new Comparator<TaskSnapshot>() {
            @Override public int compare(TaskSnapshot a, TaskSnapshot b) {
                if (a.done != b.done) return a.done ? 1 : -1;
                return inner.compare(a, b);
            }
        });
        return out;
    }

    /** Due ascending, tasks with no date (dueMillis 0) last. */
    static int compareDue(TaskSnapshot a, TaskSnapshot b) {
        if (a.hasDate() != b.hasDate()) return a.hasDate() ? -1 : 1;
        return Long.compare(a.dueMillis, b.dueMillis);
    }
}
