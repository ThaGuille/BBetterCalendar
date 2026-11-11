package com.example.bbettercalendar.ui.progress;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.stats.HourHeatmapCalculator;
import com.example.bbettercalendar.stats.InsightPhrases;
import com.example.bbettercalendar.stats.ProjectMinutes;
import com.example.bbettercalendar.stats.SessionStats;
import com.example.bbettercalendar.ui.common.chart.CalendarHeatmap;
import com.example.bbettercalendar.ui.common.chart.ChartFormat;
import com.example.bbettercalendar.ui.common.chart.ChartFormatter;
import com.example.bbettercalendar.ui.common.chart.ChartStyler;
import com.example.bbettercalendar.ui.common.chart.CompareBarsView;
import com.example.bbettercalendar.ui.common.chart.DonutView;
import com.example.bbettercalendar.ui.common.chart.HeatmapView;
import com.example.bbettercalendar.ui.common.chart.HistogramView;
import com.example.bbettercalendar.ui.common.chart.MirrorBarsView;
import com.example.bbettercalendar.ui.common.chart.StackedBarsView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pinta las 8 tarjetas de «Todo tu progreso» (mejora-visual u8) a partir de un {@link AllProgressData}.
 * Cada tarjeta es un include de {@code item_chart_card.xml}: titulo, subtitulo, cifra + delta, grafico,
 * leyenda y frase. Los graficos se crean una vez y solo cambian sus datos. Sin datos, el grafico muestra su
 * propio estado vacio y la cifra, la leyenda y la frase se ocultan (nunca una frase inventada).
 */
final class AllProgressCards {

    private static final int LEGEND_PER_ROW = 3;
    private static final int[] BUCKET_MIN = SessionStats.BUCKET_MIN;

    private final Context ctx;
    private final Resources res;
    private final Locale locale;
    private final InsightPhrases phrases;

    private final Card focus;
    private final Card hours;
    private final Card trend;
    private final Card consistency;
    private final Card sessions;
    private final Card projects;
    private final Card durations;
    private final Card phone;

    private final CompareBarsView focusChart;
    private final HeatmapView hoursChart;
    private final HeatmapView consistencyChart;
    private final StackedBarsView sessionsChart;
    private final DonutView donutChart;
    private final LinearLayout donutLegend;
    private final HistogramView durationsChart;
    private final MirrorBarsView phoneChart;

    /** Identidad de la parte fija ya pintada: no se repinta al cambiar de periodo. */
    private Object fixedRendered;

    AllProgressCards(@NonNull View root) {
        ctx = root.getContext();
        res = ctx.getResources();
        locale = res.getConfiguration().getLocales().get(0);
        phrases = new InsightPhrases(res);

        focus = new Card(root, R.id.allProgressCardFocusByDay);
        hours = new Card(root, R.id.allProgressCardBestHours);
        trend = new Card(root, R.id.allProgressCard12Weeks);
        consistency = new Card(root, R.id.allProgressCardConsistency);
        sessions = new Card(root, R.id.allProgressCardSessions);
        projects = new Card(root, R.id.allProgressCardProjects);
        durations = new Card(root, R.id.allProgressCardDurations);
        phone = new Card(root, R.id.allProgressCardFocusVsPhone);

        focusChart = new CompareBarsView(ctx);
        focus.chart.addView(focusChart, wrap());
        hoursChart = new HeatmapView(ctx);
        hours.chart.addView(hoursChart, wrap());
        consistencyChart = new HeatmapView(ctx);
        consistency.chart.addView(consistencyChart, wrap());
        sessionsChart = new StackedBarsView(ctx);
        sessions.chart.addView(sessionsChart, wrap());
        durationsChart = new HistogramView(ctx);
        durations.chart.addView(durationsChart, wrap());
        phoneChart = new MirrorBarsView(ctx);
        phone.chart.addView(phoneChart, wrap());

        LinearLayout donutRow = new LinearLayout(ctx);
        donutRow.setOrientation(LinearLayout.HORIZONTAL);
        donutRow.setGravity(Gravity.CENTER_VERTICAL);
        donutChart = new DonutView(ctx);
        int size = dimen(R.dimen.chart_donut_size);
        donutRow.addView(donutChart, new LinearLayout.LayoutParams(size, size));
        donutLegend = new LinearLayout(ctx);
        donutLegend.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams legendLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        legendLp.setMarginStart(dimen(R.dimen.spacing_md));
        donutRow.addView(donutLegend, legendLp);
        projects.chart.addView(donutRow, wrap());

        hours.title.setText(R.string.all_progress_title_best_hours);
        hours.setSubtitle(R.string.all_progress_last_90_days);
        trend.title.setText(R.string.all_progress_title_12_weeks);
        consistency.title.setText(R.string.all_progress_title_consistency);
        consistency.setSubtitle(R.string.all_progress_last_26_weeks);
        sessions.title.setText(R.string.all_progress_title_sessions);
        projects.title.setText(R.string.all_progress_title_projects);
        durations.title.setText(R.string.all_progress_title_durations);
        durations.setSubtitle(R.string.all_progress_last_90_days);
        phone.title.setText(R.string.all_progress_title_focus_vs_phone);
    }

