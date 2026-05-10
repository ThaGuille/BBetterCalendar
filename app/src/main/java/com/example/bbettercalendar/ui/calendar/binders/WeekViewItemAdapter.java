package com.example.bbettercalendar.ui.calendar.binders;

import android.graphics.Color;

import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;
import com.example.bbettercalendar.ui.calendar.weekview.WeekView;
import com.example.bbettercalendar.ui.calendar.weekview.WeekViewEntity;

import java.util.Calendar;

public class WeekViewItemAdapter extends WeekView.SimpleAdapter<CalendarItem> {

    @Override
    public WeekViewEntity onCreateEntity(CalendarItem item) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(item.getStartMillis());
        Calendar end = Calendar.getInstance();
        long endMillis = item.getEndMillis() > item.getStartMillis()
                ? item.getEndMillis()
                : item.getStartMillis() + 60L * 60L * 1000L;
        end.setTimeInMillis(endMillis);

        WeekViewEntity.Style style = new WeekViewEntity.Style.Builder()
                .setBackgroundColor(item.getColorArgb())
                .setTextColor(Color.WHITE)
                .setCornerRadius(8)
                .build();

        return new WeekViewEntity.Event.Builder<>(item)
                .setId((long) item.getId())
                .setTitle(item.getTitle() != null ? item.getTitle() : "")
                .setStartTime(start)
                .setEndTime(end)
                .setStyle(style)
                .build();
    }
}
