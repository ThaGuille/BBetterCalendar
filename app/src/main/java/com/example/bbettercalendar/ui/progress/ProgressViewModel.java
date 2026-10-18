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
// MPAndroidChart: produce un ChartBundle plano que el Fragment/adapter convierte en entries.
@HiltViewModel
public class ProgressViewModel extends AndroidViewModel {

    // Conservado para que ProjectsFragment (que reutiliza este VM) siga compilando.
    private final MutableLiveData<String> mText;

    private final MutableLiveData<TimeRange> selectedRange;
    private final MutableLiveData<ChartBundle> charts;

    // Banda 2 (progreso por proyecto, spec project-deadlines-progress). Se compone de DOS fuentes
    // vivas: los proyectos con sus recuentos (LiveData de Room, se invalida sola al escribir en
    // project O en calendarEntry) y los minutos del rango seleccionado (los publica applyRange
    // desde el executor). A diferencia de la banda de uso, no depende de ningún permiso.
    private final MutableLiveData<Map<Integer, Integer>> projectMinutesInRange;
    private final MediatorLiveData<List<ProjectProgressRow>> projectRows;
    private List<ProjectWithCounts> latestProjectCounts;

    // u17: tiles + "Past 12 weeks" (dependen del rango) y tarjeta Screen time & blocking (de hoy).
    private final MutableLiveData<ProgressOverview> overview;
    private final MutableLiveData<ProgressOverview.BlockingCard> blockingCard;

    private final ExecutorService executorService;
    private final DailyStatDAO dailyStatDao;
    private final FocusEventDAO focusEventDao;
    private final StatsDAO statsDao;
    private final CalendarEntryDAO calendarEntryDao;
    private final FocusInsightsRepository insights;
    private final AppRuleDAO appRuleDao;
    private final UsageStatsRepository usageRepo;
    private final UsageLimitScheduler usageLimitScheduler;

    private static final DateTimeFormatter X_LABEL =
            DateTimeFormatter.ofPattern("d/M", Locale.getDefault());

