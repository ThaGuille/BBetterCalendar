package com.example.bbettercalendar.ui.common.chart;

/**
 * Value scales of the Canvas chart views (mejora-visual u4). Pure Java, no Android: unit-tested in
 * {@code ChartScaleTest}. Values are plain floats; time charts use minutes.
 */
public final class ChartScale {

    /** Grid steps for minute axes: 5 m ... 1 day, so ticks read "30 m", "1 h", "2 h"... */
    private static final float[] MINUTE_STEPS = {5, 10, 15, 30, 60, 120, 180, 240, 360, 480, 720, 1440};

    private ChartScale() {
    }

    /** Largest finite, positive value among all the series (0 when there is none). Nulls are skipped. */
    public static float max(float[]... series) {
        float max = 0f;
        if (series == null) {
            return 0f;
        }
        for (float[] s : series) {
            if (s == null) {
                continue;
            }
            for (float v : s) {
                if (!Float.isNaN(v) && !Float.isInfinite(v) && v > max) {
                    max = v;
                }
            }
        }
        return max;
    }

    /** Mean of the finite values (0 for an empty or null array). */
    public static float mean(float[] values) {
        if (values == null) {
            return 0f;
        }
        double sum = 0;
        int n = 0;
        for (float v : values) {
            if (!Float.isNaN(v) && !Float.isInfinite(v)) {
                sum += v;
                n++;
            }
        }
        return n == 0 ? 0f : (float) (sum / n);
    }

    /** Sum of the finite, positive values of a row. */
    public static float sum(float[] values) {
        if (values == null) {
            return 0f;
        }
        float sum = 0f;
        for (float v : values) {
            if (!Float.isNaN(v) && !Float.isInfinite(v) && v > 0) {
                sum += v;
            }
        }
        return sum;
    }

    /**
     * Length in px of a bar for {@code value} on a scale whose top is {@code scaleMax} and whose full
     * length is {@code px}. Clamped to 0..px; a non-positive scale gives 0.
     */
    public static float extent(float value, float scaleMax, float px) {
        if (!(scaleMax > 0f) || !(value > 0f) || px <= 0f) {
            return 0f;
        }
        return Math.min(1f, value / scaleMax) * px;
    }

    /**
     * Smallest step of {@link #MINUTE_STEPS} that gives at most {@code maxTicks} grid lines up to
     * {@code max} minutes (150 m with 3 ticks -> 60, i.e. lines at 1 h and 2 h).
     */
    public static float niceMinuteStep(float max, int maxTicks) {
        int ticks = Math.max(1, maxTicks);
        if (!(max > 0f)) {
            return MINUTE_STEPS[0];
        }
        for (float step : MINUTE_STEPS) {
            if (max / step <= ticks) {
                return step;
            }
        }
        float last = MINUTE_STEPS[MINUTE_STEPS.length - 1];
        return (float) (Math.ceil(max / ticks / last) * last);
    }

    /** 1-2-5 step for unit-less axes (counts) with at most {@code maxTicks} lines up to {@code max}. */
    public static float niceStep(float max, int maxTicks) {
        int ticks = Math.max(1, maxTicks);
        if (!(max > 0f)) {
            return 1f;
        }
        double raw = max / ticks;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double[] factors = {1, 2, 5, 10};
        for (double f : factors) {
            if (f * magnitude >= raw) {
                return (float) Math.max(1d, f * magnitude);
            }
        }
        return (float) Math.max(1d, 10 * magnitude);
    }

    /** Grid values step, 2·step, ... that are &lt;= max (0 itself excluded). */
    public static float[] ticks(float max, float step) {
        if (!(max > 0f) || !(step > 0f)) {
            return new float[0];
        }
        int n = (int) Math.floor(max / step + 1e-4);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = step * (i + 1);
        }
        return out;
    }
}
