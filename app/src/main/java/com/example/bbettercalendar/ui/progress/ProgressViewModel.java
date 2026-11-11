package com.example.bbettercalendar.ui.progress;

import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDeadlineState;
import com.example.bbettercalendar.projects.ProjectWithCounts;
import com.example.bbettercalendar.projects.ProjectsRepository;
import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.stats.AppUsageDaily;
import com.example.bbettercalendar.stats.AppUsageDailyDAO;
import com.example.bbettercalendar.stats.UsageInsights;
import com.example.bbettercalendar.stats.ConsistencyCalculator;
import com.example.bbettercalendar.stats.DailyStat;
import com.example.bbettercalendar.stats.DailyStatDAO;
import com.example.bbettercalendar.stats.FocusEvent;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.FocusInsightsRepository;
import com.example.bbettercalendar.stats.PeriodComparison;
import com.example.bbettercalendar.stats.ProjectMinutes;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;
import com.example.bbettercalendar.usage.UsageAccess;
import com.example.bbettercalendar.usage.UsageStatsRepository;
import com.example.bbettercalendar.usage.limits.UsageLimitScheduler;
import com.example.bbettercalendar.usage.limits.WarnedTodayStore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

// ViewModel de la pantalla Progress (Phase 1). Lee daily_stat / focus_event / stats fuera del
// hilo principal (ExecutorService) y publica con postValue (regla #3). No importa tipos de
// MPAndroidChart: publica ProgressOverview y la tarjeta de apps ya agregados (los gráficos de «Todo tu progreso» viven en AllProgressViewModel).
@HiltViewModel
public class ProgressViewModel extends AndroidViewModel {

    // Conservado para que ProjectsFragment (que reutiliza este VM) siga compilando.
    private final MutableLiveData<String> mText;

    private final MutableLiveData<TimeRange> selectedRange;

    // Banda 2 (progreso por proyecto, spec project-deadlines-progress). Se compone de DOS fuentes
    // vivas: los proyectos con sus recuentos (LiveData de Room, se invalida sola al escribir en
    // project O en calendarEntry) y los minutos del rango seleccionado (los publica applyRange
    // desde el executor). A diferencia de la banda de uso, no depende de ningún permiso.
    private final MutableLiveData<Map<Integer, Integer>> projectMinutesInRange;
    private final MediatorLiveData<List<ProjectProgressRow>> projectRows;
    private List<ProjectWithCounts> latestProjectCounts;

    // u17: tiles + "Past 12 weeks" (dependen del rango) y tarjeta Screen time & blocking (de hoy).
    private final MutableLiveData<ProgressOverview> overview;
    private final MutableLiveData<ProgressOverview.AppsCard> appsCard;

    private final ExecutorService executorService;
    private final DailyStatDAO dailyStatDao;
    private final FocusEventDAO focusEventDao;
    private final StatsDAO statsDao;
    private final CalendarEntryDAO calendarEntryDao;
    private final FocusInsightsRepository insights;
    private final AppRuleDAO appRuleDao;
    private final AppUsageDailyDAO appUsageDao;
    private final UsageStatsRepository usageRepo;
    private final UsageLimitScheduler usageLimitScheduler;

    private static final DateTimeFormatter X_LABEL =
            DateTimeFormatter.ofPattern("d/M", Locale.getDefault());

