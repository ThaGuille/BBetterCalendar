package com.example.bbettercalendar.notifications.task;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.projects.Callback;
import com.example.bbettercalendar.projects.MainThread;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDAO;
import com.example.bbettercalendar.projects.ProjectTasksRepository;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.ui.focus.TaskFocusRequest;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Lo que el boton «Empezar» del aviso necesita antes de que MainActivity arranque Focus: la meta de la
 * tarea (si no la tiene, su pomodoro como en el cronometro del resumen, decision d9), los minutos que ya
 * lleva y su proyecto, todo leido fuera del hilo principal. El resultado llega en el hilo principal.
 */
@Singleton
public class TaskStartLauncher {

    private final CalendarEntryDAO entryDao;
    private final ProjectDAO projectDao;
    private final FocusEventDAO focusEventDao;
    private final ProjectTasksRepository tasks;
    private final MainThread mainThread;
    private final ExecutorService io;

    @Inject
    public TaskStartLauncher(CalendarEntryDAO entryDao, ProjectDAO projectDao, FocusEventDAO focusEventDao,
                             ProjectTasksRepository tasks, MainThread mainThread,
                             @IoExecutor ExecutorService io) {
        this.entryDao = entryDao;
        this.projectDao = projectDao;
        this.focusEventDao = focusEventDao;
        this.tasks = tasks;
        this.mainThread = mainThread;
        this.io = io;
    }

    /** {@code callback} recibe null si la tarea ya no existe o esta hecha. */
    public void prepare(int entryId, @NonNull Callback<TaskFocusRequest> callback) {
        tasks.ensurePomodoro(entryId, goal -> io.execute(() -> {
            TaskFocusRequest request = null;
            try {
                CalendarEntry entry = entryDao.getEventById(entryId);
                if (entry != null && !entry.isDone() && goal != null && goal > 0) {
                    Project project = projectDao.getById(entry.getProjectId());
                    request = new TaskFocusRequest(entryId, entry.getTitle(), goal,
                            focusEventDao.sumAttributedMinutes(entryId), entry.isBlockOnFocus(),
                            project == null ? 0 : project.id, project == null ? null : project.name,
                            project == null ? null : project.iconKey,
                            project == null ? 0 : project.colorIndex);
                }
            } finally {
                TaskFocusRequest result = request;
                mainThread.post(() -> callback.onResult(result));
            }
        }));
    }
}