    void render(@NonNull AllProgressData d) {
        renderFocus(d);
        renderSessions(d);
        renderProjects(d);
        renderPhone(d);
        if (fixedRendered != d.hours) {
            fixedRendered = d.hours;
            renderHours(d);
            renderTrend(d);
            renderConsistency(d);
            renderDurations(d);
        }
    }

    // ------------------------------------------------------------------ 1 · Enfoque por dia

    private void renderFocus(AllProgressData d) {
        Granularity g = d.range.granularity;
        focus.title.setText(g == Granularity.DAY ? R.string.all_progress_title_focus_hour
                : g == Granularity.WEEK ? R.string.all_progress_title_focus_day
                : g == Granularity.MONTH ? R.string.all_progress_title_focus_week
                : R.string.all_progress_title_focus_month);
        float total = sum(d.focusCurrent);
        if (total <= 0f && sum(d.focusPrevious) <= 0f) {
            focusChart.setData(null);
            focus.clearBody();
            return;
        }
        int elapsed = Math.max(1, Math.min(d.focusElapsed, d.focusCurrent.length));
        float curSoFar = sum(d.focusCurrent, elapsed);
        float prevSoFar = sum(d.focusPrevious, elapsed);
        float average = curSoFar > 0f ? curSoFar / elapsed : Float.NaN;
        String curName = res.getString(g == Granularity.DAY ? R.string.all_progress_current_day
                : g == Granularity.WEEK ? R.string.all_progress_current_week
                : g == Granularity.MONTH ? R.string.all_progress_current_month
                : R.string.all_progress_current_year);
        String prevName = res.getString(g == Granularity.DAY ? R.string.all_progress_previous_day
                : g == Granularity.WEEK ? R.string.all_progress_previous_week
                : g == Granularity.MONTH ? R.string.all_progress_previous_month
                : R.string.all_progress_previous_year);
        String[] labels = d.buckets.labels(locale, res.getString(R.string.all_progress_week_prefix),
                dayLetters());
        focusChart.setData(new CompareBarsView.Data(d.focusCurrent, d.focusPrevious, labels,
                d.focusHighlight, average, ChartFormatter.MINUTES, curName, prevName));
        focus.showFigure(ChartFormat.hoursMinutes(total), null,
                percentDelta(prevSoFar, curSoFar, R.string.all_progress_delta_up,
                        R.string.all_progress_delta_down));
        focus.setLegend(new int[]{R.color.bb_primary, R.color.bb_data_compare},
                new String[]{curName, prevName});

        String sentence;
        switch (g) {
            case DAY:
                sentence = phrases.bestHour(d.focusCurrent, ChartFormatter.MINUTES);
                break;
            case WEEK:
                sentence = phrases.bestDay(d.focusCurrent, dayNames(false), ChartFormatter.MINUTES);
                break;
            case MONTH:
                sentence = phrases.bestWeek(d.focusCurrent, ChartFormatter.MINUTES);
                break;
            case YEAR:
            default:
                sentence = phrases.bestMonth(d.focusCurrent, monthNames(), ChartFormatter.MINUTES);
                break;
        }
        focus.setSentence(sentence);
    }

