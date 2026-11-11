package com.example.bbettercalendar.ui.common.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * The one ring painter of the app (mejora-visual u6): a thin track plus a round-capped arc that starts
 * at 12 o'clock and runs clockwise. Shared by {@code ui/focus/FocusRingView} (the big Focus ring) and
 * {@link RingButtonView} (play button and task rows), so both draw exactly the same ring.
 *
 * <pre>
 *   focus palette   amber -> light sage -> sage (stops 0 / .65 / 1)
 *   rest palette    sage -> light sage
 * </pre>
 *
 * The gradient is linear from the bottom-left to the top-right corner of the ring box (the html
 * mockups rotate a TL->BR svg gradient by -90 degrees). {@link #startSweep(View)} grows the arc from 0 to
 * its value in {@code bb_motion_ring_sweep} ms when a session starts; with the system animations off it
 * is drawn whole at once.
 */
public final class RingPainter {

    private static final float START_ANGLE = -90f;
    private static final float[] FOCUS_STOPS = {0f, 0.65f, 1f};

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    @ColorInt private final int amber;
    @ColorInt private final int sageBright;
    @ColorInt private final int sage;

    private boolean rest;
    private int arcAlpha = 255;
    /** 0..1 multiplier of the drawn arc while the start sweep runs; 1 otherwise. */
    private float sweep = 1f;
    @Nullable private ValueAnimator sweepAnimator;

    public RingPainter(@NonNull Context context) {
        amber = ContextCompat.getColor(context, R.color.bb_sanct_primary);
        sageBright = ContextCompat.getColor(context, R.color.bb_sanct_sage_bright);
        sage = ContextCompat.getColor(context, R.color.bb_sanct_sage);
        trackPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setTrackColor(@ColorInt int color) {
        trackPaint.setColor(color);
    }

    /** Stroke widths in px: the track and the arc on top of it. */
    public void setStrokeWidths(float trackPx, float arcPx) {
        trackPaint.setStrokeWidth(trackPx);
        arcPaint.setStrokeWidth(arcPx);
    }

    /** Centre and radius (of the stroke's centre line) in px; rebuilds the gradient. */
    public void setBounds(float cx, float cy, float radius) {
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        updateShader();
    }

    /** Rest palette (sage only) instead of the focus one (amber to sage). */
    public void setRest(boolean rest) {
        if (this.rest != rest) {
            this.rest = rest;
            updateShader();
        }
    }

    /** Opacity of the arc, 0..1 (a paused ring is dimmed). The track keeps its colour. */
    public void setArcAlpha(float alpha) {
        arcAlpha = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        arcPaint.setAlpha(arcAlpha);
    }

    @NonNull
    public RectF getBounds() {
        return bounds;
    }

    private void updateShader() {
        if (bounds.isEmpty()) {
            return;
        }
        int[] colors = rest ? new int[]{sage, sageBright} : new int[]{amber, sageBright, sage};
        arcPaint.setShader(new LinearGradient(bounds.left, bounds.bottom, bounds.right, bounds.top,
                colors, rest ? null : FOCUS_STOPS, Shader.TileMode.CLAMP));
        arcPaint.setAlpha(arcAlpha);
    }

    /** Track circle plus the arc for {@code fraction} (0..1) of the turn. */
    public void draw(@NonNull Canvas canvas, float fraction) {
        if (bounds.isEmpty()) {
            return;
        }
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() / 2f, trackPaint);
        float f = Math.max(0f, Math.min(1f, fraction)) * sweep;
        if (f > 0f) {
            canvas.drawArc(bounds, START_ANGLE, 360f * f, false, arcPaint);
        }
    }

    /**
     * Grows the arc from nothing to its value (start of a session), invalidating {@code host} on every
     * frame. Animations off: nothing moves, the arc is drawn whole.
     */
    public void startSweep(@NonNull View host) {
        cancelSweep();
        Context ctx = host.getContext();
        int duration = Motion.duration(ctx, R.integer.bb_motion_ring_sweep);
        if (duration <= 0) {
            host.invalidate();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(Motion.interpolator(ctx, R.interpolator.bb_decelerate));
        animator.addUpdateListener(a -> {
            sweep = (float) a.getAnimatedValue();
            host.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                sweep = 1f;
                if (sweepAnimator == a) {
                    sweepAnimator = null;
                }
                host.invalidate();
            }
        });
        sweep = 0f;
        sweepAnimator = animator;
        animator.start();
    }

    /** Stops a running sweep and leaves the arc whole (call from onDetachedFromWindow). */
    public void cancelSweep() {
        ValueAnimator running = sweepAnimator;
        sweepAnimator = null;
        if (running != null) {
            running.cancel();
        }
        sweep = 1f;
    }

    public boolean isSweeping() {
        return sweepAnimator != null;
    }
}
