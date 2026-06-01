package com.example.bbettercalendar.ui.calendar.domain;

import android.content.Context;
import android.util.Log;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;

import java.util.ArrayList;
import java.util.List;

public final class CalendarItemMapper {

    private static final String TAG = "CalendarItemMapper";

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
                entry.getDescription(),
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
            } else {
                // Diagnostic for the "events added are not shown" report: entries land here when
                // both startMillis and startDayAndHour resolve to 0. If logs show this firing for
                // EVENT rows, we have a data-layer fix to make.
                Log.w(TAG, "Dropping CalendarItem with startMillis<=0 id=" + e.getId()
                        + " type=" + e.getType() + " title=" + e.getTitle());
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
