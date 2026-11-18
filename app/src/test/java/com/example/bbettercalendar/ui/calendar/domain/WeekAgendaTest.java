package com.example.bbettercalendar.ui.calendar.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** rediseno-calendario-color: rows of the Week view agenda and the day grouping it is built from. */
public class WeekAgendaTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private static CalendarItem item(int id, LocalDate day, int hour, int minutes, CalendarItem.Type type) {
        long start = day.atTime(hour, 0).atZone(ZONE).toInstant().toEpochMilli();
        return new CalendarItem(id, "item " + id, "", start, start + minutes * 60000L, 0, type);
    }

    @Test
    public void everyDayOfTheWeekHasAHeaderAndEmptyDaysOneEmptyRow() {
        List<WeekAgenda.Row> rows = WeekAgenda.rows(MONDAY, new HashMap<>());
        assertEquals(WeekAgenda.DAYS * 2, rows.size());
        for (int i = 0; i < WeekAgenda.DAYS; i++) {
            assertEquals(WeekAgenda.Kind.HEADER, rows.get(i * 2).kind);
            assertEquals(MONDAY.plusDays(i), rows.get(i * 2).date);
            assertEquals(WeekAgenda.Kind.EMPTY, rows.get(i * 2 + 1).kind);
            assertNull(rows.get(i * 2 + 1).item);
        }
    }

    @Test
    public void itemsFollowTheirDayHeaderInOrder() {
        CalendarItem a = item(1, MONDAY.plusDays(2), 9, 60, CalendarItem.Type.EVENT);
        CalendarItem b = item(2, MONDAY.plusDays(2), 16, 30, CalendarItem.Type.TASK);
        Map<LocalDate, List<CalendarItem>> byDate = DayPlan.groupByDate(Arrays.asList(a, b), ZONE);

        List<WeekAgenda.Row> rows = WeekAgenda.rows(MONDAY, byDate);
        int header = WeekAgenda.headerPosition(rows, MONDAY.plusDays(2));
        assertEquals(4, header); // two days before it, each a header + an empty row
        assertSame(a, rows.get(header + 1).item);
        assertSame(b, rows.get(header + 2).item);
        assertEquals(WeekAgenda.Kind.HEADER, rows.get(header + 3).kind);
        assertEquals(WeekAgenda.DAYS * 2 + 1, rows.size());
    }

    @Test
    public void headerPositionIsMinusOneOutsideTheWeek() {
        List<WeekAgenda.Row> rows = WeekAgenda.rows(MONDAY, new HashMap<>());
        assertEquals(0, WeekAgenda.headerPosition(rows, MONDAY));
        assertEquals(-1, WeekAgenda.headerPosition(rows, MONDAY.minusDays(1)));
        assertEquals(-1, WeekAgenda.headerPosition(rows, MONDAY.plusDays(7)));
    }

    @Test
    public void aMultiDayItemIsGroupedOnEveryDayItSpans() {
        long start = MONDAY.atTime(22, 0).atZone(ZONE).toInstant().toEpochMilli();
        long end = MONDAY.plusDays(2).atTime(10, 0).atZone(ZONE).toInstant().toEpochMilli();
        CalendarItem trip = new CalendarItem(7, "trip", "", start, end, 0, CalendarItem.Type.EVENT);
        Map<LocalDate, List<CalendarItem>> byDate = DayPlan.groupByDate(Arrays.asList(trip), ZONE);
        assertEquals(3, byDate.size());
        assertSame(trip, byDate.get(MONDAY.plusDays(1)).get(0));
    }
}
