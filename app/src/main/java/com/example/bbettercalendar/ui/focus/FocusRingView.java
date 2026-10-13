package com.example.bbettercalendar.ui.focus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.example.bbettercalendar.R;

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
 * The html gradient is a TL→BR linear one on a -90° rotated svg, i.e. bottom-left → top-right on
 * screen; the glow is a radial gradient (no blur filter on a View canvas, the stops do the fade).
 * {@code CircularProgressIndicator} cannot paint a gradient arc, hence this view.
 */
public class FocusRingView extends View {

    public enum Mode { FOCUS, REST, PAUSED }

    // Proportions of the html: 270px ring box, r = 118, track 6, arc 7. The glow (w-72, 288px)
    // spills past the svg box in the html; here it is clipped to the view, so it fills the box.
    private static final float RING_RADIUS_OF_SIZE = 118f / 270f;
    private static final float PAUSED_ARC_ALPHA = 0.45f;
    private static final float PAUSED_GLOW_SCALE = 1f / 3f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sageGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    @ColorInt private final int amber;
    @ColorInt private final int sage;
    @ColorInt private final int sageBright;
    @ColorInt private final int glowStart;
    @ColorInt private final int sageGlow;

    private Mode mode = Mode.FOCUS;
    private float remaining = 1f;

    public FocusRingView(@NonNull Context context) {
        this(context, null);
    }

    public FocusRingView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        amber = ContextCompat.getColor(context, R.color.bb_sanct_primary);
        sage = ContextCompat.getColor(context, R.color.bb_sanct_sage);
        sageBright = ContextCompat.getColor(context, R.color.bb_sanct_sage_bright);
        glowStart = ContextCompat.getColor(context, R.color.bb_sanct_glow_start);
        sageGlow = ContextCompat.getColor(context, R.color.bb_sanct_glow_sage);

        float density = getResources().getDisplayMetrics().density;
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(6f * density);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.bb_sanct_track));
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(7f * density);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
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
        float r = size * RING_RADIUS_OF_SIZE;
        arcBounds.set(cx - r, cy - r, cx + r, cy + r);

        boolean rest = mode == Mode.REST;
        int[] colors = rest
                ? new int[]{sage, sageBright}
                : new int[]{amber, sageBright, sage};
        float[] stops = rest ? null : new float[]{0f, 0.65f, 1f};
        arcPaint.setShader(new LinearGradient(arcBounds.left, arcBounds.bottom,
                arcBounds.right, arcBounds.top, colors, stops, Shader.TileMode.CLAMP));
        arcPaint.setAlpha(mode == Mode.PAUSED ? Math.round(255 * PAUSED_ARC_ALPHA) : 255);

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
        if (glowPaint.getShader() != null) {
            canvas.drawCircle(cx, cy, size / 2f, glowPaint);
        }
        canvas.drawCircle(cx, cy, size * 0.4f, sageGlowPaint);
        canvas.drawCircle(cx, cy, arcBounds.width() / 2f, trackPaint);
        if (remaining > 0f) {
            canvas.drawArc(arcBounds, -90f, 360f * remaining, false, arcPaint);
        }
    }
}
