package com.example.bbettercalendar.ui.progress;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.stats.DailyStat;
import com.example.bbettercalendar.stats.DailyStatDAO;
import com.example.bbettercalendar.stats.FocusEvent;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ViewModel de la pantalla Progress (Phase 1). Lee daily_stat / focus_event / stats fuera del
// hilo principal (ExecutorService) y publica con postValue (regla #3). No importa tipos de
// MPAndroidChart: produce un ChartBundle plano que el Fragment/adapter convierte en entries.
public class ProgressViewModel extends AndroidViewModel {

    // Conservado para que ProjectsFragment (que reutiliza este VM) siga compilando.
    private final MutableLiveData<String> mText;

    private final MutableLiveData<TimeRange> selectedRange;
    private final MutableLiveData<ChartBundle> charts;

    private final ExecutorService executorService;
    private final DailyStatDAO dailyStatDao;
    private final FocusEventDAO focusEventDao;
    private final StatsDAO statsDao;

    private static final DateTimeFormatter X_LABEL =
            DateTimeFormatter.ofPattern("d/M", Locale.getDefault());

    public ProgressViewModel(@NonNull Application application) {
        super(application);
        mText = new MutableLiveData<>();
        mText.setValue("This is progress fragment");
        selectedRange = new MutableLiveData<>();
        charts = new MutableLiveData<>();

        AppDatabase db = AppDatabase.getDatabase(application);
        dailyStatDao = db.dailyStatDao();
        focusEventDao = db.focusEventDao();
        statsDao = db.statsDao();
        executorService = Executors.newFixedThreadPool(2);

        applyRange(TimeRange.currentWeek());
    }

    public LiveData<String> getText() { return mText; }
    public LiveData<TimeRange> getSelectedRange() { return selectedRange; }
    public LiveData<ChartBundle> getCharts() { return charts; }

    // --- intents del navegador (se llaman desde clicks de UI = hilo principal) ---

    public void setGranularity(Granularity g) {
        TimeRange current = selectedRange.getValue();
        if (current == null || current.granularity == g) return;
        applyRange(current.withGranularity(g));
    }

    public void stepBack() {
        TimeRange current = selectedRange.getValue();
        if (current == null) return;
        applyRange(current.stepped(-1));
    }

    public void stepForward() {
        TimeRange current = selectedRange.getValue();
        if (current == null || !current.canStepForward(LocalDate.now())) return;
        applyRange(current.stepped(1));
    }

    // selectedRange se publica en el hilo actual (UI/constructor); el cálculo pesado de los
    // gráficos se hace en el executor y se publica con postValue.
    private void applyRange(TimeRange range) {
        selectedRange.setValue(range);
        executorService.execute(() -> charts.postValue(buildBundle(range)));
    }

    private ChartBundle buildBundle(TimeRange range) {
        LocalDate start = range.startDay();
        LocalDate end = range.endDay();
        LocalDate today = LocalDate.now();

        // Series diarias indexadas por fecha ISO; days que no existen en la tabla -> 0 (rellenado).
        Map<String, DailyStat> byDay = new HashMap<>();
        for (DailyStat ds : dailyStatDao.getRange(start.toString(), end.toString())) {
            byDay.put(ds.day, ds);
        }

        // Hoy aún no está en daily_stat (se vuelca en el reset del día siguiente): valor vivo en Stats.
        Stats live = statsDao.getStats();
        int liveTodayFocusMin = live != null ? FormatHelper.millisToMinutes(live.todayTimeStudied) : 0;
        int liveTodayFails = live != null ? live.todayFails : 0;

        int days = (int) (end.toEpochDay() - start.toEpochDay()) + 1;
        String[] labels = new String[days];
        int[] focusMinutes = new int[days];
        int[] fails = new int[days];

        LocalDate d = start;
        for (int i = 0; i < days; i++) {
            labels[i] = d.format(X_LABEL);
            if (d.equals(today)) {
                focusMinutes[i] = liveTodayFocusMin;
                fails[i] = liveTodayFails;
            } else {
                DailyStat ds = byDay.get(d.toString());
                if (ds != null) {
                    focusMinutes[i] = ds.focusMinutes;
                    fails[i] = ds.fails;
                }
            }
            d = d.plusDays(1);
        }

        // Buckets por hora del día desde focus_event (incluye hoy: los eventos se escriben en vivo).
        ZoneId zone = ZoneId.systemDefault();
        long startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
        int[] focusMinutesByHour = new int[24];
        int[] focusByHour = new int[24];
        int[] failByHour = new int[24];
        for (FocusEvent ev : focusEventDao.getRange(startMillis, endMillis)) {
            int hour = Instant.ofEpochMilli(ev.timestamp).atZone(zone).getHour();
            if (ev.type == FocusEvent.TYPE_FAIL) {
                failByHour[hour]++;
            } else if (ev.type == FocusEvent.TYPE_FOCUS) {
                focusByHour[hour]++;
                // Minutos por hora: alimenta el gráfico de concentración en vista DAY (misma
                // unidad que la serie diaria), mientras focusByHour mantiene el conteo de sesiones
                // que usa el gráfico "When" en week/month.
                focusMinutesByHour[hour] += ev.durationMin;
            }
        }

        return new ChartBundle(range.granularity, labels, focusMinutes, fails,
                focusMinutesByHour, focusByHour, failByHour);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
