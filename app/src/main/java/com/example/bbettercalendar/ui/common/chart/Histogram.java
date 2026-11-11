package com.example.bbettercalendar.ui.common.chart;

/** Buckets of {@link HistogramView} (mejora-visual u4). Pure Java. */
public final class Histogram {

    private Histogram() {
    }

    /**
     * Counts {@code samples} into {@code upperEdges.length + 1} bins: bin i holds the samples &lt;= upperEdges[i]
     * (and above the previous edge); the last bin is open ("45+"). Edges must be ascending.
     */
    public static int[] bin(int[] samples, int[] upperEdges) {
        int edges = upperEdges == null ? 0 : upperEdges.length;
        int[] counts = new int[edges + 1];
        if (samples == null) {
            return counts;
        }
        for (int s : samples) {
            int b = edges;
            for (int i = 0; i < edges; i++) {
                if (s <= upperEdges[i]) {
                    b = i;
                    break;
                }
            }
            counts[b]++;
        }
        return counts;
    }

    /** Index of the tallest bin (the first one on a tie), or -1 when every bin is empty. */
    public static int modalIndex(int[] counts) {
        if (counts == null) {
            return -1;
        }
        int best = -1;
        int max = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > max) {
                max = counts[i];
                best = i;
            }
        }
        return best;
    }
}
