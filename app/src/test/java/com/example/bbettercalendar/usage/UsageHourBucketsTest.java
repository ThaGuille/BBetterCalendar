package com.example.bbettercalendar.usage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

// u19: el reparto de un intervalo de uso por hora (foregroundMillisByHour).
public class UsageHourBucketsTest {

    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
    private static final long DAY_START = DAY.atStartOfDay(UTC).toInstant().toEpochMilli();
    private static final long DAY_END = DAY_START + 24 * HOUR;

    private static long[] add(long start, long stop) {
        long[] b = new long[UsageHourBuckets.HOURS];
        UsageHourBuckets.addInterval(b, start, stop, DAY_START, DAY_END, UTC);
        return b;
    }

    @Test
    public void intervalInsideOneHourGoesToThatHour() {
        long[] b = add(DAY_START + 9 * HOUR + 10 * MIN, DAY_START + 9 * HOUR + 40 * MIN);
        assertEquals(30 * MIN, b[9]);
        assertEquals(30 * MIN, sum(b));
    }

    @Test
    public void intervalCrossingAnHourBoundarySplitsBetweenBothHours() {
        long[] b = add(DAY_START + 9 * HOUR + 50 * MIN, DAY_START + 10 * HOUR + 15 * MIN);
        assertEquals(10 * MIN, b[9]);
        assertEquals(15 * MIN, b[10]);
    }

    @Test
    public void intervalSpanningSeveralHoursFillsTheMiddleOnes() {
        long[] b = add(DAY_START + 13 * HOUR + 30 * MIN, DAY_START + 16 * HOUR + 10 * MIN);
        assertEquals(30 * MIN, b[13]);
        assertEquals(HOUR, b[14]);
        assertEquals(HOUR, b[15]);
        assertEquals(10 * MIN, b[16]);
    }

    @Test
    public void timeBeforeTheDayIsIgnored() {
        long[] b = add(DAY_START - 30 * MIN, DAY_START + 20 * MIN);
        assertEquals(20 * MIN, b[0]);
        assertEquals(20 * MIN, sum(b));
    }

    @Test
    public void timeAfterTheDayIsIgnored() {
        long[] b = add(DAY_END - 20 * MIN, DAY_END + 45 * MIN);
        assertEquals(20 * MIN, b[23]);
        assertEquals(20 * MIN, sum(b));
    }

    @Test
    public void intervalFullyOutsideTheDayAddsNothing() {
        assertEquals(0L, sum(add(DAY_START - 3 * HOUR, DAY_START - HOUR)));
        assertEquals(0L, sum(add(DAY_END + HOUR, DAY_END + 2 * HOUR)));
    }

    @Test
    public void emptyOrInvertedIntervalAddsNothing() {
        assertEquals(0L, sum(add(DAY_START + HOUR, DAY_START + HOUR)));
        assertEquals(0L, sum(add(DAY_START + 2 * HOUR, DAY_START + HOUR)));
    }

    @Test
    public void bucketsFollowTheLocalClockNotUtc() {
        ZoneId madrid = ZoneId.of("Europe/Madrid"); // UTC+2 en octubre
        long dayStart = DAY.atStartOfDay(madrid).toInstant().toEpochMilli();
        long dayEnd = DAY.plusDays(1).atStartOfDay(madrid).toInstant().toEpochMilli();
        long[] b = new long[UsageHourBuckets.HOURS];
        // 08:30-09:30 UTC = 10:30-11:30 hora local
        UsageHourBuckets.addInterval(b, DAY_START + 8 * HOUR + 30 * MIN,
                DAY_START + 9 * HOUR + 30 * MIN, dayStart, dayEnd, madrid);
        assertEquals(30 * MIN, b[10]);
        assertEquals(30 * MIN, b[11]);
    }

    private static long sum(long[] b) {
        long s = 0;
        for (long v : b) s += v;
        return s;
    }
}
