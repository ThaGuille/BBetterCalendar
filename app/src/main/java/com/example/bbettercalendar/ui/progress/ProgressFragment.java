package com.example.bbettercalendar.ui.progress;

import android.os.Bundle;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.databinding.FragmentProgressBinding;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.stats.ConsistencyCalculator;
import com.example.bbettercalendar.ui.common.chart.ChartFormat;
import com.example.bbettercalendar.ui.common.chart.ChartFormatter;
import com.example.bbettercalendar.ui.common.chart.LimitBarView;
import com.example.bbettercalendar.ui.common.chart.SparkBarsView;
import com.example.bbettercalendar.ui.common.motion.Motion;
import com.example.bbettercalendar.ui.common.share.ShareCardRenderer;
import com.example.bbettercalendar.ui.common.widget.PeriodNavigatorView;
import com.example.bbettercalendar.ui.common.widget.ProgressRowView;
import com.example.bbettercalendar.ui.common.widget.StatTileView;
import com.example.bbettercalendar.ui.common.widget.WidgetTone;
import com.example.bbettercalendar.ui.projects.ProjectListAdapter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProgressFragment extends Fragment {

    private FragmentProgressBinding binding;
    private ProgressViewModel viewModel;
    private final ShareCardRenderer shareRenderer = new ShareCardRenderer();

    // u7: valores que se muestran, para que Motion.countUp parta del anterior al cambiar de periodo.
    private long shownFocusMinutes;
    private long shownSessions;
    private long shownTasks;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ProgressViewModel.class);
        binding = FragmentProgressBinding.inflate(inflater, container, false);

        setupHeader();
        setupNavigator();

        shownFocusMinutes = 0;
        shownSessions = 0;
        shownTasks = 0;
        // Esqueletos hasta que llegan ProgressOverview y la tarjeta de apps.
        binding.progressSummarySkeleton.show();
        binding.progressAppsSkeleton.show();

        View root = binding.getRoot();

        viewModel.getSelectedRange().observe(getViewLifecycleOwner(), this::renderRange);
        viewModel.getProjectRows().observe(getViewLifecycleOwner(), this::renderProjectRows);
        viewModel.getOverview().observe(getViewLifecycleOwner(), this::renderOverview);
        viewModel.getAppsCard().observe(getViewLifecycleOwner(), this::renderAppsCard);

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
        binding.progressAppsCard.setOnClickListener(v -> openScreenTime());
        binding.progressSeeAll.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.navigation_all_progress));
        // F0-u5: tarjeta-imagen del periodo + ACTION_SEND solo con texto (Q-06, sin enlace).
        shareRenderer.attach(this, binding.progressShareButton);
        binding.progressShareButton.setOnClickListener(v -> {
            ShareCardRenderer.Card card = buildShareCard();
            if (card != null) {
                shareRenderer.share(requireActivity(), card);
            }
        });
    }

    /** Total de enfoque y constancia del periodo seleccionado; null mientras carga. */
    private ShareCardRenderer.Card buildShareCard() {
        ProgressOverview o = viewModel.getOverview().getValue();
        TimeRange range = viewModel.getSelectedRange().getValue();
        if (o == null || range == null) return null;
        String label = range.label(requireContext());
        String focus = FormatHelper.formatDuration(o.focusMinutes * 60_000L);
        ConsistencyCalculator.Result c = o.consistency;
        String constancy = c == null || c.countedDays == 0
                ? getString(R.string.progress_consistency_none)
                : getResources().getQuantityString(R.plurals.progress_consistency_value,
                        c.countedDays, c.activeDays, c.countedDays);
        return new ShareCardRenderer.Card(
                label,
                focus,
                getString(R.string.share_progress_hero_sub),
                constancy,
                getString(R.string.share_progress_stat_label),
                null, null,
                getString(R.string.share_progress_text, label, focus),
                "bbetter-progress-card.png");
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

    // --- tarjeta resumen (u7) ---

    private void renderOverview(ProgressOverview o) {
        if (binding == null || o == null) return;
        TimeRange range = viewModel.getSelectedRange().getValue();
        Granularity g = range == null ? Granularity.WEEK : range.granularity;

        boolean firstData = binding.progressSummaryContent.getVisibility() != View.VISIBLE;
        if (firstData) {
            binding.progressSummarySkeleton.hide();
            binding.progressSummaryContent.setVisibility(View.VISIBLE);
        }

        StatTileView focusTile = binding.progressSummaryFocus;
        ChartFormatter minutes = ChartFormatter.MINUTES;
        Motion.countUp(focusTile.getValueView(), shownFocusMinutes, o.focusMinutes,
                v -> minutes.format(v));
        shownFocusMinutes = o.focusMinutes;
        if (o.hasFocusDelta) {
            long pct = Math.round(Math.abs(o.focusDeltaPercent));
            StatTileView.Trend trend = o.focusDeltaPercent > 0 ? StatTileView.Trend.UP
                    : o.focusDeltaPercent < 0 ? StatTileView.Trend.DOWN : StatTileView.Trend.NONE;
            focusTile.setDelta(getString(R.string.progress_summary_delta, (int) pct), trend,
                    o.focusDeltaPercent >= 0 ? WidgetTone.PRIMARY : WidgetTone.MUTED);
        } else {
            focusTile.clearDelta();
        }
        focusTile.setSub(getString(g == Granularity.MONTH ? R.string.progress_summary_vs_month
                : g == Granularity.YEAR ? R.string.progress_summary_vs_year
                : g == Granularity.DAY ? R.string.progress_summary_vs_day
                : R.string.progress_summary_vs_week));

        binding.progressSparkBars.setData(new SparkBarsView.Data(o.sparkValues, o.sparkLabels,
                o.sparkHighlight, ChartFormatter.MINUTES));

        StatTileView sessionsTile = binding.progressSummarySessions;
        Motion.countUp(sessionsTile.getValueView(), shownSessions, o.sessions, String::valueOf);
        shownSessions = o.sessions;
        sessionsTile.setSub(o.sessions > 0
                ? getString(R.string.progress_summary_sessions_avg, o.avgSessionMinutes)
                : getString(R.string.progress_summary_sessions_none));

        StatTileView tasksTile = binding.progressSummaryTasks;
        Motion.countUp(tasksTile.getValueView(), shownTasks, o.tasksDone, String::valueOf);
        shownTasks = o.tasksDone;
        int d = o.tasksDoneDelta;
        tasksTile.setSub(d > 0 ? getString(R.string.progress_summary_tasks_more, d)
                : d < 0 ? getString(R.string.progress_summary_tasks_less, -d)
                : getString(R.string.progress_summary_tasks_same));

        renderConsistency(o);
    }

    // Pie de constancia (plan racha-semanal-y-constancia): "18/30 días" + delta + tira.
    private void renderConsistency(ProgressOverview o) {
        ConsistencyCalculator.Result c = o.consistency;
        if (c == null) return;
        // 0 días que cuenten (primer día del periodo, aún sin sesión): un guion, no "0/0".
        binding.progressConsistencyValue.setText(c.countedDays == 0
                ? getString(R.string.progress_consistency_none)
                : getResources().getQuantityString(R.plurals.progress_consistency_value,
                        c.countedDays, c.activeDays, c.countedDays));
        binding.progressConsistencyStrip.setCells(c.cells);

        TextView delta = binding.progressConsistencyDelta;
        if (o.hasConsistencyDelta) {
            delta.setVisibility(View.VISIBLE);
            delta.setText(getString(R.string.progress_consistency_delta, o.consistencyDelta));
            delta.setTextColor(ContextCompat.getColor(requireContext(), o.consistencyDelta > 0
                    ? R.color.bb_primary : R.color.bb_on_surface_muted));
            binding.progressConsistencyRow.setContentDescription(getString(
                    R.string.progress_consistency_desc_delta, c.activeDays, c.countedDays,
                    o.consistencyDelta));
        } else {
            delta.setVisibility(View.GONE);
            binding.progressConsistencyRow.setContentDescription(getString(
                    R.string.progress_consistency_desc, c.activeDays, c.countedDays));
        }
    }

    // --- tarjeta "Tus apps" (u7) ---

    private void renderAppsCard(ProgressOverview.AppsCard c) {
        if (binding == null || c == null) return;
        binding.progressAppsSkeleton.hide();
        binding.progressAppsRows.removeAllViews();

        if (c.locked) {
            binding.progressAppsSubtitle.setText(R.string.progress_apps_locked);
        } else if (c.rows.isEmpty()) {
            binding.progressAppsSubtitle.setText(R.string.progress_apps_none);
        } else {
            String avg = ChartFormat.hoursMinutes(c.averageMinutes);
            int pct = Math.abs(c.deltaPercent);
            binding.progressAppsSubtitle.setText(!c.hasDelta || pct < 1
                    ? getString(R.string.progress_apps_subtitle, avg)
                    : getString(c.deltaPercent < 0 ? R.string.progress_apps_subtitle_less
                            : R.string.progress_apps_subtitle_more, avg, pct));
        }
        boolean show = !c.locked && !c.rows.isEmpty();
        binding.progressAppsRows.setVisibility(show ? View.VISIBLE : View.GONE);
        boolean anyLimit = false;
        if (show) {
            float scaleMax = 0f;
            for (ProgressOverview.AppsCard.AppRow r : c.rows) {
                scaleMax = Math.max(scaleMax, Math.max(r.averageMinutes, r.limitMinutes));
            }
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            int gap = getResources().getDimensionPixelSize(R.dimen.spacing_md);
            for (int i = 0; i < c.rows.size(); i++) {
                ProgressOverview.AppsCard.AppRow r = c.rows.get(i);
                anyLimit |= r.limitMinutes > 0;
                View v = inflater.inflate(R.layout.item_progress_app_row, binding.progressAppsRows, false);
                bindAppRow(v, r, scaleMax);
                if (i > 0) {
                    ((LinearLayout.LayoutParams) v.getLayoutParams()).topMargin = gap;
                }
                binding.progressAppsRows.addView(v);
            }
        }
        binding.progressAppsHint.setVisibility(anyLimit ? View.VISIBLE : View.GONE);
    }

    private void bindAppRow(View v, ProgressOverview.AppsCard.AppRow r, float scaleMax) {
        ((TextView) v.findViewById(R.id.progressAppName)).setText(r.label);
        PackageManager pm = requireContext().getPackageManager();
        Drawable icon;
        try {
            icon = pm.getApplicationIcon(r.packageName);
        } catch (PackageManager.NameNotFoundException e) {
            icon = pm.getDefaultActivityIcon();
        }
        ((android.widget.ImageView) v.findViewById(R.id.progressAppIcon)).setImageDrawable(icon);
        v.findViewById(R.id.progressAppLock).setVisibility(r.enforce ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.progressAppSuggested).setVisibility(r.suggested ? View.VISIBLE : View.GONE);
        String used = ChartFormat.hoursMinutes(r.averageMinutes);
        ((TextView) v.findViewById(R.id.progressAppValue)).setText(r.limitMinutes > 0
                ? getString(R.string.progress_apps_value_limit, used,
                        ChartFormat.hoursMinutes(r.limitMinutes))
                : used);
        LimitBarView bar = v.findViewById(R.id.progressAppBar);
        bar.setTapEnabled(false);
        bar.setData(new LimitBarView.Data(r.averageMinutes, r.limitMinutes, scaleMax,
                ChartFormatter.MINUTES));
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
        binding = null;
    }
}
