package com.example.bbettercalendar.ui.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.stats.FriendStats;
import com.example.bbettercalendar.social.stats.RankingState.FriendStatsState;

import org.junit.Test;

/** F3-u6: el mapper puro del perfil de un amigo (02 §9). */
public class FriendProfileUiTest {

    private static final FriendCard CARD = new FriendCard("u1", "bob", "Bob", 2);

    private static FriendStatsState ready(int minutesWeek, int days, int goal, int streak, int best, int total) {
        FriendStats s = new FriendStats("2026-10-28", "2026-W44", 30, minutesWeek, days, goal, streak, best,
                total, null);
        return FriendStatsState.ready("u1", s, CARD);
    }

    @Test
    public void fullStats() {
        FriendProfileUi ui = FriendProfileUi.from(ready(310, 3, 4, 5, 9, 1500));
        assertTrue(ui.showStats());
        assertFalse(ui.showEmpty());
        assertFalse(ui.showLoading());
        assertEquals(5, ui.weekStreak);
        assertEquals(3, ui.daysDone);
        assertEquals(4, ui.goal);
        assertEquals(4, ui.dotsTotal);
        assertEquals(3, ui.dotsFilled);
        assertFalse(ui.goalMet);
        assertEquals(310, ui.minutesWeek);
        assertEquals(9, ui.bestStreak);
        assertEquals(25, ui.totalHours);
        assertEquals(0, ui.totalMinutesRest);
    }

    @Test
    public void staleWeekStillShowsTheBlockWithZeros() {
        // RankingRepository ya puso a 0 la semana ajena y la racha vieja; el total y la mejor se conservan.
        FriendProfileUi ui = FriendProfileUi.from(ready(0, 0, 3, 0, 6, 900));
        assertTrue(ui.showStats());
        assertEquals(0, ui.daysDone);
        assertEquals(0, ui.dotsFilled);
        assertEquals(3, ui.dotsTotal);
        assertFalse(ui.goalMet);
        assertEquals(0, ui.minutesWeek);
        assertEquals(0, ui.weekStreak);
        assertEquals(6, ui.bestStreak);
        assertEquals(15, ui.totalHours);
    }

    @Test
    public void noStatsShowsTheEmptyStateNotZeros() {
        FriendProfileUi ui = FriendProfileUi.from(FriendStatsState.noStats("u1", CARD));
        assertTrue(ui.showEmpty());
        assertFalse(ui.showStats());
        assertFalse(ui.isGone());
    }

    @Test
    public void goalMetFillsEveryDot() {
        FriendProfileUi ui = FriendProfileUi.from(ready(400, 4, 4, 1, 1, 400));
        assertTrue(ui.goalMet);
        assertEquals(4, ui.dotsFilled);
        assertEquals(4, ui.dotsTotal);
    }

    @Test
    public void moreDaysThanGoalStaysMetAndDotsCapAtGoal() {
        FriendProfileUi ui = FriendProfileUi.from(ready(900, 6, 3, 2, 2, 2000));
        assertTrue(ui.goalMet);
        assertEquals(3, ui.dotsFilled);
        assertEquals(6, ui.daysDone);
    }

    @Test
    public void largeTotalSplitsIntoHoursAndMinutes() {
        FriendProfileUi ui = FriendProfileUi.from(ready(100, 1, 3, 0, 0, 6025));
        assertEquals(100, ui.totalHours);
        assertEquals(25, ui.totalMinutesRest);
        assertTrue(ui.totalHasHours());
        assertTrue(ui.totalHasMinutes());
    }

    @Test
    public void smallTotalIsMinutesOnly() {
        FriendProfileUi ui = FriendProfileUi.from(ready(45, 1, 3, 0, 0, 45));
        assertFalse(ui.totalHasHours());
        assertTrue(ui.totalHasMinutes());
        assertEquals(45, ui.totalMinutesRest);
    }

    @Test
    public void exactHoursOmitMinutes() {
        FriendProfileUi ui = FriendProfileUi.from(ready(45, 1, 3, 0, 0, 120));
        assertTrue(ui.totalHasHours());
        assertFalse(ui.totalHasMinutes());
    }

    @Test
    public void loadingNullAndErrorAndGone() {
        assertTrue(FriendProfileUi.from(null).showLoading());
        assertTrue(FriendProfileUi.from(FriendStatsState.loading("u1")).showLoading());
        assertTrue(FriendProfileUi.from(FriendStatsState.error("u1", new RuntimeException("x"))).showError());
        assertTrue(FriendProfileUi.from(FriendStatsState.notFriend("u1")).isGone());
        assertTrue(FriendProfileUi.from(FriendStatsState.signedOut("u1")).isGone());
    }
}
