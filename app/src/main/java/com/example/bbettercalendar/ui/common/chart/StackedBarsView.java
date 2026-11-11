package com.example.bbettercalendar.ui.common.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * Stacked bars: completed / abandoned sessions (mockup 15) and daily use per app (mockup 21). Series stack
 * bottom-up in the given order, 2dp apart; only the top segment of each bar is rounded. Optional dashed,
 * labelled average of the totals. Tap a bar: bubble with every non-zero series.
 */
public class StackedBarsView extends BaseChartView {

    /** Immutable data. */
    public static final class Data {
        final float[][] values;
        final float[] totals;
        final int[] colors;
        final String[] seriesNames;
        final String[] labels;
        final boolean showAverage;
        final ChartFormatter formatter;
        final int series;

        /**
         * @param values      [bar][series], bottom series first
         * @param seriesColors one {@code bb_*} colour resource per series
         * @param seriesNames  one name per series (bubble and summary)
         * @param labels       one label per bar
         * @param showAverage  draw the dashed average of the totals
         */
        public Data(@NonNull float[][] values, @NonNull @ColorRes int[] seriesColors, @NonNull String[] seriesNames,
                    @Nullable String[] labels, boolean showAverage, @NonNull ChartFormatter formatter) {
            series = seriesColors.length;
            this.values = new float[values.length][series];
            totals = new float[values.length];
            for (int b = 0; b < values.length; b++) {
                float[] row = fitValues(values[b], series);
                this.values[b] = row == null ? new float[series] : row;
                totals[b] = ChartScale.sum(this.values[b]);
            }
            this.colors = seriesColors.clone();
            this.seriesNames = fit(seriesNames, series);
            this.labels = fit(labels, values.length);
            this.showAverage = showAverage;
            this.formatter = formatter;
        }

        float average() {
            return ChartScale.mean(totals);
        }
    }

    @Nullable private Data data;
    @Nullable private Paint[] seriesPaints;
    private final Paint avgPaint;
    private final Paint avgLabelPaint;
    private final Paint labelPaint;
    private final float radius;
    private final float segmentGap;
    private final float labelGap;

    private float plotLeft;
    private float plotRight;
    private float plotTop;
    private float plotBottom;
    private float scaleMax;

    public StackedBarsView(@NonNull Context context) {
        this(context, null);
    }

