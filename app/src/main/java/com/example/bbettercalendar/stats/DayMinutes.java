package com.example.bbettercalendar.stats;

// Proyección de FocusEventDAO.getFocusMinutesPerDayBetween() (F3-u4): minutos de sesiones completadas
// (TYPE_FOCUS) de un día civil local. POJO simple, no una entidad: no cambia el esquema de Room (regla #6).
public class DayMinutes {
    /** Fecha civil local "YYYY-MM-DD" (date(..., 'localtime') de SQLite). */
    public String day;
    /** SUM(durationMin) de las sesiones completadas de ese día, sin tope. */
    public int minutes;
}
