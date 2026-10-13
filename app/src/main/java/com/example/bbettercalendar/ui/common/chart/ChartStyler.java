package com.example.bbettercalendar.ui.common.chart;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FontHelper;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The one place that knows how a BBetter chart looks (plan migracion-visual-stitch, u10).
 * Every MPAndroidChart (v3.1.0) in the app is built by its screen and then dressed here: colours
 * come from the {@code bb_*} tokens, the axis font from {@link FontHelper}, geometry from the
 * Stitch HTML (13-progress-final, 14-screen-time-blocking-final, 10-app-detail-instagram).
 * No other Java class may read a chart colour.
 *
 * <p>Typical use: {@code ChartStyler s = new ChartStyler(ctx);} → style the data sets
 * ({@link #styleHeroLine}, {@link #styleCompareLine}, {@link #styleBars}, {@link #styleLimitBars},
 * {@link #styleUsageLine}, {@link #focusBandsData}) → {@code chart.setData(...)} →
 * {@link #styleChart} → {@link #fitYAxis}. Chart look: right axis with 3 labels, dashed hairline
 * grid on {@code bb_outline}, solid baseline, no left axis, no legend, no zoom/highlight.
 *
 * <p>All lengths below are dp: MPAndroidChart converts line widths, radii and text sizes itself;
 * dash lengths are converted here ({@link #px}).
 */
public final class ChartStyler {

    /** Colour role of a series. */
    public enum Tone {
        /** Sage: the user's own focus data. */
        PRIMARY,
        /** Dusk: comparison series, phone usage, "within limit". */
        COMPARE,
        /** Ember: fails, "over limit". */
        DANGER,
        /** Honey: earned moments only (a single highlighted point or bar). */
        REWARD
    }

    // Geometría (dp) sacada de los SVG de html 13 / 14.
    private static final float LINE_WIDTH = 2.5f;
    private static final float COMPARE_LINE_WIDTH = 1.8f;
    private static final float COMPARE_DASH = 4f;
    private static final float MARKER_RADIUS = 4.2f;     // r 3.2 + stroke 2 (html 13)
    private static final float MARKER_HOLE_RADIUS = 2.2f;
    private static final int MAX_MARKERS = 12;           // más puntos que esto: sin marcadores
    private static final float GRID_WIDTH = 0.8f;
    private static final float GRID_DASH = 2f;
    private static final float GRID_GAP = 3f;
    private static final float LIMIT_WIDTH = 1f;
    private static final float LIMIT_DASH = 4f;
    private static final float LIMIT_GAP = 3f;
    private static final int Y_LABELS = 3;               // 0 / mitad / máximo
    private static final float BAND_WIDTH = 1f;          // barras contiguas = banda continua
    private static final float BAR_WIDTH = 0.6f;
    private static final float EXTRA_TOP = 10f;          // sitio para el halo del último punto
    private static final float EXTRA_BOTTOM = 4f;
    private static final float Y_LABEL_GAP = 12f;        // dp entre el área de datos y "20h"
    private static final float LABEL_SIZE_DELTA = 2f;    // etiqueta FOCUS = eje − 2
    // Pasos «bonitos» enteros (× 10ⁿ) para la mitad del eje Y: 0 / paso / 2·paso.
    private static final double[] NICE_STEPS = {1, 2, 3, 4, 5, 6, 8, 10};

    private final Context context;
    private final float density;
    private final int primary;
    private final int compare;
    private final int danger;
    private final int reward;
    private final int axisText;
    private final int outline;
    private final int cardSurface;
    private final Typeface axisTypeface;
    private final Typeface labelTypeface;
    private final Typeface bandTypeface;
    private final float axisTextSize;

    public ChartStyler(@NonNull Context context) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
        primary = ContextCompat.getColor(context, R.color.bb_primary);
        compare = ContextCompat.getColor(context, R.color.bb_data_compare);
        danger = ContextCompat.getColor(context, R.color.bb_danger);
        reward = ContextCompat.getColor(context, R.color.bb_accent_reward);
        axisText = ContextCompat.getColor(context, R.color.bb_on_surface_muted);
        outline = ContextCompat.getColor(context, R.color.bb_outline);
        cardSurface = ContextCompat.getColor(context, R.color.bb_surface_card);
        axisTypeface = FontHelper.appTypeface(context, 500);
        labelTypeface = FontHelper.appTypeface(context, 600);
        bandTypeface = FontHelper.appTypeface(context, 700);
        // text_micro está en sp; MPAndroidChart quiere dp (los convierte él): px / density
        // conserva la escala de fuente del usuario.
        axisTextSize = context.getResources().getDimension(R.dimen.text_micro) / density;
    }

    /** The colour of a tone ({@code @ColorInt}), for a screen-side legend dot next to a chart. */
    public int color(@NonNull Tone tone) {
        switch (tone) {
            case COMPARE:
                return compare;
            case DANGER:
                return danger;
            case REWARD:
                return reward;
            case PRIMARY:
            default:
                return primary;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Chart level
    // ------------------------------------------------------------------------------------------

    /**
     * Axes, grid, interaction and empty state of a line / bar / combined chart. Call after
     * {@code setData}. {@code xLabels} = one label per x index (null keeps numeric labels).
     */
    public void styleChart(@NonNull BarLineChartBase<?> chart, @Nullable String[] xLabels) {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setScaleEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setPinchZoom(false);
        // Gráficos de sólo lectura: sin resaltado al tocar/arrastrar (dentro de un carrusel
        // aparecía la línea vertical de highlight al deslizar).
        chart.setHighlightPerTapEnabled(false);
        chart.setHighlightPerDragEnabled(false);
        chart.setExtraOffsets(0f, EXTRA_TOP, 0f, EXTRA_BOTTOM);
        chart.getLegend().setEnabled(false);
        styleEmptyState(chart);

        chart.getAxisLeft().setEnabled(false);

        YAxis y = chart.getAxisRight();
        y.setEnabled(true);
        styleAxisText(y);
        y.setDrawAxisLine(false);
        y.setDrawGridLines(true);
        y.setGridColor(outline);
        y.setGridLineWidth(GRID_WIDTH);
        y.enableGridDashedLine(px(GRID_DASH), px(GRID_GAP), 0f);
        // Línea base sólida (la rejilla es discontinua), como el y=110 de html 13.
        y.setDrawZeroLine(true);
        y.setZeroLineColor(outline);
        y.setZeroLineWidth(GRID_WIDTH);
        y.setAxisMinimum(0f);
        y.setLabelCount(Y_LABELS, true);
        y.setDrawLimitLinesBehindData(false);
        // Separa las etiquetas del borde del área de datos (el halo del último punto las pisaba).
        y.setXOffset(Y_LABEL_GAP);

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        styleAxisText(x);
        x.setDrawGridLines(false);
        x.setDrawAxisLine(false);
        x.setGranularity(1f);
        x.setGranularityEnabled(true);
        if (xLabels != null) {
            x.setValueFormatter(new IndexAxisValueFormatter(xLabels));
        }
        roundBars(chart);
    }

    /**
     * Rounded bars and FOCUS bands (html 10 / 14): MPAndroidChart draws square rects, so every
     * bar chart (plain, stacked, grouped, or the bar layer of a combined chart) gets the custom
     * renderer. Idempotent; safe before or after {@code setData}.
     */
    private void roundBars(@NonNull BarLineChartBase<?> chart) {
        if (chart instanceof CombinedChart) {
            if (!(chart.getRenderer() instanceof RoundedCombinedChartRenderer)) {
                chart.setRenderer(new RoundedCombinedChartRenderer((CombinedChart) chart,
                        chart.getAnimator(), chart.getViewPortHandler()));
                // CombinedChart.setData builds the sub-renderers and buffers; if data is already
                // there, do it now for the new renderer.
                if (chart.getData() != null) {
                    ((RoundedCombinedChartRenderer) chart.getRenderer()).initBuffers();
                }
            }
        } else if (chart instanceof BarChart) {
            if (!(chart.getRenderer() instanceof RoundedBarChartRenderer)) {
                chart.setRenderer(new RoundedBarChartRenderer((BarChart) chart,
                        chart.getAnimator(), chart.getViewPortHandler()));
                if (chart.getData() != null) {
                    chart.getRenderer().initBuffers();
                }
            }
        }
    }

    /** "No data" text in the app font and muted colour (shown when the chart has no data). */
    public void styleEmptyState(@NonNull Chart<?> chart) {
        chart.getDescription().setEnabled(false);
        chart.setNoDataText(context.getString(R.string.chart_no_data));
        chart.setNoDataTextColor(axisText);
        chart.setNoDataTextTypeface(axisTypeface);
    }

    /**
     * Fixes the right axis to {@code 0 … niceMax(dataMax)} with 3 whole labels. Call after
     * {@link #styleChart}. Use it whenever the data are counts/minutes, so labels never show
     * fractions like "0.4".
     */
    public void fitYAxis(@NonNull BarLineChartBase<?> chart, float dataMax) {
        YAxis y = chart.getAxisRight();
        y.setAxisMinimum(0f);
        y.setAxisMaximum(niceMax(dataMax));
        y.setLabelCount(Y_LABELS, true);
    }

    /** Right-axis label formatter, e.g. {@code unitFormatter("h")} → "0h / 10h / 20h". */
    public void setYAxisFormatter(@NonNull BarLineChartBase<?> chart, @NonNull ValueFormatter f) {
        chart.getAxisRight().setValueFormatter(f);
    }

    /** Whole-number formatter with a unit suffix ("m", "h", …) for axis labels. */
    @NonNull
    public static ValueFormatter unitFormatter(@NonNull final String suffix) {
        return new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return String.format(Locale.getDefault(), "%d%s", Math.round(value), suffix);
            }
        };
    }

    /**
     * Smallest "nice" top (2 × {1,2,3,4,5,6,8}·10ⁿ) ≥ {@code dataMax}, so the 3 labels (0, half, top) are
     * whole numbers. {@code dataMax <= 0} → 2.
     */
    public static float niceMax(float dataMax) {
        double half = Math.max(dataMax, 0f) / 2.0;
        double step = 1;
        if (half > 1) {
            double magnitude = Math.pow(10, Math.floor(Math.log10(half)));
            double norm = half / magnitude;
            step = 10 * magnitude;
            for (double nice : NICE_STEPS) {
                if (norm <= nice + 1e-9) {
                    step = nice * magnitude;
                    break;
                }
            }
        }
        return (float) (step * 2);
    }

    /**
     * Legend for the few charts that need one in-chart (two series with no screen-side legend):
     * circles in the tone colours, app font, muted text. The Stitch screens draw their legend
     * above the chart instead — prefer that and leave this off.
     */
    public void styleLegend(@NonNull Chart<?> chart) {
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setTextColor(axisText);
        legend.setTypeface(axisTypeface);
        legend.setTextSize(axisTextSize);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setDrawInside(false);
    }

    /**
     * Combined "usage line + FOCUS bands" chart (html 14): bands behind the line, band label
     * inside the top of the band. Call besides {@link #styleChart}.
     */
    public void styleBandsChart(@NonNull CombinedChart chart) {
        chart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE});
        chart.setDrawValueAboveBar(false);
        chart.setDrawBarShadow(false);
    }

    // ------------------------------------------------------------------------------------------
    // Lines
    // ------------------------------------------------------------------------------------------

    /**
     * Hero line (html 13): sage 2.5dp smooth line, gradient wash ({@code bg_chart_fill_primary}),
     * hollow markers when there are few points, and the latest point in honey with a halo.
     */
    @NonNull
    public LineDataSet styleHeroLine(@NonNull LineDataSet set) {
        return styleHeroLine(set, set.getEntryCount() - 1);
    }

    /**
     * Hero line whose "current" point is {@code latestIndex} instead of the last entry (e.g. a
     * week range whose trailing days are still in the future). {@code -1} = no honey marker.
     */
    @NonNull
    public LineDataSet styleHeroLine(@NonNull LineDataSet set, int latestIndex) {
        styleLine(set, Tone.PRIMARY);
        set.setDrawFilled(true);
        set.setFillDrawable(drawable(R.drawable.bg_chart_fill_primary));
        if (latestIndex >= 0 && latestIndex < set.getEntryCount()) {
            markEntry(set.getEntryForIndex(latestIndex));
            set.setDrawIcons(true);
        }
        return set;
    }

    /** Plain line in a tone: 2.5dp, smooth, hollow markers when few points, no fill. */
    @NonNull
    public LineDataSet styleLine(@NonNull LineDataSet set, @NonNull Tone tone) {
        int c = color(tone);
        set.setColor(c);
        set.setLineWidth(LINE_WIDTH);
        set.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);   // curva suave que no pasa por debajo de 0
        set.setDrawValues(false);
        set.setDrawFilled(false);
        set.setDrawHorizontalHighlightIndicator(false);
        set.setDrawVerticalHighlightIndicator(false);
        set.setDrawCircles(set.getEntryCount() <= MAX_MARKERS);
        set.setCircleColor(c);
        set.setCircleRadius(MARKER_RADIUS);
        set.setDrawCircleHole(true);
        set.setCircleHoleRadius(MARKER_HOLE_RADIUS);
        set.setCircleHoleColor(cardSurface);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        return set;
    }

    /** Comparison series (html 13 "prev 12 wks"): dusk, 1.8dp, dashed, no fill, no markers. */
    @NonNull
    public LineDataSet styleCompareLine(@NonNull LineDataSet set) {
        styleLine(set, Tone.COMPARE);
        set.setLineWidth(COMPARE_LINE_WIDTH);
        set.enableDashedLine(px(COMPARE_DASH), px(COMPARE_DASH), 0f);
        set.setDrawCircles(false);
        return set;
    }

    /**
     * Usage line of the "today by hour" chart (html 14): dusk 2.5dp with a dusk wash, no markers.
     * Highlight its peak with {@link #markEntry}.
     */
    @NonNull
    public LineDataSet styleUsageLine(@NonNull LineDataSet set) {
        styleLine(set, Tone.COMPARE);
        set.setDrawCircles(false);
        set.setDrawFilled(true);
        set.setFillDrawable(drawable(R.drawable.bg_chart_fill_compare));
        return set;
    }

    /** Puts the honey halo marker on the last entry of {@code set}. */
    public void markLatest(@NonNull LineDataSet set) {
        int n = set.getEntryCount();
        if (n == 0) return;
        markEntry(set.getEntryForIndex(n - 1));
        set.setDrawIcons(true);
    }

    /**
     * Puts the honey halo marker on one entry (e.g. the usage peak). The data set must have
     * {@code setDrawIcons(true)} — {@link #markLatest} does it; otherwise call it yourself.
     */
    public void markEntry(@NonNull Entry entry) {
        entry.setIcon(drawable(R.drawable.bg_chart_point_reward));
    }

    // ------------------------------------------------------------------------------------------
    // Bars
    // ------------------------------------------------------------------------------------------

    /** Single-colour bars in a tone (no values, no highlight). Bar width is the caller's. */
    @NonNull
    public BarDataSet styleBars(@NonNull BarDataSet set, @NonNull Tone tone) {
        set.setColor(color(tone));
        set.setDrawValues(false);
        set.setHighLightAlpha(0);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        return set;
    }

    /** Default bar width for one bar per x slot. */
    public static float barWidth() {
        return BAR_WIDTH;
    }

    /**
     * Stacked "within / over limit" bars (html 10): dusk under the limit, ember above. Build the
     * entries with {@link #splitAtLimit}; add the dashed line with {@link #addLimitLine}.
     */
    @NonNull
    public BarDataSet styleLimitBars(@NonNull BarDataSet set) {
        styleBars(set, Tone.COMPARE);
        set.setColors(compare, danger);
        return set;
    }

    /** {@code value} split into {@code [within, over]} for a stacked {@link BarEntry}. */
    @NonNull
    public static float[] splitAtLimit(float value, float limit) {
        float within = Math.min(value, limit);
        return new float[]{Math.max(within, 0f), Math.max(value - limit, 0f)};
    }

    /**
     * Dashed limit line with a label at its top-left ("Limit 45m"); {@code formattedLimit} is
     * the value as the user reads it ("45m"). Replaces any previous limit line.
     */
    @NonNull
    public LimitLine addLimitLine(@NonNull BarLineChartBase<?> chart, float limit,
                                  @NonNull String formattedLimit) {
        LimitLine line = new LimitLine(limit,
                context.getString(R.string.chart_limit_label, formattedLimit));
        line.setLineColor(axisText);
        line.setLineWidth(LIMIT_WIDTH);
        line.enableDashedLine(px(LIMIT_DASH), px(LIMIT_GAP), 0f);
        line.setTextColor(axisText);
        line.setTypeface(labelTypeface);
        line.setTextSize(axisTextSize - 1f);
        line.setLabelPosition(LimitLine.LimitLabelPosition.LEFT_TOP);
        YAxis y = chart.getAxisRight();
        y.removeAllLimitLines();
        y.addLimitLine(line);
        // Que el límite quepa aunque ningún día lo alcance (el auto-rango ignora las LimitLine).
        float current = y.isAxisMaxCustom() ? y.getAxisMaximum() : 0f;
        if (current < limit) {
            float dataMax = chart.getData() != null ? chart.getData().getYMax() : 0f;
            fitYAxis(chart, Math.max(limit, dataMax));
        }
        return line;
    }

    /**
     * FOCUS bands (html 14) as full-height translucent sage bars, one per x slot where
     * {@code inFocus[x]} is true, labelled once per run. {@code top} = the y-axis maximum
     * (use {@link #niceMax} of the line's maximum and pass the same value to {@link #fitYAxis}).
     */
    @NonNull
    public BarData focusBandsData(@NonNull boolean[] inFocus, float top) {
        List<BarEntry> entries = new ArrayList<>();
        for (int x = 0; x < inFocus.length; x++) {
            if (!inFocus[x]) continue;
            boolean runStart = x == 0 || !inFocus[x - 1];
            // El data no nulo marca el primer tramo de cada banda: sólo ahí se pinta "FOCUS".
            entries.add(new BarEntry(x, top, runStart ? Boolean.TRUE : null));
        }
        final String label = context.getString(R.string.chart_band_focus);
        BarDataSet set = new BandBarDataSet(entries, label);
        set.setColor(ContextCompat.getColor(context, R.color.bb_chart_band));
        set.setHighLightAlpha(0);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        set.setDrawValues(true);
        set.setValueTextColor(primary);
        set.setValueTypeface(bandTypeface);
        set.setValueTextSize(Math.max(axisTextSize - LABEL_SIZE_DELTA, 6f));
        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getBarLabel(BarEntry e) {
                return e.getData() != null ? label : "";
            }
        });
        BarData data = new BarData(set);
        data.setBarWidth(BAND_WIDTH);
        return data;
    }

    // ------------------------------------------------------------------------------------------

    private void styleAxisText(@NonNull AxisBase axis) {
        axis.setTextColor(axisText);
        axis.setTypeface(axisTypeface);
        axis.setTextSize(axisTextSize);
    }

    @Nullable
    private Drawable drawable(@DrawableRes int res) {
        Drawable d = ContextCompat.getDrawable(context, res);
        return d != null ? d.mutate() : null;
    }

    private float px(float dp) {
        return dp * density;
    }
}
