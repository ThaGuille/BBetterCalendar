package com.example.bbettercalendar.projects;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;

/**
 * Alarma del aviso de empezar de una tarea. {@link TaskAlarms} la llama al guardar, cerrar o
 * archivar; el binding por defecto de {@link ProjectsModule} no hace nada y la parte del aviso de
 * empezar lo sustituye. Siempre se llama desde el executor de escritura.
 */
public interface TaskStartAlarm {
    void schedule(CalendarEntry entry);

    void cancel(CalendarEntry entry);
}
