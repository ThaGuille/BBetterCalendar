package com.example.bbettercalendar.social.interaction;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.social.stats.FriendStats;

import org.junit.Test;

import java.time.LocalDate;

/**
 * F5-u5: la regla entre zonas horarias del empujón (D-035), igual que en el servidor. Mi hoy es 2026-11-02;
 * los dayKey de los amigos están escritos a mano, no calculados.
 */
public class NudgeEligibilityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    private static FriendStats stats(String dayKey, int minutes) {
        return new FriendStats(dayKey, "2026-W45", minutes, minutes, 1, 3, 0, 0, minutes, null);
    }

    private static boolean offered(String dayKey, int minutes) {
        return NudgeEligibility.offered(stats(dayKey, minutes), TODAY);
    }

    @Test
    public void studiedMinutesOnMyDayKeyMeansNoNudge() {
        assertFalse(offered("2026-11-02", 25));
        assertTrue(NudgeEligibility.studiedToday(stats("2026-11-02", 25), TODAY));
    }

    @Test
    public void studiedMinutesOnMyDayKeyPlusOneMeansNoNudge() {
        assertFalse("a friend ahead of my time zone already lives in my tomorrow", offered("2026-11-03", 10));
    }

    @Test
    public void studiedMinutesOnMyDayKeyMinusOneIsOffered() {
        assertTrue("yesterday's minutes are not today's", offered("2026-11-01", 90));
    }

    @Test
    public void studiedMinutesTwoDaysAheadIsOffered() {
        assertTrue(offered("2026-11-04", 30));
    }

    @Test
    public void zeroMinutesIsOfferedOnEveryAcceptedDay() {
        assertTrue(offered("2026-11-02", 0));
        assertTrue(offered("2026-11-03", 0));
    }

    @Test
    public void missingStatsIsOffered() {
        assertTrue(NudgeEligibility.offered(null, TODAY));
        assertFalse(NudgeEligibility.studiedToday(null, TODAY));
    }

    @Test
    public void aLongAgoDayKeyIsOffered() {
        assertTrue(offered("2025-01-15", 120));
    }

    @Test
    public void oneMinuteIsEnoughToCountAsStudied() {
        assertFalse(offered("2026-11-02", 1));
    }

    @Test
    public void theMonthAndYearBoundariesCompareAsDates() {
        LocalDate lastDay = LocalDate.of(2026, 12, 31);
        assertFalse("tomorrow of Dec 31 is Jan 1 of the next year",
                NudgeEligibility.offered(stats("2027-01-01", 5), lastDay));
        assertTrue(NudgeEligibility.offered(stats("2026-12-30", 5), lastDay));
        assertTrue(NudgeEligibility.offered(stats("2027-01-02", 5), lastDay));
    }
}
