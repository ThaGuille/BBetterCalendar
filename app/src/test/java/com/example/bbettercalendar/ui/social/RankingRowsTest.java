package com.example.bbettercalendar.ui.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.stats.Ranking;
import com.example.bbettercalendar.social.stats.RankingState;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** F3-u7: el mapper puro de la pestaña Ranking (02 §6). */
public class RankingRowsTest {

    private static final FriendCard BOB = new FriendCard("b", "bob", "Bob", 2);
    private static final FriendCard CAROL = new FriendCard("c", "carol", "Carol", 5);

    private static Ranking.Row row(String uid, String handle, int pos, int value, int goal, boolean me) {
        return new Ranking.Row(uid, handle, pos, value, goal, me);
    }

    private static RankingState ready(List<Ranking.Row> minutes, List<Ranking.Row> consistency,
                                      FriendCard... friends) {
        Ranking r = new Ranking(minutes, consistency, null, 1, friends.length + 1);
        return RankingState.ready(r, Arrays.asList(friends));
    }

    private static RankingState threePeople() {
        return ready(
                Arrays.asList(row("b", "bob", 1, 300, 0, false), row("me", null, 2, 120, 0, true),
                        row("c", "carol", 2, 120, 0, false)),
                Arrays.asList(row("c", "carol", 1, 4, 4, false), row("me", null, 2, 3, 3, true),
                        row("b", "bob", 3, 2, 4, false)),
                BOB, CAROL);
    }

    @Test
    public void tiedRowsShareThePosition() {
        RankingRows rows = RankingRows.from(threePeople(), RankingRows.Category.MINUTES, "Alice", 1);
        assertEquals(RankingRows.Status.READY, rows.status);
        assertEquals(3, rows.items.size());
        assertEquals(1, rows.items.get(0).position);
        assertEquals(2, rows.items.get(1).position);
        assertEquals(2, rows.items.get(2).position);
    }

    @Test
    public void myRowIsMarkedAndUsesMyProfile() {
        RankingRows rows = RankingRows.from(threePeople(), RankingRows.Category.MINUTES, "Alice", 6);
        RankingRows.Item me = rows.items.get(1);
        assertTrue(me.isMe);
        assertEquals("Alice", me.name);
        assertEquals(6, me.avatarColor);
        assertNull(me.handle);
        assertFalse(rows.items.get(0).isMe);
        assertFalse(rows.items.get(2).isMe);
    }

    @Test
    public void friendRowsTakeNameAndColorFromTheirCard() {
        RankingRows rows = RankingRows.from(threePeople(), RankingRows.Category.MINUTES, "Alice", 1);
        RankingRows.Item bob = rows.items.get(0);
        assertEquals("Bob", bob.name);
        assertEquals("bob", bob.handle);
        assertEquals(2, bob.avatarColor);
        assertEquals(300, bob.value);
    }

    @Test
    public void consistencyUsesDaysAndGoalAndItsOwnOrder() {
        RankingRows rows = RankingRows.from(threePeople(), RankingRows.Category.CONSISTENCY, "Alice", 1);
        assertEquals(RankingRows.Category.CONSISTENCY, rows.category);
        assertEquals("c", rows.items.get(0).uid);
        assertEquals(4, rows.items.get(0).value);
        assertEquals(4, rows.items.get(0).goal);
        assertTrue(rows.items.get(0).goalMet());
        assertEquals("b", rows.items.get(2).uid);
        assertFalse(rows.items.get(2).goalMet());
    }

    @Test
    public void noFriendsIsTheEmptyState() {
        Ranking r = new Ranking(Collections.singletonList(row("me", null, 1, 50, 0, true)),
                Collections.singletonList(row("me", null, 1, 1, 3, true)), null, Ranking.NO_RANK, 1);
        RankingRows rows = RankingRows.from(RankingState.ready(r, Collections.<FriendCard>emptyList()),
                RankingRows.Category.MINUTES, "Alice", 1);
        assertEquals(RankingRows.Status.EMPTY, rows.status);
        assertTrue(rows.items.isEmpty());
    }

    @Test
    public void aFriendAtZeroIsJustAnotherRow() {
        RankingState s = ready(
                Arrays.asList(row("me", null, 1, 90, 0, true), row("b", "bob", 2, 0, 0, false)),
                Arrays.asList(row("me", null, 1, 2, 3, true), row("b", "bob", 2, 0, 0, false)),
                BOB);
        RankingRows rows = RankingRows.from(s, RankingRows.Category.MINUTES, "Alice", 1);
        RankingRows.Item bob = rows.items.get(1);
        assertEquals(2, bob.position);
        assertEquals(0, bob.value);
        assertEquals("Bob", bob.name);
        assertFalse(bob.goalMet());
    }

    @Test
    public void switchingCategoryKeepsTheSamePeopleWithOtherValues() {
        RankingState s = threePeople();
        RankingRows minutes = RankingRows.from(s, RankingRows.Category.MINUTES, "Alice", 1);
        RankingRows days = RankingRows.from(s, RankingRows.Category.CONSISTENCY, "Alice", 1);
        assertEquals(minutes.items.size(), days.items.size());
        assertEquals(300, minutes.items.get(0).value);
        assertEquals("b", minutes.items.get(0).uid);
        assertEquals("c", days.items.get(0).uid);
        assertEquals(4, days.items.get(0).value);
    }

    @Test
    public void nonReadyStatesMapToTheirOwnStatus() {
        assertEquals(RankingRows.Status.LOADING,
                RankingRows.from(RankingState.loading(), RankingRows.Category.MINUTES, null, 0).status);
        assertEquals(RankingRows.Status.LOADING,
                RankingRows.from(null, RankingRows.Category.MINUTES, null, 0).status);
        assertEquals(RankingRows.Status.ERROR, RankingRows.from(RankingState.error(new RuntimeException()),
                RankingRows.Category.MINUTES, null, 0).status);
        assertEquals(RankingRows.Status.HIDDEN,
                RankingRows.from(RankingState.signedOut(), RankingRows.Category.MINUTES, null, 0).status);
    }
}
