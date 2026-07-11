package com.example.bbettercalendar.ui.home;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;
import com.example.bbettercalendar.configuration.InitialConfiguration;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.stats.FocusEvent;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

public class HomeViewModel extends AndroidViewModel {

    private final String TAG = "HomeFragmentTag";
    private final MutableLiveData<String> mText;
    private final MutableLiveData<String> timerText;
    private final MutableLiveData<String> currentStreakText;
    private final MutableLiveData<String> todayFailsText;
    private final MutableLiveData<String> todayTimeStudiedText;
    private final MutableLiveData<String> timerModeText;
    private String currentStreakString;
    private ExecutorService executorService;
    private StatsDAO statsDao;
    private FocusEventDAO focusEventDao;
    private CalendarEntryDAO calendarEntryDao;
    public ConfigurationManager configManager;

    // --- "Today" task list (spec tasks-home-today) ---
    // Trigger [startOfToday, endOfToday]: re-emitirlo desde onResume fuerza a switchMap a
    // reconstruir la query Room (mismo workaround que CalendarViewModel.refresh() para el
    // lag del InvalidationTracker tras insertar desde otra pantalla) y cubre el cambio de día.
    private final MutableLiveData<long[]> todayRange = new MutableLiveData<>();
    // Instante "antes de hoy" para la sección de atrasadas; null = sección plegada, sin query.
    private final MutableLiveData<Long> overdueBefore = new MutableLiveData<>();
    private final LiveData<List<CalendarEntry>> todayTasks;
    private final LiveData<List<CalendarEntry>> overdueTasks;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        mText = new MutableLiveData<>();
        timerText = new MutableLiveData<>();
        currentStreakText = new MutableLiveData<>();
        todayFailsText = new MutableLiveData<>();
        todayTimeStudiedText = new MutableLiveData<>();
        timerModeText = new MutableLiveData<>();
        mText.setValue("This is home fragment");
        timerText.setValue("20:00");
        AppDatabase db = AppDatabase.getDatabase(application);
        statsDao = db.statsDao();
        focusEventDao = db.focusEventDao();
        calendarEntryDao = db.eventDao();
        executorService = Executors.newFixedThreadPool(2);

        todayTasks = Transformations.switchMap(todayRange, range -> {
            if (range == null) {
                return emptyEntryList();
            }
            return Transformations.map(
                    calendarEntryDao.getEventsBetween(range[0], range[1]),
                    HomeViewModel::filterAndSortTasks);
        });
        overdueTasks = Transformations.switchMap(overdueBefore, before -> {
            if (before == null) {
                return emptyEntryList();
            }
            return calendarEntryDao.getUndoneTasksBefore(before);
        });
        refreshToday();


