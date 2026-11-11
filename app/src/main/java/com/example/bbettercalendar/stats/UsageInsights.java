package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lecturas del uso de apps (mejora-visual u5). Puro, sin Android ni I/O: recibe las filas de
 * {@code app_usage_daily}, el uso de HOY en vivo (todavía no está volcado), las reglas y el "hoy".
 *
 * <p>Semántica común:
 * <ul>
 *   <li>Un día está "observado" si alguna app tiene fila ese día (o es hoy y hay uso en vivo):
 *   {@code app_usage_daily} solo guarda filas de &gt;= 1 min, así que "sin fila" de UNA app en un día
 *   observado son 0 min, pero un día sin ninguna fila es un hueco de datos y no cuenta. Las medias
 *   dividen entre los días observados, no entre los días del calendario.</li>
 *   <li>Hoy se toma del uso en vivo; una fila guardada de hoy se ignora si hay uso en vivo.</li>
 *   <li>Los días se interpretan en la zona local ({@code limitSinceMillis} -> día civil local).</li>
 * </ul>
 */
public final class UsageInsights {

    /** Una app sugerida para limitar debe estar entre las {@code SUGGEST_TOP} de más uso... */
    public static final int SUGGEST_TOP = 3;
    /** ...y pasar de media al menos esto al día (minutos). */
    public static final int SUGGEST_MIN_AVG_MIN = 30;
    /** Días mínimos a CADA lado del límite para el antes/después. */
    public static final int BEFORE_AFTER_MIN_DAYS = 3;
    /** Días previos a la fecha del límite que forman el "antes". */
    public static final int BEFORE_WINDOW_DAYS = 7;
    /** Días observados mínimos para la relación enfoque-móvil. */
    public static final int FOCUS_PHONE_MIN_DAYS = 14;
    /** Minutos de foco a partir de los que un día es "día de enfoque" (un pomodoro). */
    public static final int FOCUS_DAY_MIN = 25;
    /** Días mínimos de cada grupo (con / sin enfoque) para compararlos. */
    public static final int FOCUS_PHONE_MIN_GROUP = 3;

    private UsageInsights() {
    }

    /** Media diaria de una app. */
    public static final class AppAverage {
        @NonNull public final String packageName;
        public final double averageMinutes;

        AppAverage(@NonNull String packageName, double averageMinutes) {
            this.packageName = packageName;
            this.averageMinutes = averageMinutes;
        }
    }

    /** Antes y después del límite de una app. */
    public static final class BeforeAfter {
        @NonNull public final String packageName;
        public final double beforeAverage;
        public final double afterAverage;
        public final int beforeDays;
        public final int afterDays;

        BeforeAfter(@NonNull String packageName, double beforeAverage, double afterAverage,
                    int beforeDays, int afterDays) {
            this.packageName = packageName;
            this.beforeAverage = beforeAverage;
            this.afterAverage = afterAverage;
            this.beforeDays = beforeDays;
            this.afterDays = afterDays;
        }

        /** Minutos ahorrados desde el límite: (antes - después) x días de después; nunca negativo. */
        public int recoveredMinutes() {
            return (int) Math.max(0, Math.round((beforeAverage - afterAverage) * afterDays));
        }

        public boolean improved() {
            return afterAverage < beforeAverage;
        }
    }

    /** Móvil en días con enfoque frente a días sin él. */
    public static final class FocusVsPhone {
        public final double phoneOnFocusDays;
        public final double phoneOnOtherDays;
        public final int focusDays;
        public final int otherDays;

        FocusVsPhone(double phoneOnFocusDays, double phoneOnOtherDays, int focusDays, int otherDays) {
            this.phoneOnFocusDays = phoneOnFocusDays;
            this.phoneOnOtherDays = phoneOnOtherDays;
            this.focusDays = focusDays;
            this.otherDays = otherDays;
        }

        /** Minutos de móvil que se ahorran los días de enfoque (negativo si usa más). */
        public double savedMinutes() {
            return phoneOnOtherDays - phoneOnFocusDays;
        }
    }

