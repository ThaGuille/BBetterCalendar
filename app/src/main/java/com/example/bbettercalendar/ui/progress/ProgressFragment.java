package com.example.bbettercalendar.ui.progress;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.blocking.AccessibilityAccess;
import com.example.bbettercalendar.blocking.AccessibilityDisclosureDialog;
import com.example.bbettercalendar.blocking.BlockingSettings;
import com.example.bbettercalendar.databinding.FragmentProgressBinding;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.ui.progress.apppicker.AppPickerActivity;
import com.example.bbettercalendar.usage.UsageAccess;
import com.google.android.material.tabs.TabLayoutMediator;

import java.time.LocalDate;

public class ProgressFragment extends Fragment {

    private FragmentProgressBinding binding;
    private ProgressViewModel viewModel;
    private ChartCarouselAdapter adapter;
    private AppUsageAdapter usageAdapter;

    // Vistas de la tarjeta de estado (LOCKED / EMPTY) — están dentro del <include>, se resuelven
    // por findViewById para no depender del binding anidado del include.
    private View usageStateCard;
    private TextView usageStateTitle;
    private TextView usageStateBody;
    private TextView usageStateCta;

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

        // --- banda 3: uso de apps ---
        usageAdapter = new AppUsageAdapter(requireContext(),
                (packageName, label, currentLimitMinutes) ->
                        AppLimitDialog.newInstance(packageName, label, currentLimitMinutes)
                                .show(getChildFragmentManager(), "app_limit"),
                this::onEnforceToggle);
        binding.usageList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.usageList.setAdapter(usageAdapter);

        binding.usageEnforceMaster.setOnClickListener(v -> {
            boolean nowEnabled = !BlockingSettings.isEnforcementEnabled(requireContext());
            BlockingSettings.setEnforcementEnabled(requireContext(), nowEnabled);
            updateEnforceMaster();
        });

        View root = binding.getRoot();
        usageStateCard = root.findViewById(R.id.usage_state_card);
        usageStateTitle = root.findViewById(R.id.usage_state_title);
        usageStateBody = root.findViewById(R.id.usage_state_body);
        usageStateCta = root.findViewById(R.id.usage_state_cta);

        binding.usageAddApps.setOnClickListener(v -> openPicker());

        viewModel.getCharts().observe(getViewLifecycleOwner(), bundle -> adapter.setBundle(bundle));
        viewModel.getSelectedRange().observe(getViewLifecycleOwner(), this::renderRange);
        viewModel.getApps().observe(getViewLifecycleOwner(), rows -> usageAdapter.submit(rows));
        viewModel.getScreenTimeMillis().observe(getViewLifecycleOwner(), millis ->
                binding.usageScreenTime.setText(FormatHelper.formatDuration(millis)));
        viewModel.getUsageState().observe(getViewLifecycleOwner(), this::renderUsageState);

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
        // Al volver de Ajustes -> Accesibilidad el usuario pudo (o no) activar el servicio: re-leer y
        // re-pintar los toggles (pendiente ámbar -> activo rojo si ya está activado, o al revés).
        if (usageAdapter != null) {
            usageAdapter.setAccessibilityEnabled(AccessibilityAccess.isEnabled(requireContext()));
        }
        updateEnforceMaster();
    }

    // Tap en el toggle 🚫 de una fila (Phase 4a). Sin límite -> abrir primero el diálogo de límite (no
    // se puede hacer cumplir lo que no existe). Apagar enforce no requiere permiso -> se hace directo.
    // Encender: si el servicio de Accesibilidad ya está activado, se arma ya (rojo). Si no, se muestra
    // SIEMPRE la divulgación/guía (no sólo la 1ª vez) — y NO se arma nada hasta que el usuario pulse
    // "Enable" en ella (así el toggle no se pone "activo" sin que el usuario acepte y active el servicio).
    private void onEnforceToggle(String packageName, String label, int currentLimitMinutes,
                                 boolean currentlyEnforced) {
        if (currentLimitMinutes <= 0) {
            AppLimitDialog.newInstance(packageName, label, currentLimitMinutes)
                    .show(getChildFragmentManager(), "app_limit");
            return;
        }
        if (currentlyEnforced) {
            viewModel.setEnforceAtLimit(packageName, false);
            return;
        }
        if (AccessibilityAccess.isEnabled(requireContext())) {
            viewModel.setEnforceAtLimit(packageName, true);
        } else {
            AccessibilityDisclosureDialog.newInstance(packageName)
                    .show(getChildFragmentManager(), "accessibility_disclosure");
        }
    }

    // Interruptor maestro "hacer cumplir los límites": refleja el estado guardado (por defecto ON).
    private void updateEnforceMaster() {
        if (binding == null) return;
        boolean enabled = BlockingSettings.isEnforcementEnabled(requireContext());
        binding.usageEnforceMaster.setText(enabled
                ? R.string.progress_enforce_master_on
                : R.string.progress_enforce_master_off);
    }

    private void renderRange(TimeRange range) {
        binding.rangeLabel.setText(range.label());

        boolean canForward = range.canStepForward(LocalDate.now());
        binding.rangeNext.setEnabled(canForward);
        binding.rangeNext.setAlpha(canForward ? 1f : 0.3f);

        highlightGranularity(range.granularity);
    }

    // Banda 3: alterna lista / spinner / tarjeta de estado y configura la tarjeta según el estado.
    private void renderUsageState(UsageBandState state) {
        if (binding == null) return;
        switch (state) {
            case LOADING:
                binding.usageList.setVisibility(View.GONE);
                binding.usageLoading.setVisibility(View.VISIBLE);
                usageStateCard.setVisibility(View.GONE);
                break;
            case READY:
                binding.usageList.setVisibility(View.VISIBLE);
                binding.usageLoading.setVisibility(View.GONE);
                usageStateCard.setVisibility(View.GONE);
                break;
            case LOCKED:
                binding.usageScreenTime.setText(R.string.progress_usage_time_none);
                showStateCard(R.string.progress_usage_locked_title,
                        R.string.progress_usage_locked_body,
                        R.string.progress_usage_locked_cta,
                        v -> onLockedCta());
                break;
            case EMPTY_NO_APPS:
                showStateCard(R.string.progress_usage_empty_title,
                        R.string.progress_usage_empty_body,
                        R.string.progress_usage_empty_cta,
                        v -> openPicker());
                break;
        }
    }

    private void showStateCard(int titleRes, int bodyRes, int ctaRes, View.OnClickListener ctaClick) {
        binding.usageList.setVisibility(View.GONE);
        binding.usageLoading.setVisibility(View.GONE);
        usageStateCard.setVisibility(View.VISIBLE);
        usageStateTitle.setText(titleRes);
        usageStateBody.setText(bodyRes);
        usageStateCta.setText(ctaRes);
        usageStateCta.setOnClickListener(ctaClick);
    }

    // CTA de la tarjeta LOCKED: divulgación (si aún no se consintió) o directo a Ajustes.
    private void onLockedCta() {
        viewModel.resolveUsageAccessCta(needsDisclosure -> {
            if (!isAdded()) return;
            if (needsDisclosure) {
                new UsageDisclosureDialog().show(getChildFragmentManager(), "usage_disclosure");
            } else {
                openUsageAccessSettings();
            }
        });
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(UsageAccess.usageAccessSettingsIntent());
        } catch (ActivityNotFoundException e) {
            // Algunos dispositivos no exponen la pantalla; el estado seguirá LOCKED.
        }
    }

    private void openPicker() {
        startActivity(new Intent(requireContext(), AppPickerActivity.class));
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
