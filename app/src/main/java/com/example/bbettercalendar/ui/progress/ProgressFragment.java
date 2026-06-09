package com.example.bbettercalendar.ui.progress;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.databinding.FragmentProgressBinding;
import com.google.android.material.tabs.TabLayoutMediator;

import java.time.LocalDate;

public class ProgressFragment extends Fragment {

    private FragmentProgressBinding binding;
    private ProgressViewModel viewModel;
    private ChartCarouselAdapter adapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ProgressViewModel.class);
        binding = FragmentProgressBinding.inflate(inflater, container, false);

        adapter = new ChartCarouselAdapter();
        binding.chartPager.setAdapter(adapter);
        // Indicador de puntos: TabLayout sincronizado con el ViewPager2 (sin texto de pestaña).
        new TabLayoutMediator(binding.chartDots, binding.chartPager,
                (tab, position) -> { }).attach();

        binding.granularityDay.setOnClickListener(v -> viewModel.setGranularity(Granularity.DAY));
        binding.granularityWeek.setOnClickListener(v -> viewModel.setGranularity(Granularity.WEEK));
        binding.granularityMonth.setOnClickListener(v -> viewModel.setGranularity(Granularity.MONTH));
        binding.rangePrev.setOnClickListener(v -> viewModel.stepBack());
        binding.rangeNext.setOnClickListener(v -> viewModel.stepForward());

        viewModel.getCharts().observe(getViewLifecycleOwner(), bundle -> adapter.setBundle(bundle));
        viewModel.getSelectedRange().observe(getViewLifecycleOwner(), this::renderRange);

        return binding.getRoot();
    }

    private void renderRange(TimeRange range) {
        binding.rangeLabel.setText(range.label());

        boolean canForward = range.canStepForward(LocalDate.now());
        binding.rangeNext.setEnabled(canForward);
        binding.rangeNext.setAlpha(canForward ? 1f : 0.3f);

        highlightGranularity(range.granularity);
    }

    // Selección del segmented Day/Week/Month gestionada a mano (igual que el control de
    // activity_create_event): el seleccionado usa bg_segmented_item_selected.
    private void highlightGranularity(Granularity g) {
        binding.granularityDay.setBackgroundResource(g == Granularity.DAY
                ? R.drawable.bg_segmented_item_selected : R.drawable.bg_segmented_item);
        binding.granularityWeek.setBackgroundResource(g == Granularity.WEEK
                ? R.drawable.bg_segmented_item_selected : R.drawable.bg_segmented_item);
        binding.granularityMonth.setBackgroundResource(g == Granularity.MONTH
                ? R.drawable.bg_segmented_item_selected : R.drawable.bg_segmented_item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
