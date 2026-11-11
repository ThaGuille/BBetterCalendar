package com.example.bbettercalendar.calendarEntries;

import androidx.annotation.NonNull;

/**
 * mejora-visual u1 (DB v15): la ÚNICA forma de marcar o desmarcar una fila como hecha. Pone
 * {@code isDone} y, a la vez, {@code completedAtMillis}: el instante del marcado al marcar y 0 al
 * desmarcar. Así "tareas hechas en este periodo" cuenta por la fecha real de completado y no por
 * la fecha de la tarea. Puro (sin Android ni BD): el llamador lo aplica a la fila releída y la guarda.
 *
 * <p>Lo usan los 4 sitios que marcan hecho: {@code HomeViewModel.setTaskDone},
 * {@code ProjectDetailViewModel} y {@code ProjectsViewModel} (casillas) y
 * {@code FocusAttributionRepository.maybeAutoComplete} (objetivo de minutos alcanzado).
 */
public final class CompletionStamp {

    private CompletionStamp() { }

    /**
     * Marca ({@code done = true}) o desmarca la fila. Marcar una fila que YA estaba hecha y tiene
     * sello conserva el sello original (un segundo toque no mueve la fecha de completado).
     *
     * @param nowMillis {@code System.currentTimeMillis()} del llamador
     */
    public static void apply(@NonNull CalendarEntry entry, boolean done, long nowMillis) {
        if (done) {
            if (!entry.isDone() || entry.getCompletedAtMillis() <= 0) {
                entry.setCompletedAtMillis(nowMillis);
            }
        } else {
            entry.setCompletedAtMillis(0L);
        }
        entry.setDone(done);
    }
}
