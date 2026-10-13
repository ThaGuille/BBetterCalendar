package com.example.bbettercalendar.usage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

// u20: reparto puro (sin Android) de un intervalo de uso en dias locales consecutivos. Un
// intervalo que cruza la medianoche se parte entre los dos dias; lo que cae fuera de
// [firstDay, firstDay + buckets.length) se ignora. Los limites de dia salen de LocalDate, asi
// que un dia con cambio de hora (23 o 25 h) sigue siendo un solo indice.
public final class UsageDayBuckets {

    private UsageDayBuckets() { }

    // Suma en buckets[i] los ms de [start, stop) que caen en el dia firstDay + i.
    public static void addInterval(long[] buckets, long start, long stop,
                                   LocalDate firstDay, ZoneId zone) {
        long rangeStart = firstDay.atStartOfDay(zone).toInstant().toEpochMilli();
        long rangeEnd = firstDay.plusDays(buckets.length).atStartOfDay(zone).toInstant().toEpochMilli();
        long cursor = Math.max(start, rangeStart);
        long limit = Math.min(stop, rangeEnd);
        while (cursor < limit) {
            ZonedDateTime at = Instant.ofEpochMilli(cursor).atZone(zone);
            int index = (int) ChronoUnit.DAYS.between(firstDay, at.toLocalDate());
            long nextDay = at.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            // Guarda contra zonas con saltos raros: siempre se avanza.
            long next = Math.min(Math.max(nextDay, cursor + 1), limit);
            if (index >= 0 && index < buckets.length) {
                buckets[index] += next - cursor;
            }
            cursor = next;
        }
    }
}
