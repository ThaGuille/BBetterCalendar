package com.example.bbettercalendar.ui.screentime;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.stats.AppUsageDaily;
import com.example.bbettercalendar.stats.AppUsageDailyDAO;
import com.example.bbettercalendar.stats.ConsentRecord;
import com.example.bbettercalendar.stats.ConsentRecordDAO;
import com.example.bbettercalendar.stats.InsightPhrases;
import com.example.bbettercalendar.stats.UsageInsights;
import com.example.bbettercalendar.usage.UsageAccess;
import com.example.bbettercalendar.usage.UsageStatsRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

// ViewModel de "Tiempo de pantalla" (mejora-visual u9). Todo el trabajo de BD / queryEvents corre en
// el executor y se publica con postValue (regla #3). El historial sale de app_usage_daily y el dia de
// hoy del uso en vivo (UsageInsights ya sabe combinarlos). Entrega ScreenTimeState plano.
@HiltViewModel
public class ScreenTimeViewModel extends AndroidViewModel {

    public static final int WEEK_DAYS = 7;
    public static final int MONTH_DAYS = 30;
    /** Apps de "Uso por app" (las seguidas se anaden aunque no entren). */
    static final int TOP_APPS = 8;
    /** Apps con color propio en "Dia a dia"; el resto va a "Otras". */
    static final int DAILY_SERIES = 3;
    /** Ventana de historial que se lee de la BD (cubre periodo + anterior + el "antes" de un limite). */
    private static final int HISTORY_DAYS = 90;

    public interface DisclosureDecision {
        void decided(boolean needsDisclosure);
    }

    private final MutableLiveData<ScreenTimeState> state = new MutableLiveData<>();
    private final ExecutorService executor;
    private final AppRuleDAO appRuleDao;
    private final AppUsageDailyDAO appUsageDao;
    private final ConsentRecordDAO consentRecordDao;
    private final UsageStatsRepository usageRepo;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean month;

    @Inject
    public ScreenTimeViewModel(@NonNull Application application, AppRuleDAO appRuleDao,
                               AppUsageDailyDAO appUsageDao, ConsentRecordDAO consentRecordDao,
                               @IoExecutor ExecutorService executor) {
        super(application);
        this.appRuleDao = appRuleDao;
        this.appUsageDao = appUsageDao;
        this.consentRecordDao = consentRecordDao;
        this.executor = executor;
        usageRepo = new UsageStatsRepository(application);
    }

    public LiveData<ScreenTimeState> getState() {
        return state;
    }

    public boolean isMonth() {
        return month;
    }

    public void setMonth(boolean month) {
        if (this.month == month) return;
        this.month = month;
        refresh();
    }

    /** Recalcula todo (onResume: al volver de Ajustes, del picker o del detalle no hay callback). */
    public void refresh() {
        executor.execute(this::compute);
    }

    /** La divulgacion de Usage Access solo hace falta si aun no hay consentimiento vigente. */
    public void resolveUsageAccessCta(DisclosureDecision decision) {
        executor.execute(() -> {
            ConsentRecord rec = consentRecordDao.get(ConsentRecord.KEY_USAGE_ACCESS);
            boolean needs = rec == null
                    || rec.disclosureVersion < ConsentRecord.USAGE_ACCESS_DISCLOSURE_VERSION;
            mainHandler.post(() -> decision.decided(needs));
        });
    }

