package com.example.bbettercalendar.social.friends;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.FieldValue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * F2-u5: los mapas de {@link FriendRequestWrites} tienen exactamente las claves que aceptan las reglas de
 * F2-u3 ({@code validRequestFields}, {@code validRequestCounter} y el update de {@code users/},
 * {@code validRequestAccept}). Si una regla cambia, este test y {@code firestore.rules} cambian juntos.
 */
public class FriendRequestWritesTest {

    private static final FriendCard ALICE = new FriendCard("uidA", "alice", "Alice", 3);
    private static final FriendCard BOB = new FriendCard("uidB", "bob", "Bob", 5);

    private static Set<String> keys(String... k) {
        return new HashSet<>(Arrays.asList(k));
    }

    @Test
    public void request_hasExactlyTheTenRuleKeys_andTheCards() {
        Map<String, Object> m = FriendRequestWrites.request(ALICE, BOB);
        assertEquals(keys("from", "to", "status", "createdAt", "fromHandle", "fromName", "fromColor",
                "toHandle", "toName", "toColor"), m.keySet());
        assertEquals("uidA", m.get("from"));
        assertEquals("uidB", m.get("to"));
        assertEquals("pending", m.get("status"));
        assertTrue(m.get("createdAt") instanceof FieldValue); // serverTimestamp() == request.time
        assertEquals("alice", m.get("fromHandle"));
        assertEquals("Alice", m.get("fromName"));
        assertEquals(3, m.get("fromColor"));
        assertEquals("bob", m.get("toHandle"));
        assertEquals("Bob", m.get("toName"));
        assertEquals(5, m.get("toColor"));
    }

    @Test
    public void counter_hasExactlyReqDayReqCountReqLastTo() {
        RequestQuota.Next next = RequestQuota.next(null, null, 3 * RequestQuota.DAY_MS + 1000);
        Map<String, Object> m = FriendRequestWrites.counter(next, "uidB");
        assertEquals(keys("reqDay", "reqCount", "reqLastTo"), m.keySet());
        assertEquals(3L, m.get("reqDay"));
        assertEquals(1L, m.get("reqCount"));
        assertEquals("uidB", m.get("reqLastTo")); // reqLastTo == to de la solicitud del mismo lote
    }

    @Test
    public void accept_changesOnlyStatus() {
        Map<String, Object> m = FriendRequestWrites.accept();
        assertEquals(keys("status"), m.keySet());
        assertEquals("accepted", m.get("status"));
    }

    @Test
    public void idAndPaths_areDeterministic() {
        assertEquals("uidA_uidB", FriendRequestWrites.requestId("uidA", "uidB"));
        assertEquals("friendRequests/uidA_uidB", FriendRequestWrites.requestPath("uidA_uidB"));
        assertEquals("users/uidA", FriendRequestWrites.userPath("uidA"));
        assertEquals("users/uidA/friends/uidB", FriendRequestWrites.friendPath("uidA", "uidB"));
        assertEquals("handles/bob", FriendRequestWrites.handlePath("bob"));
        assertEquals("profiles/uidA", FriendRequestWrites.profilePath("uidA"));
    }
}
