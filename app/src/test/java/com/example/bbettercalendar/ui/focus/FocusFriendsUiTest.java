package com.example.bbettercalendar.ui.focus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.social.Friend;
import com.example.bbettercalendar.social.SocialSnapshot;
import com.example.bbettercalendar.social.presence.Presence;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FocusFriendsUiTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long SINCE = NOW - 1000L;

    private static Friend studying(String uid, String name, int color, long sessionStart) {
        return new Friend(uid, uid, name, color, Presence.studying(sessionStart, SINCE), 0L, 0L);
    }

    private static Friend away(String uid, String name) {
        return new Friend(uid, uid, name, 3, Presence.AWAY, 0L, 0L);
    }

    private static SocialSnapshot snap(String crown, Friend... f) {
        return new SocialSnapshot(Arrays.asList(f), null, crown);
    }

    @Test
    public void empty_hasEmptyFlagAndZeroHeader() {
        FocusFriendsUi.Result r = FocusFriendsUi.map(SocialSnapshot.EMPTY, NOW);
        assertTrue(r.empty);
        assertTrue(r.cells.isEmpty());
        assertEquals("0 / 0", r.header);
    }

    @Test
    public void allAway_dimmedWithNoTime() {
        FocusFriendsUi.Result r = FocusFriendsUi.map(snap(null, away("a", "Ana"), away("b", "Bea")), NOW);
        assertFalse(r.empty);
        assertEquals("0 / 2", r.header);
        for (FocusFriendsUi.Cell c : r.cells) {
            assertEquals(FocusFriendsUi.Line.AWAY, c.line);
            assertEquals("", c.timeText);
            assertEquals(FocusFriendsUi.AWAY_ALPHA, c.alpha, 0f);
        }
    }

    @Test
    public void ordering_studyingFirstLongestSessionFirst_headerCounts() {
        FocusFriendsUi.Result r = FocusFriendsUi.map(snap(null,
                away("z", "Zoe"),
                studying("b", "Bob", 1, NOW - 60_000L),
                studying("c", "Cal", 2, NOW - 600_000L)), NOW);
        assertEquals("2 / 3", r.header);
        assertEquals("Cal", r.cells.get(0).name);
        assertEquals("Bob", r.cells.get(1).name);
        assertEquals("Zoe", r.cells.get(2).name);
        assertEquals(2, r.cells.get(0).avatarColor);
        assertEquals(1f, r.cells.get(0).alpha, 0f);
    }

    @Test
    public void tickingTime_growsWithNow() {
        SocialSnapshot s = snap(null, studying("b", "Bob", 1, NOW - 12 * 60_000L));
        assertEquals("0:12:00", FocusFriendsUi.map(s, NOW).cells.get(0).timeText);
        assertEquals("0:12:01", FocusFriendsUi.map(s, NOW + 1000L).cells.get(0).timeText);
        assertEquals("0:12:05", FocusFriendsUi.map(s, NOW + 5999L).cells.get(0).timeText);
    }

    @Test
    public void crownOnAnAwayFriend_stillShown() {
        FocusFriendsUi.Result r = FocusFriendsUi.map(snap("z",
                studying("b", "Bob", 1, NOW - 1000L), away("z", "Zoe")), NOW);
        assertFalse(r.cells.get(0).crown);
        assertTrue(r.cells.get(1).crown);
        assertTrue(r.cells.get(1).isAway());
    }

    @Test
    public void timeOverOneHour_formatsHoursUnpadded() {
        SocialSnapshot s = snap(null, studying("b", "Bob", 1, NOW - (3600_000L + 5 * 60_000L + 9000L)));
        assertEquals("1:05:09", FocusFriendsUi.map(s, NOW).cells.get(0).timeText);
        assertEquals("12:00:00", FocusFriendsUi.formatTime(12 * 3600_000L));
    }

    @Test
    public void paused_frozenTimeAndLabelDoesNotTick() {
        Friend p = new Friend("p", "p", "Pau Roca", 4,
                Presence.paused(NOW - 600_000L, NOW - 300_000L, SINCE), 0L, 0L);
        SocialSnapshot s = snap(null, p);
        FocusFriendsUi.Cell now = FocusFriendsUi.map(s, NOW).cells.get(0);
        FocusFriendsUi.Cell later = FocusFriendsUi.map(s, NOW + 90_000L).cells.get(0);
        assertEquals(FocusFriendsUi.Line.PAUSED, now.line);
        assertEquals("0:05:00", now.timeText);
        assertEquals("0:05:00", later.timeText);
        assertEquals("Pau", now.name);
        assertEquals(1f, now.alpha, 0f);
        assertEquals("1 / 1", FocusFriendsUi.map(s, NOW).header);
    }

    @Test
    public void capsGridAtEightCells_headerStillCountsAll() {
        List<Friend> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) list.add(away("u" + i, "N" + i));
        FocusFriendsUi.Result r = FocusFriendsUi.map(new SocialSnapshot(list, null, null), NOW);
        assertEquals(8, r.cells.size());
        assertEquals("0 / 10", r.header);
    }
}
