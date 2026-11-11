package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordsTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final ZoneId UTC = ZoneId.of("UTC");
    // Miércoles: la semana en curso es lun 2026-10-05 .. dom 2026-10-11.
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    private static FocusEvent focus(LocalDate day, int hour, int durationMin) {
        return SessionStatsTest.focusAt(day, hour, durationMin);
    }

    @Test
    public void computeEmptyHasNoRecords() {
        Records.Result r = Records.compute(Collections.<FocusEvent>emptyList(), TODAY, UTC);
        assertTrue(r.isEmpty());
        assertNull(r.bestDay);
        assertEquals(0, r.longestSessionMin);
        assertEquals(-1, r.peakHour);
        assertEquals(0, r.weeksAboveAverage);
    }

    @Test
    public void computeSingleDayFillsEveryRecordButTheWeeks() {
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(TODAY, 10, 25));
        ev.add(focus(TODAY, 15, 50));
        Records.Result r = Records.compute(ev, TODAY, UTC);
        assertEquals(TODAY, r.bestDay);
        assertEquals(75, r.bestDayMinutes);
        assertEquals(50, r.longestSessionMin);
        assertEquals(TODAY, r.longestSessionDay);
        assertEquals(14, r.peakHour); // la de 50 min acaba a las 15:00: 14:10-15:00 casi toda en la hora 14
        assertEquals(0, r.weeksAboveAverage);
    }

    @Test
    public void bestDayAndLongestSessionTiesGoToTheMostRecent() {
        LocalDate older = TODAY.minusDays(3);
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(older, 10, 50));
        ev.add(focus(TODAY, 10, 50));
        Records.Result r = Records.compute(ev, TODAY, UTC);
        assertEquals(TODAY, r.bestDay);
        assertEquals(TODAY, r.longestSessionDay);
    }

    @Test
    public void weeksAboveAverageCountsConsecutiveWeeksOverTheRunningMean() {
        // Totales semanales 10, 20, 30, 40 (lunes de cada semana): 20 > 10, 30 > 15, 40 > 20 -> 3.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(LocalDate.of(2026, 9, 14), 10, 10));
        ev.add(focus(LocalDate.of(2026, 9, 21), 10, 20));
        ev.add(focus(LocalDate.of(2026, 9, 28), 10, 30));
        ev.add(focus(LocalDate.of(2026, 10, 5), 10, 40));
        assertEquals(3, Records.compute(ev, TODAY, UTC).weeksAboveAverage);
    }

    @Test
    public void weeksAboveAverageSkipsTheWeekInProgressWhenItIsStillBelow() {
        // 10, 20, 30 y la semana en curso con 5: no supera la media, la cuenta termina en la anterior.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(LocalDate.of(2026, 9, 14), 10, 10));
        ev.add(focus(LocalDate.of(2026, 9, 21), 10, 20));
        ev.add(focus(LocalDate.of(2026, 9, 28), 10, 30));
        ev.add(focus(LocalDate.of(2026, 10, 5), 10, 5));
        assertEquals(2, Records.compute(ev, TODAY, UTC).weeksAboveAverage);
    }

    @Test
    public void weeksAboveAverageStopsAtAWeekThatDidNotBeatTheMean() {
        // 30, 10, 20, 40: 10 no supera 30 -> corta; 20 > 20? no (media 20) -> no; 40 > 20 -> 1.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focus(LocalDate.of(2026, 9, 14), 10, 30));
        ev.add(focus(LocalDate.of(2026, 9, 21), 10, 10));
        ev.add(focus(LocalDate.of(2026, 9, 28), 10, 20));
        ev.add(focus(LocalDate.of(2026, 10, 5), 10, 40));
        assertEquals(1, Records.compute(ev, TODAY, UTC).weeksAboveAverage);
    }

    @Test
    public void computeMidnightSessionBelongsToTheDayItEndedOn() {
        // Acaba a las 00:10 del jueves: ese jueves es el mejor día aunque empezó el miércoles.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(SessionStatsTest.ev(FocusEvent.TYPE_FOCUS, ZonedDateTime.of(2026, 10, 8, 0, 10, 0, 0, MADRID), 25));
        Records.Result r = Records.compute(ev, LocalDate.of(2026, 10, 8), MADRID);
        assertEquals(LocalDate.of(2026, 10, 8), r.bestDay);
        assertEquals(23, r.peakHour); // 23:45-00:10: 15 min en la hora 23 y 10 en la 0
    }

    @Test
    public void computeDstWeekAcrossTheClockChangeStillCountsWholeWeeks() {
        // Semana del cambio de hora de primavera (lun 2026-03-23): 100 min; la siguiente 200 -> 1 semana.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(SessionStatsTest.ev(FocusEvent.TYPE_FOCUS, ZonedDateTime.of(2026, 3, 29, 10, 0, 0, 0, MADRID), 100));
        ev.add(SessionStatsTest.ev(FocusEvent.TYPE_FOCUS, ZonedDateTime.of(2026, 3, 30, 10, 0, 0, 0, MADRID), 200));
        Records.Result r = Records.compute(ev, LocalDate.of(2026, 3, 31), MADRID);
        assertEquals(1, r.weeksAboveAverage);
        assertEquals(LocalDate.of(2026, 3, 30), r.bestDay);
    }

    @Test
    public void computeIgnoresFailsAndFutureEvents() {
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(SessionStatsTest.failAt(TODAY, 10));
        ev.add(focus(TODAY.plusDays(2), 10, 60));
        assertTrue(Records.compute(ev, TODAY, UTC).isEmpty());
    }
}
