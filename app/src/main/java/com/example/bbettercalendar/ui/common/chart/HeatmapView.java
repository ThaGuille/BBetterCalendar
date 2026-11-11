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

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Grid of square cells coloured on the 5-step sequential ramp {@code bb_heat_0..4} (dark pine to light sage),
 * radius 3dp, gap 2dp, with row and column labels. Two uses: best hours (7 days x 18 hours from 6 h to 23 h,
 * mockup 12, {@link Data#hours}) and consistency (26 weeks x 7 days, mockup 14, {@link Data#calendar}).
 * Columns fade in one after another. Tap a cell: bubble with its name and value.
 */
public class HeatmapView extends BaseChartView {

    /** Best-hours grid: first hour and number of hours (6 h ... 23 h). */
    public static final int FIRST_HOUR = 6;
    public static final int HOURS = 18;

    /** Immutable data. */
    public static final class Data {
        final float[][] values;
        final int rows;
        final int cols;
        @Nullable final String[] rowLabels;
        final String[] colLabels;
        final boolean colLabelsOnTop;
        final String[][] cellNames;
        final ChartFormatter formatter;
        final float max;

        /**
         * @param values         [row][column]; NaN = no cell (future days)
         * @param rowLabels      one per row, or null for no label column
         * @param colLabels      one per column, null entries for no label
         * @param colLabelsOnTop labels above the grid, left-aligned on their column (months); otherwise
         *                       below, centred (hours)
         * @param cellNames      [row][column] names for the bubble and summary, or null to use the labels
         */
        public Data(@NonNull float[][] values, @Nullable String[] rowLabels, @Nullable String[] colLabels,
                    boolean colLabelsOnTop, @Nullable String[][] cellNames, @NonNull ChartFormatter formatter) {
            rows = values.length;
            cols = rows == 0 ? 0 : values[0].length;
            this.values = new float[rows][cols];
            this.cellNames = new String[rows][cols];
            this.rowLabels = rowLabels == null ? null : fit(rowLabels, rows);
            this.colLabels = fit(colLabels, cols);
            float m = 0f;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    float v = c < values[r].length ? values[r][c] : Float.NaN;
                    this.values[r][c] = v;
                    if (v > m) {
                        m = v;
                    }
                    String name = cellNames != null && r < cellNames.length && cellNames[r] != null
                            && c < cellNames[r].length ? cellNames[r][c] : null;
                    if (name == null) {
                        name = ((this.rowLabels != null ? this.rowLabels[r] + " " : "") + this.colLabels[c]).trim();
                    }
                    this.cellNames[r][c] = name;
                }
            }
            this.max = m;
            this.colLabelsOnTop = colLabelsOnTop;
            this.formatter = formatter;
        }

        /**
         * Best hours: 7 rows (Monday first) x {@link #HOURS} columns from {@link #FIRST_HOUR}; labels every 3 h.
         *
         * @param dayLabels one-letter row labels ("L", "M"...)
         * @param dayNames  full names for the bubble ("Lunes"...)
         */
        @NonNull
        public static Data hours(@NonNull float[][] values, @NonNull String[] dayLabels, @NonNull String[] dayNames,
                                 @NonNull ChartFormatter formatter) {
            String[] cols = new String[HOURS];
            String[][] names = new String[values.length][HOURS];
            for (int c = 0; c < HOURS; c++) {
                int hour = FIRST_HOUR + c;
                cols[c] = hour % 3 == 0 ? hour + " h" : null;
                for (int r = 0; r < values.length; r++) {
                    names[r][c] = (r < dayNames.length ? dayNames[r] + " " : "") + hour + " h";
                }
            }
            return new Data(values, dayLabels, cols, false, names, formatter);
        }

        /** Consistency calendar built by {@link CalendarHeatmap}: month labels on top, no row labels. */
        @NonNull
        public static Data calendar(@NonNull CalendarHeatmap cal, @NonNull Locale locale,
                                    @NonNull ChartFormatter formatter) {
            int weeks = cal.weeks();
            String[] cols = new String[weeks];
            for (int i = 0; i < cal.labelColumns.length; i++) {
                String month = Month.of(cal.labelMonths[i]).getDisplayName(TextStyle.SHORT_STANDALONE, locale);
                if (month.endsWith(".")) {
                    month = month.substring(0, month.length() - 1);
                }
                cols[cal.labelColumns[i]] = month;
            }
            DateTimeFormatter day = DateTimeFormatter.ofPattern("d MMM", locale);
            String[][] names = new String[CalendarHeatmap.DAYS][weeks];
            for (int r = 0; r < CalendarHeatmap.DAYS; r++) {
                for (int c = 0; c < weeks; c++) {
                    LocalDate d = cal.dates[r][c];
                    names[r][c] = d == null ? "" : d.format(day);
                }
            }
            return new Data(cal.values, null, cols, true, names, formatter);
        }
    }

    @Nullable private Data data;
    private final Paint[] heat = new Paint[GridGeometry.HEAT_STEPS];
    private final Paint labelPaint;
    private final Paint selectedPaint;
    private final RectF cellRect = new RectF();
    private final float radius;
    private final float gap;
    private final float labelGap;
    private final float cellMax;

    private float gridLeft;
    private float gridTop;
    private float cell;

    public HeatmapView(@NonNull Context context) {
        this(context, null);
    }

    public HeatmapView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        int[] ramp = {R.color.bb_heat_0, R.color.bb_heat_1, R.color.bb_heat_2, R.color.bb_heat_3, R.color.bb_heat_4};
        for (int i = 0; i < heat.length; i++) {
            heat[i] = fillPaint(ramp[i]);
        }
        labelPaint = textPaint(R.style.TextAppearance_BBetter_Micro, 500);
        selectedPaint = strokePaint(R.color.bb_on_surface, R.dimen.chart_selected_stroke);
        radius = dimen(R.dimen.chart_cell_radius);
        gap = dimen(R.dimen.chart_cell_gap);
        labelGap = dimen(R.dimen.chart_label_gap);
        cellMax = dimen(R.dimen.chart_cell_max);
    }

    public void setData(@Nullable Data data) {
        this.data = data;
        onDataChanged();
    }

    @Override
    protected boolean hasData() {
        return data != null && data.rows > 0 && data.cols > 0;
    }

    @Override
    protected int markCount() {
        return data == null ? 1 : data.cols;
    }

    private float rowLabelWidth(@NonNull Data d) {
        if (d.rowLabels == null) {
            return 0f;
        }
        float w = 0f;
        for (String s : d.rowLabels) {
            w = Math.max(w, labelPaint.measureText(s));
        }
        return w + labelGap;
    }

    private boolean hasColLabels(@NonNull Data d) {
        for (String s : d.colLabels) {
            if (!s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private float colLabelHeight(@NonNull Data d) {
        return hasColLabels(d) ? textHeight(labelPaint) + labelGap : 0f;
    }

    private void layoutGrid(@NonNull Data d, int width) {
        gridLeft = getPaddingLeft() + rowLabelWidth(d);
        float available = width - getPaddingRight() - gridLeft;
        cell = Math.min(cellMax, GridGeometry.cellSize(available, d.cols, gap));
        gridTop = getPaddingTop() + (d.colLabelsOnTop ? colLabelHeight(d) : 0f);
    }

    @Override
    protected int desiredHeight(int width) {
        Data d = data;
        if (d == null || d.rows == 0 || d.cols == 0) {
            return super.desiredHeight(width);
        }
        layoutGrid(d, width);
        float grid = d.rows * cell + (d.rows - 1) * gap;
        return Math.round(getPaddingTop() + getPaddingBottom() + grid + colLabelHeight(d));
    }

    @Override
    protected void onDrawChart(@NonNull Canvas canvas) {
        Data d = data;
        if (d == null) {
            return;
        }
        layoutGrid(d, getWidth());
        int sel = selectedMark();
        for (int c = 0; c < d.cols; c++) {
            float p = markProgress(c);
            float x = GridGeometry.cellStart(c, gridLeft, cell, gap);
            for (int r = 0; r < d.rows; r++) {
                float v = d.values[r][c];
                if (Float.isNaN(v)) {
                    continue;
                }
                float y = GridGeometry.cellStart(r, gridTop, cell, gap);
                Paint paint = heat[GridGeometry.heatBucket(v, d.max)];
                int mark = r * d.cols + c;
                paint.setAlpha(Math.round(alphaFor(mark) * p));
                cellRect.set(x, y, x + cell, y + cell);
                canvas.drawRoundRect(cellRect, radius, radius, paint);
                if (mark == sel) {
                    canvas.drawRoundRect(cellRect, radius, radius, selectedPaint);
                }
            }
        }
        if (d.rowLabels != null) {
            labelPaint.setTextAlign(Paint.Align.LEFT);
            for (int r = 0; r < d.rows; r++) {
                float cy = GridGeometry.cellStart(r, gridTop, cell, gap) + cell / 2f;
                canvas.drawText(d.rowLabels[r], getPaddingLeft(), baselineAt(labelPaint, cy), labelPaint);
            }
        }
        float gridBottom = GridGeometry.cellStart(d.rows, gridTop, cell, gap) - gap;
        float baseline = d.colLabelsOnTop
                ? getPaddingTop() - labelPaint.ascent()
                : gridBottom + labelGap - labelPaint.ascent();
        float maxRight = getWidth() - getPaddingRight();
        for (int c = 0; c < d.cols; c++) {
            String label = d.colLabels[c];
            if (label.isEmpty()) {
                continue;
            }
            float x = GridGeometry.cellStart(c, gridLeft, cell, gap);
            float w = labelPaint.measureText(label);
            float left = d.colLabelsOnTop ? x : x + cell / 2f - w / 2f;
            left = Math.max(getPaddingLeft(), Math.min(maxRight - w, left));
            labelPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(label, left, baseline, labelPaint);
        }
    }

    @Override
    protected int markAt(float x, float y) {
        Data d = data;
        if (d == null) {
            return NO_MARK;
        }
        layoutGrid(d, getWidth());
        int c = GridGeometry.indexAt(x, gridLeft, cell, gap, d.cols);
        int r = GridGeometry.indexAt(y, gridTop, cell, gap, d.rows);
        if (c < 0 || r < 0 || Float.isNaN(d.values[r][c])) {
            return NO_MARK;
        }
        return r * d.cols + c;
    }

    @Override
    protected void markAnchor(int mark, @NonNull PointF out) {
        Data d = data;
        if (d == null) {
            return;
        }
        layoutGrid(d, getWidth());
        int r = mark / d.cols;
        int c = mark % d.cols;
        out.set(GridGeometry.cellStart(c, gridLeft, cell, gap) + cell / 2f, GridGeometry.cellStart(r, gridTop, cell, gap));
    }

    @Nullable
    @Override
    protected CharSequence bubbleText(int mark) {
        Data d = data;
        if (d == null) {
            return null;
        }
        int r = mark / d.cols;
        int c = mark % d.cols;
        return d.cellNames[r][c] + " · " + d.formatter.format(d.values[r][c]);
    }

    @NonNull
    @Override
    protected CharSequence summary() {
        Data d = data;
        if (d == null) {
            return "";
        }
        int bestR = 0;
        int bestC = 0;
        float best = -1f;
        for (int r = 0; r < d.rows; r++) {
            for (int c = 0; c < d.cols; c++) {
                if (d.values[r][c] > best) {
                    best = d.values[r][c];
                    bestR = r;
                    bestC = c;
                }
            }
        }
        return getContext().getString(R.string.chart_desc_heatmap, d.cellNames[bestR][bestC],
                d.formatter.format(Math.max(0f, best)));
    }
}
