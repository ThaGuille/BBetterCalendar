package com.example.bbettercalendar.ui.calendar.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rows of the Week view agenda (rediseno-calendario-color): the seven days of a week in order, each
 * a header followed by its items, or by one "nothing planned" row when the day is empty. Pure, so it
 * is unit-testable on the JVM; {@code WeekAgendaAdapter} turns the rows into views.
 */
public final class WeekAgenda {

    public static final int DAYS = 7;

    public enum Kind { HEADER, ITEM, EMPTY }

    public static final class Row {
        @NonNull public final Kind kind;
        @NonNull public final LocalDate date;
        /** Only for {@link Kind#ITEM}. */
        @Nullable public final CalendarItem item;

        private Row(@NonNull Kind kind, @NonNull LocalDate date, @Nullable CalendarItem item) {
            this.kind = kind;
            this.date = date;
            this.item = item;
        }
    }

    private WeekAgenda() {}

    @NonNull
    public static List<Row> rows(@NonNull LocalDate weekStart,
                                 @NonNull Map<LocalDate, List<CalendarItem>> itemsByDate) {
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            LocalDate day = weekStart.plusDays(i);
            out.add(new Row(Kind.HEADER, day, null));
            List<CalendarItem> items = itemsByDate.get(day);
            if (items == null || items.isEmpty()) {
                out.add(new Row(Kind.EMPTY, day, null));
            } else {
                for (CalendarItem item : items) out.add(new Row(Kind.ITEM, day, item));
            }
        }
        return out;
    }

    /** Position of the header of {@code date} in {@code rows}, or -1 when the day is not in them. */
    public static int headerPosition(@NonNull List<Row> rows, @NonNull LocalDate date) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind == Kind.HEADER && row.date.equals(date)) return i;
        }
        return -1;
    }
}
