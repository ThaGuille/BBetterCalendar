package com.example.bbettercalendar.projects;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.calendarEntries.CompletionStamp;
import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.stats.AttributedMinutes;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.ProjectMinutes;
import com.example.bbettercalendar.ui.projects.agenda.AgendaTask;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Tareas, subtareas y secciones de un proyecto (rediseno-proyectos, spec 3 y 4). Toda escritura va en
 * {@code @DbWriteExecutor}; los resultados que la pantalla necesita vuelven por {@link Callback} en el
 * hilo principal y el estado llega por Room LiveData ({@link #observeBoard}).
 */
@Singleton
public class ProjectTasksRepository {

    private final CalendarEntryDAO calendarEntryDao;
    private final ProjectSectionDAO sectionDao;
    private final TaskSubtaskDAO subtaskDao;
    private final FocusEventDAO focusEventDao;
    private final TaskAlarms taskAlarms;
    private final TransactionRunner transaction;
    private final MainThread mainThread;
    private final ExecutorService dbWriteExecutor;

    @Inject
    public ProjectTasksRepository(CalendarEntryDAO calendarEntryDao, ProjectSectionDAO sectionDao,
                                  TaskSubtaskDAO subtaskDao, FocusEventDAO focusEventDao,
                                  TaskAlarms taskAlarms, TransactionRunner transaction,
                                  MainThread mainThread,
                                  @DbWriteExecutor ExecutorService dbWriteExecutor) {
        this.calendarEntryDao = calendarEntryDao;
        this.sectionDao = sectionDao;
        this.subtaskDao = subtaskDao;
        this.focusEventDao = focusEventDao;
        this.taskAlarms = taskAlarms;
        this.transaction = transaction;
        this.mainThread = mainThread;
        this.dbWriteExecutor = dbWriteExecutor;
    }

    // ---- lectura ----

    /**
     * El tablero del proyecto en un solo LiveData. Se vuelve a emitir con una escritura en cualquiera
     * de las tablas de origen (secciones, tareas, subtareas, eventos de enfoque); no emite hasta que
     * los cinco origenes han dado su primer valor. Los minutos de la semana cuentan desde el lunes
     * 00:00 local de la semana en que se llama.
     */
    public LiveData<ProjectBoard> observeBoard(int projectId) {
        long weekStart = ProjectBoard.weekStartMillis(System.currentTimeMillis(), ZoneId.systemDefault());
        LiveData<List<ProjectSection>> sections = sectionDao.observeByProject(projectId);
        LiveData<List<CalendarEntry>> entries = calendarEntryDao.observeTasksByProject(projectId);
        LiveData<List<TaskSubtask>> subtasks = subtaskDao.observeByProject(projectId);
        LiveData<List<AttributedMinutes>> attributed = focusEventDao.observeAttributedMinutesByEntry();
        LiveData<List<ProjectMinutes>> weekMinutes =
                focusEventDao.observeMinutesByProject(weekStart, Long.MAX_VALUE);

        MediatorLiveData<ProjectBoard> board = new MediatorLiveData<>();
        Runnable recompute = () -> {
            if (sections.getValue() == null || entries.getValue() == null
                    || subtasks.getValue() == null || attributed.getValue() == null
                    || weekMinutes.getValue() == null) {
                return;
            }
            board.setValue(ProjectBoard.assemble(projectId, sections.getValue(), entries.getValue(),
                    subtasks.getValue(), attributed.getValue(), weekMinutes.getValue()));
        };
        board.addSource(sections, v -> recompute.run());
        board.addSource(entries, v -> recompute.run());
        board.addSource(subtasks, v -> recompute.run());
        board.addSource(attributed, v -> recompute.run());
        board.addSource(weekMinutes, v -> recompute.run());
        return board;
    }

    /**
     * Las tareas pendientes (no hechas, no plantillas, no retiradas) del proyecto para la hoja de agenda,
     * leidas fuera del hilo principal; {@code callback} las recibe en el hilo principal.
     */
    public void loadAgenda(int projectId, Callback<List<AgendaTask>> callback) {
        dbWriteExecutor.execute(() -> {
            List<AgendaTask> out = new ArrayList<>();
            for (CalendarEntry e : calendarEntryDao.getItemsByProjectNow(projectId)) {
                if (e.isDone() || e.isTemplate() || e.isDismissed()) continue;
                out.add(new AgendaTask(e.getTitle(), Math.max(0L, e.getStartMillis()), hasTime(e),
                        e.getDuration() / 60_000, e.isBlockOnFocus()));
            }
            mainThread.post(() -> callback.onResult(out));
        });
    }

    private static boolean hasTime(CalendarEntry e) {
        if (e.getStartMillis() <= 0L) return false;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(e.getStartMillis());
        return c.get(Calendar.HOUR_OF_DAY) != 0 || c.get(Calendar.MINUTE) != 0;
    }

    // ---- tareas ----

    /**
     * Inserta o actualiza la tarea del borrador (nueva si {@code editId} es 0) y reemplaza sus
     * subtareas, en una transaccion; despues reprograma sus alarmas. Un borrador sin titulo no se
     * guarda. {@code callback} (puede ser null) recibe el id de la tarea en el hilo principal.
     */
    public void saveDraft(TaskDraft draft, Callback<Integer> callback) {
        dbWriteExecutor.execute(() -> {
            if (draft == null || !draft.canSave()) return;
            CalendarEntry existing = draft.editId != 0
                    ? calendarEntryDao.getEventById((int) draft.editId) : null;
            int sectionId = (int) draft.sectionId;
            if (sectionId == 0) {
                // Sin seccion elegida: la primera del proyecto.
                ProjectSection first = sectionDao.getFirstOfProject((int) draft.projectId);
                if (first != null) sectionId = first.id;
            }
            CalendarEntry entry = buildEntry(draft, existing, sectionId, System.currentTimeMillis());
            List<TaskSubtask> subtasks = new ArrayList<>();
            for (int i = 0; i < draft.subtasks.size(); i++) {
                TaskDraft.Subtask s = draft.subtasks.get(i);
                subtasks.add(new TaskSubtask(0, s.title, s.done, i));
            }
            transaction.run(() -> {
                int id;
                if (existing == null) {
                    id = (int) calendarEntryDao.insert(entry);
                    entry.setId(id);
                } else {
                    id = existing.getId();
                    calendarEntryDao.update(entry);
                }
                subtaskDao.deleteByEntry(id);
                for (TaskSubtask s : subtasks) s.entryId = id;
                if (!subtasks.isEmpty()) subtaskDao.insertAll(subtasks);
            });
            taskAlarms.onTaskSaved(entry);
            if (callback != null) {
                int id = entry.getId();
                mainThread.post(() -> callback.onResult(id));
            }
        });
    }

    // Construye la fila SOLO con CalendarEntry.EventBuilder (regla #4). Al editar, lo que el borrador
    // no maneja (repeticion, plantilla, marcas de materializado, retirada, sello de completado) se
    // conserva de la fila existente. La repeticion sigue inerte hasta que llegue su parte.
    static CalendarEntry buildEntry(TaskDraft draft, CalendarEntry existing, int sectionId,
                                    long nowMillis) {
        CalendarEntry.EventBuilder builder = new CalendarEntry.EventBuilder()
                .setEventType(AddEventActivity.TYPE_TASK)
                .setEventTitle(draft.title.trim())
                .setEventDescription(draft.note)
                .setEventProjectId((int) draft.projectId)
                .setSectionId(sectionId)
                .setPriority(draft.priority)
                .setTagId((int) draft.tagId)
                .setBlockOnFocus(draft.isBlockOnFocus())
                .setEventDuration((int) draft.durationMillis())
                .setEventTargetMinutes(draft.targetMinutes())
                .setEventNotifications(draft.toNotificationFlags())
                .setEventIsDone(draft.done);
        String note = draft.completionNote == null ? "" : draft.completionNote.trim();
        builder.setCompletionNote(draft.done && !note.isEmpty() ? note : null);

        Long day = draft.getDayStartMillis();
        boolean inSeries = existing != null && (existing.isTemplate() || existing.getTemplateId() != 0);
        if (day != null) {
            // Igual que QuickAddTaskSheet: sin hora elegida la tarea queda a las 00:00 de su dia.
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(day);
            Integer time = draft.getTimeOfDayMinutes();
            start.set(Calendar.HOUR_OF_DAY, time == null ? 0 : time / 60);
            start.set(Calendar.MINUTE, time == null ? 0 : time % 60);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            builder.setEventStartDayAndHour(start);
            builder.setEventHiddenInCalendar(inSeries && existing.isHiddenInCalendar());
        } else {
            // Sin fecha: vive solo dentro del proyecto, no se pinta en el Calendario.
            builder.setEventHiddenInCalendar(true);
        }

        if (existing != null) {
            builder.setEventId(existing.getId())
                    .setEventRepetition(existing.getRepetition())
                    .setEventRepetitionInterval(existing.getRepetitionInterval())
                    .setEventRepetitionDays(existing.getRepetitionDays())
                    .setEventIsTemplate(existing.isTemplate())
                    .setEventTemplateId(existing.getTemplateId());
        }
        CalendarEntry entry = builder.build();
        if (existing != null) {
            entry.setMaterializedUntilMillis(existing.getMaterializedUntilMillis());
            entry.setDismissed(existing.isDismissed());
            entry.setCompletedAtMillis(existing.getCompletedAtMillis());
        }
        CompletionStamp.apply(entry, draft.done, nowMillis);
        return entry;
    }

    /** Marca la tarea y todas sus subtareas como hechas o pendientes. */
    public void setDone(int entryId, boolean done) {
        dbWriteExecutor.execute(() -> {
            CalendarEntry[] holder = new CalendarEntry[1];
            transaction.run(() -> {
                CalendarEntry fresh = calendarEntryDao.getEventById(entryId);
                if (fresh == null) return;
                CompletionStamp.apply(fresh, done, System.currentTimeMillis());
                calendarEntryDao.update(fresh);
                subtaskDao.setAllDone(entryId, done);
                holder[0] = fresh;
            });
            CalendarEntry fresh = holder[0];
            if (fresh == null) return;
            if (done) {
                taskAlarms.onTaskClosed(fresh);
            } else {
                taskAlarms.onTaskSaved(fresh);
            }
        });
    }

    /** Alterna una subtarea; si deja todas hechas, la tarea pasa a hecha. */
    public void toggleSubtask(int subtaskId) {
        dbWriteExecutor.execute(() -> {
            CalendarEntry[] completed = new CalendarEntry[1];
            transaction.run(() -> {
                TaskSubtask subtask = subtaskDao.getById(subtaskId);
                if (subtask == null) return;
                subtask.done = !subtask.done;
                subtaskDao.update(subtask);
                if (!subtask.done) return;
                List<TaskSubtask> all = subtaskDao.getByEntry(subtask.entryId);
                for (TaskSubtask s : all) {
                    if (!s.done) return;
                }
                CalendarEntry task = calendarEntryDao.getEventById(subtask.entryId);
                if (task == null || task.isDone()) return;
                CompletionStamp.apply(task, true, System.currentTimeMillis());
                calendarEntryDao.update(task);
                completed[0] = task;
            });
            if (completed[0] != null) taskAlarms.onTaskClosed(completed[0]);
        });
    }

    /** Borra la tarea y sus subtareas y cancela sus alarmas. */
    public void deleteTask(int entryId) {
        dbWriteExecutor.execute(() -> {
            CalendarEntry entry = calendarEntryDao.getEventById(entryId);
            if (entry == null) return;
            taskAlarms.onTaskClosed(entry);
            transaction.run(() -> {
                subtaskDao.deleteByEntry(entryId);
                calendarEntryDao.delete(entry);
            });
        });
    }

    /** Nota al completar; el texto vacio la quita. */
    public void setCompletionNote(int entryId, String text) {
        dbWriteExecutor.execute(() -> {
            CalendarEntry fresh = calendarEntryDao.getEventById(entryId);
            if (fresh == null) return;
            String trimmed = text == null ? "" : text.trim();
            fresh.setCompletionNote(trimmed.isEmpty() ? null : trimmed);
            calendarEntryDao.update(fresh);
        });
    }

    public void moveTask(int entryId, int sectionId) {
        dbWriteExecutor.execute(() -> calendarEntryDao.moveToSection(entryId, sectionId));
    }

    // ---- secciones ----

    /** Anade una seccion al final del proyecto. */
    public void addSection(int projectId, String name) {
        dbWriteExecutor.execute(() -> sectionDao.insert(
                new ProjectSection(projectId, name, sectionDao.nextPosition(projectId))));
    }

    public void renameSection(int sectionId, String name) {
        dbWriteExecutor.execute(() -> {
            ProjectSection section = sectionDao.getById(sectionId);
            if (section == null) return;
            section.name = name;
            sectionDao.update(section);
        });
    }

    /**
     * Borra la seccion y pasa sus tareas a la primera seccion que quede (por posicion). La ultima
     * seccion de un proyecto no se puede borrar: no cambia nada y {@code callback} (puede ser null)
     * recibe {@code false}; si se borra, recibe {@code true}. Llega en el hilo principal.
     */
    public void deleteSection(int sectionId, Callback<Boolean> callback) {
        dbWriteExecutor.execute(() -> {
            boolean deleted = deleteSectionNow(sectionId);
            if (callback != null) mainThread.post(() -> callback.onResult(deleted));
        });
    }

    /** La escritura de {@link #deleteSection}; fuera del hilo principal. Devuelve false para la ultima seccion. */
    boolean deleteSectionNow(int sectionId) {
        ProjectSection section = sectionDao.getById(sectionId);
        if (section == null) return false;
        List<ProjectSection> all = sectionDao.getByProject(section.projectId);
        ProjectSection target = null;
        for (ProjectSection s : all) {
            if (s.id != sectionId) {
                target = s;
                break;
            }
        }
        if (target == null) return false;
        ProjectSection dest = target;
        transaction.run(() -> {
            calendarEntryDao.moveSectionTasks(sectionId, dest.id);
            sectionDao.delete(section);
        });
        return true;
    }
}
