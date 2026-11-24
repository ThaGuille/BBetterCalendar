package com.example.bbettercalendar.projects;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.calendarEntries.RecurrenceMaterializer;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Lo que pasa con las tareas de un proyecto al cerrarlo (archivar o completar) y al reabrirlo
 * (desarchivar, reabrir o el Deshacer de esas acciones), spec 3.8:
 * <ul>
 *   <li>Cerrar: fuera los recordatorios y avisos de empezar de todas sus tareas
 *   ({@link TaskAlarms#onProjectClosed}) y sus series se cortan: las ocurrencias futuras sin hacer se
 *   retiran (los datos quedan) y {@link RecurrenceMaterializer} deja de rellenarlas mientras el proyecto
 *   no esté ACTIVE.</li>
 *   <li>Reabrir: vuelven las ocurrencias futuras retiradas al cerrar, la marca de agua de cada serie
 *   avanza hasta "ahora" (lo que cayó mientras estuvo cerrado no se genera) y la serie se rellena desde
 *   ahí; las alarmas de sus tareas pendientes se rearman.</li>
 * </ul>
 * Sincrona: la llama {@link ProjectsRepository} desde el executor de escritura, con el status del
 * proyecto ya guardado.
 */
@Singleton
public class ProjectLifecycle {

    private final CalendarEntryDAO calendarEntryDao;
    private final TaskAlarms taskAlarms;
    private final RecurrenceMaterializer materializer;

    @Inject
    public ProjectLifecycle(CalendarEntryDAO calendarEntryDao, TaskAlarms taskAlarms,
                            RecurrenceMaterializer materializer) {
        this.calendarEntryDao = calendarEntryDao;
        this.taskAlarms = taskAlarms;
        this.materializer = materializer;
    }

    /** Proyecto archivado o completado en {@code nowMillis}: corta sus series y apaga sus avisos. */
    public void onProjectClosed(int projectId, long nowMillis) {
        taskAlarms.onProjectClosed(projectId);
        calendarEntryDao.dismissProjectSeriesAfter(projectId, nowMillis);
    }

    /** Proyecto vuelto a ACTIVE en {@code nowMillis}: sus series siguen desde ahora y sus avisos vuelven. */
    public void onProjectReopened(int projectId, long nowMillis) {
        calendarEntryDao.restoreProjectSeriesAfter(projectId, nowMillis);
        // Antes de materializar: asi las ocurrencias nuevas no se programan dos veces.
        taskAlarms.onProjectReopened(projectId);
        List<CalendarEntry> templates = calendarEntryDao.getRepeatingTemplatesOfProject(projectId);
        if (templates == null) return;
        for (CalendarEntry template : templates) {
            if (template.getMaterializedUntilMillis() < nowMillis) {
                template.setMaterializedUntilMillis(nowMillis);
                calendarEntryDao.update(template);
            }
            materializer.materializeTemplate(template.getId());
        }
    }
}
