package com.example.bbettercalendar.social.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.firebase.firestore.FieldValue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Los mapas de {@link ProfileWrites} tienen exactamente las claves de la lista blanca de
 * {@code firebase/firestore.rules} (F1-u1); si cambian unas, tienen que cambiar las otras.
 */
public class ProfileWritesTest {

    private static final FieldValue SERVER_TS = FieldValue.serverTimestamp();

    private static Set<String> keys(String... k) {
        return new HashSet<>(Arrays.asList(k));
    }

    @Test
    public void userCreateHasExactlyTheUsersWhitelistWithServerTimestamps() {
        Map<String, Object> m = ProfileWrites.userCreate("18+", 1);
        assertEquals(keys("createdAt", "ageBand", "termsVersion", "termsAcceptedAt"), m.keySet());
        assertEquals(SERVER_TS, m.get("createdAt"));
        assertEquals(SERVER_TS, m.get("termsAcceptedAt"));
        assertEquals("18+", m.get("ageBand"));
        assertEquals(1, m.get("termsVersion"));
    }

    @Test
    public void termsAcceptTouchesOnlyVersionAndServerTimestamp() {
        Map<String, Object> m = ProfileWrites.termsAccept(2);
        assertEquals(keys("termsVersion", "termsAcceptedAt"), m.keySet());
        assertEquals(2, m.get("termsVersion"));
        assertEquals(SERVER_TS, m.get("termsAcceptedAt"));
    }

    @Test
    public void profileCreateHasTheWhitelistWithoutHandleChangedAt() {
        Map<String, Object> m = ProfileWrites.profileCreate("Alice", "alice", 3);
        assertEquals(keys("displayName", "handle", "avatarColor", "createdAt"), m.keySet());
        assertFalse("D-022: el perfil nace sin handleChangedAt", m.containsKey("handleChangedAt"));
        assertEquals(SERVER_TS, m.get("createdAt"));
        assertEquals("Alice", m.get("displayName"));
        assertEquals("alice", m.get("handle"));
        assertEquals(3, m.get("avatarColor"));
    }

    @Test
    public void profileRenameWritesHandleAndServerTimestamp() {
        Map<String, Object> m = ProfileWrites.profileRename("alice2");
        assertEquals(keys("handle", "handleChangedAt"), m.keySet());
        assertEquals("alice2", m.get("handle"));
        assertEquals(SERVER_TS, m.get("handleChangedAt"));
    }

    @Test
    public void profileUpdateTouchesOnlyNameAndColor() {
        Map<String, Object> m = ProfileWrites.profileUpdate("Alicia", 5);
        assertEquals(keys("displayName", "avatarColor"), m.keySet());
        assertEquals("Alicia", m.get("displayName"));
        assertEquals(5, m.get("avatarColor"));
    }

    @Test
    public void handleCreateIsTheMinimalPublicCard() {
        Map<String, Object> m = ProfileWrites.handleCreate("uid-1", "Alice", 3);
        assertEquals(keys("uid", "displayName", "avatarColor"), m.keySet());
        assertEquals("uid-1", m.get("uid"));
        assertEquals("Alice", m.get("displayName"));
        assertEquals(3, m.get("avatarColor"));
    }

    @Test
    public void handleUpdateTouchesOnlyTheDenormalisedFields() {
        Map<String, Object> m = ProfileWrites.handleUpdate("Alicia", 5);
        assertEquals(keys("displayName", "avatarColor"), m.keySet());
        assertEquals("Alicia", m.get("displayName"));
        assertEquals(5, m.get("avatarColor"));
    }

    @Test
    public void collectionNamesMatchTheRules() {
        assertEquals("users", ProfileWrites.USERS);
        assertEquals("profiles", ProfileWrites.PROFILES);
        assertEquals("handles", ProfileWrites.HANDLES);
    }
}