    /**
     * Media diaria por app en los últimos {@code rangeDays} días que terminan en {@code today}
     * (inclusive), ordenada de más a menos uso (empate: por paquete). Vacía si no hay días observados.
     *
     * @param liveToday minutos de hoy por paquete (null = no hay medición en vivo)
     */
    @NonNull
    public static List<AppAverage> dailyAverages(@NonNull List<AppUsageDaily> rows,
                                                 @Nullable Map<String, Integer> liveToday,
                                                 @NonNull LocalDate today, int rangeDays) {
        if (rangeDays < 1) {
            throw new IllegalArgumentException("rangeDays must be >= 1, was " + rangeDays);
        }
        return averagesBetween(rows, liveToday, today.minusDays(rangeDays - 1L), today, today);
    }

    /** Las {@code n} apps con más uso (la lista ya viene ordenada de {@link #dailyAverages}). */
    @NonNull
    public static List<AppAverage> topN(@NonNull List<AppAverage> averages, int n) {
        return new ArrayList<>(averages.subList(0, Math.max(0, Math.min(n, averages.size()))));
    }

    /**
     * Apps sugeridas para ponerles límite: entre las {@link #SUGGEST_TOP} de más uso, con al menos
     * {@link #SUGGEST_MIN_AVG_MIN} min/día y sin límite activo ({@code dailyLimitMinutes <= 0} o sin regla).
     */
    @NonNull
    public static List<AppAverage> suggested(@NonNull List<AppAverage> averages, @NonNull List<AppRule> rules) {
        Set<String> limited = new HashSet<>();
        for (AppRule r : rules) {
            if (r != null && r.dailyLimitMinutes > 0) {
                limited.add(r.packageName);
            }
        }
        List<AppAverage> out = new ArrayList<>();
        for (AppAverage a : topN(averages, SUGGEST_TOP)) {
            if (a.averageMinutes >= SUGGEST_MIN_AVG_MIN && !limited.contains(a.packageName)) {
                out.add(a);
            }
        }
        return out;
    }

    /**
     * Antes/después del límite de {@code rule}: media de los {@link #BEFORE_WINDOW_DAYS} días previos al
     * día del límite frente a la media desde ese día hasta hoy. null si la regla no tiene
     * {@code limitSinceMillis} o si algún lado tiene menos de {@link #BEFORE_AFTER_MIN_DAYS} días observados.
     */
    @Nullable
    public static BeforeAfter beforeAfter(@NonNull List<AppUsageDaily> rows,
                                          @Nullable Map<String, Integer> liveToday,
                                          @NonNull AppRule rule, @NonNull LocalDate today,
                                          @NonNull ZoneId zone) {
        if (rule.limitSinceMillis <= 0 || rule.dailyLimitMinutes <= 0) {
            return null;
        }
        LocalDate since = Instant.ofEpochMilli(rule.limitSinceMillis).atZone(zone).toLocalDate();
        if (since.isAfter(today)) {
            return null;
        }
        LocalDate beforeFrom = since.minusDays(BEFORE_WINDOW_DAYS);
        LocalDate beforeTo = since.minusDays(1);
        Set<LocalDate> observed = observedDays(rows, liveToday, today);
        int beforeDays = countBetween(observed, beforeFrom, beforeTo);
        int afterDays = countBetween(observed, since, today);
        if (beforeDays < BEFORE_AFTER_MIN_DAYS || afterDays < BEFORE_AFTER_MIN_DAYS) {
            return null;
        }
        double before = averageOf(rows, liveToday, rule.packageName, beforeFrom, beforeTo, today, beforeDays);
        double after = averageOf(rows, liveToday, rule.packageName, since, today, today, afterDays);
        return new BeforeAfter(rule.packageName, before, after, beforeDays, afterDays);
    }

