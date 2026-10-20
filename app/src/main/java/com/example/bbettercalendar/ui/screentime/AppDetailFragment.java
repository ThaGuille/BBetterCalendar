package com.example.bbettercalendar.ui.screentime;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.blocking.AccessibilityAccess;
import com.example.bbettercalendar.blocking.AccessibilityDisclosureDialog;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.chart.ChartStyler;
import com.example.bbettercalendar.ui.common.widget.SegmentedControlView;
import com.example.bbettercalendar.ui.common.widget.StatTileView;
import com.example.bbettercalendar.ui.common.widget.StepperView;
import com.example.bbettercalendar.ui.common.widget.WidgetTone;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * u20: per-app detail ({@code navigation_app_detail}, html 10): header (icon, name, category),
 * Week / Month, 3 tiles, stacked "within / over limit" bars with the limit line, "Daily limit"
 * stepper + presets (writes {@code AppRule.dailyLimitMinutes}), "Warn me before"
 * ({@code warnBeforeMinutes}), "Block when limit is reached" ({@code enforceAtLimit}) and "Stop
 * tracking" ({@code tracked = false}). No bottom nav (MainActivity hides it).
 */
@AndroidEntryPoint
public class AppDetailFragment extends Fragment
        implements AccessibilityDisclosureDialog.OnConsentGrantedListener,
        InsetsHelper.OwnsBottomInset {

    /** Nav argument (mobile_navigation.xml): package of the app whose detail is shown. */
    public static final String ARG_PACKAGE_NAME = "packageName";

    // PLACEHOLDER(ui-placeholders#app-detail-blocked-opens): no se cuentan aperturas bloqueadas.
    private static final int MOCK_BLOCKED_OPENS = 6;

    private static final String DISCLOSURE_TAG = "accessibility_disclosure";
    private static final int[] WARN_OPTIONS = {5, 10, 15, 30};
    private static final int FIRST_LIMIT_MINUTES = 30;   // "+" desde "sin limite"

    private AppDetailViewModel viewModel;
    private ChartStyler styler;
    private String packageName;
    private String appLabel;

    private StatTileView tileToday;
    private StatTileView tileAvg;
    private TextView chartTitle;
    private FrameLayout chartHolder;
    private BarChart chart;
    private TextView limitBadge;
    private TextView limitSubtitle;
    private StepperView stepper;
    private ChipGroup presets;
    private View warnRow;
    private TextView warnValue;
    private SwitchMaterial blockSwitch;
    private View a11yStatus;
    private View a11yDot;
    private TextView a11yText;
    private SegmentedControlView period;

    private boolean binding;
    private int currentLimit;
    private int currentWarn = 5;

    public AppDetailFragment() {
        super(R.layout.fragment_app_detail);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsHelper.apply(view.findViewById(R.id.appDetailScroll), InsetsHelper.BOTTOM);
        packageName = getArguments() != null ? getArguments().getString(ARG_PACKAGE_NAME) : null;
        if (packageName == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        viewModel = new ViewModelProvider(this).get(AppDetailViewModel.class);
        styler = new ChartStyler(requireContext());

        view.findViewById(R.id.appDetailBack).setOnClickListener(v -> navigateBack());
        bindHeader(view);

        tileToday = view.findViewById(R.id.appDetailTileToday);
        tileAvg = view.findViewById(R.id.appDetailTileAvg);
        chartTitle = view.findViewById(R.id.appDetailChartTitle);
        chartHolder = view.findViewById(R.id.appDetailChartHolder);
        limitBadge = view.findViewById(R.id.appDetailLimitBadge);
        limitSubtitle = view.findViewById(R.id.appDetailLimitSubtitle);
        stepper = view.findViewById(R.id.appDetailStepper);
        presets = view.findViewById(R.id.appDetailPresets);
        warnRow = view.findViewById(R.id.appDetailWarnRow);
        warnValue = view.findViewById(R.id.appDetailWarnValue);
        blockSwitch = view.findViewById(R.id.appDetailBlockSwitch);
        a11yStatus = view.findViewById(R.id.appDetailA11yStatus);
        a11yDot = view.findViewById(R.id.appDetailA11yDot);
        a11yText = view.findViewById(R.id.appDetailA11yText);
        period = view.findViewById(R.id.appDetailPeriod);
        TextView blockedCount = view.findViewById(R.id.appDetailBlockedCount);
        TextView stop = view.findViewById(R.id.appDetailStopTracking);

        blockedCount.setText(String.valueOf(MOCK_BLOCKED_OPENS));
        limitSubtitle.setText(getString(R.string.app_detail_limit_subtitle, appLabel));
        stop.setText(getString(R.string.app_detail_stop_tracking, appLabel));

        stepper.setValueFormatter(value -> value == 0
                ? getString(R.string.app_detail_value_none) : String.valueOf(value));
        stepper.setOnValueChangeListener((v, value) -> {
            // "+" desde "sin limite" salta a un primer limite razonable en vez de a 5 min.
            if (currentLimit == 0 && value > 0 && value < FIRST_LIMIT_MINUTES) {
                value = FIRST_LIMIT_MINUTES;
                stepper.setValue(value);
            }
            viewModel.setDailyLimit(value);
        });
        presets.setOnCheckedStateChangeListener((group, ids) -> {
            if (binding || ids.isEmpty()) return;
            viewModel.setDailyLimit(presetMinutes(ids.get(0)));
        });
        period.setOnSegmentSelectedListener((v, index) -> viewModel.setMonth(index == 1));
        period.setSelectedIndex(viewModel.isMonth() ? 1 : 0);
        warnRow.setOnClickListener(v -> showWarnDialog());
        blockSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (!binding) onBlockToggled(checked);
        });
        a11yStatus.setOnClickListener(v -> {
            if (!AccessibilityAccess.isEnabled(requireContext())) openAccessibilitySettings();
        });
        stop.setOnClickListener(v -> viewModel.stopTracking(() -> {
            if (isAdded()) navigateBack();
        }));

        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        viewModel.setPackage(packageName);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Al volver de Ajustes (accesibilidad / uso) no hay callback: re-leer todo.
        if (viewModel != null) {
            viewModel.refresh();
            renderAccessibility(blockSwitch != null && blockSwitch.isChecked());
        }
    }

    private void navigateBack() {
        if (!NavHostFragment.findNavController(this).navigateUp()) {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        }
    }

    // ---------------------------------------------------------------- header

    private void bindHeader(View root) {
        PackageManager pm = requireContext().getPackageManager();
        ImageView icon = root.findViewById(R.id.appDetailIcon);
        TextView name = root.findViewById(R.id.appDetailName);
        TextView subtitle = root.findViewById(R.id.appDetailSubtitle);

        appLabel = packageName;
        CharSequence category = null;
        Drawable drawable = null;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            if (label != null) appLabel = label.toString();
            drawable = pm.getApplicationIcon(ai);
            // La categoria solo existe desde API 26; antes (o sin declarar) se oculta.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && ai.category != ApplicationInfo.CATEGORY_UNDEFINED) {
                category = ApplicationInfo.getCategoryTitle(requireContext(), ai.category);
            }
        } catch (PackageManager.NameNotFoundException e) {
            // App desinstalada: se muestra el paquete y un icono neutro.
        }
        name.setText(appLabel);
        if (drawable != null) {
            icon.setImageDrawable(drawable);
        } else {
            icon.setImageResource(R.drawable.ic_shield_24);
            icon.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.bb_on_surface_muted)));
        }
        // El estado (Limited / No limit) lo completa render(); aqui solo la categoria.
        subtitle.setText(category != null ? category : "");
        subtitle.setTag(category);
    }

    private void renderSubtitle(boolean hasLimit) {
        View root = getView();
        if (root == null) return;
        TextView subtitle = root.findViewById(R.id.appDetailSubtitle);
        CharSequence category = (CharSequence) subtitle.getTag();
        String status = getString(hasLimit ? R.string.app_detail_status_limited
                : R.string.app_detail_status_no_limit);
        subtitle.setText(category != null && category.length() > 0
                ? getString(R.string.app_detail_subtitle_join, category, status) : status);
    }

    // ---------------------------------------------------------------- render

    private void render(@Nullable AppDetailState s) {
        if (s == null || getView() == null) return;
        binding = true;
        currentLimit = s.limitMinutes;
        currentWarn = s.warnBeforeMinutes;
        renderSubtitle(s.hasLimit());
        renderTiles(s);
        renderChart(s);
        renderLimit(s);
        renderProtection(s);
        binding = false;
    }

    private void renderTiles(AppDetailState s) {
        if (!s.usageAccess) {
            tileToday.setValue(getString(R.string.app_detail_value_none), null);
            tileToday.setSub(null);
            tileAvg.setValue(getString(R.string.app_detail_value_none), null);
            tileAvg.clearDelta();
            return;
        }
        setMinutesValue(tileToday, s.todayMillis);
        if (s.hasLimit()) {
            long remaining = s.limitMinutes * 60_000L - s.todayMillis;
            boolean over = remaining < 0;
            tileToday.setSub(getString(over ? R.string.app_detail_today_over
                    : R.string.app_detail_today_under, formatDuration(Math.abs(remaining))));
            tileToday.setSubTone(over ? WidgetTone.DANGER : WidgetTone.MUTED);
        } else {
            tileToday.setSub(getString(R.string.app_detail_today_no_limit));
            tileToday.setSubTone(WidgetTone.MUTED);
        }

        setMinutesValue(tileAvg, s.avgMillis);
        if (s.hasPrevious && s.deltaPercent != 0) {
            boolean down = s.deltaPercent < 0;
            tileAvg.setDelta(getString(R.string.app_detail_delta, Math.abs(s.deltaPercent)),
                    down ? StatTileView.Trend.DOWN : StatTileView.Trend.UP,
                    down ? WidgetTone.PRIMARY : WidgetTone.DANGER);
        } else {
            tileAvg.clearDelta();
        }
    }

    // "42" + "m" bajo la hora; "1h 5" + "m" desde ahi.
    private void setMinutesValue(StatTileView tile, long millis) {
        int minutes = (int) TimeUnit.MILLISECONDS.toMinutes(millis);
        String unit = getString(R.string.app_detail_unit_minutes_short);
        if (minutes < 60) {
            tile.setValue(String.valueOf(minutes), unit);
        } else {
            tile.setValue(getString(R.string.app_detail_value_hours_minutes,
                    minutes / 60, minutes % 60), unit);
        }
    }

    // Barras apiladas dentro / sobre el limite + linea del limite (html 10).
    private void renderChart(AppDetailState s) {
        chartTitle.setText(s.month ? R.string.app_detail_chart_title_month
                : R.string.app_detail_chart_title_week);
        if (chart == null) {
            chart = new BarChart(requireContext());
            chartHolder.addView(chart, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        int days = s.dayMillis.length;
        float limit = s.hasLimit() ? s.limitMinutes : Float.MAX_VALUE;
        List<BarEntry> entries = new ArrayList<>(days);
        float max = 0f;
        for (int i = 0; i < days; i++) {
            float minutes = s.dayMillis[i] / 60_000f;
            max = Math.max(max, minutes);
            entries.add(new BarEntry(i, ChartStyler.splitAtLimit(minutes, limit)));
        }
        BarDataSet set = styler.styleLimitBars(new BarDataSet(entries, ""));
        if (max > 0f) {
            // Punto honey sobre la barra de hoy (html 10).
            styler.markEntry(entries.get(days - 1));
            set.setDrawIcons(true);
        }
        BarData data = new BarData(set);
        data.setBarWidth(ChartStyler.barWidth());
        chart.setData(data);
        styler.styleChart(chart, dayLabels(s));
        chart.getXAxis().setLabelCount(days, false);
        float top = ChartStyler.niceMax(Math.max(Math.max(max, s.hasLimit() ? s.limitMinutes : 0f), 10f));
        styler.fitYAxis(chart, top);
        styler.setYAxisFormatter(chart, new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return formatDuration(Math.round(value) * 60_000L);
            }
        });
        if (s.hasLimit()) {
            styler.addLimitLine(chart, s.limitMinutes, formatDuration(s.limitMinutes * 60_000L));
        } else {
            chart.getAxisRight().removeAllLimitLines();
        }
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    // Semana: inicial del dia (M T W ...). Mes: dia del mes cada 5 dias hasta hoy.
    private String[] dayLabels(AppDetailState s) {
        int days = s.dayMillis.length;
        String[] labels = new String[days];
        for (int i = 0; i < days; i++) {
            LocalDate d = s.firstDay.plusDays(i);
            if (s.month) {
                labels[i] = (days - 1 - i) % 5 == 0 ? String.valueOf(d.getDayOfMonth()) : "";
            } else {
                labels[i] = d.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.getDefault());
            }
        }
        return labels;
    }

    private void renderLimit(AppDetailState s) {
        limitBadge.setText(s.hasLimit() ? R.string.app_detail_limit_active
                : R.string.app_detail_limit_off);
        limitBadge.setTextColor(ContextCompat.getColor(requireContext(),
                s.hasLimit() ? R.color.bb_primary_bright : R.color.bb_on_surface_muted));
        stepper.setValue(s.limitMinutes);
        stepper.setUnit(s.hasLimit() ? getString(R.string.app_detail_stepper_unit) : null);
        stepper.setCaption(getString(s.hasLimit() ? R.string.app_detail_stepper_caption
                : R.string.app_detail_stepper_caption_none));
        int checked;
        switch (s.limitMinutes) {
            case 30:
                checked = R.id.appDetailPreset30;
                break;
            case 45:
                checked = R.id.appDetailPreset45;
                break;
            case 60:
                checked = R.id.appDetailPreset60;
                break;
            case 0:
                checked = R.id.appDetailPresetNone;
                break;
            default:
                checked = View.NO_ID;
        }
        if (checked == View.NO_ID) {
            presets.clearCheck();
        } else {
            presets.check(checked);
        }
    }

    private static int presetMinutes(int chipId) {
        if (chipId == R.id.appDetailPreset30) return 30;
        if (chipId == R.id.appDetailPreset45) return 45;
        if (chipId == R.id.appDetailPreset60) return 60;
        return 0;
    }

    private void renderProtection(AppDetailState s) {
        warnValue.setText(getString(R.string.app_detail_warn_value, s.warnBeforeMinutes));
        // Sin limite no hay nada ante lo que avisar ni que hacer cumplir.
        warnRow.setEnabled(s.hasLimit());
        warnRow.setAlpha(s.hasLimit() ? 1f : 0.5f);
        blockSwitch.setChecked(s.enforceAtLimit);
        renderAccessibility(s.enforceAtLimit);
    }

    private void renderAccessibility(boolean enforce) {
        if (a11yText == null) return;
        boolean on = AccessibilityAccess.isEnabled(requireContext());
        a11yText.setText(on ? R.string.app_detail_a11y_enabled : R.string.app_detail_a11y_disabled);
        int color = ContextCompat.getColor(requireContext(),
                on ? R.color.bb_primary_bright : R.color.bb_accent_reward);
        a11yText.setTextColor(color);
        ViewCompat.setBackgroundTintList(a11yDot, ColorStateList.valueOf(color));
        a11yStatus.setClickable(!on);
    }

    // ---------------------------------------------------------------- formatting

    private String formatDuration(long millis) {
        int minutes = (int) TimeUnit.MILLISECONDS.toMinutes(millis);
        if (minutes < 60) return getString(R.string.app_detail_duration_m, minutes);
        if (minutes % 60 == 0) return getString(R.string.app_detail_duration_h, minutes / 60);
        return getString(R.string.app_detail_duration_hm, minutes / 60, minutes % 60);
    }

    // ---------------------------------------------------------------- warn before

    private void showWarnDialog() {
        if (currentLimit <= 0) return;
        String[] labels = new String[WARN_OPTIONS.length];
        int selected = -1;
        for (int i = 0; i < WARN_OPTIONS.length; i++) {
            labels[i] = getString(R.string.app_detail_warn_option, WARN_OPTIONS[i]);
            if (WARN_OPTIONS[i] == currentWarn) selected = i;
        }
        new AlertDialog.Builder(requireActivity(), R.style.RoundedDialog)
                .setTitle(R.string.app_detail_warn_dialog_title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    viewModel.setWarnBefore(WARN_OPTIONS[which]);
                    dialog.dismiss();
                })
                .show();
    }

    // ---------------------------------------------------------------- block (enforceAtLimit)

    private void onBlockToggled(boolean checked) {
        if (!checked) {
            viewModel.setEnforceAtLimit(false);
            return;
        }
        if (currentLimit <= 0) {
            // No se puede hacer cumplir lo que no existe.
            setSwitchSilently(false);
            Toast.makeText(requireContext(), R.string.app_detail_block_needs_limit,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (AccessibilityAccess.isEnabled(requireContext())) {
            viewModel.setEnforceAtLimit(true);
        } else {
            // Mismo criterio que Screen time: sin el servicio no se arma nada hasta que el
            // usuario acepte la divulgacion (onAccessibilityConsentGranted).
            setSwitchSilently(false);
            AccessibilityDisclosureDialog.newInstance().show(getChildFragmentManager(), DISCLOSURE_TAG);
        }
    }

    @Override
    public void onAccessibilityConsentGranted() {
        if (viewModel != null && currentLimit > 0) {
            viewModel.setEnforceAtLimit(true);
        }
    }

    private void setSwitchSilently(boolean on) {
        binding = true;
        blockSwitch.setChecked(on);
        binding = false;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(AccessibilityAccess.accessibilitySettingsIntent());
        } catch (ActivityNotFoundException e) {
            // Algunos dispositivos no exponen la pantalla; el estado seguira en "Off".
        }
    }
}
