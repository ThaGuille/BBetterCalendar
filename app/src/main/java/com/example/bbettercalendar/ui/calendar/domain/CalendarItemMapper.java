package com.example.bbettercalendar.ui.calendar.domain;

import android.content.Context;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;

import java.util.ArrayList;
import java.util.List;

public final class CalendarItemMapper {

    private CalendarItemMapper() {}

    public static CalendarItem toItem(CalendarEntry entry, Context ctx) {
        CalendarItem.Type type = mapType(entry.getType());
        long start = entry.getStartMillis();
        long end = entry.getEndMillis();
        // Defensive: if the long mirrors weren't populated (legacy row pre-migration), fall back
        // to reading the JSON Calendar in memory.
        if (start == 0L && entry.getStartDayAndHour() != null) {
            start = entry.getStartDayAndHour().getTimeInMillis();
        }
        if (end == 0L && entry.getEndDayAndHour() != null) {
            end = entry.getEndDayAndHour().getTimeInMillis();
        }
        if (end < start) end = start;
        return new CalendarItem(
                entry.getId(),
                entry.getTitle() != null ? entry.getTitle() : "",
                start,
                end,
                ColorResolver.colorFor(type, ctx),
                type
        );
    }

    public static List<CalendarItem> toItems(List<CalendarEntry> entries, Context ctx) {
        List<CalendarItem> out = new ArrayList<>(entries.size());
        for (CalendarEntry e : entries) {
            CalendarItem item = toItem(e, ctx);
            if (item.getStartMillis() > 0L) {
                out.add(item);
            }
        }
        return out;
    }

    private static CalendarItem.Type mapType(int dbType) {
        switch (dbType) {
            case AddEventActivity.TYPE_EVENT:        return CalendarItem.Type.EVENT;
            case AddEventActivity.TYPE_TASK:         return CalendarItem.Type.TASK;
            case AddEventActivity.TYPE_NOTIFICATION: return CalendarItem.Type.REMINDER;
            default:                                 return CalendarItem.Type.EVENT;
        }
    }
}
