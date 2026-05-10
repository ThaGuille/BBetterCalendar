package com.example.bbettercalendar.ui.calendar.domain;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;

public final class ColorResolver {

    private ColorResolver() {}

    public static int colorFor(CalendarItem.Type type, Context ctx) {
        int resId;
        switch (type) {
            case EVENT:    resId = R.color.calendar_item_event;    break;
            case TASK:     resId = R.color.calendar_item_task;     break;
            case REMINDER: resId = R.color.calendar_item_reminder; break;
            default:       resId = R.color.calendar_item_event;
        }
        return ContextCompat.getColor(ctx, resId);
    }
}
