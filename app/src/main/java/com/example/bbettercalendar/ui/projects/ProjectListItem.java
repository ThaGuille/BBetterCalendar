package com.example.bbettercalendar.ui.projects;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.Project;

import java.util.Collections;
import java.util.Comparator;
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

    /** Minutos que le faltan a una subtarea: objetivo menos lo ya atribuido (nunca negativo). */
    public static int remainingMinutes(CalendarEntry entry) {
        return Math.max(0, entry.getTargetMinutes() - entry.getAttributedMinutes());
    }

    /** Minutos que faltan en las subtareas sin hacer (estimación restante; 0 si ninguna tiene objetivo). */
    public int estimatedRemainingMinutes() {
        int total = 0;
        for (CalendarEntry entry : subtasks) {
            if (!entry.isDone() && entry.getTargetMinutes() > 0) {
                total += remainingMinutes(entry);
            }
        }
        return total;
    }

    /**
     * Orden de la lista (html 03): activos antes que completados/archivados; dentro, por urgencia
     * (fecha límite más cercana primero, los sin fecha al final) y, a igualdad, el más reciente.
     */
    public static final Comparator<ProjectListItem> BY_URGENCY = (a, b) -> {
        int byStatus = Integer.compare(a.project.status, b.project.status);
        if (byStatus != 0) return byStatus;
        long da = a.project.softDeadlineMillis;
        long db = b.project.softDeadlineMillis;
        boolean ha = da > 0L;
        boolean hb = db > 0L;
        if (ha != hb) return ha ? -1 : 1;
        if (ha && da != db) return Long.compare(da, db);
        return Long.compare(b.project.createdAtMillis, a.project.createdAtMillis);
    };
}
