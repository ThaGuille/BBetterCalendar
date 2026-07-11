package com.example.bbettercalendar.calendarEntries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.popups.RepetitionOptions;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JVM unit tests for {@link RecurrenceMaterializer#occurrenceStarts(int, long, int, int, long, long)}
 * — the pure occurrence-date math of the tasks-recurrence spec: daily interval, weekly weekday
 * bitmask, and the idempotent-top-up invariant (consecutive windows never overlap). No Android
 * runtime needed; only {@link Calendar} is used.
 */
public class RecurrenceMaterializerTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    /** Anchor at a fixed, timezone-stable instant with a non-midnight time-of-day. */
    private static long anchorAt(int year, int month0, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month0, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    private static int dayOfMonth(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.DAY_OF_MONTH);
    }

    private static int weekdayBit(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        // SUNDAY=1..SATURDAY=7 -> lunes=0..domingo=6, mismo mapeo que RepetitionSpec.weekdayBit.
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    // ---------------------------------------------------------------- daily interval math

    @Test
    public void daily_everyDay_producesConsecutiveDays() {
        long anchor = anchorAt(2026, Calendar.JULY, 11, 9, 30);
        long until = anchor + 5 * DAY; // horizon 5 days out, inclusive

        List<Long> starts = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.DAILY, anchor, /*interval*/ 1, /*mask*/ 0,
                /*afterExclusive*/ anchor - 1, until);

        // anchor .. anchor+5*DAY inclusive = 6 occurrences, one per day, preserving time-of-day.
        assertEquals(6, starts.size());
        for (int i = 0; i < starts.size(); i++) {
            assertEquals(anchor + i * DAY, (long) starts.get(i));
        }
    }

    @Test
    public void daily_everyThreeDays_respectsInterval() {
        long anchor = anchorAt(2026, Calendar.JULY, 11, 8, 0);
        long until = anchor + 10 * DAY;

        List<Long> starts = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.DAILY, anchor, /*interval*/ 3, /*mask*/ 0,
                anchor - 1, until);

        // day 0, 3, 6, 9 fall within [0,10]. day 12 is past the horizon.
        assertEquals(4, starts.size());
        assertEquals(anchor + 0 * DAY, (long) starts.get(0));
        assertEquals(anchor + 3 * DAY, (long) starts.get(1));
        assertEquals(anchor + 6 * DAY, (long) starts.get(2));
        assertEquals(anchor + 9 * DAY, (long) starts.get(3));
    }

    @Test
    public void daily_intervalZero_treatedAsOne() {
        long anchor = anchorAt(2026, Calendar.JULY, 11, 8, 0);
        long until = anchor + 3 * DAY;

        // interval 0 would be an infinite loop if not clamped; occurrenceStarts uses max(1, interval).
        List<Long> starts = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.DAILY, anchor, /*interval*/ 0, /*mask*/ 0,
                anchor - 1, until);

        assertEquals(4, starts.size());
    }

    @Test
    public void afterExclusiveIsStrict_anchorAtBoundaryExcluded() {
        long anchor = anchorAt(2026, Calendar.JULY, 11, 8, 0);
        long until = anchor + 3 * DAY;

        // afterExclusive == anchor: the anchor day itself must be excluded (strict >).
        List<Long> starts = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.DAILY, anchor, 1, 0, /*afterExclusive*/ anchor, until);

        assertEquals(3, starts.size());
        assertEquals(anchor + 1 * DAY, (long) starts.get(0));
    }

    // ---------------------------------------------------------------- weekly bitmask

    @Test
    public void weekly_monWedFri_onlyEmitsSelectedWeekdays() {
        // Anchor Monday 2026-07-13. Mask = Mon|Wed|Fri (bits 0,2,4).
        long anchor = anchorAt(2026, Calendar.JULY, 13, 7, 0);
        assertEquals("anchor must be Monday", 0, weekdayBit(anchor));
        int mask = (1 << 0) | (1 << 2) | (1 << 4);
        long until = anchor + 14 * DAY; // two full weeks

        List<Long> starts = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.WEEKLY, anchor, /*interval*/ 1, mask,
                anchor - 1, until);

        // Every emitted day must be Mon/Wed/Fri, and no other weekday appears.
        Set<Integer> allowed = new HashSet<>();
        allowed.add(0);
        allowed.add(2);
        allowed.add(4);
        for (long m : starts) {
            assertTrue("emitted a non-selected weekday", allowed.contains(weekdayBit(m)));
        }
        // 2 weeks + boundary day 14 (also a Monday) -> Mon,Wed,Fri x2 + trailing Mon = 7.
        assertEquals(7, starts.size());
    }

    @Test
    public void weekly_emptyMask_fallsBackToAnchorWeekday() {
        // Anchor Wednesday 2026-07-15, no days marked -> weekly on the anchor's own weekday.
        long anchor = anchorAt(2026, Calendar.JULY, 15, 7, 0);
        int anchorBit = weekdayBit(anchor);
        long until = anchor + 21 * DAY;

        List<Long> starts = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.WEEKLY, anchor, 1, /*mask*/ 0, anchor - 1, until);

        for (long m : starts) {
            assertEquals("empty mask must stick to anchor weekday", anchorBit, weekdayBit(m));
        }
        // anchor, +7, +14, +21 -> 4 occurrences.
        assertEquals(4, starts.size());
    }

    // ---------------------------------------------------------------- monthly (day-of-month clamp)

    @Test
    public void monthly_endOfMonthAnchor_reDerivesDayAndDoesNotDrift() {
        // Anchor Jan 31 2026 (2026 is not a leap year -> Feb has 28 days). A naive add(MONTH,1)
        // would clamp to Feb 28 and stick there (28 Mar, 28 Apr...). Correct behavior: clamp only
        // for short months, then snap back to 31.
        long anchor = anchorAt(2026, Calendar.JANUARY, 31, 9, 0);
        long until = anchorAt(2026, Calendar.AUGUST, 1, 0, 0);

        List<Long> starts = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.MONTHLY, anchor, /*interval*/ 1, /*mask*/ 0,
                anchor - 1, until);

        int[] expectedDays = {31, 28, 31, 30, 31, 30, 31}; // Jan..Jul 2026
        assertEquals(expectedDays.length, starts.size());
        for (int i = 0; i < expectedDays.length; i++) {
            assertEquals("month " + i + " day-of-month drifted",
                    expectedDays[i], dayOfMonth(starts.get(i)));
        }
    }

    // ---------------------------------------------------------------- idempotent top-up

    @Test
    public void topUp_consecutiveWindowsAreDisjointAndCoverFullRange_daily() {
        long anchor = anchorAt(2026, Calendar.JULY, 11, 9, 0);
        long fullHorizon = anchor + 30 * DAY;
        long firstWatermark = anchor + 10 * DAY; // first run only materialized 10 days out

        // Run 1: (anchor-1, firstWatermark]
        List<Long> first = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.DAILY, anchor, 2, 0, anchor - 1, firstWatermark);
        // Run 2 (top-up): afterExclusive advances to the previous watermark.
        List<Long> second = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.DAILY, anchor, 2, 0, firstWatermark, fullHorizon);
        // A single full-window run for comparison.
        List<Long> whole = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.DAILY, anchor, 2, 0, anchor - 1, fullHorizon);

        // Disjoint: no instant appears in both windows (idempotent top-up = no duplicate rows).
        Set<Long> firstSet = new HashSet<>(first);
        for (long m : second) {
            assertFalse("top-up re-emitted an already-materialized occurrence", firstSet.contains(m));
        }
        // Complete: the two windows union exactly to the single full-range computation.
        List<Long> union = new ArrayList<>(first);
        union.addAll(second);
        assertEquals(whole, union);
    }

    @Test
    public void topUp_atOrPastWatermark_emitsNothing() {
        long anchor = anchorAt(2026, Calendar.JULY, 11, 9, 0);
        long watermark = anchor + 30 * DAY;

        // Re-running with afterExclusive == untilInclusive == watermark yields no new occurrences.
        List<Long> starts = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.DAILY, anchor, 1, 0, watermark, watermark);

        assertTrue(starts.isEmpty());
    }

    @Test
    public void nonPositiveAnchor_yieldsNoOccurrences() {
        List<Long> starts = RecurrenceMaterializer.occurrenceStarts(
                RepetitionOptions.DAILY, /*anchor*/ 0L, 1, 0, -1, DAY * 10);
        assertTrue(starts.isEmpty());
    }
}
