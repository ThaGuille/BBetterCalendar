package com.example.bbettercalendar.ui.progress;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.chart.ChartStyler;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

// Carrusel (ViewPager2) de los gráficos de All progress (movido desde Progress en u18). Convierte el ChartBundle plano del ViewModel
// en LineChart/BarChart de MPAndroidChart; el estilo (colores bb_*, fuente, ejes, rejilla) lo pone
// ui/common/chart/ChartStyler (u10). Cada página tiene su propio viewType para que el gráfico se
// cree una sola vez por posición.
public class ChartCarouselAdapter extends RecyclerView.Adapter<ChartCarouselAdapter.CardVH> {

    private static final int PAGE_CONCENT = 0;
    private static final int PAGE_FAILS = 1;
    private static final int PAGE_HOURLY = 2;
    private static final int PAGE_PROJECTS = 3;
    private static final int PAGE_COUNT = 4;
    // En vista DAY la serie diaria es un único día (un punto): los gráficos de concentración y
    // fallos pasan a ser barras por hora del día, y el gráfico "When" sobra (sería el solapado de
    // esos dos), así que esa página se cae y las posiciones se remapean.
    private static final int PAGE_COUNT_DAY = 3;
    private static final int PAGE_PROJECTS_DAY = 2;

    private ChartBundle bundle;

    public void setBundle(ChartBundle bundle) {
        this.bundle = bundle;
        notifyDataSetChanged();
    }

    private boolean isDay() {
        return bundle != null && bundle.granularity == Granularity.DAY;
    }

    @Override
    public int getItemCount() {
        return isDay() ? PAGE_COUNT_DAY : PAGE_COUNT;
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

    // Título de la página, expuesto para el selector con nombre (TabLayout en AllProgressFragment):
    // así el tab y la tarjeta del carrusel siempre muestran la misma etiqueta, incluida la
    // remapeada de DAY, sin duplicar el switch en dos sitios.
    public CharSequence pageTitle(Context ctx, int position) {
        return labelFor(ctx, position);
    }

    @Override
    public void onBindViewHolder(@NonNull CardVH h, int position) {
        Context ctx = h.itemView.getContext();
        // Sin título en la tarjeta: la pestaña de encima ya lo dice (spec frontend-structure-cleanup).
        h.container.removeAllViews();
        if (bundle == null) return;

        // Colores, fuente y geometría viven en ChartStyler (u10); aquí sólo datos -> series.
        ChartStyler styler = new ChartStyler(ctx);

        Chart<?> chart;
        if (isDay()) {
            // DAY: concentración y fallos son barras por hora del día (no hay tendencia con un
            // solo día); la de proyectos se comporta igual en los dos modos.
            if (position == PAGE_CONCENT) {
                chart = buildHourlyBarChart(ctx, styler, bundle.focusMinutesByHour, ChartStyler.Tone.PRIMARY);
            } else if (position == PAGE_FAILS) {
                chart = buildHourlyBarChart(ctx, styler, bundle.failByHour, ChartStyler.Tone.DANGER);
            } else {
                chart = buildProjectChart(ctx, styler);
            }
        } else {
            switch (position) {
                case PAGE_CONCENT:
                    chart = buildLineChart(ctx, styler, bundle.dayLabels, bundle.focusMinutes, true);
                    break;
                case PAGE_FAILS:
                    chart = buildLineChart(ctx, styler, bundle.dayLabels, bundle.fails, false);
                    break;
                case PAGE_HOURLY:
                    chart = buildHourlyChart(ctx, styler, bundle.focusByHour, bundle.failByHour);
                    break;
                case PAGE_PROJECTS:
                default:
                    chart = buildProjectChart(ctx, styler);
                    break;
            }
        }
        h.container.addView(chart);

        // Dentro de ViewPager2 (RecyclerView) el chart se añade durante el bind, antes de que la
        // vista tenga tamaño: el invalidate() dibuja un viewport vacío y no se vuelve
        // a pintar hasta un swipe. Forzamos un repintado tras el layout para que aparezca a la primera.
        final Chart<?> bound = chart;
        bound.post(() -> {
            bound.notifyDataSetChanged();
            bound.invalidate();
        });
    }

    // Las posiciones son posicionales y DAY las remapea, así que la etiqueta también depende del
    // modo: en DAY la posición 2 ya no es "When" sino la de proyectos.
    private String labelFor(Context ctx, int position) {
        if (isDay() && position == PAGE_PROJECTS_DAY) {
            return ctx.getString(R.string.all_progress_tab_projects);
        }
        switch (position) {
            case PAGE_CONCENT:
                return ctx.getString(R.string.all_progress_tab_focus);
            case PAGE_FAILS:
                return ctx.getString(R.string.all_progress_tab_interruptions);
            case PAGE_HOURLY:
                return ctx.getString(R.string.all_progress_tab_time_of_day);
            case PAGE_PROJECTS:
            default:
                return ctx.getString(R.string.all_progress_tab_projects);
        }
    }

    // Concentración = línea héroe (salvia con degradado y último punto en ámbar, html 13);
    // fallos = línea simple en ember. En rangos largos (mes) el styler omite los marcadores.
    private LineChart buildLineChart(Context ctx, ChartStyler styler, String[] labels, int[] values,
                                     boolean hero) {
        LineChart chart = new LineChart(ctx);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            entries.add(new Entry(i, values[i]));
        }
        LineDataSet set = new LineDataSet(entries, "");
        if (hero) {
            // El punto «actual» es el último día con datos: en la semana/mes en curso los días
            // finales aún no han llegado (valen 0) y no deben llevar el marcador ámbar.
            styler.styleHeroLine(set, lastNonZero(values));
        } else {
            styler.styleLine(set, ChartStyler.Tone.DANGER);
        }
        chart.setData(new LineData(set));
        styler.styleChart(chart, labels);
        styler.fitYAxis(chart, max(values));
        return chart;
    }