    // ------------------------------------------------------------------ 2 · Tus mejores horas

    private void renderHours(AllProgressData d) {
        HourHeatmapCalculator.Result r = d.hours;
        if (r.isEmpty()) {
            hoursChart.setData(null);
            hours.clearBody();
            return;
        }
        float[][] values = new float[7][HeatmapView.HOURS];
        for (int day = 0; day < 7; day++) {
            for (int c = 0; c < HeatmapView.HOURS; c++) {
                values[day][c] = (float) r.averageMinutes[day][HeatmapView.FIRST_HOUR + c];
            }
        }
        hoursChart.setData(HeatmapView.Data.hours(values, dayLetters(), dayNames(true),
                ChartFormatter.MINUTES));
        hours.setLegend(new int[]{R.color.bb_heat_1, R.color.bb_heat_3, R.color.bb_heat_4},
                new String[]{res.getString(R.string.all_progress_legend_low),
                        res.getString(R.string.all_progress_legend_mid),
                        res.getString(R.string.all_progress_legend_high)});
        hours.setSentence(phrases.bestSlot(r));
    }

    // ------------------------------------------------------------------ 3 · Ultimas 12 semanas

    private LineChart trendChart;

    private void renderTrend(AllProgressData d) {
        float[] cur = d.trendCurrentHours;
        float[] prev = d.trendPreviousHours;
        if (trendChart != null) {
            trend.chart.removeView(trendChart);
        }
        trendChart = new LineChart(ctx);
        trend.chart.addView(trendChart, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dimen(R.dimen.chart_height)));
        ChartStyler styler = new ChartStyler(ctx);
        float max = 0f;
        for (int i = 0; i < cur.length; i++) {
            max = Math.max(max, Math.max(cur[i], prev[i]));
        }
        if (max <= 0f) {
            styler.styleEmptyState(trendChart);
            trend.clearBody();
            return;
        }
        List<Entry> curEntries = new ArrayList<>();
        List<Entry> prevEntries = new ArrayList<>();
        for (int i = 0; i < cur.length; i++) {
            curEntries.add(new Entry(i, cur[i]));
            prevEntries.add(new Entry(i, prev[i]));
        }
        LineDataSet curSet = new LineDataSet(curEntries, "");
        styler.styleHeroLine(curSet);
        // Marcadores huecos cada 4 semanas y el ultimo punto en ambar (como la tarjeta de Progreso).
        curSet.setDrawCircles(false);
        styler.markEvery(curSet, 4);
        // Sin historia de las 12 semanas anteriores la linea de comparacion seria un suelo plano: se omite.
        boolean hasPrevious = sum(prev) > 0f;
        LineData lineData = new LineData();
        if (hasPrevious) {
            LineDataSet prevSet = new LineDataSet(prevEntries, "");
            styler.styleCompareLine(prevSet);
            lineData.addDataSet(prevSet);
        }
        lineData.addDataSet(curSet);
        trendChart.setData(lineData);
        styler.styleChart(trendChart, d.trendLabels);
        trendChart.getXAxis().setLabelCount(cur.length, false);
        styler.fitYAxis(trendChart, max);
        styler.setYAxisFormatter(trendChart, ChartStyler.unitFormatter("h"));
        trendChart.notifyDataSetChanged();
        trendChart.invalidate();

