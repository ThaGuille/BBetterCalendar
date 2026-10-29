package com.example.bbettercalendar.ui.focus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import com.example.bbettercalendar.social.SocialRank;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.stats.Ranking;
import com.example.bbettercalendar.social.stats.RankingState;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CycleRankTest {

    private static final FriendCard BOB = new FriendCard("uidB", "bob", "Bob", 1);
    private static final FriendCard CAROL = new FriendCard("uidC", "carol", "Carol", 2);

    private static Ranking ranking(int myPosition, int groupSize) {
        List<Ranking.Row> none = Collections.emptyList();
        return new Ranking(none, none, null, myPosition, groupSize);
    }

    @Test
    public void readyWithFriends_givesMyPositionAndGroup() {
        RankingState state = RankingState.ready(ranking(2, 3), Arrays.asList(BOB, CAROL));
        SocialRank rank = CycleRank.from(state);
        assertNotNull(rank);
        assertEquals(2, rank.getPosition());
        assertEquals(3, rank.getGroupSize());
    }

    @Test
    public void tieAtMyPosition_sharesThePosition() {
        // 1, 2, 2: yo y Bob empatados en el 2.º, Carol 1.ª.
        RankingState state = RankingState.ready(ranking(2, 3), Arrays.asList(BOB, CAROL));
        SocialRank rank = CycleRank.from(state);
        assertNotNull(rank);
        assertEquals(2, rank.getPosition());
        assertEquals(3, rank.getGroupSize());
    }

    @Test
    public void readyWithoutFriends_hasNoRank() {
        RankingState state = RankingState.ready(ranking(Ranking.NO_RANK, 1),
                Collections.<FriendCard>emptyList());
        assertNull(CycleRank.from(state));
    }

    @Test
    public void notReady_hasNoRank() {
        assertNull(CycleRank.from(RankingState.signedOut()));
        assertNull(CycleRank.from(RankingState.loading()));
        assertNull(CycleRank.from(RankingState.error(new RuntimeException("x"))));
        assertNull(CycleRank.from(null));
    }

    @Test
    public void firstPlace_isFirst() {
        RankingState state = RankingState.ready(ranking(1, 2), Collections.singletonList(BOB));
        SocialRank rank = CycleRank.from(state);
        assertNotNull(rank);
        assertEquals(true, rank.isFirst());
    }
}
