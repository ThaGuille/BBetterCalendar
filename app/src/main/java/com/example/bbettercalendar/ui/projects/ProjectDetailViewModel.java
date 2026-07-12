package com.example.bbettercalendar.ui.projects;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDAO;
import com.example.bbettercalendar.stats.AttributedMinutes;
import com.example.bbettercalendar.stats.FocusEventDAO;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProjectDetailViewModel extends AndroidViewModel {

    private final ExecutorService executorService;
    private final ProjectDAO projectDao;
    private final CalendarEntryDAO calendarEntryDao;
    private final FocusEventDAO focusEventDao;

    private final MutableLiveData<Integer> projectIdLiveData = new MutableLiveData<>();
    private final LiveData<Project> project;
    private final LiveData<List<CalendarEntry>> items;
    // Items enriquecidos con los minutos atribuidos por item (spec focus-attribution), calculados
    // fuera del hilo principal. Es la que observa el fragment.
    private final MediatorLiveData<List<CalendarEntry>> itemsEnriched = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> projectDeleted = new MutableLiveData<>();

    public ProjectDetailViewModel(@NonNull Application application) {
        super(application);
        executorService = Executors.newSingleThreadExecutor();
        AppDatabase db = AppDatabase.getDatabase(application);
        projectDao = db.projectDao();
        calendarEntryDao = db.eventDao();
        focusEventDao = db.focusEventDao();

        project = Transformations.switchMap(projectIdLiveData, id ->
                id == null ? emptyProject() : projectDao.observeById(id));
        items = Transformations.switchMap(projectIdLiveData, id ->
                id == null ? emptyEntryList() : calendarEntryDao.observeItemsByProject(id));
        itemsEnriched.addSource(items, this::enrichWithAttributedMinutes);
    }

    // Rellena CalendarEntry.attributedMinutes (transitorio) para los items con objetivo, con una
    // sola query agrupada, fuera del hilo principal.
    private void enrichWithAttributedMinutes(List<CalendarEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            itemsEnriched.setValue(entries);
            return;
        }
        executorService.execute(() -> {
            List<AttributedMinutes> sums = focusEventDao.getAttributedMinutesByEntry();
            Map<Integer, Integer> byId = new HashMap<>();
            for (AttributedMinutes am : sums) {
                byId.put(am.entryId, am.minutes);
            }
            for (CalendarEntry e : entries) {
                if (e.getTargetMinutes() > 0) {
                    Integer m = byId.get(e.getId());
                    e.setAttributedMinutes(m == null ? 0 : m);
                }
            }
            itemsEnriched.postValue(entries);
        });
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
        executorService.execute(() -> {
            CalendarEntry fresh = calendarEntryDao.getEventById(entry.getId());
            if (fresh == null) {
                return;
            }
            fresh.setDone(done);
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
        executorService.execute(() -> calendarEntryDao.insert(entry));
    }

    public void updateHeader(String name, String notes) {
        int projectId = requireProjectId();
        executorService.execute(() -> {
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
        executorService.execute(() -> {
            Project fresh = projectDao.getById(projectId);
            if (fresh == null) {
                return;
            }
            fresh.softDeadlineMillis = softDeadlineMillis;
            projectDao.update(fresh);
        });
    }

    /** Transición de status (spec projects-mvp decisión #2) — nunca borra filas, sólo las retiene. */
    public void completeProject() {
        int projectId = requireProjectId();
        executorService.execute(() -> {
            Project fresh = projectDao.getById(projectId);
            if (fresh == null) {
                return;
            }
            fresh.status = Project.STATUS_COMPLETED;
            fresh.completedAtMillis = System.currentTimeMillis();
            projectDao.update(fresh);
        });
    }

    /** Cascade manual (decisión #3) — único camino que borra items de verdad. */
    public void deleteProject() {
        int projectId = requireProjectId();
        executorService.execute(() -> {
            calendarEntryDao.deleteItemsByProject(projectId);
            projectDao.deleteById(projectId);
            projectDeleted.postValue(true);
        });
    }

    private int requireProjectId() {
        Integer id = projectIdLiveData.getValue();
        return id == null ? 0 : id;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
