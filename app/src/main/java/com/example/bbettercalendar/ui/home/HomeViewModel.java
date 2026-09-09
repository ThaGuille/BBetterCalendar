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
import com.example.bbettercalendar.calendarEntries.RecurrenceMaterializer;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;
import com.example.bbettercalendar.configuration.InitialConfiguration;
import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.popups.RepetitionSpec;
import com.example.bbettercalendar.stats.FocusAttributionRepository;
import com.example.bbettercalendar.stats.FocusEvent;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class HomeViewModel extends AndroidViewModel {

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private final String TAG = "HomeFragmentTag";
    private final MutableLiveData<String> mText;
    private final MutableLiveData<String> timerText;
    private final MutableLiveData<String> todayFailsText;
    private final MutableLiveData<String> todayTimeStudiedText;
    private final MutableLiveData<String> timerModeText;
    private ExecutorService ioExecutor;
    private ExecutorService dbWriteExecutor;
    private StatsDAO statsDao;
    private FocusEventDAO focusEventDao;
    private CalendarEntryDAO calendarEntryDao;
    private FocusAttributionRepository focusAttributionRepository;
    public ConfigurationManager configManager;

    // --- "Today" task list (spec tasks-home-today) ---
    // Trigger [startOfToday, endOfToday]: sólo se re-emite cuando el día cambia con la app
    // abierta (ver refreshToday()) -- el propio LiveData de Room ya se invalida sola en
    // cualquier escritura a calendarEntry, así que no hace falta forzarla desde onResume
    // (repository-layer-consolidation, T2: ver data-model.md invariante de invalidación).
    private final MutableLiveData<long[]> todayRange = new MutableLiveData<>();
    // Instante "antes de hoy" para la sección de atrasadas; null = sección plegada, sin query.
    private final MutableLiveData<Long> overdueBefore = new MutableLiveData<>();
    private final LiveData<List<CalendarEntry>> todayTasks;
    // Lista de hoy enriquecida con los minutos atribuidos por tarea (spec focus-attribution),
    // ahora observando también focus_event (spec repository-layer-consolidation) -- es la que
    // observa el fragment.
    private final LiveData<List<CalendarEntry>> todayTasksEnriched;
    private final LiveData<List<CalendarEntry>> overdueTasks;

    // --- Racha "días con pomodoro" (spec focus-mode-and-streak) ---
    // Inicio de la ventana de 30 días (00:00 de hace 29 días). Es una LiveData y no una constante
    // porque la ventana se desliza: refreshToday() la reajusta cuando el día cambia con la app
    // abierta, igual que hace con todayRange.
    private final MutableLiveData<Long> streakWindowStart = new MutableLiveData<>();
    private final LiveData<Integer> activeDaysLast30;
    // Evento de un solo disparo: nº de días con pomodoro de ESTE MES, emitido sólo cuando la
    // sesión recién completada es la primera del día. null = nada pendiente que celebrar.
    private final MutableLiveData<Integer> firstFocusOfDay = new MutableLiveData<>();

    @Inject
    public HomeViewModel(@NonNull Application application, StatsDAO statsDao, FocusEventDAO focusEventDao,
                         CalendarEntryDAO calendarEntryDao, ConfigurationManager configManager,
                         FocusAttributionRepository focusAttributionRepository,
                         @IoExecutor ExecutorService ioExecutor,
                         @DbWriteExecutor ExecutorService dbWriteExecutor) {
        super(application);
        mText = new MutableLiveData<>();
        timerText = new MutableLiveData<>();
        todayFailsText = new MutableLiveData<>();
        todayTimeStudiedText = new MutableLiveData<>();
        timerModeText = new MutableLiveData<>();
        // R.string.home_greeting ("Hi there") ya existía sin usar; el literal anterior era un
        // placeholder de plantilla que se estaba mostrando en producción.
        mText.setValue(application.getString(com.example.bbettercalendar.R.string.home_greeting));
        timerText.setValue("20:00");
        this.statsDao = statsDao;
        this.focusEventDao = focusEventDao;
        this.calendarEntryDao = calendarEntryDao;
        this.focusAttributionRepository = focusAttributionRepository;
        this.ioExecutor = ioExecutor;
        this.dbWriteExecutor = dbWriteExecutor;
        // HomeFragment inyecta su propio ConfigurationManager y lo vuelve a fijar vía
        // setConfigManager() (mismo Singleton de Hilt) -- se mantiene por compatibilidad, ver ese
        // método más abajo.
        this.configManager = configManager;

        todayTasks = Transformations.switchMap(todayRange, range -> {
            if (range == null) {
                return emptyEntryList();
            }
            return Transformations.map(
                    calendarEntryDao.getEventsBetween(range[0], range[1]),
                    HomeViewModel::filterAndSortTasks);
        });
        todayTasksEnriched = focusAttributionRepository.enrich(todayTasks);
        overdueTasks = Transformations.switchMap(overdueBefore, before -> {
            if (before == null) {
                return emptyEntryList();
            }
            return Transformations.map(
                    calendarEntryDao.getUndoneTasksBefore(before),
                    HomeViewModel::collapseOverdue);
        });
        // La query sólo se re-suscribe cuando la ventana se mueve (cambio de día); dentro de la
        // misma ventana la LiveData de Room ya se reemite sola al insertar un FocusEvent.
        activeDaysLast30 = Transformations.switchMap(streakWindowStart, since -> {
            if (since == null) {
                return zeroLive();
            }
            return focusEventDao.observeActiveDaysSince(since);
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
        ioExecutor.execute(() -> {
            Stats initialStats = statsDao.getStats();
            setInitialTexts(initialStats);
        });

    }

    private void setInitialTexts(Stats initialStats){
        Log.i(TAG, "View Model setInitialTexts()");
        // Valores "pelados" (sin frase): la fila compacta de stats ya pinta su label al lado
        // (spec tasks-home-today) y la frase completa duplicaba esa etiqueta.
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

    /**
     * Colapsa la lista de atrasadas (spec tasks-recurrence): una serie recurrente ignorada N días
     * no debe apilar N filas. Las ocurrencias de una misma plantilla se reducen a UNA fila
     * representante (la más antigua no hecha) que lleva el nº de días fallados; las tareas sueltas
     * (templateId == 0) se muestran individualmente. Entrada ya ordenada por startMillis ASC.
     */
    // Package-private (no private) para poder cubrirla con tests JVM — ver HomeViewModelCollapseTest.
    static List<CalendarEntry> collapseOverdue(List<CalendarEntry> entries) {
        List<CalendarEntry> result = new ArrayList<>();
        if (entries == null) {
            return result;
        }
        Map<Integer, CalendarEntry> firstByTemplate = new LinkedHashMap<>();
        Map<Integer, Integer> countByTemplate = new HashMap<>();
        for (CalendarEntry e : entries) {
            int templateId = e.getTemplateId();
            if (templateId == 0) {
                result.add(e); // tarea suelta atrasada: fila propia
                continue;
            }
            if (!firstByTemplate.containsKey(templateId)) {
                firstByTemplate.put(templateId, e);
            }
            Integer count = countByTemplate.get(templateId);
            countByTemplate.put(templateId, count == null ? 1 : count + 1);
        }
        for (Map.Entry<Integer, CalendarEntry> en : firstByTemplate.entrySet()) {
            CalendarEntry representative = en.getValue();
            representative.setSeriesMissedCount(countByTemplate.get(en.getKey()));
            result.add(representative);
        }
        Collections.sort(result,
                (a, b) -> Long.compare(a.getStartMillis(), b.getStartMillis()));
        return result;
    }

    private static LiveData<Integer> zeroLive() {
        MutableLiveData<Integer> zero = new MutableLiveData<>();
        zero.setValue(0);
        return zero;
    }

    private static LiveData<List<CalendarEntry>> emptyEntryList() {
        MutableLiveData<List<CalendarEntry>> empty = new MutableLiveData<>();
        empty.setValue(Collections.emptyList());
        return empty;
    }

    /**
     * Recalcula [startOfToday, endOfToday] y sólo re-dispara las queries si el día cambió --
     * la lista de hoy en sí ya se re-emite sola en cualquier inserción/actualización de
     * calendarEntry (LiveData de Room, auto-invalidada). Llamar desde onResume (main thread).
     */
    public void refreshToday() {
        long startOfToday = startOfTodayMillis();
        long[] current = todayRange.getValue();
        if (current != null && current[0] == startOfToday) {
            return;
        }
        long endOfToday = startOfToday + DAY_MILLIS - 1;
        todayRange.setValue(new long[]{startOfToday, endOfToday});
        // Ventana de 30 días CIVILES incluyendo hoy -> hoy cuenta como el día 30, no como el 31.
        streakWindowStart.setValue(startOfToday - 29L * DAY_MILLIS);
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
        dbWriteExecutor.execute(() -> {
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

    public void quickAddTask(String title, Calendar startDayAndHour, RepetitionSpec repetition,
                             int targetMinutes) {
        boolean repeats = repetition != null && repetition.repeats();
        CalendarEntry.EventBuilder builder = new CalendarEntry.EventBuilder()
                .setEventType(AddEventActivity.TYPE_TASK)  // build() no pone defaults (regla #4)
                .setEventTitle(title)
                .setEventStartDayAndHour(startDayAndHour)
                .setEventTargetMinutes(targetMinutes)
                .setEventIsDone(false);
        if (repeats) {
            // Tarea recurrente: se guarda como PLANTILLA; el materializador crea sus ocurrencias.
            builder.setEventRepetition(repetition.repetition)
                    .setEventRepetitionInterval(repetition.interval)
                    .setEventRepetitionDays(repetition.daysMask)
                    .setEventIsTemplate(true)
                    // La plantilla lleva el flag; las ocurrencias lo heredan en el materializador
                    // (spec recurrence-calendar-visibility).
                    .setEventHiddenInCalendar(repetition.hidesFromCalendar());
        }
        CalendarEntry task = builder.build();
        dbWriteExecutor.execute(() -> {
            long rowId = calendarEntryDao.insert(task);
            if (repeats) {
                new RecurrenceMaterializer(getApplication(), calendarEntryDao)
                        .materializeTemplate((int) rowId);
            }
        });
    }

    /**
     * Retira (sin borrar) una tarea atrasada desde Home (spec tasks-recurrence): si es una serie
     * recurrente, retira todas sus ocurrencias pasadas no hechas; si es una tarea suelta, sólo esa
     * fila. Los datos quedan en la BD para estadísticas/gráficas futuras.
     */
    public void dismissSeries(CalendarEntry entry) {
        final int entryId = entry.getId();
        final int templateId = entry.getTemplateId();
        dbWriteExecutor.execute(() -> {
            if (templateId != 0) {
                calendarEntryDao.dismissSeriesBefore(templateId, startOfTodayMillis());
            } else {
                CalendarEntry fresh = calendarEntryDao.getEventById(entryId);
                if (fresh != null) {
                    fresh.setDismissed(true);
                    calendarEntryDao.update(fresh);
                }
            }
        });
    }

    private static long startOfTodayMillis() {
        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }

    public LiveData<List<CalendarEntry>> getTodayTasks() {
        return todayTasksEnriched;
    }

    public LiveData<List<CalendarEntry>> getOverdueTasks() {
        return overdueTasks;
    }

    //Cuando el usuario cierra la app a medio contador
    public void addFails(int boundEntryId) {
        dbWriteExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "View Model addFails()");
                statsDao.addFails();
                // El fallo registra la tarea vinculada pero con 0 minutos: no avanza el objetivo.
                logFocusEvent(FocusEvent.TYPE_FAIL, 0, boundEntryId);
                todayFailsText.postValue(String.valueOf(statsDao.getTodayFails()));
            }
        });
    }

    //Cuando el temporizador llega a 0 se actualizan y guardan estadísticas
    public void completeTimer(int timerTime, int boundEntryId){
        dbWriteExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "View Model addTimeStudied( " + timerTime + " )");
                statsDao.addTimeStudied(timerTime);
                statsDao.addTasksDone();
                logFocusEvent(FocusEvent.TYPE_FOCUS, FormatHelper.millisToMinutes(timerTime), boundEntryId);
                if (boundEntryId != 0) {
                    focusAttributionRepository.maybeAutoComplete(boundEntryId);
                }
                maybeAnnounceFirstFocusOfDay();
                String formattedTime = FormatHelper.formatTime(statsDao.getTodayTimeStudied(), "HH:mm");
                todayTimeStudiedText.postValue(formattedTime);
            }
        });
    }

    /**
     * Se llama justo DESPUÉS de insertar el FocusEvent de la sesión, dentro del mismo executor:
     * si el recuento de hoy es exactamente 1, esta sesión es la primera del día y hay algo que
     * celebrar (spec focus-mode-and-streak). Contar filas en vez de guardar un "último día
     * celebrado" evita un flag persistente y sale correcto solo tras un reinicio o un cambio de día.
     */
    private void maybeAnnounceFirstFocusOfDay() {
        long startOfToday = startOfTodayMillis();
        long endOfToday = startOfToday + DAY_MILLIS - 1;
        if (focusEventDao.countFocusBetween(startOfToday, endOfToday) != 1) {
            return;
        }
        Calendar month = Calendar.getInstance();
        month.set(Calendar.DAY_OF_MONTH, 1);
        month.set(Calendar.HOUR_OF_DAY, 0);
        month.set(Calendar.MINUTE, 0);
        month.set(Calendar.SECOND, 0);
        month.set(Calendar.MILLISECOND, 0);
        int daysThisMonth = focusEventDao.countActiveDaysBetween(month.getTimeInMillis(), endOfToday);
        firstFocusOfDay.postValue(daysThisMonth);
    }

    /** Días de los últimos 30 con al menos un pomodoro completado (la racha de la toolbar). */
    public LiveData<Integer> getActiveDaysLast30() {
        return activeDaysLast30;
    }

    /** Nº de días con pomodoro de este mes, emitido sólo al completar el primero del día. */
    public LiveData<Integer> getFirstFocusOfDay() {
        return firstFocusOfDay;
    }

    /** El fragment lo llama tras mostrar la celebración (evita re-disparo al volver a Home). */
    public void clearFirstFocusOfDay() {
        firstFocusOfDay.setValue(null);
    }

    // Registra un evento con timestamp para el histórico por horas de Progress.
    // Se llama desde dentro del executorService, así que ya está fuera del hilo principal.
    private void logFocusEvent(int type, int durationMin, int entryId){
        FocusEvent ev = new FocusEvent();
        ev.timestamp = System.currentTimeMillis();
        ev.type = type;
        ev.durationMin = durationMin;
        ev.entryId = entryId;
        focusEventDao.insert(ev);
    }

    public LiveData<String> getAutoCompletedTaskTitle() {
        return focusAttributionRepository.getAutoCompletedTaskTitle();
    }

    /** El fragment lo llama tras consumir el evento de auto-completado (evita re-disparo). */
    public void clearAutoCompletedTaskTitle() {
        focusAttributionRepository.clearAutoCompletedTaskTitle();
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
        dbWriteExecutor.execute(new Runnable() {
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


    public LiveData<String> getTodayFailsText() {return todayFailsText;}
    public LiveData<String> getTodayTimeStudiedText() {return todayTimeStudiedText;}
    public LiveData<String> getTimerModeText() {return timerModeText;}

    // ioExecutor/dbWriteExecutor son los executors compartidos de la app (Hilt @Singleton) -- ya
    // no se cierran aquí; onCleared() no tiene nada más que liberar.
}