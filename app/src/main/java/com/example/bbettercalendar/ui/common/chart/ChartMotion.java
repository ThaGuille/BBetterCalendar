package com.example.bbettercalendar.ui.common.chart;

/**
 * Timing of the staggered chart entrance (mejora-visual u4): every mark (bar, column, slice) grows in
 * {@code duration} ms and starts {@code step} ms after the previous one. The step is the 40 ms of
 * {@code bb_motion_chart_stagger}, shrunk when there are many marks so the whole entrance never spans more
 * than {@code maxSpan} ms of delays (a 26-column heat map would otherwise take more than a second).
 * Pure Java; the curve is applied by the view.
 */
public final class ChartMotion {

    private ChartMotion() {
    }

    /** Delay between two consecutive marks. */
    public static float stepMs(int count, float staggerMs, float maxSpanMs) {
        if (count <= 1 || staggerMs <= 0f) {
            return 0f;
        }
        return Math.min(staggerMs, Math.max(0f, maxSpanMs) / (count - 1));
    }

    /** Length of the whole entrance. */
    public static float totalMs(int count, float durationMs, float staggerMs, float maxSpanMs) {
        return Math.max(0f, durationMs) + stepMs(count, staggerMs, maxSpanMs) * Math.max(0, count - 1);
    }

    /** Linear progress 0..1 of mark {@code index} at {@code elapsedMs} into the entrance. */
    public static float markFraction(float elapsedMs, int index, int count, float durationMs, float staggerMs,
                                     float maxSpanMs) {
        if (durationMs <= 0f) {
            return 1f;
        }
        float start = stepMs(count, staggerMs, maxSpanMs) * Math.max(0, index);
        float f = (elapsedMs - start) / durationMs;
        return Math.max(0f, Math.min(1f, f));
    }
}
