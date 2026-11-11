package com.example.bbettercalendar.ui.common.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * Focus per day / week / month against the previous period (mockup 11): sage bars (the highlighted one, today,
 * in {@code bb_primary_bright}) with a thin {@code bb_data_compare} bar for the previous period beside each,
 * a dashed labelled average line, a dotted grid with hour labels on the right and top corners of 4dp.
 * Values are minutes. Tap a column: bubble with the value and the previous one.
 */
public class CompareBarsView extends BaseChartView {

    private static final int MAX_TICKS = 3;

    /** Immutable data. */
    public static final class Data {
        final float[] current;
        @Nullable final float[] previous;
        final String[] labels;
        final int highlight;
        final float average;
        final ChartFormatter formatter;
        final String currentName;
        @Nullable final String previousName;

        /**
         * @param current      minutes per column
         * @param previous     same columns of the previous period, or null for no comparison
         * @param labels       column labels ("L", "M"...)
         * @param highlight    column drawn brighter and with a bold label (today), or -1
         * @param average      value of the dashed average line, or NaN / 0 for none
         * @param currentName  series name for the bubble and summary ("Esta semana")
         * @param previousName previous series name ("Semana anterior")
         */
        public Data(@NonNull float[] current, @Nullable float[] previous, @Nullable String[] labels, int highlight,
                    float average, @NonNull ChartFormatter formatter, @NonNull String currentName,
                    @Nullable String previousName) {
            this.current = current.clone();
            this.previous = fitValues(previous, current.length);
            this.labels = fit(labels, current.length);
            this.highlight = highlight;
            this.average = average;
            this.formatter = formatter;
            this.currentName = currentName;
            this.previousName = previousName;
        }

        boolean hasAverage() {
            return average > 0f;
        }
    }

    @Nullable private Data data;

    private final Paint barPaint;
    private final Paint todayPaint;
    private final Paint prevPaint;
    private final Paint gridPaint;
    private final Paint avgPaint;
    private final Paint axisPaint;
    private final Paint labelPaint;
    private final Paint labelStrongPaint;
    private final float radius;
    private final float gap;
    private final float labelGap;

    // Layout of the last pass (also used for hit tests).
    private float plotLeft;
    private float plotRight;
    private float plotTop;
    private float plotBottom;
    private float scaleMax;
    private float axisWidth;
    private float[] ticks = new float[0];

    public CompareBarsView(@NonNull Context context) {
        this(context, null);
    }

