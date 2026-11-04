package com.example.bbettercalendar.social.push;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** L-u7: qué notificaciones activas son de Amigos (ids en [40_000, 49_999]). */
public class FriendsNotificationsTest {

    @Test
    public void friendsIdsAreCancelled() {
        assertEquals(Arrays.asList(40_000, 45_123, 49_999),
                FriendsNotifications.idsToCancel(new int[]{40_000, 45_123, 49_999}));
    }

    @Test
    public void otherIdsAreKept() {
        assertEquals(Arrays.asList(40_000, 49_999),
                FriendsNotifications.idsToCancel(new int[]{1, 39_999, 40_000, 49_999, 50_000, 50_001, 60_000,
                        100_000}));
        assertFalse(FriendsNotifications.isFriendsId(39_999));
        assertFalse(FriendsNotifications.isFriendsId(50_000));
        assertTrue(FriendsNotifications.isFriendsId(40_000));
    }

    @Test
    public void noneActiveCancelsNothing() {
        assertEquals(Collections.emptyList(), FriendsNotifications.idsToCancel(new int[0]));
        assertEquals(Collections.emptyList(), FriendsNotifications.idsToCancel(new int[]{1, 50_001}));
    }
}
