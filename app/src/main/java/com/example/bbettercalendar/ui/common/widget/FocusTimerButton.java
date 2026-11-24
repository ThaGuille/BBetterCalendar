package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * Stopwatch button that focuses a task (rediseno-proyectos u3, D-P04): the {@code ic_timer_24}
 * stopwatch in the Focus amber ({@code bb_sanct_primary}) inside a translucent amber circle, with an
 * amber ring that shows the minutes already attributed to the task over its goal. While the task's
 * session runs ({@link #setRunning}) the ring follows the live progress and the icon is a pause.
 *
 * <p>Two sizes ({@code app:timerSize}): {@code row} (34dp circle, task rows) and {@code big} (42dp circle,
 * the summary card), both in a view of at least 48dp so the touch target is 48dp. The ring is the shared
 * {@link RingPainter} (focus palette). Content description and tooltip: "Focus" / "Open Focus" when running.
 * Opening Focus belongs to the screen (it sets the click listener).
 */
public class FocusTimerButton extends View {

    public static final int SIZE_ROW = 0;
    public static final int SIZE_BIG = 1;

    private static final float ICON_OF_CIRCLE = 0.5f;
    private static final float FILL_ALPHA = 0.16f;
    private static final float TRACK_ALPHA = 0.28f;

    private final RingPainter ring;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private final int touch;
    private final float circle;
    private final float stroke;
    @ColorInt private final int amber;
    @Nullable private final Drawable timerIcon;
    @Nullable private final Drawable pauseIcon;

    private float progress;
    private boolean running;
    private float cx;
    private float cy;

    public FocusTimerButton(@NonNull Context context) {
        this(context, null);
    }

    public FocusTimerButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FocusTimerButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        int s = SIZE_ROW;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FocusTimerButton, defStyleAttr, 0);
        try {
            s = a.getInt(R.styleable.FocusTimerButton_timerSize, SIZE_ROW);
        } finally {
            a.recycle();
        }
        size = s;
        touch = getResources().getDimensionPixelSize(R.dimen.widget_focus_timer_touch);
        circle = getResources().getDimension(size == SIZE_BIG
                ? R.dimen.widget_focus_timer_big : R.dimen.widget_focus_timer_row);
        stroke = getResources().getDimension(size == SIZE_BIG
                ? R.dimen.widget_focus_timer_stroke_big : R.dimen.widget_focus_timer_stroke_row);
        amber = ContextCompat.getColor(context, R.color.bb_sanct_primary);
        ring = new RingPainter(context);
        ring.setTrackColor(ColorUtils.setAlphaComponent(amber, Math.round(255 * TRACK_ALPHA)));
        ring.setStrokeWidths(stroke, stroke);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(ColorUtils.setAlphaComponent(amber, Math.round(255 * FILL_ALPHA)));
        timerIcon = tinted(R.drawable.ic_timer_24);
        pauseIcon = tinted(R.drawable.ic_pause_v2_filled_24);
        setClickable(true);
        setFocusable(true);
        setMinimumWidth(touch);
        setMinimumHeight(touch);
        Motion.pressable(this);
        updateLabels();
    }

    @Nullable
    private Drawable tinted(int res) {
        Drawable d = ContextCompat.getDrawable(getContext(), res);
        if (d == null) {
            return null;
        }
        Drawable wrapped = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(wrapped, amber);
        return wrapped;
    }

    // ------------------------------------------------------------------ API

    /** Ring fraction, 0..1 (minutes attributed over the goal, or the live progress while running). */
    public void setProgress(float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        if (f != progress) {
            progress = f;
            invalidate();
        }
    }

    public float getProgress() {
        return progress;
    }

    /** Ring from minutes: attributed over goal; no goal = empty ring. */
    public void setGoal(int attributedMinutes, int goalMinutes) {
        setProgress(goalMinutes > 0 ? attributedMinutes / (float) goalMinutes : 0f);
    }

    /** Running: pause icon, live ring, "Open Focus" label. The ring sweeps in when it starts. */
    public void setRunning(boolean running) {
        if (this.running == running) {
            return;
        }
        this.running = running;
        updateLabels();
        if (running && isAttachedToWindow()) {
            ring.startSweep(this);
        }
        invalidate();
    }

    public boolean isRunning() {
        return running;
    }

    public int getTimerSize() {
        return size;
    }

    /** Diameter in px of the drawn circle (34dp row, 42dp big). */
    public float getCircleDiameter() {
        return circle;
    }

    // ------------------------------------------------------------------ look

    private void updateLabels() {
        String label = getContext().getString(running
                ? R.string.widget_focus_timer_open : R.string.widget_focus_timer_focus);
        setContentDescription(label);
        TooltipCompat.setTooltipText(this, label);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = Math.max(touch, getSuggestedMinimumWidth());
        int h = Math.max(touch, getSuggestedMinimumHeight());
        setMeasuredDimension(resolveSize(w + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(h + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = getPaddingLeft() + (w - getPaddingLeft() - getPaddingRight()) / 2f;
        cy = getPaddingTop() + (h - getPaddingTop() - getPaddingBottom()) / 2f;
        ring.setBounds(cx, cy, circle / 2f - stroke / 2f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(cx, cy, circle / 2f, fillPaint);
        ring.draw(canvas, progress);
        Drawable icon = running ? pauseIcon : timerIcon;
        if (icon != null) {
            int half = Math.round(circle * ICON_OF_CIRCLE / 2f);
            icon.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
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
