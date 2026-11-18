package com.example.bbettercalendar.ui.calendar.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure helpers of the day agenda (mejora-visual u12) shared by the Month and Week views. */
public final class DayPlan {

    private DayPlan() {}

    /**
     * Minutes of {@code items} that fall inside {@code day}: a multi-day item only counts the part
     * of it that overlaps the day; items without duration (deadlines, reminders) count 0.
     */
    public static long plannedMinutes(@NonNull List<CalendarItem> items, @NonNull LocalDate day,
                                      @NonNull ZoneId zone) {
        long dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long total = 0;
        for (CalendarItem item : items) {
            long start = Math.max(item.getStartMillis(), dayStart);
            long end = Math.min(item.getEndMillis(), dayEnd);
            if (end > start) total += (end - start) / 60000L;
        }
        return total;
    }

    /**
     * Items by the day they fall on, keeping the order of {@code items}. A multi-day item appears on
     * every day from its start to its end.
     */
    @NonNull
    public static Map<LocalDate, List<CalendarItem>> groupByDate(@Nullable List<CalendarItem> items,
                                                                 @NonNull ZoneId zone) {
        Map<LocalDate, List<CalendarItem>> map = new HashMap<>();
        if (items == null) return map;
        for (CalendarItem item : items) {
            LocalDate start = Instant.ofEpochMilli(item.getStartMillis()).atZone(zone).toLocalDate();
            LocalDate end = Instant.ofEpochMilli(item.getEndMillis()).atZone(zone).toLocalDate();
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                List<CalendarItem> list = map.get(cursor);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(cursor, list);
                }
                list.add(item);
                cursor = cursor.plusDays(1);
            }
        }
        return map;
    }
}
