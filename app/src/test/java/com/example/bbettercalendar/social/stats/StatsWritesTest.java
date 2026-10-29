package com.example.bbettercalendar.social.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.FieldValue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * F3-u4: el mapa de {@link StatsWrites} tiene exactamente las 10 claves de {@code validStats()} en
 * {@code firebase/firestore.rules} (F3-u2) y, por la decisión del usuario r1, cada campo se recorta al rango que
 * aceptan las reglas: una entrada fuera de rango nunca da un documento rechazado. {@link #acceptedByRules} es
 * {@code validStats()} copiado a Java (sin {@code updatedAt == request.time}, que pone el servidor).
 */
public class StatsWritesTest {

    private static final Object SENTINEL = new Object();
    private static final Set<String> CONTRACT_KEYS = new HashSet<>(Arrays.asList("dayKey", "weekKey",
            "minutesToday", "minutesWeekCapped", "daysThisWeek", "weeklyGoal", "weekStreak", "bestWeekStreak",
            "totalMinutes", "updatedAt"));

    private static FriendStats stats(int today, int week, int days, int goal, int streak, int best, int total) {
        return new FriendStats("2026-10-28", "2026-W44", today, week, days, goal, streak, best, total, null);
    }

    private static FriendStats valid() {
        return stats(50, 130, 3, 4, 2, 5, 1234);
    }

    private static int i(Map<String, Object> m, String key) {
        return (Integer) m.get(key);
    }

    /** validStats() de firestore.rules, campo a campo. */
    private static boolean acceptedByRules(Map<String, Object> d) {
        if (!d.keySet().equals(CONTRACT_KEYS)) {
            return false;
        }
        for (String k : CONTRACT_KEYS) {
            if (!k.endsWith("Key") && !k.equals("updatedAt") && !(d.get(k) instanceof Integer)) {
                return false;
            }
        }
        int today = i(d, "minutesToday");
        int days = i(d, "daysThisWeek");
        int week = i(d, "minutesWeekCapped");
        int goal = i(d, "weeklyGoal");
        int streak = i(d, "weekStreak");
        int best = i(d, "bestWeekStreak");
        int total = i(d, "totalMinutes");
        return d.get("dayKey") instanceof String && ((String) d.get("dayKey")).matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
                && d.get("weekKey") instanceof String && ((String) d.get("weekKey")).matches("^[0-9]{4}-W[0-9]{2}$")
                && today >= 0 && today <= 1440
                && days >= 0 && days <= 7
                && week >= 0 && week <= 1680 && week <= 240 * days
                && goal >= 1 && goal <= 7
                && streak >= 0
                && best >= streak
                && total >= 0;
    }

    @Test
    public void toMapHasExactlyTheTenContractKeys() {
        Map<String, Object> m = StatsWrites.toMap(valid(), SENTINEL);
        assertEquals(CONTRACT_KEYS, m.keySet());
        assertSame(SENTINEL, m.get("updatedAt"));
    }

    @Test
    public void noKeyMentionsFails() {
        for (String key : StatsWrites.toMap(valid(), SENTINEL).keySet()) {
            assertFalse(key, key.toLowerCase().contains("fail"));
        }
    }

    @Test
    public void theProductionMapUsesTheServerTimestamp() {
        Map<String, Object> m = StatsWrites.toMap(valid());
        assertEquals(CONTRACT_KEYS, m.keySet());
        assertEquals(FieldValue.serverTimestamp(), m.get("updatedAt"));
    }

    @Test
    public void validValuesGoThroughUnchangedAndPassTheRules() {
        Map<String, Object> m = StatsWrites.toMap(valid(), SENTINEL);
        assertTrue(acceptedByRules(m));
        assertEquals("2026-10-28", m.get("dayKey"));
        assertEquals("2026-W44", m.get("weekKey"));
        assertEquals(50, i(m, "minutesToday"));
        assertEquals(130, i(m, "minutesWeekCapped"));
        assertEquals(3, i(m, "daysThisWeek"));
        assertEquals(4, i(m, "weeklyGoal"));
        assertEquals(2, i(m, "weekStreak"));
        assertEquals(5, i(m, "bestWeekStreak"));
        assertEquals(1234, i(m, "totalMinutes"));
    }

    @Test
    public void minutesTodayIsClampedTo0To1440() {
        Map<String, Object> high = StatsWrites.toMap(stats(2000, 130, 3, 4, 2, 5, 10), SENTINEL);
        assertEquals(1440, i(high, "minutesToday"));
        assertTrue(acceptedByRules(high));
        Map<String, Object> low = StatsWrites.toMap(stats(-5, 130, 3, 4, 2, 5, 10), SENTINEL);
        assertEquals(0, i(low, "minutesToday"));
        assertTrue(acceptedByRules(low));
    }

    @Test
    public void minutesWeekCappedIsClampedTo1680And240PerDayAndNotNegative() {
        Map<String, Object> overAll = StatsWrites.toMap(stats(50, 5000, 7, 4, 2, 5, 10), SENTINEL);
        assertEquals(1680, i(overAll, "minutesWeekCapped"));
        assertTrue(acceptedByRules(overAll));
        Map<String, Object> overDays = StatsWrites.toMap(stats(50, 600, 2, 4, 2, 5, 10), SENTINEL);
        assertEquals(480, i(overDays, "minutesWeekCapped")); // 240 * daysThisWeek
        assertTrue(acceptedByRules(overDays));
        Map<String, Object> noDays = StatsWrites.toMap(stats(50, 30, 0, 4, 2, 5, 10), SENTINEL);
        assertEquals(0, i(noDays, "minutesWeekCapped"));
        assertTrue(acceptedByRules(noDays));
        Map<String, Object> negative = StatsWrites.toMap(stats(50, -1, 3, 4, 2, 5, 10), SENTINEL);
        assertEquals(0, i(negative, "minutesWeekCapped"));
        assertTrue(acceptedByRules(negative));
    }

    @Test
    public void daysThisWeekIsClampedTo0To7() {
        Map<String, Object> high = StatsWrites.toMap(stats(50, 130, 9, 4, 2, 5, 10), SENTINEL);
        assertEquals(7, i(high, "daysThisWeek"));
        assertTrue(acceptedByRules(high));
        Map<String, Object> low = StatsWrites.toMap(stats(50, 130, -1, 4, 2, 5, 10), SENTINEL);
        assertEquals(0, i(low, "daysThisWeek"));
        assertEquals(0, i(low, "minutesWeekCapped"));
        assertTrue(acceptedByRules(low));
    }

    @Test
    public void weeklyGoalIsClampedTo1To7() {
        Map<String, Object> zero = StatsWrites.toMap(stats(50, 130, 3, 0, 2, 5, 10), SENTINEL);
        assertEquals(1, i(zero, "weeklyGoal"));
        assertTrue(acceptedByRules(zero));
        Map<String, Object> nine = StatsWrites.toMap(stats(50, 130, 3, 9, 2, 5, 10), SENTINEL);
        assertEquals(7, i(nine, "weeklyGoal"));
        assertTrue(acceptedByRules(nine));
    }

    @Test
    public void weekStreakIsNeverNegative() {
        Map<String, Object> m = StatsWrites.toMap(stats(50, 130, 3, 4, -2, 5, 10), SENTINEL);
        assertEquals(0, i(m, "weekStreak"));
        assertEquals(5, i(m, "bestWeekStreak"));
        assertTrue(acceptedByRules(m));
    }

    @Test
    public void bestWeekStreakIsRaisedToWeekStreak() {
        Map<String, Object> m = StatsWrites.toMap(stats(50, 130, 3, 4, 6, 2, 10), SENTINEL);
        assertEquals(6, i(m, "weekStreak"));
        assertEquals(6, i(m, "bestWeekStreak"));
        assertTrue(acceptedByRules(m));
        Map<String, Object> bothNegative = StatsWrites.toMap(stats(50, 130, 3, 4, -3, -7, 10), SENTINEL);
        assertEquals(0, i(bothNegative, "weekStreak"));
        assertEquals(0, i(bothNegative, "bestWeekStreak"));
        assertTrue(acceptedByRules(bothNegative));
    }

    @Test
    public void totalMinutesIsNeverNegative() {
        Map<String, Object> m = StatsWrites.toMap(stats(50, 130, 3, 4, 2, 5, -40), SENTINEL);
        assertEquals(0, i(m, "totalMinutes"));
        assertTrue(acceptedByRules(m));
    }

    @Test
    public void everyFieldOutOfRangeAtOnceStillPassesTheRules() {
        Map<String, Object> m = StatsWrites.toMap(stats(2000, 9999, 9, 0, -1, -5, -1), SENTINEL);
        assertTrue(acceptedByRules(m));
    }

    @Test
    public void theFingerprintStartsWithTheKeysAndFollowsTheClampedValues() {
        String fp = StatsWrites.fingerprint(valid());
        assertTrue(fp, fp.startsWith("2026-10-28|2026-W44|"));
        assertEquals(StatsWrites.fingerprint(stats(1440, 130, 3, 4, 2, 5, 1234)),
                StatsWrites.fingerprint(stats(2000, 130, 3, 4, 2, 5, 1234)));
        assertNotEquals(fp, StatsWrites.fingerprint(stats(51, 130, 3, 4, 2, 5, 1234)));
    }
}
