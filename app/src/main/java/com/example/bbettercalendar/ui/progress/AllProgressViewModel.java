package com.example.bbettercalendar.ui.progress;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.stats.AppUsageDaily;
import com.example.bbettercalendar.stats.AppUsageDailyDAO;
import com.example.bbettercalendar.stats.DailyStat;
import com.example.bbettercalendar.stats.DailyStatDAO;
import com.example.bbettercalendar.stats.FocusEvent;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.FocusInsightsRepository;
import com.example.bbettercalendar.stats.HourHeatmapCalculator;
import com.example.bbettercalendar.stats.PeriodComparison;
import com.example.bbettercalendar.stats.Records;
import com.example.bbettercalendar.stats.SessionStats;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;
import com.example.bbettercalendar.stats.UsageInsights;
import com.example.bbettercalendar.usage.UsageAccess;
import com.example.bbettercalendar.usage.UsageStatsRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel de «Todo tu progreso» (mejora-visual u8). Tiene su propio rango (el selector de esta pantalla
 * nunca mueve el de Progreso). Lee fuera del hilo principal (executor) y publica con postValue (regla #3):
 * un {@link AllProgressData} por cambio de periodo; la parte fija (90 dias, 12 y 26 semanas) se calcula
 * una vez y se reutiliza.
 */
@HiltViewModel
public class AllProgressViewModel extends AndroidViewModel {

    /** Dias de las tarjetas «Tus mejores horas» y «Duracion de tus sesiones». */
    static final int HOURS_DAYS = 90;
    /** Semanas del calendario de constancia. */
    static final int CONSISTENCY_WEEKS = 26;
    /** Ventana de la frase «Enfoque frente a movil». */
    private static final int PHONE_SENTENCE_DAYS = 30;
    /** Minimo de dias con uso medido para mostrar la tarjeta «Enfoque frente a movil». */
    static final int MIN_PHONE_DAYS = 3;

    private final MutableLiveData<TimeRange> selectedRange = new MutableLiveData<>();
    private final MutableLiveData<AllProgressData> data = new MutableLiveData<>();
    private final AtomicInteger sequence = new AtomicInteger();

    private final ExecutorService executorService;
    private final DailyStatDAO dailyStatDao;
    private final FocusEventDAO focusEventDao;
    private final StatsDAO statsDao;
    private final AppRuleDAO appRuleDao;
    private final AppUsageDailyDAO appUsageDao;
    private final FocusInsightsRepository insights;
    private final UsageStatsRepository usageRepo;

    // Parte fija: solo la toca el executor (lectura y escritura dentro de computeFixed).
    private AllProgressData fixed;

    @Inject
    public AllProgressViewModel(@NonNull Application application, DailyStatDAO dailyStatDao,
                                FocusEventDAO focusEventDao, StatsDAO statsDao, AppRuleDAO appRuleDao,
                                AppUsageDailyDAO appUsageDao, FocusInsightsRepository insights,
                                @IoExecutor ExecutorService executorService) {
        super(application);
        this.dailyStatDao = dailyStatDao;
        this.focusEventDao = focusEventDao;
        this.statsDao = statsDao;
        this.appRuleDao = appRuleDao;
        this.appUsageDao = appUsageDao;
        this.insights = insights;
        this.executorService = executorService;
        usageRepo = new UsageStatsRepository(application);
        apply(TimeRange.currentWeek());
    }

    public LiveData<TimeRange> getSelectedRange() { return selectedRange; }

    LiveData<AllProgressData> getData() { return data; }

    // --- intents del selector (hilo principal) ---

    public void setGranularity(Granularity g) {
        TimeRange current = selectedRange.getValue();
        if (current == null || current.granularity == g) return;
        apply(current.withGranularity(g));
    }

    public void stepBack() {
        TimeRange current = selectedRange.getValue();
        if (current == null) return;
        apply(current.stepped(-1));
    }

    public void stepForward() {
        TimeRange current = selectedRange.getValue();
        if (current == null || !current.canStepForward(LocalDate.now())) return;
        apply(current.stepped(1));
    }

    /** Reevalua el acceso de uso (al volver de Ajustes). */
    public void refresh() {
        TimeRange current = selectedRange.getValue();
        if (current != null) apply(current);
    }

    private void apply(TimeRange range) {
        selectedRange.setValue(range);
        final int mine = sequence.incrementAndGet();
        executorService.execute(() -> {
            AllProgressData d = compute(range);
            // Un cambio de periodo mas reciente ya esta en marcha: este resultado llegaria tarde.
            if (mine == sequence.get()) {
                data.postValue(d);
            }
        });
    }

    // ------------------------------------------------------------------ calculo (executor)

    private AllProgressData compute(TimeRange range) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        if (fixed == null) {
            fixed = computeFixed(today, zone);
        }
        AllProgressData d = new AllProgressData();
        d.hours = fixed.hours;
        d.trendCurrentHours = fixed.trendCurrentHours;
        d.trendPreviousHours = fixed.trendPreviousHours;
        d.trendLabels = fixed.trendLabels;
        d.daily26Weeks = fixed.daily26Weeks;
        d.sessions90 = fixed.sessions90;
        d.records = fixed.records;
        d.longestRunDays = fixed.longestRunDays;

        d.range = range;
        d.buckets = PeriodBuckets.of(range);
        TimeRange prev = range.stepped(-1);
        // Mismo numero de columnas para el periodo anterior: lo que sobra de un mes mas largo se descarta.
        int count = d.buckets.count;
        PeriodBuckets prevBuckets = PeriodBuckets.of(prev).withCount(count);

        long from = startMillis(prev.startDay(), zone);
        long to = startMillis(range.endDay().plusDays(1), zone) - 1;
        List<FocusEvent> events = focusEventDao.getRange(from, to);

        // 1 · Enfoque por dia
        d.focusCurrent = new float[count];
        d.focusPrevious = new float[count];
        if (range.granularity == Granularity.DAY) {
            for (FocusEvent ev : events) {
                if (ev.type != FocusEvent.TYPE_FOCUS) continue;
                ZonedDateTime z = Instant.ofEpochMilli(ev.timestamp).atZone(zone);
                int idx = d.buckets.indexOf(z);
                if (idx < 0) continue;
                if (z.toLocalDate().equals(range.anchor)) {
                    d.focusCurrent[idx] += ev.durationMin;
                } else if (z.toLocalDate().equals(prev.anchor)) {
                    d.focusPrevious[idx] += ev.durationMin;
                }
            }
        } else {
            Map<LocalDate, Integer> byDay = focusMinutesByDay(prev.startDay(), range.endDay(), today);
            accumulate(byDay, range, d.buckets, d.focusCurrent);
            accumulate(byDay, prev, prevBuckets, d.focusPrevious);
        }
        boolean inProgress = range.contains(today);
        d.focusHighlight = -1;
        d.focusElapsed = count;
        if (inProgress) {
            d.focusElapsed = Math.max(1, d.buckets.elapsed(today, ZonedDateTime.now(zone).getHour()));
            d.focusHighlight = d.focusElapsed - 1;
        }

        // 5 · Sesiones terminadas
        d.sessionBars = new float[count][2];
        long curStart = startMillis(range.startDay(), zone);
        for (FocusEvent ev : events) {
            if (ev.timestamp < curStart || ev.timestamp > to) continue;
            if (ev.type != FocusEvent.TYPE_FOCUS && ev.type != FocusEvent.TYPE_FAIL) continue;
            int idx = d.buckets.indexOf(Instant.ofEpochMilli(ev.timestamp).atZone(zone));
            if (idx < 0) continue;
            d.sessionBars[idx][ev.type == FocusEvent.TYPE_FOCUS ? 0 : 1]++;
        }
        d.sessionsCurrent = SessionStats.compute(events, range.startDay(), range.endDay(), zone);
        d.sessionsPrevious = SessionStats.compute(events, prev.startDay(), prev.endDay(), zone);

        // 6 · Tiempo por proyecto
        d.projects = focusEventDao.getMinutesByProject(curStart, to);

        // 8 · Enfoque frente a movil
        computePhone(d, range, today, zone);
        return d;
    }

    private AllProgressData computeFixed(LocalDate today, ZoneId zone) {
        AllProgressData f = new AllProgressData();
        LocalDate from90 = today.minusDays(HOURS_DAYS - 1);
        long endToday = startMillis(today.plusDays(1), zone) - 1;
        List<FocusEvent> events90 = focusEventDao.getRange(startMillis(from90, zone), endToday);
        f.hours = HourHeatmapCalculator.compute(events90, from90, today, zone);
        f.sessions90 = SessionStats.compute(events90, from90, today, zone);
        f.records = Records.compute(focusEventDao.getRange(0L, endToday), today, zone);

        // 3 · 12 semanas completas (igual que la tarjeta que tenia Progreso): si la semana de hoy esta a
        // medias, la linea termina en la anterior para que el ultimo punto no se hunda.
        LocalDate weekStart = PeriodComparison.periodStart(today, PeriodComparison.Period.WEEK);
        LocalDate trendAnchor = today.equals(weekStart.plusDays(6)) ? today : weekStart.minusDays(1);
        PeriodComparison.Result trend = insights.focusMinutesComparison(
                trendAnchor, PeriodComparison.Period.WEEK, 12, false);
        int n = trend.periods;
        f.trendCurrentHours = new float[n];
        f.trendPreviousHours = new float[n];
        for (int i = 0; i < n; i++) {
            f.trendCurrentHours[i] = trend.current[i] / 60f;
            f.trendPreviousHours[i] = trend.previous[i] / 60f;
        }
        f.trendLabels = ProgressViewModel.trendLabels(trend.currentStarts);

        // 4 · constancia: minutos por dia de las 26 semanas que terminan hoy
        int days = CONSISTENCY_WEEKS * 7;
        LocalDate from = today.minusDays(days - 1);
        Map<LocalDate, Integer> byDay = focusMinutesByDay(from, today, today);
        f.daily26Weeks = new float[days];
        int run = 0;
        int longest = 0;
        for (int i = 0; i < days; i++) {
            Integer m = byDay.get(from.plusDays(i));
            f.daily26Weeks[i] = m == null ? 0f : m;
            run = f.daily26Weeks[i] > 0f ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        f.longestRunDays = longest;
        return f;
    }

    private void computePhone(AllProgressData d, TimeRange range, LocalDate today, ZoneId zone) {
        // Dia: no hay uso por hora guardado, asi que se muestran los 7 dias que terminan en ese dia.
        LocalDate from;
        LocalDate to;
        if (range.granularity == Granularity.DAY) {
            to = range.anchor;
            from = to.minusDays(6);
        } else {
            from = range.startDay();
            to = range.endDay();
        }
        // Con granularidad dia las 7 columnas son los 7 dias terminados en el dia elegido, no el lunes-domingo.
        boolean sevenDays = range.granularity == Granularity.DAY;
        PeriodBuckets b = d.buckets;

        Set<String> tracked = new HashSet<>();
        for (AppRule r : appRuleDao.getTracked()) tracked.add(r.packageName);
        boolean access = UsageAccess.hasUsageAccess(getApplication());

        Map<LocalDate, Integer> phone = access
                ? phoneMinutesByDay(from, to, today, zone, tracked) : new HashMap<>();
        Map<LocalDate, Integer> focus = focusMinutesByDay(from, to, today);

        int count = sevenDays ? 7 : b.count;
        d.mirrorFocus = new float[count];
        d.mirrorPhone = new float[count];
        if (sevenDays) {
            for (int i = 0; i < 7; i++) {
                LocalDate day = from.plusDays(i);
                Integer f = focus.get(day);
                Integer p = phone.get(day);
                d.mirrorFocus[i] = f == null ? 0f : f;
                d.mirrorPhone[i] = p == null ? 0f : p;
            }
            String[] letters = getApplication().getResources()
                    .getStringArray(com.example.bbettercalendar.R.array.all_progress_day_letters);
            d.mirrorLabels = new String[7];
            for (int i = 0; i < 7; i++) {
                d.mirrorLabels[i] = letters[from.plusDays(i).getDayOfWeek().getValue() - 1];
            }
        } else {
            accumulate(focus, range, b, d.mirrorFocus);
            accumulate(phone, range, b, d.mirrorPhone);
            d.mirrorLabels = b.labels(Locale.getDefault(),
                    getApplication().getString(com.example.bbettercalendar.R.string.all_progress_week_prefix),
                    getApplication().getResources()
                            .getStringArray(com.example.bbettercalendar.R.array.all_progress_day_letters));
        }
        // Disponible con acceso de uso y al menos MIN_PHONE_DAYS dias medidos en los ultimos 30 (no solo en el
        // periodo: a principios de semana el periodo aun no tiene dias suficientes y la tarjeta parpadearia).
        // La frase usa esos mismos 30 dias (necesita >= 14 dias medidos).
        d.focusVsPhone = null;
        d.phoneAvailable = false;
        if (access) {
            LocalDate from30 = today.minusDays(PHONE_SENTENCE_DAYS - 1);
            Map<LocalDate, Integer> phone30 = phoneMinutesByDay(from30, today, today, zone, tracked);
            d.phoneAvailable = phone30.size() >= MIN_PHONE_DAYS;
            d.focusVsPhone = UsageInsights.focusVsPhone(focusMinutesByDay(from30, today, today), phone30);
        }
    }

    // ------------------------------------------------------------------ lecturas

    private static long startMillis(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** daily_stat de [from, to] con el valor vivo de hoy (hoy aun no esta volcado), un valor por dia. */
    private Map<LocalDate, Integer> focusMinutesByDay(LocalDate from, LocalDate to, LocalDate today) {
        Map<LocalDate, Integer> out = new HashMap<>();
        for (DailyStat ds : dailyStatDao.getRange(from.toString(), to.toString())) {
            try {
                out.put(LocalDate.parse(ds.day), ds.focusMinutes);
            } catch (RuntimeException ignored) {
                // fila con 'day' corrupto: se trata como ausente
            }
        }
        if (!today.isBefore(from) && !today.isAfter(to)) {
            Stats live = statsDao.getStats();
            out.put(today, live != null ? FormatHelper.millisToMinutes(live.todayTimeStudied) : 0);
        }
        return out;
    }

    /** Minutos de las apps seguidas por dia (app_usage_daily + hoy en vivo); solo dias con datos. */
    private Map<LocalDate, Integer> phoneMinutesByDay(LocalDate from, LocalDate to, LocalDate today,
                                                      ZoneId zone, Set<String> tracked) {
        Map<LocalDate, Integer> out = new HashMap<>();
        for (AppUsageDaily row : appUsageDao.getBetween(from.toString(), to.toString())) {
            if (!tracked.contains(row.packageName)) continue;
            try {
                LocalDate day = LocalDate.parse(row.day);
                Integer prev = out.get(day);
                out.put(day, (prev == null ? 0 : prev) + row.minutes);
            } catch (RuntimeException ignored) {
                // fila con 'day' corrupto: se trata como ausente
            }
        }
        if (!today.isBefore(from) && !today.isAfter(to) && !tracked.isEmpty() && !out.containsKey(today)) {
            long begin = startMillis(today, zone);
            long end = startMillis(today.plusDays(1), zone);
            int live = 0;
            for (Map.Entry<String, Long> en : usageRepo.foregroundMillis(begin, end).entrySet()) {
                if (tracked.contains(en.getKey())) live += (int) (en.getValue() / 60_000L);
            }
            if (live > 0) out.put(today, live);
        }
        return out;
    }

    /** Suma los valores diarios del periodo en sus columnas (los dias fuera del periodo se ignoran). */
    private static void accumulate(Map<LocalDate, Integer> byDay, TimeRange range, PeriodBuckets b,
                                   float[] into) {
        for (Map.Entry<LocalDate, Integer> en : byDay.entrySet()) {
            LocalDate day = en.getKey();
            if (!range.contains(day) || en.getValue() == null) continue;
            int idx = b.indexOfDay(day);
            if (idx >= 0 && idx < into.length) into[idx] += en.getValue();
        }
    }

}
