package com.example.bbettercalendar.social.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.example.bbettercalendar.social.stats.RankingCalculator.FriendInput;

/** F3-u3: semántica del ranking (02 §6, D-030). Hoy = 2026-10-28 (2026-W44, anterior 2026-W43). */
public class RankingCalculatorTest {

    private static final WeekKey.Keys KEYS = WeekKey.Keys.of(LocalDate.of(2026, 10, 28));
    private static final String DAY = "2026-10-28";
    private static final String WEEK = "2026-W44";

    /** Stats de esta semana y hoy: minutos de hoy, de la semana (con tope), días y objetivo. */
    private static FriendStats now(int today, int week, int days, int goal) {
        return new FriendStats(DAY, WEEK, today, week, days, goal, 0, 0, 0, null);
    }

    private static FriendInput f(String uid, FriendStats s) {
        return new FriendInput(uid, uid, s);
    }

    private static List<String> uids(List<Ranking.Row> rows) {
        List<String> out = new ArrayList<>();
        for (Ranking.Row r : rows) {
            out.add(r.uid);
        }
        return out;
    }

    private static List<Integer> positions(List<Ranking.Row> rows) {
        List<Integer> out = new ArrayList<>();
        for (Ranking.Row r : rows) {
            out.add(r.position);
        }
        return out;
    }

    private static Ranking.Row row(List<Ranking.Row> rows, String uid) {
        for (Ranking.Row r : rows) {
            if (r.uid.equals(uid)) {
                return r;
            }
        }
        throw new AssertionError("no row " + uid);
    }

    @Test
    public void plainOrder_byCappedWeekMinutes() {
        Ranking r = RankingCalculator.compute("me", now(0, 100, 1, 3), Arrays.asList(
                f("ana", now(0, 300, 3, 3)), f("ben", now(0, 50, 1, 3))), KEYS);
        assertEquals(Arrays.asList("ana", "me", "ben"), uids(r.minutes));
        assertEquals(Arrays.asList(1, 2, 3), positions(r.minutes));
        assertEquals(300, r.minutes.get(0).value);
        assertTrue(row(r.minutes, "me").isMe);
        assertEquals(2, r.myMinutesPosition);
        assertEquals(3, r.groupSize);
    }

    @Test
    public void ties_shareThePosition_withCompetitionRanking() {
        Ranking r = RankingCalculator.compute("me", now(0, 200, 2, 3), Arrays.asList(
                f("ana", now(0, 400, 3, 3)), f("ben", now(0, 200, 2, 3)), f("cris", now(0, 100, 1, 3))),
                KEYS);
        assertEquals(Arrays.asList(1, 2, 2, 4), positions(r.minutes));
        // dentro del empate, yo primero y luego por @usuario
        assertEquals(Arrays.asList("ana", "me", "ben", "cris"), uids(r.minutes));
        assertEquals(2, r.myMinutesPosition);
    }

    @Test
    public void friendWithAnotherWeekKey_countsZero() {
        FriendStats old = new FriendStats("2026-10-20", "2026-W43", 100, 500, 5, 3, 1, 2, 900, null);
        Ranking r = RankingCalculator.compute("me", now(0, 10, 1, 3), Arrays.asList(
                f("ana", old)), KEYS);
        assertEquals(0, row(r.minutes, "ana").value);
        assertEquals(Arrays.asList("me", "ana"), uids(r.minutes));
        assertEquals(1, r.myMinutesPosition);
        assertEquals(0, row(r.consistency, "ana").value);
        // y su minutesToday de otro día tampoco da corona
        assertNull(r.crownUid);
    }

    @Test
    public void friendWithNoDoc_countsZero_andStillAppears() {
        Ranking r = RankingCalculator.compute("me", now(0, 10, 1, 3), Arrays.asList(
                f("ana", null)), KEYS);
        assertEquals(2, r.minutes.size());
        Ranking.Row ana = row(r.minutes, "ana");
        assertEquals(0, ana.value);
        assertEquals(2, ana.position);
        assertEquals(0, ana.goal);
    }

    @Test
    public void consistency_usesTheUncappedRatio() {
        // Ana 5/3 > Ben 3/3 > Cris 2/4
        Ranking r = RankingCalculator.compute("me", null, Arrays.asList(
                f("cris", now(0, 0, 2, 4)), f("ben", now(0, 0, 3, 3)), f("ana", now(0, 0, 5, 3))), KEYS);
        List<Ranking.Row> c = r.consistency;
        assertEquals("ana", c.get(0).uid);
        assertEquals("ben", c.get(1).uid);
        assertEquals("cris", c.get(2).uid);
        assertEquals("me", c.get(3).uid);
        assertEquals(Arrays.asList(1, 2, 3, 4), positions(c));
        assertEquals(5, c.get(0).value);
        assertEquals(3, c.get(0).goal);
    }

