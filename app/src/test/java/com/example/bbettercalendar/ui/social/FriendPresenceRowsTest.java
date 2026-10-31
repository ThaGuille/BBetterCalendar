package com.example.bbettercalendar.ui.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.social.Friend;
import com.example.bbettercalendar.social.SocialSnapshot;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.presence.Presence;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** F4-u7: el mapper puro de la sección Amigos con presencia (02 §7 y §9). */
public class FriendPresenceRowsTest {

    private static final FriendCard ANA = new FriendCard("a", "ana", "Ana", 1);
    private static final FriendCard BOB = new FriendCard("b", "bob", "bob", 2);
    private static final FriendCard CAROL = new FriendCard("c", "carol", "Carol", 3);
    private static final FriendCard DAN = new FriendCard("d", "dan", "Dan", 4);

    private static Friend studying(FriendCard c, long start) {
        return new Friend(c.uid, c.handle, c.displayName, c.avatarColor, Presence.studying(start, start), 0L, 0);
    }

    private static Friend paused(FriendCard c, long start, long pausedAt) {
        return new Friend(c.uid, c.handle, c.displayName, c.avatarColor,
                Presence.paused(start, pausedAt, pausedAt), 0L, 0);
    }

    private static Friend away(FriendCard c) {
        return new Friend(c.uid, c.handle, c.displayName, c.avatarColor, Presence.AWAY, 0L, 0);
    }

    private static SocialSnapshot snap(Friend... f) {
        return new SocialSnapshot(Arrays.asList(f), null, null);
    }

    private static List<String> names(List<FriendPresenceRows.Entry> rows) {
        List<String> out = new ArrayList<>();
        for (FriendPresenceRows.Entry e : rows) {
            out.add(e.card.displayName);
        }
        return out;
    }

    @Test
    public void studyingFirst_thenByName() {
        List<FriendPresenceRows.Entry> rows = FriendPresenceRows.from(Arrays.asList(ANA, BOB, CAROL),
                snap(away(ANA), away(BOB), studying(CAROL, 1000)));
        assertEquals(Arrays.asList("Carol", "Ana", "bob"), names(rows));
        assertTrue(rows.get(0).studying);
        assertFalse(rows.get(1).studying);
    }

    @Test
    public void amongStudying_longestSessionFirst() {
        List<FriendPresenceRows.Entry> rows = FriendPresenceRows.from(Arrays.asList(ANA, BOB, CAROL),
                snap(studying(ANA, 3000), studying(BOB, 1000), studying(CAROL, 2000)));
        assertEquals(Arrays.asList("bob", "Carol", "Ana"), names(rows));
    }

    @Test
    public void sameStart_fallsBackToName_ignoringCase() {
        List<FriendPresenceRows.Entry> rows = FriendPresenceRows.from(Arrays.asList(CAROL, BOB, ANA),
                snap(studying(CAROL, 500), studying(BOB, 500), studying(ANA, 500)));
        assertEquals(Arrays.asList("Ana", "bob", "Carol"), names(rows));
    }

    @Test
    public void friendMissingFromSnapshot_isAway() {
        List<FriendPresenceRows.Entry> rows = FriendPresenceRows.from(Arrays.asList(ANA, BOB),
                snap(studying(BOB, 100)));
        assertEquals(Arrays.asList("bob", "Ana"), names(rows));
        assertFalse(rows.get(1).studying);
        assertEquals(0L, rows.get(1).elapsedMs(999_999L));
    }

    @Test
    public void nullOrEmptySnapshot_everyoneAway_orderedByName() {
        List<FriendCard> friends = Arrays.asList(DAN, BOB, ANA);
        for (SocialSnapshot s : new SocialSnapshot[] {null, SocialSnapshot.EMPTY}) {
            List<FriendPresenceRows.Entry> rows = FriendPresenceRows.from(friends, s);
            assertEquals(Arrays.asList("Ana", "bob", "Dan"), names(rows));
            for (FriendPresenceRows.Entry e : rows) {
                assertFalse(e.studying);
            }
        }
    }

    @Test
    public void emptyFriends_isEmpty() {
        assertTrue(FriendPresenceRows.from(Collections.<FriendCard>emptyList(), snap(studying(ANA, 1))).isEmpty());
    }

    @Test
    public void pausedFriend_isStudyingGroup_withFrozenTime() {
        List<FriendPresenceRows.Entry> rows = FriendPresenceRows.from(Arrays.asList(ANA, BOB),
                snap(away(ANA), paused(BOB, 1_000L, 1_000L + 42 * 60_000L)));
        FriendPresenceRows.Entry bob = rows.get(0);
        assertEquals("bob", bob.card.displayName);
        assertTrue(bob.studying);
        assertTrue(bob.paused);
        // congelado: no depende de la hora de hoy
        assertEquals(42 * 60_000L, bob.elapsedMs(1_000L));
        assertEquals(42 * 60_000L, bob.elapsedMs(10_000_000_000L));
        assertEquals("0:42", FriendPresenceRows.formatElapsed(bob.elapsedMs(5L)));
    }

    @Test
    public void pausedGoesAfterActiveStudying_evenWithEarlierSessionStart() {
        List<FriendPresenceRows.Entry> rows = FriendPresenceRows.from(Arrays.asList(ANA, BOB),
                snap(studying(ANA, 5_000L), paused(BOB, 1_000L, 2_000L)));
        assertEquals(Arrays.asList("Ana", "bob"), names(rows));
    }

    @Test
    public void amongPaused_longestSessionFirst_andAwayStaysLast() {
        List<FriendPresenceRows.Entry> rows = FriendPresenceRows.from(Arrays.asList(ANA, BOB, CAROL, DAN),
                snap(away(ANA), paused(BOB, 3_000L, 4_000L), paused(CAROL, 1_000L, 2_000L),
                        studying(DAN, 9_000L)));
        assertEquals(Arrays.asList("Dan", "Carol", "bob", "Ana"), names(rows));
    }

    @Test
    public void liveElapsed_growsAndNeverNegative() {
        FriendPresenceRows.Entry e = FriendPresenceRows.from(Collections.singletonList(ANA),
                snap(studying(ANA, 10_000L))).get(0);
        assertEquals(5_000L, e.elapsedMs(15_000L));
        assertEquals(0L, e.elapsedMs(2_000L));
    }

    @Test
    public void formatElapsed_hMm() {
        assertEquals("0:00", FriendPresenceRows.formatElapsed(0));
        assertEquals("0:00", FriendPresenceRows.formatElapsed(-5));
        assertEquals("0:59", FriendPresenceRows.formatElapsed(59 * 60_000L + 59_000L));
        assertEquals("1:02", FriendPresenceRows.formatElapsed(62 * 60_000L));
        assertEquals("12:00", FriendPresenceRows.formatElapsed(12 * 3_600_000L));
    }
}
