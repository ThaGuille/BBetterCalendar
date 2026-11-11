package com.example.bbettercalendar.ui.focus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.motion.Motion;
import com.example.bbettercalendar.ui.common.widget.RingPainter;

/**
 * Focus hero ring, «Calm Focus Sanctuary» (.stitch/html/01b-01d): a hearth glow behind a thin
 * track and a round-capped arc that shows the time LEFT, from 12 o'clock clockwise.
 *
 * <pre>
 *   FOCUS   amber → light sage → sage arc, amber glow (01b)
 *   REST    sage → light sage arc, cool sage glow only (01c)
 *   PAUSED  the FOCUS arc at 45 %, glow at a third (01d)
 * </pre>
 *
 * Track and arc are painted by the shared {@link RingPainter} (mejora-visual u6), the same one the
 * play buttons use; this view adds the glows. The glow is a radial gradient (no blur filter on a View
 * canvas, the stops do the fade). {@link #setBreathing(boolean)} makes it breathe slowly (alpha and
 * scale, 4 s a breath) on this screen only, and only with the system animations on; {@link #sweepIn()}
 * grows the arc when a session starts.
 */
public class FocusRingView extends View {

    public enum Mode { FOCUS, REST, PAUSED }

    // Proportions of the html: 270px ring box, r = 118, track 6, arc 7. The glow (w-72, 288px)
    // spills past the svg box in the html; here it is clipped to the view, so it fills the box.
    private static final float RING_RADIUS_OF_SIZE = 118f / 270f;
    private static final float PAUSED_ARC_ALPHA = 0.45f;
    private static final float PAUSED_GLOW_SCALE = 1f / 3f;
    /** Breathing range: the glow goes from this alpha / scale to full and back. */
    private static final float BREATH_MIN_ALPHA = 0.6f;
    private static final float BREATH_MIN_SCALE = 0.92f;

    private final RingPainter ring;
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sageGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @ColorInt private final int glowStart;
    @ColorInt private final int sageGlow;

    private Mode mode = Mode.FOCUS;
    private float remaining = 1f;

    private boolean breathingWanted;
    @Nullable private ValueAnimator breath;
    /** 0 = exhaled (dim, small) .. 1 = full glow. */
    private float breathLevel = 1f;

    public FocusRingView(@NonNull Context context) {
        this(context, null);
    }

    public FocusRingView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        glowStart = ContextCompat.getColor(context, R.color.bb_sanct_glow_start);
        sageGlow = ContextCompat.getColor(context, R.color.bb_sanct_glow_sage);

        float density = getResources().getDisplayMetrics().density;
        ring = new RingPainter(context);
        ring.setTrackColor(ContextCompat.getColor(context, R.color.bb_sanct_track));
        ring.setStrokeWidths(6f * density, 7f * density);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Fraction of the block still to go, 0..1 (1 = full ring). */
    public void setRemaining(float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        if (f != remaining) {
            remaining = f;
            invalidate();
        }
    }

    public void setMode(@NonNull Mode newMode) {
        if (newMode != mode) {
            mode = newMode;
            updateShaders(getWidth(), getHeight());
            invalidate();
        }
    }

    /** Grows the arc from nothing to its value (a session starts). */
    public void sweepIn() {
        ring.startSweep(this);
    }

    /** Slow breathing of the glow while true (no-op with the system animations off). */
    public void setBreathing(boolean on) {
        if (breathingWanted == on) {
            return;
        }
        breathingWanted = on;
        if (on) {
            startBreathing();
        } else {
            stopBreathing();
        }
    }

    private void startBreathing() {
        if (breath != null || !isAttachedToWindow() || !Motion.enabled(getContext())) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 0f);
        animator.setDuration(getResources().getInteger(R.integer.bb_motion_breathe_half));
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a -> {
            breathLevel = (float) a.getAnimatedValue();
            invalidate();
        });
        breath = animator;
        animator.start();
    }

    private void stopBreathing() {
        ValueAnimator running = breath;
        breath = null;
        if (running != null) {
            running.cancel();
        }
        breathLevel = 1f;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (breathingWanted) {
            startBreathing();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        ValueAnimator running = breath;
        breath = null;
        if (running != null) {
            running.cancel();
        }
        breathLevel = 1f;
        ring.cancelSweep();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShaders(w, h);
    }

    private void updateShaders(int w, int h) {
        if (w == 0 || h == 0) {
            return;
        }
        float cx = w / 2f;
        float cy = h / 2f;
        float size = Math.min(w, h);
        ring.setBounds(cx, cy, size * RING_RADIUS_OF_SIZE);

        boolean rest = mode == Mode.REST;
        ring.setRest(rest);
        ring.setArcAlpha(mode == Mode.PAUSED ? PAUSED_ARC_ALPHA : 1f);

        // Hearth glow: rgba(229,169,60,.18) → .06 at 50 % → 0 at 70 % of a 288/300 box.
        float glowR = size / 2f;
        if (rest) {
            glowPaint.setShader(null);
        } else {
            float scale = mode == Mode.PAUSED ? PAUSED_GLOW_SCALE : 1f;
            int start = scaleAlpha(glowStart, scale);
            int mid = scaleAlpha(glowStart, scale / 3f);
            glowPaint.setShader(new RadialGradient(cx, cy, glowR,
                    new int[]{start, mid, ColorUtils.setAlphaComponent(glowStart, 0)},
                    new float[]{0f, 0.5f, 0.7f}, Shader.TileMode.CLAMP));
        }
        // Inner sage wash (bg-secondary/10 blur-2xl, 240/300 box); the only glow while resting.
        sageGlowPaint.setShader(new RadialGradient(cx, cy, size * 0.4f,
                new int[]{sageGlow, ColorUtils.setAlphaComponent(sageGlow, 0)},
                null, Shader.TileMode.CLAMP));
    }

    @ColorInt
    private static int scaleAlpha(@ColorInt int color, float scale) {
        return ColorUtils.setAlphaComponent(color, Math.round(android.graphics.Color.alpha(color) * scale));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float size = Math.min(getWidth(), getHeight());
        float alpha = BREATH_MIN_ALPHA + (1f - BREATH_MIN_ALPHA) * breathLevel;
        float scale = BREATH_MIN_SCALE + (1f - BREATH_MIN_SCALE) * breathLevel;
        int paintAlpha = Math.round(255 * alpha);
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        if (glowPaint.getShader() != null) {
            glowPaint.setAlpha(paintAlpha);
            canvas.drawCircle(cx, cy, size / 2f, glowPaint);
        }
        sageGlowPaint.setAlpha(paintAlpha);
        canvas.drawCircle(cx, cy, size * 0.4f, sageGlowPaint);
        canvas.restore();
        ring.draw(canvas, remaining);
    }
}
