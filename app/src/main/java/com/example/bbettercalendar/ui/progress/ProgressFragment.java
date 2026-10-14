package com.example.bbettercalendar.ui.progress;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.databinding.FragmentProgressBinding;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.ui.common.chart.ChartStyler;
import com.example.bbettercalendar.ui.common.widget.PeriodNavigatorView;
import com.example.bbettercalendar.ui.common.widget.ProgressRowView;
import com.example.bbettercalendar.ui.common.widget.StatTileView;
import com.example.bbettercalendar.ui.common.widget.WidgetTone;
import com.example.bbettercalendar.ui.projects.ProjectListAdapter;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProgressFragment extends Fragment {

    private FragmentProgressBinding binding;
    private ProgressViewModel viewModel;

    // u17: pantalla rediseñada (html 13). Las vistas que se repintan se resuelven una vez.
    private StatTileView tileFocus;
    private StatTileView tileSessions;
    private StatTileView tileTasks;
    private LineChart trendChart;

    // PLACEHOLDER(ui-placeholders#progress-blocked-opens): no se cuentan aperturas bloqueadas.
    private static final int MOCK_BLOCKED_OPENS = 23;

    // Vistas de la tarjeta de estado (LOCKED / EMPTY) — están dentro del <include>, se resuelven
    // por findViewById para no depender del binding anidado del include.

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ProgressViewModel.class);
        binding = FragmentProgressBinding.inflate(inflater, container, false);

        setupHeader();
        setupNavigator();

        tileFocus = binding.progressTileFocus;
        tileSessions = binding.progressTileSessions;
        tileTasks = binding.progressTileTasks;

        View root = binding.getRoot();

        viewModel.getSelectedRange().observe(getViewLifecycleOwner(), this::renderRange);
        viewModel.getProjectRows().observe(getViewLifecycleOwner(), this::renderProjectRows);
        viewModel.getOverview().observe(getViewLifecycleOwner(), this::renderOverview);
        viewModel.getBlockingCard().observe(getViewLifecycleOwner(), this::renderBlockingCard);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // No hay callback al volver de Ajustes (permiso/accesibilidad) ni del picker: re-evaluar.
        if (viewModel != null) {
            viewModel.refreshUsageAccess();
            viewModel.armUsageLimitMonitor();
        }
    }

    // --- cabecera y navegador (u17) ---

    private void setupHeader() {
        binding.progressShieldButton.setOnClickListener(v -> openScreenTime());
        binding.progressManageBlocking.setOnClickListener(v -> openScreenTime());
        binding.progressSeeAll.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.navigation_all_progress));
        // PLACEHOLDER(ui-placeholders#progress-share): sin informe que compartir todavía.
        binding.progressShareButton.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.progress_share_soon, Toast.LENGTH_SHORT).show());
    }

    private void openScreenTime() {
        NavHostFragment.findNavController(this).navigate(R.id.navigation_screen_time);
    }

    private void setupNavigator() {
        // Week / Month / Year. DAY solo se elige en All progress (carrusel).
        binding.progressGranularity.setItems(
                getString(R.string.progress_granularity_week),
                getString(R.string.progress_granularity_month),
                getString(R.string.progress_granularity_year));
        binding.progressGranularity.setOnSegmentSelectedListener((view, index) ->
                viewModel.setGranularity(index == 1 ? Granularity.MONTH
                        : index == 2 ? Granularity.YEAR : Granularity.WEEK));
        binding.progressPeriodNavigator.setOnPeriodNavigateListener(
                new PeriodNavigatorView.OnPeriodNavigateListener() {
                    @Override
                    public void onPrevious(@NonNull PeriodNavigatorView view) {
                        viewModel.stepBack();
                    }

                    @Override
                    public void onNext(@NonNull PeriodNavigatorView view) {
                        viewModel.stepForward();
                    }
                });
    }

    private void renderRange(TimeRange range) {
        if (binding == null) return;
        binding.progressPeriodNavigator.setTitle(range.label(requireContext()));
        binding.progressPeriodNavigator.setSubtitle(range.subtitle(requireContext()));
        binding.progressPeriodNavigator.setNextEnabled(range.canStepForward(LocalDate.now()));

        binding.progressGranularity.setSelectedIndex(range.granularity == Granularity.MONTH ? 1
                : range.granularity == Granularity.YEAR ? 2 : 0);
        binding.progressProjectsTitle.setText(range.granularity == Granularity.MONTH
                ? R.string.progress_projects_month
                : range.granularity == Granularity.YEAR
                ? R.string.progress_projects_year : R.string.progress_projects_week);
    }

    // --- tiles + "Past 12 weeks" ---

    private void renderOverview(ProgressOverview o) {
        if (binding == null || o == null) return;
        TimeRange range = viewModel.getSelectedRange().getValue();
        Granularity g = range == null ? Granularity.WEEK : range.granularity;

        tileFocus.setValue(FormatHelper.formatDuration(o.focusMinutes * 60_000L));
        if (o.hasFocusDelta) {
            long pct = Math.round(Math.abs(o.focusDeltaPercent));
            StatTileView.Trend trend = o.focusDeltaPercent > 0 ? StatTileView.Trend.UP
                    : o.focusDeltaPercent < 0 ? StatTileView.Trend.DOWN : StatTileView.Trend.NONE;
            tileFocus.setDelta(getString(R.string.progress_tile_delta_percent, String.valueOf(pct)),
                    trend, o.focusDeltaPercent >= 0 ? WidgetTone.PRIMARY : WidgetTone.MUTED);
        } else {
            tileFocus.clearDelta();
        }

        tileSessions.setValue(String.valueOf(o.sessions));
        tileSessions.setSub(getString(R.string.progress_tile_sessions_avg,
                String.format(Locale.getDefault(), "%.1f", o.sessionsPerDay)));

        tileTasks.setValue(String.valueOf(o.tasksDone));
        int vsRes = g == Granularity.MONTH ? R.string.progress_tile_tasks_vs_month
                : g == Granularity.YEAR ? R.string.progress_tile_tasks_vs_year
                : g == Granularity.DAY ? R.string.progress_tile_tasks_vs_day
                : R.string.progress_tile_tasks_vs_week;
        tileTasks.setSub(getString(vsRes,
                String.format(Locale.getDefault(), "%+d", o.tasksDoneDelta)));

        binding.progressTrendValue.setText(
                String.format(Locale.getDefault(), "%.1f", o.trendCurrentMinutes / 60.0));
        binding.progressTrendDelta.setText(getString(R.string.progress_trend_delta,
                String.format(Locale.getDefault(), "%+.1f", o.trendDeltaMinutes / 60.0)));
        renderTrendChart(o);
    }

    private static final int TREND_MARKER_STEP = 4;

    // Línea héroe (12 semanas actuales) sobre la de comparación discontinua (las 12 anteriores).
    private void renderTrendChart(ProgressOverview o) {
        if (trendChart == null) {
            trendChart = new LineChart(requireContext());
            binding.progressTrendChartHolder.addView(trendChart);
        }
        ChartStyler styler = new ChartStyler(requireContext());
        int n = o.trendCurrentHours.length;
        List<Entry> cur = new ArrayList<>();
        List<Entry> prev = new ArrayList<>();
        float max = 0f;
        for (int i = 0; i < n; i++) {
            cur.add(new Entry(i, o.trendCurrentHours[i]));
            prev.add(new Entry(i, o.trendPreviousHours[i]));
            max = Math.max(max, Math.max(o.trendCurrentHours[i], o.trendPreviousHours[i]));
        }
        LineDataSet curSet = new LineDataSet(cur, "");
        styler.styleHeroLine(curSet);
        // html 13: marcadores huecos cada 4 semanas (no en todas) y el último punto en ámbar.
        curSet.setDrawCircles(false);
        styler.markEvery(curSet, TREND_MARKER_STEP);
        LineDataSet prevSet = new LineDataSet(prev, "");
        styler.styleCompareLine(prevSet);
        trendChart.setData(new LineData(prevSet, curSet));
        styler.styleChart(trendChart, o.trendLabels);
        trendChart.getXAxis().setLabelCount(n, false);
        styler.fitYAxis(trendChart, max);
        styler.setYAxisFormatter(trendChart, ChartStyler.unitFormatter("h"));
        trendChart.notifyDataSetChanged();
        trendChart.invalidate();
    }

    // --- tarjeta Screen time & blocking ---

    private void renderBlockingCard(ProgressOverview.BlockingCard c) {
        if (binding == null || c == null) return;
        binding.progressBlockingChips.removeAllViews();
        binding.progressBlockingChips.setVisibility(View.GONE);
        binding.progressBlockingGoalGroup.setVisibility(View.GONE);

        if (c.locked) {
            binding.progressBlockingSubtitle.setText(R.string.progress_blocking_locked);
        } else if (c.trackedCount == 0) {
            binding.progressBlockingSubtitle.setText(R.string.progress_blocking_no_apps);
        } else {
            String used = FormatHelper.formatDuration(c.usedMillis);
            if (c.goalMinutes > 0) {
                binding.progressBlockingSubtitle.setText(getString(R.string.progress_blocking_today_goal,
                        used, FormatHelper.formatDuration(c.goalMinutes * 60_000L)));
                binding.progressBlockingGoalGroup.setVisibility(View.VISIBLE);
                binding.progressBlockingBar.setProgressCompat(c.percentOfGoal(), false);
                binding.progressBlockingPercent.setText(
                        getString(R.string.progress_blocking_percent, c.percentOfGoal()));
                binding.progressBlockingRemaining.setText(getString(R.string.progress_blocking_remaining,
                        FormatHelper.formatDuration(c.remainingMillis())));
            } else {
                binding.progressBlockingSubtitle.setText(getString(R.string.progress_blocking_today_only, used));
            }
            addAppChips(c);
        }

        int onSurface = ContextCompat.getColor(requireContext(), R.color.bb_on_surface);
        String count = getString(R.string.progress_blocking_opens_count, MOCK_BLOCKED_OPENS);
        SpannableStringBuilder sb = new SpannableStringBuilder(count);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, count.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(onSurface), 0, count.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(' ').append(getString(R.string.progress_blocking_opens_suffix));
        binding.progressBlockingOpens.setText(sb);
    }

    private void addAppChips(ProgressOverview.BlockingCard c) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int gap = getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        for (int i = 0; i < c.chips.size(); i++) {
            ProgressOverview.BlockingCard.AppChip chip = c.chips.get(i);
            View v = inflater.inflate(R.layout.item_progress_app_chip, binding.progressBlockingChips, false);
            ((TextView) v.findViewById(R.id.progressChipName)).setText(chip.label);
            TextView usage = v.findViewById(R.id.progressChipUsage);
            View dot = v.findViewById(R.id.progressChipDot);
            View lock = v.findViewById(R.id.progressChipLock);
            if (chip.blocked) {
                v.setBackgroundResource(R.drawable.bg_progress_app_tile_blocked);
                dot.setVisibility(View.GONE);
                lock.setVisibility(View.VISIBLE);
                usage.setText(R.string.progress_blocking_blocked);
                usage.setTextColor(ContextCompat.getColor(requireContext(), R.color.bb_danger));
            } else {
                usage.setText(FormatHelper.formatDuration(chip.millis));
                // Cerca del límite: punto ámbar (el del drawable); si no, salvia.
                if (!chip.nearLimit) {
                    dot.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(),
                            R.color.bb_primary));
                }
            }
            if (i > 0) {
                ((LinearLayout.LayoutParams) v.getLayoutParams()).setMarginStart(gap);
            }
            binding.progressBlockingChips.addView(v);
        }
        binding.progressBlockingChips.setVisibility(c.chips.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // --- "Projects this week" (minutos por proyecto del rango; barra = parte del total) ---

    private void renderProjectRows(List<ProjectProgressRow> rows) {
        if (binding == null) return;
        binding.progressProjectsRows.removeAllViews();
        long total = 0;
        List<ProjectProgressRow> shown = new ArrayList<>();
        if (rows != null) {
            for (ProjectProgressRow r : rows) {
                if (r.minutesInRange > 0) {
                    shown.add(r);
                    total += r.minutesInRange;
                }
            }
        }
        binding.progressProjectsCard.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        if (shown.isEmpty()) return;
        java.util.Collections.sort(shown, (a, b) -> Integer.compare(b.minutesInRange, a.minutesInRange));

        binding.progressProjectsLogged.setText(getString(R.string.progress_projects_logged,
                FormatHelper.formatDuration(total * 60_000L)));
        int gap = getResources().getDimensionPixelSize(R.dimen.spacing_card_gap);
        for (int i = 0; i < shown.size(); i++) {
            ProjectProgressRow r = shown.get(i);
            ProgressRowView row = new ProgressRowView(requireContext());
            row.setTitle(r.name);
            row.setValue(FormatHelper.formatDuration(r.minutesInRange * 60_000L));
            row.setProgress((int) Math.round(100.0 * r.minutesInRange / total));
            row.setIndicatorColorRes(ProjectListAdapter.accentColorResFor(r.colorIndex));
            row.setOnClickListener(v -> openProjectDetail(r));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = gap;
            binding.progressProjectsRows.addView(row, lp);
        }
    }

    private void openProjectDetail(ProjectProgressRow row) {
        Bundle args = new Bundle();
        args.putInt("projectId", row.projectId);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_global_project_detail, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        trendChart = null;
        binding = null;
    }
}
