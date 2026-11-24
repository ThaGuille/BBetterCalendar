package com.example.bbettercalendar.ui.projects.picker;

import com.example.bbettercalendar.projects.domain.DateChoices;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Labels and conversions of the task pickers. Pure Java (no Android), so the JVM tests cover them. */
public final class PickerLabels {
    private PickerLabels() {}

    /** "25 min", "50 min", "1 h", "1 h 30", "2 h", "2 h 30": the same in English and Spanish. */
    public static String durationLabel(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + " h" : h + " h " + m;
    }

    /** "9:00", "17:00": hour without leading zero, minutes with. */
    public static String timeLabel(int minutesOfDay) {
        return (minutesOfDay / 60) + ":" + (minutesOfDay % 60 < 10 ? "0" : "") + (minutesOfDay % 60);
    }

    /** Label of the next Saturday after today, "Sat 21" / "Sáb 21". */
    public static String nextSaturdayLabel(long nowMillis, ZoneId zone, Locale locale) {
        return shortDay(DateChoices.nextSaturday(nowMillis, zone), zone, locale, "EEE d");
    }

    /** Label of a custom day: "Thu 3 Dec" / "Jue 3 dic". */
    public static String dayLabel(long dayStartMillis, ZoneId zone, Locale locale) {
        return shortDay(dayStartMillis, zone, locale, "EEE d MMM");
    }

    private static String shortDay(long dayStartMillis, ZoneId zone, Locale locale, String pattern) {
        LocalDate date = Instant.ofEpochMilli(dayStartMillis).atZone(zone).toLocalDate();
        String text = DateTimeFormatter.ofPattern(pattern, locale).format(date).replace(".", "");
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }

    /** MaterialDatePicker works in UTC midnight: the selection for a local day start. */
    public static long dayToPickerSelection(long dayStartMillis, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(dayStartMillis).atZone(zone).toLocalDate();
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /** The local day start of a MaterialDatePicker selection (UTC midnight). */
    public static long pickerSelectionToDay(long utcSelection, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(utcSelection).atZone(ZoneOffset.UTC).toLocalDate();
        return date.atStartOfDay(zone).toInstant().toEpochMilli();
    }
}
