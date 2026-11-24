package com.example.bbettercalendar.projects.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Test fixtures: tiny builder for TaskSnapshot and fixed dates (2026-11-24 is a Tuesday). */
final class Tasks {
    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private Tasks() {}

    static long day(int y, int m, int d) {
        return LocalDate.of(y, m, d).atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    static long at(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(ZONE).toInstant().toEpochMilli();
    }

    /** Pending, non-template, no date, priority 0, section position 0. */
    static B task(long id) {
        return new B(id);
    }

    static final class B {
        long id;
        long due;
        int priority;
        int sectionPosition;
        boolean done;
        boolean template;

        B(long id) {
            this.id = id;
        }

        B due(long millis) { this.due = millis; return this; }
        B prio(int p) { this.priority = p; return this; }
        B section(int pos) { this.sectionPosition = pos; return this; }
        B done() { this.done = true; return this; }
        B template() { this.template = true; return this; }

        TaskSnapshot build() {
            return new TaskSnapshot(id, 1L, sectionPosition, "t" + id, due, due != 0L, priority,
                    done, template, 0, 0, false, 0L);
        }
    }

    static long[] ids(java.util.List<TaskSnapshot> list) {
        long[] out = new long[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i).id;
        return out;
    }
}
