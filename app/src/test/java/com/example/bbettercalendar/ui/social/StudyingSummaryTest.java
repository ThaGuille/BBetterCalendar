package com.example.bbettercalendar.ui.social;

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

/** F4-u6: {@link StudyingSummary} from the single snapshot (counts, avatars, "and N more"). */
public class StudyingSummaryTest {

    private static final long MIN = 60_000L;
    private static final long NOW = 1_800_000_000_000L;

    private static Friend studying(String name, long startedMinAgo) {
        return new Friend(name, name, name, 0, Presence.studying(NOW - startedMinAgo * MIN, NOW), 0L, 0L);
    }

    private static Friend paused(String name, long startedMinAgo) {
        return new Friend(name, name, name, 0,
                Presence.paused(NOW - startedMinAgo * MIN, NOW - MIN, NOW), 0L, 0L);
    }

    private static Friend away(String name) {
        return new Friend(name, name, name, 0, Presence.AWAY, 0L, 0L);
    }

    private static SocialSnapshot snap(Friend... friends) {
        return new SocialSnapshot(new ArrayList<>(Arrays.asList(friends)), null, null);
    }

    @Test
    public void nobodyStudying_friendsAway() {
        StudyingSummary s = StudyingSummary.of(snap(away("Ana"), away("Bob")), 3);

        assertEquals(0, s.getStudyingCount());
        assertEquals(2, s.getFriendCount());
        assertTrue(s.hasFriends());
        assertTrue(s.isNobodyStudying());
        assertTrue(s.getShown().isEmpty());
        assertEquals("", s.getLeadName());
        assertEquals(0, s.getOthersCount());
        assertEquals(0, s.getMoreCount());
    }

    @Test
    public void noFriends_isNotAnEmptyRoomOfFriends() {
        StudyingSummary s = StudyingSummary.of(SocialSnapshot.EMPTY, 3);

        assertFalse(s.hasFriends());
        assertTrue(s.isNobodyStudying());
        assertEquals(0, s.getFriendCount());
    }

    @Test
    public void oneStudying_leadAndNoOthers() {
        StudyingSummary s = StudyingSummary.of(snap(studying("Bob", 5), away("Carol")), 3);

        assertEquals(1, s.getStudyingCount());
        assertEquals(2, s.getFriendCount());
        assertEquals(Arrays.asList("Bob"), s.getShownFirstNames());
        assertEquals("Bob", s.getLeadName());
        assertEquals(0, s.getOthersCount());
        assertEquals(0, s.getMoreCount());
    }

    @Test
    public void threeStudying_allShownInDisplayOrder() {
        // longest session first: Cleo (30), Ana (20), Bob (10)
        StudyingSummary s = StudyingSummary.of(
                snap(studying("Bob", 10), studying("Cleo", 30), studying("Ana", 20)), 3);

        assertEquals(Arrays.asList("Cleo", "Ana", "Bob"), s.getShownFirstNames());
        assertEquals(0, s.getMoreCount());
        assertEquals("Cleo", s.getLeadName());
        assertEquals(2, s.getOthersCount());
    }

    @Test
    public void moreStudyingThanAvatars_splitsTheRemainder() {
        List<Friend> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(studying("F" + i, 50 - i));
        }
        StudyingSummary s = StudyingSummary.of(new SocialSnapshot(five, null, null), 3);

        assertEquals(5, s.getStudyingCount());
        assertEquals(3, s.getShown().size());
        assertEquals(2, s.getMoreCount());
        assertEquals(4, s.getOthersCount());
    }

    @Test
    public void namesWithSpaces_useTheFirstWord() {
        StudyingSummary s = StudyingSummary.of(
                snap(studying("Laia Martí Puig", 20), studying("  Pau  ", 10)), 3);

        assertEquals(Arrays.asList("Laia", "Pau"), s.getShownFirstNames());
        assertEquals("Laia", s.getLeadName());
        assertEquals("Marc", StudyingSummary.firstName("Marc"));
    }

    @Test
    public void pausedFriendCountsAsStudying() {
        StudyingSummary s = StudyingSummary.of(snap(paused("Dana", 40), away("Eli")), 3);

        assertEquals(1, s.getStudyingCount());
        assertFalse(s.isNobodyStudying());
        assertEquals("Dana", s.getLeadName());
    }

    @Test
    public void zeroOrNegativeMax_showsNoneButCountsAll() {
        StudyingSummary s = StudyingSummary.of(snap(studying("Bob", 5), studying("Ana", 4)), -1);

        assertEquals(2, s.getStudyingCount());
        assertTrue(s.getShown().isEmpty());
        assertEquals(2, s.getMoreCount());
    }
}
