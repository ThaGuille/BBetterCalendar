package com.example.bbettercalendar.ui.projects;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;
import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectWithCounts;
import com.example.bbettercalendar.projects.ProjectsRepository;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.FocusInsightsRepository;
import com.example.bbettercalendar.stats.ProjectMinutes;
import com.example.bbettercalendar.stats.StreakCalculator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

// Lista de proyectos (spec projects-mvp; repository-layer-consolidation T2). El % de cada
// proyecto llega ya resuelto en ProjectsRepository.observeAllWithCounts() (una unica query Room
// con JOIN via subqueries correladas); Room invalida sola ante cualquier escritura en project O
// calendarEntry, sin Observer manual ni refresh()/onResume().
// u16: la lista se compone con otras tres fuentes reactivas (items de todos los proyectos, minutos
// de foco de hoy por proyecto, duracion de pomodoro). Todo es composicion en el hilo principal
// sobre datos ya en memoria; la unica I/O propia (leer la configuracion) va en un executor.
@HiltViewModel
public class ProjectsViewModel extends AndroidViewModel {

    /** Resumen de la cabecera de la lista (html 03: "4 active - 2 due this week" + racha). */
    public static final class Summary {
        public final int active;
        public final int dueThisWeek;
        public final int streakDays;

        Summary(int active, int dueThisWeek, int streakDays) {
            this.active = active;
            this.dueThisWeek = dueThisWeek;
            this.streakDays = streakDays;
        }
    }

    private static final long WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final ProjectsRepository repository;
    private final CalendarEntryDAO calendarEntryDao;
    private final ExecutorService dbWriteExecutor;
    private final MediatorLiveData<List<ProjectListItem>> projectItems = new MediatorLiveData<>();
    private final MediatorLiveData<Summary> summary = new MediatorLiveData<>();
    private final MutableLiveData<Integer> pomodoroMinutes =
            new MutableLiveData<>(ProjectListItem.DEFAULT_POMODORO_MINUTES);

    private List<ProjectWithCounts> latestProjects;
    private List<CalendarEntry> latestItems;
    private List<ProjectMinutes> latestToday;
    private StreakCalculator.Result latestStreak;

    @Inject
    public ProjectsViewModel(@NonNull Application application, ProjectsRepository repository,
                             CalendarEntryDAO calendarEntryDao, FocusEventDAO focusEventDao,
                             FocusInsightsRepository focusInsightsRepository,
                             ConfigurationManager configurationManager,
                             @DbWriteExecutor ExecutorService dbWriteExecutor,
                             @IoExecutor ExecutorService ioExecutor) {
        super(application);
        this.repository = repository;
        this.calendarEntryDao = calendarEntryDao;
        this.dbWriteExecutor = dbWriteExecutor;

        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        long dayStart = start.getTimeInMillis();
        long dayEnd = dayStart + 24L * 60 * 60 * 1000 - 1;

        projectItems.addSource(repository.observeAllWithCounts(), value -> {
            latestProjects = value;
            recompose();
        });
        projectItems.addSource(calendarEntryDao.observeAllProjectItems(), value -> {
            latestItems = value;
            recompose();
        });
        projectItems.addSource(focusEventDao.observeMinutesByProject(dayStart, dayEnd), value -> {
            latestToday = value;
            recompose();
        });
        projectItems.addSource(pomodoroMinutes, value -> recompose());

        summary.addSource(projectItems, value -> recomposeSummary());
        summary.addSource(focusInsightsRepository.observeStreak(), value -> {
            latestStreak = value;
            recomposeSummary();
        });

        // ConfigurationManager.getConfiguration() puede tocar disco: fuera del hilo principal.
        ioExecutor.execute(() -> {
            Configuration configuration = configurationManager.getConfiguration();
            int minutes = configuration == null ? 0 : configuration.getHomeTimerTime() / 60000;
            if (minutes > 0) {
                pomodoroMinutes.postValue(minutes);
            }
        });
    }

    private void recompose() {
        if (latestProjects == null) {
            return;
        }
        Map<Integer, List<CalendarEntry>> itemsByProject = new HashMap<>();
        if (latestItems != null) {
            for (CalendarEntry entry : latestItems) {
                List<CalendarEntry> bucket = itemsByProject.get(entry.getProjectId());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    itemsByProject.put(entry.getProjectId(), bucket);
                }
                bucket.add(entry);
            }
        }
        Map<Integer, Integer> todayByProject = new HashMap<>();
        if (latestToday != null) {
            for (ProjectMinutes pm : latestToday) {
                todayByProject.put(pm.projectId, pm.minutes);
            }
        }
        Integer pomodoro = pomodoroMinutes.getValue();
        int pomodoroMin = pomodoro == null ? ProjectListItem.DEFAULT_POMODORO_MINUTES : pomodoro;

        List<ProjectListItem> result = new ArrayList<>();
        for (ProjectWithCounts pwc : latestProjects) {
            List<CalendarEntry> subtasks = itemsByProject.get(pwc.project.id);
            if (subtasks == null) {
                subtasks = new ArrayList<>();
            }
            Integer today = todayByProject.get(pwc.project.id);
            result.add(new ProjectListItem(pwc.project, pwc.doneCount, pwc.totalCount,
                    subtasks, pomodoroMin, today == null ? 0 : today));
        }
        projectItems.setValue(result);
    }

    private void recomposeSummary() {
        List<ProjectListItem> items = projectItems.getValue();
        if (items == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int active = 0;
        int due = 0;
        for (ProjectListItem item : items) {
            Project project = item.project;
            if (project.status != Project.STATUS_ACTIVE) {
                continue;
            }
            active++;
            long deadline = project.softDeadlineMillis;
            if (deadline >= now && deadline - now <= WEEK_MILLIS) {
                due++;
            }
        }
        summary.setValue(new Summary(active, due,
                latestStreak == null ? 0 : latestStreak.currentStreak));
    }

    public LiveData<List<ProjectListItem>> getProjects() {
        return projectItems;
    }

    public LiveData<Summary> getSummary() {
        return summary;
    }

    public void createProject(String name, String notes, long softDeadlineMillis) {
        repository.createProject(name, notes, softDeadlineMillis);
    }

    /** Checkbox de una subtarea dentro de la tarjeta (mismo patron que ProjectDetailViewModel). */
    public void setItemDone(CalendarEntry entry, boolean done) {
        dbWriteExecutor.execute(() -> {
            CalendarEntry fresh = calendarEntryDao.getEventById(entry.getId());
            if (fresh == null) {
                return;
            }
            fresh.setDone(done);
            calendarEntryDao.update(fresh);
        });
    }
}
