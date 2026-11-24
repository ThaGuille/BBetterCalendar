package com.example.bbettercalendar.ui.projects.list;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FontHelper;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * The small subtask ring of a task row: a thin ring with "n/m" inside (done over total). Tapping it expands
 * the subtasks under the row. The ring is drawn small, the touch area is 48dp.
 */
public class SubtaskCountView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float touch;
    private final float ring;
    private int done;
    private int total;

    public SubtaskCountView(@NonNull Context context) {
        this(context, null);
    }

    public SubtaskCountView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touch = getResources().getDimension(R.dimen.touch_target_min);
        ring = getResources().getDimension(R.dimen.projects_main_subtask_ring);
        float stroke = getResources().getDimension(R.dimen.projects_main_subtask_ring_stroke);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.bb_surface_raised));
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(ContextCompat.getColor(context, R.color.bb_primary));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(ContextCompat.getColor(context, R.color.bb_on_surface_muted));
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9,
                getResources().getDisplayMetrics()));
        textPaint.setTypeface(FontHelper.appTypeface(context, 600));
        setClickable(true);
        setFocusable(true);
        Motion.pressable(this);
    }

    /** Done over total subtasks; the content description says it in words. */
    public void setCounts(int done, int total) {
        this.done = done;
        this.total = total;
        setContentDescription(getContext().getString(R.string.projects_main_subtasks_desc, done, total));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(touch);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float half = ring / 2f - arcPaint.getStrokeWidth() / 2f;
        oval.set(cx - half, cy - half, cx + half, cy + half);
        canvas.drawArc(oval, 0f, 360f, false, trackPaint);
        if (total > 0 && done > 0) {
            canvas.drawArc(oval, -90f, 360f * done / total, false, arcPaint);
        }
        float baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(done + "/" + total, cx, baseline, textPaint);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return android.widget.Button.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClickable(true);
    }
}
