package com.example.bbettercalendar.ui.screentime;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.stats.ConsentRecord;
import com.example.bbettercalendar.stats.ConsentRecordDAO;
import com.example.bbettercalendar.stats.FocusEvent;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.usage.UsageAccess;
import com.example.bbettercalendar.usage.UsageHourBuckets;
import com.example.bbettercalendar.usage.UsageStatsRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

// ViewModel de "Screen time & blocking" (u19, html 14). Todo el trabajo de BD / queryEvents corre
// en el executor y se publica con postValue (regla #3). No importa MPAndroidChart: entrega
// ScreenTimeState plano y el Fragment dibuja.
@HiltViewModel
public class ScreenTimeViewModel extends AndroidViewModel {

    // Dias previos que forman la "media" del delta "vs avg" del tile Tracked apps.
    private static final int AVERAGE_DAYS = 7;

    public interface DisclosureDecision {
        void decided(boolean needsDisclosure);
    }

    private final MutableLiveData<ScreenTimeState> state = new MutableLiveData<>();
    private final ExecutorService executor;
    private final ExecutorService dbWriteExecutor;
    private final AppRuleDAO appRuleDao;
    private final FocusEventDAO focusEventDao;
    private final ConsentRecordDAO consentRecordDao;
    private final UsageStatsRepository usageRepo;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public ScreenTimeViewModel(@NonNull Application application, AppRuleDAO appRuleDao,
                               FocusEventDAO focusEventDao, ConsentRecordDAO consentRecordDao,
                               @IoExecutor ExecutorService executor,
                               @DbWriteExecutor ExecutorService dbWriteExecutor) {
        super(application);
        this.appRuleDao = appRuleDao;
        this.focusEventDao = focusEventDao;
        this.consentRecordDao = consentRecordDao;
        this.executor = executor;
        this.dbWriteExecutor = dbWriteExecutor;
        usageRepo = new UsageStatsRepository(application);
    }

    public LiveData<ScreenTimeState> getState() {
        return state;
    }

    /** Recalcula todo (onResume: al volver de Ajustes, del picker o del detalle no hay callback). */
    public void refresh() {
        executor.execute(this::compute);
    }

    /** Candado de una fila: escribe fuera del hilo principal y recalcula. */
    public void setEnforceAtLimit(String packageName, boolean enforce) {
        dbWriteExecutor.execute(() -> {
            appRuleDao.setEnforceAtLimit(packageName, enforce);
            executor.execute(this::compute);
        });
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
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        long begin = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        List<AppRule> tracked = appRuleDao.getTracked();
        Map<String, Long> foreground = Collections.emptyMap();
        long[] hours = new long[UsageHourBuckets.HOURS];
        long avgTotal = 0L;
        if (access) {
            foreground = usageRepo.foregroundMillis(begin, end);
            hours = usageRepo.foregroundMillisByHour(today);
            if (!tracked.isEmpty()) {
                long prevBegin = today.minusDays(AVERAGE_DAYS).atStartOfDay(zone)
                        .toInstant().toEpochMilli();
                Map<String, Long> previous = usageRepo.foregroundMillis(prevBegin, begin);
                for (AppRule rule : tracked) {
                    Long m = previous.get(rule.packageName);
                    if (m != null) avgTotal += m;
                }
            }
        }

        long trackedMillis = 0L;
        long limitedMillis = 0L;
        int totalLimit = 0;
        int enforced = 0;
        List<ScreenTimeState.AppRow> rows = new ArrayList<>(tracked.size());
        for (AppRule rule : tracked) {
            Long m = foreground.get(rule.packageName);
            long millis = m == null ? 0L : m;
            trackedMillis += millis;
            if (rule.dailyLimitMinutes > 0) {
                limitedMillis += millis;
                totalLimit += rule.dailyLimitMinutes;
                if (rule.enforceAtLimit) enforced++;
            }
            rows.add(new ScreenTimeState.AppRow(rule.packageName,
                    usageRepo.labelFor(rule.packageName), millis,
                    rule.dailyLimitMinutes, rule.enforceAtLimit));
        }
        Collections.sort(rows, (a, b) -> Long.compare(b.todayMillis, a.todayMillis));

        long avgDaily = avgTotal / AVERAGE_DAYS;
        boolean hasAverage = avgDaily > 0L;
        int delta = hasAverage
                ? (int) Math.round((trackedMillis - avgDaily) * 100.0 / avgDaily) : 0;

        // Franjas de concentracion de hoy (misma regla que ProgressViewModel.buildBundle: la hora
        // del evento FOCUS acumula sus minutos).
        int[] focusMinutes = new int[UsageHourBuckets.HOURS];
        for (FocusEvent ev : focusEventDao.getRange(begin, end - 1)) {
            if (ev.type != FocusEvent.TYPE_FOCUS) continue;
            focusMinutes[Instant.ofEpochMilli(ev.timestamp).atZone(zone).getHour()] += ev.durationMin;
        }
        boolean[] focusHours = new boolean[UsageHourBuckets.HOURS];
        for (int h = 0; h < focusHours.length; h++) {
            focusHours[h] = focusMinutes[h] > 0;
        }

        state.postValue(new ScreenTimeState(access, trackedMillis, hasAverage, delta,
                totalLimit, limitedMillis, hours, focusHours, rows, enforced));
    }
}