    /**
     * Móvil en días con enfoque ({@code >= FOCUS_DAY_MIN} min) frente al resto. Los días son los de
     * {@code phoneByDay} (los observados); un día sin enfoque registrado cuenta como 0. null con menos
     * de {@link #FOCUS_PHONE_MIN_DAYS} días o con menos de {@link #FOCUS_PHONE_MIN_GROUP} en algún grupo.
     */
    @Nullable
    public static FocusVsPhone focusVsPhone(@NonNull Map<LocalDate, Integer> focusByDay,
                                            @NonNull Map<LocalDate, Integer> phoneByDay) {
        if (phoneByDay.size() < FOCUS_PHONE_MIN_DAYS) {
            return null;
        }
        long withSum = 0;
        long otherSum = 0;
        int with = 0;
        int other = 0;
        for (Map.Entry<LocalDate, Integer> en : phoneByDay.entrySet()) {
            Integer f = focusByDay.get(en.getKey());
            int phone = en.getValue() == null ? 0 : en.getValue();
            if (f != null && f >= FOCUS_DAY_MIN) {
                with++;
                withSum += phone;
            } else {
                other++;
                otherSum += phone;
            }
        }
        if (with < FOCUS_PHONE_MIN_GROUP || other < FOCUS_PHONE_MIN_GROUP) {
            return null;
        }
        return new FocusVsPhone((double) withSum / with, (double) otherSum / other, with, other);
    }

    // ---- internos ----

    @NonNull
    private static List<AppAverage> averagesBetween(List<AppUsageDaily> rows, Map<String, Integer> live,
                                                    LocalDate from, LocalDate to, LocalDate today) {
        Set<LocalDate> observed = observedDays(rows, live, today);
        int days = countBetween(observed, from, to);
        if (days == 0) {
            return Collections.emptyList();
        }
        Map<String, Long> sums = new HashMap<>();
        for (AppUsageDaily r : rows) {
            LocalDate d = parse(r);
            if (d == null || d.isBefore(from) || d.isAfter(to) || (d.equals(today) && live != null)) {
                continue;
            }
            sums.merge(r.packageName, (long) r.minutes, Long::sum);
        }
        if (live != null && !today.isBefore(from) && !today.isAfter(to)) {
            for (Map.Entry<String, Integer> en : live.entrySet()) {
                if (en.getValue() != null) {
                    sums.merge(en.getKey(), (long) en.getValue(), Long::sum);
                }
            }
        }
        List<AppAverage> out = new ArrayList<>();
        for (Map.Entry<String, Long> en : sums.entrySet()) {
            if (en.getValue() > 0) {
                out.add(new AppAverage(en.getKey(), (double) en.getValue() / days));
            }
        }
        Collections.sort(out, (a, b) -> {
            int c = Double.compare(b.averageMinutes, a.averageMinutes);
            return c != 0 ? c : a.packageName.compareTo(b.packageName);
        });
        return out;
    }

    private static double averageOf(List<AppUsageDaily> rows, Map<String, Integer> live, String pkg,
                                    LocalDate from, LocalDate to, LocalDate today, int days) {
        long sum = 0;
        for (AppUsageDaily r : rows) {
            LocalDate d = parse(r);
            if (d == null || !pkg.equals(r.packageName) || d.isBefore(from) || d.isAfter(to)
                    || (d.equals(today) && live != null)) {
                continue;
            }
            sum += r.minutes;
        }
        if (live != null && !today.isBefore(from) && !today.isAfter(to)) {
            Integer m = live.get(pkg);
            sum += m == null ? 0 : m;
        }
        return (double) sum / days;
    }

    @NonNull
    private static Set<LocalDate> observedDays(List<AppUsageDaily> rows, Map<String, Integer> live,
                                               LocalDate today) {
        Set<LocalDate> out = new HashSet<>();
        for (AppUsageDaily r : rows) {
            LocalDate d = parse(r);
            if (d != null && r.minutes > 0 && !(d.equals(today) && live != null)) {
                out.add(d);
            }
        }
        if (live != null && !live.isEmpty()) {
            out.add(today);
        }
        return out;
    }

    private static int countBetween(Set<LocalDate> days, LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d : days) {
            if (!d.isBefore(from) && !d.isAfter(to)) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    private static LocalDate parse(AppUsageDaily r) {
        if (r == null) {
            return null;
        }
        try {
            return LocalDate.parse(r.day);
        } catch (RuntimeException e) {
            return null; // fila con 'day' corrupto: se trata como ausente
        }
    }
}
