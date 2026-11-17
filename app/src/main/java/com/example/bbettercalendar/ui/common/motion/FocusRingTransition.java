package com.example.bbettercalendar.ui.common.motion;

/**
 * Pure description (no Android calls) of the Home play button to Focus ring transition. {@link ScreenTransitions}
 * applies exactly these values, so the JVM tests can check them.
 *
 * <p>Plan pomodoro-calendario-ui u3 (decision d5): the button still grows into the ring box, but it fades through.
 * The start view (the play disc) fades out completely before the end view (the ring and its timer text) fades in,
 * on the Focus background colour, so the disc and the ring are never drawn together. Before this, the container
 * was transparent and the default fade mode (IN) kept the disc opaque while the ring faded in on top of it.
 */
public final class FocusRingTransition {

    /** How the two views of the shared element are blended; maps onto MaterialContainerTransform's fade modes. */
    public enum FadeMode { THROUGH, CROSS, IN, OUT }

    /**
     * Progress (0..1) of the shared-element animation where the fade through switches from the start view to the
     * end view: Material's FADE_MODE_THROUGH default threshold. The start view is fully out at this point and the
     * end view starts coming in from it.
     */
    public static final float FADE_THROUGH_THRESHOLD = 0.35f;

    /** Fade mode used for both directions. */
    public static final FadeMode FADE_MODE = FadeMode.THROUGH;

    /** Container colour is the Focus background (bb_sanct_surface), never transparent. */
    public static final boolean OPAQUE_CONTAINER = true;

    /** Both ends of the growing shape are circles (corner size relative to the view). */
    public static final float CORNER_SIZE_FRACTION = 0.5f;

    /** The start view's fade-out ends at this progress. */
    public static float startFadeOutEnd() {
        return FADE_THROUGH_THRESHOLD;
    }

    /** The end view's fade-in starts at this progress. */
    public static float endFadeInStart() {
        return FADE_THROUGH_THRESHOLD;
    }

    /** True when the start view is gone before the end view appears (disc and ring never drawn together). */
    public static boolean neverOverlaps() {
        return FADE_MODE == FadeMode.THROUGH && startFadeOutEnd() <= endFadeInStart();
    }

    /** True when the description is a shared element at all (the user kept the growing button: d5 option 1). */
    public static boolean hasSharedElement() {
        return true;
    }

    private FocusRingTransition() {}
}
