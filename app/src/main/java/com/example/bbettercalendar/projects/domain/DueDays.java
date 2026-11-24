package com.example.bbettercalendar.projects.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Days to a project deadline (the "24 d" label). Pure Java. */
public final class DueDays {
    private DueDays() {}

    /** Calendar days from today to the deadline day (negative when past); null without deadline (0). */
    public static Integer until(long deadlineMillis, long nowMillis, ZoneId zone) {
        if (deadlineMillis == 0L) return null;
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        LocalDate due = Instant.ofEpochMilli(deadlineMillis).atZone(zone).toLocalDate();
        return (int) ChronoUnit.DAYS.between(today, due);
    }
}
