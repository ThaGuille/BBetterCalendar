package com.example.bbettercalendar.debug;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.chart.BaseChartView;
import com.example.bbettercalendar.ui.common.chart.CalendarHeatmap;
import com.example.bbettercalendar.ui.common.chart.ChartFormat;
import com.example.bbettercalendar.ui.common.chart.ChartFormatter;
import com.example.bbettercalendar.ui.common.chart.ChartScale;
import com.example.bbettercalendar.ui.common.chart.CompareBarsView;
import com.example.bbettercalendar.ui.common.chart.DonutView;
import com.example.bbettercalendar.ui.common.chart.DumbbellRowView;
import com.example.bbettercalendar.ui.common.chart.HeatmapView;
import com.example.bbettercalendar.ui.common.chart.HistogramView;
import com.example.bbettercalendar.ui.common.chart.LimitBarView;
import com.example.bbettercalendar.ui.common.chart.MirrorBarsView;
import com.example.bbettercalendar.ui.common.chart.SparkBarsView;
import com.example.bbettercalendar.ui.common.chart.StackedBarsView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Canvas chart kit section of the debug gallery (plan mejora-visual u4), shown by
 * {@link DebugChartsGalleryActivity}: fills every view of {@code gallery_section_charts.xml} with the sample
 * data of mockups 11-18, 20 and 21 and leaves one chart without data. The adb recording
 * {@code mv-u4-charts} walks through it. Demo data only; nothing here touches the database.
 */
final class ChartGallerySection {