        // Observador que se activa cuando la base de datos se ha inicializado en la clase InitialConfiguration
        /*Observer<Boolean> initializationObserver = new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean initialized) {
                if (initialized) {
                    executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                Stats initialStats = statsDao.getStats();
                                setInitialTexts(initialStats);
                        }
                    });
                    // Elimina el observador una vez que la condición se ha cumplido
                    InitialConfiguration.getInstance().getInitializationStatus().removeObserver(this);
                }
            }
        };
        InitialConfiguration.getInstance().getInitializationStatus().observeForever(initializationObserver);*/
        executorService.execute(() -> {
            Stats initialStats = statsDao.getStats();
            setInitialTexts(initialStats);
        });

    }

    private void setInitialTexts(Stats initialStats){
        Log.i(TAG, "View Model setInitialTexts()");
        // Valores "pelados" (sin frase): la fila compacta de stats ya pinta su label al lado
        // (spec tasks-home-today) y la frase completa duplicaba esa etiqueta.
        currentStreakText.postValue(String.valueOf(initialStats.currentStreak));
        todayFailsText.postValue(String.valueOf(initialStats.todayFails));
        String formattedTime = FormatHelper.formatTime(initialStats.todayTimeStudied, "HH:mm");
        todayTimeStudiedText.postValue(formattedTime);
        timerModeText.postValue("-- Concentration --");
    }

    // ------------------------ "Today" task list (spec tasks-home-today) ------------------------

    /** Solo tareas, no hechas primero, y dentro de cada grupo por hora ascendente. */
    private static List<CalendarEntry> filterAndSortTasks(List<CalendarEntry> entries) {
        List<CalendarEntry> tasks = new ArrayList<>();
        if (entries != null) {
            for (CalendarEntry entry : entries) {
                if (entry.getType() == AddEventActivity.TYPE_TASK) {
                    tasks.add(entry);
                }
            }
        }
        Collections.sort(tasks, (a, b) -> {
            if (a.isDone() != b.isDone()) {
                return a.isDone() ? 1 : -1;
            }
            return Long.compare(a.getStartMillis(), b.getStartMillis());
        });
        return tasks;
    }

    private static LiveData<List<CalendarEntry>> emptyEntryList() {
        MutableLiveData<List<CalendarEntry>> empty = new MutableLiveData<>();
        empty.setValue(Collections.emptyList());
        return empty;
    }

    /** Recalcula el rango de hoy y re-dispara las queries. Llamar desde onResume (main thread). */
    public void refreshToday() {
        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        long startOfToday = day.getTimeInMillis();
        long endOfToday = startOfToday + 24L * 60 * 60 * 1000 - 1;
        todayRange.setValue(new long[]{startOfToday, endOfToday});
        if (overdueBefore.getValue() != null) {
            overdueBefore.setValue(startOfToday);
        }
    }

    /** La sección de atrasadas solo mantiene query Room viva mientras está desplegada. */
    public void setOverdueVisible(boolean visible) {
        if (!visible) {
            overdueBefore.setValue(null);
            return;
        }
        long[] range = todayRange.getValue();
        overdueBefore.setValue(range != null ? range[0] : null);
    }

    public void setTaskDone(CalendarEntry entry, boolean done) {
        executorService.execute(() -> {
            // Releer la fila en vez de mutar desde este hilo la instancia que el adapter
            // tiene bindeada en el main thread. No hace falta postValue: la LiveData de
            // Room re-emite sola al invalidarse la tabla.
            CalendarEntry fresh = calendarEntryDao.getEventById(entry.getId());
            if (fresh == null) {
                return;
            }
            fresh.setDone(done);
            calendarEntryDao.update(fresh);
        });
    }

    public void quickAddTask(String title, Calendar startDayAndHour) {
        CalendarEntry task = new CalendarEntry.EventBuilder()
                .setEventType(AddEventActivity.TYPE_TASK)  // build() no pone defaults (regla #4)
                .setEventTitle(title)
                .setEventStartDayAndHour(startDayAndHour)
                .setEventIsDone(false)
                .build();
        executorService.execute(() -> calendarEntryDao.insert(task));
    }

    public LiveData<List<CalendarEntry>> getTodayTasks() {
        return todayTasks;
    }

    public LiveData<List<CalendarEntry>> getOverdueTasks() {
        return overdueTasks;
    }

    //Cuando el usuario cierra la app a medio contador
    public void addFails() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "View Model addFails()");
                statsDao.addFails();
                logFocusEvent(FocusEvent.TYPE_FAIL, 0);
                todayFailsText.postValue(String.valueOf(statsDao.getTodayFails()));
            }
        });
    }

    //Cuando el temporizador llega a 0 se actualizan y guardan estadísticas
    public void completeTimer(int timerTime){
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "View Model addTimeStudied( " + timerTime + " )");
                statsDao.addTimeStudied(timerTime);
                statsDao.addTasksDone();
                logFocusEvent(FocusEvent.TYPE_FOCUS, FormatHelper.millisToMinutes(timerTime));
                String formattedTime = FormatHelper.formatTime(statsDao.getTodayTimeStudied(), "HH:mm");
                todayTimeStudiedText.postValue(formattedTime);
            }
        });
    }

    // Registra un evento con timestamp para el histórico por horas de Progress.
    // Se llama desde dentro del executorService, así que ya está fuera del hilo principal.
    private void logFocusEvent(int type, int durationMin){
        FocusEvent ev = new FocusEvent();
        ev.timestamp = System.currentTimeMillis();
        ev.type = type;
        ev.durationMin = durationMin;
        focusEventDao.insert(ev);
    }

    public void setRestTimer(){
        timerText.postValue(FormatHelper.formatTime(configManager.getConfiguration().getHomeRestTime(), "mm:ss"));
        timerModeText.postValue("-- Rest --");
    }

    public void resetTimer(){
        timerText.postValue(FormatHelper.formatTime(configManager.getConfiguration().getHomeTimerTime(), "mm:ss"));
        timerModeText.postValue("-- Concentration --");
    }

    public void setConfigManager(ConfigurationManager configManager) {
        this.configManager = configManager;
        timerText.postValue(FormatHelper.formatTime(configManager.getConfiguration().getHomeTimerTime(), "mm:ss"));
    }

    public void updateConfiguration(Configuration config){
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                configManager.updateConfiguration(config);
                updateConfigurationUIValues();
            }
        });
    }

    private void updateConfigurationUIValues(){
        timerText.postValue(FormatHelper.formatTime(configManager.getConfiguration().getHomeTimerTime(), "mm:ss"));
        //todo update other values
    }

    public LiveData<String> getText() {
        return mText;
    }
    public LiveData<String> getTimerText() {
        return timerText;
    }


    public LiveData<String> getCurrentStreakText() {return currentStreakText;}
    public LiveData<String> getTodayFailsText() {return todayFailsText;}
    public LiveData<String> getTodayTimeStudiedText() {return todayTimeStudiedText;}
    public LiveData<String> getTimerModeText() {return timerModeText;}

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}