package com.example.bbettercalendar.ui.screentime;

import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.DbWriteExecutor;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.usage.UsageLimitNotifier;
import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.usage.UsageAccess;
import com.example.bbettercalendar.usage.UsageStatsRepository;
import com.example.bbettercalendar.usage.limits.UsageLimitChecker;
import com.example.bbettercalendar.usage.limits.UsageLimitScheduler;
import com.example.bbettercalendar.usage.limits.WarnedTodayStore;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

// ViewModel de "Detalle por app" (u20, html 10). Lectura de BD / queryEvents y escrituras en los
// executors; se publica con postValue (regla #3). La app se fija con setPackage() desde el
// argumento de navegacion; no importa MPAndroidChart (entrega AppDetailState plano).
@HiltViewModel
public class AppDetailViewModel extends AndroidViewModel {

    private final MutableLiveData<AppDetailState> state = new MutableLiveData<>();
    private final ExecutorService executor;
    private final ExecutorService dbWriteExecutor;
    private final AppRuleDAO appRuleDao;
    private final UsageStatsRepository usageRepo;
    private final UsageLimitScheduler limitScheduler;
    private final UsageLimitChecker limitChecker;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile String packageName;
    private volatile boolean month;

    @Inject
    public AppDetailViewModel(@NonNull Application application, AppRuleDAO appRuleDao,
                              @IoExecutor ExecutorService executor,
                              @DbWriteExecutor ExecutorService dbWriteExecutor) {
        super(application);
        this.appRuleDao = appRuleDao;
        this.executor = executor;
        this.dbWriteExecutor = dbWriteExecutor;
        usageRepo = new UsageStatsRepository(application);
        // Mismo montaje que ProgressViewModel: el cambio de limite evalua y rearma ya mismo.
        AlarmManager alarmManager = (AlarmManager) application.getSystemService(Context.ALARM_SERVICE);
        WarnedTodayStore warnedStore = new WarnedTodayStore(application);
        limitScheduler = new UsageLimitScheduler(application, alarmManager, appRuleDao, warnedStore);
        limitChecker = new UsageLimitChecker(application, appRuleDao, warnedStore,
                new UsageLimitNotifier(application, new BBetterNotifier(application)));
    }

    public LiveData<AppDetailState> getState() {
        return state;
    }

    /** Fija la app (una sola vez por pantalla) y la carga. */
    public void setPackage(@NonNull String pkg) {
        if (pkg.equals(packageName)) return;
        packageName = pkg;
        refresh();
    }

    public boolean isMonth() {
        return month;
    }

    public void setMonth(boolean month) {
        if (this.month == month) return;
        this.month = month;
        refresh();
    }

    /** Recalcula todo (onResume: al volver de Ajustes no hay callback). */
    public void refresh() {
        if (packageName == null) return;
        executor.execute(this::compute);
    }

    /** Limite diario en minutos (0 = sin limite). */
    public void setDailyLimit(int minutes) {
        final String pkg = packageName;
        if (pkg == null) return;
        dbWriteExecutor.execute(() -> {
            appRuleDao.setDailyLimit(pkg, minutes);
            // Sin limite no hay nada que hacer cumplir: se apaga el candado para no dejarlo
            // armado y mudo (la fila de Screen time lo mostraria activo sin efecto).
            if (minutes <= 0) appRuleDao.setEnforceAtLimit(pkg, false);
            executor.execute(() -> {
                limitChecker.run();
                limitScheduler.arm();
                compute();
            });
        });
    }

    public void setWarnBefore(int minutes) {
        final String pkg = packageName;
        if (pkg == null) return;
        dbWriteExecutor.execute(() -> {
            appRuleDao.setWarnBefore(pkg, minutes);
            executor.execute(() -> {
                limitChecker.run();
                compute();
            });
        });
    }

    public void setEnforceAtLimit(boolean enforce) {
        final String pkg = packageName;
        if (pkg == null) return;
        dbWriteExecutor.execute(() -> {
            appRuleDao.setEnforceAtLimit(pkg, enforce);
            executor.execute(this::compute);
        });
    }

    /** "Stop tracking": la app sale de las listas; la regla (limite, aviso) se conserva. */
    public void stopTracking(@Nullable Runnable onDone) {
        final String pkg = packageName;
        if (pkg == null) return;
        dbWriteExecutor.execute(() -> {
            appRuleDao.setTracked(pkg, false);
            executor.execute(limitScheduler::arm);
            if (onDone != null) mainHandler.post(onDone);
        });
    }

    // Corre SIEMPRE en el executor.
    private void compute() {
        final String pkg = packageName;
        if (pkg == null) return;
        boolean access = UsageAccess.hasUsageAccess(getApplication());
        boolean isMonth = month;
        int days = isMonth ? AppDetailState.MONTH_DAYS : AppDetailState.WEEK_DAYS;
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(days - 1L);

        long[] current = new long[days];
        boolean hasPrevious = false;
        int delta = 0;
        if (access) {
            // Un solo recorrido: el periodo anterior (mismo largo) + el actual.
            long[] both = usageRepo.foregroundMillisByDay(pkg, firstDay.minusDays(days), days * 2);
            long prevSum = 0L;
            long curSum = 0L;
            for (int i = 0; i < days; i++) {
                prevSum += both[i];
                current[i] = both[days + i];
                curSum += current[i];
            }
            if (prevSum > 0L) {
                hasPrevious = true;
                delta = (int) Math.round((curSum - prevSum) * 100.0 / prevSum);
            }
        }
        long total = 0L;
        for (long v : current) total += v;

        AppRule rule = appRuleDao.getByPackage(pkg);
        boolean tracked = rule != null && rule.tracked;
        int limit = rule == null ? 0 : rule.dailyLimitMinutes;
        int warn = rule == null ? 5 : rule.warnBeforeMinutes;
        boolean enforce = rule != null && rule.enforceAtLimit;

        state.postValue(new AppDetailState(access, isMonth, tracked, limit, warn, enforce,
                current[days - 1], total / days, hasPrevious, delta, firstDay, current));
    }
}
