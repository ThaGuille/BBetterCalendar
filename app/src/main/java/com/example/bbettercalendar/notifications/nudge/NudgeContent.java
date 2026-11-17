package com.example.bbettercalendar.notifications.nudge;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationIds;
import com.example.bbettercalendar.notifications.NotificationPrefs;
import com.example.bbettercalendar.notifications.NotificationSpec;
import com.example.bbettercalendar.stats.DayMinutes;
import com.example.bbettercalendar.stats.FocusEventDAO;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Contenido de cada aviso: lee Room (síncrono, sólo desde el executor de IO), pasa los números por
 * {@link NudgeData} y los convierte en texto. Devuelve null cuando no hay nada que decir («sólo con
 * contenido»): ese disparo no se publica ni cuenta para el presupuesto.
 */
final class NudgeContent {

    static final int MORNING_VARIANTS = 3;
    static final int STREAK_VARIANTS = 3;
    static final int EVENING_VARIANTS = 3;
    private static final int ACTIVE_DAYS_LOOKBACK = 31;

    /** Aviso listo para publicar + la variante de copy usada (para no repetirla la próxima vez). */
    static final class Built {
        final NotificationSpec spec;
        final int variant;

        Built(NotificationSpec spec, int variant) {
            this.spec = spec;
            this.variant = variant;
        }
    }

    private NudgeContent() {}

    static String channelFor(String nudge) {
        return NotificationPrefs.NUDGE_WEEKLY.equals(nudge)
                ? NotificationChannels.CHANNEL_WEEKLY_SUMMARY
                : NotificationChannels.CHANNEL_DAILY_NUDGES;
    }

    static int notificationIdFor(String nudge) {
        switch (nudge) {
            case NotificationPrefs.NUDGE_MORNING: return NotificationIds.NUDGE_MORNING;
            case NotificationPrefs.NUDGE_STREAK: return NotificationIds.NUDGE_STREAK;
            case NotificationPrefs.NUDGE_EVENING_PLAN: return NotificationIds.NUDGE_EVENING_PLAN;
            default: return NotificationIds.WEEKLY_SUMMARY;
        }
    }

    @WorkerThread
    @Nullable
    static Built build(@NonNull Context context, @NonNull String nudge,
                       @NonNull CalendarEntryDAO entryDao, @NonNull FocusEventDAO focusDao,
                       long nowMillis, @NonNull ZoneId zone, int lastVariant, long seed) {
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        switch (nudge) {
            case NotificationPrefs.NUDGE_MORNING:
                return morning(context, entryDao, nowMillis, zone, today, lastVariant, seed);
            case NotificationPrefs.NUDGE_STREAK:
                return streak(context, focusDao, zone, today, lastVariant, seed);
            case NotificationPrefs.NUDGE_EVENING_PLAN:
                return evening(context, entryDao, zone, today, lastVariant, seed);
            case NotificationPrefs.NUDGE_WEEKLY:
                return weekly(context, entryDao, focusDao, nowMillis, zone, today);
            default:
                return null;
        }
    }

    // ---------------- Tu día ----------------

    private static Built morning(Context context, CalendarEntryDAO dao, long now, ZoneId zone,
                                 LocalDate today, int lastVariant, long seed) {
        long start = startOf(today, zone);
        long end = startOf(today.plusDays(1), zone) - 1L;
        List<CalendarEntry> entries = dao.getEventsBetweenSync(start, end);
        NudgeData.Agenda agenda = NudgeData.agenda(entries, now);
        if (agenda.isEmpty()) return null;

        Resources res = context.getResources();
        String counts = countsPhrase(res, agenda.openTasks, agenda.events);
        int variant = NudgePolicy.pickVariant(MORNING_VARIANTS, lastVariant, seed);
        int titleRes = variant == 0 ? R.string.nudge_morning_title_1
                : variant == 1 ? R.string.nudge_morning_title_2 : R.string.nudge_morning_title_3;
        String title = res.getString(titleRes, counts);

        String body;
        CalendarEntry first = agenda.firstUpcoming;
        if (first != null) {
            String name = first.getTitle() == null || first.getTitle().trim().isEmpty()
                    ? res.getString(R.string.nudge_untitled) : first.getTitle().trim();
            String time = DateFormat.getTimeFormat(context).format(new Date(first.getStartMillis()));
            body = res.getString(R.string.nudge_morning_first_up, name, time);
            int more = agenda.moreAfterFirst();
            if (more > 0) {
                body = body + " " + res.getQuantityString(R.plurals.nudge_morning_more, more, more);
            }
        } else {
            body = res.getString(R.string.nudge_morning_no_upcoming);
        }

        NotificationSpec spec = new NotificationSpec.Builder(
                channelFor(NotificationPrefs.NUDGE_MORNING), NotificationIds.NUDGE_MORNING)
                .title(title)
                .body(body)
                // Títulos de tareas: en la pantalla de bloqueo segura sólo se ve la versión genérica.
                .publicVersion(res.getString(R.string.nudge_morning_public_title),
                        res.getString(R.string.nudge_morning_public_body))
                .openMainActivity(context)
                .build();
        return new Built(spec, variant);
    }

