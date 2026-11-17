package com.example.bbettercalendar.notifications.event;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Qué decir en el cuerpo de un recordatorio según la hora real de inicio y el momento en que
 * llega (plan sistema-notificaciones A2). Antes se mostraba la etiqueta del offset («Empieza
 * 5 minutos antes»), que además mentía tras «Posponer» o con una alarma retrasada por Doze.
 * Java puro: lo prueba {@code ReminderTimingTest}.
 */
public final class ReminderTiming {

    public enum Kind { NOW, IN_MINUTES, TODAY_AT, TOMORROW_AT, ON_DATE }

    public final Kind kind;
    /** Minutos que faltan (sólo {@link Kind#IN_MINUTES}, 1..59). */
    public final int minutes;

    private ReminderTiming(Kind kind, int minutes) {
        this.kind = kind;
        this.minutes = minutes;
    }

    public static ReminderTiming of(long startMillis, long nowMillis, TimeZone zone) {
        long diff = startMillis - nowMillis;
        if (diff <= 30_000L) {
            return new ReminderTiming(Kind.NOW, 0);
        }
        long minutes = (diff + 59_999L) / 60_000L; // redondeo hacia arriba: 4 min 10 s -> «5 min»
        if (minutes < 60) {
            return new ReminderTiming(Kind.IN_MINUTES, (int) minutes);
        }
        int dayDelta = dayDelta(startMillis, nowMillis, zone);
        if (dayDelta == 0) return new ReminderTiming(Kind.TODAY_AT, 0);
        if (dayDelta == 1) return new ReminderTiming(Kind.TOMORROW_AT, 0);
        return new ReminderTiming(Kind.ON_DATE, 0);
    }

    private static int dayDelta(long a, long b, TimeZone zone) {
        Calendar ca = Calendar.getInstance(zone);
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance(zone);
        cb.setTimeInMillis(b);
        int delta = 0;
        // Pocas iteraciones (los offsets llegan a 1 día): contar días de calendario, no 24 h.
        while (cb.get(Calendar.YEAR) != ca.get(Calendar.YEAR)
                || cb.get(Calendar.DAY_OF_YEAR) != ca.get(Calendar.DAY_OF_YEAR)) {
            cb.add(Calendar.DAY_OF_YEAR, 1);
            delta++;
            if (delta > 2) return delta;
        }
        return delta;
    }
}
