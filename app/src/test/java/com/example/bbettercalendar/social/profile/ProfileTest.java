package com.example.bbettercalendar.social.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/** {@link Profile#canRenameAt}: el primer renombrado es libre (D-022) y después, cada 30 días (D-019). */
public class ProfileTest {

    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final Date NOW = new Date(1_790_000_000_000L);

    private static Profile withChangedAt(Date handleChangedAt) {
        return new Profile("uid", "Alice", "alice", 3, new Date(0), handleChangedAt);
    }

    @Test
    public void neverRenamedCanRenameNow() {
        Profile p = withChangedAt(null);
        assertEquals(NOW, p.canRenameAt(NOW));
        assertTrue(p.canRenameNow(NOW));
    }

    @Test
    public void renamedRecentlyWaitsThirtyDays() {
        Date changed = new Date(NOW.getTime() - 10 * DAY);
        Profile p = withChangedAt(changed);
        assertEquals(new Date(changed.getTime() + 30 * DAY), p.canRenameAt(NOW));
        assertFalse(p.canRenameNow(NOW));
    }

    @Test
    public void exactlyThirtyDaysLaterIsAllowed() {
        Profile p = withChangedAt(new Date(NOW.getTime() - 30 * DAY));
        assertTrue(p.canRenameNow(NOW));
    }

    @Test
    public void renamedLongAgoCanRenameNow() {
        Profile p = withChangedAt(new Date(NOW.getTime() - 45 * DAY));
        assertTrue(p.canRenameNow(NOW));
    }
}
