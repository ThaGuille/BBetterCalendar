package com.example.bbettercalendar.usage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

// u20: el reparto de un intervalo de uso por dia (foregroundMillisByDay).
public class UsageDayBucketsTest {

    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;
    private static final long DAY = 24 * HOUR;
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate FIRST = LocalDate.of(2026, 10, 1);
    private static final long START = FIRST.atStartOfDay(UTC).toInstant().toEpochMilli();

    private static long[] add(int days, long start, long stop) {
        long[] b = new long[days];
        UsageDayBuckets.addInterval(b, start, stop, FIRST, UTC);
        return b;
    }

    @Test
    public void intervalInsideOneDayGoesToThatDay() {
        long[] b = add(3, START + DAY + 2 * HOUR, START + DAY + 3 * HOUR);
        assertEquals(0L, b[0]);
        assertEquals(HOUR, b[1]);
        assertEquals(0L, b[2]);
    }

    @Test
    public void intervalAcrossMidnightIsSplit() {
        long[] b = add(3, START + DAY - 10 * MIN, START + DAY + 20 * MIN);
        assertEquals(10 * MIN, b[0]);
        assertEquals(20 * MIN, b[1]);
    }

    @Test
    public void intervalSpanningSeveralDaysFillsEachOne() {
        long[] b = add(4, START + 12 * HOUR, START + 2 * DAY + 6 * HOUR);
        assertEquals(12 * HOUR, b[0]);
        assertEquals(DAY, b[1]);
        assertEquals(6 * HOUR, b[2]);
        assertEquals(0L, b[3]);
    }

    @Test
    public void timeOutsideTheRangeIsIgnored() {
        long[] b = add(2, START - HOUR, START + 30 * MIN);
        assertEquals(30 * MIN, b[0]);
        long[] c = add(2, START + 2 * DAY - 15 * MIN, START + 2 * DAY + HOUR);
        assertEquals(15 * MIN, c[1]);
        assertEquals(15 * MIN, c[0] + c[1]);
    }

    @Test
    public void emptyOrReversedIntervalAddsNothing() {
        long[] b = add(2, START + HOUR, START + HOUR);
        assertEquals(0L, b[0] + b[1]);
        long[] c = add(2, START + 2 * HOUR, START + HOUR);
        assertEquals(0L, c[0] + c[1]);
    }
}
