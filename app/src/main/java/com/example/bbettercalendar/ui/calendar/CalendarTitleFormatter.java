package com.example.bbettercalendar.ui.calendar;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Títulos de la cabecera del calendario (rediseno-calendario-color): el mes en grande y el año al
 * lado, más pequeño y apagado ("Noviembre 2026").
 */
public final class CalendarTitleFormatter {

    private static final float YEAR_SCALE = 0.7f;

    private CalendarTitleFormatter() {}

    /** "Noviembre 2026". */
    @NonNull
    public static CharSequence monthYear(@NonNull Context ctx, @NonNull YearMonth month) {
        String name = capitalize(month.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())));
        return withMutedSuffix(ctx, name, String.valueOf(month.getYear()));
    }

    /** Semana que empieza en {@code weekStart}: "Noviembre 2026", o "Nov – dic 2026" si pisa dos meses. */
    @NonNull
    public static CharSequence week(@NonNull Context ctx, @NonNull LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        if (YearMonth.from(weekStart).equals(YearMonth.from(weekEnd))) {
            return monthYear(ctx, YearMonth.from(weekStart));
        }
        DateTimeFormatter shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.getDefault());
        String range = capitalize(weekStart.format(shortMonth)) + " – " + weekEnd.format(shortMonth);
        return withMutedSuffix(ctx, range, String.valueOf(weekEnd.getYear()));
    }

    private static CharSequence withMutedSuffix(Context ctx, String main, String suffix) {
        SpannableString text = new SpannableString(main + " " + suffix);
        int from = main.length() + 1;
        text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.bb_on_surface_muted)),
                from, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(YEAR_SCALE), from, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    @NonNull
    public static String capitalize(@NonNull String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }
}
