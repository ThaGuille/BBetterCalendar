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
 * Focus against phone (mockup 18): focus grows up (sage), use of limited apps grows down
 * ({@code bb_data_compare}), from a middle band that holds the day labels. Both series are minutes on
 * <b>one shared scale</b>, so equal lengths mean equal time. Tap a day: bubble with both values.
 */
public class MirrorBarsView extends BaseChartView {

    /** Immutable data. */
    public static final class Data {
        final float[] up;
        final float[] down;
        final String[] labels;
        final String upName;
        final String downName;
        final ChartFormatter formatter;

        /**
         * @param up       focus minutes per day
         * @param down     phone minutes per day (same length)
         * @param upName   "Enfoque"
         * @param downName "Apps limitadas"
         */
        public Data(@NonNull float[] up, @NonNull float[] down, @Nullable String[] labels, @NonNull String upName,
                    @NonNull String downName, @NonNull ChartFormatter formatter) {
            this.up = up.clone();
            float[] d = fitValues(down, up.length);
            this.down = d == null ? new float[up.length] : d;
            this.labels = fit(labels, up.length);
            this.upName = upName;
            this.downName = downName;
            this.formatter = formatter;
        }

        float scale() {
            return ChartScale.max(up, down);
        }
    }

    @Nullable private Data data;
    private final Paint upPaint;
    private final Paint downPaint;
    private final Paint labelPaint;
    private final float radius;
    private final float labelGap;

    private float midTop;
    private float midBottom;

    public MirrorBarsView(@NonNull Context context) {
        this(context, null);
    }

    public MirrorBarsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        upPaint = fillPaint(R.color.bb_primary);
        downPaint = fillPaint(R.color.bb_data_compare);
        labelPaint = textPaint(R.style.TextAppearance_BBetter_Micro, 500);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        radius = dimen(R.dimen.chart_bar_radius);
        labelGap = dimen(R.dimen.chart_label_gap);
    }

    public void setData(@Nullable Data data) {
        this.data = data;
        onDataChanged();
    }

    @Override
    protected boolean hasData() {
        return data != null && data.up.length > 0 && data.scale() > 0f;
    }

    @Override
    protected int markCount() {
        return data == null ? 1 : data.up.length;
    }

    @Override
    protected int desiredHeight(int width) {
        return Math.round(dimen(R.dimen.chart_mirror_height));
    }

    private BarSlots layoutPlot(@NonNull Data d) {
        float band = textHeight(labelPaint) + labelGap * 2f;
        float top = getPaddingTop();
        float bottom = getHeight() - getPaddingBottom();
        float mid = top + (bottom - top) / 2f;
        midTop = mid - band / 2f;
        midBottom = mid + band / 2f;
        return new BarSlots(getPaddingLeft(), getWidth() - getPaddingLeft() - getPaddingRight(), d.up.length);
    }

    private float half() {
        return midTop - getPaddingTop();
    }

    @Override
    protected void onDrawChart(@NonNull Canvas canvas) {
        Data d = data;
        if (d == null) {
            return;
        }
        BarSlots slots = layoutPlot(d);
        float barW = slots.slotWidth() * 0.5f;
        float scale = d.scale();
        float half = half();
        for (int i = 0; i < d.up.length; i++) {
            float p = markProgress(i);
            int alpha = alphaFor(i);
            float cx = slots.center(i);
            float l = cx - barW / 2f;
            float r = cx + barW / 2f;
            float hu = ChartScale.extent(d.up[i], scale, half) * p;
            float hd = ChartScale.extent(d.down[i], scale, half) * p;
            upPaint.setAlpha(alpha);
            downPaint.setAlpha(alpha);
            drawBar(canvas, l, midTop - hu, r, midTop, radius, true, upPaint);
            drawBar(canvas, l, midBottom, r, midBottom + hd, radius, false, downPaint);
            canvas.drawText(d.labels[i], cx, baselineAt(labelPaint, (midTop + midBottom) / 2f), labelPaint);
        }
    }

    @Override
    protected int markAt(float x, float y) {
        Data d = data;
        return d == null ? NO_MARK : layoutPlot(d).indexAt(x);
    }

    @Override
    protected void markAnchor(int mark, @NonNull PointF out) {
        Data d = data;
        if (d == null) {
            return;
        }
        BarSlots slots = layoutPlot(d);
        out.set(slots.center(mark), midTop - ChartScale.extent(d.up[mark], d.scale(), half()));
    }

    @Nullable
    @Override
    protected CharSequence bubbleText(int mark) {
        Data d = data;
        if (d == null) {
            return null;
        }
        return d.labels[mark] + "\n" + d.upName + " · " + d.formatter.format(d.up[mark])
                + "\n" + d.downName + " · " + d.formatter.format(d.down[mark]);
    }

    @NonNull
    @Override
    protected CharSequence summary() {
        Data d = data;
        if (d == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(getContext().getString(R.string.chart_desc_mirror));
        for (int i = 0; i < d.up.length; i++) {
            sb.append(i == 0 ? ". " : "; ").append(d.labels[i]).append(": ")
                    .append(d.upName).append(' ').append(d.formatter.format(d.up[i])).append(", ")
                    .append(d.downName).append(' ').append(d.formatter.format(d.down[i]));
        }
        return sb.toString();
    }
}
