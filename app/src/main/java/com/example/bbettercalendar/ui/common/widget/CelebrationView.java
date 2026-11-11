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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.feedback.HapticFeedback;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * mejora-visual u13: the celebration mark. A ring that completes (900 ms, {@code bb_motion_celebrate})
 * with the sprout inside and, once it closes, a burst of ~14 amber / sage particles (600 ms).
 * {@link #play()} runs it once per view ("only once per entry": a second call does nothing; the
 * fragments call it only on a fresh entry and {@link #showComplete()} after a rotation). With the
 * system animations off the ring is drawn whole and there are no particles. The haptic is
 * {@link HapticFeedback#confirm(View)} when the ring closes.
 */
public class CelebrationView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Drawable sprout;
    private final int amber;
    private final int sage;
    private final int particleCount;
    private final float stroke;
    private final float particleMin;
    private final float particleMax;

    /** 0..1 of the ring arc. */
    private float ring;
    /** 0..1 of the burst; 0 = not started or finished (nothing drawn). */
    private float burst;
    private boolean played;
    @Nullable private ValueAnimator ringAnimator;
    @Nullable private ValueAnimator burstAnimator;

    public CelebrationView(@NonNull Context context) {
        this(context, null);
    }

    public CelebrationView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        amber = ContextCompat.getColor(context, R.color.bb_sanct_primary);
        sage = ContextCompat.getColor(context, R.color.bb_sanct_sage_bright);
        stroke = getResources().getDimension(R.dimen.celebration_ring_stroke);
        particleMin = getResources().getDimension(R.dimen.celebration_particle_min);
        particleMax = getResources().getDimension(R.dimen.celebration_particle_max);
        particleCount = getResources().getInteger(R.integer.cycle_celebrate_particles);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.bb_sanct_card));
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        particlePaint.setStyle(Paint.Style.FILL);
        Drawable d = ContextCompat.getDrawable(context, R.drawable.ic_celebration_sprout);
        sprout = d == null ? null : d.mutate();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Runs the ring and the burst; only the first call of this view does anything. */
    public void play() {
        if (played) {
            return;
        }
        played = true;
        cancelAnimators();
        if (!Motion.enabled(getContext())) {
            ring = 1f;
            burst = 0f;
            invalidate();
            return;
        }
        ring = 0f;
        ringAnimator = ValueAnimator.ofFloat(0f, 1f);
        ringAnimator.setDuration(getResources().getInteger(R.integer.bb_motion_celebrate));
        ringAnimator.setInterpolator(Motion.interpolator(getContext(), R.interpolator.bb_decelerate));
        ringAnimator.addUpdateListener(a -> {
            ring = (float) a.getAnimatedValue();
            invalidate();
        });
        ringAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    ring = 1f;
                    HapticFeedback.confirm(CelebrationView.this);
                    startBurst();
                }
            }
        });
        ringAnimator.start();
    }

    /** Whole ring, no particles, no haptic (rotation, animations off, or a view that already played). */
    public void showComplete() {
        played = true;
        cancelAnimators();
        ring = 1f;
        burst = 0f;
        invalidate();
    }

    private void startBurst() {
        burstAnimator = ValueAnimator.ofFloat(0.001f, 1f);
        burstAnimator.setDuration(getResources().getInteger(R.integer.cycle_celebrate_burst));
        burstAnimator.setInterpolator(Motion.interpolator(getContext(), R.interpolator.bb_decelerate));
        burstAnimator.addUpdateListener(a -> {
            burst = (float) a.getAnimatedValue();
            invalidate();
        });
        burstAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                burst = 0f;
                invalidate();
            }
        });
        burstAnimator.start();
    }

    private void cancelAnimators() {
        if (ringAnimator != null) {
            ringAnimator.cancel();
            ringAnimator = null;
        }
        if (burstAnimator != null) {
            burstAnimator.cancel();
            burstAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimators();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float half = Math.min(w, h) / 2f;
        float ringRadius = half * 0.72f;
        oval.set(w / 2f - ringRadius, h / 2f - ringRadius, w / 2f + ringRadius, h / 2f + ringRadius);
        arcPaint.setShader(new LinearGradient(oval.left, oval.top, oval.right, oval.bottom,
                sage, amber, Shader.TileMode.CLAMP));
        if (sprout != null) {
            int box = Math.round(ringRadius * 1.45f);
            int left = Math.round(w / 2f - box / 2f);
            int top = Math.round(h / 2f - box / 2f);
            sprout.setBounds(left, top, left + box, top + box);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        canvas.drawOval(oval, trackPaint);
        if (ring > 0f) {
            canvas.drawArc(oval, -90f, 360f * ring, false, arcPaint);
        }
        if (sprout != null) {
            int save = canvas.save();
            float scale = 0.6f + 0.4f * ring;
            canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
            sprout.setAlpha(Math.round(255 * Math.min(1f, ring * 1.5f)));
            sprout.draw(canvas);
            canvas.restoreToCount(save);
        }
        if (burst > 0f) {
            drawBurst(canvas);
        }
    }

    private void drawBurst(Canvas canvas) {
        float half = Math.min(getWidth(), getHeight()) / 2f;
        float inner = oval.width() / 2f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        for (int i = 0; i < particleCount; i++) {
            double angle = Math.toRadians(360.0 * i / particleCount + (i % 2 == 0 ? 0 : 9));
            // alternate two travel distances so the burst is not a perfect circle
            float reach = inner + (half - inner - particleMax) * (i % 3 == 0 ? 1f : 0.6f);
            float r = inner + (reach - inner) * burst;
            float size = particleMin + (particleMax - particleMin) * ((i * 7 % 5) / 4f);
            particlePaint.setColor(i % 2 == 0 ? amber : sage);
            particlePaint.setAlpha(Math.round(255 * (1f - burst)));
            canvas.drawCircle(cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r,
                    size, particlePaint);
        }
    }
}
