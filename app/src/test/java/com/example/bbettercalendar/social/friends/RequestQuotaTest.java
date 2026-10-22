package com.example.bbettercalendar.social.friends;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** F2-u5: {@link RequestQuota} repite en el cliente lo que exige {@code validRequestCounter} (D-028 §2). */
public class RequestQuotaTest {

    private static final long DAY = RequestQuota.DAY_MS;
    /** Día 20000 desde epoch (2024-10-04), a las 23:59:59 UTC. */
    private static final long NOW = 20_000L * DAY + DAY - 1000;

    @Test
    public void sameDay_addsOne() {
        RequestQuota.Next n = RequestQuota.next(20_000L, 4L, NOW);
        assertTrue(n.allowed);
        assertEquals(20_000L, n.day);
        assertEquals(5L, n.count);
    }

    @Test
    public void newDay_resetsToOne() {
        RequestQuota.Next n = RequestQuota.next(19_999L, 20L, NOW); // ayer llegó al tope
        assertTrue(n.allowed);
        assertEquals(20_000L, n.day);
        assertEquals(1L, n.count);
        // Nunca enviado: tampoco hay contador.
        RequestQuota.Next first = RequestQuota.next(null, null, NOW);
        assertTrue(first.allowed);
        assertEquals(1L, first.count);
        // Justo a medianoche UTC cambia el día.
        assertEquals(20_001L, RequestQuota.utcDay(NOW + 1000));
    }

    @Test
    public void twentiethOfTheDay_isAllowed() {
        RequestQuota.Next n = RequestQuota.next(20_000L, 19L, NOW);
        assertTrue(n.allowed);
        assertEquals(20L, n.count);
        assertEquals(RequestQuota.DAILY_LIMIT, n.count);
    }

    @Test
    public void twentyFirstOfTheDay_isRefused() {
        RequestQuota.Next n = RequestQuota.next(20_000L, 20L, NOW);
        assertFalse(n.allowed);
        assertEquals(20L, n.count);
    }
}
