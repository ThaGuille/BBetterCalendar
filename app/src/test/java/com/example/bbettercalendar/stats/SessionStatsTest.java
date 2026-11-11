package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionStatsTest {

    private static final double EPS = 1e-9;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate D1 = LocalDate.of(2026, 10, 6);

    static FocusEvent ev(int type, ZonedDateTime when, int durationMin) {
        FocusEvent e = new FocusEvent();
        e.timestamp = when.toInstant().toEpochMilli();
        e.type = type;
        e.durationMin = durationMin;
        return e;
    }

    static FocusEvent focusAt(LocalDate day, int hour, int durationMin) {
        return ev(FocusEvent.TYPE_FOCUS, day.atTime(hour, 0).atZone(UTC), durationMin);
    }

    static FocusEvent failAt(LocalDate day, int hour) {
        return ev(FocusEvent.TYPE_FAIL, day.atTime(hour, 0).atZone(UTC), 0);
    }

    @Test
    public void computeEmptyHasNoRateNoModalAndNoDelta() {
        SessionStats.Result r = SessionStats.compute(Collections.<FocusEvent>emptyList(), D1, D1, UTC);
        assertFalse(r.hasCompletionRate());
        assertEquals(0, r.modalDurationMin);
        assertEquals(0, r.focusCount);
        assertTrue(r.perDay.isEmpty());
        assertFalse(SessionStats.hasDelta(r, r));
        assertEquals(0.0, SessionStats.deltaPoints(r, r), EPS);
    }

    @Test
    public void computeSingleDayCountsBucketsAndPerDay() {
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focusAt(D1, 9, 25));
        ev.add(focusAt(D1, 11, 25));
        ev.add(focusAt(D1, 14, 50));
        ev.add(failAt(D1, 16));
        SessionStats.Result r = SessionStats.compute(ev, D1, D1, UTC);
        assertEquals(3, r.focusCount);
        assertEquals(1, r.failCount);
        assertEquals(100, r.totalMinutes);
        assertEquals(25, r.modalDurationMin);
        assertEquals(0.75, r.completionRate(), EPS);
        // cubos 10/15/20/25/30/45+
        assertArrayEquals(new int[]{0, 0, 0, 2, 0, 1}, r.buckets);
        assertEquals(1, r.perDay.size());
        SessionStats.DayCount dc = r.perDay.get(D1);
        assertNotNull(dc);
        assertEquals(3, dc.focus);
        assertEquals(1, dc.fails);
        assertEquals(100, dc.minutes);
    }

    @Test
    public void bucketIndexUsesTheLowerBoundOfEachBucket() {
        assertEquals(0, SessionStats.bucketIndex(5));
        assertEquals(0, SessionStats.bucketIndex(10));
        assertEquals(0, SessionStats.bucketIndex(14));
        assertEquals(1, SessionStats.bucketIndex(15));
        assertEquals(2, SessionStats.bucketIndex(20));
        assertEquals(3, SessionStats.bucketIndex(25));
        assertEquals(4, SessionStats.bucketIndex(44));
        assertEquals(5, SessionStats.bucketIndex(45));
        assertEquals(5, SessionStats.bucketIndex(120));
    }

    @Test
    public void modalDurationTieGoesToTheShorterOne() {
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(focusAt(D1, 9, 50));
        ev.add(focusAt(D1, 10, 25));
        ev.add(focusAt(D1, 11, 50));
        ev.add(focusAt(D1, 12, 25));
        assertEquals(25, SessionStats.compute(ev, D1, D1, UTC).modalDurationMin);
    }

    @Test
    public void completionDeltaIsInPercentagePointsAndNeedsBothPeriods() {
        LocalDate prevDay = D1.minusDays(7);
        List<FocusEvent> ev = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ev.add(focusAt(prevDay, 8 + i, 25));
        }
        for (int i = 0; i < 4; i++) {
            ev.add(failAt(prevDay, 15 + i));
        }
        for (int i = 0; i < 8; i++) {
            ev.add(focusAt(D1, 8 + i, 25));
        }
        for (int i = 0; i < 2; i++) {
            ev.add(failAt(D1, 17 + i));
        }
        SessionStats.Result cur = SessionStats.compute(ev, D1, D1, UTC);
        SessionStats.Result prev = SessionStats.compute(ev, prevDay, prevDay, UTC);
        assertTrue(SessionStats.hasDelta(cur, prev));
        assertEquals(20.0, SessionStats.deltaPoints(cur, prev), EPS);
        SessionStats.Result none = SessionStats.compute(ev, D1.minusDays(60), D1.minusDays(59), UTC);
        assertFalse(SessionStats.hasDelta(cur, none));
    }

    @Test
    public void previousRangeIsTheSameLengthRightBefore() {
        LocalDate[] p = SessionStats.previousRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));
        assertEquals(LocalDate.of(2026, 9, 28), p[0]);
        assertEquals(LocalDate.of(2026, 10, 4), p[1]);
        LocalDate[] one = SessionStats.previousRange(D1, D1);
        assertEquals(D1.minusDays(1), one[0]);
        assertEquals(D1.minusDays(1), one[1]);
    }

    @Test
    public void computeMidnightAssignsTheEventToTheLocalDayOfItsEnd() {
        // 23:59 y 00:01 locales: dos días distintos aunque estén a 2 minutos.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(ev(FocusEvent.TYPE_FOCUS, ZonedDateTime.of(2026, 10, 6, 23, 59, 0, 0, MADRID), 25));
        ev.add(ev(FocusEvent.TYPE_FOCUS, ZonedDateTime.of(2026, 10, 7, 0, 1, 0, 0, MADRID), 25));
        SessionStats.Result r = SessionStats.compute(ev, D1, D1.plusDays(1), MADRID);
        assertEquals(2, r.perDay.size());
        assertEquals(1, r.perDay.get(D1).focus);
        assertEquals(1, r.perDay.get(D1.plusDays(1)).focus);
    }

    @Test
    public void computeDstDayUsesTheLocalCalendarNotUtc() {
        // 2026-03-28 23:30 UTC ya es 2026-03-29 00:30 en Madrid (CET). Y 22:30 UTC del 29 (00:30 CEST del 30)
        // cae en el día 30 local: el cambio de hora no desplaza ningún evento de día.
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(ev(FocusEvent.TYPE_FOCUS, ZonedDateTime.of(2026, 3, 28, 23, 30, 0, 0, UTC), 25));
        ev.add(ev(FocusEvent.TYPE_FOCUS, ZonedDateTime.of(2026, 3, 29, 22, 30, 0, 0, UTC), 25));
        LocalDate dst = LocalDate.of(2026, 3, 29);
        SessionStats.Result r = SessionStats.compute(ev, dst, dst.plusDays(1), MADRID);
        assertEquals(1, r.perDay.get(dst).focus);
        assertEquals(1, r.perDay.get(dst.plusDays(1)).focus);
    }

    @Test
    public void computeIgnoresOtherTypesNullsAndOutOfRangeDays() {
        List<FocusEvent> ev = new ArrayList<>();
        ev.add(ev(FocusEvent.TYPE_TASK, D1.atTime(9, 0).atZone(UTC), 0));
        ev.add(null);
        ev.add(focusAt(D1.minusDays(1), 9, 25));
        SessionStats.Result r = SessionStats.compute(ev, D1, D1, UTC);
        assertEquals(0, r.focusCount + r.failCount);
    }
}
