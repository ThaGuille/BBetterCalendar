package com.example.bbettercalendar.ui.common.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * One row of "before and after the limit" (mockup 21): app name on the left (up to two lines, never cut),
 * a hollow dot for the daily average before the limit, a sage dot for the average since, a line between
 * them, and the change in percent on the right (sage when it went down, {@code bb_danger} when it went up).
 * Rows of one card share {@code scaleMax} so their dots line up. The "after" dot slides in from "before".
 */
public class DumbbellRowView extends BaseChartView {

    private static final float NAME_SHARE = 0.42f;

    /** Immutable data. */
    public static final class Data {
        final String name;
        final float before;
        final float after;
        final float scaleMax;
        final String beforeName;
        final String afterName;
        final ChartFormatter formatter;
        @Nullable final Integer change;

        /**
         * @param before     daily average before the limit (minutes)
         * @param after      daily average since the limit
         * @param scaleMax   largest value of every row in the card ({@link ChartScale#max})
         * @param beforeName "7 días antes"
         * @param afterName  "Desde el límite"
         */
        public Data(@NonNull String name, float before, float after, float scaleMax, @NonNull String beforeName,
                    @NonNull String afterName, @NonNull ChartFormatter formatter) {
            this.name = name;
            this.before = before;
            this.after = after;
            this.scaleMax = Math.max(scaleMax, Math.max(before, after));
            this.beforeName = beforeName;
            this.afterName = afterName;
            this.formatter = formatter;
            this.change = ChartFormat.percentChange(before, after);
        }

        @NonNull
        String changeText() {
            return change == null ? "" : ChartFormat.signedPercent(change);
        }
    }

    @Nullable private Data data;
    private final TextPaint namePaint;
    private final Paint percentPaint;
    private final Paint linePaint;
    private final Paint beforePaint;
    private final Paint afterPaint;
    private final float dotRadius;
    private final float rowPadding;
    private final float gap;
    private final int downColor;
    private final int upColor;
    @Nullable private StaticLayout nameLayout;
    private int nameLayoutWidth = -1;

    public DumbbellRowView(@NonNull Context context) {
        this(context, null);
    }

    public DumbbellRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        namePaint = new TextPaint(textPaint(R.style.TextAppearance_BBetter_BodyStrong, 700));
        namePaint.setColor(color(R.color.bb_on_surface));
        percentPaint = textPaint(R.style.TextAppearance_BBetter_BodyStrong, 700);
        percentPaint.setTextAlign(Paint.Align.RIGHT);
        linePaint = strokePaint(R.color.bb_outline, R.dimen.chart_dot_stroke);
        beforePaint = strokePaint(R.color.bb_on_surface_muted, R.dimen.chart_dot_stroke);
        afterPaint = fillPaint(R.color.bb_primary);
        dotRadius = dimen(R.dimen.chart_dot_radius);
        rowPadding = dimen(R.dimen.chart_row_padding);
        gap = dimen(R.dimen.spacing_sm);
        downColor = color(R.color.bb_primary_bright);
        upColor = color(R.color.bb_danger);
    }

    public void setData(@Nullable Data data) {
        this.data = data;
        nameLayout = null;
        onDataChanged();
    }

    @Override
    protected boolean hasData() {
        return data != null && (data.before > 0f || data.after > 0f);
    }

    private float contentWidth(int width) {
        return width - getPaddingLeft() - getPaddingRight();
    }

    @NonNull
    private StaticLayout nameLayout(@NonNull Data d, int width) {
        int w = Math.max(1, Math.round(contentWidth(width) * NAME_SHARE));
        StaticLayout layout = nameLayout;
        if (layout == null || nameLayoutWidth != w) {
            layout = StaticLayout.Builder.obtain(d.name, 0, d.name.length(), namePaint, w)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(2)
                    .build();
            nameLayout = layout;
            nameLayoutWidth = w;
        }
        return layout;
    }

    @Override
    protected int desiredHeight(int width) {
        Data d = data;
        float min = dimen(R.dimen.chart_row_min_height);
        if (d == null || width <= 0) {
            return Math.round(min);
        }
        return Math.round(Math.max(min, nameLayout(d, width).getHeight() + rowPadding * 2f));
    }

    private float trackLeft() {
        return getPaddingLeft() + contentWidth(getWidth()) * NAME_SHARE + gap + dotRadius;
    }

    private float trackRight(@NonNull Data d) {
        float percentW = percentPaint.measureText(ChartFormat.MINUS + "100 %");
        return getWidth() - getPaddingRight() - percentW - gap - dotRadius;
    }

    private float xOf(@NonNull Data d, float value) {
        float l = trackLeft();
        return l + ChartScale.extent(value, d.scaleMax, trackRight(d) - l);
    }

    private float centerY() {
        return getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
    }

    @Override
    protected void onDrawChart(@NonNull Canvas canvas) {
        Data d = data;
        if (d == null) {
            return;
        }
        StaticLayout layout = nameLayout(d, getWidth());
        float cy = centerY();
        canvas.save();
        canvas.translate(getPaddingLeft(), cy - layout.getHeight() / 2f);
        layout.draw(canvas);
        canvas.restore();

        float p = markProgress(0);
        float xb = xOf(d, d.before);
        float xa = xb + (xOf(d, d.after) - xb) * p;
        int alpha = alphaFor(0);
        linePaint.setAlpha(alpha);
        beforePaint.setAlpha(alpha);
        afterPaint.setAlpha(alpha);
        drawLine(canvas, Math.min(xa, xb), cy, Math.max(xa, xb), cy, linePaint);
        canvas.drawCircle(xb, cy, dotRadius - dimen(R.dimen.chart_dot_stroke) / 2f, beforePaint);
        canvas.drawCircle(xa, cy, dotRadius, afterPaint);

        if (d.change != null) {
            percentPaint.setColor(d.change > 0 ? upColor : downColor);
            percentPaint.setAlpha(Math.round(255 * p));
            canvas.drawText(d.changeText(), getWidth() - getPaddingRight(), baselineAt(percentPaint, cy), percentPaint);
        }
    }

    @Override
    protected int markAt(float x, float y) {
        return data == null ? NO_MARK : 0;
    }

    @Override
    protected void markAnchor(int mark, @NonNull PointF out) {
        Data d = data;
        if (d == null) {
            return;
        }
        out.set((xOf(d, d.before) + xOf(d, d.after)) / 2f, centerY() - dotRadius);
    }

    @Nullable
    @Override
    protected CharSequence bubbleText(int mark) {
        Data d = data;
        if (d == null) {
            return null;
        }
        return d.beforeName + " · " + d.formatter.format(d.before) + "\n"
                + d.afterName + " · " + d.formatter.format(d.after);
    }

    @NonNull
    @Override
    protected CharSequence summary() {
        Data d = data;
        if (d == null) {
            return "";
        }
        return getContext().getString(R.string.chart_desc_dumbbell, d.name, d.beforeName,
                d.formatter.format(d.before), d.afterName, d.formatter.format(d.after), d.changeText());
    }
}
