package com.example.bbettercalendar.ui.common.motion;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.view.OneShotPreDrawListener;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.transition.Transition;

import com.example.bbettercalendar.R;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.transition.Hold;
import com.google.android.material.transition.MaterialContainerTransform;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;

import java.util.concurrent.TimeUnit;

/**
 * Screen transitions (plan mejora-visual u15), all from {@code com.google.android.material.transition}:
 * <ul>
 *   <li>tab to tab (the bottom bar): {@link MaterialFadeThrough};</li>
 *   <li>tab to a secondary screen (Focus, All progress, Screen time, profile...) and back: {@link MaterialSharedAxis} Z;</li>
 *   <li>secondary to secondary (Screen time to app detail...) and period / month changes: {@link MaterialSharedAxis} X;</li>
 *   <li>project card to its detail: {@link MaterialContainerTransform} with {@link Hold} on the list;</li>
 *   <li>Home play button to the Focus ring: {@link MaterialContainerTransform} on the shared ring plus the Z axis
 *       for the rest of both screens;</li>
 *   <li>sheets and dialogs keep Material's own animations (nothing here touches them).</li>
 * </ul>
 * Durations: {@code bb_motion_screen_enter} (300 ms) and {@code bb_motion_screen_exit} (250 ms) through
 * {@link Motion#duration}, so with the system animations off every transition lasts 0 ms and navigation is instant.
 *
 * <p>How the pieces meet. Every navigation fragment calls {@link #apply(Fragment)} in {@code onCreate} (MainActivity
 * applies it to the ones that do not, the social screens). Which transition a screen plays depends on where it comes
 * from and where it goes, so MainActivity's destination listener calls {@link #onNavigate} before the fragment
 * transaction runs: it sets the exit / reenter / return of the screen that leaves and leaves the kind pending for the
 * screen that is about to be created. Each fragment keeps its kinds in its arguments, so a rotation or a recreated
 * process plays the same transitions on the way back. Reenter mirrors the exit and return mirrors the enter: system
 * Back plays the reverse transition.
 */
public final class ScreenTransitions {

    /** Transition family of one navigation. */
    public enum Kind { FADE_THROUGH, AXIS_Z, AXIS_X, CONTAINER }

    @VisibleForTesting static final String ARG_ENTER = "bb_transition_enter";
    @VisibleForTesting static final String ARG_EXIT = "bb_transition_exit";

    /** transitionName shared by the Home play button and the Focus ring box. */
    public static final String FOCUS_RING = "bb_focus_ring";
    private static final String PROJECT_CARD_PREFIX = "bb_project_card_";

    /** Longest wait for the data of a postponed screen before it enters anyway. */
    private static final long POSTPONE_TIMEOUT_MS = 500L;

    /** Kind decided by {@link #onNavigate} for the screen being created; consumed by {@link #apply}. */
    @Nullable private static Kind pendingEnter;
    /** Set by {@link #prepareContainerTransform()} right before a navigate() with a shared element. */
    private static boolean pendingContainer;

    private ScreenTransitions() {}

    // ------------------------------------------------------------------ decisions (pure)

    /** Tab to tab: fade through. Tab to secondary or back: Z axis. Secondary to secondary: X axis. */
    @NonNull
    public static Kind classify(boolean fromTopLevel, boolean toTopLevel) {
        if (fromTopLevel && toTopLevel) return Kind.FADE_THROUGH;
        if (!fromTopLevel && !toTopLevel) return Kind.AXIS_X;
        return Kind.AXIS_Z;
    }

    /**
     * Enter kind of a screen being created: what it saved (recreation), else what the navigation left pending,
     * else fade through (the start destination).
     */
    @NonNull
    @VisibleForTesting
    static Kind resolveEnter(@Nullable Kind saved, @Nullable Kind pending) {
        if (saved != null) return saved;
        if (pending != null) return pending;
        return Kind.FADE_THROUGH;
    }

    /** A screen that entered through a container transform returns through it: its own return stays untouched. */
    @VisibleForTesting
    static boolean keepsReturn(@Nullable Kind enteredWith) {
        return enteredWith == Kind.CONTAINER;
    }

    @Nullable
    @VisibleForTesting
    static Kind parse(@Nullable String name) {
        if (name == null) return null;
        for (Kind kind : Kind.values()) {
            if (kind.name().equals(name)) return kind;
        }
        return null;
    }

    // ------------------------------------------------------------------ fragments

