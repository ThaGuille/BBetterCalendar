package com.example.bbettercalendar.ui.calendar.domain;

/**
 * Pure mapping from the focus minutes of a day to the tint level of its calendar cell
 * (mejora-visual u12): 0 = none (no tint), 1 = little, 2 = some, 3 = a lot. The level indexes the
 * {@code bb_cal_focus_1..3} ramp. No Android types, so it is unit-testable on the JVM.
 */
public final class CalendarHeatLevel {

    /** First minute count that reaches level 2. */
    public static final int LEVEL_2_MIN = 30;
    /** First minute count that reaches level 3. */
    public static final int LEVEL_3_MIN = 90;
    public static final int MAX_LEVEL = 3;

    private CalendarHeatLevel() {}

    public static int levelFor(int focusMinutes) {
        if (focusMinutes <= 0) return 0;
        if (focusMinutes < LEVEL_2_MIN) return 1;
        if (focusMinutes < LEVEL_3_MIN) return 2;
        return MAX_LEVEL;
    }
}
