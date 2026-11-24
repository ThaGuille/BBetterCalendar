package com.example.bbettercalendar.projects.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Quick date choices of the pickers, as local start-of-day millis. Pure Java. */
public final class DateChoices {
    private DateChoices() {}

    private static LocalDate today(long nowMillis, ZoneId zone) {
        return Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
    }

    private static long start(LocalDate d, ZoneId zone) {
        return d.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public static long startOfToday(long nowMillis, ZoneId zone) {
        return start(today(nowMillis, zone), zone);
    }

    public static long tomorrow(long nowMillis, ZoneId zone) {
        return start(today(nowMillis, zone).plusDays(1), zone);
    }

    /** First Saturday strictly after today (called on a Saturday it gives the next week's). */
    public static long nextSaturday(long nowMillis, ZoneId zone) {
        return start(today(nowMillis, zone).with(TemporalAdjusters.next(DayOfWeek.SATURDAY)), zone);
    }

    /** Start of the last day of the current month. */
    public static long endOfMonth(long nowMillis, ZoneId zone) {
        return start(today(nowMillis, zone).with(TemporalAdjusters.lastDayOfMonth()), zone);
    }

    /** Start of the day one calendar month after today (clamped to that month's length). */
    public static long inOneMonth(long nowMillis, ZoneId zone) {
        return start(today(nowMillis, zone).plusMonths(1), zone);
    }
}
