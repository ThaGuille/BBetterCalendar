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
 * Mini bars of a summary card: usually 7 days, no axes, the highlighted one (today) brighter. A day with no
 * value keeps a 2dp stub so the week still reads as seven slots.
 */
public class SparkBarsView extends BaseChartView {

    /** Immutable data. */
    public static final class Data {
        final float[] values;
        final String[] labels;
        final int highlight;
        final ChartFormatter formatter;

        /**
         * @param labels    names used by the bubble and the summary ("Lun"...), may be null
         * @param highlight index drawn brighter (today), or -1
         */
        public Data(@NonNull float[] values, @Nullable String[] labels, int highlight,
                    @NonNull ChartFormatter formatter) {
            this.values = values.clone();
            this.labels = fit(labels, values.length);
            this.highlight = highlight;
            this.formatter = formatter;
        }
    }

    @Nullable private Data data;
    private final Paint barPaint;
    private final Paint todayPaint;
    private final Paint stubPaint;
    private final float radius;
    private final float minBar;

    public SparkBarsView(@NonNull Context context) {
        this(context, null);
    }

    public SparkBarsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        barPaint = fillPaint(R.color.bb_heat_2);
        todayPaint = fillPaint(R.color.bb_primary_bright);
        stubPaint = fillPaint(R.color.bb_chart_grid);
        radius = dimen(R.dimen.chart_spark_radius);
        minBar = dimen(R.dimen.chart_bar_min);
    }

    public void setData(@Nullable Data data) {
        this.data = data;
        onDataChanged();
    }

    @Override
    protected boolean hasData() {
        return data != null && data.values.length > 0;
    }

    @Override
    protected int markCount() {
        return data == null ? 1 : data.values.length;
    }

    @Override
    protected int desiredHeight(int width) {
        return Math.round(dimen(R.dimen.chart_spark_height));
    }

    private BarSlots slots(@NonNull Data d) {
        return new BarSlots(getPaddingLeft(), getWidth() - getPaddingLeft() - getPaddingRight(), d.values.length);
    }

    private float plotHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    @Override
    protected void onDrawChart(@NonNull Canvas canvas) {
        Data d = data;
        if (d == null) {
            return;
        }
        BarSlots slots = slots(d);
        float barW = slots.slotWidth() * 0.6f;
        float bottom = getHeight() - getPaddingBottom();
        float max = ChartScale.max(d.values);
        for (int i = 0; i < d.values.length; i++) {
            float cx = slots.center(i);
            float h = ChartScale.extent(d.values[i], max, plotHeight()) * markProgress(i);
            Paint paint = h < minBar ? stubPaint : (i == d.highlight ? todayPaint : barPaint);
            h = Math.max(minBar, h);
            paint.setAlpha(alphaFor(i));
            drawBar(canvas, cx - barW / 2f, bottom - h, cx + barW / 2f, bottom, radius, true, paint);
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
        float h = ChartScale.extent(d.values[mark], ChartScale.max(d.values), plotHeight());
        out.set(slots(d).center(mark), getHeight() - getPaddingBottom() - Math.max(minBar, h));
    }

    @Nullable
    @Override
    protected CharSequence bubbleText(int mark) {
        Data d = data;
        if (d == null) {
            return null;
        }
        String label = d.labels[mark];
        String value = d.formatter.format(d.values[mark]);
        return label.isEmpty() ? value : label + " · " + value;
    }

    @NonNull
    @Override
    protected CharSequence summary() {
        Data d = data;
        if (d == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(getContext().getString(R.string.chart_desc_bars)).append(": ");
        for (int i = 0; i < d.values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            if (!d.labels[i].isEmpty()) {
                sb.append(d.labels[i]).append(' ');
            }
            sb.append(d.formatter.format(d.values[i]));
        }
        return sb.toString();
    }
}
