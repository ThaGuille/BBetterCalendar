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

    private static final long NOW = 1_800_000_000_000L;

    private final SocialSnapshot mock = new MockSocialRepository(NOW).getSnapshot().getValue();

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
        // html 01 crown; since F4-u4 the crown is a uid, like RankingRepository's crownUid.
        assertEquals("laia", mock.getCrownUid());
        Friend top = mock.getTopFriend();
        assertNotNull(top);
        assertEquals("Laia", top.getName());
        assertEquals(192, top.getMinutesToday()); // 3:12:05
    }

    @Test
    public void mock_carriesNoRank() {
        // F3-u8: the rank is real now (RankingRepository -> CycleRank); the mock has none.
        assertNull(mock.getUserRank());
    }

    @Test
    public void friends_areOrderedStudyingFirstThenLongestSession() {
        List<Friend> friends = mock.getFriends();
        boolean seenAway = false;
        for (int i = 0; i < friends.size(); i++) {
            Friend f = friends.get(i);
            if (!f.isStudying()) seenAway = true;
            assertFalse("studying after away at " + i, seenAway && f.isStudying());
            assertFalse(f.getInitials().isEmpty());
            if (i > 0 && friends.get(i - 1).isStudying() && f.isStudying()) {
                assertTrue(friends.get(i - 1).getSessionStartMs() <= f.getSessionStartMs());
            }
        }
        assertEquals("Laia", friends.get(0).getName());
        assertEquals("Pau", friends.get(1).getName());
        // Laia lleva 3:12:05 en el momento en que se construyó la maqueta.
        assertEquals((3 * 3600 + 12 * 60 + 5) * 1000L, friends.get(0).getElapsedMs(NOW));
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
}
