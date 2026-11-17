package com.example.bbettercalendar.ui.common.widget;

/**
 * Colour weight along the full turn of the progress ring (pure: no Android calls). The arc is drawn
 * from a gradient fixed to the whole turn, so the head of the arc gets more intense as the session
 * advances: soft at the start of the turn, fully intense at its end.
 */
public final class RingRamp {

    /** Weight at the very start of the turn: soft, never invisible. */
    public static final float START_INTENSITY = 0.3f;

    private RingRamp() {}

    /** Weight 0..1 of the intense colour at {@code t} (0..1 of the turn); strictly increasing, 1 at t = 1. */
    public static float intensity(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        return START_INTENSITY + (1f - START_INTENSITY) * c;
    }

    /** ARGB blend: {@code soft} at weight 0, {@code intense} at weight 1 (each channel, alpha included). */
    public static int blend(int soft, int intense, float weight) {
        float w = Math.max(0f, Math.min(1f, weight));
        int a = mix(soft >>> 24, intense >>> 24, w);
        int r = mix((soft >> 16) & 0xFF, (intense >> 16) & 0xFF, w);
        int g = mix((soft >> 8) & 0xFF, (intense >> 8) & 0xFF, w);
        int b = mix(soft & 0xFF, intense & 0xFF, w);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Colour of the turn at {@code t}: soft to intense along {@link #intensity(float)}. */
    public static int colorAt(int soft, int intense, float t) {
        return blend(soft, intense, intensity(t));
    }

    private static int mix(int from, int to, float w) {
        return Math.round(from + (to - from) * w);
    }
}
