package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.stats.ConsistencyCalculator;

import java.util.Collections;
import java.util.List;

/**
 * One-row "barcode" of a period's focus days (plan racha-semanal-y-constancia): one rounded cell per
 * day (week / month) or per week (year), drawn from {@link ConsistencyCalculator.Cell}s. Kept to a
 * single short row on purpose so the Progress tiles card barely grows.
 *
 * <pre>
 *   ▮▮▯▮▮▯▯▮▮▮▯▮▢┊┊┊┊┊   ← done (honey, intensity = active/possible) · rest (raised) ·
 *                           today pending (sage ring) · future (hairline)
 * </pre>
 *
 * Same honey / sage language as {@link StreakWeekView}'s dots. Pure drawing, no text: the screen
 * puts the "18/30 days" summary next to it and sets the content description.
 */
public class ConsistencyStripView extends View {

    private static final float MIN_ACTIVE_ALPHA = 0.35f;

    private final Paint restPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint donePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int doneColor;
    private final float gap;
    private final float maxRadius;
    private final float stroke;

    @NonNull private List<ConsistencyCalculator.Cell> cells = Collections.emptyList();

    public ConsistencyStripView(@NonNull Context context) {
        this(context, null);
    }

    public ConsistencyStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ConsistencyStripView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        gap = getResources().getDimension(R.dimen.widget_consistency_cell_gap);
        maxRadius = getResources().getDimension(R.dimen.widget_consistency_cell_radius);
        stroke = getResources().getDimension(R.dimen.widget_hairline);
        doneColor = ContextCompat.getColor(context, R.color.bb_accent_reward);

        restPaint.setStyle(Paint.Style.FILL);
        restPaint.setColor(ContextCompat.getColor(context, R.color.bb_surface_raised));
        donePaint.setStyle(Paint.Style.FILL);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(stroke);
        outlinePaint.setColor(ContextCompat.getColor(context, R.color.bb_outline));
        todayPaint.setStyle(Paint.Style.STROKE);
        todayPaint.setStrokeWidth(stroke * 1.5f);
        todayPaint.setColor(ContextCompat.getColor(context, R.color.bb_primary));
    }

    /** Cells in order (oldest first). Null = empty strip. */
    public void setCells(@Nullable List<ConsistencyCalculator.Cell> cells) {
        this.cells = cells != null ? cells : Collections.<ConsistencyCalculator.Cell>emptyList();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultHeight = getResources().getDimensionPixelSize(R.dimen.widget_consistency_height);
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(defaultHeight + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int n = cells.size();
        if (n == 0) {
            return;
        }
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        // Many cells (a year by weeks): shrink the gap so cells stay readable.
        float g = Math.min(gap, width / n * 0.3f);
        float cellW = (width - g * (n - 1)) / n;
        if (cellW <= 0f) {
            return;
        }
        float radius = Math.min(maxRadius, Math.min(cellW, height) / 2f);
        float inset = stroke / 2f;

        for (int i = 0; i < n; i++) {
            ConsistencyCalculator.Cell c = cells.get(i);
            float x = left + i * (cellW + g);
            rect.set(x, top, x + cellW, top + height);
            if (c.future) {
                rect.inset(inset, inset);
                canvas.drawRoundRect(rect, radius, radius, outlinePaint);
                continue;
            }
            canvas.drawRoundRect(rect, radius, radius, restPaint);
            if (c.active > 0) {
                float ratio = c.ratio();
                float alpha = MIN_ACTIVE_ALPHA + (1f - MIN_ACTIVE_ALPHA) * ratio;
                donePaint.setColor(doneColor);
                donePaint.setAlpha(Math.round(255 * alpha));
                canvas.drawRoundRect(rect, radius, radius, donePaint);
            }
            if (c.pendingToday) {
                rect.inset(stroke * 0.75f, stroke * 0.75f);
                canvas.drawRoundRect(rect, radius, radius, todayPaint);
            }
        }
    }
}
