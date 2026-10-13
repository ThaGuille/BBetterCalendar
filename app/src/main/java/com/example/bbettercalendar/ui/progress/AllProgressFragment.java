package com.example.bbettercalendar.ui.progress;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.example.bbettercalendar.ui.common.widget.PeriodNavigatorView;
import com.example.bbettercalendar.ui.common.widget.SegmentedControlView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.time.LocalDate;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * u18: All progress ({@code navigation_all_progress}) — hosts the chart carousel that used to live
 * in Progress (u12 shell: top bar with back, no bottom nav). Own {@link ProgressViewModel} instance
 * (fragment-scoped), so its period selector never moves Progress' range.
 */
@AndroidEntryPoint
public class AllProgressFragment extends Fragment {

    private ProgressViewModel viewModel;
    private ChartCarouselAdapter adapter;
    private SegmentedControlView granularity;
    private PeriodNavigatorView navigator;

    public AllProgressFragment() {
        super(R.layout.fragment_all_progress);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TopBarHelper.bind(this, view, R.string.title_all_progress);
        viewModel = new ViewModelProvider(this).get(ProgressViewModel.class);

        adapter = new ChartCarouselAdapter();
        ViewPager2 pager = view.findViewById(R.id.chart_pager);
        TabLayout tabs = view.findViewById(R.id.chart_dots);
        pager.setAdapter(adapter);
        // Tabs titled per chart; autoRefresh re-maps them when DAY drops the "time of day" page.
        new TabLayoutMediator(tabs, pager,
                (tab, position) -> tab.setText(adapter.pageTitle(requireContext(), position))).attach();

        granularity = view.findViewById(R.id.allProgressGranularity);
        navigator = view.findViewById(R.id.allProgressPeriodNavigator);
        granularity.setItems(
                getString(R.string.progress_granularity_day),
                getString(R.string.progress_granularity_week),
                getString(R.string.progress_granularity_month),
                getString(R.string.progress_granularity_year));
        granularity.setOnSegmentSelectedListener((v, index) ->
                viewModel.setGranularity(granularityAt(index)));
        navigator.setOnPeriodNavigateListener(new PeriodNavigatorView.OnPeriodNavigateListener() {
            @Override
            public void onPrevious(@NonNull PeriodNavigatorView v) {
                viewModel.stepBack();
            }

            @Override
            public void onNext(@NonNull PeriodNavigatorView v) {
                viewModel.stepForward();
            }
        });

        viewModel.getCharts().observe(getViewLifecycleOwner(), bundle -> adapter.setBundle(bundle));
        viewModel.getSelectedRange().observe(getViewLifecycleOwner(), this::renderRange);
    }

    private static Granularity granularityAt(int index) {
        switch (index) {
            case 0: return Granularity.DAY;
            case 2: return Granularity.MONTH;
            case 3: return Granularity.YEAR;
            default: return Granularity.WEEK;
        }
    }

    private static int indexOf(Granularity g) {
        switch (g) {
            case DAY: return 0;
            case MONTH: return 2;
            case YEAR: return 3;
            default: return 1;
        }
    }

    private void renderRange(TimeRange range) {
        if (range == null || getView() == null) return;
        navigator.setTitle(range.label(requireContext()));
        navigator.setSubtitle(range.subtitle(requireContext()));
        navigator.setNextEnabled(range.canStepForward(LocalDate.now()));
        granularity.setSelectedIndex(indexOf(range.granularity));
    }
}