    public CompareBarsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        barPaint = fillPaint(R.color.bb_primary);
        todayPaint = fillPaint(R.color.bb_primary_bright);
        prevPaint = fillPaint(R.color.bb_data_compare);
        gridPaint = strokePaint(R.color.bb_chart_grid, R.dimen.chart_line, R.dimen.chart_dot_on, R.dimen.chart_dot_off);
        avgPaint = strokePaint(R.color.bb_chart_average, R.dimen.chart_line, R.dimen.chart_dash_on,
                R.dimen.chart_dash_off);
        axisPaint = textPaint(R.style.TextAppearance_BBetter_Micro, 500);
        labelPaint = textPaint(R.style.TextAppearance_BBetter_Micro, 500);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelStrongPaint = textPaint(R.style.TextAppearance_BBetter_Micro_Bold, 700);
        labelStrongPaint.setColor(color(R.color.bb_on_surface));
        labelStrongPaint.setTextAlign(Paint.Align.CENTER);
        radius = dimen(R.dimen.chart_bar_radius);
        gap = dimen(R.dimen.chart_compare_gap);
        labelGap = dimen(R.dimen.chart_label_gap);
    }

    public void setData(@Nullable Data data) {
        this.data = data;
        onDataChanged();
    }

    @Override
    protected boolean hasData() {
        return data != null && data.current.length > 0;
    }

    @Override
    protected int markCount() {
        return data == null ? 1 : data.current.length;
    }

    private void layoutPlot(@NonNull Data d) {
        scaleMax = Math.max(ChartScale.max(d.current, d.previous), d.hasAverage() ? d.average : 0f);
        if (!(scaleMax > 0f)) {
            scaleMax = 1f;
        }
        ticks = ChartScale.ticks(scaleMax, ChartScale.niceMinuteStep(scaleMax, MAX_TICKS));
        axisWidth = 0f;
        for (float t : ticks) {
            axisWidth = Math.max(axisWidth, axisPaint.measureText(d.formatter.format(t)));
        }
        if (axisWidth > 0f) {
            axisWidth += labelGap;
        }
        plotLeft = getPaddingLeft();
        plotRight = getWidth() - getPaddingRight() - axisWidth;
        plotTop = getPaddingTop() + textHeight(axisPaint) / 2f;
        plotBottom = getHeight() - getPaddingBottom() - textHeight(labelPaint) - labelGap;
    }

    @Override
    protected void onDrawChart(@NonNull Canvas canvas) {
        Data d = data;
        if (d == null) {
            return;
        }
        layoutPlot(d);
        float plotH = plotBottom - plotTop;
        int n = d.current.length;

        for (float t : ticks) {
            float y = plotBottom - ChartScale.extent(t, scaleMax, plotH);
            drawLine(canvas, plotLeft, y, plotRight, y, gridPaint);
            canvas.drawText(d.formatter.format(t), plotRight + labelGap, baselineAt(axisPaint, y), axisPaint);
        }

        BarSlots slots = new BarSlots(plotLeft, plotRight - plotLeft, n);
        float slotW = slots.slotWidth();
        boolean compare = d.previous != null;
        float group = slotW * (compare ? 0.72f : 0.56f);
        float prevW = compare ? (group - gap) * 0.42f : 0f;
        float curW = compare ? group - gap - prevW : group;
        for (int i = 0; i < n; i++) {
            float p = markProgress(i);
            int alpha = alphaFor(i);
            float left = slots.center(i) - group / 2f;
            if (compare) {
                float h = ChartScale.extent(d.previous[i], scaleMax, plotH) * p;
                prevPaint.setAlpha(alpha);
                drawBar(canvas, left, plotBottom - h, left + prevW, plotBottom, radius, true, prevPaint);
                left += prevW + gap;
            }
            Paint paint = i == d.highlight ? todayPaint : barPaint;
            paint.setAlpha(alpha);
            float h = ChartScale.extent(d.current[i], scaleMax, plotH) * p;
            drawBar(canvas, left, plotBottom - h, left + curW, plotBottom, radius, true, paint);
        }

        if (d.hasAverage()) {
            float y = plotBottom - ChartScale.extent(d.average, scaleMax, plotH);
            int a = Math.round(255 * markProgress(n - 1));
            avgPaint.setAlpha(a);
            drawLine(canvas, plotLeft, y, plotRight, y, avgPaint);
            String label = getContext().getString(R.string.chart_average, d.formatter.format(d.average));
            int old = axisPaint.getAlpha();
            axisPaint.setAlpha(Math.min(old, a));
            canvas.drawText(label, plotLeft + labelGap / 2f, y - labelGap / 2f - axisPaint.descent(), axisPaint);
            axisPaint.setAlpha(old);
        }

        float baseline = plotBottom + labelGap - labelPaint.ascent();
        for (int i = 0; i < n; i++) {
            Paint paint = i == d.highlight ? labelStrongPaint : labelPaint;
            canvas.drawText(d.labels[i], slots.center(i), baseline, paint);
        }
    }

    @Override
    protected int markAt(float x, float y) {
        Data d = data;
        if (d == null) {
            return NO_MARK;
        }
        layoutPlot(d);
        return new BarSlots(plotLeft, plotRight - plotLeft, d.current.length).indexAt(x);
    }

    @Override
    protected void markAnchor(int mark, @NonNull PointF out) {
        Data d = data;
        if (d == null) {
            return;
        }
        layoutPlot(d);
        BarSlots slots = new BarSlots(plotLeft, plotRight - plotLeft, d.current.length);
        float v = Math.max(d.current[mark], d.previous != null ? d.previous[mark] : 0f);
        out.set(slots.center(mark), plotBottom - ChartScale.extent(v, scaleMax, plotBottom - plotTop));
    }

    @Nullable
    @Override
    protected CharSequence bubbleText(int mark) {
        Data d = data;
        if (d == null) {
            return null;
        }
        String text = d.labels[mark] + " · " + d.formatter.format(d.current[mark]);
        if (d.previous != null) {
            String name = d.previousName != null ? d.previousName : "";
            text += "\n" + name + " · " + d.formatter.format(d.previous[mark]);
        }
        return text;
    }

    @NonNull
    @Override
    protected CharSequence summary() {
        Data d = data;
        if (d == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(getContext().getString(R.string.chart_desc_bars));
        sb.append(". ").append(d.currentName).append(": ");
        appendSeries(sb, d, d.current);
        if (d.previous != null) {
            sb.append(". ").append(d.previousName != null ? d.previousName : "").append(": ");
            appendSeries(sb, d, d.previous);
        }
        if (d.hasAverage()) {
            sb.append(". ").append(getContext().getString(R.string.chart_average, d.formatter.format(d.average)));
        }
        return sb.toString();
    }

    private static void appendSeries(StringBuilder sb, Data d, float[] values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(d.labels[i]).append(' ').append(d.formatter.format(values[i]));
        }
    }
}
