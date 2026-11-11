package com.example.bbettercalendar.ui.common.chart;

/**
 * Text and small calculations shared by the chart views (mejora-visual u4). Pure Java: units "h", "m" and
 * "%" read the same in English and Spanish, so nothing here needs resources.
 */
public final class ChartFormat {

    /** Typographic minus (U+2212) for negative changes, as in the mockups ("−43 %"). */
    public static final String MINUS = "−";

    /** {@link LimitBarView} states. */
    public static final int LIMIT_NONE = 0;
    public static final int LIMIT_WITHIN = 1;
    public static final int LIMIT_OVER = 2;

    private ChartFormat() {
    }

    /** Minutes as "2 h 30 m", "45 m", "2 h" (rounded to the minute; negatives as 0). */
    public static String hoursMinutes(float minutes) {
        int m = Math.max(0, Math.round(minutes));
        int h = m / 60;
        int r = m % 60;
        if (h == 0) {
            return r + " m";
        }
        return r == 0 ? h + " h" : h + " h " + r + " m";
    }

    /** "41 %". */
    public static String percent(int value) {
        return value + " %";
    }

    /** "+12 %", "−43 %", "0 %". */
    public static String signedPercent(int value) {
        if (value > 0) {
            return "+" + value + " %";
        }
        if (value < 0) {
            return MINUS + (-value) + " %";
        }
        return "0 %";
    }

    /** Whole-percent change from {@code before} to {@code after}; null when there is no "before". */
    public static Integer percentChange(float before, float after) {
        if (!(before > 0f) || Float.isNaN(after)) {
            return null;
        }
        return Math.round((after - before) / before * 100f);
    }

    /** Share of {@code part} in {@code total} as a whole percent (0 when the total is 0). */
    public static int share(float part, float total) {
        return total > 0f ? Math.round(part / total * 100f) : 0;
    }

    /** State of a usage bar: no limit, within it, or over it (strictly above). */
    public static int limitState(float used, float limit) {
        if (!(limit > 0f)) {
            return LIMIT_NONE;
        }
        return used > limit ? LIMIT_OVER : LIMIT_WITHIN;
    }
}
