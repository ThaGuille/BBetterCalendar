package com.example.bbettercalendar.projects;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.domain.TaskSnapshot;
import com.example.bbettercalendar.stats.AttributedMinutes;
import com.example.bbettercalendar.stats.ProjectMinutes;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Todo lo que pinta la pantalla de un proyecto, en un solo valor: secciones en orden, tareas como
 * {@link TaskSnapshot} (las plantillas marcadas), la fila original de cada tarea (para sus iconos),
 * subtareas por tarea, y los minutos enfocados del proyecto desde el lunes 00:00 de esta semana.
 * Los minutos atribuidos de cada tarea van dentro de su {@code TaskSnapshot}.
 */
public final class ProjectBoard {

    public final List<ProjectSection> sections;
    public final List<TaskSnapshot> tasks;
    /** Fila original de cada tarea, por id. */
    public final Map<Long, CalendarEntry> entries;
    /** Subtareas de cada tarea (por id de tarea), en su orden; sin entrada = sin subtareas. */
    public final Map<Long, List<TaskSubtask>> subtasks;
    public final int focusedWeekMinutes;

    public ProjectBoard(List<ProjectSection> sections, List<TaskSnapshot> tasks,
                        Map<Long, CalendarEntry> entries, Map<Long, List<TaskSubtask>> subtasks,
                        int focusedWeekMinutes) {
        this.sections = sections;
        this.tasks = tasks;
        this.entries = entries;
        this.subtasks = subtasks;
        this.focusedWeekMinutes = focusedWeekMinutes;
    }

    /** Lunes 00:00 (hora local) de la semana de {@code nowMillis}. */
    public static long weekStartMillis(long nowMillis, ZoneId zone) {
        LocalDate monday = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** Monta el tablero con lo que emiten los cinco origenes reactivos. */
    public static ProjectBoard assemble(int projectId, List<ProjectSection> sectionRows,
                                        List<CalendarEntry> entryRows, List<TaskSubtask> subtaskRows,
                                        List<AttributedMinutes> attributed,
                                        List<ProjectMinutes> projectMinutes) {
        List<ProjectSection> sections = sectionRows == null
                ? Collections.<ProjectSection>emptyList() : new ArrayList<>(sectionRows);

        Map<Integer, Integer> positionById = new HashMap<>();
        for (ProjectSection s : sections) positionById.put(s.id, s.position);
        // Una tarea con sectionId 0 (o de una seccion que ya no existe) se pinta en la primera.
        ProjectSection first = sections.isEmpty() ? null : sections.get(0);

        Map<Integer, Integer> minutesByEntry = new HashMap<>();
        if (attributed != null) {
            for (AttributedMinutes a : attributed) minutesByEntry.put(a.entryId, a.minutes);
        }

        List<TaskSnapshot> tasks = new ArrayList<>();
        Map<Long, CalendarEntry> entries = new HashMap<>();
        if (entryRows != null) {
            for (CalendarEntry e : entryRows) {
                long sectionId = e.getSectionId();
                int sectionPosition;
                Integer pos = positionById.get(e.getSectionId());
                if (pos != null) {
                    sectionPosition = pos;
                } else if (first != null) {
                    sectionId = first.id;
                    sectionPosition = first.position;
                } else {
                    sectionId = 0L;
                    sectionPosition = 0;
                }
                Integer attributedMin = minutesByEntry.get(e.getId());
                tasks.add(new TaskSnapshot(e.getId(), sectionId, sectionPosition, e.getTitle(),
                        e.getStartMillis(), hasTime(e), e.getPriority(), e.isDone(), e.isTemplate(),
                        e.getTargetMinutes(), attributedMin == null ? 0 : attributedMin,
                        e.isBlockOnFocus(), e.getTagId()));
                entries.put((long) e.getId(), e);
            }
        }

        Map<Long, List<TaskSubtask>> subtasks = new HashMap<>();
        if (subtaskRows != null) {
            for (TaskSubtask st : subtaskRows) {
                List<TaskSubtask> list = subtasks.get((long) st.entryId);
                if (list == null) {
                    list = new ArrayList<>();
                    subtasks.put((long) st.entryId, list);
                }
                list.add(st);
            }
        }

        int focused = 0;
        if (projectMinutes != null) {
            for (ProjectMinutes pm : projectMinutes) {
                if (pm.projectId == projectId) focused = pm.minutes;
            }
        }
        return new ProjectBoard(sections, tasks, entries, subtasks, focused);
    }

    // Las tareas con fecha pero sin hora se guardan a las 00:00 (como QuickAddTaskSheet): esa hora
    // exacta cuenta como "sin hora".
    private static boolean hasTime(CalendarEntry e) {
        if (e.getStartMillis() <= 0L) return false;
        Calendar c = e.getStartDayAndHour();
        if (c == null) {
            c = Calendar.getInstance();
            c.setTimeInMillis(e.getStartMillis());
        }
        return c.get(Calendar.HOUR_OF_DAY) != 0 || c.get(Calendar.MINUTE) != 0;
    }
}
