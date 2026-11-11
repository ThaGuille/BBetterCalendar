package com.example.bbettercalendar.calendarEntries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * mejora-visual u1: {@link CompletionStamp}, el único escritor de {@code completedAtMillis}
 * (marcar = ahora, desmarcar = 0, re-marcar no mueve la fecha).
 */
public class CompletionStampTest {

    private static final long NOW = 1_762_800_000_000L;

    private static CalendarEntry task(boolean done) {
        return new CalendarEntry.EventBuilder()
                .setEventType(AddEventActivity.TYPE_TASK)
                .setEventTitle("t")
                .setEventIsDone(done)
                .build();
    }

    @Test
    public void markingDoneStampsNow() {
        CalendarEntry entry = task(false);
        CompletionStamp.apply(entry, true, NOW);
        assertTrue(entry.isDone());
        assertEquals(NOW, entry.getCompletedAtMillis());
    }

    @Test
    public void unmarkingClearsTheStamp() {
        CalendarEntry entry = task(false);
        CompletionStamp.apply(entry, true, NOW);
        CompletionStamp.apply(entry, false, NOW + 5_000L);
        assertFalse(entry.isDone());
        assertEquals(0L, entry.getCompletedAtMillis());
    }

    @Test
    public void markingAnAlreadyDoneEntryKeepsItsOriginalStamp() {
        CalendarEntry entry = task(false);
        CompletionStamp.apply(entry, true, NOW);
        CompletionStamp.apply(entry, true, NOW + 60_000L);
        assertTrue(entry.isDone());
        assertEquals(NOW, entry.getCompletedAtMillis());
    }

    @Test
    public void aDoneEntryWithoutStampGetsOneWhenMarkedAgain() {
        // Fila hecha sin sello (p. ej. creada hecha con el builder): marcarla le pone la fecha.
        CalendarEntry entry = task(true);
        assertEquals(0L, entry.getCompletedAtMillis());
        CompletionStamp.apply(entry, true, NOW);
        assertEquals(NOW, entry.getCompletedAtMillis());
    }

    @Test
    public void remarkingAfterUnmarkingStampsTheNewInstant() {
        CalendarEntry entry = task(false);
        CompletionStamp.apply(entry, true, NOW);
        CompletionStamp.apply(entry, false, NOW + 1_000L);
        CompletionStamp.apply(entry, true, NOW + 2_000L);
        assertEquals(NOW + 2_000L, entry.getCompletedAtMillis());
    }
}
