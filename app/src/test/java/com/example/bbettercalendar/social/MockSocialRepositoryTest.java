package com.example.bbettercalendar.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MockSocialRepositoryTest {

    private final SocialSnapshot mock = new MockSocialRepository().getSnapshot().getValue();

    @Test
    public void mock_matchesStitchCounts() {
        assertNotNull(mock);
        // html 01: "Studying now 5 / 8"
        assertEquals(8, mock.getFriendCount());
        assertEquals(5, mock.getStudyingCount());
        assertFalse(mock.isEmpty());
    }

    @Test
    public void mock_topFriendIsLaia() {
        // html 01 crown, html 02 "Laia and 3 others still studying"
        Friend top = mock.getTopFriend();
        assertNotNull(top);
        assertEquals("Laia", top.getName());
        assertEquals(192, top.getMinutesToday()); // 3:12:05
    }

    @Test
    public void mock_carriesNoRank() {
        // F3-u8: the rank is real now (RankingRepository -> CycleRank); the mock has none.
        assertNull(mock.getUserRank());
        assertNull(new MockSocialRepository().getHomeSnapshot().getValue().getUserRank());
        assertNull(new MockSocialRepository().getCelebrationSnapshot().getValue().getUserRank());
    }

    @Test
    public void friends_areOrderedStudyingFirstThenByTime() {
        List<Friend> friends = mock.getFriends();
        boolean seenAway = false;
        for (int i = 0; i < friends.size(); i++) {
            Friend f = friends.get(i);
            if (!f.isStudying()) seenAway = true;
            assertFalse("studying after away at " + i, seenAway && f.isStudying());
            assertFalse(f.getInitials().isEmpty());
            if (i > 0 && friends.get(i - 1).isStudying() == f.isStudying()) {
                assertTrue(friends.get(i - 1).getFocusSecondsToday() >= f.getFocusSecondsToday());
            }
        }
        assertEquals("Laia", friends.get(0).getName());
        assertEquals("Pau", friends.get(1).getName());
    }

    @Test
    public void studying_containsOnlyStudying() {
        for (Friend f : mock.getStudying()) {
            assertTrue(f.isStudying());
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void friends_areImmutable() {
        mock.getFriends().clear();
    }

    @Test
    public void empty_repositoryHasNoData() {
        SocialSnapshot empty = new EmptySocialRepository().getSnapshot().getValue();
        assertSame(SocialSnapshot.EMPTY, empty);
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.getStudyingCount());
        assertNull(empty.getTopFriend());
        assertNull(empty.getUserRank());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rank_rejectsPositionOutsideGroup() {
        new SocialRank(3, 2);
    }

    @Test
    public void home_matchesHtml04() {
        // html 04: "3 friends studying now" · "Maya, Alex, Leo" (avatars M, A, L)
        SocialSnapshot home = new MockSocialRepository().getHomeSnapshot().getValue();
        assertNotNull(home);
        assertEquals(3, home.getStudyingCount());
        List<Friend> studying = home.getStudying();
        assertEquals("Maya", studying.get(0).getName());
        assertEquals("Alex", studying.get(1).getName());
        assertEquals("Leo", studying.get(2).getName());
        for (Friend f : studying) {
            assertTrue(f.isStudying());
        }
    }

    @Test
    public void celebration_matchesHtml02() {
        // html 02: "Laia and 3 others still studying", avatars L, M, J, rank 2nd among friends
        SocialSnapshot celebration = new MockSocialRepository().getCelebrationSnapshot().getValue();
        assertNotNull(celebration);
        assertEquals(4, celebration.getStudyingCount());
        List<Friend> studying = celebration.getStudying();
        assertEquals("Laia", studying.get(0).getName());
        assertEquals("Marc", studying.get(1).getName());
        assertEquals("Júlia", studying.get(2).getName());
        assertNull(celebration.getUserRank());
    }

    @Test
    public void empty_repositoryHasNoDataForEveryScreen() {
        EmptySocialRepository empty = new EmptySocialRepository();
        assertSame(SocialSnapshot.EMPTY, empty.getHomeSnapshot().getValue());
        assertSame(SocialSnapshot.EMPTY, empty.getCelebrationSnapshot().getValue());
    }
}
