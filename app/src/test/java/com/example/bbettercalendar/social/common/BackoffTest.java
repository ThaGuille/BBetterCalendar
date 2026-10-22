package com.example.bbettercalendar.social.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BackoffTest {

    @Test
    public void delaysDoubleFromOneSecondAndStopAfterSixAttempts() {
        Backoff b = new Backoff();
        List<Long> delays = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            delays.add(b.nextDelayMs());
        }
        assertEquals(Arrays.asList(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, -1L, -1L), delays);
        assertEquals(Backoff.MAX_ATTEMPTS, b.attempts());
    }

    @Test
    public void delayIsCappedAtSixtySeconds() {
        assertEquals(1_000L, Backoff.delayFor(0));
        assertEquals(32_000L, Backoff.delayFor(5));
        assertEquals(60_000L, Backoff.delayFor(6));
        assertEquals(60_000L, Backoff.delayFor(10));
        assertEquals(60_000L, Backoff.delayFor(63));
        assertEquals(1_000L, Backoff.delayFor(-1));
    }

    @Test
    public void resetStartsAgainFromOneSecond() {
        Backoff b = new Backoff();
        b.nextDelayMs();
        b.nextDelayMs();
        b.nextDelayMs();
        b.reset();
        assertEquals(0, b.attempts());
        assertEquals(1_000L, b.nextDelayMs());
    }

    @Test
    public void fakeClockRunsEachRetryOnlyWhenItsDelayHasPassed() {
        FakeScheduler clock = new FakeScheduler();
        Backoff b = new Backoff();
        List<Long> firedAt = new ArrayList<>();
        // Cada intento programa el siguiente con la espera de Backoff, como hace WatchedListener.
        Runnable[] attempt = new Runnable[1];
        attempt[0] = () -> {
            firedAt.add(clock.now());
            long d = b.nextDelayMs();
            if (d >= 0) {
                clock.schedule(d, attempt[0]);
            }
        };
        clock.schedule(b.nextDelayMs(), attempt[0]);
        clock.advance(999);
        assertTrue(firedAt.isEmpty());
        clock.advance(1);
        assertEquals(Arrays.asList(1_000L), firedAt);
        clock.advance(10 * 60_000L);
        // 1 s, +2, +4, +8, +16, +32 = 6 intentos y ninguno más.
        assertEquals(Arrays.asList(1_000L, 3_000L, 7_000L, 15_000L, 31_000L, 63_000L), firedAt);
        assertTrue(clock.pending().isEmpty());
    }
}
