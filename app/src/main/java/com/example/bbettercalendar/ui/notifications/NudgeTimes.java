package com.example.bbettercalendar.ui.notifications;

/**
 * Pure helpers for the daily-nudge time picker: the allowed window is 08:00 to 21:59 (nothing
 * between 22:00 and 8:00, plan sistema-notificaciones).
 */
public final class NudgeTimes {

    public static final int MIN_MINUTES = 8 * 60;
    public static final int MAX_MINUTES = 22 * 60 - 1;

    private NudgeTimes() { }

    public static boolean isAllowed(int minutesOfDay) {
        return minutesOfDay >= MIN_MINUTES && minutesOfDay <= MAX_MINUTES;
    }

    /** Clamps minutes since midnight into the allowed window. */
    public static int clamp(int minutesOfDay) {
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, minutesOfDay));
    }

    public static int toMinutes(int hour, int minute) {
        return hour * 60 + minute;
    }

    public static int hourOf(int minutesOfDay) {
        return minutesOfDay / 60;
    }

    public static int minuteOf(int minutesOfDay) {
        return minutesOfDay % 60;
    }
}
