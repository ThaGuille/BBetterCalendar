package com.example.bbettercalendar.social.invite;

import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.FieldValue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

/**
 * F2-u6: las claves exactas que aceptan las reglas de F2-u3 en {@code invites/{code}} (create y update) y en
 * {@code users/{uid}.inviteCode}.
 */
public class InviteWritesTest {

    @Test
    public void inviteCreateHasUidHandleAndServerCreatedAt() {
        Map<String, Object> m = InviteWrites.inviteCreate("u1", "alice");
        assertEquals(new HashSet<>(Arrays.asList("uid", "handle", "createdAt")), m.keySet());
        assertEquals("u1", m.get("uid"));
        assertEquals("alice", m.get("handle"));
        assertEquals(FieldValue.serverTimestamp(), m.get("createdAt"));
        assertEquals("invites/ABCD2345", InviteWrites.invitePath("ABCD2345"));
    }

    @Test
    public void userCodeAndRenameTouchOneKeyEach() {
        assertEquals(Collections.singletonMap("inviteCode", "ABCD2345"), InviteWrites.userInviteCode("ABCD2345"));
        assertEquals(Collections.singletonMap("handle", "alice2"), InviteWrites.inviteHandleUpdate("alice2"));
    }
}
