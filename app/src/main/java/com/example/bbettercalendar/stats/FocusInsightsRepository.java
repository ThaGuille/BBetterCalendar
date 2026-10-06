package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.helpers.FormatHelper;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Puente fino entre Room y los cálculos puros de u3 ({@link StreakCalculator},
 * {@link PeriodComparison}) para que Home/Focus/Cycle complete/Progress no repitan la lectura.
 * Sin esquema nuevo: sólo lee focus_event, daily_stat y stats.
 */
@Singleton
public class FocusInsightsRepository {

    private final FocusEventDAO focusEventDao;
    private final DailyStatDAO dailyStatDao;
    private final StatsDAO statsDao;
    private final ExecutorService ioExecutor;

    @Inject
    public FocusInsightsRepository(FocusEventDAO focusEventDao, DailyStatDAO dailyStatDao,
                                   StatsDAO statsDao, @IoExecutor ExecutorService ioExecutor) {
        this.focusEventDao = focusEventDao;
        this.dailyStatDao = dailyStatDao;
        this.statsDao = statsDao;
        this.ioExecutor = ioExecutor;
    }

    /**
     * Racha reactiva: se reemite sola al insertar un FocusEvent (Room hace la I/O en su executor; el
     * map sólo recorre la lista ya en memoria, como FocusAttributionRepository.enrich). "Hoy" se
     * toma en cada emisión: si la pantalla sigue abierta al pasar la medianoche sin sesión nueva,
     * el consumidor debe re-suscribirse (p. ej. en onResume) para que la fila semanal avance.
     */
    @NonNull
    public LiveData<StreakCalculator.Result> observeStreak() {
        return Transformations.map(focusEventDao.observeAllActiveDays(),
                days -> StreakCalculator.compute(days, LocalDate.now()));
    }

    /**
     * Comparación de minutos de foco (daily_stat + el valor vivo de hoy desde Stats). Síncrona:
     * llamar SOLO desde un executor (regla #3).
     */
    @WorkerThread
    @NonNull
    public PeriodComparison.Result focusMinutesComparison(@NonNull LocalDate anchor,
                                                          @NonNull PeriodComparison.Period period,
                                                          int n, boolean toDate) {
        LocalDate from = PeriodComparison.queryStart(anchor, period, n);
        LocalDate today = LocalDate.now();
        Integer liveToday = null;
        if (!today.isBefore(from) && !today.isAfter(anchor)) {
            Stats live = statsDao.getStats();
            liveToday = live != null ? FormatHelper.millisToMinutes(live.todayTimeStudied) : 0;
        }
        return PeriodComparison.compute(
                PeriodComparison.focusMinutesByDay(
                        dailyStatDao.getRange(from.toString(), anchor.toString()),
                        liveToday != null ? today : null,
                        liveToday != null ? liveToday : 0),
                anchor, period, n, toDate);
    }

    /** Versión asíncrona de {@link #focusMinutesComparison}: corre en el executor y publica con postValue. */
    public void loadFocusMinutesComparison(@NonNull LocalDate anchor,
                                           @NonNull PeriodComparison.Period period, int n, boolean toDate,
                                           @NonNull MutableLiveData<PeriodComparison.Result> out) {
        ioExecutor.execute(() -> out.postValue(focusMinutesComparison(anchor, period, n, toDate)));
    }
}
