package com.example.bbettercalendar.ui.common.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
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
 *   focus palette   soft amber -> bb_sanct_primary amber
 *   rest palette    soft sage  -> bb_sanct_sage_bright
 * </pre>
 *
 * The gradient is a {@link SweepGradient} over the full turn, rotated to start at 12 o'clock and run
 * clockwise, with its colours taken from {@link RingRamp}: the arc head gets more intense as the
 * session advances. The painter adds no start stub (that is {@code RingStateMapper}'s job).
 {@link #startSweep(View)} grows the arc from 0 to
 * its value in {@code bb_motion_ring_sweep} ms when a session starts; with the system animations off it
 * is drawn whole at once.
 */
public final class RingPainter {

    private static final float START_ANGLE = -90f;
    private static final int RAMP_STOPS = 16;
    private static final float SOFT_ALPHA = 0.45f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    @ColorInt private final int amber;
    @ColorInt private final int sageBright;
    @ColorInt private final int sage;
    private final Matrix shaderMatrix = new Matrix();

    @ColorInt private int softColor;
    @ColorInt private int intenseColor;
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
        // BUTT: a round cap at the start would sit on the gradient seam and show its intense colour.
        arcPaint.setStrokeCap(Paint.Cap.BUTT);
        headPaint.setStyle(Paint.Style.FILL);
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

    /** Rest palette (sage only) instead of the focus one (amber). */
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
        int intense = rest ? sageBright : amber;
        int base = rest ? sage : amber;
        int soft = (base & 0x00FFFFFF) | (Math.round(255 * SOFT_ALPHA) << 24);
        softColor = soft;
        intenseColor = intense;
        int[] colors = new int[RAMP_STOPS + 1];
        float[] stops = new float[RAMP_STOPS + 1];
        for (int i = 0; i <= RAMP_STOPS; i++) {
            stops[i] = i / (float) RAMP_STOPS;
            colors[i] = RingRamp.colorAt(soft, intense, stops[i]);
        }
        SweepGradient shader = new SweepGradient(bounds.centerX(), bounds.centerY(), colors, stops);
        // SweepGradient starts at 3 o'clock: rotate it to 12 o'clock (clockwise, like the arc).
        shaderMatrix.setRotate(START_ANGLE, bounds.centerX(), bounds.centerY());
        shader.setLocalMatrix(shaderMatrix);
        arcPaint.setShader(shader);
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
            if (RingRamp.hasHeadCap(f)) {
                // Round look only at the head: a small filled circle in the head colour.
                double rad = Math.toRadians(RingRamp.headAngle(START_ANGLE, f));
                float r = bounds.width() / 2f;
                headPaint.setColor(RingRamp.headColor(softColor, intenseColor, f));
                headPaint.setAlpha(Math.round(Color.alpha(headPaint.getColor()) * arcAlpha / 255f));
                canvas.drawCircle(bounds.centerX() + (float) (r * Math.cos(rad)),
                        bounds.centerY() + (float) (r * Math.sin(rad)),
                        arcPaint.getStrokeWidth() / 2f, headPaint);
            }
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
