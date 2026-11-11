package com.example.bbettercalendar.ui.common.chart;

/**
 * Horizontal layout of {@code count} equal slots (one bar or bar group each) across a plot (mejora-visual
 * u4). Pure Java; a tap anywhere in a slot's column selects its bar, so thin bars stay easy to hit.
 */
public final class BarSlots {

    private final float left;
    private final float width;
    private final int count;

    public BarSlots(float left, float width, int count) {
        this.left = left;
        this.width = Math.max(0f, width);
        this.count = Math.max(0, count);
    }

    public int count() {
        return count;
    }

    public float slotWidth() {
        return count == 0 ? 0f : width / count;
    }

    /** Centre x of slot {@code i}. */
    public float center(int i) {
        return left + slotWidth() * (i + 0.5f);
    }

    /** Slot under {@code x}, or -1 outside the plot. */
    public int indexAt(float x) {
        if (count == 0 || x < left || x >= left + width) {
            return -1;
        }
        int i = (int) ((x - left) / slotWidth());
        return Math.min(count - 1, Math.max(0, i));
    }
}
