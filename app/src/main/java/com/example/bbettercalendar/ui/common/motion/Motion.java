package com.example.bbettercalendar.ui.common.motion;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.TextView;

import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * Motion helpers (plan mejora-visual u2). Timings are {@code R.integer.bb_motion_*}, curves
 * {@code R.interpolator.bb_*}. Every helper checks {@link #enabled(Context)} first: with the system
 * animations off (Settings > Remove animations, animator_duration_scale 0) nothing moves and the views
 * are left in their final state.
 */
public final class Motion {

    /** Press scale (mockup 04). */
    public static final float PRESS_SCALE = 0.93f;
    /** staggerIn: duration per child and delay between children, in ms. */
    public static final long STAGGER_DURATION_MS = 260L;
    public static final long STAGGER_STEP_MS = 50L;

    private static final int TAG_COUNT_UP = R.id.bb_motion_count_up_tag;

    private Motion() {}

    /** Formats the number shown while {@link #countUp} runs (and the final value). */
    public interface ValueFormatter {
        @NonNull String format(long value);
    }

    /** True when animations run: ValueAnimator.areAnimatorsEnabled() (API 26+), the animator scale before that. */
    public static boolean enabled(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return ValueAnimator.areAnimatorsEnabled();
        }
        return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
    }

    /** {@code R.integer.bb_motion_*} in ms, or 0 when animations are off. */
    public static int duration(@NonNull Context context, @IntegerRes int res) {
        return enabled(context) ? context.getResources().getInteger(res) : 0;
    }

    @NonNull
    public static Interpolator interpolator(@NonNull Context context, int interpolatorRes) {
        return AnimationUtils.loadInterpolator(context, interpolatorRes);
    }

    /**
     * Press feedback for views that do not get it from the theme (custom views, rows): scales to 0.93 in
     * 80 ms on touch down and bounces back in 300 ms on release. The listener never consumes the event,
     * so clicks and ripples keep working. The view must be clickable (a non-clickable view gets the DOWN
     * but never the UP / CANCEL, so it would stay scaled).
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void pressable(@NonNull View view) {
        view.setOnTouchListener((v, event) -> {
            Context ctx = v.getContext();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scaleTo(v, PRESS_SCALE, duration(ctx, R.integer.bb_motion_press_down),
                            interpolator(ctx, R.interpolator.bb_standard));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    scaleTo(v, 1f, duration(ctx, R.integer.bb_motion_press_up),
                            interpolator(ctx, R.interpolator.bb_overshoot));
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private static void scaleTo(View v, float scale, int durationMs, Interpolator curve) {
        v.animate().cancel();
        if (durationMs <= 0) {
            v.setScaleX(scale);
            v.setScaleY(scale);
            return;
        }
        v.animate().scaleX(scale).scaleY(scale).setDuration(durationMs).setInterpolator(curve).start();
    }

    /**
     * Entrance of a group's children: each rises 10dp while fading in (260 ms, decelerate), 50 ms after
     * the previous one. Animations off: children stay where they are, fully visible.
     */
    public static void staggerIn(@NonNull ViewGroup group) {
        Context ctx = group.getContext();
        if (!enabled(ctx)) {
            return;
        }
        float offset = ctx.getResources().getDimension(R.dimen.motion_stagger_offset);
        Interpolator curve = interpolator(ctx, R.interpolator.bb_decelerate);
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(0f);
            child.setTranslationY(offset);
            child.animate().alpha(1f).translationY(0f)
                    .setStartDelay(i * STAGGER_STEP_MS)
                    .setDuration(STAGGER_DURATION_MS)
                    .setInterpolator(curve)
                    .start();
        }
    }

    /**
     * Counts the text from {@code from} to {@code to} in {@code bb_motion_count_up} ms (decelerate). With
     * animations off the final value is set at once. A running count on the same view is cancelled.
     *
     * @return the animator, or null when nothing animates
     */
    @Nullable
    public static ValueAnimator countUp(@NonNull TextView view, long from, long to, @NonNull ValueFormatter formatter) {
        Object running = view.getTag(TAG_COUNT_UP);
        if (running instanceof Animator) {
            ((Animator) running).cancel();
        }
        view.setTag(TAG_COUNT_UP, null);
        int duration = duration(view.getContext(), R.integer.bb_motion_count_up);
        if (duration <= 0 || from == to) {
            view.setText(formatter.format(to));
            return null;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator(view.getContext(), R.interpolator.bb_decelerate));
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            view.setText(formatter.format(from + Math.round((to - from) * f)));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                if (!cancelled) {
                    view.setText(formatter.format(to));
                }
                if (view.getTag(TAG_COUNT_UP) == a) {
                    view.setTag(TAG_COUNT_UP, null);
                }
            }
        });
        view.setTag(TAG_COUNT_UP, animator);
        animator.start();
        return animator;
    }
}
