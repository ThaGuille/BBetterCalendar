package com.example.bbettercalendar.ui.projects;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.Project;

import java.util.Collections;
import java.util.List;

// Fila de la lista de proyectos: el Project más su % ya calculado (recuento de items,
// decisión #6 del roadmap). No es una entidad Room — sólo una composición para la UI.
// u16: además lleva los subtareas (items) del proyecto, la duración del pomodoro configurada y los
// minutos de foco de hoy, para pintar la tarjeta desplegable (html 03) sin más consultas.
public class ProjectListItem {
    /** Duración de pomodoro si la configuración aún no se ha leído (Configuration: 20 min). */
    public static final int DEFAULT_POMODORO_MINUTES = 20;

    public final Project project;
    public final int doneCount;
    public final int totalCount;
    public final List<CalendarEntry> subtasks;
    public final int pomodoroMinutes;
    public final int todayMinutes;

    public ProjectListItem(Project project, int doneCount, int totalCount) {
        this(project, doneCount, totalCount, Collections.<CalendarEntry>emptyList(),
                DEFAULT_POMODORO_MINUTES, 0);
    }

    public ProjectListItem(Project project, int doneCount, int totalCount,
                           List<CalendarEntry> subtasks, int pomodoroMinutes, int todayMinutes) {
        this.project = project;
        this.doneCount = doneCount;
        this.totalCount = totalCount;
        this.subtasks = subtasks;
        this.pomodoroMinutes = pomodoroMinutes > 0 ? pomodoroMinutes : DEFAULT_POMODORO_MINUTES;
        this.todayMinutes = Math.max(0, todayMinutes);
    }

    public int percent() {
        return totalCount == 0 ? 0 : Math.round(100f * doneCount / totalCount);
    }

    /** Pomodoros que cubren un objetivo en minutos (redondeo hacia arriba; 0 si no hay objetivo). */
    public static int pomodorosFor(int targetMinutes, int pomodoroMinutes) {
        if (targetMinutes <= 0) {
            return 0;
        }
        int size = pomodoroMinutes > 0 ? pomodoroMinutes : DEFAULT_POMODORO_MINUTES;
        return (targetMinutes + size - 1) / size;
    }

    /** Pomodoros completados hoy en este proyecto, derivados de los minutos de foco atribuidos. */
    public int pomodorosToday() {
        return todayMinutes / pomodoroMinutes;
    }

    /** Minutos objetivo de las subtareas sin hacer (estimación restante; 0 si ninguna tiene objetivo). */
    public int estimatedRemainingMinutes() {
        int total = 0;
        for (CalendarEntry entry : subtasks) {
            if (!entry.isDone() && entry.getTargetMinutes() > 0) {
                total += entry.getTargetMinutes();
            }
        }
        return total;
    }
}
