package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageInsightsTest {

    private static final double EPS = 1e-9;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final String A = "com.a";
    private static final String B = "com.b";

    private static AppUsageDaily row(LocalDate day, String pkg, int min) {
        return new AppUsageDaily(day.toString(), pkg, min);
    }

    private static Map<String, Integer> live(Object... pairs) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    private static AppRule rule(String pkg, int limit, ZonedDateTime since) {
        AppRule r = new AppRule(pkg, true);
        r.dailyLimitMinutes = limit;
        r.limitSinceMillis = since == null ? 0 : since.toInstant().toEpochMilli();
        return r;
    }

    private static UsageInsights.AppAverage find(List<UsageInsights.AppAverage> l, String pkg) {
        for (UsageInsights.AppAverage a : l) {
            if (a.packageName.equals(pkg)) {
                return a;
            }
        }
        return null;
    }

    @Test
    public void dailyAveragesEmptyGivesNothingAnywhere() {
        List<AppUsageDaily> none = Collections.emptyList();
        assertTrue(UsageInsights.dailyAverages(none, null, TODAY, 7).isEmpty());
        assertTrue(UsageInsights.dailyAverages(none, new HashMap<String, Integer>(), TODAY, 7).isEmpty());
        assertNull(UsageInsights.beforeAfter(none, null, rule(A, 30, TODAY.minusDays(5).atTime(12, 0).atZone(UTC)),
                TODAY, UTC));
        assertNull(UsageInsights.focusVsPhone(new HashMap<LocalDate, Integer>(), new HashMap<LocalDate, Integer>()));
        assertTrue(UsageInsights.suggested(Collections.<UsageInsights.AppAverage>emptyList(),
                Collections.<AppRule>emptyList()).isEmpty());
        assertTrue(UsageInsights.topN(Collections.<UsageInsights.AppAverage>emptyList(), 5).isEmpty());
    }

    @Test
    public void dailyAveragesSingleDayIsThatDayOnly() {
        List<AppUsageDaily> rows = new ArrayList<>();
        rows.add(row(TODAY.minusDays(1), A, 40));
        rows.add(row(TODAY.minusDays(1), B, 10));
        List<UsageInsights.AppAverage> avg = UsageInsights.dailyAverages(rows, null, TODAY, 30);
        assertEquals(2, avg.size());
        assertEquals(A, avg.get(0).packageName);
        assertEquals(40.0, avg.get(0).averageMinutes, EPS);
        assertEquals(10.0, find(avg, B).averageMinutes, EPS);
    }

    @Test
    public void dailyAveragesDivideByObservedDaysNotByTheRange() {
        // 4 días observados de un rango de 30: la media de A es 100/4, no 100/30. B solo uso 1 día: 20/4.
        List<AppUsageDaily> rows = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            rows.add(row(TODAY.minusDays(i), A, 25));
        }
        rows.add(row(TODAY.minusDays(2), B, 20));
        List<UsageInsights.AppAverage> avg = UsageInsights.dailyAverages(rows, null, TODAY, 30);
        assertEquals(25.0, find(avg, A).averageMinutes, EPS);
        assertEquals(5.0, find(avg, B).averageMinutes, EPS);
    }

    @Test
    public void dailyAveragesUseLiveTodayAndIgnoreAStoredTodayRow() {
        List<AppUsageDaily> rows = new ArrayList<>();
        rows.add(row(TODAY.minusDays(1), A, 30));
        rows.add(row(TODAY, A, 999)); // no debería existir; manda el uso en vivo
        List<UsageInsights.AppAverage> avg = UsageInsights.dailyAverages(rows, live(A, 50), TODAY, 7);
        assertEquals(40.0, find(avg, A).averageMinutes, EPS); // (30 + 50) / 2 días
    }

    @Test
    public void dailyAveragesRespectTheRangeWindow() {
        List<AppUsageDaily> rows = new ArrayList<>();
        rows.add(row(TODAY.minusDays(1), A, 10));
        rows.add(row(TODAY.minusDays(20), A, 500)); // fuera de los últimos 7 días
        List<UsageInsights.AppAverage> avg = UsageInsights.dailyAverages(rows, null, TODAY, 7);
        assertEquals(10.0, find(avg, A).averageMinutes, EPS);
    }

    @Test
    public void topNKeepsTheHighestAndSortsTiesByPackage() {
        List<AppUsageDaily> rows = new ArrayList<>();
        LocalDate d = TODAY.minusDays(1);
        rows.add(row(d, "z", 30));
        rows.add(row(d, "m", 30));
        rows.add(row(d, "a", 90));
        rows.add(row(d, "q", 5));
        List<UsageInsights.AppAverage> top = UsageInsights.topN(UsageInsights.dailyAverages(rows, null, TODAY, 7), 3);
        assertEquals(3, top.size());
        assertEquals("a", top.get(0).packageName);
        assertEquals("m", top.get(1).packageName);
        assertEquals("z", top.get(2).packageName);
    }

    @Test
    public void suggestedNeedsTopThreeAtLeast30MinutesAndNoLimit() {
        List<AppUsageDaily> rows = new ArrayList<>();
        LocalDate d = TODAY.minusDays(1);
        rows.add(row(d, "limited", 120));
        rows.add(row(d, "free", 90));
        rows.add(row(d, "alsoLimited", 85));
        rows.add(row(d, "fourth", 80)); // sin límite y con > 30 min, pero 4.º por uso
        rows.add(row(d, "low", 29));
        List<AppRule> rules = new ArrayList<>();
        rules.add(rule("limited", 60, TODAY.minusDays(9).atTime(9, 0).atZone(UTC)));
        rules.add(rule("alsoLimited", 45, TODAY.minusDays(9).atTime(9, 0).atZone(UTC)));
        List<UsageInsights.AppAverage> sug = UsageInsights.suggested(
                UsageInsights.dailyAverages(rows, null, TODAY, 7), rules);
        assertEquals(1, sug.size());
        assertEquals("free", sug.get(0).packageName);
    }

    @Test
    public void suggestedSkipsAppsUnder30MinutesEvenInTheTopThree() {
        List<AppUsageDaily> rows = new ArrayList<>();
        rows.add(row(TODAY.minusDays(1), "low", 29));
        assertTrue(UsageInsights.suggested(UsageInsights.dailyAverages(rows, null, TODAY, 7),
                Collections.<AppRule>emptyList()).isEmpty());
    }

    @Test
    public void beforeAfterWithTooFewDaysOnEachSideIsNull() {
        AppRule r = rule(A, 30, ZonedDateTime.of(2026, 10, 10, 12, 0, 0, 0, UTC));
        List<AppUsageDaily> rows = new ArrayList<>();
        rows.add(row(LocalDate.of(2026, 10, 8), A, 60));
        rows.add(row(LocalDate.of(2026, 10, 9), A, 60)); // solo 2 días antes
        for (int d = 10; d <= 19; d++) {
            rows.add(row(LocalDate.of(2026, 10, d), A, 30));
        }
        assertNull(UsageInsights.beforeAfter(rows, null, r, TODAY, UTC));
        // y con 3 antes pero solo 2 después tampoco
        List<AppUsageDaily> rows2 = new ArrayList<>();
        for (int d = 7; d <= 9; d++) {
            rows2.add(row(LocalDate.of(2026, 10, d), A, 60));
        }
        rows2.add(row(LocalDate.of(2026, 10, 18), A, 30));
        rows2.add(row(LocalDate.of(2026, 10, 19), A, 30));
        assertNull(UsageInsights.beforeAfter(rows2, null, r, TODAY, UTC));
    }

    @Test
    public void beforeAfterAveragesBothSidesAndRecoversTheDifference() {
        AppRule r = rule(A, 30, ZonedDateTime.of(2026, 10, 10, 12, 0, 0, 0, UTC));
        List<AppUsageDaily> rows = new ArrayList<>();
        for (int d = 3; d <= 9; d++) {
            rows.add(row(LocalDate.of(2026, 10, d), A, 60));
            rows.add(row(LocalDate.of(2026, 10, d), B, 5));
        }
        for (int d = 10; d <= 19; d++) {
            rows.add(row(LocalDate.of(2026, 10, d), A, 30));
        }
        UsageInsights.BeforeAfter ba = UsageInsights.beforeAfter(rows, live(A, 30), r, TODAY, UTC);
        assertNotNull(ba);
        assertEquals(7, ba.beforeDays);
        assertEquals(11, ba.afterDays); // 10..19 más hoy en vivo
        assertEquals(60.0, ba.beforeAverage, EPS);
        assertEquals(30.0, ba.afterAverage, EPS);
        assertTrue(ba.improved());
        assertEquals(330, ba.recoveredMinutes());
    }

    @Test
    public void beforeAfterWorseAfterwardsRecoversNothing() {
        AppRule r = rule(A, 30, ZonedDateTime.of(2026, 10, 10, 12, 0, 0, 0, UTC));
        List<AppUsageDaily> rows = new ArrayList<>();
        for (int d = 5; d <= 9; d++) {
            rows.add(row(LocalDate.of(2026, 10, d), A, 20));
        }
        for (int d = 10; d <= 14; d++) {
            rows.add(row(LocalDate.of(2026, 10, d), A, 50));
        }
        UsageInsights.BeforeAfter ba = UsageInsights.beforeAfter(rows, null, r, TODAY, UTC);
        assertNotNull(ba);
        assertFalse(ba.improved());
        assertEquals(0, ba.recoveredMinutes());
    }

    @Test
    public void beforeAfterWithoutALimitOrSinceDateIsNull() {
        List<AppUsageDaily> rows = new ArrayList<>();
        assertNull(UsageInsights.beforeAfter(rows, null, rule(A, 0, null), TODAY, UTC));
        assertNull(UsageInsights.beforeAfter(rows, null, rule(A, 30, null), TODAY, UTC));
    }

    @Test
    public void beforeAfterDstLimitDayIsTheLocalDayNotTheUtcOne() {
        // 2026-03-29 00:30 en Madrid = 2026-03-28 23:30 UTC. El día del límite es el 29 (local):
        // 7 días antes (22..28) y desde el 29 hasta hoy (31).
        ZonedDateTime since = ZonedDateTime.of(2026, 3, 29, 0, 30, 0, 0, MADRID);
        LocalDate today = LocalDate.of(2026, 3, 31);
        List<AppUsageDaily> rows = new ArrayList<>();
        for (int d = 22; d <= 28; d++) {
            rows.add(row(LocalDate.of(2026, 3, d), A, 80));
        }
        rows.add(row(LocalDate.of(2026, 3, 29), A, 10));
        rows.add(row(LocalDate.of(2026, 3, 30), A, 10));
        UsageInsights.BeforeAfter ba = UsageInsights.beforeAfter(rows, live(A, 10), rule(A, 30, since), today, MADRID);
        assertNotNull(ba);
        assertEquals(7, ba.beforeDays);
        assertEquals(3, ba.afterDays);
        assertEquals(80.0, ba.beforeAverage, EPS);
        // Con UTC el día del límite sería el 28: el "después" incluiría un día de 80 y la media no sería 10.
        UsageInsights.BeforeAfter utc = UsageInsights.beforeAfter(rows, live(A, 10), rule(A, 30, since), today, UTC);
        assertNotNull(utc);
        assertEquals(4, utc.afterDays);
    }

    @Test
    public void beforeAfterLimitSetAtMidnightSharpCountsTheNewDayAsAfter() {
        // Límite puesto a las 00:00:00.000 del día 10: ese día ya es "después".
        ZonedDateTime since = ZonedDateTime.of(2026, 10, 10, 0, 0, 0, 0, UTC);
        List<AppUsageDaily> rows = new ArrayList<>();
        for (int d = 6; d <= 9; d++) {
            rows.add(row(LocalDate.of(2026, 10, d), A, 40));
        }
        for (int d = 10; d <= 12; d++) {
            rows.add(row(LocalDate.of(2026, 10, d), A, 20));
        }
        UsageInsights.BeforeAfter ba = UsageInsights.beforeAfter(rows, null, rule(A, 30, since), TODAY, UTC);
        assertNotNull(ba);
        assertEquals(4, ba.beforeDays);
        assertEquals(3, ba.afterDays);
    }

    @Test
    public void focusVsPhoneNeedsFourteenDays() {
        Map<LocalDate, Integer> phone = new HashMap<>();
        Map<LocalDate, Integer> focus = new HashMap<>();
        for (int i = 0; i < 13; i++) {
            phone.put(TODAY.minusDays(i), 100);
            focus.put(TODAY.minusDays(i), i % 2 == 0 ? 60 : 0);
        }
        assertNull(UsageInsights.focusVsPhone(focus, phone));
        phone.put(TODAY.minusDays(13), 100);
        assertNotNull(UsageInsights.focusVsPhone(focus, phone));
    }

    @Test
    public void focusVsPhoneComparesFocusDaysAgainstTheRest() {
        Map<LocalDate, Integer> phone = new HashMap<>();
        Map<LocalDate, Integer> focus = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            boolean f = i < 10;
            phone.put(TODAY.minusDays(i), f ? 100 : 150);
            if (f) {
                focus.put(TODAY.minusDays(i), 60);
            }
        }
        UsageInsights.FocusVsPhone r = UsageInsights.focusVsPhone(focus, phone);
        assertNotNull(r);
        assertEquals(10, r.focusDays);
        assertEquals(10, r.otherDays);
        assertEquals(100.0, r.phoneOnFocusDays, EPS);
        assertEquals(150.0, r.phoneOnOtherDays, EPS);
        assertEquals(50.0, r.savedMinutes(), EPS);
    }

    @Test
    public void focusVsPhoneWithATinyGroupIsNull() {
        Map<LocalDate, Integer> phone = new HashMap<>();
        Map<LocalDate, Integer> focus = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            phone.put(TODAY.minusDays(i), 100);
        }
        focus.put(TODAY, 60);
        focus.put(TODAY.minusDays(1), 60); // solo 2 días de enfoque
        assertNull(UsageInsights.focusVsPhone(focus, phone));
    }
}
