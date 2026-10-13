package com.example.bbettercalendar.usage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

// Reparto puro (sin Android) de un intervalo de uso en las 24 franjas horarias de un dia.
// Un intervalo que cruza el limite de una hora se parte entre las dos horas, y lo que cae fuera
// de [dayStart, dayEnd) se ignora. La franja es la hora LOCAL del reloj (0..23): en un dia con
// cambio de hora (23 o 25 horas) las horas repetidas se suman en el mismo indice.
public final class UsageHourBuckets {

    public static final int HOURS = 24;

    private UsageHourBuckets() { }

    // Suma en buckets[hora] los ms de [start, stop) recortados a [dayStart, dayEnd).
    public static void addInterval(long[] buckets, long start, long stop,
                                   long dayStart, long dayEnd, ZoneId zone) {
        long cursor = Math.max(start, dayStart);
        long limit = Math.min(stop, dayEnd);
        while (cursor < limit) {
            ZonedDateTime at = Instant.ofEpochMilli(cursor).atZone(zone);
            int hour = at.getHour();
            long nextHour = at.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli();
            // Guarda contra zonas con saltos raros: siempre se avanza.
            long next = Math.min(Math.max(nextHour, cursor + 1), limit);
            buckets[hour] += next - cursor;
            cursor = next;
        }
    }
}
