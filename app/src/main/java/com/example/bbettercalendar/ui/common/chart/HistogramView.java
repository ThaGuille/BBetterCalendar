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
 * Session lengths (mockup 17): wide bars per duration bucket, the modal bucket highlighted
 * ({@code bb_heat_4}) and labelled above ("14 sesiones"), the rest in {@code bb_heat_2}. Bucket the raw
 * samples with {@link Histogram#bin}. Tap a bar: bubble with its count.
 */
public class HistogramView extends BaseChartView {

    /** Immutable data. */
    public static final class Data {
        final int[] counts;
        final String[] labels;
        final int modal;
        final ChartFormatter countFormatter;

        /**
         * @param counts         one count per bucket
         * @param labels         one label per bucket ("10 m" ... "45+")
         * @param countFormatter count as text with its unit ("14 sesiones"), usually from a plural resource
         */
        public Data(@NonNull int[] counts, @Nullable String[] labels, @NonNull ChartFormatter countFormatter) {
            this.counts = counts.clone();
            this.labels = fit(labels, counts.length);
            this.modal = Histogram.modalIndex(this.counts);
            this.countFormatter = countFormatter;
        }

        float max() {
            return modal < 0 ? 0f : counts[modal];
        }
    }

    @Nullable private Data data;
    private final Paint barPaint;
    private final Paint modalPaint;
    private final Paint labelPaint;
    private final Paint modalLabelPaint;
    private final float radius;
    private final float labelGap;

    private float plotTop;
    private float plotBottom;

    public HistogramView(@NonNull Context context) {
        this(context, null);
    }

    public HistogramView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        barPaint = fillPaint(R.color.bb_heat_2);
        modalPaint = fillPaint(R.color.bb_heat_4);
        labelPaint = textPaint(R.style.TextAppearance_BBetter_Caption, 500);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        modalLabelPaint = textPaint(R.style.TextAppearance_BBetter_Caption_Semibold, 600);
        modalLabelPaint.setColor(color(R.color.bb_on_surface));
        modalLabelPaint.setTextAlign(Paint.Align.CENTER);
        radius = dimen(R.dimen.chart_bar_radius);
        labelGap = dimen(R.dimen.chart_label_gap);
    }

    public void setData(@Nullable Data data) {
        this.data = data;
        onDataChanged();
    }

    @Override
    protected boolean hasData() {
        return data != null && data.modal >= 0;
    }

    @Override
    protected int markCount() {
        return data == null ? 1 : data.counts.length;
    }

    @Override
    protected int desiredHeight(int width) {
        return Math.round(dimen(R.dimen.chart_histogram_height));
    }

    private BarSlots slots(@NonNull Data d) {
        plotTop = getPaddingTop() + textHeight(modalLabelPaint) + labelGap / 2f;
        plotBottom = getHeight() - getPaddingBottom() - textHeight(labelPaint) - labelGap;
        return new BarSlots(getPaddingLeft(), getWidth() - getPaddingLeft() - getPaddingRight(), d.counts.length);
    }

    @Override
    protected void onDrawChart(@NonNull Canvas canvas) {
        Data d = data;
        if (d == null) {
            return;
        }
        BarSlots slots = slots(d);
        float barW = slots.slotWidth() * 0.86f;
        float plotH = plotBottom - plotTop;
        float max = d.max();
        for (int i = 0; i < d.counts.length; i++) {
            Paint paint = i == d.modal ? modalPaint : barPaint;
            paint.setAlpha(alphaFor(i));
            float h = ChartScale.extent(d.counts[i], max, plotH) * markProgress(i);
            float cx = slots.center(i);
            drawBar(canvas, cx - barW / 2f, plotBottom - h, cx + barW / 2f, plotBottom, radius, true, paint);
        }
        float modalP = markProgress(d.modal);
        float modalTop = plotBottom - ChartScale.extent(max, max, plotH) * modalP;
        modalLabelPaint.setAlpha(Math.round(255 * modalP));
        canvas.drawText(d.countFormatter.format(d.counts[d.modal]), slots.center(d.modal),
                modalTop - labelGap / 2f - modalLabelPaint.descent(), modalLabelPaint);
        float baseline = plotBottom + labelGap - labelPaint.ascent();
        for (int i = 0; i < d.counts.length; i++) {
            canvas.drawText(d.labels[i], slots.center(i), baseline, labelPaint);
        }
    }

    @Override
    protected int markAt(float x, float y) {
        Data d = data;
        return d == null ? NO_MARK : slots(d).indexAt(x);
    }

    @Override
    protected void markAnchor(int mark, @NonNull PointF out) {
        Data d = data;
        if (d == null) {
            return;
        }
        BarSlots slots = slots(d);
        out.set(slots.center(mark), plotBottom - ChartScale.extent(d.counts[mark], d.max(), plotBottom - plotTop));
    }

    @Nullable
    @Override
    protected CharSequence bubbleText(int mark) {
        Data d = data;
        if (d == null) {
            return null;
        }
        return d.labels[mark] + " · " + d.countFormatter.format(d.counts[mark]);
    }

    @NonNull
    @Override
    protected CharSequence summary() {
        Data d = data;
        if (d == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(getContext().getString(R.string.chart_desc_histogram,
                d.labels[d.modal], d.countFormatter.format(d.counts[d.modal])));
        for (int i = 0; i < d.counts.length; i++) {
            sb.append(i == 0 ? ". " : ", ").append(d.labels[i]).append(' ').append(d.countFormatter.format(d.counts[i]));
        }
        return sb.toString();
    }
}
