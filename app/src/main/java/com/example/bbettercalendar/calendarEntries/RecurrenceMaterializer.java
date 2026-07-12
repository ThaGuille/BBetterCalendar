package com.example.bbettercalendar.calendarEntries;

import android.app.AlarmManager;
import android.content.Context;

import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.popups.RepetitionOptions;
import com.example.bbettercalendar.popups.RepetitionSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Materializa las series recurrentes (spec tasks-recurrence): a partir de una fila PLANTILLA
 * ({@code isTemplate = 1}) genera filas de OCURRENCIA reales — cada una con su propio
 * {@code isDone} — desde la fecha ancla hasta un horizonte fijo, y les programa sus recordatorios.
 *
 * Estrategia: ventana deslizante ({@link #HORIZON_DAYS} días), rellenada de forma idempotente
 * mediante la marca de agua {@code materializedUntilMillis} por plantilla. Se llama en cada
 * arranque ({@link #materializeAll()}) y tras crear una plantilla ({@link #materializeTemplate(int)}).
 *
 * TODOS los métodos públicos hacen E/S de BD sincrónica: hay que invocarlos fuera del hilo
 * principal (los call sites ya corren sobre ExecutorService), regla #3.
 */
public class RecurrenceMaterializer {

    public static final int HORIZON_DAYS = 35;
    // Límite de relleno hacia atrás: al adoptar una serie legacy antigua no queremos generar
    // cientos de ocurrencias pasadas. Dos semanas de historial atrasado bastan para la sección
    // colapsada de atrasadas; lo anterior no es accionable.
    private static final int BACKFILL_DAYS = 14;
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private final CalendarEntryDAO dao;
    private final EventReminderScheduler scheduler;

    public RecurrenceMaterializer(Context context, CalendarEntryDAO dao) {
        this.dao = dao;
        Context app = context.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        this.scheduler = new EventReminderScheduler(app, am);
    }

    /** Adopta filas repetitivas legacy y rellena todas las plantillas. Llamar fuera del hilo UI. */
    public void materializeAll() {
        adoptLegacyRepeatingRows();
        List<CalendarEntry> templates = dao.getTemplates();
        if (templates == null) return;
        long horizon = horizonMillis();
        for (CalendarEntry template : templates) {
            materialize(template, horizon);
        }
    }

    /** Rellena una única plantilla recién creada. Llamar fuera del hilo UI. */
    public void materializeTemplate(int templateId) {
        CalendarEntry template = dao.getEventById(templateId);
        if (template == null || !template.isTemplate()) return;
        materialize(template, horizonMillis());
    }

    // Promueve filas con repetition != NONE que aún no son plantillas (modelo antiguo de
    // "avanzar la misma fila") a plantillas del nuevo modelo, para que sigan repitiéndose.
    private void adoptLegacyRepeatingRows() {
        List<CalendarEntry> legacy = dao.getLegacyRepeatingRows();
        if (legacy == null) return;
        for (CalendarEntry row : legacy) {
            row.setTemplate(true);
            if (row.getRepetitionInterval() < 1) {
                row.setRepetitionInterval(1);
            }
            row.setMaterializedUntilMillis(0);
            dao.update(row);
        }
    }

    private void materialize(CalendarEntry template, long horizon) {
        long anchor = template.getStartMillis();
        if (anchor <= 0L || template.getRepetition() == RepetitionOptions.NONE) return;

        long backfillFloor = System.currentTimeMillis() - BACKFILL_DAYS * DAY_MILLIS;
        long afterExclusive = Math.max(template.getMaterializedUntilMillis(), backfillFloor);

        List<Long> starts = occurrenceStarts(template, afterExclusive, horizon);
        for (long startMillis : starts) {
            insertOccurrence(template, startMillis);
        }
        template.setMaterializedUntilMillis(Math.max(template.getMaterializedUntilMillis(), horizon));
        dao.update(template);
    }

    private List<Long> occurrenceStarts(CalendarEntry t, long afterExclusive, long untilInclusive) {
        return occurrenceStarts(t.getRepetition(), t.getStartMillis(), t.getRepetitionInterval(),
                t.getRepetitionDays(), afterExclusive, untilInclusive);
    }

    // Calcula los instantes de inicio de las ocurrencias en (afterExclusive, untilInclusive],
    // preservando la hora del día de la ancla. Función pura (sin BD ni Android más allá de
    // Calendar) para poder cubrirla con tests JVM — ver RecurrenceMaterializerTest.
    static List<Long> occurrenceStarts(int repetition, long anchor, int interval, int daysMask,
                                       long afterExclusive, long untilInclusive) {
        List<Long> out = new ArrayList<>();
        if (anchor <= 0L) return out;

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(anchor);

        switch (repetition) {
            case RepetitionOptions.MONTHLY: {
                int anchorDay = c.get(Calendar.DAY_OF_MONTH);
                // Caminar sobre el día 1 y fijar el día objetivo (clamp al máximo real del mes) en
                // CADA iteración evita el "clamp pegajoso" de add(MONTH): un ancla el 31-ene, con
                // add(MONTH) directo, degrada a 28-feb -> 28-mar -> 28-abr... para siempre. Con la
                // re-derivación desde anchorDay, feb da 28 pero mar vuelve a 31.
                c.set(Calendar.DAY_OF_MONTH, 1);
                fastForwardMonths(c, afterExclusive);
                while (c.getTimeInMillis() <= untilInclusive) {
                    Calendar occ = (Calendar) c.clone();
                    occ.set(Calendar.DAY_OF_MONTH,
                            Math.min(anchorDay, occ.getActualMaximum(Calendar.DAY_OF_MONTH)));
                    long m = occ.getTimeInMillis();
                    if (m > afterExclusive && m <= untilInclusive) out.add(m);
                    c.add(Calendar.MONTH, 1);
                }
                break;
            }
            case RepetitionOptions.WEEKLY: {
                int mask = daysMask;
                if (mask == 0) {
                    // Sin días marcados: semanal en el día de la semana de la ancla.
                    mask = 1 << RepetitionSpec.weekdayBit(c);
                }
                fastForwardDays(c, afterExclusive, 7); // saltos de semana entera preservan el weekday
                while (c.getTimeInMillis() <= untilInclusive) {
                    long m = c.getTimeInMillis();
                    if (m > afterExclusive && (mask & (1 << RepetitionSpec.weekdayBit(c))) != 0) {
                        out.add(m);
                    }
                    c.add(Calendar.DAY_OF_YEAR, 1);
                }
                break;
            }
            case RepetitionOptions.DAILY:
            default: {
                int step = Math.max(1, interval);
                fastForwardDays(c, afterExclusive, step);
                while (c.getTimeInMillis() <= untilInclusive) {
                    long m = c.getTimeInMillis();
                    if (m > afterExclusive) out.add(m);
                    c.add(Calendar.DAY_OF_YEAR, step);
                }
                break;
            }
        }
        return out;
    }

    // Adelanta c en bloques completos de stepDays hasta justo por debajo de afterExclusive, para no
    // re-caminar todo el histórico en cada top-up (una plantilla de un año son cientos de add()).
    // Conservador por diseño (margen -2 pasos): usa días de 24h como aproximación, así que la deriva
    // por horario de verano (≤1h) nunca hace que saltemos una ocurrencia válida — el while afina.
    private static void fastForwardDays(Calendar c, long afterExclusive, int stepDays) {
        long start = c.getTimeInMillis();
        if (afterExclusive <= start) return;
        long stepMillis = (long) stepDays * DAY_MILLIS;
        long steps = (afterExclusive - start) / stepMillis - 2;
        if (steps > 0) {
            c.add(Calendar.DAY_OF_YEAR, (int) (steps * stepDays));
        }
    }

    // Igual que fastForwardDays pero en meses. Divisor de 31 días (subestima el nº de meses) más
    // margen -1: garantiza no sobrepasar. c debe venir ya normalizado al día 1 (ver MONTHLY).
    private static void fastForwardMonths(Calendar c, long afterExclusive) {
        long start = c.getTimeInMillis();
        if (afterExclusive <= start) return;
        long months = (afterExclusive - start) / (31L * DAY_MILLIS) - 1;
        if (months > 0) {
            c.add(Calendar.MONTH, (int) months);
        }
    }

    private void insertOccurrence(CalendarEntry template, long startMillis) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(startMillis);

        boolean[] notifications = template.getNotifications() != null
                ? Arrays.copyOf(template.getNotifications(), template.getNotifications().length)
                : new boolean[7];

        CalendarEntry occurrence = new CalendarEntry.EventBuilder()
                .setEventType(template.getType())  // hereda el tipo de la plantilla, no lo asume tarea
                .setEventTitle(template.getTitle())
                .setEventDescription(template.getDescription())
                .setEventStartDayAndHour(start)
                .setEventDuration(template.getDuration())
                .setEventTargetMinutes(template.getTargetMinutes())
                .setEventNotifications(notifications)
                .setEventIsDone(false)
                .setEventTemplateId(template.getId())
                .build();

        long rowId = dao.insert(occurrence);
        occurrence.setId((int) rowId);
        scheduler.scheduleFor(occurrence);
    }

    private long horizonMillis() {
        return System.currentTimeMillis() + HORIZON_DAYS * DAY_MILLIS;
    }
}
