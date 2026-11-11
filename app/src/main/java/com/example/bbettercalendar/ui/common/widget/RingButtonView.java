package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * Play button with a live ring (mejora-visual u6, D1 «anillo vivo», mockups 01 option A and 02): a disc
 * inside the same amber -> sage ring as the Focus screen, painted by the shared {@link RingPainter}.
 *
 * <pre>
 *   IDLE     full ring, glossy sage disc, play
 *   RUNNING  ring = time left (empties), raised disc, pause
 *   PAUSED   ring dimmed, play (resume)
 *   REST     sage-only ring, raised disc, pause
 * </pre>
 *
 * Variants ({@code app:ringVariant}): {@code primary} (Home, 84dp) as above; {@code focus} (Focus, 72dp)
 * keeps the glossy disc except during a rest; {@code compact} (task rows, 34-40dp) always shows a raised
 * disc with play and its ring is the minutes done over the target. Play and pause morph into each other
 * (animated vector, {@code bb_motion_icon_morph}); the ring sweeps from 0 to its value when a session
 * starts ({@link RingPainter#startSweep}). With the system animations off both jump to their final look.
 * The view honours its padding (the ring fits inside it), so a 48dp touch target can hold a 36dp ring.
 * Clicks and the content description belong to the screen, as with the ImageButtons it replaces.
 */
public class RingButtonView extends View {

    public static final int VARIANT_PRIMARY = 0;
    public static final int VARIANT_FOCUS = 1;
    public static final int VARIANT_COMPACT = 2;

    /** Ring thickness and disc gap as a fraction of the ring box (mockup 01: 4dp ring on 84dp). */
    private static final float STROKE_OF_SIZE = 0.05f;
    private static final float STROKE_OF_SIZE_COMPACT = 0.075f;
    private static final float GAP_OF_SIZE = 0.055f;
    private static final float GAP_OF_SIZE_COMPACT = 0.07f;
    /** Glyph box relative to the disc radius. */
    private static final float ICON_OF_DISC_RADIUS = 0.95f;
    private static final float PAUSED_ARC_ALPHA = 0.45f;

    private final RingPainter ring;
    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @ColorInt private final int discRaised;
    @ColorInt private final int discLight;
    @ColorInt private final int discDeep;
    @ColorInt private final int gloss;
    @ColorInt private final int iconOnFill;
    @ColorInt private final int iconOnRaised;

    private int variant;
    @NonNull private RingState state = RingState.IDLE;
    private boolean stateSet;
    private boolean rest;
    private float remaining = 1f;

    private float cx;
    private float cy;
    private float discRadius;
    @Nullable private Shader discShader;

    @Nullable private Drawable icon;
    private boolean iconIsPause;
    @ColorInt private int iconTint;

    public RingButtonView(@NonNull Context context) {
        this(context, null);
    }

    public RingButtonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RingButtonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RingButtonView, defStyleAttr, 0);
        try {
            variant = a.getInt(R.styleable.RingButtonView_ringVariant, VARIANT_PRIMARY);
        } finally {
            a.recycle();
        }
        ring = new RingPainter(context);
        ring.setTrackColor(ContextCompat.getColor(context, R.color.bb_surface_raised));
        discRaised = ContextCompat.getColor(context, R.color.bb_surface_raised);
        discLight = ContextCompat.getColor(context, R.color.bb_primary_bright);
        discDeep = ContextCompat.getColor(context, R.color.bb_primary);
        gloss = ContextCompat.getColor(context, R.color.bb_ring_disc_gloss);
        iconOnFill = ContextCompat.getColor(context, R.color.bb_on_primary);
        iconOnRaised = ContextCompat.getColor(context, R.color.bb_primary_bright);
        discPaint.setStyle(Paint.Style.FILL);
        glossPaint.setStyle(Paint.Style.FILL);
        setClickable(true);
        setFocusable(true);
        Motion.pressable(this);
        updateIcon(false);
    }

    // ------------------------------------------------------------------ API

    /** Fraction of the ring drawn, 0..1: time left of the countdown, or minutes done / target (compact). */
    public void setRemaining(float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        if (f != remaining) {
            remaining = f;
            invalidate();
        }
    }

    public float getRemaining() {
        return remaining;
    }

    /**
     * New state. The first call only paints it; a later IDLE -> RUNNING / REST also sweeps the ring in,
     * and play <-> pause morphs.
     */
    public void setState(@NonNull RingState newState) {
        RingState old = state;
        boolean wasSet = stateSet;
        stateSet = true;
        if (wasSet && old == newState) {
            return;
        }
        state = newState;
        ring.setArcAlpha(newState == RingState.PAUSED ? PAUSED_ARC_ALPHA : 1f);
        updateIcon(wasSet);
        if (wasSet && old == RingState.IDLE && newState.ticking()) {
            ring.startSweep(this);
        }
        invalidate();
    }

    @NonNull
    public RingState getState() {
        return state;
    }

    /** Rest palette for the ring (sage only). */
    public void setRest(boolean rest) {
        if (this.rest != rest) {
            this.rest = rest;
            ring.setRest(rest);
            invalidate();
        }
    }

    /** Sweeps the ring in now (a screen that opens on a session that has just started). */
    public void playStartSweep() {
        ring.startSweep(this);
    }

    public void setVariant(int variant) {
        if (this.variant != variant) {
            this.variant = variant;
            updateGeometry();
            updateIcon(false);
            invalidate();
        }
    }

    // ------------------------------------------------------------------ look

    private boolean discFilled() {
        switch (variant) {
            case VARIANT_COMPACT:
                return false;
            case VARIANT_FOCUS:
                return state != RingState.REST;
            case VARIANT_PRIMARY:
            default:
                return !state.ticking();
        }
    }

    private boolean showsPause() {
        return variant != VARIANT_COMPACT && state.ticking();
    }

    /** Swaps play / pause; morphs when {@code animate} and the system animations are on. */
    private void updateIcon(boolean animate) {
        boolean pause = showsPause();
        if (icon != null && pause == iconIsPause) {
            applyIconTint();
            return;
        }
        boolean morph = animate && icon != null && Motion.enabled(getContext());
        // Not started, an animated vector shows its first frame: pause_to_play = pause, play_to_pause = play.
        @DrawableRes int res;
        if (morph) {
            res = pause ? R.drawable.avd_play_to_pause : R.drawable.avd_pause_to_play;
        } else {
            res = pause ? R.drawable.avd_pause_to_play : R.drawable.avd_play_to_pause;
        }
        Drawable next = ContextCompat.getDrawable(getContext(), res);
        if (icon != null) {
            icon.setCallback(null);
            if (icon instanceof Animatable) {
                ((Animatable) icon).stop();
            }
        }
        icon = next == null ? null : DrawableCompat.wrap(next.mutate());
        iconIsPause = pause;
        iconTint = 0;
        if (icon != null) {
            icon.setCallback(this);
            layoutIcon();
            applyIconTint();
            if (morph && icon instanceof Animatable) {
                ((Animatable) icon).start();
            }
        }
    }

    private void applyIconTint() {
        if (icon == null) {
            return;
        }
        int tint = discFilled() ? iconOnFill : iconOnRaised;
        if (tint != iconTint) {
            iconTint = tint;
            DrawableCompat.setTint(icon, tint);
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == icon || super.verifyDrawable(who);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateGeometry();
    }

    private void updateGeometry() {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) {
            return;
        }
        float size = Math.min(w, h);
        boolean compact = variant == VARIANT_COMPACT;
        float stroke = size * (compact ? STROKE_OF_SIZE_COMPACT : STROKE_OF_SIZE);
        float gap = size * (compact ? GAP_OF_SIZE_COMPACT : GAP_OF_SIZE);
        cx = getPaddingLeft() + w / 2f;
        cy = getPaddingTop() + h / 2f;
        float ringRadius = size / 2f - stroke / 2f;
        ring.setStrokeWidths(stroke, stroke);
        ring.setBounds(cx, cy, ringRadius);
        discRadius = Math.max(0f, ringRadius - stroke / 2f - gap);
        // Glossy disc: light sage top-left -> sage bottom-right, plus a soft highlight up-left.
        discShader = new LinearGradient(cx - discRadius, cy - discRadius, cx + discRadius, cy + discRadius,
                discLight, discDeep, Shader.TileMode.CLAMP);
        glossPaint.setShader(new RadialGradient(cx - discRadius * 0.35f, cy - discRadius * 0.45f,
                Math.max(1f, discRadius * 0.9f),
                new int[]{gloss, ColorUtils.setAlphaComponent(gloss, 0)}, null, Shader.TileMode.CLAMP));
        layoutIcon();
    }

    private void layoutIcon() {
        if (icon == null || discRadius <= 0f) {
            return;
        }
        int half = Math.round(discRadius * ICON_OF_DISC_RADIUS / 2f * (variant == VARIANT_COMPACT ? 1.1f : 1f));
        icon.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        ring.draw(canvas, remaining);
        if (discRadius <= 0f) {
            return;
        }
        boolean filled = discFilled();
        if (filled) {
            discPaint.setShader(discShader);
            canvas.drawCircle(cx, cy, discRadius, discPaint);
            canvas.drawCircle(cx, cy, discRadius, glossPaint);
        } else {
            discPaint.setShader(null);
            discPaint.setColor(discRaised);
            canvas.drawCircle(cx, cy, discRadius, discPaint);
        }
        if (icon != null) {
            applyIconTint();
            icon.draw(canvas);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        ring.cancelSweep();
        super.onDetachedFromWindow();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Button.class.getName();
    }
}
