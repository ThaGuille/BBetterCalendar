package com.example.bbettercalendar.stats;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.helpers.FormatHelper;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Write side of a Pomodoro session (migración visual, u2): what {@code HomeViewModel.completeTimer}
 * and {@code addFails} used to do, moved here verbatim so the activity-scoped
 * {@code FocusSessionViewModel} can record a session even when Home is not on screen (Focus
 * screen, another tab, app in background). Singleton, so the "today" LiveData it publishes is the
 * same one Home observes through {@code HomeViewModel}.
 */
@Singleton
public class FocusSessionRepository {

    private static final String TAG = "FocusSessionRepository";
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private final StatsDAO statsDao;
    private final FocusEventDAO focusEventDao;
    private final FocusAttributionRepository focusAttributionRepository;
    private final ExecutorService ioExecutor;
    private final ExecutorService dbWriteExecutor;

    private final MutableLiveData<Integer> todayFails = new MutableLiveData<>();
    private final MutableLiveData<Integer> todayTimeStudiedMillis = new MutableLiveData<>();
    // Evento de un solo disparo: nº de días con pomodoro de ESTE MES, emitido sólo cuando la
    // sesión recién completada es la primera del día. null = nada pendiente que celebrar.
    private final MutableLiveData<Integer> firstFocusOfDay = new MutableLiveData<>();

    @Inject
    public FocusSessionRepository(StatsDAO statsDao, FocusEventDAO focusEventDao,
                                  FocusAttributionRepository focusAttributionRepository,
                                  @IoExecutor ExecutorService ioExecutor,
                                  @DbWriteExecutor ExecutorService dbWriteExecutor) {
        this.statsDao = statsDao;
        this.focusEventDao = focusEventDao;
        this.focusAttributionRepository = focusAttributionRepository;
        this.ioExecutor = ioExecutor;
        this.dbWriteExecutor = dbWriteExecutor;
    }

    /** Re-reads today's fails / time studied from {@code Stats} (old HomeViewModel.setInitialTexts). */
    public void refreshToday() {
        ioExecutor.execute(() -> {
            Stats stats = statsDao.getStats();
            todayFails.postValue(stats.todayFails);
            todayTimeStudiedMillis.postValue(stats.todayTimeStudied);
        });
    }

    //Cuando el temporizador llega a 0 se actualizan y guardan estadísticas
    public void recordFocusCompleted(int timerTime, int boundEntryId) {
        dbWriteExecutor.execute(() -> {
            Log.i(TAG, "addTimeStudied( " + timerTime + " )");
            statsDao.addTimeStudied(timerTime);
            statsDao.addTasksDone();
            logFocusEvent(FocusEvent.TYPE_FOCUS, FormatHelper.millisToMinutes(timerTime), boundEntryId);
            if (boundEntryId != 0) {
                focusAttributionRepository.maybeAutoComplete(boundEntryId);
            }
            maybeAnnounceFirstFocusOfDay();
            todayTimeStudiedMillis.postValue(statsDao.getTodayTimeStudied());
        });
    }

    //Cuando el usuario cierra la app a medio contador
    public void recordFail(int boundEntryId) {
        dbWriteExecutor.execute(() -> {
            Log.i(TAG, "addFails()");
            statsDao.addFails();
            // El fallo registra la tarea vinculada pero con 0 minutos: no avanza el objetivo.
            logFocusEvent(FocusEvent.TYPE_FAIL, 0, boundEntryId);
            todayFails.postValue(statsDao.getTodayFails());
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

    // Registra un evento con timestamp para el histórico por horas de Progress.
    // Se llama desde dentro del executor, así que ya está fuera del hilo principal.
    private void logFocusEvent(int type, int durationMin, int entryId) {
        FocusEvent ev = new FocusEvent();
        ev.timestamp = System.currentTimeMillis();
        ev.type = type;
        ev.durationMin = durationMin;
        ev.entryId = entryId;
        focusEventDao.insert(ev);
    }

    private static long startOfTodayMillis() {
        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }

    /** Today's fail count (Stats.todayFails); null until the first read lands. */
    public LiveData<Integer> getTodayFails() {
        return todayFails;
    }

    /** Today's studied time in millis (Stats.todayTimeStudied); null until the first read. */
    public LiveData<Integer> getTodayTimeStudiedMillis() {
        return todayTimeStudiedMillis;
    }

    /** Nº de días con pomodoro de este mes, emitido sólo al completar el primero del día. */
    public LiveData<Integer> getFirstFocusOfDay() {
        return firstFocusOfDay;
    }

    /** Main thread: call after showing the celebration (avoids re-firing on return). */
    public void clearFirstFocusOfDay() {
        firstFocusOfDay.setValue(null);
    }
}
