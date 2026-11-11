package com.example.bbettercalendar.ui.common.chart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Slices and angles of {@link DonutView} (mejora-visual u4). Pure Java. Angles are in degrees as the
 * Canvas uses them: 0 = 3 o'clock, clockwise; the first slice starts at 12 o'clock (-90).
 */
public final class DonutGeometry {

    /** Source index of the folded "Others" slice. */
    public static final int OTHERS = -1;

    private DonutGeometry() {
    }

    /** Result of {@link #fold}: slice values, largest first, and the input index of each (or {@link #OTHERS}). */
    public static final class Folded {
        public final float[] values;
        public final int[] source;

        Folded(float[] values, int[] source) {
            this.values = values;
            this.source = source;
        }
    }

    /**
     * Keeps the {@code maxSlices} largest positive values (largest first; ties keep input order) and adds the
     * rest up into one trailing "Others" slice. With {@code maxSlices} or fewer positive values nothing folds.
     */
    public static Folded fold(float[] values, int maxSlices) {
        List<Integer> idx = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] > 0f && !Float.isInfinite(values[i])) {
                    idx.add(i);
                }
            }
        }
        final float[] v = values;
        Collections.sort(idx, (a, b) -> Float.compare(v[b], v[a]));
        int keep = Math.min(idx.size(), Math.max(0, maxSlices));
        boolean others = idx.size() > keep;
        int n = keep + (others ? 1 : 0);
        float[] outV = new float[n];
        int[] outS = new int[n];
        for (int i = 0; i < keep; i++) {
            outS[i] = idx.get(i);
            outV[i] = v[idx.get(i)];
        }
        if (others) {
            float rest = 0f;
            for (int i = keep; i < idx.size(); i++) {
                rest += v[idx.get(i)];
            }
            outV[keep] = rest;
            outS[keep] = OTHERS;
        }
        return new Folded(outV, outS);
    }

    /**
     * Start angle and sweep of every slice, {@code gapDeg} apart (no gap for a single slice).
     *
     * @return {@code [0]} starts, {@code [1]} sweeps
     */
    public static float[][] arcs(float[] values, float gapDeg) {
        int n = values == null ? 0 : values.length;
        float[][] out = new float[2][n];
        float total = ChartScale.sum(values);
        if (n == 0 || !(total > 0f)) {
            return out;
        }
        float gap = n == 1 ? 0f : Math.max(0f, gapDeg);
        float available = 360f - gap * n;
        float start = -90f + gap / 2f;
        for (int i = 0; i < n; i++) {
            float sweep = values[i] > 0f ? available * values[i] / total : 0f;
            out[0][i] = start;
            out[1][i] = sweep;
            start += sweep + gap;
        }
        return out;
    }

    /** Canvas angle of a point at ({@code dx}, {@code dy}) from the centre (y grows downwards). */
    public static float angleOf(float dx, float dy) {
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    /**
     * Slice under {@code angleDeg}; a tap in the gap goes to the nearer neighbour (half a gap each side).
     * -1 when there are no slices.
     */
    public static int sliceAt(float angleDeg, float[] starts, float[] sweeps, float gapDeg) {
        int n = starts == null ? 0 : starts.length;
        if (n == 0) {
            return -1;
        }
        float half = n == 1 ? 180f : Math.max(0f, gapDeg) / 2f;
        for (int i = 0; i < n; i++) {
            float from = starts[i] - half;
            float rel = normalize(angleDeg - from);
            if (rel < sweeps[i] + 2 * half) {
                return i;
            }
        }
        return -1;
    }

    /** Angle in [0, 360). */
    static float normalize(float deg) {
        float a = deg % 360f;
        return a < 0f ? a + 360f : a;
    }
}