    public StackedBarsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        avgPaint = strokePaint(R.color.bb_chart_average, R.dimen.chart_line, R.dimen.chart_dash_on,
                R.dimen.chart_dash_off);
        avgLabelPaint = textPaint(R.style.TextAppearance_BBetter_Micro, 500);
        labelPaint = textPaint(R.style.TextAppearance_BBetter_Micro, 500);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        radius = dimen(R.dimen.chart_bar_radius);
        segmentGap = dimen(R.dimen.chart_segment_gap);
        labelGap = dimen(R.dimen.chart_label_gap);
    }

    public void setData(@Nullable Data data) {
        this.data = data;
        if (data != null) {
            Paint[] paints = new Paint[data.series];
            for (int s = 0; s < data.series; s++) {
                paints[s] = fillPaint(data.colors[s]);
            }
            seriesPaints = paints;
        }
        onDataChanged();
    }

    @Override
    protected boolean hasData() {
        return data != null && data.totals.length > 0 && ChartScale.max(data.totals) > 0f;
    }

    @Override
    protected int markCount() {
        return data == null ? 1 : data.totals.length;
    }

    private void layoutPlot(@NonNull Data d) {
        scaleMax = ChartScale.max(d.totals);
        plotLeft = getPaddingLeft();
        plotRight = getWidth() - getPaddingRight();
        // Room above the tallest bar for the average label when it is drawn.
        plotTop = getPaddingTop() + (d.showAverage ? textHeight(avgLabelPaint) + labelGap / 2f : 0f);
        plotBottom = getHeight() - getPaddingBottom() - textHeight(labelPaint) - labelGap;
    }

    @Override
    protected void onDrawChart(@NonNull Canvas canvas) {
        Data d = data;
        Paint[] paints = seriesPaints;
        if (d == null || paints == null) {
            return;
        }
        layoutPlot(d);
        float plotH = plotBottom - plotTop;
        BarSlots slots = new BarSlots(plotLeft, plotRight - plotLeft, d.totals.length);
        float barW = slots.slotWidth() * 0.55f;
        for (int b = 0; b < d.totals.length; b++) {
            float p = markProgress(b);
            int alpha = alphaFor(b);
            float l = slots.center(b) - barW / 2f;
            float r = l + barW;
            int top = topSeries(d, b);
            float base = plotBottom;
            float cumulative = 0f;
            for (int s = 0; s < d.series; s++) {
                float v = d.values[b][s];
                if (!(v > 0f)) {
                    continue;
                }
                cumulative += v;
                float y = plotBottom - ChartScale.extent(cumulative, scaleMax, plotH) * p;
                float segBottom = s == firstSeries(d, b) ? base : base - segmentGap;
                paints[s].setAlpha(alpha);
                if (segBottom > y) {
                    drawBar(canvas, l, y, r, segBottom, s == top ? radius : 0f, true, paints[s]);
                }
                base = y;
            }
        }
        if (d.showAverage) {
            float avg = d.average();
            float y = plotBottom - ChartScale.extent(avg, scaleMax, plotH);
            int a = Math.round(255 * markProgress(d.totals.length - 1));
            avgPaint.setAlpha(a);
            drawLine(canvas, plotLeft, y, plotRight, y, avgPaint);
            avgLabelPaint.setAlpha(a);
            String label = getContext().getString(R.string.chart_average, d.formatter.format(avg));
            canvas.drawText(label, plotLeft, y - labelGap / 2f - avgLabelPaint.descent(), avgLabelPaint);
        }
        float baseline = plotBottom + labelGap - labelPaint.ascent();
        for (int b = 0; b < d.totals.length; b++) {
            canvas.drawText(d.labels[b], slots.center(b), baseline, labelPaint);
        }
    }

    private static int topSeries(@NonNull Data d, int bar) {
        for (int s = d.series - 1; s >= 0; s--) {
            if (d.values[bar][s] > 0f) {
                return s;
            }
        }
        return -1;
    }

    private static int firstSeries(@NonNull Data d, int bar) {
        for (int s = 0; s < d.series; s++) {
            if (d.values[bar][s] > 0f) {
                return s;
            }
        }
        return -1;
    }

    @Override
    protected int markAt(float x, float y) {
        Data d = data;
        if (d == null) {
            return NO_MARK;
        }
        layoutPlot(d);
        return new BarSlots(plotLeft, plotRight - plotLeft, d.totals.length).indexAt(x);
    }

    @Override
    protected void markAnchor(int mark, @NonNull PointF out) {
        Data d = data;
        if (d == null) {
            return;
        }
        layoutPlot(d);
        BarSlots slots = new BarSlots(plotLeft, plotRight - plotLeft, d.totals.length);
        out.set(slots.center(mark), plotBottom - ChartScale.extent(d.totals[mark], scaleMax, plotBottom - plotTop));
    }

    @Nullable
    @Override
    protected CharSequence bubbleText(int mark) {
        Data d = data;
        if (d == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(d.labels[mark]).append(" · ").append(d.formatter.format(d.totals[mark]));
        for (int s = d.series - 1; s >= 0; s--) {
            if (d.values[mark][s] > 0f) {
                sb.append('\n').append(d.seriesNames[s]).append(" · ").append(d.formatter.format(d.values[mark][s]));
            }
        }
        return sb.toString();
    }

    @NonNull
    @Override
    protected CharSequence summary() {
        Data d = data;
        if (d == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(getContext().getString(R.string.chart_desc_stacked)).append(". ");
        for (int b = 0; b < d.totals.length; b++) {
            if (b > 0) {
                sb.append("; ");
            }
            sb.append(d.labels[b]).append(' ').append(d.formatter.format(d.totals[b]));
            StringBuilder parts = new StringBuilder();
            for (int s = 0; s < d.series; s++) {
                if (d.values[b][s] > 0f) {
                    if (parts.length() > 0) {
                        parts.append(", ");
                    }
                    parts.append(d.seriesNames[s]).append(' ').append(d.formatter.format(d.values[b][s]));
                }
            }
            if (parts.length() > 0) {
                sb.append(" (").append(parts).append(')');
            }
        }
        if (d.showAverage) {
            sb.append(". ").append(getContext().getString(R.string.chart_average, d.formatter.format(d.average())));
        }
        return sb.toString();
    }
}
