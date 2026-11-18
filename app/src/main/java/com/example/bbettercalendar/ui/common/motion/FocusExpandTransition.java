package com.example.bbettercalendar.ui.common.motion;

/**
 * Pure description (no Android calls) of the Home to Focus transition. {@link ScreenTransitions} applies exactly
 * these values, so the JVM tests can check them.
 *
 * <p>2026-11-18 (pequenas-mejoras): Focus is no longer "a new screen". The Home timer card (the hero with the play
 * button) expands downwards until it fills the whole screen and becomes Focus; Back shrinks Focus back into the
 * card. One container transform with the whole card as the start view and the whole Focus root as the end view;
 * the rest of Home holds still underneath. It replaces the play-disc-to-ring transform of pomodoro-calendario-ui
 * u3 (decision d5), whose fade-through rule it keeps: the card's content fades out completely before Focus fades in,
 * on the Focus background colour, so the two are never drawn together.
 */
public final class FocusExpandTransition {

    /** How the two views of the shared element are blended; maps onto MaterialContainerTransform's fade modes. */
    public enum FadeMode { THROUGH, CROSS, IN, OUT }

    /**
     * Progress (0..1) of the shared-element animation where the fade through switches from the start view to the
     * end view: Material's FADE_MODE_THROUGH default threshold.
     */
    public static final float FADE_THROUGH_THRESHOLD = 0.35f;

    /** Fade mode used for both directions. */
    public static final FadeMode FADE_MODE = FadeMode.THROUGH;

    /** Container colour is the Focus background (bb_sanct_surface), never transparent. */
    public static final boolean OPAQUE_CONTAINER = true;

    /**
     * Corner radius of the Focus end: square, it is the whole screen. The card end uses the card's own radius
     * ({@code R.dimen.radius_card}), so the shape morphs from the card to the screen and back.
     */
    public static final float SCREEN_CORNER_RADIUS = 0f;

    /** The start view's fade-out ends at this progress. */
    public static float startFadeOutEnd() {
        return FADE_THROUGH_THRESHOLD;
    }

    /** The end view's fade-in starts at this progress. */
    public static float endFadeInStart() {
        return FADE_THROUGH_THRESHOLD;
    }

    /** True when the start view is gone before the end view appears (card and Focus never drawn together). */
    public static boolean neverOverlaps() {
        return FADE_MODE == FadeMode.THROUGH && startFadeOutEnd() <= endFadeInStart();
    }

    /**
     * True: the shared element is the whole Home timer card on one side and the whole Focus screen on the other
     * (the screen grows out of the card), not a single button.
     */
    public static boolean expandsWholeScreen() {
        return true;
    }

    private FocusExpandTransition() {}
}