    private BarChart buildHourlyChart(Context ctx, ChartStyler styler, int[] focus, int[] fail) {
        BarChart chart = new BarChart(ctx);
        List<BarEntry> focusEntries = new ArrayList<>();
        List<BarEntry> failEntries = new ArrayList<>();
        String[] hourLabels = new String[24];
        for (int hr = 0; hr < 24; hr++) {
            focusEntries.add(new BarEntry(hr, focus[hr]));
            failEntries.add(new BarEntry(hr, fail[hr]));
            hourLabels[hr] = String.valueOf(hr);
        }
        BarDataSet focusSet = styler.styleBars(
                new BarDataSet(focusEntries, ctx.getString(R.string.all_progress_tab_focus)),
                ChartStyler.Tone.PRIMARY);
        BarDataSet failSet = styler.styleBars(
                new BarDataSet(failEntries, ctx.getString(R.string.all_progress_tab_interruptions)),
                ChartStyler.Tone.DANGER);

        BarData data = new BarData(focusSet, failSet);
        // (barWidth + barSpace) * 2 + groupSpace = 1.0  -> un grupo por hora
        float groupSpace = 0.3f;
        float barSpace = 0.05f;
        float barWidth = 0.3f;
        data.setBarWidth(barWidth);
        chart.setData(data);

        styler.styleChart(chart, hourLabels);
        styler.fitYAxis(chart, Math.max(max(focus), max(fail)));
        XAxis x = chart.getXAxis();
        x.setCenterAxisLabels(true);
        x.setAxisMinimum(0f);
        x.setAxisMaximum(0f + data.getGroupWidth(groupSpace, barSpace) * 24);
        chart.groupBars(0f, groupSpace, barSpace);
        // Dos series y sin leyenda en la cabecera de la tarjeta: aquí sí hace falta la leyenda.
        styler.styleLegend(chart);
        return chart;
    }

    // Barra única por hora del día (0–23). Usado en vista DAY para concentración (minutos) y
    // fallos (conteo), donde la serie diaria sería un solo punto.
    private BarChart buildHourlyBarChart(Context ctx, ChartStyler styler, int[] values,
                                         ChartStyler.Tone tone) {
        BarChart chart = new BarChart(ctx);
        List<BarEntry> entries = new ArrayList<>();
        String[] hourLabels = new String[24];
        for (int hr = 0; hr < 24; hr++) {
            entries.add(new BarEntry(hr, values[hr]));
            hourLabels[hr] = String.valueOf(hr);
        }
        BarDataSet set = styler.styleBars(new BarDataSet(entries, ""), tone);
        BarData data = new BarData(set);
        data.setBarWidth(ChartStyler.barWidth());
        chart.setData(data);

        styler.styleChart(chart, hourLabels);
        styler.fitYAxis(chart, max(values));
        XAxis x = chart.getXAxis();
        // Una etiqueta cada pocas horas: 24 etiquetas pegadas se solapan en un eje estrecho.
        x.setLabelCount(7, false);
        x.setAxisMinimum(-0.5f);
        x.setAxisMaximum(23.5f);
        return chart;
    }

    // Minutos de concentración atribuidos por proyecto dentro del rango (spec
    // project-deadlines-progress). Deliberadamente básica: una barra por proyecto, sin filtros ni
    // métricas seleccionables — eso llega más adelante. Rango sin datos -> estado vacío estilado
    // por ChartStyler (texto «sin datos»), sin vista vacía a medida.
    private BarChart buildProjectChart(Context ctx, ChartStyler styler) {
        BarChart chart = new BarChart(ctx);
        String[] labels = bundle.projectLabels;
        int[] values = bundle.projectMinutes;
        if (labels == null || values == null || values.length == 0) {
            styler.styleEmptyState(chart);
            return chart;
        }

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            entries.add(new BarEntry(i, values[i]));
        }
        BarDataSet set = styler.styleBars(new BarDataSet(entries, ""), ChartStyler.Tone.PRIMARY);
        BarData data = new BarData(set);
        data.setBarWidth(0.5f);
        chart.setData(data);

        styler.styleChart(chart, labels);
        styler.fitYAxis(chart, max(values));
        XAxis x = chart.getXAxis();
        // Un nombre de proyecto es mucho más ancho que "12" o "3/5": con muchos proyectos las
        // etiquetas se solaparían, así que se limita cuántas se dibujan.
        x.setLabelCount(Math.min(values.length, 5), false);
        x.setAxisMinimum(-0.5f);
        x.setAxisMaximum(values.length - 0.5f);
        return chart;
    }

    private static int lastNonZero(int[] values) {
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i] > 0) return i;
        }
        return -1;
    }

    private static int max(int[] values) {
        int m = 0;
        if (values != null) {
            for (int v : values) m = Math.max(m, v);
        }
        return m;
    }

    static class CardVH extends RecyclerView.ViewHolder {
        final FrameLayout container;

        CardVH(@NonNull View v) {
            super(v);
            container = v.findViewById(R.id.chart_container);
        }
    }
}
