package com.example.bbettercalendar.social.invite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** F2-u6: una invitación pendiente vale 7 días desde que se guardó. */
public class PendingInviteTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    public void freshForSevenDays() {
        assertTrue(PendingInvite.isFresh(NOW, NOW));
        assertTrue(PendingInvite.isFresh(NOW - 6 * DAY, NOW));
        assertTrue(PendingInvite.isFresh(NOW - 7 * DAY + 1, NOW));
        assertFalse(PendingInvite.isFresh(NOW - 7 * DAY, NOW));
        assertFalse(PendingInvite.isFresh(NOW - 30 * DAY, NOW));
    }

    @Test
    public void missingOrFutureDateIsNotFresh() {
        assertFalse(PendingInvite.isFresh(0L, NOW));
        assertFalse(PendingInvite.isFresh(NOW + DAY, NOW));
    }
}