        float last = cur[cur.length - 1];
        trend.showFigure(String.format(locale, "%.1f", last),
                res.getString(R.string.all_progress_trend_unit), null);
        if (hasPrevious) {
            trend.setLegend(new int[]{R.color.bb_primary, R.color.bb_data_compare},
                    new String[]{res.getString(R.string.all_progress_legend_last_12),
                            res.getString(R.string.all_progress_legend_previous_12)});
        } else {
            trend.legend.setVisibility(View.GONE);
        }
        trend.setSentence(phrases.weeksAboveAverage(d.records));
    }

    // ------------------------------------------------------------------ 4 · Constancia

    private void renderConsistency(AllProgressData d) {
        int active = 0;
        for (float v : d.daily26Weeks) {
            if (v > 0f) active++;
        }
        if (active == 0) {
            consistencyChart.setData(null);
            consistency.clearBody();
            return;
        }
        CalendarHeatmap cal = CalendarHeatmap.build(LocalDate.now(), d.daily26Weeks,
                AllProgressViewModel.CONSISTENCY_WEEKS);
        consistencyChart.setData(HeatmapView.Data.calendar(cal, locale, ChartFormatter.MINUTES));
        consistency.showFigure(String.valueOf(active),
                res.getString(R.string.all_progress_active_days_of, d.daily26Weeks.length), null);
        consistency.setSentence(phrases.activeRun(d.longestRunDays));
    }

    // ------------------------------------------------------------------ 5 · Sesiones terminadas

    private void renderSessions(AllProgressData d) {
        SessionStats.Result cur = d.sessionsCurrent;
        if (cur.focusCount + cur.failCount == 0) {
            sessionsChart.setData(null);
            sessions.clearBody();
            return;
        }
        int[] colors = {R.color.bb_primary, R.color.bb_danger};
        String[] names = {res.getString(R.string.all_progress_legend_completed),
                res.getString(R.string.all_progress_legend_abandoned)};
        sessionsChart.setData(new StackedBarsView.Data(d.sessionBars, colors, names,
                d.buckets.labels(locale, res.getString(R.string.all_progress_week_prefix), dayLetters()),
                false,
                ChartFormatter.COUNT));
        String delta = null;
        boolean hasDelta = SessionStats.hasDelta(cur, d.sessionsPrevious);
        if (hasDelta) {
            int pts = (int) Math.round(SessionStats.deltaPoints(cur, d.sessionsPrevious));
            if (pts != 0) {
                delta = res.getString(pts > 0 ? R.string.all_progress_delta_points_up
                        : R.string.all_progress_delta_points_down, Math.abs(pts));
            }
        }
        sessions.showFigure(ChartFormat.percent((int) Math.round(cur.completionRate() * 100)),
                res.getString(R.string.all_progress_completion_unit),
                delta == null ? null : new Delta(delta, delta.startsWith("▲")));
        sessions.setLegend(colors, names);
        sessions.setSentence(phrases.completion(cur, hasDelta ? d.sessionsPrevious : null));
    }

    // ------------------------------------------------------------------ 6 · Tiempo por proyecto

    private void renderProjects(AllProgressData d) {
        donutLegend.removeAllViews();
        List<ProjectMinutes> list = d.projects;
        float[] minutes = new float[list == null ? 0 : list.size()];
        String[] names = new String[minutes.length];
        float total = 0f;
        for (int i = 0; i < minutes.length; i++) {
            minutes[i] = list.get(i).minutes;
            names[i] = list.get(i).projectName == null ? "" : list.get(i).projectName;
            total += minutes[i];
        }
        // Sin datos el estado vacio ocupa todo el ancho (el dónut mide 140dp y partiria el texto).
        LinearLayout.LayoutParams donutLp = (LinearLayout.LayoutParams) donutChart.getLayoutParams();
        donutLp.width = total <= 0f ? ViewGroup.LayoutParams.MATCH_PARENT : dimen(R.dimen.chart_donut_size);
        donutChart.setLayoutParams(donutLp);
        donutLegend.setVisibility(total <= 0f ? View.GONE : View.VISIBLE);
        if (total <= 0f) {
            donutChart.setData(null);
            projects.clearBody();
            return;
        }
        DonutView.Data data = new DonutView.Data(minutes, names, null,
                res.getString(R.string.chart_others), res.getString(R.string.chart_total),
                ChartFormatter.MINUTES);
        donutChart.setData(data);
        int pad = dimen(R.dimen.spacing_xs);
        int indent = dimen(R.dimen.progress_legend_dot) + dimen(R.dimen.spacing_sm);
        for (int i = 0; i < data.sliceCount(); i++) {
            TextView name = dotLabel(data.sliceColor(i), data.sliceName(i),
                    R.style.TextAppearance_BBetter_Body);
            name.setPadding(0, pad, 0, 0);
            donutLegend.addView(name);
            TextView value = text(ChartFormat.hoursMinutes(data.sliceValue(i)) + " · "
                    + ChartFormat.percent(data.slicePercent(i)), R.style.TextAppearance_BBetter_Caption);
            value.setFontFeatureSettings("tnum");
            value.setPaddingRelative(indent, 0, 0, pad);
            donutLegend.addView(value);
        }
        projects.clearBody();
    }

    // ------------------------------------------------------------------ 7 · Duracion de tus sesiones

    private void renderDurations(AllProgressData d) {
        SessionStats.Result r = d.sessions90;
        if (r.focusCount == 0) {
            durationsChart.setData(null);
            durations.clearBody();
            return;
        }
        String[] labels = new String[BUCKET_MIN.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = i == labels.length - 1 ? BUCKET_MIN[i] + "+" : BUCKET_MIN[i] + " m";
        }
        durationsChart.setData(new HistogramView.Data(r.buckets, labels, v -> {
            int n = Math.round(v);
            return res.getQuantityString(R.plurals.all_progress_sessions_count, n, n);
        }));
        durations.setSentence(phrases.modalDuration(r));
    }

    // ------------------------------------------------------------------ 8 · Enfoque frente a movil

    private void renderPhone(AllProgressData d) {
        phone.root.setVisibility(d.phoneAvailable ? View.VISIBLE : View.GONE);
        if (!d.phoneAvailable) {
            return;
        }
        String up = res.getString(R.string.all_progress_series_focus);
        String down = res.getString(R.string.all_progress_series_phone);
        phoneChart.setData(new MirrorBarsView.Data(d.mirrorFocus, d.mirrorPhone, d.mirrorLabels, up, down,
                ChartFormatter.MINUTES));
        phone.setLegend(new int[]{R.color.bb_primary, R.color.bb_data_compare},
                new String[]{res.getString(R.string.all_progress_legend_focus_up),
                        res.getString(R.string.all_progress_legend_phone_down)});
        phone.setSentence(phrases.focusVsPhone(d.focusVsPhone));
    }

    // ------------------------------------------------------------------ utilidades

    private static final class Delta {
        final String text;
        final boolean up;

        Delta(String text, boolean up) {
            this.text = text;
            this.up = up;
        }
    }

    @Nullable
    private Delta percentDelta(float before, float after, @androidx.annotation.StringRes int up,
                               @androidx.annotation.StringRes int down) {
        Integer pc = ChartFormat.percentChange(before, after);
        if (pc == null || pc == 0) {
            return null;
        }
        return new Delta(res.getString(pc > 0 ? up : down, Math.abs(pc)), pc > 0);
    }

    private static float sum(float[] v) {
        return sum(v, v.length);
    }

    private static float sum(float[] v, int n) {
        float s = 0f;
        for (int i = 0; i < Math.min(n, v.length); i++) s += v[i];
        return s;
    }

    private String[] dayLetters() {
        return res.getStringArray(R.array.all_progress_day_letters);
    }

    private String[] dayNames(boolean capitalized) {
        String[] out = new String[7];
        for (int i = 0; i < 7; i++) {
            String n = DayOfWeek.of(i + 1).getDisplayName(TextStyle.FULL, locale);
            out[i] = capitalized ? capitalize(n) : n;
        }
        return out;
    }

    private String[] monthNames() {
        String[] out = new String[12];
        for (int i = 0; i < 12; i++) {
            out[i] = capitalize(Month.of(i + 1).getDisplayName(TextStyle.FULL_STANDALONE, locale));
        }
        return out;
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(locale) + s.substring(1);
    }

    private static FrameLayout.LayoutParams wrap() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dimen(int id) {
        return res.getDimensionPixelSize(id);
    }

    private TextView dotLabel(@ColorRes int color, String label, @StyleRes int style) {
        TextView tv = text(label, style);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ContextCompat.getColor(ctx, color));
        int size = dimen(R.dimen.progress_legend_dot);
        dot.setSize(size, size);
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
        tv.setCompoundDrawablePadding(dimen(R.dimen.spacing_sm));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    private TextView text(String value, @StyleRes int style) {
        TextView tv = new TextView(ctx);
        TextViewCompat.setTextAppearance(tv, style);
        tv.setText(value);
        return tv;
    }

    /** Vistas de un include de item_chart_card. */
    private final class Card {
        final View root;
        final TextView title;
        final TextView subtitle;
        final View figureRow;
        final TextView figure;
        final TextView unit;
        final TextView delta;
        final FrameLayout chart;
        final LinearLayout legend;
        final TextView sentence;

        Card(View parent, @IdRes int id) {
            root = parent.findViewById(id);
            title = root.findViewById(R.id.chartCardTitle);
            subtitle = root.findViewById(R.id.chartCardSubtitle);
            figureRow = root.findViewById(R.id.chartCardFigureRow);
            figure = root.findViewById(R.id.chartCardFigure);
            unit = root.findViewById(R.id.chartCardUnit);
            delta = root.findViewById(R.id.chartCardDelta);
            chart = root.findViewById(R.id.chartCardChart);
            legend = root.findViewById(R.id.chartCardLegend);
            sentence = root.findViewById(R.id.chartCardSentence);
        }

        void setSubtitle(int stringRes) {
            subtitle.setText(stringRes);
            subtitle.setVisibility(View.VISIBLE);
        }

        /** Sin datos: fuera cifra, leyenda y frase (el grafico enseña su estado vacio). */
        void clearBody() {
            figureRow.setVisibility(View.GONE);
            legend.setVisibility(View.GONE);
            legend.removeAllViews();
            sentence.setVisibility(View.GONE);
        }

        void showFigure(String value, @Nullable String unitText, @Nullable Delta d) {
            figure.setText(value);
            unit.setVisibility(unitText == null ? View.GONE : View.VISIBLE);
            if (unitText != null) {
                unit.setText(unitText);
            }
            delta.setVisibility(d == null ? View.GONE : View.VISIBLE);
            if (d != null) {
                delta.setText(d.text);
                delta.setTextColor(ContextCompat.getColor(ctx,
                        d.up ? R.color.bb_primary_bright : R.color.bb_on_surface_muted));
            }
            figureRow.setVisibility(View.VISIBLE);
        }

        void setSentence(String text) {
            sentence.setText(text);
            sentence.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        }

        void setLegend(@ColorRes int[] colors, String[] names) {
            legend.removeAllViews();
            LinearLayout row = null;
            for (int i = 0; i < names.length; i++) {
                // Como mucho 3 por linea: en 360dp no caben 4 etiquetas una junto a otra.
                if (i % LEGEND_PER_ROW == 0) {
                    row = new LinearLayout(ctx);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    legend.addView(row);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(dimen(R.dimen.spacing_md));
                row.addView(dotLabel(colors[i], names[i], R.style.TextAppearance_BBetter_Caption), lp);
            }
            legend.setVisibility(View.VISIBLE);
        }
    }
}
