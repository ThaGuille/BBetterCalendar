package com.example.bbettercalendar.projects;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.calendarEntries.CompletionStamp;
import com.example.bbettercalendar.calendarEntries.RecurrenceMaterializer;
import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.popups.RepetitionOptions;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.stats.AttributedMinutes;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.ProjectMinutes;
import com.example.bbettercalendar.ui.projects.agenda.AgendaTask;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final RecurrenceMaterializer materializer;
    private final TransactionRunner transaction;
    private final MainThread mainThread;
    private final ExecutorService dbWriteExecutor;

    @Inject
    public ProjectTasksRepository(CalendarEntryDAO calendarEntryDao, ProjectSectionDAO sectionDao,
                                  TaskSubtaskDAO subtaskDao, FocusEventDAO focusEventDao,
                                  TaskAlarms taskAlarms, RecurrenceMaterializer materializer,
                                  TransactionRunner transaction, MainThread mainThread,
                                  @DbWriteExecutor ExecutorService dbWriteExecutor) {
        this.calendarEntryDao = calendarEntryDao;
        this.sectionDao = sectionDao;
        this.subtaskDao = subtaskDao;
        this.focusEventDao = focusEventDao;
        this.taskAlarms = taskAlarms;
        this.materializer = materializer;
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
     *
     * <p>Repetir (spec 3.8, decision d5): un borrador con Repetir crea una serie como el editor del
     * Calendario, una plantilla ({@code isTemplate = 1}) con las ocurrencias que rellena
     * {@link RecurrenceMaterializer}; la tarea escrita es su primera ocurrencia (lleva sus subtareas).
     * Al editar una ocurrencia los cambios son solo de esa ocurrencia; cambiar Repetir cambia la serie
     * desde ella (su plantilla pasa a la nueva frecuencia, anclada en esa ocurrencia) y quitarlo la
     * termina. En los dos casos las ocurrencias futuras sin hacer de la serie anterior se borran.
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
            long now = System.currentTimeMillis();
            CalendarEntry entry = buildEntry(draft, existing, sectionId, now);
            CalendarEntry found = existing == null || existing.isTemplate() || existing.getTemplateId() == 0
                    ? null : calendarEntryDao.getEventById(existing.getTemplateId());
            CalendarEntry template = found != null && found.isTemplate() ? found : null;
            SeriesPlan plan = SeriesPlan.of(existing, template,
                    entry.getStartMillis() > 0L ? repetitionOf(draft.getRepeat()) : RepetitionOptions.NONE);

            List<TaskSubtask> subtasks = new ArrayList<>();
            for (int i = 0; i < draft.subtasks.size(); i++) {
                TaskDraft.Subtask s = draft.subtasks.get(i);
                subtasks.add(new TaskSubtask(0, s.title, s.done, i));
            }
            // Las ocurrencias de la serie anterior que se van: sus alarmas fuera antes de borrar las filas.
            List<CalendarEntry> doomed = new ArrayList<>();
            if (plan.cutsSeries()) {
                long after = existing.isTemplate() ? now : entry.getStartMillis();
                List<CalendarEntry> later = calendarEntryDao.getUndoneOccurrencesAfter(plan.oldTemplateId, after);
                if (later != null) {
                    for (CalendarEntry e : later) {
                        if (e.getId() != entry.getId()) doomed.add(e);
                    }
                }
                for (CalendarEntry e : doomed) taskAlarms.onTaskClosed(e);
            }
            int[] materialize = {0};
            transaction.run(() -> {
                for (CalendarEntry e : doomed) {
                    subtaskDao.deleteByEntry(e.getId());
                    calendarEntryDao.delete(e);
                }
                if (existing != null && existing.isTemplate()) {
                    // La propia plantilla (ninguna pantalla la abre hoy): su Repetir cambia la serie desde ahora.
                    if (plan.kind == SeriesPlan.Kind.CHANGE) {
                        entry.setRepetition(plan.wanted);
                        entry.setRepetitionInterval(1);
                        entry.setRepetitionDays(0);
                        entry.setMaterializedUntilMillis(Math.max(now, entry.getMaterializedUntilMillis()));
                        if (plan.wanted != RepetitionOptions.NONE) materialize[0] = existing.getId();
                    }
                } else if (plan.kind == SeriesPlan.Kind.END) {
                    template.setRepetition(RepetitionOptions.NONE);
                    calendarEntryDao.update(template);
                    entry.setTemplateId(0);
                } else if (plan.kind == SeriesPlan.Kind.CHANGE) {
                    CalendarEntry changed = seriesTemplate(template, entry.getStartMillis(), plan.wanted);
                    changed.setId(template.getId());
                    calendarEntryDao.update(changed);
                    materialize[0] = changed.getId();
                } else if (plan.kind == SeriesPlan.Kind.START) {
                    int templateId = (int) calendarEntryDao.insert(
                            seriesTemplate(entry, entry.getStartMillis(), plan.wanted));
                    entry.setTemplateId(templateId);
                    materialize[0] = templateId;
                }
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
            // Rellena la serie (y programa los avisos de sus ocurrencias) despues de escribir la tarea.
            if (materialize[0] != 0) materializer.materializeTemplate(materialize[0]);
            if (callback != null) {
                int id = entry.getId();
                mainThread.post(() -> callback.onResult(id));
            }
        });
    }

    /**
     * Lo que un guardado hace con la serie: {@code current} es la frecuencia de la serie de la tarea (la de
     * su plantilla; NONE si no tiene o si la serie termino) y {@code wanted} la del borrador.
     */
    static final class SeriesPlan {
        enum Kind { NONE, START, CHANGE, END }

        final Kind kind;
        final int current;
        final int wanted;
        final int oldTemplateId;

        private SeriesPlan(Kind kind, int current, int wanted, int oldTemplateId) {
            this.kind = kind;
            this.current = current;
            this.wanted = wanted;
            this.oldTemplateId = oldTemplateId;
        }

        static SeriesPlan of(@Nullable CalendarEntry existing, @Nullable CalendarEntry template, int wanted) {
            if (existing != null && existing.isTemplate()) {
                int current = existing.getRepetition();
                return new SeriesPlan(wanted == current ? Kind.NONE : Kind.CHANGE, current, wanted,
                        existing.getId());
            }
            int current = template == null ? RepetitionOptions.NONE : template.getRepetition();
            int oldId = template == null ? 0 : template.getId();
            Kind kind;
            if (wanted == current) {
                kind = Kind.NONE;
            } else if (current == RepetitionOptions.NONE) {
                kind = Kind.START;
            } else if (wanted == RepetitionOptions.NONE) {
                kind = Kind.END;
            } else {
                kind = Kind.CHANGE;
            }
            return new SeriesPlan(kind, current, wanted, oldId);
        }

        /** Las ocurrencias futuras sin hacer de la serie anterior se borran. */
        boolean cutsSeries() {
            return oldTemplateId != 0 && current != RepetitionOptions.NONE
                    && (kind == Kind.CHANGE || kind == Kind.END);
        }
    }

    /**
     * La plantilla de una serie con los datos de {@code fields}, anclada en {@code anchorMillis} (su primera
     * ocurrencia, ya escrita: la marca de agua empieza ahi) y que se repite con {@code repetition} (cada dia,
     * cada semana el dia de la semana del ancla, o cada mes). Sin id. Solo con EventBuilder (regla #4).
     */
    static CalendarEntry seriesTemplate(CalendarEntry fields, long anchorMillis, int repetition) {
        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(anchorMillis);
        boolean[] notifications = fields.getNotifications() == null ? new boolean[7]
                : Arrays.copyOf(fields.getNotifications(), fields.getNotifications().length);
        CalendarEntry template = new CalendarEntry.EventBuilder()
                .setEventType(fields.getType())
                .setEventTitle(fields.getTitle())
                .setEventDescription(fields.getDescription())
                .setEventProjectId(fields.getProjectId())
                .setEventStartDayAndHour(anchor)
                .setEventDuration(fields.getDuration())
                .setEventTargetMinutes(fields.getTargetMinutes())
                .setEventNotifications(notifications)
                .setEventIsDone(false)
                .setEventHiddenInCalendar(fields.isHiddenInCalendar())
                .copyTaskFieldsFrom(fields)
                .setCompletionNote(null)
                .setEventRepetition(repetition)
                .setEventRepetitionInterval(1)
                .setEventRepetitionDays(0)
                .setEventIsTemplate(true)
                .build();
        template.setMaterializedUntilMillis(anchorMillis);
        return template;
    }

    /** Repetir del borrador como {@link RepetitionOptions} (NONE, DAILY, WEEKLY, MONTHLY). */
    public static int repetitionOf(TaskDraft.Repeat repeat) {
        if (repeat == null) return RepetitionOptions.NONE;
        switch (repeat) {
            case DAILY: return RepetitionOptions.DAILY;
            case WEEKLY: return RepetitionOptions.WEEKLY;
            case MONTHLY: return RepetitionOptions.MONTHLY;
            default: return RepetitionOptions.NONE;
        }
    }

    /**
     * El Repetir que muestra el editor para {@code entry}: el de su serie ({@code template}, la plantilla
     * de una ocurrencia; puede ser null), o el suyo si es la plantilla. NONE si no se repite.
     */
    public static TaskDraft.Repeat seriesRepeat(CalendarEntry entry, @Nullable CalendarEntry template) {
        int repetition = RepetitionOptions.NONE;
        if (entry.isTemplate()) {
            repetition = entry.getRepetition();
        } else if (template != null && template.isTemplate() && template.getId() == entry.getTemplateId()) {
            repetition = template.getRepetition();
        }
        switch (repetition) {
            case RepetitionOptions.DAILY: return TaskDraft.Repeat.DAILY;
            case RepetitionOptions.WEEKLY: return TaskDraft.Repeat.WEEKLY;
            case RepetitionOptions.MONTHLY: return TaskDraft.Repeat.MONTHLY;
            default: return TaskDraft.Repeat.NONE;
        }
    }

    // Construye la fila SOLO con CalendarEntry.EventBuilder (regla #4). Al editar, lo que el borrador
    // no maneja aqui (repeticion, plantilla, marcas de materializado, retirada, sello de completado) se
    // conserva de la fila existente; saveDraft aplica despues el Repetir del borrador a la serie.
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

    /**
     * Enfocar una tarea sin meta (decision d9): le activa el pomodoro con su duracion, o 25 min si no
     * tiene ({@link com.example.bbettercalendar.ui.projects.FocusLink#goalForTask}). Con meta no toca
     * nada. {@code callback} recibe la meta resultante (0 si la tarea ya no existe) en el hilo principal.
     */
    public void ensurePomodoro(int entryId, Callback<Integer> callback) {
        dbWriteExecutor.execute(() -> {
            CalendarEntry entry = calendarEntryDao.getEventById(entryId);
            int goal = 0;
            if (entry != null) {
                goal = entry.getTargetMinutes();
                if (goal <= 0) {
                    goal = com.example.bbettercalendar.ui.projects.FocusLink.goalForTask(entry.getDuration());
                    entry.setTargetMinutes(goal);
                    if (entry.getDuration() <= 0) entry.setDuration(goal * 60_000);
                    calendarEntryDao.update(entry);
                }
            }
            int result = goal;
            if (callback != null) mainThread.post(() -> callback.onResult(result));
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
