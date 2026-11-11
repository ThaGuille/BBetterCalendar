package com.example.bbettercalendar.ui.calendar.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

public class DayPlanTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    private static long at(LocalDate d, int hour, int min) {
        return d.atTime(hour, min).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static CalendarItem item(long start, long end) {
        return new CalendarItem(1, "t", null, start, end, 0, CalendarItem.Type.EVENT);
    }

    @Test
    public void emptyListIsZero() {
        assertEquals(0, DayPlan.plannedMinutes(Collections.emptyList(), DAY, ZONE));
    }

    @Test
    public void sumsDurationsOfTheDay() {
        long total = DayPlan.plannedMinutes(Arrays.asList(
                item(at(DAY, 9, 0), at(DAY, 11, 0)),
                item(at(DAY, 16, 30), at(DAY, 17, 0))), DAY, ZONE);
        assertEquals(150, total);
    }

    @Test
    public void multiDayItemOnlyCountsTheOverlap() {
        long total = DayPlan.plannedMinutes(Collections.singletonList(
                item(at(DAY.minusDays(1), 22, 0), at(DAY, 1, 0))), DAY, ZONE);
        assertEquals(60, total);
    }

    @Test
    public void itemsWithoutDurationCountZero() {
        assertEquals(0, DayPlan.plannedMinutes(Collections.singletonList(
                item(at(DAY, 9, 0), at(DAY, 9, 0))), DAY, ZONE));
    }
}