    @Inject
    public ProgressViewModel(@NonNull Application application, DailyStatDAO dailyStatDao,
                              FocusEventDAO focusEventDao, StatsDAO statsDao, AppRuleDAO appRuleDao,
                              AppUsageDailyDAO appUsageDao,
                              ProjectsRepository projectsRepository,
                              CalendarEntryDAO calendarEntryDao, FocusInsightsRepository insights,
                              @IoExecutor ExecutorService executorService) {
        super(application);
        mText = new MutableLiveData<>();
        mText.setValue("This is progress fragment");
        selectedRange = new MutableLiveData<>();
        projectMinutesInRange = new MutableLiveData<>();
        projectRows = new MediatorLiveData<>();
        overview = new MutableLiveData<>();
        appsCard = new MutableLiveData<>();

        this.calendarEntryDao = calendarEntryDao;
        this.insights = insights;
        this.dailyStatDao = dailyStatDao;
        this.focusEventDao = focusEventDao;
        this.statsDao = statsDao;
        this.appRuleDao = appRuleDao;
        this.appUsageDao = appUsageDao;
        usageRepo = new UsageStatsRepository(application);
        AlarmManager alarmManager = (AlarmManager) application.getSystemService(Context.ALARM_SERVICE);
        WarnedTodayStore warnedStore = new WarnedTodayStore(application);
        usageLimitScheduler = new UsageLimitScheduler(application, alarmManager, appRuleDao, warnedStore);
        this.executorService = executorService;

        // La composición en sí es en memoria (dos listas ya cargadas), así que va en el hilo
        // principal: el trabajo de BD ya está fuera de él -- Room en su propio hilo para los
        // recuentos, el executor para los minutos del rango (regla #3).
        projectRows.addSource(projectsRepository.observeAllWithCounts(), counts -> {
            latestProjectCounts = counts;
            rebuildProjectRows();
        });
        projectRows.addSource(projectMinutesInRange, minutes -> rebuildProjectRows());

        applyRange(TimeRange.currentWeek());
    }

    public LiveData<String> getText() { return mText; }
    public LiveData<TimeRange> getSelectedRange() { return selectedRange; }
    public LiveData<List<ProjectProgressRow>> getProjectRows() { return projectRows; }
    public LiveData<ProgressOverview> getOverview() { return overview; }
    public LiveData<ProgressOverview.AppsCard> getAppsCard() { return appsCard; }

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
    // gráficos y de la tarjeta de bloqueo se hace en el executor y se publica con postValue.
    private void applyRange(TimeRange range) {
        selectedRange.setValue(range);
        executorService.execute(() -> {
            // Minutos por proyecto del rango (id -> minutos) para la banda de proyectos.
            projectMinutesInRange.postValue(toMinutesById(queryProjectMinutes(range)));
        });
        executorService.execute(() -> refreshOverview(range));
        executorService.execute(this::publishAppsCard);
    }

    // --- u17: tiles del periodo + "Past 12 weeks" ---

    private static PeriodComparison.Period periodOf(Granularity g) {
        switch (g) {
            case MONTH:
                return PeriodComparison.Period.MONTH;
            case YEAR:
                return PeriodComparison.Period.YEAR;
            case DAY:
            case WEEK:
            default:
                return PeriodComparison.Period.WEEK;
        }
    }

    private static final DateTimeFormatter TREND_MONTH =
            DateTimeFormatter.ofPattern("MMM", Locale.getDefault());

    /**
     * Etiqueta del eje X por semana: el mes en cada cambio de mes, "" en el resto. La primera
     * semana sólo se etiqueta si el siguiente cambio queda a {@link #TREND_LABEL_GAP} o más
     * índices; si no, "JUL" y "AUG" se pisaban.
     */
    static String[] trendLabels(LocalDate[] weekStarts) {
        int n = weekStarts.length;
        String[] labels = new String[n];
        int nextChange = n;
        for (int i = 1; i < n; i++) {
            if (weekStarts[i].getMonthValue() != weekStarts[i - 1].getMonthValue()) {
                nextChange = i;
                break;
            }
        }
        for (int i = 0; i < n; i++) {
            boolean change = i == 0
                    ? nextChange >= TREND_LABEL_GAP
                    : weekStarts[i].getMonthValue() != weekStarts[i - 1].getMonthValue();
            labels[i] = change
                    ? weekStarts[i].format(TREND_MONTH).toUpperCase(Locale.getDefault()) : "";
        }
        return labels;
    }

    private static final int TREND_LABEL_GAP = 3;

