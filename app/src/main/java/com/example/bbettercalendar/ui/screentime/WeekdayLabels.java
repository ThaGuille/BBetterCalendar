package com.example.bbettercalendar.ui.screentime;

import androidx.annotation.NonNull;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;

// mejora-visual u9: inicial del dia para los ejes de Tiempo de pantalla y del detalle de app.
// En espanol "L M X J V S D" (la libreria da "m" para martes y miercoles); en el resto, su inicial.
final class WeekdayLabels {

    private WeekdayLabels() {
    }

    @NonNull
    static String initial(@NonNull DayOfWeek day) {
        Locale locale = Locale.getDefault();
        if ("es".equals(locale.getLanguage())) {
            return String.valueOf("LMXJVSD".charAt(day.getValue() - 1));
        }
        return day.getDisplayName(TextStyle.NARROW, locale).toUpperCase(locale);
    }
}
