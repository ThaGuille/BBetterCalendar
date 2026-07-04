package com.example.bbettercalendar.usage.limits;

import android.content.Context;

import com.example.bbettercalendar.notifications.usage.UsageLimitNotifier;
import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.usage.UsageStatsRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

// El núcleo del chequeo periódico (regla #3: llamado siempre desde el executor del receiver).
// Para cada app seguida con límite: compara el uso de HOY (desde medianoche) contra
// limite-warnBefore y contra el límite, y dispara como mucho un aviso + una notificación de
// límite por app y día (de-dup vía WarnedTodayStore).
@Singleton
public class UsageLimitChecker {

    private final AppRuleDAO appRuleDao;
    private final UsageStatsRepository usageRepo;
    private final WarnedTodayStore warnedStore;
    private final UsageLimitNotifier notifier;

    @Inject
    public UsageLimitChecker(@ApplicationContext Context context, AppRuleDAO appRuleDao,
                              WarnedTodayStore warnedStore, UsageLimitNotifier notifier) {
        this.appRuleDao = appRuleDao;
        this.usageRepo = new UsageStatsRepository(context);
        this.warnedStore = warnedStore;
        this.notifier = notifier;
    }

    public void run() {
        warnedStore.clearIfNewDay();

        List<AppRule> limited = appRuleDao.getLimited();
        if (limited.isEmpty()) return;

        ZoneId zone = ZoneId.systemDefault();
        long begin = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli();
        long end = System.currentTimeMillis();
        Map<String, Long> foreground = usageRepo.foregroundMillis(begin, end);

        for (AppRule rule : limited) {
            Long usedMillis = foreground.get(rule.packageName);
            long usedMinutes = TimeUnit.MILLISECONDS.toMinutes(usedMillis == null ? 0L : usedMillis);

            // Sólo hay una ventana de aviso previo con sentido si el límite es más largo que el
            // propio lead time (rule.dailyLimitMinutes > rule.warnBeforeMinutes); si no, el umbral
            // de aviso caería a <= 0 y el aviso dispararía de inmediato con 0 minutos de uso.
            boolean hasWarnWindow = rule.dailyLimitMinutes > rule.warnBeforeMinutes;
            if (usedMinutes >= rule.dailyLimitMinutes) {
                if (!warnedStore.hasReached(rule.packageName)) {
                    notifier.reached(rule.packageName, usageRepo.labelFor(rule.packageName));
                    warnedStore.markReached(rule.packageName);
                }
            } else if (hasWarnWindow && usedMinutes >= rule.dailyLimitMinutes - rule.warnBeforeMinutes) {
                if (!warnedStore.hasWarned(rule.packageName)) {
                    int minutesLeft = (int) (rule.dailyLimitMinutes - usedMinutes);
                    notifier.warn(rule.packageName, usageRepo.labelFor(rule.packageName), minutesLeft);
                    warnedStore.markWarned(rule.packageName);
                }
            }
        }
    }
}