    // Corre SIEMPRE en el executor; publica con postValue. Rango en curso: el delta compara "hasta
    // hoy" con la misma posicion del periodo anterior (toDate); rango pasado: periodos completos.
    private void refreshOverview(TimeRange range) {
        LocalDate today = LocalDate.now();
        boolean inProgress = range.contains(today);
        LocalDate anchor = inProgress ? today : range.endDay();
        ZoneId zone = ZoneId.systemDefault();

        PeriodComparison.Result focus = insights.focusMinutesComparison(
                anchor, periodOf(range.granularity), 1, inProgress);

        long start = range.startDay().atStartOfDay(zone).toInstant().toEpochMilli();
        long end = range.endDay().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
        int sessions = focusEventDao.countFocusBetween(start, end);
        LocalDate lastDay = range.endDay().isAfter(today) ? today : range.endDay();
        long elapsedDays = Math.max(1L, ChronoUnit.DAYS.between(range.startDay(), lastDay) + 1);

        int tasksDone = calendarEntryDao.countDoneTasksBetween(start, end);
        TimeRange prev = range.stepped(-1);
        long pStart = prev.startDay().atStartOfDay(zone).toInstant().toEpochMilli();
        long pEnd = prev.endDay().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
        int tasksPrev = calendarEntryDao.countDoneTasksBetween(pStart, pEnd);

        // "Past 12 weeks" vs las 12 anteriores: sólo semanas COMPLETAS. Si la del ancla está a
        // medias, el último punto (2-3 días) caía al suelo y la línea parecía hundirse; se
        // termina en la semana anterior.
        LocalDate weekStart = PeriodComparison.periodStart(anchor, PeriodComparison.Period.WEEK);
        LocalDate trendAnchor = anchor.equals(weekStart.plusDays(6)) ? anchor : weekStart.minusDays(1);
        PeriodComparison.Result trend = insights.focusMinutesComparison(
                trendAnchor, PeriodComparison.Period.WEEK, 12, false);
        int n = trend.periods;
        float[] cur = new float[n];
        float[] prv = new float[n];
        for (int i = 0; i < n; i++) {
            cur[i] = trend.current[i] / 60f;
            prv[i] = trend.previous[i] / 60f;
        }
        String[] labels = trendLabels(trend.currentStarts);

        // Constancia: una sola lectura desde el inicio del periodo anterior cubre los dos.
        Set<LocalDate> activeDays = ConsistencyCalculator.parseIsoDays(
                focusEventDao.getActiveDaysSince(pStart));
        ConsistencyCalculator.Result consistency = ConsistencyCalculator.compute(activeDays,
                range.startDay(), range.endDay(), today, range.granularity == Granularity.YEAR);
        LocalDate prevTo = prev.endDay();
        if (inProgress) {
            // Misma altura del periodo anterior; con meses/años de distinta longitud se recorta.
            LocalDate sameOffset = prev.startDay().plusDays(
                    ChronoUnit.DAYS.between(range.startDay(), today));
            if (sameOffset.isBefore(prevTo)) {
                prevTo = sameOffset;
            }
        }
        int prevActive = ConsistencyCalculator.activeDaysBetween(activeDays, prev.startDay(), prevTo);

        // Mini barras de la tarjeta resumen: minutos de foco por dia (por mes en Ano).
        int days = (int) (range.endDay().toEpochDay() - range.startDay().toEpochDay()) + 1;
        float[] spark = new float[days];
        Map<String, DailyStat> byDay = new HashMap<>();
        for (DailyStat ds : dailyStatDao.getRange(range.startDay().toString(), range.endDay().toString())) {
            byDay.put(ds.day, ds);
        }
        Stats live = statsDao.getStats();
        for (int i = 0; i < days; i++) {
            LocalDate d = range.startDay().plusDays(i);
            if (d.equals(today)) {
                spark[i] = live != null ? FormatHelper.millisToMinutes(live.todayTimeStudied) : 0f;
            } else {
                DailyStat ds = byDay.get(d.toString());
                spark[i] = ds != null ? ds.focusMinutes : 0f;
            }
        }
        String[] sparkLabels = new String[days];
        int sparkHighlight = inProgress ? (int) ChronoUnit.DAYS.between(range.startDay(), today) : -1;
        for (int i = 0; i < days; i++) {
            sparkLabels[i] = range.startDay().plusDays(i).format(X_LABEL);
        }
        if (range.granularity == Granularity.YEAR) {
            float[] byMonth = new float[12];
            String[] monthLabels = new String[12];
            for (int i = 0; i < days; i++) {
                byMonth[range.startDay().plusDays(i).getMonthValue() - 1] += spark[i];
            }
            for (int m = 0; m < 12; m++) {
                monthLabels[m] = range.startDay().withMonth(m + 1).format(TREND_MONTH);
            }
            sparkHighlight = inProgress ? today.getMonthValue() - 1 : -1;
            spark = byMonth;
            sparkLabels = monthLabels;
        }
        int avgSession = sessions > 0 ? (int) Math.round((double) focus.currentTotal / sessions) : 0;

        overview.postValue(new ProgressOverview(focus.currentTotal, focus.hasDeltaPercent(),
                focus.deltaPercent(), sessions, sessions / (double) elapsedDays,
                tasksDone, tasksDone - tasksPrev, cur, prv, labels,
                trend.currentTotal, trend.deltaAbsolute(),
                consistency, prevActive > 0, consistency.activeDays - prevActive,
                spark, sparkLabels, sparkHighlight, avgSession));
    }