    private static final String[] DAY_LETTERS = {"L", "M", "X", "J", "V", "S", "D"};
    private static final String[] DAY_NAMES = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado",
            "Domingo"};

    private static final int LEGEND_PER_ROW = 3;

    private ChartGallerySection() {
    }

    static void bind(@NonNull Activity a) {
        bindCompare(a);
        bindSpark(a);
        bindHours(a);
        bindCalendar(a);
        bindSessions(a);
        bindDonut(a);
        bindHistogram(a);
        bindMirror(a);
        bindLimitRows(a);
        bindAppDaily(a);
        bindDumbbells(a);
        CompareBarsView empty = a.findViewById(R.id.galleryU4Empty);
        empty.setData(null);

        TextView selected = a.findViewById(R.id.galleryU4Selected);
        BaseChartView.OnMarkSelectedListener listener = (chart, mark, text) -> selected.setText(
                text == null ? "Toca una barra" : "Seleccionado: " + text.toString().replace('\n', ' '));
        int[] charts = {R.id.galleryU4Compare, R.id.galleryU4Spark, R.id.galleryU4Hours, R.id.galleryU4Calendar,
                R.id.galleryU4Sessions, R.id.galleryU4Donut, R.id.galleryU4Histogram, R.id.galleryU4Mirror,
                R.id.galleryU4AppDaily, R.id.galleryU4DumbbellVideos, R.id.galleryU4DumbbellSocial};
        for (int id : charts) {
            BaseChartView chart = a.findViewById(id);
            chart.setOnMarkSelectedListener(listener);
        }
        LinearLayout rows = a.findViewById(R.id.galleryU4LimitRows);
        for (int i = 0; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i) instanceof BaseChartView) {
                ((BaseChartView) rows.getChildAt(i)).setOnMarkSelectedListener(listener);
            }
        }
    }

    // ------------------------------------------------------------------ mockup 11

    private static void bindCompare(@NonNull Activity a) {
        float[] now = {96, 125, 56, 140, 108, 42, 70};
        float[] prev = {75, 85, 95, 65, 115, 28, 18};
        CompareBarsView view = a.findViewById(R.id.galleryU4Compare);
        view.setData(new CompareBarsView.Data(now, prev, DAY_LETTERS, 6, ChartScale.mean(now),
                ChartFormatter.MINUTES, "Esta semana", "Semana anterior"));
        legend(a, R.id.galleryU4CompareLegend, new int[]{R.color.bb_primary, R.color.bb_data_compare},
                new String[]{"Esta semana", "Semana anterior"});
    }

    private static void bindSpark(@NonNull Activity a) {
        float[] week = {96, 125, 56, 140, 108, 0, 70};
        SparkBarsView view = a.findViewById(R.id.galleryU4Spark);
        view.setData(new SparkBarsView.Data(week, DAY_NAMES, 6, ChartFormatter.MINUTES));
    }

    // ------------------------------------------------------------------ mockups 12 and 14

    private static void bindHours(@NonNull Activity a) {
        float[][] values = new float[7][HeatmapView.HOURS];
        for (int r = 0; r < 7; r++) {
            boolean weekend = r >= 5;
            for (int c = 0; c < HeatmapView.HOURS; c++) {
                float h = HeatmapView.FIRST_HOUR + c;
                double morning = 55 * Math.exp(-Math.pow(h - 10, 2) / 2.5);
                double evening = 40 * Math.exp(-Math.pow(h - 17.5, 2) / 3.5);
                double v = morning + evening + ((r * 31 + c * 17) % 7) * 2;
                if (r == 1) {
                    v *= 1.2;
                }
                if (weekend) {
                    v *= 0.35;
                }
                if (h >= 21 || h < 7) {
                    v *= 0.1;
                }
                values[r][c] = (float) Math.round(v);
            }
        }
        HeatmapView view = a.findViewById(R.id.galleryU4Hours);
        view.setData(HeatmapView.Data.hours(values, DAY_LETTERS, DAY_NAMES, ChartFormatter.MINUTES));
        legend(a, R.id.galleryU4HoursLegend, new int[]{R.color.bb_heat_1, R.color.bb_heat_3, R.color.bb_heat_4},
                new String[]{"Poco", "Bastante", "Mucho"});
    }

    private static void bindCalendar(@NonNull Activity a) {
        LocalDate today = LocalDate.now();
        int days = 26 * 7;
        float[] daily = new float[days];
        for (int i = 0; i < days; i++) {
            LocalDate d = today.minusDays(days - 1 - i);
            // Integer hash of the day index: a fixed, irregular pattern (a linear one draws stripes).
            int x = (i + 1) * 0x9E3779B1;
            x ^= x >>> 15;
            x *= 0x85EBCA6B;
            x ^= x >>> 13;
            int seed = (x & 0x7fffffff) % 17;
            boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
            int threshold = weekend ? 12 : 6;
            daily[i] = seed < threshold ? 0f : (seed - threshold + 1) * 18f;
        }
        Locale locale = a.getResources().getConfiguration().getLocales().get(0);
        HeatmapView view = a.findViewById(R.id.galleryU4Calendar);
        view.setData(HeatmapView.Data.calendar(CalendarHeatmap.build(today, daily, 26), locale,
                ChartFormatter.MINUTES));
    }

    // ------------------------------------------------------------------ mockups 15 to 18

    private static void bindSessions(@NonNull Activity a) {
        float[][] values = {{5, 0}, {6, 1}, {3, 1}, {7, 0}, {6, 1}, {1, 0}, {3, 0}};
        int[] colors = {R.color.bb_primary, R.color.bb_danger};
        String[] names = {"Completadas", "Abandonadas"};
        StackedBarsView view = a.findViewById(R.id.galleryU4Sessions);
        view.setData(new StackedBarsView.Data(values, colors, names, DAY_LETTERS, false, ChartFormatter.COUNT));
        legend(a, R.id.galleryU4SessionsLegend, colors, names);
    }

    private static void bindDonut(@NonNull Activity a) {
        float[] minutes = {310, 220, 140, 90};
        String[] names = {"Trabajo de fin de grado", "Oposición inglés", "App BBetter", "Lectura"};
        DonutView.Data data = new DonutView.Data(minutes, names, null, "Otros", "total", ChartFormatter.MINUTES);
        DonutView view = a.findViewById(R.id.galleryU4Donut);
        view.setData(data);
        LinearLayout box = a.findViewById(R.id.galleryU4DonutLegend);
        // Name on one line (two at most when long), value under it: the column beside a 140dp donut is
        // too narrow on a 360dp screen for name and value side by side without breaking words.
        int pad = dimen(a, R.dimen.spacing_xs);
        int indent = dimen(a, R.dimen.gallery_legend_dot) + dimen(a, R.dimen.spacing_sm);
        for (int i = 0; i < data.sliceCount(); i++) {
            TextView name = dotLabel(a, data.sliceColor(i), data.sliceName(i), R.style.TextAppearance_BBetter_Body);
            name.setPadding(0, pad, 0, 0);
            box.addView(name);
            TextView value = text(a, ChartFormat.hoursMinutes(data.sliceValue(i))
                    + " · " + ChartFormat.percent(data.slicePercent(i)), R.style.TextAppearance_BBetter_Caption);
            value.setFontFeatureSettings("tnum");
            value.setPaddingRelative(indent, 0, 0, pad);
            box.addView(value);
        }
    }

    private static void bindHistogram(@NonNull Activity a) {
        int[] counts = {2, 5, 9, 14, 6, 2};
        String[] labels = {"10 m", "15 m", "20 m", "25 m", "30 m", "45+"};
        HistogramView view = a.findViewById(R.id.galleryU4Histogram);
        view.setData(new HistogramView.Data(counts, labels, v -> Math.round(v) + " sesiones"));
    }

    private static void bindMirror(@NonNull Activity a) {
        float[] focus = {100, 125, 75, 140, 115, 60, 90};
        float[] phone = {80, 55, 105, 40, 70, 120, 92};
        MirrorBarsView view = a.findViewById(R.id.galleryU4Mirror);
        view.setData(new MirrorBarsView.Data(focus, phone, DAY_LETTERS, "Enfoque", "Apps limitadas",
                ChartFormatter.MINUTES));
        legend(a, R.id.galleryU4MirrorLegend, new int[]{R.color.bb_primary, R.color.bb_data_compare},
                new String[]{"Enfoque (arriba)", "Apps limitadas (abajo)"});
    }

    // ------------------------------------------------------------------ mockups 20 and 21

    private static void bindLimitRows(@NonNull Activity a) {
        String[] names = {"Vídeos", "Red social", "Juego", "Chat", "Navegador"};
        float[] used = {52, 48, 44, 28, 18};
        float[] limits = {60, 45, 0, 0, 0};
        float scaleMax = ChartScale.max(used, limits);
        LinearLayout box = a.findViewById(R.id.galleryU4LimitRows);
        int gap = dimen(a, R.dimen.spacing_sm);
        for (int i = 0; i < names.length; i++) {
            LinearLayout header = new LinearLayout(a);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setPadding(0, gap, 0, dimen(a, R.dimen.spacing_xs));
            TextView name = text(a, names[i], R.style.TextAppearance_BBetter_BodyStrong);
            header.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            String value = ChartFormat.hoursMinutes(used[i])
                    + (limits[i] > 0 ? " / " + ChartFormat.hoursMinutes(limits[i]) : "");
            TextView amount = text(a, value, R.style.TextAppearance_BBetter_Body);
            amount.setFontFeatureSettings("tnum");
            header.addView(amount);
            box.addView(header);
            LimitBarView bar = new LimitBarView(a);
            bar.setId(View.generateViewId());
            bar.setData(new LimitBarView.Data(used[i], limits[i], scaleMax, ChartFormatter.MINUTES));
            box.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private static void bindAppDaily(@NonNull Activity a) {
        float[][] values = {
                {60, 40, 35, 40}, {40, 35, 10, 25}, {85, 50, 40, 40}, {25, 20, 15, 20},
                {55, 45, 30, 35}, {105, 80, 40, 45}, {70, 45, 40, 40}};
        int[] colors = {R.color.bb_cat_1, R.color.bb_cat_2, R.color.bb_cat_3, R.color.bb_cat_4};
        String[] names = {"Vídeos", "Red social", "Juego", "Otras"};
        StackedBarsView view = a.findViewById(R.id.galleryU4AppDaily);
        view.setData(new StackedBarsView.Data(values, colors, names, DAY_LETTERS, true, ChartFormatter.MINUTES));
        legend(a, R.id.galleryU4AppDailyLegend, colors, names);
    }

    private static void bindDumbbells(@NonNull Activity a) {
        float[] before = {92, 84};
        float[] after = {52, 48};
        float scaleMax = ChartScale.max(before, after);
        DumbbellRowView videos = a.findViewById(R.id.galleryU4DumbbellVideos);
        videos.setData(new DumbbellRowView.Data("Vídeos", before[0], after[0], scaleMax, "7 días antes",
                "Desde el límite", ChartFormatter.MINUTES));
        DumbbellRowView social = a.findViewById(R.id.galleryU4DumbbellSocial);
        social.setData(new DumbbellRowView.Data("Red social", before[1], after[1], scaleMax, "7 días antes",
                "Desde el límite", ChartFormatter.MINUTES));
        legend(a, R.id.galleryU4DumbbellLegend, new int[]{R.color.bb_on_surface_muted, R.color.bb_primary},
                new String[]{"7 días antes", "Desde el límite"});
    }

    // ------------------------------------------------------------------ legend helpers

    private static void legend(@NonNull Activity a, @IdRes int boxId, @NonNull @ColorRes int[] colors,
                               @NonNull String[] names) {
        LinearLayout box = a.findViewById(boxId);
        LinearLayout row = null;
        for (int i = 0; i < names.length; i++) {
            // At most 3 items per line: a 360dp screen has no room for 4 labels side by side.
            if (i % LEGEND_PER_ROW == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row);
            }
            TextView item = dotLabel(a, colors[i], names[i], R.style.TextAppearance_BBetter_Caption);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dimen(a, R.dimen.spacing_md));
            row.addView(item, lp);
        }
    }

    @NonNull
    private static TextView dotLabel(@NonNull Activity a, @ColorRes int color, @NonNull String label,
                                     @StyleRes int style) {
        TextView tv = text(a, label, style);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ContextCompat.getColor(a, color));
        int size = dimen(a, R.dimen.gallery_legend_dot);
        dot.setSize(size, size);
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
        tv.setCompoundDrawablePadding(dimen(a, R.dimen.spacing_sm));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    @NonNull
    private static TextView text(@NonNull Activity a, @NonNull String value, @StyleRes int style) {
        TextView tv = new TextView(a);
        TextViewCompat.setTextAppearance(tv, style);
        tv.setText(value);
        return tv;
    }

    private static int dimen(@NonNull Activity a, int res) {
        return a.getResources().getDimensionPixelSize(res);
    }
}
