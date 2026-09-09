package com.example.bbettercalendar.ui.calendar.domain;

import android.content.Context;

import com.example.bbettercalendar.projects.Project;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte deadlines de proyecto en {@link CalendarItem} para pintarlos en el calendario junto a
 * las entradas reales (spec project-deadlines-progress). No hay filas espejo en calendarEntry: el
 * calendario lee la tabla project directamente, así que no hay nada que sincronizar ni que pueda
 * quedar desfasado.
 *
 * El item resultante es de longitud cero ({@code start == end}), exactamente como queda un
 * recordatorio tras el clamp {@code end < start} de {@link CalendarItemMapper}.
 *
 * <b>El id va negado</b> ({@code -project.id}): un id negativo marca "esto no es un CalendarEntry",
 * así que ningún consumidor puede confundirlo con uno ni usarlo para abrir el editor de entradas.
 * {@link #projectIdOf(CalendarItem)} lo descodifica de vuelta — encode y decode viven juntos aquí.
 */
public final class ProjectDeadlineItemMapper {

    private ProjectDeadlineItemMapper() {}

    /**
     * Variante sin Context, con el color ya resuelto: mantiene la lógica de id/instante pura y
     * testable en la JVM (mismo truco que {@code FocusAttributionRepository.applyAttribution}).
     * Devuelve null si el proyecto no tiene deadline.
     */
    static CalendarItem toItem(Project project, int colorArgb) {
        if (project == null || project.softDeadlineMillis <= 0L) {
            return null;
        }
        long at = project.softDeadlineMillis;
        return new CalendarItem(
                -project.id,
                project.name != null ? project.name : "",
                null,
                at,
                at,
                colorArgb,
                CalendarItem.Type.DEADLINE
        );
    }

    public static List<CalendarItem> toItems(List<Project> projects, Context ctx) {
        List<CalendarItem> out = new ArrayList<>();
        if (projects == null) {
            return out;
        }
        int color = ColorResolver.colorFor(CalendarItem.Type.DEADLINE, ctx);
        for (Project project : projects) {
            CalendarItem item = toItem(project, color);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** Descodifica el id del proyecto de un item DEADLINE. 0 si el item no es un deadline. */
    public static int projectIdOf(CalendarItem item) {
        if (item == null || item.getType() != CalendarItem.Type.DEADLINE) {
            return 0;
        }
        return -item.getId();
    }
}
