package com.example.bbettercalendar.usage;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.stats.AppUsageDaily;
import com.example.bbettercalendar.stats.AppUsageDailyDAO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * mejora-visual u1 (DB v15): vuelca a {@code app_usage_daily} el uso por app de los días COMPLETOS
 * que Android aún conserva (hasta {@link #DAYS_BACK} días atrás, nunca hoy), para todas las apps con
 * uso, a través de {@link UsageStatsRepository}. Así el histórico de Tiempo de pantalla sobrevive a
 * la ventana corta de eventos del sistema.
 *
 * <ul>
 *   <li><b>Idempotente</b>: upsert por (día, paquete) y nunca baja una cifra ya guardada (un día que
 *       Android empieza a purgar devolvería menos minutos que los que ya se guardaron).</li>
 *   <li><b>Sin acceso de uso no hace nada</b> (ni lee ni escribe).</li>
 *   <li>Una vez por día civil y proceso: los días completos no cambian a lo largo del día, y el
 *       receptor de límites lo llama en cada tick.</li>
 * </ul>
 *
 * Lo llaman {@code DBMigration.onCreate()} (arranque en frío, {@link #snapshotAsync()} en el executor
 * de IO) y {@code UsageLimitReceiver} ({@link #snapshotNow()}, ya en su executor). Regla #3: nunca en
 * el hilo principal.
 */
@Singleton
public class UsageSnapshotter {

    private static final String TAG = "UsageSnapshotter";
    /** Días completos hacia atrás que se vuelcan (ayer ... hoy - 7). */
    public static final int DAYS_BACK = 7;
    private static final long MILLIS_PER_MINUTE = 60_000L;

    /** Lo que lee la instantánea: el uso del sistema tras la puerta del acceso de uso (los tests pasan uno falso). */
    public interface UsageSource {
        boolean hasUsageAccess();

        /** pkg -> milisegundos en primer plano por día local, índice i = firstDay + i. */
        @NonNull
        Map<String, long[]> foregroundMillisByDay(@NonNull LocalDate firstDay, int days);
    }

    private final UsageSource source;
    private final AppUsageDailyDAO dao;
    private final Executor executor;
    private final Supplier<LocalDate> today;
    // Día civil en el que ya se hizo la instantánea en este proceso (null = ninguna aún).
    private LocalDate snapshotDoneOn;

    @Inject
    public UsageSnapshotter(@ApplicationContext Context context, AppUsageDailyDAO dao,
                            @IoExecutor ExecutorService ioExecutor) {
        this(systemSource(context), dao, ioExecutor, LocalDate::now);
    }

    /** Para tests: fuente de uso, executor y "hoy" inyectables. */
    UsageSnapshotter(UsageSource source, AppUsageDailyDAO dao, Executor executor,
                     Supplier<LocalDate> today) {
        this.source = source;
        this.dao = dao;
        this.executor = executor;
        this.today = today;
    }

    /** Encola la instantánea en el executor de IO (seguro desde el hilo principal). */
    public void snapshotAsync() {
        executor.execute(() -> {
            try {
                snapshotNow();
            } catch (RuntimeException e) {
                Log.w(TAG, "Usage snapshot failed", e);
            }
        });
    }

    /**
     * Síncrona: llámala desde un hilo de fondo.
     *
     * @return filas escritas (0 sin acceso de uso, si ya se hizo hoy o si no había nada nuevo).
     */
    public synchronized int snapshotNow() {
        LocalDate todayDate = today.get();
        if (todayDate.equals(snapshotDoneOn) || !source.hasUsageAccess()) {
            return 0;
        }
        LocalDate firstDay = todayDate.minusDays(DAYS_BACK);
        LocalDate lastDay = todayDate.minusDays(1);
        Map<String, long[]> perApp = source.foregroundMillisByDay(firstDay, DAYS_BACK);

        Map<String, Integer> stored = new HashMap<>();
        for (AppUsageDaily row : dao.getBetween(firstDay.toString(), lastDay.toString())) {
            stored.put(key(row.day, row.packageName), row.minutes);
        }

        List<AppUsageDaily> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> app : perApp.entrySet()) {
            long[] byDay = app.getValue();
            int days = Math.min(byDay.length, DAYS_BACK);
            for (int i = 0; i < days; i++) {
                int minutes = (int) Math.min(Integer.MAX_VALUE, byDay[i] / MILLIS_PER_MINUTE);
                if (minutes <= 0) {
                    continue;
                }
                String day = firstDay.plusDays(i).toString();
                Integer previous = stored.get(key(day, app.getKey()));
                if (previous != null && previous >= minutes) {
                    continue;
                }
                rows.add(new AppUsageDaily(day, app.getKey(), minutes));
            }
        }
        if (!rows.isEmpty()) {
            dao.upsertAll(rows);
        }
        snapshotDoneOn = todayDate;
        return rows.size();
    }

    private static String key(String day, String packageName) {
        return day + '|' + packageName;
    }

    private static UsageSource systemSource(Context context) {
        final Context app = context.getApplicationContext();
        final UsageStatsRepository repository = new UsageStatsRepository(app);
        return new UsageSource() {
            @Override
            public boolean hasUsageAccess() {
                return UsageAccess.hasUsageAccess(app);
            }

            @NonNull
            @Override
            public Map<String, long[]> foregroundMillisByDay(@NonNull LocalDate firstDay, int days) {
                return repository.foregroundMillisByDayAllApps(firstDay, days);
            }
        };
    }
}
