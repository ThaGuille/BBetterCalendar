package com.example.bbettercalendar.ui.common.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * Loading placeholder (mejora-visual u3): a rounded block ({@code bb_skeleton_base}) with a soft shine
 * ({@code bb_skeleton_shine}) that sweeps across it every 1.1 s, linear. {@link #stopAndHide()} ends the
 * animation and hides the view when the data arrives; with the system animations off the block is drawn
 * static (no shine, no animator). Height comes from the layout; {@link #setWidthFraction} narrows it.
 */
public class SkeletonView extends View {

    /** One sweep, linear (mockup 04, "Carga"). */
    public static final long SHINE_MS = 1100L;
    private static final float BAND = 0.6f;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shineMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final int shineColor;
    private float radius;
    private float widthFraction = 1f;
    @Nullable private ValueAnimator sweep;
    private float sweepPosition = -1f;
    private boolean active = true;

    public SkeletonView(@NonNull Context context) {
        this(context, null);
    }

    public SkeletonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SkeletonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        radius = getResources().getDimension(R.dimen.skeleton_radius);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SkeletonView, defStyleAttr, 0);
        try {
            radius = a.getDimension(R.styleable.SkeletonView_skeletonRadius, radius);
        } finally {
            a.recycle();
        }
        basePaint.setColor(ContextCompat.getColor(context, R.color.bb_skeleton_base));
        shineColor = ContextCompat.getColor(context, R.color.bb_skeleton_shine);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Fraction (0..1] of the offered width this block takes (rows of different length). */
    public void setWidthFraction(float fraction) {
        widthFraction = Math.max(0.05f, Math.min(1f, fraction));
        requestLayout();
    }

    /** Shows the block again and restarts the shine (if animations are on). */
    public void start() {
        active = true;
        setVisibility(VISIBLE);
        startSweep();
    }

    /** Data arrived: stops the shine and hides the view (GONE). */
    public void stopAndHide() {
        active = false;
        stopSweep();
        setVisibility(GONE);
    }

    public boolean isShining() {
        return sweep != null && sweep.isRunning();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (widthFraction < 1f && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            int w = (int) (MeasureSpec.getSize(widthMeasureSpec) * widthFraction);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float band = Math.max(1f, w * BAND);
        int clear = Color.argb(0, Color.red(shineColor), Color.green(shineColor), Color.blue(shineColor));
        shinePaint.setShader(new LinearGradient(0, 0, band, 0,
                new int[]{clear, shineColor, clear}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        float r = Math.min(radius, Math.min(rect.width(), rect.height()) / 2f);
        canvas.drawRoundRect(rect, r, r, basePaint);
        if (sweep != null && sweep.isRunning() && shinePaint.getShader() != null) {
            float band = getWidth() * BAND;
            float x = -band + (getWidth() + band) * sweepPosition;
            shineMatrix.setTranslate(x, 0);
            shinePaint.getShader().setLocalMatrix(shineMatrix);
            canvas.drawRoundRect(rect, r, r, shinePaint);
        }
    }

    private void startSweep() {
        if (sweep != null || !active || getVisibility() != VISIBLE || !isAttachedToWindow()
                || !Motion.enabled(getContext())) {
            return;
        }
        sweep = ValueAnimator.ofFloat(0f, 1f);
        sweep.setDuration(SHINE_MS);
        sweep.setInterpolator(new LinearInterpolator());
        sweep.setRepeatCount(ValueAnimator.INFINITE);
        sweep.addUpdateListener(a -> {
            sweepPosition = (float) a.getAnimatedValue();
            invalidate();
        });
        sweep.start();
    }

    private void stopSweep() {
        if (sweep != null) {
            sweep.cancel();
            sweep = null;
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startSweep();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSweep();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startSweep();
        } else {
            stopSweep();
        }
    }
}
