package com.example.bbettercalendar.ui.common.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * Usage bar of one app (mockup 20): a rounded horizontal bar on a track, with a light tick at the daily
 * limit. {@code bb_danger} when the use is over the limit, sage when it has a limit and is within it,
 * {@code bb_data_compare} when it has none. Rows of one list share {@code scaleMax}. The name and value
 * text are the row's own layout. When the row itself opens a screen, call {@link #setTapEnabled}(false).
 */
public class LimitBarView extends BaseChartView {

    /** Immutable data. */
    public static final class Data {
        final float used;
        final float limit;
        final float scaleMax;
        final ChartFormatter formatter;
        final int state;

        /**
         * @param used     minutes used
         * @param limit    daily limit in minutes, 0 for none
         * @param scaleMax largest of used / limit over the whole list ({@link ChartScale#max})
         */
        public Data(float used, float limit, float scaleMax, @NonNull ChartFormatter formatter) {
            this.used = Math.max(0f, used);
            this.limit = Math.max(0f, limit);
            this.scaleMax = Math.max(scaleMax, Math.max(this.used, this.limit));
            this.formatter = formatter;
            this.state = ChartFormat.limitState(this.used, this.limit);
        }
    }

    @Nullable private Data data;
    private final Paint trackPaint;
    private final Paint overPaint;
    private final Paint withinPaint;
    private final Paint nonePaint;
    private final Paint tickPaint;
    private final RectF rect = new RectF();
    private final float barHeight;
    private final float tickWidth;

    public LimitBarView(@NonNull Context context) {
        this(context, null);
    }

    public LimitBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        trackPaint = fillPaint(R.color.bb_surface_raised);
        overPaint = fillPaint(R.color.bb_danger);
        withinPaint = fillPaint(R.color.bb_primary);
        nonePaint = fillPaint(R.color.bb_data_compare);
        tickPaint = fillPaint(R.color.bb_on_surface);
        barHeight = dimen(R.dimen.chart_limit_bar);
        tickWidth = dimen(R.dimen.chart_limit_tick);
    }

    public void setData(@Nullable Data data) {
        this.data = data;
        onDataChanged();
    }

    @Override
    protected boolean hasData() {
        return data != null && data.scaleMax > 0f;
    }

    @Override
    protected int desiredHeight(int width) {
        return Math.round(dimen(R.dimen.chart_limit_height));
    }

    /** The empty state of a single bar is just its track: no "No data yet" text in a 16dp row. */
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!hasData()) {
            drawTrack(canvas);
            return;
        }
        super.onDraw(canvas);
    }

    private float left() {
        return getPaddingLeft() + tickWidth / 2f;
    }

    private float right() {
        return getWidth() - getPaddingRight() - tickWidth / 2f;
    }

    private float cy() {
        return getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
    }

    private void drawTrack(@NonNull Canvas canvas) {
        float r = barHeight / 2f;
        rect.set(left(), cy() - r, right(), cy() + r);
        canvas.drawRoundRect(rect, r, r, trackPaint);
    }

    @Override
    protected void onDrawChart(@NonNull Canvas canvas) {
        Data d = data;
        if (d == null) {
            return;
        }
        drawTrack(canvas);
        float r = barHeight / 2f;
        float cy = cy();
        float w = ChartScale.extent(d.used, d.scaleMax, right() - left()) * markProgress(0);
        if (w > 0f) {
            Paint fill = d.state == ChartFormat.LIMIT_OVER ? overPaint
                    : d.state == ChartFormat.LIMIT_WITHIN ? withinPaint : nonePaint;
            rect.set(left(), cy - r, left() + Math.max(w, barHeight), cy + r);
            canvas.drawRoundRect(rect, r, r, fill);
        }
        if (d.state != ChartFormat.LIMIT_NONE) {
            float x = left() + ChartScale.extent(d.limit, d.scaleMax, right() - left());
            rect.set(x - tickWidth / 2f, getPaddingTop(), x + tickWidth / 2f, getHeight() - getPaddingBottom());
            canvas.drawRoundRect(rect, tickWidth / 2f, tickWidth / 2f, tickPaint);
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
        out.set(left() + ChartScale.extent(d.used, d.scaleMax, right() - left()), cy() - barHeight / 2f);
    }

    @Nullable
    @Override
    protected CharSequence bubbleText(int mark) {
        return summary();
    }

    @NonNull
    @Override
    protected CharSequence summary() {
        Data d = data;
        if (d == null) {
            return "";
        }
        String used = d.formatter.format(d.used);
        switch (d.state) {
            case ChartFormat.LIMIT_OVER:
                return getContext().getString(R.string.chart_limit_over, used, d.formatter.format(d.limit));
            case ChartFormat.LIMIT_WITHIN:
                return getContext().getString(R.string.chart_limit_of, used, d.formatter.format(d.limit));
            default:
                return getContext().getString(R.string.chart_limit_none, used);
        }
    }
}