    /** Call from {@code onCreate}: enter / return from where the screen came, exit / reenter as last left. */
    public static void apply(@NonNull Fragment fragment) {
        Bundle args = argumentsOf(fragment);
        Kind enter = resolveEnter(args == null ? null : parse(args.getString(ARG_ENTER)), pendingEnter);
        pendingEnter = null;
        Kind exit = args == null ? null : parse(args.getString(ARG_EXIT));
        if (args != null) args.putString(ARG_ENTER, enter.name());
        setEnter(fragment, enter);
        setExit(fragment, exit == null ? Kind.FADE_THROUGH : exit);
    }

    /** MainActivity fallback for the navigation fragments that do not call {@link #apply} themselves. */
    public static void applyIfMissing(@NonNull Fragment fragment) {
        if (fragment.getExitTransition() == null && fragment.getEnterTransition() == null
                && fragment.getSharedElementEnterTransition() == null) {
            apply(fragment);
        }
    }

    /**
     * MainActivity's destination listener, called before the fragment transaction runs (navigate() commits it
     * asynchronously): the primary navigation fragment is still the screen that leaves.
     */
    public static void onNavigate(@NonNull FragmentManager navHostManager, boolean fromTopLevel, boolean toTopLevel) {
        Kind kind = pendingContainer ? Kind.CONTAINER : classify(fromTopLevel, toTopLevel);
        pendingContainer = false;
        pendingEnter = kind;
        Fragment outgoing = navHostManager.getPrimaryNavigationFragment();
        if (outgoing == null || outgoing.getContext() == null) return;
        setExit(outgoing, kind);
        Bundle args = argumentsOf(outgoing);
        if (args != null) args.putString(ARG_EXIT, kind.name());
        // When the navigation pops the outgoing screen (Back, a tab change, popUpTo), its return plays.
        Kind entered = args == null ? null : parse(args.getString(ARG_ENTER));
        if (kind != Kind.CONTAINER && !keepsReturn(entered)) {
            Context ctx = outgoing.requireContext();
            outgoing.setReturnTransition(screen(ctx, kind, false, exitMs(ctx)));
        }
    }

    /** Call right before navigating with a shared element from a card (FragmentNavigatorExtras). */
    public static void prepareContainerTransform() {
        pendingContainer = true;
    }

    /** transitionName of a project card and of the detail it opens. */
    @NonNull
    public static String projectCardName(int projectId) {
        return PROJECT_CARD_PREFIX + projectId;
    }

    /**
     * Focus: the Home play button grows into the ring box (container transform, circular both ends) while the rest
     * of the screen uses its Z axis. Without a shared element (any other way in) only the Z axis plays.
     */
    public static void sharedFocusRing(@NonNull Fragment focus) {
        Context ctx = focus.requireContext();
        ShapeAppearanceModel circle = ShapeAppearanceModel.builder()
                .setAllCornerSizes(new RelativeCornerSize(0.5f)).build();
        MaterialContainerTransform in = container(ctx, enterMs(ctx), Color.TRANSPARENT);
        in.setStartShapeAppearanceModel(circle);
        in.setEndShapeAppearanceModel(circle);
        MaterialContainerTransform back = container(ctx, exitMs(ctx), Color.TRANSPARENT);
        back.setStartShapeAppearanceModel(circle);
        back.setEndShapeAppearanceModel(circle);
        focus.setSharedElementEnterTransition(in);
        focus.setSharedElementReturnTransition(back);
    }

