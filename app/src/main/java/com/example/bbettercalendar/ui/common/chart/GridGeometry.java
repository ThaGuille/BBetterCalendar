package com.example.bbettercalendar.ui.common.chart;

/**
 * Cells of the heat maps (mejora-visual u4): square cells with a fixed gap, and the 5-step colour bucket
 * of a value. Pure Java.
 */
public final class GridGeometry {

    /** Steps of the sequential ramp: bucket 0 = no activity ({@code bb_heat_0}) ... 4 = most ({@code bb_heat_4}). */
    public static final int HEAT_STEPS = 5;

    private GridGeometry() {
    }

    /** Side of a cell when {@code count} cells and their gaps fill {@code available} px. */
    public static float cellSize(float available, int count, float gap) {
        if (count <= 0) {
            return 0f;
        }
        return Math.max(0f, (available - gap * (count - 1)) / count);
    }

    /** Start (left or top) of cell {@code i}. */
    public static float cellStart(int i, float start, float cell, float gap) {
        return start + i * (cell + gap);
    }

    /** Index of the cell under {@code pos} (a gap belongs to the cell before it), or -1 outside. */
    public static int indexAt(float pos, float start, float cell, float gap, int count) {
        if (count <= 0 || cell <= 0f || pos < start) {
            return -1;
        }
        int i = (int) ((pos - start) / (cell + gap));
        return i < count ? i : -1;
    }

    /**
     * Ramp bucket of {@code value} against the largest value {@code max}: 0 for no activity, otherwise 1..4
     * by quarters of the maximum (any activity is at least 1, the maximum is always 4).
     */
    public static int heatBucket(float value, float max) {
        if (!(value > 0f) || !(max > 0f)) {
            return 0;
        }
        float ratio = Math.min(1f, value / max);
        int bucket = (int) Math.ceil(ratio * (HEAT_STEPS - 1) - 1e-6);
        return Math.max(1, Math.min(HEAT_STEPS - 1, bucket));
    }
}
