package com.example.bbettercalendar.calendarEntries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.popups.RepetitionOptions;

import org.junit.Test;

import java.util.Calendar;

/**
 * rediseno-proyectos (DB v16): los cinco campos nuevos de tarea (prioridad, etiqueta, sección,
 * bloqueo al enfocar y nota al completar) entran por {@link CalendarEntry.EventBuilder} y ninguna
 * copia de una fila los pierde: build(), la ocurrencia del materializador,
 * {@link CalendarEntry.EventBuilder#copyTaskFieldsFrom(CalendarEntry)} y {@link CompletionStamp}.
 */
public class EventBuilderV16Test {

    private static final long ANCHOR = 1_763_996_400_000L; // 2025-11-24 15:00 UTC
    private static final long NEXT_WEEK = ANCHOR + 7L * 24 * 60 * 60 * 1000;

    private static CalendarEntry taskWithV16Fields() {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(ANCHOR);
        return new CalendarEntry.EventBuilder()
                .setEventType(AddEventActivity.TYPE_TASK)
                .setEventTitle("Escribir el capítulo 3")
                .setEventStartDayAndHour(start)
                .setPriority(CalendarEntry.PRIORITY_HIGH)
                .setTagId(7)
                .setSectionId(11)
                .setBlockOnFocus(true)
                .setCompletionNote("Con la tutora")
                .build();
    }

    @Test
    public void buildCopiesTheFiveNewFields() {
        CalendarEntry entry = taskWithV16Fields();
        assertEquals(3, entry.getPriority());
        assertEquals(7, entry.getTagId());
        assertEquals(11, entry.getSectionId());
        assertTrue(entry.isBlockOnFocus());
        assertEquals("Con la tutora", entry.getCompletionNote());
    }

    @Test
    public void aTaskBuiltWithoutThemHasNoPriorityTagSectionBlockOrNote() {
        CalendarEntry entry = new CalendarEntry.EventBuilder()
                .setEventType(AddEventActivity.TYPE_TASK)
                .setEventTitle("Suelta")
                .build();
        assertEquals(0, entry.getPriority());
        assertEquals(0, entry.getTagId());
        assertEquals(0, entry.getSectionId());
        assertFalse(entry.isBlockOnFocus());
        assertNull(entry.getCompletionNote());
    }

    @Test
    public void anOccurrenceOfARepeatingTaskKeepsTheFiveFields() {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(ANCHOR);
        CalendarEntry template = new CalendarEntry.EventBuilder()
                .setEventType(AddEventActivity.TYPE_TASK)
                .setEventTitle("Ensayar la defensa")
                .setEventStartDayAndHour(start)
                .setEventRepetition(RepetitionOptions.WEEKLY)
                .setEventIsTemplate(true)
                .setPriority(CalendarEntry.PRIORITY_MEDIUM)
                .setTagId(4)
                .setSectionId(9)
                .setBlockOnFocus(true)
                .setCompletionNote("nota de la serie")
                .build();
        template.setId(42);

        CalendarEntry occurrence = RecurrenceMaterializer.buildOccurrence(template, NEXT_WEEK);

        assertEquals(2, occurrence.getPriority());
        assertEquals(4, occurrence.getTagId());
        assertEquals(9, occurrence.getSectionId());
        assertTrue(occurrence.isBlockOnFocus());
        assertEquals("nota de la serie", occurrence.getCompletionNote());
        // y sigue siendo una ocurrencia normal de esa serie
        assertEquals(42, occurrence.getTemplateId());
        assertEquals(NEXT_WEEK, occurrence.getStartMillis());
        assertFalse(occurrence.isTemplate());
        assertFalse(occurrence.isDone());
    }

    @Test
    public void rebuildingAnEditedTaskWithTheHelperKeepsTheFiveFields() {
        CalendarEntry stored = taskWithV16Fields();

        // Un guardado que reescribe la tarea con otro título y otra meta (lo que el formulario enseña)
        CalendarEntry saved = new CalendarEntry.EventBuilder()
                .setEventType(AddEventActivity.TYPE_TASK)
                .setEventTitle("Escribir el capítulo 3 (v2)")
                .setEventTargetMinutes(90)
                .copyTaskFieldsFrom(stored)
                .build();

        assertEquals("Escribir el capítulo 3 (v2)", saved.getTitle());
        assertEquals(90, saved.getTargetMinutes());
        assertEquals(3, saved.getPriority());
        assertEquals(7, saved.getTagId());
        assertEquals(11, saved.getSectionId());
        assertTrue(saved.isBlockOnFocus());
        assertEquals("Con la tutora", saved.getCompletionNote());
    }

    @Test
    public void markingDoneAndUndoingKeepsTheFiveFields() {
        CalendarEntry entry = taskWithV16Fields();

        CompletionStamp.apply(entry, true, NEXT_WEEK);
        CompletionStamp.apply(entry, false, NEXT_WEEK + 1_000L);

        assertEquals(3, entry.getPriority());
        assertEquals(7, entry.getTagId());
        assertEquals(11, entry.getSectionId());
        assertTrue(entry.isBlockOnFocus());
        assertEquals("Con la tutora", entry.getCompletionNote());
    }
}
