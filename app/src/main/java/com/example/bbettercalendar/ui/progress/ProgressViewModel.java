package com.example.bbettercalendar.ui.progress;

import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.usage.UsageLimitNotifier;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDeadlineState;
import com.example.bbettercalendar.projects.ProjectWithCounts;
import com.example.bbettercalendar.projects.ProjectsRepository;
import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.stats.ConsentRecord;
import com.example.bbettercalendar.stats.ConsentRecordDAO;
import com.example.bbettercalendar.stats.DailyStat;
import com.example.bbettercalendar.stats.DailyStatDAO;
import com.example.bbettercalendar.stats.FocusEvent;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.ProjectMinutes;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;
import com.example.bbettercalendar.usage.UsageAccess;
import com.example.bbettercalendar.usage.UsageStatsRepository;
import com.example.bbettercalendar.usage.limits.UsageLimitChecker;
import com.example.bbettercalendar.usage.limits.UsageLimitScheduler;
import com.example.bbettercalendar.usage.limits.WarnedTodayStore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // Banda 3 (uso de apps). Independiente de los gráficos: los gráficos nunca dependen del permiso.
    private final MutableLiveData<UsageBandState> usageState;
    private final MutableLiveData<List<AppUsageRow>> apps;
    private final MutableLiveData<Long> screenTimeMillis;

    private final ExecutorService executorService;
    private final ExecutorService dbWriteExecutor;
    private final DailyStatDAO dailyStatDao;
    private final FocusEventDAO focusEventDao;
    private final StatsDAO statsDao;
    private final AppRuleDAO appRuleDao;
    private final ConsentRecordDAO consentRecordDao;
    private final UsageStatsRepository usageRepo;
    private final UsageLimitScheduler usageLimitScheduler;
    private final UsageLimitChecker usageLimitChecker;
    private final Handler mainHandler;

    private static final DateTimeFormatter X_LABEL =
            DateTimeFormatter.ofPattern("d/M", Locale.getDefault());

    // Decisión del CTA "Turn on usage access": ¿hay que mostrar la divulgación o ir directo a Ajustes?
    public interface DisclosureDecision {
        void decided(boolean needsDisclosure);
    }

    @Inject
    public ProgressViewModel(@NonNull Application application, DailyStatDAO dailyStatDao,
                              FocusEventDAO focusEventDao, StatsDAO statsDao, AppRuleDAO appRuleDao,
                              ConsentRecordDAO consentRecordDao, ProjectsRepository projectsRepository,
                              @IoExecutor ExecutorService executorService,
                              @DbWriteExecutor ExecutorService dbWriteExecutor) {
        super(application);
        mText = new MutableLiveData<>();
        mText.setValue("This is progress fragment");
        selectedRange = new MutableLiveData<>();
        charts = new MutableLiveData<>();
        projectMinutesInRange = new MutableLiveData<>();
        projectRows = new MediatorLiveData<>();
        usageState = new MutableLiveData<>();
        apps = new MutableLiveData<>();
        screenTimeMillis = new MutableLiveData<>();

        this.dailyStatDao = dailyStatDao;
        this.focusEventDao = focusEventDao;
        this.statsDao = statsDao;
        this.appRuleDao = appRuleDao;
        this.consentRecordDao = consentRecordDao;
        usageRepo = new UsageStatsRepository(application);
        AlarmManager alarmManager = (AlarmManager) application.getSystemService(Context.ALARM_SERVICE);
        WarnedTodayStore warnedStore = new WarnedTodayStore(application);
        usageLimitScheduler = new UsageLimitScheduler(application, alarmManager, appRuleDao, warnedStore);
        usageLimitChecker = new UsageLimitChecker(application, appRuleDao, warnedStore,
                new UsageLimitNotifier(application, new BBetterNotifier(application)));
        mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = executorService;
        this.dbWriteExecutor = dbWriteExecutor;

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
    public LiveData<UsageBandState> getUsageState() { return usageState; }
    public LiveData<List<AppUsageRow>> getApps() { return apps; }
    public LiveData<Long> getScreenTimeMillis() { return screenTimeMillis; }

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
    // gráficos y del uso de apps se hace en el executor y se publica con postValue.
    private void applyRange(TimeRange range) {
        selectedRange.setValue(range);
        executorService.execute(() -> {
            // Una sola query de minutos por proyecto alimenta las dos superficies: la página de
            // proyectos del carrusel (nombre -> minutos) y la banda (id -> minutos).
            List<ProjectMinutes> byProject = queryProjectMinutes(range);
            charts.postValue(buildBundle(range, byProject));
            projectMinutesInRange.postValue(toMinutesById(byProject));
        });
        executorService.execute(() -> refreshUsage(range));
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
                    ProjectDeadlineState.from(project.softDeadlineMillis, now)));
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

    // --- banda 3: uso de apps ---

    // Re-evalúa el acceso a uso y recarga la lista para el rango actual. Lo llama el Fragment en
    // onResume() (al volver de Ajustes o del picker no hay callback ni cambio de configuración).
    public void refreshUsageAccess() {
        TimeRange current = selectedRange.getValue();
        if (current == null) return;
        executorService.execute(() -> refreshUsage(current));
    }

    // Corre SIEMPRE en el executor (queryEvents + DAO fuera del hilo principal, regla #3).
    // Si no hay permiso -> LOCKED y no se lee nada. Con permiso pero sin apps -> EMPTY_NO_APPS.
    // Con apps -> READY con la lista (apps seguidas ⨝ uso del rango). El total de pantalla del
    // rango se publica siempre que haya permiso (cabecera de la banda).
    private void refreshUsage(TimeRange range) {
        if (!UsageAccess.hasUsageAccess(getApplication())) {
            apps.postValue(Collections.emptyList());
            screenTimeMillis.postValue(0L);
            usageState.postValue(UsageBandState.LOCKED);
            return;
        }

        ZoneId zone = ZoneId.systemDefault();
        long begin = range.startDay().atStartOfDay(zone).toInstant().toEpochMilli();
        long end = range.endDay().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        screenTimeMillis.postValue(usageRepo.totalScreenTime(begin, end));

        List<AppRule> tracked = appRuleDao.getTracked();
        if (tracked.isEmpty()) {
            apps.postValue(Collections.emptyList());
            usageState.postValue(UsageBandState.EMPTY_NO_APPS);
            return;
        }

        Map<String, Long> foreground = usageRepo.foregroundMillis(begin, end);
        // El límite es diario: sólo tiene sentido mostrar "usado/límite" cuando el rango
        // seleccionado es un único día — en Semana/Mes el usado acumulado no es comparable
        // contra un límite diario (saldría "superado" casi siempre). dailyLimitMinutes se
        // conserva siempre (lo necesita AppLimitDialog para precargar el valor actual).
        boolean showLimit = range.granularity == Granularity.DAY;
        List<AppUsageRow> rows = new ArrayList<>(tracked.size());
        for (AppRule rule : tracked) {
            Long millis = foreground.get(rule.packageName);
            rows.add(new AppUsageRow(rule.packageName, usageRepo.labelFor(rule.packageName),
                    millis == null ? 0L : millis, rule.dailyLimitMinutes, showLimit,
                    rule.enforceAtLimit));
        }
        // Más usadas primero.
        Collections.sort(rows, (a, b) -> Long.compare(b.foregroundMillis, a.foregroundMillis));

        apps.postValue(rows);
        usageState.postValue(UsageBandState.READY);
    }

    // Phase 3: fija (o borra, con minutes=0) el límite diario de una app seguida. Escribe fuera
    // del hilo principal (regla #3), evalúa el límite ya mismo (el uso de hoy puede ya superar el
    // umbral de aviso si la app llevaba tiempo abierta antes de fijar el límite — si no, la primera
    // comprobación periódica no llega hasta dentro de 5 min y puede saltarse la ventana de aviso),
    // reprograma el monitor de límites y refresca la lista.
    public void setDailyLimit(String packageName, int minutes) {
        dbWriteExecutor.execute(() -> {
            appRuleDao.setDailyLimit(packageName, minutes);
            // El seguimiento (checker/scheduler/refreshUsage) no es parte de la escritura -- se
            // reenvía a @IoExecutor para que corra en el mismo pool que applyRange()/
            // refreshUsageAccess() (repository-layer-consolidation, T2: evita que refreshUsage()
            // quede reenganchable desde dos pools sin orden entre sí, hallazgo del code-reviewer
            // en la verificación de este tramo).
            executorService.execute(() -> {
                usageLimitChecker.run();
                usageLimitScheduler.arm();
                TimeRange current = selectedRange.getValue();
                if (current != null) refreshUsage(current);
            });
        });
    }

    // Reevalúa si el monitor de límites debe seguir armado (llamarlo, p.ej., en onResume).
    public void armUsageLimitMonitor() {
        executorService.execute(usageLimitScheduler::arm);
    }

    // Phase 4a: activa/desactiva "hacer cumplir el límite" para una app (toggle 🚫 de la fila).
    // Escribe fuera del hilo principal (regla #3) y refresca la lista. El servicio de Accesibilidad
    // observa observeEnforced(), así que recoge el cambio solo — aquí no hace falta notificarlo.
    public void setEnforceAtLimit(String packageName, boolean enforce) {
        dbWriteExecutor.execute(() -> {
            appRuleDao.setEnforceAtLimit(packageName, enforce);
            // Ver comentario en setDailyLimit(): refreshUsage() se reenvía a @IoExecutor, el
            // único pool desde el que se dispara en toda la clase.
            executorService.execute(() -> {
                TimeRange current = selectedRange.getValue();
                if (current != null) refreshUsage(current);
            });
        });
    }

    // El CTA de la tarjeta LOCKED: comprueba (fuera del hilo principal) si ya hay un consentimiento
    // vigente. needsDisclosure=true -> mostrar la divulgación antes de Ajustes; false -> ir directo.
    public void resolveUsageAccessCta(DisclosureDecision decision) {
        executorService.execute(() -> {
            ConsentRecord rec = consentRecordDao.get(ConsentRecord.KEY_USAGE_ACCESS);
            boolean needsDisclosure = rec == null
                    || rec.disclosureVersion < ConsentRecord.USAGE_ACCESS_DISCLOSURE_VERSION;
            mainHandler.post(() -> decision.decided(needsDisclosure));
        });
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