    private static String countsPhrase(Resources res, int tasks, int events) {
        String t = tasks > 0 ? res.getQuantityString(R.plurals.nudge_count_tasks, tasks, tasks) : null;
        String e = events > 0 ? res.getQuantityString(R.plurals.nudge_count_events, events, events) : null;
        if (t != null && e != null) return res.getString(R.string.nudge_count_join, t, e);
        return t != null ? t : e;
    }

    // ---------------- Racha en juego ----------------

    private static Built streak(Context context, FocusEventDAO dao, ZoneId zone, LocalDate today,
                                int lastVariant, long seed) {
        long since = startOf(today.minusDays(ACTIVE_DAYS_LOOKBACK), zone);
        List<String> days = dao.getActiveDaysSince(since);
        int streak = NudgeData.streakAtStake(days, today);
        if (streak <= 0) return null;

        Resources res = context.getResources();
        int variant = NudgePolicy.pickVariant(STREAK_VARIANTS, lastVariant, seed);
        int bodyRes = variant == 0 ? R.plurals.nudge_streak_body_1
                : variant == 1 ? R.plurals.nudge_streak_body_2 : R.plurals.nudge_streak_body_3;
        NotificationSpec spec = new NotificationSpec.Builder(
                channelFor(NotificationPrefs.NUDGE_STREAK), NotificationIds.NUDGE_STREAK)
                .title(res.getString(R.string.nudge_streak_title))
                .body(res.getQuantityString(bodyRes, streak, streak))
                .openMainActivity(context)
                .build();
        return new Built(spec, variant);
    }

    // ---------------- Planear mañana ----------------

    private static Built evening(Context context, CalendarEntryDAO dao, ZoneId zone, LocalDate today,
                                 int lastVariant, long seed) {
        LocalDate tomorrow = today.plusDays(1);
        long start = startOf(tomorrow, zone);
        long end = startOf(tomorrow.plusDays(1), zone) - 1L;
        if (!NudgeData.tomorrowIsOpen(dao.getEventsBetweenSync(start, end), start)) return null;

        Resources res = context.getResources();
        int variant = NudgePolicy.pickVariant(EVENING_VARIANTS, lastVariant, seed);
        int bodyRes = variant == 0 ? R.string.nudge_evening_body_1
                : variant == 1 ? R.string.nudge_evening_body_2 : R.string.nudge_evening_body_3;
        NotificationSpec spec = new NotificationSpec.Builder(
                channelFor(NotificationPrefs.NUDGE_EVENING_PLAN), NotificationIds.NUDGE_EVENING_PLAN)
                .title(res.getString(R.string.nudge_evening_title))
                .body(res.getString(bodyRes))
                .openMainActivity(context)
                .build();
        return new Built(spec, variant);
    }

    // ---------------- Resumen semanal ----------------

    private static Built weekly(Context context, CalendarEntryDAO entryDao, FocusEventDAO focusDao,
                                long now, ZoneId zone, LocalDate today) {
        LocalDate monday = NudgePolicy.weekStart(today);
        long weekStart = startOf(monday, zone);
        long lastWeekStart = startOf(monday.minusWeeks(1), zone);
        int lastWeekMinutes = 0;
        for (DayMinutes d
                : focusDao.getFocusMinutesPerDayBetween(lastWeekStart, weekStart - 1L)) {
            if (d != null && d.minutes > 0) lastWeekMinutes += d.minutes;
        }
        NudgeData.Week week = NudgeData.week(
                focusDao.getFocusMinutesPerDayBetween(weekStart, now),
                lastWeekMinutes,
                entryDao.countDoneTasksBetween(weekStart, now));
        if (!week.hasActivity()) return null;

        Resources res = context.getResources();
        String delta;
        if (week.deltaPercent == null) {
            delta = "";
        } else if (week.deltaPercent > 0) {
            delta = res.getString(R.string.nudge_weekly_delta_up, week.deltaPercent);
        } else if (week.deltaPercent < 0) {
            delta = res.getString(R.string.nudge_weekly_delta_down, week.deltaPercent);
        } else {
            delta = res.getString(R.string.nudge_weekly_delta_same);
        }
        String duration = week.focusMinutes >= 60
                ? res.getString(R.string.nudge_weekly_hours_minutes, week.focusMinutes / 60, week.focusMinutes % 60)
                : res.getString(R.string.nudge_weekly_minutes, week.focusMinutes);
        String body = res.getString(R.string.nudge_weekly_focus, duration, delta)
                + "\n" + res.getString(R.string.nudge_weekly_active_days, week.activeDays)
                + "\n" + res.getQuantityString(R.plurals.nudge_weekly_tasks, week.doneTasks, week.doneTasks);

        NotificationSpec spec = new NotificationSpec.Builder(
                channelFor(NotificationPrefs.NUDGE_WEEKLY), NotificationIds.WEEKLY_SUMMARY)
                .title(res.getString(R.string.nudge_weekly_title))
                .body(body)
                .openMainActivity(context)
                .build();
        return new Built(spec, 0);
    }

    private static long startOf(LocalDate day, ZoneId zone) {
        ZonedDateTime z = day.atStartOfDay(zone);
        return z.toInstant().toEpochMilli();
    }
}
