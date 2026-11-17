package com.example.bbettercalendar.notifications.nudge;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.stats.DayMinutes;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Los números de cada aviso, sin Android (JUnit): qué hay en la agenda, cuánto mide la racha,
 * cómo fue la semana. {@link NudgeContent} los lee de Room y los convierte en texto.
 */
public final class NudgeData {

    /** «Planear mañana» sólo sale si mañana tiene como mucho esto. */
    public static final int EVENING_MAX_TOMORROW_ITEMS = 1;
    /** «Racha en juego» sólo sale con una racha de al menos esto (días seguidos hasta ayer). */
    public static final int STREAK_MIN_DAYS = 2;

    private NudgeData() {}

    // ---------------- Agenda ----------------

    /** Lo que hay en un día: tareas sin hacer, eventos y el primero que queda por llegar. */
    public static final class Agenda {
        public final int openTasks;
        public final int events;
        /** Primera tarea o evento que empieza a partir de ahora (null = ninguno). */
        @Nullable public final CalendarEntry firstUpcoming;

        Agenda(int openTasks, int events, @Nullable CalendarEntry firstUpcoming) {
            this.openTasks = openTasks;
            this.events = events;
            this.firstUpcoming = firstUpcoming;
        }

        public int total() {
            return openTasks + events;
        }

        public boolean isEmpty() {
            return total() == 0;
        }

        /** Cuántos más hay además del primero ("y N más"). */
        public int moreAfterFirst() {
            return firstUpcoming == null ? 0 : Math.max(0, total() - 1);
        }
    }

    /**
     * Resume las entradas de un día (lo que devuelve {@code getEventsBetweenSync(inicio, fin)}, sin
     * plantillas ni retiradas). Cuenta las tareas no hechas y los eventos que aún no han terminado a
     * {@code nowMillis}; los recordatorios (tipo 3) no cuentan.
     */
    @NonNull
    public static Agenda agenda(@Nullable List<CalendarEntry> entries, long nowMillis) {
        int tasks = 0;
        int events = 0;
        CalendarEntry first = null;
        if (entries != null) {
            for (CalendarEntry e : entries) {
                if (e == null || e.isTemplate() || e.isDismissed()) continue;
                boolean counted;
                if (e.getType() == AddEventActivity.TYPE_TASK) {
                    counted = !e.isDone();
                    if (counted) tasks++;
                } else if (e.getType() == AddEventActivity.TYPE_EVENT) {
                    long end = Math.max(e.getEndMillis(), e.getStartMillis());
                    counted = !e.isDone() && end >= nowMillis;
                    if (counted) events++;
                } else {
                    counted = false;
                }
                if (counted && e.getStartMillis() >= nowMillis
                        && (first == null || e.getStartMillis() < first.getStartMillis())) {
                    first = e;
                }
            }
        }
        return new Agenda(tasks, events, first);
    }

    // ---------------- Racha ----------------

    /**
     * Días seguidos con al menos un pomodoro completado que terminan AYER (hoy no cuenta: todavía
     * puede pasar). {@code isoDays} = fechas locales "YYYY-MM-DD" de {@code getActiveDaysSince}.
     */
    public static int streakEndingYesterday(@NonNull Collection<String> isoDays, @NonNull LocalDate today) {
        Set<LocalDate> days = parse(isoDays);
        int streak = 0;
        LocalDate cursor = today.minusDays(1);
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** Hay racha que perder (>= {@link #STREAK_MIN_DAYS}) y hoy todavía no hubo pomodoro. 0 = no aplica. */
    public static int streakAtStake(@NonNull Collection<String> isoDays, @NonNull LocalDate today) {
        if (parse(isoDays).contains(today)) return 0;
        int streak = streakEndingYesterday(isoDays, today);
        return streak >= STREAK_MIN_DAYS ? streak : 0;
    }

    // ---------------- Mañana ----------------

    /** «Planear mañana» aplica si mañana tiene como mucho {@link #EVENING_MAX_TOMORROW_ITEMS} entradas. */
    public static boolean tomorrowIsOpen(@Nullable List<CalendarEntry> tomorrowEntries, long tomorrowStartMillis) {
        return agenda(tomorrowEntries, tomorrowStartMillis).total() <= EVENING_MAX_TOMORROW_ITEMS;
    }

    // ---------------- Semana ----------------

    public static final class Week {
        public final int focusMinutes;
        public final int activeDays;
        public final int doneTasks;
        /** Variación de minutos frente a la semana anterior en %, o null si la anterior fue 0. */
        @Nullable public final Integer deltaPercent;

        Week(int focusMinutes, int activeDays, int doneTasks, @Nullable Integer deltaPercent) {
            this.focusMinutes = focusMinutes;
            this.activeDays = activeDays;
            this.doneTasks = doneTasks;
            this.deltaPercent = deltaPercent;
        }

        /** El resumen sólo sale si la semana tuvo concentración o alguna tarea hecha. */
        public boolean hasActivity() {
            return focusMinutes > 0 || doneTasks > 0;
        }
    }

    /**
     * @param thisWeekDays     minutos por día de esta semana (lunes hasta ahora)
     * @param lastWeekMinutes  total de minutos de la semana anterior completa
     */
    @NonNull
    public static Week week(@Nullable List<DayMinutes> thisWeekDays, int lastWeekMinutes, int doneTasks) {
        int minutes = 0;
        Set<String> active = new HashSet<>();
        if (thisWeekDays != null) {
            for (DayMinutes d : thisWeekDays) {
                if (d == null || d.minutes <= 0) continue;
                minutes += d.minutes;
                if (d.day != null) active.add(d.day);
            }
        }
        return new Week(minutes, Math.min(7, active.size()), Math.max(0, doneTasks),
                deltaPercent(minutes, lastWeekMinutes));
    }

    /** Redondeado al entero más cercano; null si no hay base con la que comparar. */
    @Nullable
    static Integer deltaPercent(int thisWeek, int lastWeek) {
        if (lastWeek <= 0) return null;
        return (int) Math.round((thisWeek - lastWeek) * 100.0 / lastWeek);
    }

    private static Set<LocalDate> parse(Collection<String> isoDays) {
        Set<LocalDate> out = new HashSet<>();
        for (String s : isoDays) {
            if (s == null) continue;
            try {
                out.add(LocalDate.parse(s));
            } catch (RuntimeException ignored) {
                // Fila con fecha corrupta: no tumba el cálculo.
            }
        }
        return out;
    }
}