    @Inject
    public ProgressViewModel(@NonNull Application application, DailyStatDAO dailyStatDao,
                              FocusEventDAO focusEventDao, StatsDAO statsDao, AppRuleDAO appRuleDao,
                              ProjectsRepository projectsRepository,
                              CalendarEntryDAO calendarEntryDao, FocusInsightsRepository insights,
                              @IoExecutor ExecutorService executorService) {
        super(application);
        mText = new MutableLiveData<>();
        mText.setValue("This is progress fragment");
        selectedRange = new MutableLiveData<>();
        charts = new MutableLiveData<>();
        projectMinutesInRange = new MutableLiveData<>();
        projectRows = new MediatorLiveData<>();
        overview = new MutableLiveData<>();
        blockingCard = new MutableLiveData<>();

        this.calendarEntryDao = calendarEntryDao;
        this.insights = insights;
        this.dailyStatDao = dailyStatDao;
        this.focusEventDao = focusEventDao;
        this.statsDao = statsDao;
        this.appRuleDao = appRuleDao;
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
    public LiveData<ChartBundle> getCharts() { return charts; }
    public LiveData<List<ProjectProgressRow>> getProjectRows() { return projectRows; }
    public LiveData<ProgressOverview> getOverview() { return overview; }
    public LiveData<ProgressOverview.BlockingCard> getBlockingCard() { return blockingCard; }

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
            // Una sola query de minutos por proyecto alimenta las dos superficies: la página de
            // proyectos del carrusel (nombre -> minutos) y la banda (id -> minutos).
            List<ProjectMinutes> byProject = queryProjectMinutes(range);
            charts.postValue(buildBundle(range, byProject));
            projectMinutesInRange.postValue(toMinutesById(byProject));
        });
        executorService.execute(() -> refreshOverview(range));
        executorService.execute(this::publishBlockingCard);
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

        overview.postValue(new ProgressOverview(focus.currentTotal, focus.hasDeltaPercent(),
                focus.deltaPercent(), sessions, sessions / (double) elapsedDays,
                tasksDone, tasksDone - tasksPrev, cur, prv, labels,
                trend.currentTotal, trend.deltaAbsolute(),
                consistency, prevActive > 0, consistency.activeDays - prevActive));
    }

    // Tarjeta "Screen time & blocking": uso de HOY de las apps seguidas frente a la suma de sus
    // limites diarios. Corre en el executor (queryEvents + DAO) y publica con postValue.
    private void publishBlockingCard() {
        if (!UsageAccess.hasUsageAccess(getApplication())) {
            blockingCard.postValue(new ProgressOverview.BlockingCard(true, 0, 0L, 0,
                    Collections.<ProgressOverview.BlockingCard.AppChip>emptyList()));
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        long begin = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        List<AppRule> tracked = appRuleDao.getTracked();
        Map<String, Long> foreground = tracked.isEmpty()
                ? Collections.<String, Long>emptyMap() : usageRepo.foregroundMillis(begin, end);

        long usedLimited = 0L;
        long usedAll = 0L;
        int goal = 0;
        List<ProgressOverview.BlockingCard.AppChip> chips = new ArrayList<>();
        for (AppRule rule : tracked) {
            Long m = foreground.get(rule.packageName);
            long millis = m == null ? 0L : m;
            usedAll += millis;
            boolean limited = rule.dailyLimitMinutes > 0;
            long limitMillis = rule.dailyLimitMinutes * 60_000L;
            if (limited) {
                usedLimited += millis;
                goal += rule.dailyLimitMinutes;
            }
            boolean blocked = limited && rule.enforceAtLimit && millis >= limitMillis;
            boolean near = limited && millis * 100L >= limitMillis * 75L;
            chips.add(new ProgressOverview.BlockingCard.AppChip(rule.packageName,
                    usageRepo.labelFor(rule.packageName), millis, blocked, near));
        }
        Collections.sort(chips, (a, b) -> Long.compare(b.millis, a.millis));
        if (chips.size() > 3) chips = new ArrayList<>(chips.subList(0, 3));

        blockingCard.postValue(new ProgressOverview.BlockingCard(false, tracked.size(),
                goal > 0 ? usedLimited : usedAll, goal, chips));
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

    // --- tarjeta Screen time & blocking ---

    // Re-evalúa el acceso a uso y recarga la tarjeta. Lo llama el Fragment en onResume() (al
    // volver de Ajustes o de Screen time no hay callback ni cambio de configuración).
    public void refreshUsageAccess() {
        executorService.execute(this::publishBlockingCard);
    }

    // Reevalúa si el monitor de límites debe seguir armado (llamarlo, p.ej., en onResume).
    public void armUsageLimitMonitor() {
        executorService.execute(usageLimitScheduler::arm);
    }

    private ChartBundle buildBundle(TimeRange range, List<ProjectMinutes> byProject) {
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

        // YEAR (u17): 365 puntos no se leen en el carrusel heredado -> daily_stat agregado por mes
        // (12 buckets, etiqueta "MMM"). Las series por hora y por proyecto no cambian.
        if (range.granularity == Granularity.YEAR) {
            String[] monthLabels = new String[12];
            int[] monthFocus = new int[12];
            int[] monthFails = new int[12];
            LocalDate day = start;
            for (int i = 0; i < days; i++) {
                int m = day.getMonthValue() - 1;
                monthFocus[m] += focusMinutes[i];
                monthFails[m] += fails[i];
                day = day.plusDays(1);
            }
            for (int m = 0; m < 12; m++) {
                monthLabels[m] = start.withMonth(m + 1).format(TREND_MONTH);
            }
            labels = monthLabels;
            focusMinutes = monthFocus;
            fails = monthFails;
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

        // Página de proyectos del carrusel: la query ya viene ordenada por minutos desc.
        int projectCount = byProject == null ? 0 : byProject.size();
        String[] projectLabels = new String[projectCount];
        int[] projectMinutes = new int[projectCount];
        for (int i = 0; i < projectCount; i++) {
            ProjectMinutes pm = byProject.get(i);
            projectLabels[i] = pm.projectName != null ? pm.projectName : "";
            projectMinutes[i] = pm.minutes;
        }

        return new ChartBundle(range.granularity, labels, focusMinutes, fails,
                focusMinutesByHour, focusByHour, failByHour, projectLabels, projectMinutes);
    }

    // executorService es el @IoExecutor compartido de la app (Hilt @Singleton) -- ya no se cierra
    // aquí; onCleared() no tiene nada más que liberar.
}
