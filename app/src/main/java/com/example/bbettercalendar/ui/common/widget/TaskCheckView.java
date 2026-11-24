package com.example.bbettercalendar.ui.common.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.Checkable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * Square check of a task (rediseno-proyectos u3, D-P03): a 17dp box ({@code app:checkSmall} = 14dp, for
 * subtasks) inside a touch target of at least 48dp. Unchecked it shows only a border whose colour is the
 * task priority (3 = bb_danger, 2 = bb_secondary, 0 and 1 = bb_checkbox_border); checked it is filled
 * sage with the app sprout inside. Checking pops the box (scale with overshoot, through
 * {@link Motion}); with the system animations off it just changes.
 *
 * <p>{@link #toggle()} (a tap) notifies the {@link OnCheckedChangeListener}; {@link #setChecked(boolean)}
 * does not, so a RecyclerView can bind a row without firing it. The content description is
 * "Complete: title" / "Uncheck: title" (see {@link #setTaskTitle}).
 */
public class TaskCheckView extends View implements Checkable {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(@NonNull TaskCheckView view, boolean isChecked);
    }

    /** Priority colours (spec D-P03). */
    public static final int PRIORITY_HIGH = 3;
    public static final int PRIORITY_MEDIUM = 2;

    private static final float SPROUT_OF_BOX = 0.9f;
    private static final float POP_FROM = 0.78f;

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF boxRect = new RectF();
    private final boolean small;
    private final float boxSize;
    private final float radius;
    private final int touch;
    @ColorInt private final int fill;
    @ColorInt private final int borderDefault;
    @ColorInt private final int borderMedium;
    @ColorInt private final int borderHigh;
    @Nullable private final Drawable sprout;

    private boolean checked;
    private int priority;
    @Nullable private String taskTitle;
    @Nullable private OnCheckedChangeListener listener;
    @Nullable private ValueAnimator pop;
    private float scale = 1f;

    public TaskCheckView(@NonNull Context context) {
        this(context, null);
    }

    public TaskCheckView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskCheckView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        boolean isSmall = false;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TaskCheckView, defStyleAttr, 0);
        try {
            isSmall = a.getBoolean(R.styleable.TaskCheckView_checkSmall, false);
        } finally {
            a.recycle();
        }
        small = isSmall;
        boxSize = getResources().getDimension(small
                ? R.dimen.widget_task_check_box_small : R.dimen.widget_task_check_box);
        radius = getResources().getDimension(R.dimen.widget_task_check_radius);
        touch = getResources().getDimensionPixelSize(R.dimen.widget_task_check_touch);
        fill = ContextCompat.getColor(context, R.color.bb_primary);
        borderDefault = ContextCompat.getColor(context, R.color.bb_checkbox_border);
        borderMedium = ContextCompat.getColor(context, R.color.bb_secondary);
        borderHigh = ContextCompat.getColor(context, R.color.bb_danger);
        Drawable d = ContextCompat.getDrawable(context, R.drawable.ic_sprout_check);
        sprout = d == null ? null : d.mutate();
        boxPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(getResources().getDimension(R.dimen.widget_task_check_stroke));
        setClickable(true);
        setFocusable(true);
        setMinimumWidth(touch);
        setMinimumHeight(touch);
        updateDescription();
    }

    // ------------------------------------------------------------------ API

    /** Priority of the task: 3 = danger border, 2 = secondary border, anything else = the neutral border. */
    public void setPriority(int priority) {
        if (this.priority != priority) {
            this.priority = priority;
            invalidate();
        }
    }

    public int getPriority() {
        return priority;
    }

    /** Border colour for a priority (also used by the JVM-free callers that want the same mapping). */
    @ColorInt
    public int borderColorFor(int priority) {
        if (priority >= PRIORITY_HIGH) {
            return borderHigh;
        }
        return priority == PRIORITY_MEDIUM ? borderMedium : borderDefault;
    }

    /** Title used by the content description ("Complete: title"). Null = a generic description. */
    public void setTaskTitle(@Nullable String title) {
        taskTitle = title;
        updateDescription();
    }

    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    public boolean isSmall() {
        return small;
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    /** Sets the state without animation and without calling the listener (use it when binding a row). */
    @Override
    public void setChecked(boolean checked) {
        if (this.checked == checked) {
            return;
        }
        cancelPop();
        this.checked = checked;
        updateDescription();
        invalidate();
    }

    /** What a tap does: flips the state, plays the pop when it becomes checked and notifies the listener. */
    @Override
    public void toggle() {
        checked = !checked;
        updateDescription();
        if (checked) {
            playPop();
        } else {
            cancelPop();
        }
        invalidate();
        if (listener != null) {
            listener.onCheckedChanged(this, checked);
        }
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    // ------------------------------------------------------------------ look

    private void updateDescription() {
        Context c = getContext();
        String text;
        if (taskTitle == null || taskTitle.isEmpty()) {
            text = c.getString(checked ? R.string.widget_task_check_uncheck_generic
                    : R.string.widget_task_check_complete_generic);
        } else {
            text = c.getString(checked ? R.string.widget_task_check_uncheck
                    : R.string.widget_task_check_complete, taskTitle);
        }
        setContentDescription(text);
    }

    private void playPop() {
        cancelPop();
        Context ctx = getContext();
        int duration = Motion.duration(ctx, R.integer.bb_motion_icon_morph);
        if (duration <= 0) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(POP_FROM, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(Motion.interpolator(ctx, R.interpolator.bb_overshoot));
        animator.addUpdateListener(a -> {
            scale = (float) a.getAnimatedValue();
            invalidate();
        });
        scale = POP_FROM;
        pop = animator;
        animator.start();
    }

    private void cancelPop() {
        ValueAnimator running = pop;
        pop = null;
        if (running != null) {
            running.cancel();
        }
        scale = 1f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = Math.max(touch, getSuggestedMinimumWidth());
        int h = Math.max(touch, getSuggestedMinimumHeight());
        setMeasuredDimension(resolveSize(w + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(h + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float cx = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
        float cy = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
        float half = boxSize / 2f * scale;
        boxRect.set(cx - half, cy - half, cx + half, cy + half);
        if (checked) {
            boxPaint.setColor(fill);
            canvas.drawRoundRect(boxRect, radius, radius, boxPaint);
            if (sprout != null) {
                int s = Math.round(boxSize * SPROUT_OF_BOX * scale / 2f);
                sprout.setBounds(Math.round(cx) - s, Math.round(cy) - s, Math.round(cx) + s, Math.round(cy) + s);
                sprout.draw(canvas);
            }
        } else {
            // Stroke inside the box so the drawn size stays 17dp (or 14dp).
            float inset = borderPaint.getStrokeWidth() / 2f;
            boxRect.inset(inset, inset);
            borderPaint.setColor(borderColorFor(priority));
            canvas.drawRoundRect(boxRect, radius, radius, borderPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelPop();
        super.onDetachedFromWindow();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return CheckBox.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(checked);
    }
}
