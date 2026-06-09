package com.example.bbettercalendar.ui.progress;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.List;

// Carrusel (ViewPager2) de 3 gráficos de Progress. Convierte el ChartBundle plano del ViewModel
// en LineChart/BarChart de MPAndroidChart, estilados con el palette bb_*. Cada página tiene su
// propio viewType para que el gráfico se cree una sola vez por posición.
public class ChartCarouselAdapter extends RecyclerView.Adapter<ChartCarouselAdapter.CardVH> {

    private static final int PAGE_CONCENT = 0;
    private static final int PAGE_FAILS = 1;
    private static final int PAGE_HOURLY = 2;
    private static final int PAGE_COUNT = 3;

    private ChartBundle bundle;

    public void setBundle(ChartBundle bundle) {
        this.bundle = bundle;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @NonNull
    @Override
    public CardVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chart_card, parent, false);
        return new CardVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CardVH h, int position) {
        Context ctx = h.itemView.getContext();
        h.label.setText(labelFor(ctx, position));
        h.container.removeAllViews();
        if (bundle == null) return;

        int primary = ContextCompat.getColor(ctx, R.color.bb_primary);
        int danger = ContextCompat.getColor(ctx, R.color.bb_danger);
        int axis = ContextCompat.getColor(ctx, R.color.bb_on_surface_muted);
        int grid = ContextCompat.getColor(ctx, R.color.bb_divider);

        switch (position) {
            case PAGE_CONCENT:
                h.container.addView(buildLineChart(ctx, bundle.dayLabels, bundle.focusMinutes, primary, axis, grid));
                break;
            case PAGE_FAILS:
                h.container.addView(buildLineChart(ctx, bundle.dayLabels, bundle.fails, danger, axis, grid));
                break;
            case PAGE_HOURLY:
                h.container.addView(buildHourlyChart(ctx, bundle.focusByHour, bundle.failByHour, primary, danger, axis, grid));
                break;
        }
    }

    private String labelFor(Context ctx, int position) {
        switch (position) {
            case PAGE_CONCENT:
                return ctx.getString(R.string.progress_chart_concent);
            case PAGE_FAILS:
                return ctx.getString(R.string.progress_chart_fails);
            case PAGE_HOURLY:
            default:
                return ctx.getString(R.string.progress_chart_when);
        }
    }

    private LineChart buildLineChart(Context ctx, String[] labels, int[] values,
                                     int color, int axisColor, int gridColor) {
        LineChart chart = new LineChart(ctx);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            entries.add(new Entry(i, values[i]));
        }
        LineDataSet set = new LineDataSet(entries, "");
        set.setColor(color);
        set.setCircleColor(color);
        set.setCircleHoleColor(color);
        set.setLineWidth(2f);
        set.setCircleRadius(3f);
        set.setDrawValues(false);
        set.setDrawFilled(true);
        set.setFillColor(color);
        set.setFillAlpha(40);
        chart.setData(new LineData(set));
        styleCommon(chart, labels, axisColor, gridColor);
        return chart;
    }

    private BarChart buildHourlyChart(Context ctx, int[] focus, int[] fail,
                                      int focusColor, int failColor, int axisColor, int gridColor) {
        BarChart chart = new BarChart(ctx);
        List<BarEntry> focusEntries = new ArrayList<>();
        List<BarEntry> failEntries = new ArrayList<>();
        String[] hourLabels = new String[24];
        for (int hr = 0; hr < 24; hr++) {
            focusEntries.add(new BarEntry(hr, focus[hr]));
            failEntries.add(new BarEntry(hr, fail[hr]));
            hourLabels[hr] = String.valueOf(hr);
        }
        BarDataSet focusSet = new BarDataSet(focusEntries, ctx.getString(R.string.progress_chart_concent));
        focusSet.setColor(focusColor);
        focusSet.setDrawValues(false);
        BarDataSet failSet = new BarDataSet(failEntries, ctx.getString(R.string.progress_chart_fails));
        failSet.setColor(failColor);
        failSet.setDrawValues(false);

        BarData data = new BarData(focusSet, failSet);
        // (barWidth + barSpace) * 2 + groupSpace = 1.0  -> un grupo por hora
        float groupSpace = 0.3f;
        float barSpace = 0.05f;
        float barWidth = 0.3f;
        data.setBarWidth(barWidth);
        chart.setData(data);

        styleCommon(chart, hourLabels, axisColor, gridColor);
        XAxis x = chart.getXAxis();
        x.setCenterAxisLabels(true);
        x.setAxisMinimum(0f);
        x.setAxisMaximum(0f + data.getGroupWidth(groupSpace, barSpace) * 24);
        chart.groupBars(0f, groupSpace, barSpace);
        chart.getLegend().setEnabled(true);
        chart.getLegend().setTextColor(axisColor);
        return chart;
    }

    private void styleCommon(BarLineChartBase<?> chart, String[] labels, int axisColor, int gridColor) {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setScaleEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setPinchZoom(false);
        chart.getAxisRight().setEnabled(false);
        chart.setExtraBottomOffset(4f);

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setTextColor(axisColor);
        x.setGranularity(1f);
        x.setValueFormatter(new IndexAxisValueFormatter(labels));

        YAxis y = chart.getAxisLeft();
        y.setTextColor(axisColor);
        y.setGridColor(gridColor);
        y.setAxisMinimum(0f);

        chart.getLegend().setEnabled(false);
        chart.invalidate();
    }

    static class CardVH extends RecyclerView.ViewHolder {
        final TextView label;
        final FrameLayout container;

        CardVH(@NonNull View v) {
            super(v);
            label = v.findViewById(R.id.chart_card_label);
            container = v.findViewById(R.id.chart_container);
        }
    }
}