    // Corre SIEMPRE en el executor.
    private void compute() {
        boolean access = UsageAccess.hasUsageAccess(getApplication());
        boolean isMonth = month;
        int range = isMonth ? MONTH_DAYS : WEEK_DAYS;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(range - 1L);

        // Uso de hoy en vivo (minutos enteros); sin permiso no hay medicion en vivo y hoy sale de la BD.
        Map<String, Integer> live = null;
        if (access) {
            long begin = today.atStartOfDay(zone).toInstant().toEpochMilli();
            long end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            live = new HashMap<>();
            for (Map.Entry<String, Long> en : usageRepo.foregroundMillis(begin, end).entrySet()) {
                int minutes = (int) (en.getValue() / 60_000L);
                if (minutes > 0) live.put(en.getKey(), minutes);
            }
            // Sin uso en vivo todavia hoy, lo que haya en la BD de hoy se conserva.
            if (live.isEmpty()) live = null;
        }
        List<AppUsageDaily> rows = appUsageDao.getBetween(
                today.minusDays(HISTORY_DAYS - 1L).toString(), today.toString());
        List<AppRule> rules = appRuleDao.getAll();
        Map<String, AppRule> ruleByPkg = new HashMap<>();
        for (AppRule r : rules) ruleByPkg.put(r.packageName, r);

        List<UsageInsights.AppAverage> averages = UsageInsights.dailyAverages(rows, live, today, range);
        if (averages.isEmpty()) {
            state.postValue(ScreenTimeState.empty(access, isMonth, firstDay));
            return;
        }
        List<UsageInsights.AppAverage> previous = UsageInsights.dailyAverages(
                rows, null, today.minusDays(range), range);

        float total = 0f;
        for (UsageInsights.AppAverage a : averages) total += a.averageMinutes;
        float prevTotal = 0f;
        for (UsageInsights.AppAverage a : previous) prevTotal += a.averageMinutes;
        boolean hasDelta = prevTotal > 0f && total > 0f;
        int delta = hasDelta ? Math.round((total - prevTotal) * 100f / prevTotal) : 0;

        // Uso por app: top N + las seguidas que no entren.
        Set<String> suggested = new HashSet<>();
        for (UsageInsights.AppAverage a : UsageInsights.suggested(averages, rules)) {
            suggested.add(a.packageName);
        }
        Set<String> shown = new HashSet<>();
        List<ScreenTimeState.AppRow> apps = new ArrayList<>();
        for (UsageInsights.AppAverage a : UsageInsights.topN(averages, TOP_APPS)) {
            shown.add(a.packageName);
            apps.add(row(a.packageName, (float) a.averageMinutes, ruleByPkg.get(a.packageName),
                    suggested.contains(a.packageName)));
        }
        for (AppRule r : appRuleDao.getTracked()) {
            if (shown.add(r.packageName)) {
                apps.add(row(r.packageName, 0f, r, false));
            }
        }
        Collections.sort(apps, (x, y) -> Float.compare(y.avgMinutes, x.avgMinutes));

        // Dia a dia: 3 apps principales + otras.
        String[] top = new String[DAILY_SERIES];
        String[] topNames = new String[DAILY_SERIES];
        for (int i = 0; i < DAILY_SERIES; i++) {
            if (i < averages.size()) {
                top[i] = averages.get(i).packageName;
                topNames[i] = usageRepo.labelFor(top[i]);
            } else {
                topNames[i] = "";
            }
        }
        Map<LocalDate, Map<String, Integer>> byDay = new HashMap<>();
        for (AppUsageDaily r : rows) {
            LocalDate d = parse(r.day);
            if (d == null || d.isBefore(firstDay) || d.isAfter(today)
                    || (d.equals(today) && live != null)) {
                continue;
            }
            byDay.computeIfAbsent(d, k -> new HashMap<>()).merge(r.packageName, r.minutes, Integer::sum);
        }
        if (live != null) byDay.put(today, live);
        float[][] daily = new float[range][DAILY_SERIES + 1];
        float[] weekdaySum = new float[7];
        int[] weekdayDays = new int[7];
        for (int i = 0; i < range; i++) {
            LocalDate d = firstDay.plusDays(i);
            Map<String, Integer> day = byDay.get(d);
            if (day == null) continue;
            float sum = 0f;
            float named = 0f;
            for (Map.Entry<String, Integer> en : day.entrySet()) {
                sum += en.getValue();
            }
            for (int s = 0; s < DAILY_SERIES; s++) {
                Integer m = top[s] == null ? null : day.get(top[s]);
                daily[i][s] = m == null ? 0f : m;
                named += daily[i][s];
            }
            daily[i][DAILY_SERIES] = Math.max(0f, sum - named);
            if (sum > 0f) {
                int wd = d.getDayOfWeek().getValue() - 1;
                weekdaySum[wd] += sum;
                weekdayDays[wd]++;
            }
        }
        float[] weekdayAvg = new float[7];
        int peak = -1;
        for (int w = 0; w < 7; w++) {
            weekdayAvg[w] = weekdayDays[w] == 0 ? 0f : weekdaySum[w] / weekdayDays[w];
        }
        if (InsightPhrases.hasBestColumn(weekdayAvg)) {
            float best = 0f;
            for (int w = 0; w < 7; w++) {
                if (weekdayAvg[w] > best) {
                    best = weekdayAvg[w];
                    peak = w;
                }
            }
        }

        // Antes y despues del limite.
        List<ScreenTimeState.BeforeAfterRow> ba = new ArrayList<>();
        float recovered = 0f;
        for (AppRule r : appRuleDao.getLimited()) {
            UsageInsights.BeforeAfter result = UsageInsights.beforeAfter(rows, live, r, today, zone);
            if (result == null) continue;
            ba.add(new ScreenTimeState.BeforeAfterRow(usageRepo.labelFor(r.packageName),
                    (float) result.beforeAverage, (float) result.afterAverage));
            if (result.improved()) recovered += (float) (result.beforeAverage - result.afterAverage);
        }

        state.postValue(new ScreenTimeState(access, isMonth, total, hasDelta, delta, apps,
                firstDay, daily, topNames, peak, peak < 0 ? 0f : weekdayAvg[peak], ba, recovered, 0));
    }

    private ScreenTimeState.AppRow row(String pkg, float avg, AppRule rule, boolean suggested) {
        boolean limited = rule != null && rule.tracked && rule.dailyLimitMinutes > 0;
        return new ScreenTimeState.AppRow(pkg, usageRepo.labelFor(pkg), avg,
                limited ? rule.dailyLimitMinutes : 0,
                limited && rule.enforceAtLimit, suggested);
    }

    private static LocalDate parse(String day) {
        try {
            return LocalDate.parse(day);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