    /** Postpones the enter of a screen whose layout depends on data, at most {@value #POSTPONE_TIMEOUT_MS} ms. */
    public static void postponeUntilReady(@NonNull Fragment fragment) {
        fragment.postponeEnterTransition(POSTPONE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /** Starts a postponed enter once {@code laidOut} has its data laid out (next pre-draw). Safe to call often. */
    public static void startWhenDrawn(@NonNull Fragment fragment, @NonNull View laidOut) {
        OneShotPreDrawListener.add(laidOut, () -> {
            if (fragment.isAdded()) fragment.startPostponedEnterTransition();
        });
    }

    // ------------------------------------------------------------------ in-screen changes

    /**
     * Period or month change: the incoming half of a shared X axis on {@code target} (slides 30dp in from the side
     * of the step and fades in). Animations off: the view is left in place.
     */
    public static void periodShift(@NonNull View target, boolean forward) {
        Context ctx = target.getContext();
        int duration = enterMs(ctx);
        target.animate().cancel();
        if (duration <= 0) {
            target.setTranslationX(0f);
            target.setAlpha(1f);
            return;
        }
        float dx = ctx.getResources().getDimension(R.dimen.motion_shared_axis_slide);
        target.setTranslationX(forward ? dx : -dx);
        target.setAlpha(0f);
        target.animate().translationX(0f).alpha(1f).setDuration(duration)
                .setInterpolator(curve(ctx)).start();
    }

    /**
     * Bottom bar (and its divider) on tabs only. It overlays the screens (MainActivity), so hiding it never resizes
     * the navigation host: it fades with the screen transition and stays laid out (INVISIBLE) to keep its height.
     */
    public static void showBottomBar(boolean show, boolean animate, @NonNull View... views) {
        for (View view : views) {
            Context ctx = view.getContext();
            int duration = animate ? (show ? enterMs(ctx) : exitMs(ctx)) : 0;
            view.animate().cancel();
            if (show) {
                if (view.getVisibility() != View.VISIBLE) {
                    view.setAlpha(duration > 0 ? 0f : 1f);
                    view.setVisibility(View.VISIBLE);
                }
                if (duration > 0) {
                    view.animate().alpha(1f).setDuration(duration).setInterpolator(curve(ctx)).start();
                } else {
                    view.setAlpha(1f);
                }
            } else if (duration > 0 && view.getVisibility() == View.VISIBLE) {
                view.animate().alpha(0f).setDuration(duration).setInterpolator(curve(ctx))
                        .withEndAction(() -> view.setVisibility(View.INVISIBLE)).start();
            } else {
                view.setVisibility(View.INVISIBLE);
            }
        }
    }

    // ------------------------------------------------------------------ builders

    private static void setEnter(@NonNull Fragment f, @NonNull Kind kind) {
        Context ctx = f.requireContext();
        if (kind == Kind.CONTAINER) {
            // The whole screen is the shared element: nothing else moves on its own.
            f.setEnterTransition(null);
            f.setReturnTransition(null);
            int surface = ContextCompat.getColor(ctx, R.color.bb_surface);
            f.setSharedElementEnterTransition(container(ctx, enterMs(ctx), surface));
            f.setSharedElementReturnTransition(container(ctx, exitMs(ctx), surface));
            return;
        }
        f.setEnterTransition(screen(ctx, kind, true, enterMs(ctx)));
        f.setReturnTransition(screen(ctx, kind, false, exitMs(ctx)));
    }

    private static void setExit(@NonNull Fragment f, @NonNull Kind kind) {
        Context ctx = f.requireContext();
        if (kind == Kind.CONTAINER) {
            // The list stays put under the growing card, and on the way back while it shrinks.
            Hold out = new Hold();
            out.setDuration(enterMs(ctx));
            Hold back = new Hold();
            back.setDuration(exitMs(ctx));
            f.setExitTransition(out);
            f.setReenterTransition(back);
            return;
        }
        f.setExitTransition(screen(ctx, kind, true, exitMs(ctx)));
        f.setReenterTransition(screen(ctx, kind, false, enterMs(ctx)));
    }

    @NonNull
    private static Transition screen(@NonNull Context ctx, @NonNull Kind kind, boolean forward, int duration) {
        Transition t;
        switch (kind) {
            case AXIS_Z:
                t = new MaterialSharedAxis(MaterialSharedAxis.Z, forward);
                break;
            case AXIS_X:
                t = new MaterialSharedAxis(MaterialSharedAxis.X, forward);
                break;
            case FADE_THROUGH:
            default:
                t = new MaterialFadeThrough();
                break;
        }
        t.setDuration(duration);
        t.setInterpolator(curve(ctx));
        return t;
    }

    @NonNull
    private static MaterialContainerTransform container(@NonNull Context ctx, int duration, int containerColor) {
        MaterialContainerTransform t = new MaterialContainerTransform();
        // Drawn over the whole navigation host, so the card can grow to the full screen.
        t.setDrawingViewId(R.id.nav_host_fragment_activity_main);
        t.setDuration(duration);
        t.setInterpolator(curve(ctx));
        t.setScrimColor(Color.TRANSPARENT);
        t.setAllContainerColors(containerColor);
        return t;
    }

    private static int enterMs(@NonNull Context ctx) {
        return Motion.duration(ctx, R.integer.bb_motion_screen_enter);
    }

    private static int exitMs(@NonNull Context ctx) {
        return Motion.duration(ctx, R.integer.bb_motion_screen_exit);
    }

    @NonNull
    private static Interpolator curve(@NonNull Context ctx) {
        return Motion.interpolator(ctx, R.interpolator.bb_standard);
    }

    /** The fragment's arguments, created empty when it has none (only while its state is not saved). */
    @Nullable
    private static Bundle argumentsOf(@NonNull Fragment fragment) {
        Bundle args = fragment.getArguments();
        if (args == null && !fragment.isStateSaved()) {
            args = new Bundle();
            fragment.setArguments(args);
        }
        return args;
    }
}