    @Test
    public void consistency_equalRatiosTie() {
        // 3/3 y 4/4 valen 1; 2/4 y 1/2 valen 0.5
        Ranking r = RankingCalculator.compute("me", now(0, 0, 4, 4), Arrays.asList(
                f("ana", now(0, 0, 3, 3)), f("ben", now(0, 0, 2, 4)), f("cris", now(0, 0, 1, 2))), KEYS);
        assertEquals(Arrays.asList(1, 1, 3, 3), positions(r.consistency));
        assertEquals(Arrays.asList("me", "ana", "ben", "cris"), uids(r.consistency));
    }

    @Test
    public void crown_onlyAmongTheSameDayKey_neverMe() {
        FriendStats yesterday = new FriendStats("2026-10-27", WEEK, 200, 200, 1, 3, 0, 0, 0, null);
        Ranking r = RankingCalculator.compute("me", now(230, 230, 1, 3), Arrays.asList(
                f("ana", yesterday), f("ben", now(60, 60, 1, 3))), KEYS);
        assertEquals("ben", r.crownUid);
    }

    @Test
    public void crown_cappedAt240_andHandleBreaksTies() {
        Ranking r = RankingCalculator.compute("me", null, Arrays.asList(
                f("zoe", now(1000, 240, 1, 3)), f("ana", now(240, 240, 1, 3)), f("bob", now(239, 239, 1, 3))),
                KEYS);
        // 1000 y 240 cuentan 240: empate entre zoe y ana, gana el handle menor
        assertEquals("ana", r.crownUid);
    }

    @Test
    public void crown_noneAtZeroMinutes() {
        Ranking r = RankingCalculator.compute("me", now(100, 100, 1, 3), Arrays.asList(
                f("ana", now(0, 50, 1, 3)), f("ben", null)), KEYS);
        assertNull(r.crownUid);
    }

    @Test
    public void noFriends_groupSizeOne_andNoRank() {
        Ranking r = RankingCalculator.compute("me", now(10, 10, 1, 3),
                Collections.<FriendInput>emptyList(), KEYS);
        assertEquals(1, r.groupSize);
        assertEquals(Ranking.NO_RANK, r.myMinutesPosition);
        assertEquals(1, r.minutes.size());
        assertNull(r.crownUid);
    }

    @Test
    public void myOwnDocMissing_countsZero() {
        Ranking r = RankingCalculator.compute("me", null, Arrays.asList(
                f("ana", now(0, 40, 1, 3))), KEYS);
        Ranking.Row me = row(r.minutes, "me");
        assertEquals(0, me.value);
        assertEquals(2, me.position);
        assertEquals(2, r.myMinutesPosition);
        assertEquals(0, row(r.consistency, "me").goal);
    }

    @Test
    public void myStaleDoc_countsZeroToo() {
        FriendStats lastWeek = new FriendStats("2026-10-21", "2026-W43", 0, 500, 5, 3, 1, 1, 0, null);
        Ranking r = RankingCalculator.compute("me", lastWeek, Arrays.asList(
                f("ana", now(0, 40, 1, 3))), KEYS);
        assertEquals(0, row(r.minutes, "me").value);
        assertEquals(2, r.myMinutesPosition);
    }

    @Test
    public void weekStreak_shownForThisWeekOrThePreviousOne_elseZero() {
        FriendStats mine = new FriendStats(DAY, WEEK, 0, 0, 0, 3, 4, 6, 100, null);
        FriendStats prev = new FriendStats("2026-10-21", "2026-W43", 0, 0, 0, 3, 4, 6, 100, null);
        FriendStats older = new FriendStats("2026-10-14", "2026-W42", 0, 0, 0, 3, 4, 6, 100, null);
        assertEquals(4, RankingCalculator.applySemantics(mine, KEYS).weekStreak);
        assertEquals(4, RankingCalculator.applySemantics(prev, KEYS).weekStreak);
        FriendStats o = RankingCalculator.applySemantics(older, KEYS);
        assertEquals(0, o.weekStreak);
        // bestWeekStreak y totalMinutes tal como se publicaron
        assertEquals(6, o.bestWeekStreak);
        assertEquals(100, o.totalMinutes);
        assertNull(RankingCalculator.applySemantics(null, KEYS));
    }

    @Test
    public void applySemantics_zeroesWeekAndDayFieldsOfAnotherWeek() {
        FriendStats prev = new FriendStats("2026-10-21", "2026-W43", 90, 500, 5, 3, 4, 6, 100, null);
        FriendStats s = RankingCalculator.applySemantics(prev, KEYS);
        assertNotNull(s);
        assertEquals(0, s.minutesToday);
        assertEquals(0, s.minutesWeekCapped);
        assertEquals(0, s.daysThisWeek);
        assertEquals(3, s.weeklyGoal);
    }
}
