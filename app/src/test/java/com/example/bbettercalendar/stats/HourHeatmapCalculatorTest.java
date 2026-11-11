package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HourHeatmapCalculatorTest {

    private static final double EPS = 1e-9;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final ZoneId UTC = ZoneId.of("UTC");

    static FocusEvent focus(ZonedDateTime end, int durationMin) {
        FocusEvent e = new FocusEvent();
        e.timestamp = end.toInstant().toEpochMilli();
        e.type = FocusEvent.TYPE_FOCUS;
        e.durationMin = durationMin;
        return e;
    }

    @Test
    public void computeEmptyListGivesNoSlotAndNoPeak() {
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(Collections.<FocusEvent>emptyList(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), UTC);
        assertTrue(r.isEmpty());
        assertNull(r.bestSlot());
        assertEquals(-1, r.peakHour());
        assertEquals(0, r.activeDays);
    }

    @Test
    public void computeSingleDaySplitsMinutesAcrossHours() {
        // Martes 2026-10-06, sesión de 60 min que acaba a las 10:30 -> 30 min en la hora 9 y 30 en la 10.
        LocalDate day = LocalDate.of(2026, 10, 6);
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(ZonedDateTime.of(2026, 10, 6, 10, 30, 0, 0, UTC), 60));
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(ev, day, day, UTC);
        assertEquals(30.0, r.averageMinutes[1][9], EPS);
        assertEquals(30.0, r.averageMinutes[1][10], EPS);
        assertEquals(1, r.activeDays);
        assertEquals(60.0, r.totalMinutes, EPS);
        HourHeatmapCalculator.Slot slot = r.bestSlot();
        assertNotNull(slot);
        assertEquals(DayOfWeek.TUESDAY, slot.dayOfWeek);
        assertEquals(9, slot.startHour);
        assertEquals(11, slot.endHour);
    }

    @Test
    public void computeAverageDividesByWeekdayOccurrencesInRange() {
        // Dos semanas (14 días): cada día de la semana aparece 2 veces. Una sesión de 60 min un solo martes
        // da una media de 30 min por martes en esa hora.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, UTC), 60));
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(ev,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18), UTC);
        assertEquals(30.0, r.averageMinutes[1][9], EPS);
        assertEquals(1, r.activeDays);
    }

    @Test
    public void computeMidnightSplitsSessionAcrossTwoDays() {
        // Acaba el miércoles 00:30, dura 60: 30 min martes hora 23 y 30 min miércoles hora 0.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(ZonedDateTime.of(2026, 10, 7, 0, 30, 0, 0, UTC), 60));
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(ev,
                LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7), UTC);
        assertEquals(30.0, r.averageMinutes[1][23], EPS);
        assertEquals(30.0, r.averageMinutes[2][0], EPS);
        assertEquals(2, r.activeDays);
    }

    @Test
    public void computeMidnightRangeEdgeCountsOnlyTheDayInsideTheRange() {
        // Mismo evento, pero el rango acaba el martes: el trozo del miércoles no cuenta.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(ZonedDateTime.of(2026, 10, 7, 0, 30, 0, 0, UTC), 60));
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(ev,
                LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 6), UTC);
        assertEquals(30.0, r.averageMinutes[1][23], EPS);
        assertEquals(0.0, r.averageMinutes[2][0], EPS);
        assertEquals(30.0, r.totalMinutes, EPS);
    }

    @Test
    public void computeDstSpringForwardLeavesTheMissingHourAtZero() {
        // Madrid 2026-03-29 (domingo): a las 02:00 los relojes saltan a las 03:00. Una sesión de 60 min que
        // acaba a las 03:30 empezó a las 01:30: 30 min en la hora 1, 0 en la 2 (no existe) y 30 en la 3.
        LocalDate day = LocalDate.of(2026, 3, 29);
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, MADRID), 60));
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(ev, day, day, MADRID);
        assertEquals(30.0, r.averageMinutes[6][1], EPS);
        assertEquals(0.0, r.averageMinutes[6][2], EPS);
        assertEquals(30.0, r.averageMinutes[6][3], EPS);
        assertEquals(60.0, r.totalMinutes, EPS);
    }

    @Test
    public void computeDstFallBackAddsTheRepeatedHourOnce() {
        // Madrid 2026-10-25: a las 03:00 CEST los relojes vuelven a las 02:00 CET, la hora 2 ocurre dos veces.
        // 90 min que acaban en la segunda 02:30 (01:30 UTC): 60 en la primera hora 2 y 30 en la segunda.
        LocalDate day = LocalDate.of(2026, 10, 25);
        ZonedDateTime end = ZonedDateTime.of(2026, 10, 25, 1, 30, 0, 0, UTC).withZoneSameInstant(MADRID);
        assertEquals(2, end.getHour());
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(end, 90));
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(ev, day, day, MADRID);
        assertEquals(90.0, r.averageMinutes[6][2], EPS);
        assertEquals(90.0, r.totalMinutes, EPS);
    }

    @Test
    public void computeIgnoresFailsZeroDurationAndOutOfRange() {
        List<FocusEvent> ev = new ArrayList<>();
        FocusEvent fail = focus(ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, UTC), 25);
        fail.type = FocusEvent.TYPE_FAIL;
        ev.add(fail);
        ev.add(focus(ZonedDateTime.of(2026, 10, 6, 11, 0, 0, 0, UTC), 0));
        ev.add(focus(ZonedDateTime.of(2026, 9, 1, 11, 0, 0, 0, UTC), 25));
        ev.add(null);
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(ev,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), UTC);
        assertTrue(r.isEmpty());
    }

    @Test
    public void bestSlotTieBreaksToTheEarliestDayAndToASlotThatStartsWithMinutes() {
        // Las mismas 60 min a las 9 un lunes y un martes: gana el lunes, de 9 a 11 (no de 8 a 10).
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, UTC), 60));
        ev.add(focus(ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, UTC), 60));
        HourHeatmapCalculator.Result r = HourHeatmapCalculator.compute(ev,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), UTC);
        HourHeatmapCalculator.Slot slot = r.bestSlot();
        assertNotNull(slot);
        assertEquals(DayOfWeek.MONDAY, slot.dayOfWeek);
        assertEquals(9, slot.startHour);
        assertEquals(9, r.peakHour());
    }
}
