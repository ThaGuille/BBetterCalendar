package com.example.bbettercalendar.ui.projects;

import android.content.Context;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.ProjectDeadlineState;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// Textos compartidos por la lista y el detalle de proyectos (mejora-visual u11): duraciones
// "6 h 20 m", "unas 5 h" y los textos de vencimiento ("Vence en 3 días", "Vence el 14 nov · 3 días").
final class ProjectFormat {

    private ProjectFormat() { }

    /** "6 h 20 m" / "2 h" / "40 m". */
    static String duration(@NonNull Context context, int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) {
            return context.getString(R.string.project_duration_m, m);
        }
        if (m == 0) {
            return context.getString(R.string.project_duration_h, h);
        }
        return context.getString(R.string.project_duration_hm, h, m);
    }

    /** Redondeo para "Quedan unas 5 h": a la hora si pasa de 90 min, a 5 min si no. */
    static int roundedMinutes(int minutes) {
        if (minutes >= 90) {
            return Math.round(minutes / 60f) * 60;
        }
        return Math.max(5, Math.round(minutes / 5f) * 5);
    }

    /** Minutos que faltan en las tareas sin hacer que tienen objetivo (0 si ninguna lo tiene). */
    static int remainingMinutes(@NonNull Iterable<CalendarEntry> entries) {
        int total = 0;
        for (CalendarEntry entry : entries) {
            if (!entry.isDone() && entry.getTargetMinutes() > 0) {
                total += ProjectListItem.remainingMinutes(entry);
            }
        }
        return total;
    }

    /** Dias de calendario entre dos instantes (negativo si el segundo es anterior). */
    static int daysBetween(long fromMillis, long toMillis) {
        return (int) Math.round((startOfDay(toMillis) - startOfDay(fromMillis)) / 86_400_000d);
    }

    private static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Chip de la tarjeta: "Vence hoy" / "Vence mañana" / "Vence en 3 días" / "Vence 28 oct" / "Vencido". */
    static String cardDeadline(@NonNull Context context, long deadlineMillis, long nowMillis,
                               @NonNull ProjectDeadlineState state) {
        if (state == ProjectDeadlineState.PASSED) {
            return context.getString(R.string.project_deadline_passed);
        }
        int days = daysBetween(nowMillis, deadlineMillis);
        if (days <= 0) {
            return context.getString(R.string.project_due_today);
        }
        if (days == 1) {
            return context.getString(R.string.project_due_tomorrow);
        }
        if (days < 7) {
            return context.getString(R.string.project_due_in_days, days);
        }
        return context.getString(R.string.project_due_on, shortDate(deadlineMillis));
    }

    /** Chip del detalle: "Vence el 14 nov · 3 días" / "Vencido". */
    static String detailDeadline(@NonNull Context context, long deadlineMillis, long nowMillis,
                                 @NonNull ProjectDeadlineState state) {
        if (state == ProjectDeadlineState.PASSED) {
            return context.getString(R.string.project_deadline_passed);
        }
        int days = daysBetween(nowMillis, deadlineMillis);
        String left;
        if (days <= 0) {
            left = context.getString(R.string.project_due_day_today);
        } else if (days == 1) {
            left = context.getString(R.string.project_due_day_tomorrow);
        } else {
            left = context.getString(R.string.project_due_days_left, days);
        }
        return context.getString(R.string.project_due_full, shortDate(deadlineMillis), left);
    }

    private static String shortDate(long millis) {
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMM");
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis));
    }
}
