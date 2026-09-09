package com.example.bbettercalendar.ui.calendar.domain;

import java.util.Calendar;
import java.util.TimeZone;

public class CalendarItem {

    // DEADLINE (spec project-deadlines-progress) no viene de un CalendarEntry sino de un Project:
    // se pinta de sólo lectura y su id va NEGADO (ver ProjectDeadlineItemMapper), así que nada
    // puede confundirlo con una fila de calendarEntry.
    public enum Type { EVENT, TASK, REMINDER, DEADLINE }

    private final int id;
    private final String title;
    private final String description;
    private final long startMillis;
    private final long endMillis;
    private final int colorArgb;
    private final Type type;

    public CalendarItem(int id, String title, String description, long startMillis, long endMillis, int colorArgb, Type type) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.colorArgb = colorArgb;
        this.type = type;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public long getStartMillis() { return startMillis; }
    public long getEndMillis() { return endMillis; }
    public int getColorArgb() { return colorArgb; }
    public Type getType() { return type; }

    public boolean isMultiDay() {
        if (endMillis <= startMillis) return false;
        Calendar s = Calendar.getInstance(TimeZone.getDefault());
        s.setTimeInMillis(startMillis);
        Calendar e = Calendar.getInstance(TimeZone.getDefault());
        e.setTimeInMillis(endMillis);
        return s.get(Calendar.YEAR) != e.get(Calendar.YEAR)
                || s.get(Calendar.DAY_OF_YEAR) != e.get(Calendar.DAY_OF_YEAR);
    }

    public long durationMinutes() {
        if (endMillis <= startMillis) return 0L;
        return (endMillis - startMillis) / 60000L;
    }
}
