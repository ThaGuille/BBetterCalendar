package com.example.bbettercalendar.ui.projects;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.calendarEntries.CompletionStamp;
import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.notifications.project.ProjectDeadlineScheduler;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDAO;
import com.example.bbettercalendar.stats.FocusAttributionRepository;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.ProjectMinutes;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ProjectDetailViewModel extends AndroidViewModel {

    private final ExecutorService dbWriteExecutor;
    private final ProjectDAO projectDao;
    private final CalendarEntryDAO calendarEntryDao;
    private final ProjectDeadlineScheduler deadlineScheduler;

    private final MutableLiveData<Integer> projectIdLiveData = new MutableLiveData<>();
    private final LiveData<Project> project;
    private final LiveData<List<CalendarEntry>> items;
    // Items enriquecidos con los minutos atribuidos por item (spec focus-attribution), ahora
    // observando también focus_event (spec repository-layer-consolidation). Es la que observa
    // el fragment.
    private final LiveData<List<CalendarEntry>> itemsEnriched;
    private final MutableLiveData<Boolean> projectDeleted = new MutableLiveData<>();
    // u11: minutos de foco del proyecto, de por vida y desde el inicio de esta semana ("Enfocado").
    private final LiveData<Integer> focusedTotalMinutes;
    private final LiveData<Integer> focusedWeekMinutes;

    @Inject
    public ProjectDetailViewModel(@NonNull Application application, ProjectDAO projectDao,
                                   CalendarEntryDAO calendarEntryDao,
                                   FocusAttributionRepository focusAttributionRepository,
                                   FocusEventDAO focusEventDao,
                                   ProjectDeadlineScheduler deadlineScheduler,
                                   @DbWriteExecutor ExecutorService dbWriteExecutor) {
        super(application);
        this.dbWriteExecutor = dbWriteExecutor;
        this.projectDao = projectDao;
        this.calendarEntryDao = calendarEntryDao;
        this.deadlineScheduler = deadlineScheduler;

        project = Transformations.switchMap(projectIdLiveData, id ->
                id == null ? emptyProject() : projectDao.observeById(id));
        items = Transformations.switchMap(projectIdLiveData, id ->
                id == null ? emptyEntryList() : calendarEntryDao.observeItemsByProject(id));
        itemsEnriched = focusAttributionRepository.enrich(items);

        Calendar weekStart = Calendar.getInstance();
        weekStart.set(Calendar.DAY_OF_WEEK, weekStart.getFirstDayOfWeek());
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);
        long weekStartMillis = weekStart.getTimeInMillis();
        focusedTotalMinutes = Transformations.switchMap(projectIdLiveData, id ->
                minutesOf(id, focusEventDao.observeMinutesByProject(0L, Long.MAX_VALUE)));
        focusedWeekMinutes = Transformations.switchMap(projectIdLiveData, id ->
                minutesOf(id, focusEventDao.observeMinutesByProject(weekStartMillis, Long.MAX_VALUE)));
    }

    private static LiveData<Integer> minutesOf(Integer projectId, LiveData<List<ProjectMinutes>> source) {
        return Transformations.map(source, list -> {
            if (list != null && projectId != null) {
                for (ProjectMinutes pm : list) {
                    if (pm.projectId == projectId) {
                        return pm.minutes;
                    }
                }
            }
            return 0;
        });
    }

    public LiveData<Integer> getFocusedTotalMinutes() {
        return focusedTotalMinutes;
    }

    public LiveData<Integer> getFocusedWeekMinutes() {
        return focusedWeekMinutes;
    }

    private static LiveData<Project> emptyProject() {
        return new MutableLiveData<>();
    }

    private static LiveData<List<CalendarEntry>> emptyEntryList() {
        MutableLiveData<List<CalendarEntry>> empty = new MutableLiveData<>();
        empty.setValue(Collections.emptyList());
        return empty;
    }

    /** No-op si ya apunta al mismo proyecto — evita reiniciar las queries en cada onViewCreated. */
    public void setProjectId(int projectId) {
        if (projectIdLiveData.getValue() == null || projectIdLiveData.getValue() != projectId) {
            projectIdLiveData.setValue(projectId);
        }
    }

    public LiveData<Project> getProject() {
        return project;
    }

    public LiveData<List<CalendarEntry>> getItems() {
        return itemsEnriched;
    }

    public LiveData<Boolean> getProjectDeleted() {
        return projectDeleted;
    }

    public void setItemDone(CalendarEntry entry, boolean done) {
        dbWriteExecutor.execute(() -> {
            CalendarEntry fresh = calendarEntryDao.getEventById(entry.getId());
            if (fresh == null) {
                return;
            }
            CompletionStamp.apply(fresh, done, System.currentTimeMillis());
            calendarEntryDao.update(fresh);
        });
    }

    /** Item de proyecto (spec projects-mvp): startDayAndHour null -> undated, vive sólo aquí. */
    public void addItem(String title, Calendar startDayAndHour, int targetMinutes) {
        int projectId = requireProjectId();
        CalendarEntry.EventBuilder builder = new CalendarEntry.EventBuilder()
                .setEventType(AddEventActivity.TYPE_TASK)
                .setEventTitle(title)
                .setEventTargetMinutes(targetMinutes)
                .setEventIsDone(false)
                .setEventProjectId(projectId);
        if (startDayAndHour != null) {
            builder.setEventStartDayAndHour(startDayAndHour);
        }
        CalendarEntry entry = builder.build();
        dbWriteExecutor.execute(() -> calendarEntryDao.insert(entry));
    }

    public void updateHeader(String name, String notes) {
        int projectId = requireProjectId();
        dbWriteExecutor.execute(() -> {
            Project fresh = projectDao.getById(projectId);
            if (fresh == null) {
                return;
            }
            fresh.name = name;
            fresh.notes = notes;
            projectDao.update(fresh);
        });
    }

    public void updateDeadline(long softDeadlineMillis) {
        int projectId = requireProjectId();
        dbWriteExecutor.execute(() -> {
            Project fresh = projectDao.getById(projectId);
            if (fresh == null) {
                return;
            }
            fresh.softDeadlineMillis = softDeadlineMillis;
            projectDao.update(fresh);
            // Cancelar SIEMPRE antes de reprogramar: las alarmas del deadline anterior siguen
            // armadas y dispararían fuera de sitio (o, si el deadline se borró, sin deadline).
            // scheduleFor() no hace nada si softDeadlineMillis <= 0, así que borrar sólo cancela.
            deadlineScheduler.cancelFor(projectId);
            deadlineScheduler.scheduleFor(fresh);
        });
    }

    /** Transición de status (spec projects-mvp decisión #2) — nunca borra filas, sólo las retiene. */
    public void completeProject() {
        int projectId = requireProjectId();
        dbWriteExecutor.execute(() -> {
            Project fresh = projectDao.getById(projectId);
            if (fresh == null) {
                return;
            }
            fresh.status = Project.STATUS_COMPLETED;
            fresh.completedAtMillis = System.currentTimeMillis();
            projectDao.update(fresh);
            // Un proyecto ya terminado no debe seguir avisando de su deadline (decisión #11).
            deadlineScheduler.cancelFor(projectId);
        });
    }

    /** Cascade manual (decisión #3) — único camino que borra items de verdad. */
    public void deleteProject() {
        int projectId = requireProjectId();
        dbWriteExecutor.execute(() -> {
            calendarEntryDao.deleteItemsByProject(projectId);
            projectDao.deleteById(projectId);
            // La fila ya no existe: sin esto el receiver recibiría la alarma y la descartaría, pero
            // la PendingIntent seguiría viva en AlarmManager hasta su hora.
            deadlineScheduler.cancelFor(projectId);
            projectDeleted.postValue(true);
        });
    }

    private int requireProjectId() {
        Integer id = projectIdLiveData.getValue();
        return id == null ? 0 : id;
    }

    // dbWriteExecutor es el @DbWriteExecutor compartido de la app (Hilt @Singleton) -- ya no se
    // cierra aquí; onCleared() no tiene nada más que liberar.
}
