package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.stats.DayMinutes;
import com.example.bbettercalendar.stats.StreakCalculator;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Los valores de {@code stats/{uid}} a partir de lo que hay en el móvil (F3-u4, 03 §9). Puro: ni Android ni
 * reloj ni E/S; {@link StatsPublisher} le pasa las filas de {@code FocusEventDAO}, el
 * {@link StreakCalculator.Result} y el objetivo, y el «hoy» del dispositivo.
 *
 * <ul>
 *   <li>{@code minutesToday}: minutos de hoy, sin tope.</li>
 *   <li>{@code minutesWeekCapped}: suma de lunes a hoy de {@code min(minutos del día, 240)} (D-005).</li>
 *   <li>{@code daysThisWeek}, {@code weekStreak} y {@code bestWeekStreak}: los de {@link StreakCalculator.Result},
 *       que se calcula con toda la historia local (D-031).</li>
 *   <li>{@code dayKey} y {@code weekKey}: {@link WeekKey} de hoy.</li>
 * </ul>
 * Devuelve los valores tal cual; el recorte a los rangos de las reglas lo hace {@link StatsWrites} (un solo
 * sitio). {@code updatedAtMillis} es null: la hora la pone el servidor.
 */
public final class StatsComputer {

    /** Tope por día de la cifra semanal de Minutos (D-005). */
    public static final int DAY_CAP_MINUTES = 240;

    private StatsComputer() {
    }

    /**
     * @param weekDays     minutos por día civil local ({@code FocusEventDAO.getFocusMinutesPerDayBetween}); los
     *                     días fuera de lunes..hoy o mal formados se ignoran.
     * @param totalMinutes {@code SUM(durationMin)} de todas las sesiones completadas (D-031).
     * @param streak       racha semanal calculada con todos los días con sesión y el mismo {@code today}.
     * @param goal         objetivo semanal ({@code WeeklyGoalStore}).
     */
    @NonNull
    public static FriendStats compute(@NonNull Collection<DayMinutes> weekDays, int totalMinutes,
                                      @NonNull StreakCalculator.Result streak, int goal,
                                      @NonNull LocalDate today) {
        LocalDate monday = StreakCalculator.weekStart(today);
        Map<LocalDate, Integer> perDay = new HashMap<>();
        for (DayMinutes row : weekDays) {
            LocalDate d = row == null ? null : parse(row.day);
            if (d == null || d.isBefore(monday) || d.isAfter(today)) {
                continue;
            }
            Integer prev = perDay.get(d);
            perDay.put(d, (prev == null ? 0 : prev) + row.minutes);
        }
        Integer todayMinutes = perDay.get(today);
        int weekCapped = 0;
        for (int minutes : perDay.values()) {
            weekCapped += Math.max(0, Math.min(minutes, DAY_CAP_MINUTES));
        }
        return new FriendStats(WeekKey.dayKey(today), WeekKey.weekKey(today),
                todayMinutes == null ? 0 : todayMinutes, weekCapped, streak.daysThisWeek, goal,
                streak.weekStreak, streak.bestWeekStreak, totalMinutes, null);
    }

    @Nullable
    private static LocalDate parse(@Nullable String iso) {
        if (iso == null) {
            return null;
        }
        try {
            return LocalDate.parse(iso);
        } catch (RuntimeException e) {
            return null; // una fila con timestamp corrupto no tumba el cálculo
        }
    }
}
