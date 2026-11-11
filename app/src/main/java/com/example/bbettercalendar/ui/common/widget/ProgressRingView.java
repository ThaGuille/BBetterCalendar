package com.example.bbettercalendar.ui.common.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FontHelper;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * Progress ring with the percentage centred (mejora-visual u3): a round-capped arc from 12 o'clock over a
 * track. Stroke ({@code app:ringStroke}) and colour ({@code app:ringColor}) are configurable; the first
 * value and every later {@link #setProgress(int, boolean)} sweep in (450 ms, {@code bb_decelerate}), with the
 * number counting along. With animations off the final state is drawn at once. Used by Projects and the app
 * block screen.
 */
public class ProgressRingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float stroke;
    private boolean showText = true;
    private int target;
    private float shown;
    private boolean entered;
    @Nullable private ValueAnimator animator;

    public ProgressRingView(@NonNull Context context) {
        this(context, null);
    }

    public ProgressRingView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgressRingView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        stroke = getResources().getDimension(R.dimen.progress_ring_stroke);
        int color = ContextCompat.getColor(context, R.color.bb_primary);
        int track = ContextCompat.getColor(context, R.color.bb_surface_raised);
        int initial = 0;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ProgressRingView, defStyleAttr, 0);
        try {
            stroke = a.getDimension(R.styleable.ProgressRingView_ringStroke, stroke);
            color = a.getColor(R.styleable.ProgressRingView_ringColor, color);
            track = a.getColor(R.styleable.ProgressRingView_ringTrackColor, track);
            showText = a.getBoolean(R.styleable.ProgressRingView_ringShowText, true);
            initial = a.getInt(R.styleable.ProgressRingView_ringProgress, 0);
        } finally {
            a.recycle();
        }
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(track);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(color);
        applyStroke();

        // Text size and weight come from the type scale, not from literals.
        TypedArray t = context.obtainStyledAttributes(R.style.TextAppearance_BBetter_Label,
                new int[]{android.R.attr.textSize});
        float size = t.getDimension(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14,
                getResources().getDisplayMetrics()));
        t.recycle();
        textPaint.setTextSize(size);
        textPaint.setTypeface(FontHelper.appTypeface(context, 700));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFontFeatureSettings("tnum");
        textPaint.setColor(ContextCompat.getColor(context, R.color.bb_on_surface));

        target = clamp(initial);
        shown = 0f;
        updateDescription();
    }

    private void applyStroke() {
        trackPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeWidth(stroke);
    }

    public void setStrokeWidthPx(float px) {
        stroke = px;
        applyStroke();
        invalidate();
    }

    public void setRingColor(@ColorInt int color) {
        arcPaint.setColor(color);
        invalidate();
    }

    public void setTrackColor(@ColorInt int color) {
        trackPaint.setColor(color);
        invalidate();
    }

    public void setShowText(boolean show) {
        showText = show;
        invalidate();
    }

    public int getProgress() {
        return target;
    }

    /** Sets 0..100 and sweeps to it (no sweep when animations are off or the view is not shown yet). */
    public void setProgress(int percent, boolean animate) {
        target = clamp(percent);
        updateDescription();
        cancelAnimation();
        if (animate && isAttachedToWindow() && Motion.enabled(getContext())) {
            sweepTo(target);
        } else {
            shown = target;
            entered = true;
            invalidate();
        }
    }

    private void sweepTo(int to) {
        animator = ValueAnimator.ofFloat(shown, to);
        animator.setDuration(Motion.duration(getContext(), R.integer.bb_motion_chart));
        animator.setInterpolator(Motion.interpolator(getContext(), R.interpolator.bb_decelerate));
        animator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
        entered = true;
    }

    private void cancelAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private void updateDescription() {
        setContentDescription(getContext().getString(R.string.widget_ring_percent, target));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!entered) {
            if (Motion.enabled(getContext())) {
                shown = 0f;
                sweepTo(target);
            } else {
                shown = target;
                entered = true;
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimation();
        shown = target;
        // u15: sin volver a entrar al reengancharse. Las transiciones de pantalla pasan la pantalla que sale
        // por el overlay del contenedor (se desengancha y se vuelve a enganchar): el anillo barría otra vez
        // desde 0 mientras se iba. Una vista nueva (pantalla recreada) sigue entrando con su barrido.
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float half = stroke / 2f;
        oval.set(getPaddingLeft() + half, getPaddingTop() + half,
                getWidth() - getPaddingRight() - half, getHeight() - getPaddingBottom() - half);
        canvas.drawArc(oval, 0, 360, false, trackPaint);
        if (shown > 0f) {
            canvas.drawArc(oval, -90f, 360f * shown / 100f, false, arcPaint);
        }
        if (showText) {
            String text = getContext().getString(R.string.widget_ring_percent, Math.round(shown));
            // Large text: shrink to fit inside the ring instead of touching the stroke.
            float room = oval.width() - stroke * 2f;
            float size = textPaint.getTextSize();
            float wide = textPaint.measureText(text);
            if (room > 0f && wide > room) {
                textPaint.setTextSize(size * room / wide);
            }
            float y = getHeight() / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(text, getWidth() / 2f, y, textPaint);
            textPaint.setTextSize(size);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int def = (int) (getResources().getDisplayMetrics().density * 96);
        setMeasuredDimension(resolveSize(def, widthMeasureSpec), resolveSize(def, heightMeasureSpec));
    }
}
