package com.example.bbettercalendar.stats;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.calendarEntries.CompletionStamp;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import javax.inject.Inject;
import javax.inject.Singleton;

// Repositorio de atribución de minutos de foco por tarea/item (tranche T2, spec
// repository-layer-consolidation, finding F4): antes enrichWithAttributedMinutes vivía
// duplicado, literal, en HomeViewModel y ProjectDetailViewModel. Al observar focus_event (no
// sólo la lista de entries de entrada), X/Ym se actualiza en cuanto termina un pomodoro, en vez
// de esperar a que la lista de tareas/items vuelva a emitir primero (bug fix aceptado, ver
// proposal.md "Behavior changes we accept").
@Singleton
public class FocusAttributionRepository {

    private final FocusEventDAO focusEventDao;
    private final CalendarEntryDAO calendarEntryDao;
    private final EventReminderScheduler eventReminderScheduler;
    private final MutableLiveData<String> autoCompletedTaskTitle = new MutableLiveData<>();

    @Inject
    public FocusAttributionRepository(FocusEventDAO focusEventDao, CalendarEntryDAO calendarEntryDao,
                                      EventReminderScheduler eventReminderScheduler) {
        this.eventReminderScheduler = eventReminderScheduler;
        this.focusEventDao = focusEventDao;
        this.calendarEntryDao = calendarEntryDao;
    }

    // Fusión pura -- sin I/O -- para poder testearla con JUnit puro (ver FocusAttributionTest).
    // Sólo toca las entries con targetMinutes > 0; el resto se deja tal cual (attributedMinutes
    // por defecto a 0 en CalendarEntry).
    static List<CalendarEntry> applyAttribution(List<CalendarEntry> entries,
                                                 Map<Integer, Integer> minutesByEntryId) {
        if (entries == null) {
            return null;
        }
        for (CalendarEntry entry : entries) {
            if (entry.getTargetMinutes() > 0) {
                Integer minutes = minutesByEntryId.get(entry.getId());
                entry.setAttributedMinutes(minutes == null ? 0 : minutes);
            }
        }
        return entries;
    }

    /**
     * Rediseño de Proyectos (u4): como {@link #applyAttribution} pero sin tocar las filas que ya se
     * emitieron. Con los ciclos de una tarea (y «Terminar antes») los minutos cambian sin que cambie la
     * fila en Room; si se mutara la misma instancia, la lista que un adapter ya tiene pintada cambiaría
     * por debajo y su DiffUtil no vería nada nuevo (Home se quedaba en «25 / 60 min»). Cada fila con
     * meta sale como copia con sus minutos; las demás, tal cual.
     */
    static List<CalendarEntry> withAttribution(List<CalendarEntry> entries,
                                               Map<Integer, Integer> minutesByEntryId) {
        if (entries == null) {
            return null;
        }
        List<CalendarEntry> out = new ArrayList<>(entries.size());
        for (CalendarEntry entry : entries) {
            if (entry.getTargetMinutes() > 0) {
                Integer minutes = minutesByEntryId.get(entry.getId());
                CalendarEntry copy = copyOf(entry);
                copy.setAttributedMinutes(minutes == null ? 0 : minutes);
                out.add(copy);
            } else {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * Copia superficial de una fila ya construida, campo a campo (todos, también los transitorios),
     * para no depender de una lista de campos que se quede corta cuando la entidad crezca. No es
     * construir una entrada nueva (regla 4): la original ya pasó por EventBuilder.
     */
    static CalendarEntry copyOf(CalendarEntry source) {
        CalendarEntry copy = new CalendarEntry();
        for (Field field : CalendarEntry.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                field.set(copy, field.get(source));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot copy CalendarEntry." + field.getName(), e);
            }
        }
        return copy;
    }

    /**
     * Envuelve una lista observable de CalendarEntry (tareas/items) con sus minutos atribuidos,
     * recalculando cuando cambia la lista de entrada O cuando se registra un nuevo FocusEvent --
     * las dos fuentes viven en tablas distintas, así que hace falta un MediatorLiveData
     * (Transformations.map sólo observa una fuente). El merge corre en el hilo principal: ambas
     * fuentes ya hicieron su I/O en el executor de Room, así que esto sólo recorre dos listas ya
     * en memoria -- no es DB/disco, así que la regla #3 no exige postValue aquí (evita además el
     * fallo de ordenación entre hilos del pool de 4 que tuvo que arreglarse tras T1, ver commit
     * 4d6f9a8).
     */
    public LiveData<List<CalendarEntry>> enrich(LiveData<List<CalendarEntry>> entries) {
        MediatorLiveData<List<CalendarEntry>> result = new MediatorLiveData<>();
        LiveData<List<AttributedMinutes>> minutesSource = focusEventDao.observeAttributedMinutesByEntry();
        Runnable recompute = () -> {
            List<CalendarEntry> currentEntries = entries.getValue();
            if (currentEntries == null) {
                // minutesSource (una fuente independiente) puede disparar el merge antes de que
                // entries haya emitido nunca -- no hay nada que publicar todavía. Sin esta guarda
                // se reenvía null aguas abajo y HomeFragment/ProjectDetailFragment revientan al
                // llamar .isEmpty() sobre él (bug real encontrado por el ui-tester en la
                // verificación de repository-layer-consolidation).
                return;
            }
            if (currentEntries.isEmpty()) {
                result.setValue(currentEntries);
                return;
            }
            List<AttributedMinutes> sums = minutesSource.getValue();
            Map<Integer, Integer> byId = new HashMap<>();
            if (sums != null) {
                for (AttributedMinutes am : sums) {
                    byId.put(am.entryId, am.minutes);
                }
            }
            result.setValue(withAttribution(currentEntries, byId));
        };
        result.addSource(entries, e -> recompute.run());
        result.addSource(minutesSource, m -> recompute.run());
        return result;
    }

    /**
     * Auto-completado (spec focus-attribution, decisión #7): sólo se evalúa al terminar una
     * sesión vinculada -- nunca en un recálculo pasivo -- para que un des-marcado manual no se
     * vuelva a marcar solo. Marca isDone y emite el evento de feedback si se alcanza el objetivo.
     * Movido aquí desde HomeViewModel (T2, F4) por cohesión -- es el lado de escritura de la
     * misma regla que enrich() lee, no estaba duplicado en ningún otro sitio. Debe llamarse ya
     * fuera del hilo principal (misma secuencia atómica que HomeViewModel.completeTimer() en
     * @DbWriteExecutor) -- no crea su propio hilo.
     */
    public void maybeAutoComplete(int entryId) {
        CalendarEntry entry = calendarEntryDao.getEventById(entryId);
        if (entry == null || entry.isDone() || entry.getTargetMinutes() <= 0) {
            return;
        }
        int attributed = focusEventDao.sumAttributedMinutes(entryId);
        if (attributed >= entry.getTargetMinutes()) {
            CompletionStamp.apply(entry, true, System.currentTimeMillis());
            calendarEntryDao.update(entry);
            eventReminderScheduler.cancelFor(entry);
            autoCompletedTaskTitle.postValue(entry.getTitle());
        }
    }

    public LiveData<String> getAutoCompletedTaskTitle() {
        return autoCompletedTaskTitle;
    }

    /** El fragment lo llama tras consumir el evento de auto-completado (evita re-disparo). */
    public void clearAutoCompletedTaskTitle() {
        autoCompletedTaskTitle.setValue(null);
    }
}
