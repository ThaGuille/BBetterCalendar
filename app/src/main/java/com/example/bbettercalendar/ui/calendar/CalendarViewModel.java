package com.example.bbettercalendar.ui.calendar;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDAO;
import com.example.bbettercalendar.stats.DailyStat;
import com.example.bbettercalendar.stats.DailyStatDAO;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItemMapper;
import com.example.bbettercalendar.ui.calendar.domain.ProjectDeadlineItemMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class CalendarViewModel extends AndroidViewModel {

    public static class DateRange {
        public final long startMillis;
        public final long endMillis;
        public DateRange(long startMillis, long endMillis) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }
    }

    private final CalendarEntryDAO dao;
    private final ProjectDAO projectDao;
    private final MutableLiveData<DateRange> range = new MutableLiveData<>();
    private final LiveData<List<CalendarItem>> items;
    private final DailyStatDAO dailyStatDao;
    private final StatsDAO statsDao;
    private final ExecutorService executor;
    private final MutableLiveData<Map<LocalDate, Integer>> focusMinutes = new MutableLiveData<>(new HashMap<>());

    @Inject
    public CalendarViewModel(@NonNull Application application, CalendarEntryDAO dao,
                              ProjectDAO projectDao, DailyStatDAO dailyStatDao, StatsDAO statsDao,
                              @IoExecutor ExecutorService executor) {
        super(application);
        this.dao = dao;
        this.projectDao = projectDao;
        this.dailyStatDao = dailyStatDao;
        this.statsDao = statsDao;
        this.executor = executor;
        this.items = Transformations.switchMap(range, r -> {
            if (r == null) {
                MutableLiveData<List<CalendarItem>> empty = new MutableLiveData<>();
                empty.setValue(Collections.emptyList());
                return empty;
            }
            return mergedItems(r);
        });
    }

    /**
     * El calendario pinta DOS fuentes (spec project-deadlines-progress): las entradas del rango y
     * los deadlines de los proyectos activos que caen dentro. Las dos son LiveData de Room, así que
     * cada una se invalida sola -- editar un deadline en la pestaña Projects repinta el calendario
     * sin ningún refresh() (ver el invariante de invalidación en data-model.md).
     *
     * Las lambdas del Mediator corren en el hilo principal y sólo componen listas ya cargadas: el
     * trabajo de BD lo hace Room en el suyo, así que setValue es correcto aquí (regla #3).
     */
    private LiveData<List<CalendarItem>> mergedItems(DateRange r) {
        LiveData<List<CalendarEntry>> entries =
                dao.getVisibleEventsBetween(r.startMillis, r.endMillis);
        LiveData<List<Project>> deadlines =
                projectDao.observeDeadlinesBetween(r.startMillis, r.endMillis);

        MediatorLiveData<List<CalendarItem>> merged = new MediatorLiveData<>();
        final List<CalendarItem> entryItems = new ArrayList<>();
        final List<CalendarItem> deadlineItems = new ArrayList<>();

        merged.addSource(entries, list -> {
            entryItems.clear();
            if (list != null) {
                entryItems.addAll(CalendarItemMapper.toItems(list, getApplication()));
            }
            merged.setValue(combine(entryItems, deadlineItems));
        });
        merged.addSource(deadlines, list -> {
            deadlineItems.clear();
            deadlineItems.addAll(ProjectDeadlineItemMapper.toItems(list, getApplication()));
            merged.setValue(combine(entryItems, deadlineItems));
        });
        return merged;
    }

    private static List<CalendarItem> combine(List<CalendarItem> entryItems,
                                              List<CalendarItem> deadlineItems) {
        List<CalendarItem> out = new ArrayList<>(entryItems.size() + deadlineItems.size());
        out.addAll(entryItems);
        out.addAll(deadlineItems);
        Collections.sort(out, (a, b) -> Long.compare(a.getStartMillis(), b.getStartMillis()));
        return out;
    }

    public void setRange(long startMillis, long endMillis) {
        DateRange current = range.getValue();
        if (current != null && current.startMillis == startMillis && current.endMillis == endMillis) {
            return;
        }
        range.setValue(new DateRange(startMillis, endMillis));
        loadFocusMinutes(startMillis, endMillis);
    }

    /**
     * Minutos de enfoque por día del rango visible (mejora-visual u12): daily_stat para los días
     * ya volcados y, para hoy (que se vuelca en el reset del día siguiente), el valor vivo de Stats.
     * DB en el executor, publicado con postValue (regla #3).
     */
    private void loadFocusMinutes(long startMillis, long endMillis) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        LocalDate start = java.time.Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate();
        LocalDate end = java.time.Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate();
        executor.execute(() -> {
            Map<LocalDate, Integer> out = new HashMap<>();
            List<DailyStat> rows = dailyStatDao.getRange(start.toString(), end.toString());
            if (rows != null) {
                for (DailyStat ds : rows) {
                    try {
                        out.put(LocalDate.parse(ds.day), ds.focusMinutes);
                    } catch (RuntimeException ignored) {
                        // fila con día mal formado: se ignora
                    }
                }
            }
            LocalDate today = LocalDate.now();
            if (!today.isBefore(start) && !today.isAfter(end)) {
                Stats live = statsDao.getStats();
                if (live != null) {
                    out.put(today, FormatHelper.millisToMinutes(live.todayTimeStudied));
                }
            }
            focusMinutes.postValue(out);
        });
    }

    public LiveData<Map<LocalDate, Integer>> getFocusMinutes() {
        return focusMinutes;
    }

    public LiveData<List<CalendarItem>> getItems() {
        return items;
    }
}
