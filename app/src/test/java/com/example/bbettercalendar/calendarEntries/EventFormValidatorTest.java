package com.example.bbettercalendar.calendarEntries;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** mejora-visual u14: reglas de "Guardar" del formulario de evento / tarea. */
public class EventFormValidatorTest {

    private static final long START = 1_762_800_000_000L;
    private static final long HOUR = 3_600_000L;

    @Test
    public void nullAndBlankTitlesAreMissing() {
        assertFalse(EventFormValidator.hasTitle(null));
        assertFalse(EventFormValidator.hasTitle(""));
        assertFalse(EventFormValidator.hasTitle("   "));
    }

    @Test
    public void realTitleCounts() {
        assertTrue(EventFormValidator.hasTitle("Dentist"));
        assertTrue(EventFormValidator.hasTitle("  x "));
    }

    @Test
    public void endBeforeStartIsAnError() {
        assertTrue(EventFormValidator.isEndBeforeStart(START, START - 1));
        EventFormValidator.Result r = EventFormValidator.validate("Cita", true, START, START - HOUR);
        assertTrue(r.endBeforeStart);
        assertFalse(r.canSave());
    }

    @Test
    public void endEqualToStartOrLaterIsValid() {
        assertFalse(EventFormValidator.isEndBeforeStart(START, START));
        assertTrue(EventFormValidator.validate("Cita", true, START, START).canSave());
        assertTrue(EventFormValidator.validate("Cita", true, START, START + HOUR).canSave());
    }

    @Test
    public void taskIgnoresTheEnd() {
        EventFormValidator.Result r = EventFormValidator.validate("Comprar", false, START, 0L);
        assertFalse(r.endBeforeStart);
        assertTrue(r.canSave());
    }

    @Test
    public void missingTitleBlocksSaveEvenWithValidDates() {
        EventFormValidator.Result r = EventFormValidator.validate(" ", true, START, START + HOUR);
        assertTrue(r.titleMissing);
        assertFalse(r.endBeforeStart);
        assertFalse(r.canSave());
    }
}