    // Tarjeta "Tus apps": media diaria de los ultimos 7 dias (app_usage_daily + hoy en vivo) frente a
    // los 7 anteriores, y las 3 apps con mas uso. Corre en el executor y publica con postValue.
    private void publishAppsCard() {
        if (!UsageAccess.hasUsageAccess(getApplication())) {
            appsCard.postValue(new ProgressOverview.AppsCard(true, 0f, false, 0,
                    Collections.<ProgressOverview.AppsCard.AppRow>emptyList()));
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        long begin = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        Map<String, Integer> liveToday = new HashMap<>();
        for (Map.Entry<String, Long> en : usageRepo.foregroundMillis(begin, end).entrySet()) {
            int minutes = (int) (en.getValue() / 60_000L);
            if (minutes > 0) liveToday.put(en.getKey(), minutes);
        }
        List<AppUsageDaily> rows = appUsageDao.getBetween(today.minusDays(14).toString(), today.toString());
        List<UsageInsights.AppAverage> now = UsageInsights.dailyAverages(rows,
                liveToday.isEmpty() ? null : liveToday, today, 7);
        LocalDate prevEnd = today.minusDays(7);
        List<UsageInsights.AppAverage> before = UsageInsights.dailyAverages(rows, null, prevEnd, 7);

        float nowTotal = 0f;
        for (UsageInsights.AppAverage a : now) nowTotal += (float) a.averageMinutes;
        float beforeTotal = 0f;
        for (UsageInsights.AppAverage a : before) beforeTotal += (float) a.averageMinutes;
        boolean hasDelta = beforeTotal > 0f && nowTotal > 0f;
        int deltaPercent = hasDelta ? Math.round((nowTotal - beforeTotal) * 100f / beforeTotal) : 0;

        List<AppRule> rules = appRuleDao.getAll();
        Map<String, AppRule> ruleByPkg = new HashMap<>();
        for (AppRule r : rules) ruleByPkg.put(r.packageName, r);
        Set<String> suggested = new java.util.HashSet<>();
        for (UsageInsights.AppAverage a : UsageInsights.suggested(now, rules)) suggested.add(a.packageName);

        List<ProgressOverview.AppsCard.AppRow> top = new ArrayList<>();
        for (UsageInsights.AppAverage a : UsageInsights.topN(now, 3)) {
            AppRule rule = ruleByPkg.get(a.packageName);
            int limit = rule != null && rule.dailyLimitMinutes > 0 ? rule.dailyLimitMinutes : 0;
            top.add(new ProgressOverview.AppsCard.AppRow(a.packageName,
                    usageRepo.labelFor(a.packageName), (float) a.averageMinutes, limit,
                    limit > 0 && rule.enforceAtLimit, suggested.contains(a.packageName)));
        }
        appsCard.postValue(new ProgressOverview.AppsCard(false, nowTotal, hasDelta, deltaPercent, top));
    }

    // --- banda 2: progreso por proyecto ---

    private List<ProjectMinutes> queryProjectMinutes(TimeRange range) {
        ZoneId zone = ZoneId.systemDefault();
        long start = range.startDay().atStartOfDay(zone).toInstant().toEpochMilli();
        long end = range.endDay().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
        return focusEventDao.getMinutesByProject(start, end);
    }

    private static Map<Integer, Integer> toMinutesById(List<ProjectMinutes> byProject) {
        Map<Integer, Integer> map = new HashMap<>();
        if (byProject != null) {
            for (ProjectMinutes pm : byProject) {
                map.put(pm.projectId, pm.minutes);
            }
        }
        return map;
    }

    /**
     * Compone las filas de la banda. Pertenencia: un proyecto aparece si está ACTIVO, O si tuvo
     * minutos atribuidos dentro del rango — así la banda es retrospectiva (un proyecto que
     * terminaste el mes pasado sigue apareciendo en la semana en que trabajaste en él) sin crecer
     * sin límite según se acumulan proyectos completados. Orden: minutos del rango desc, luego
     * nombre — el mismo criterio que la gráfica, porque responden a la misma pregunta.
     */
    private void rebuildProjectRows() {
        List<ProjectWithCounts> counts = latestProjectCounts;
        if (counts == null) {
            projectRows.setValue(Collections.emptyList());
            return;
        }
        Map<Integer, Integer> minutes = projectMinutesInRange.getValue();
        long now = System.currentTimeMillis();

        List<ProjectProgressRow> rows = new ArrayList<>(counts.size());
        for (ProjectWithCounts pwc : counts) {
            Project project = pwc.project;
            if (project == null) continue;
            Integer m = minutes == null ? null : minutes.get(project.id);
            int minutesInRange = m == null ? 0 : m;
            if (project.status != Project.STATUS_ACTIVE && minutesInRange <= 0) continue;

            rows.add(new ProjectProgressRow(project.id, project.name, project.colorIndex,
                    pwc.doneCount, pwc.totalCount, minutesInRange,
                    ProjectDeadlineState.from(project.softDeadlineMillis, now,
                            ProjectDeadlineState.isFinished(project.status, pwc.doneCount, pwc.totalCount))));
        }
        Collections.sort(rows, (a, b) -> {
            int byMinutes = Integer.compare(b.minutesInRange, a.minutesInRange);
            if (byMinutes != 0) return byMinutes;
            String an = a.name == null ? "" : a.name;
            String bn = b.name == null ? "" : b.name;
            return an.compareToIgnoreCase(bn);
        });
        projectRows.setValue(rows);
    }

    // --- tarjeta Tus apps ---

    // Re-evalúa el acceso a uso y recarga la tarjeta. Lo llama el Fragment en onResume() (al
    // volver de Ajustes o de Screen time no hay callback ni cambio de configuración).
    public void refreshUsageAccess() {
        executorService.execute(this::publishAppsCard);
    }

    // Reevalúa si el monitor de límites debe seguir armado (llamarlo, p.ej., en onResume).
    public void armUsageLimitMonitor() {
        executorService.execute(usageLimitScheduler::arm);
    }

    // executorService es el @IoExecutor compartido de la app (Hilt @Singleton) -- ya no se cierra
    // aquí; onCleared() no tiene nada más que liberar.
}
