package com.example.bbettercalendar.ui.calendar.domain;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Pure helpers of the day agenda card (mejora-visual u12). */
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
}
