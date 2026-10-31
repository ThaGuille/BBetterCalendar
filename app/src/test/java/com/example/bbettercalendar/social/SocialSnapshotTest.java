package com.example.bbettercalendar.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.social.presence.Presence;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * F4-u4: {@link SocialSnapshot} y {@link Friend} con la semántica de quien mira (02 §7, D-033): quien estudia
 * primero (la pausa cuenta como estudiando), entre ellos la sesión más larga primero, después el nombre; la
 * corona es la {@code crownUid} del ranking, esté como esté ese amigo.
 */
public class SocialSnapshotTest {

    private static final long MIN = 60_000L;
    /** Hora del servidor y del móvil de los casos (sin desfase salvo donde se diga). */
    private static final long NOW = 1_800_000_000_000L;

    private static Friend studying(String uid, String name, long startedMinAgo) {
        return new Friend(uid, uid, name, 0, Presence.studying(NOW - startedMinAgo * MIN, NOW), 0L, 0L);
    }

    private static Friend paused(String uid, String name, long startedMinAgo, long pausedMinAgo) {
        return new Friend(uid, uid, name, 0,
                Presence.paused(NOW - startedMinAgo * MIN, NOW - pausedMinAgo * MIN, NOW), 0L, 0L);
    }

    private static Friend away(String uid, String name) {
        return new Friend(uid, uid, name, 0, Presence.AWAY, 0L, 0L);
    }

    private static List<String> names(List<Friend> friends) {
        List<String> out = new ArrayList<>();
        for (Friend f : friends) {
            out.add(f.getName());
        }
        return out;
    }

    @Test
    public void studyingFirst_thenAwayByName() {
        SocialSnapshot s = new SocialSnapshot(Arrays.asList(
                away("z", "Zoe"), studying("b", "Bob", 10), away("a", "anna"), away("c", "Carol")), null, null);

        assertEquals(Arrays.asList("Bob", "anna", "Carol", "Zoe"), names(s.getFriends()));
        assertEquals(1, s.getStudyingCount());
        assertEquals(4, s.getFriendCount());
    }

    @Test
    public void amongStudying_theLongestSessionComesFirst() {
        SocialSnapshot s = new SocialSnapshot(Arrays.asList(
                studying("a", "Anna", 5), studying("b", "Bob", 90), studying("c", "Carol", 30)), null, null);

        assertEquals(Arrays.asList("Bob", "Carol", "Anna"), names(s.getStudying()));
    }

    @Test
    public void sameSessionStart_fallsBackToName() {
        SocialSnapshot s = new SocialSnapshot(Arrays.asList(
                studying("m", "Marc", 20), studying("l", "Laia", 20)), null, null);

        assertEquals(Arrays.asList("Laia", "Marc"), names(s.getFriends()));
    }

    @Test
    public void pausedFriends_stayInTheStudyingGroupButAfterTheActiveOnes() {
        SocialSnapshot s = new SocialSnapshot(Arrays.asList(
                away("a", "Anna"), studying("b", "Bob", 15), paused("c", "Carol", 60, 5),
                studying("d", "Dani", 100)), null, null);

        assertEquals(Arrays.asList("Dani", "Bob", "Carol", "Anna"), names(s.getFriends()));
        assertEquals(3, s.getStudyingCount());
        assertEquals(Arrays.asList("Dani", "Bob", "Carol"), names(s.getStudying()));
    }

    @Test
    public void pausedFriend_isStudyingWithAFrozenTime() {
        Friend carol = paused("c", "Carol", 60, 5);

        assertTrue(carol.isStudying());
        assertTrue(carol.isPaused());
        assertEquals(Friend.Status.STUDYING, carol.getStatus());
        assertEquals(55 * MIN, carol.getFrozenElapsedMs());
        assertEquals(55 * MIN, carol.getElapsedMs(NOW));
        assertEquals(55 * MIN, carol.getElapsedMs(NOW + 30 * MIN)); // no corre mientras está en pausa
    }

    @Test
    public void crownIsOnTheCrownUid_evenWhenThatFriendIsAway() {
        Friend pau = away("pau", "Pau");
        SocialSnapshot s = new SocialSnapshot(Arrays.asList(studying("b", "Bob", 300), pau), null, "pau");

        assertEquals("pau", s.getCrownUid());
        assertSame(pau, s.getTopFriend()); // no es «el que más tiempo lleva»: es la corona del ranking
    }

    @Test
    public void noCrown_whenThereIsNoCrownUidOrItIsNotAFriend() {
        List<Friend> friends = Arrays.asList(studying("b", "Bob", 300));

        assertNull(new SocialSnapshot(friends, null, null).getTopFriend());
        assertNull(new SocialSnapshot(friends, null, "gone").getTopFriend());
    }

    @Test
    public void friend_convertsTheServerStartToTheDeviceClock() {
        // El servidor va 5 s por delante de este móvil: el inicio en el reloj del móvil es 5 s antes.
        long offset = 5_000L;
        long serverStart = NOW - 10 * MIN;
        Friend bob = new Friend("b", "bob", "Bob Ros", 3, Presence.studying(serverStart, NOW), offset, 0L);

        assertEquals(serverStart - offset, bob.getSessionStartMs());
        long deviceNow = NOW - offset; // la misma hora que NOW en el servidor
        assertEquals(10 * MIN, bob.getElapsedMs(deviceNow));
        assertFalse(bob.isPaused());
        assertEquals("BR", bob.getInitials());
        assertEquals("bob", bob.getHandle());
        assertEquals(3, bob.getAvatarColor());
        assertEquals("b", bob.getUid());
        assertEquals("b", bob.getId());
    }

    @Test
    public void awayFriend_hasNoTime() {
        Friend anna = away("a", "Anna");

        assertFalse(anna.isStudying());
        assertFalse(anna.isPaused());
        assertEquals(Friend.Status.AWAY, anna.getStatus());
        assertEquals(0L, anna.getElapsedMs(NOW));
        assertEquals(0L, anna.getSessionStartMs());
    }

    @Test
    public void empty_hasNothing() {
        assertTrue(SocialSnapshot.EMPTY.isEmpty());
        assertEquals(0, SocialSnapshot.EMPTY.getStudyingCount());
        assertNull(SocialSnapshot.EMPTY.getTopFriend());
        assertNull(SocialSnapshot.EMPTY.getCrownUid());
        assertNull(SocialSnapshot.EMPTY.getUserRank());
    }

    @Test
    public void initials_takeUpToTwoWords() {
        assertEquals("LM", Friend.initialsOf("Laia Martí"));
        assertEquals("P", Friend.initialsOf("pau"));
        assertEquals("JB", Friend.initialsOf("  Júlia  Bosch Puig "));
        assertEquals("", Friend.initialsOf(""));
    }
}
