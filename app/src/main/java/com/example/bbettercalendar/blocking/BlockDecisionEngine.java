package com.example.bbettercalendar.blocking;

import android.content.Context;

import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.usage.UsageStatsRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Decide, para un paquete que pasa a primer plano, si hay que cubrirlo (superó su límite diario).
// Diseñado para NO golpear UsageStatsRepository.queryEvents en cada evento de ventana (el repo es
// sin caché y avisa de que es caro): la decisión por paquete se cachea con un TTL corto y, una vez
// bloqueado, queda "pegado" hasta medianoche (latch). Un chequeo de día (à la WarnedTodayStore)
// resetea el latch en el límite del día sin cablearlo al reset del Splash.
//
// decide() se llama SIEMPRE fuera del hilo principal (lo invoca el executor del servicio, regla #3).
public class BlockDecisionEngine {

    // Ventana durante la que reutilizamos una decisión NEGATIVA ("todavía por debajo del límite")
    // sin re-consultar el uso. Corta para que el cruce del límite se note enseguida; los bloqueos
    // positivos no dependen de esto (quedan en el latch hasta medianoche).
    private static final long NEGATIVE_TTL_MILLIS = TimeUnit.SECONDS.toMillis(20);

    // Resultado de una decisión: si cubrir + el uso de hoy (para el texto de la portada).
    public static final class Decision {
        public final boolean block;
        public final long usedMillis;

        Decision(boolean block, long usedMillis) {
            this.block = block;
            this.usedMillis = usedMillis;
        }
    }

    private final Context appContext;
    private final UsageStatsRepository usageRepo;

    // Reglas a hacer cumplir, refrescadas por observeEnforced() desde el servicio (hilo principal);
    // leídas en decide() (executor) -> mapa concurrente y referencia volátil.
    private volatile Map<String, AppRule> enforcedByPkg = new HashMap<>();

    // Caché de la última comprobación negativa por paquete (timestamp) y latch de "bloqueado hoy".
    private final Map<String, Long> lastNegativeCheckAt = new ConcurrentHashMap<>();
    private final Map<String, Boolean> blockedToday = new ConcurrentHashMap<>();
    private volatile String dayKey = LocalDate.now().toString();

    public BlockDecisionEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.usageRepo = new UsageStatsRepository(appContext);
    }

    // Sustituye la caché de reglas a hacer cumplir (lista de observeEnforced()).
    public void setEnforcedRules(List<AppRule> rules) {
        Map<String, AppRule> map = new HashMap<>();
        for (AppRule rule : rules) {
            map.put(rule.packageName, rule);
        }
        this.enforcedByPkg = map;
    }

    public Decision decide(String packageName) {
        resetIfNewDay();

        if (packageName == null) return new Decision(false, 0L);
        if (!BlockingSettings.isEnforcementEnabled(appContext)) return new Decision(false, 0L);

        AppRule rule = enforcedByPkg.get(packageName);
        if (rule == null || rule.dailyLimitMinutes <= 0) return new Decision(false, 0L);

        // Latch: una vez cubierto hoy, sigue cubierto hasta medianoche sin re-consultar.
        if (Boolean.TRUE.equals(blockedToday.get(packageName))) {
            return new Decision(true, usageTodayMillis(packageName));
        }

        // TTL de la decisión negativa: si la comprobamos hace poco y estaba por debajo, no re-leemos.
        Long checkedAt = lastNegativeCheckAt.get(packageName);
        if (checkedAt != null && System.currentTimeMillis() - checkedAt < NEGATIVE_TTL_MILLIS) {
            return new Decision(false, 0L);
        }

        long usedMillis = usageTodayMillis(packageName);
        long usedMinutes = TimeUnit.MILLISECONDS.toMinutes(usedMillis);
        if (usedMinutes >= rule.dailyLimitMinutes) {
            blockedToday.put(packageName, Boolean.TRUE);
            return new Decision(true, usedMillis);
        }

        lastNegativeCheckAt.put(packageName, System.currentTimeMillis());
        return new Decision(false, usedMillis);
    }

    // Uso de HOY (desde medianoche) del paquete, en ms. Consulta cara -> sólo desde decide().
    private long usageTodayMillis(String packageName) {
        ZoneId zone = ZoneId.systemDefault();
        long begin = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli();
        long end = System.currentTimeMillis();
        Long millis = usageRepo.foregroundMillis(begin, end).get(packageName);
        return millis == null ? 0L : millis;
    }

    // Barre el latch + la caché al cruzar medianoche (auto-reset, sin cablear al reset del Splash).
    private void resetIfNewDay() {
        String today = LocalDate.now().toString();
        if (!today.equals(dayKey)) {
            dayKey = today;
            blockedToday.clear();
            lastNegativeCheckAt.clear();
        }
    }
}
