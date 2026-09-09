package com.example.bbettercalendar.stats;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final MutableLiveData<String> autoCompletedTaskTitle = new MutableLiveData<>();

    @Inject
    public FocusAttributionRepository(FocusEventDAO focusEventDao, CalendarEntryDAO calendarEntryDao) {
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
            result.setValue(applyAttribution(currentEntries, byId));
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
            entry.setDone(true);
            calendarEntryDao.update(entry);
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
