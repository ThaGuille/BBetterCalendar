package com.example.bbettercalendar.ui.common.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * Time per project (mockup 16): at most 4 slices plus "Others", largest first from 12 o'clock, separated by a
 * small gap, with the total centred. The legend is a normal layout beside the view: build it from
 * {@link Data#sliceCount()}, {@link Data#sliceName(int)}, {@link Data#sliceValue(int)} and
 * {@link Data#sliceColor(int)} so it matches the folded slices. Slices sweep in one after another.
 */
public class DonutView extends BaseChartView {

    public static final int MAX_SLICES = 4;
    private static final float GAP_DEG = 4f;
    private static final int[] DEFAULT_COLORS = {
            R.color.bb_project_1, R.color.bb_project_2, R.color.bb_project_3, R.color.bb_project_4};

    /** Immutable data, already folded to {@link #MAX_SLICES} + "Others". */
    public static final class Data {
        final float[] values;
        final String[] names;
        final int[] colors;
        final float total;
        final String centerValue;
        final String centerCaption;
        final ChartFormatter formatter;
        final float[] starts;
        final float[] sweeps;

        /**
         * @param values     one value per item (minutes per project)
         * @param names      one name per item
         * @param colors     one {@code bb_*} colour per item, or null for {@code bb_project_1..4} in order
         * @param othersName name of the folded slice ("Otros")
         * @param caption    small text under the total ("total")
         */
        public Data(@NonNull float[] values, @NonNull String[] names, @Nullable @ColorRes int[] colors,
                    @NonNull String othersName, @NonNull String caption, @NonNull ChartFormatter formatter) {
            DonutGeometry.Folded folded = DonutGeometry.fold(values, MAX_SLICES);
            String[] allNames = fit(names, values.length);
            int n = folded.values.length;
            this.values = folded.values;
            this.names = new String[n];
            this.colors = new int[n];
            for (int i = 0; i < n; i++) {
                int src = folded.source[i];
                if (src == DonutGeometry.OTHERS) {
                    this.names[i] = othersName;
                    this.colors[i] = R.color.bb_cat_4;
                } else {
                    this.names[i] = allNames[src];
                    this.colors[i] = colors != null && src < colors.length
                            ? colors[src] : DEFAULT_COLORS[src % DEFAULT_COLORS.length];
                }
            }
            total = ChartScale.sum(values);
            centerValue = formatter.format(total);
            centerCaption = caption;
            this.formatter = formatter;
            float[][] arcs = DonutGeometry.arcs(this.values, GAP_DEG);
            starts = arcs[0];
            sweeps = arcs[1];
        }

        public int sliceCount() {
            return values.length;
        }

        @NonNull
        public String sliceName(int i) {
            return names[i];
        }

        public float sliceValue(int i) {
            return values[i];
        }

        @ColorRes
        public int sliceColor(int i) {
            return colors[i];
        }

        public int slicePercent(int i) {
            return ChartFormat.share(values[i], total);
        }
    }

    @Nullable private Data data;
    @Nullable private Paint[] slicePaints;
    private final Paint valuePaint;
    private final Paint captionPaint;
    private final RectF oval = new RectF();
    private final float maxStroke;

    public DonutView(@NonNull Context context) {
        this(context, null);
    }

    public DonutView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        valuePaint = textPaint(R.style.TextAppearance_BBetter_Title, 700);
        valuePaint.setColor(color(R.color.bb_on_surface));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        captionPaint = textPaint(R.style.TextAppearance_BBetter_Caption, 500);
        captionPaint.setTextAlign(Paint.Align.CENTER);
        maxStroke = dimen(R.dimen.chart_donut_stroke);
    }

    public void setData(@Nullable Data data) {
        this.data = data;
        if (data != null) {
            Paint[] paints = new Paint[data.sliceCount()];
            for (int i = 0; i < paints.length; i++) {
                paints[i] = fillPaint(data.colors[i]);
                paints[i].setStyle(Paint.Style.STROKE);
                paints[i].setStrokeCap(Paint.Cap.BUTT);
            }
            slicePaints = paints;
        }
        onDataChanged();
    }

    @Override
    protected boolean hasData() {
        return data != null && data.sliceCount() > 0 && data.total > 0f;
    }

    @Override
    protected int markCount() {
        return data == null ? 1 : data.sliceCount();
    }

    @Override
    protected int desiredHeight(int width) {
        int size = Math.round(dimen(R.dimen.chart_donut_size));
        return width > 0 ? Math.min(size, width) : size;
    }

    @Override
    protected int desiredWidth() {
        return Math.round(dimen(R.dimen.chart_donut_size));
    }

    private float stroke() {
        return Math.min(maxStroke, diameter() * 0.2f);
    }

    private float diameter() {
        return Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom());
    }

    private float cx() {
        return getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
    }

    private float cy() {
        return getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
    }

    @Override
    protected void onDrawChart(@NonNull Canvas canvas) {
        Data d = data;
        Paint[] paints = slicePaints;
        if (d == null || paints == null) {
            return;
        }
        float stroke = stroke();
        float r = diameter() / 2f - stroke * 0.58f;
        float cx = cx();
        float cy = cy();
        oval.set(cx - r, cy - r, cx + r, cy + r);
        int sel = selectedMark();
        for (int i = 0; i < d.sliceCount(); i++) {
            float p = markProgress(i);
            Paint paint = paints[i];
            paint.setStrokeWidth(i == sel ? stroke * 1.15f : stroke);
            paint.setAlpha(alphaFor(i));
            if (p > 0f && d.sweeps[i] > 0f) {
                canvas.drawArc(oval, d.starts[i], d.sweeps[i] * p, false, paint);
            }
        }
        float gap = textHeight(captionPaint) * 0.1f;
        float valueH = textHeight(valuePaint);
        float captionH = textHeight(captionPaint);
        float top = cy - (valueH + gap + captionH) / 2f;
        canvas.drawText(d.centerValue, cx, top - valuePaint.ascent(), valuePaint);
        canvas.drawText(d.centerCaption, cx, top + valueH + gap - captionPaint.ascent(), captionPaint);
    }

    @Override
    protected int markAt(float x, float y) {
        Data d = data;
        if (d == null) {
            return NO_MARK;
        }
        float dx = x - cx();
        float dy = y - cy();
        float dist = (float) Math.hypot(dx, dy);
        float stroke = stroke();
        float outer = diameter() / 2f;
        // The ring plus a stroke of tolerance inwards (fingers are wider than the ring).
        if (dist > outer + stroke / 2f || dist < outer - stroke * 2f) {
            return NO_MARK;
        }
        int i = DonutGeometry.sliceAt(DonutGeometry.angleOf(dx, dy), d.starts, d.sweeps, GAP_DEG);
        return i < 0 ? NO_MARK : i;
    }

    @Override
    protected void markAnchor(int mark, @NonNull PointF out) {
        Data d = data;
        if (d == null) {
            return;
        }
        double mid = Math.toRadians(d.starts[mark] + d.sweeps[mark] / 2f);
        float r = diameter() / 2f;
        out.set(cx() + (float) (Math.cos(mid) * r), cy() + (float) (Math.sin(mid) * r));
    }

    @Nullable
    @Override
    protected CharSequence bubbleText(int mark) {
        Data d = data;
        if (d == null) {
            return null;
        }
        return d.names[mark] + " · " + d.formatter.format(d.values[mark])
                + " (" + ChartFormat.percent(d.slicePercent(mark)) + ")";
    }

    @NonNull
    @Override
    protected CharSequence summary() {
        Data d = data;
        if (d == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(getContext().getString(R.string.chart_desc_donut, d.centerValue));
        for (int i = 0; i < d.sliceCount(); i++) {
            sb.append(i == 0 ? ". " : ", ").append(d.names[i]).append(' ').append(d.formatter.format(d.values[i]))
                    .append(" (").append(ChartFormat.percent(d.slicePercent(i))).append(')');
        }
        return sb.toString();
    }
}
