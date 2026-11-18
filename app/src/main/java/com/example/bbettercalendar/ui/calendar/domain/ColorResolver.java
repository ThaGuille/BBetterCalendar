package com.example.bbettercalendar.ui.calendar.domain;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;

public final class ColorResolver {

    private ColorResolver() {}

    /**
     * Colour of an item's mark (rediseno-calendario-color). Tasks, events and reminders share one
     * neutral colour on purpose: the calendar does not encode the item type in colour, so the channel
     * stays free for what the items gain later (importance, tags). Only the project deadline differs.
     */
    public static int colorFor(CalendarItem.Type type, Context ctx) {
        int resId = type == CalendarItem.Type.DEADLINE
                ? R.color.calendar_item_deadline
                : R.color.calendar_item_default;
        return ContextCompat.getColor(ctx, resId);
    }
}
