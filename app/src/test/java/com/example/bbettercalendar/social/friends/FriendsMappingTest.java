package com.example.bbettercalendar.social.friends;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F2-u5: de documentos ({@link FriendsBackend.Doc}) a modelos con {@link FriendsMapping}, y el orden en el
 * cliente (amigos alfabéticos; solicitudes, la más nueva primero). Los números llegan de Firestore como
 * {@code Long}.
 */
public class FriendsMappingTest {

    private static FriendsBackend.Doc doc(String id, Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return new FriendsBackend.Doc(id, m, false);
    }

    private static FriendsBackend.Doc request(String from, String to, String status, Long created) {
        return doc(from + "_" + to, "from", from, "to", to, "status", status,
                "createdAt", created == null ? null : new Date(created),
                "fromHandle", "h_" + from, "fromName", "N " + from, "fromColor", 1L,
                "toHandle", "h_" + to, "toName", "N " + to, "toColor", 2L);
    }

    @Test
    public void profileAndHandleDocs_mapToCards_malformedOrMissingAreNull() {
        assertEquals(new FriendCard("uidB", "bob", "Bob", 5),
                FriendsMapping.profileCard(doc("uidB", "displayName", "Bob", "handle", "bob", "avatarColor", 5L,
                        "createdAt", new Date(0))));
        assertEquals(new FriendCard("uidB", "bob", "Bob", 5),
                FriendsMapping.handleCard(doc("bob", "uid", "uidB", "displayName", "Bob", "avatarColor", 5L)));
        assertNull(FriendsMapping.profileCard(doc("uidB", "displayName", "Bob", "avatarColor", 5L)));
        assertNull(FriendsMapping.profileCard(new FriendsBackend.Doc("uidB", null, false)));
        assertNull(FriendsMapping.handleCard(doc("bob", "displayName", "Bob", "avatarColor", 5L)));
        assertNull(FriendsMapping.profileCard(doc("uidB", "displayName", "Bob", "handle", "bob",
                "avatarColor", "5")));
    }

    @Test
    public void requestDoc_mapsCardsStatusReasonAndDate() {
        FriendRequest r = FriendsMapping.request(request("uidA", "me", "pending", 1000L));
        assertEquals("uidA_me", r.id);
        assertEquals(new FriendCard("uidA", "h_uidA", "N uidA", 1), r.from);
        assertEquals(new FriendCard("me", "h_me", "N me", 2), r.to);
        assertEquals(FriendRequest.Status.PENDING, r.status);
        assertEquals(new Date(1000L), r.createdAt);
        assertEquals(r.from, r.other("me"));
        assertNull(r.failReason);

        FriendsBackend.Doc failed = request("uidA", "me", "failed", 1000L);
        failed.data.put("failReason", "cap");
        FriendRequest f = FriendsMapping.request(failed);
        assertEquals(FriendRequest.Status.FAILED, f.status);
        assertEquals(FriendRequest.FAIL_CAP, f.failReason);

        assertEquals(FriendRequest.Status.UNKNOWN,
                FriendsMapping.request(request("uidA", "me", "declined", 1L)).status);
        FriendsBackend.Doc noCard = request("uidA", "me", "pending", 1L);
        noCard.data.remove("toHandle");
        assertNull(FriendsMapping.request(noCard));
    }

    @Test
    public void friends_areSortedAlphabetically_ignoringCaseAndAccents() {
        List<FriendCard> sorted = FriendsMapping.sortFriends(Arrays.asList(
                new FriendCard("u1", "zoe", "zoe", 0),
                new FriendCard("u2", "alvaro", "Álvaro", 0),
                new FriendCard("u3", "bea", "Bea", 0),
                new FriendCard("u4", "ana2", "ana", 0),
                new FriendCard("u5", "ana1", "Ana", 0)));
        List<String> handles = new ArrayList<>();
        for (FriendCard c : sorted) {
            handles.add(c.handle);
        }
        // «Álvaro» va con la a (antes que «Ana»: l < n); «Ana» y «ana» empatan y decide el @usuario.
        assertEquals(Arrays.asList("alvaro", "ana1", "ana2", "bea", "zoe"), handles);
    }

    @Test
    public void incomingAndOutgoing_filterByStatusAndSide_newestFirst() {
        List<FriendsBackend.Doc> in = Arrays.asList(
                request("uidA", "me", "pending", 1000L),
                request("uidB", "me", "failed", 3000L),
                request("uidC", "me", "accepted", 4000L), // transitorio: la Function la borra
                request("uidD", "me", "pending", null),   // hora del servidor aún pendiente: la más nueva
                request("uidE", "me", "pending", 2000L));
        List<FriendRequest> incoming = FriendsMapping.incoming(in, "me");
        List<String> ids = new ArrayList<>();
        for (FriendRequest r : incoming) {
            ids.add(r.id);
        }
        assertEquals(Arrays.asList("uidD_me", "uidB_me", "uidE_me", "uidA_me"), ids);
        assertEquals(3, FriendsMapping.pendingCount(incoming));

        List<FriendsBackend.Doc> out = Arrays.asList(
                request("me", "uidA", "pending", 1000L),
                request("me", "uidB", "failed", 3000L),
                request("me", "uidC", "pending", 2000L));
        List<FriendRequest> outgoing = FriendsMapping.outgoing(out, "me");
        assertEquals(2, outgoing.size());
        assertEquals("me_uidC", outgoing.get(0).id);
        assertEquals("me_uidA", outgoing.get(1).id);

        assertEquals(Arrays.asList("a", "b", "c"), FriendsMapping.friendIds(Arrays.asList(
                doc("c"), doc("a"), doc("b"), doc("a"))));
    }
}
