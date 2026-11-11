package com.example.bbettercalendar.ui.progress;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.example.bbettercalendar.ui.common.motion.ScreenTransitions;
import com.example.bbettercalendar.ui.common.widget.PeriodNavigatorView;
import com.example.bbettercalendar.ui.common.widget.SegmentedControlView;

import java.time.LocalDate;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Todo tu progreso ({@code navigation_all_progress}), mejora-visual u8: selector de periodo fijo arriba y
 * un NestedScrollView con 8 tarjetas de graficos (mockups 11-18). Tiene su propio
 * {@link AllProgressViewModel}: el selector de esta pantalla nunca mueve el rango de Progreso. Las
 * tarjetas las pinta {@link AllProgressCards}.
 */
@AndroidEntryPoint
public class AllProgressFragment extends Fragment {

    private AllProgressViewModel viewModel;
    private AllProgressCards cards;
    private SegmentedControlView granularity;
    private PeriodNavigatorView navigator;

    public AllProgressFragment() {
        super(R.layout.fragment_all_progress);
    }

    // u15: transiciones de pantalla (ScreenTransitions: fundido entre pestañas, eje Z/X, contenedor).
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenTransitions.apply(this);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TopBarHelper.bind(this, view, R.string.title_all_progress);
        viewModel = new ViewModelProvider(this).get(AllProgressViewModel.class);
        cards = new AllProgressCards(view);

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

        viewModel.getData().observe(getViewLifecycleOwner(), data -> {
            if (data != null) cards.render(data);
        });
        viewModel.getSelectedRange().observe(getViewLifecycleOwner(), this::renderRange);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Al volver de Ajustes puede haber cambiado el acceso de uso (tarjeta «Enfoque frente a móvil»).
        if (viewModel != null) viewModel.refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cards = null;
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
