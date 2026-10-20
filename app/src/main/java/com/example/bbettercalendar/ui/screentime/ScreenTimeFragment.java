package com.example.bbettercalendar.ui.screentime;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import android.content.res.ColorStateList;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.blocking.AccessibilityAccess;
import com.example.bbettercalendar.blocking.AccessibilityDisclosureDialog;
import com.example.bbettercalendar.blocking.BlockingSettings;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.example.bbettercalendar.ui.common.chart.ChartStyler;
import com.example.bbettercalendar.ui.common.widget.AppUsageRowView;
import com.example.bbettercalendar.ui.common.widget.StatTileView;
import com.example.bbettercalendar.ui.common.widget.WidgetTone;
import com.example.bbettercalendar.ui.focus.FocusSessionViewModel;
import com.example.bbettercalendar.ui.progress.UsageDisclosureDialog;
import com.example.bbettercalendar.ui.progress.apppicker.AppPickerActivity;
import com.example.bbettercalendar.usage.UsageAccess;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * u19: Screen time &amp; blocking ({@code navigation_screen_time}, html 14): 3 tiles, "Today by hour"
 * (usage line + FOCUS bands), "Manage your apps" ({@link AppUsageRowView} rows: lock =
 * {@code enforceAtLimit}, row / chevron = app detail, "Block a new app" = picker), "Focus shield"
 * and the permissions footer. Top bar with back; no bottom nav (MainActivity hides it).
 */
@AndroidEntryPoint
public class ScreenTimeFragment extends Fragment
        implements AccessibilityDisclosureDialog.OnConsentGrantedListener,
        InsetsHelper.OwnsBottomInset {

    // PLACEHOLDER(ui-placeholders#screen-time-blocked-opens): no se cuentan aperturas bloqueadas.
    private static final int MOCK_BLOCKED_OPENS = 23;

    private static final String DISCLOSURE_TAG = "accessibility_disclosure";
    private static final String USAGE_DISCLOSURE_TAG = "usage_disclosure";

    private ScreenTimeViewModel viewModel;
    private FocusSessionViewModel session;
    private ChartStyler styler;

    private StatTileView tileTracked;
    private StatTileView tileLimit;
    private TextView blockedCount;
    private FrameLayout chartHolder;
    private CombinedChart chart;
    private TextView protectedCount;
    private LinearLayout appRows;
    private TextView appsEmpty;
    private SwitchMaterial shieldSwitch;
    private View footer;
    private ImageView footerIcon;
    private TextView footerText;

    private boolean bindingShield;
    // Consentimiento de accesibilidad en curso: el candado de esta app, o el escudo.
    @Nullable private String pendingEnforcePackage;
    private boolean pendingShield;

    public ScreenTimeFragment() {
        super(R.layout.fragment_screen_time);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TopBarHelper.bind(this, view, R.string.title_screen_time);
        InsetsHelper.apply(view.findViewById(R.id.screenTimeScroll), InsetsHelper.BOTTOM);

        viewModel = new ViewModelProvider(this).get(ScreenTimeViewModel.class);
        session = new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class);
        styler = new ChartStyler(requireContext());

        tileTracked = view.findViewById(R.id.screenTimeTileTracked);
        tileLimit = view.findViewById(R.id.screenTimeTileLimit);
        blockedCount = view.findViewById(R.id.screenTimeBlockedCount);
        chartHolder = view.findViewById(R.id.screenTimeHourChartHolder);
        protectedCount = view.findViewById(R.id.screenTimeProtected);
        appRows = view.findViewById(R.id.screenTimeAppRows);
        appsEmpty = view.findViewById(R.id.screenTimeAppsEmpty);
        shieldSwitch = view.findViewById(R.id.screenTimeShieldSwitch);
        footer = view.findViewById(R.id.screenTimeFooter);
        footerIcon = view.findViewById(R.id.screenTimeFooterIcon);
        footerText = view.findViewById(R.id.screenTimeFooterText);

        tileTracked.setDeltaChip(false);
        blockedCount.setText(String.valueOf(MOCK_BLOCKED_OPENS));

        view.findViewById(R.id.screenTimeBlockNew).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AppPickerActivity.class)));
        shieldSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (!bindingShield) onShieldToggled(checked);
        });
        footer.setOnClickListener(v -> onFooterTapped());

        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Al volver de Ajustes (permisos), del picker o del detalle no hay callback: re-leer todo.
        viewModel.refresh();
        renderShield();
        renderFooter();
    }

    // ---------------------------------------------------------------- render

    private void render(@Nullable ScreenTimeState s) {
        if (s == null || getView() == null) return;
        renderTiles(s);
        renderHourChart(s);
        renderApps(s);
        renderFooter();
    }

    private void renderTiles(ScreenTimeState s) {
        if (!s.usageAccess) {
            tileTracked.setValue(getString(R.string.screen_time_value_none));
            tileTracked.clearDelta();
        } else {
            tileTracked.setValue(formatMillis(s.trackedMillis));
            if (s.hasAverage && s.deltaPercentVsAverage != 0) {
                boolean down = s.deltaPercentVsAverage < 0;
                tileTracked.setDelta(getString(R.string.screen_time_delta_vs_avg,
                                Math.abs(s.deltaPercentVsAverage)),
                        down ? StatTileView.Trend.DOWN : StatTileView.Trend.UP,
                        down ? WidgetTone.PRIMARY : WidgetTone.DANGER);
            } else {
                tileTracked.clearDelta();
            }
        }

        if (s.totalLimitMinutes > 0) {
            tileLimit.setValue(formatMillis(s.totalLimitMinutes * 60_000L));
            tileLimit.setProgress(s.limitPercent());
            tileLimit.setSub(getString(R.string.screen_time_limit_reached, s.limitPercent()));
        } else {
            tileLimit.setValue(getString(R.string.screen_time_value_none));
            tileLimit.setProgress(-1);
            tileLimit.setSub(getString(R.string.screen_time_limit_none));
        }
    }

    // Usage line (minutos por hora) + bandas FOCUS, como el grafico de html 14.
    private void renderHourChart(ScreenTimeState s) {
        if (chart == null) {
            chart = new CombinedChart(requireContext());
            chartHolder.addView(chart, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        List<Entry> line = new ArrayList<>(24);
        int peak = 0;
        float max = 0f;
        for (int h = 0; h < s.hourMillis.length; h++) {
            float minutes = s.hourMillis[h] / 60_000f;
            line.add(new Entry(h, minutes));
            if (minutes > max) {
                max = minutes;
                peak = h;
            }
        }
        LineDataSet usage = styler.styleUsageLine(new LineDataSet(line, getString(R.string.screen_time_legend_usage)));
        if (max > 0f) {
            styler.markEntry(usage.getEntryForIndex(peak));
            usage.setDrawIcons(true);
        }
        float top = ChartStyler.niceMax(Math.max(max, 10f));

        CombinedData data = new CombinedData();
        data.setData(new LineData(usage));
        data.setData(styler.focusBandsData(s.focusHours, top));
        styler.styleBandsChart(chart);
        chart.setData(data);
        styler.styleChart(chart, hourLabels());
        styler.fitYAxis(chart, top);
        chart.getAxisRight().setDrawLabels(false);
        chart.getXAxis().setLabelCount(24, false);
        chart.getXAxis().setAxisMinimum(-0.5f);
        chart.getXAxis().setAxisMaximum(23.5f);
        chart.getXAxis().setAvoidFirstLastClipping(true);
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    // 00:00 06:00 12:00 18:00 24:00 (la ultima franja, 23, hace de borde final del dia).
    private static String[] hourLabels() {
        String[] labels = new String[24];
        for (int h = 0; h < labels.length; h++) {
            labels[h] = "";
        }
        labels[0] = "00:00";
        labels[6] = "06:00";
        labels[12] = "12:00";
        labels[18] = "18:00";
        labels[23] = "24:00";
        return labels;
    }

    private void renderApps(ScreenTimeState s) {
        protectedCount.setText(getResources().getQuantityString(
                R.plurals.screen_time_protected, s.enforcedCount, s.enforcedCount));
        appRows.removeAllViews();
        appsEmpty.setVisibility(s.apps.isEmpty() ? View.VISIBLE : View.GONE);
        PackageManager pm = requireContext().getPackageManager();
        int gap = getResources().getDimensionPixelSize(R.dimen.spacing_card_gap);
        for (ScreenTimeState.AppRow app : s.apps) {
            AppUsageRowView row = new AppUsageRowView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = gap;
            appRows.addView(row, lp);
            bindRow(row, app, pm);
        }
    }

    private void bindRow(AppUsageRowView row, ScreenTimeState.AppRow app, PackageManager pm) {
        row.setAppName(app.label);
        row.setAppIcon(iconFor(pm, app.packageName));
        String used = formatMillis(app.todayMillis);
        if (!app.hasLimit()) {
            row.setNoLimit(true);
            row.setStatus(AppUsageRowView.Status.NONE, null);
            row.setUsage(getString(R.string.screen_time_row_no_limit, used), 0);
        } else {
            row.setNoLimit(false);
            String limit = formatMillis(app.limitMillis());
            if (app.isOverLimit()) {
                if (app.enforceAtLimit) {
                    row.setStatus(AppUsageRowView.Status.BLOCKED, getString(
                            R.string.screen_time_row_blocked_until,
                            getString(R.string.screen_time_midnight)));
                } else {
                    row.setStatus(AppUsageRowView.Status.WARNING,
                            getString(R.string.screen_time_row_over_limit));
                }
                row.setUsage(getString(R.string.screen_time_row_reached, used, limit), 100);
            } else {
                row.setStatus(app.isNearLimit() ? AppUsageRowView.Status.WARNING
                        : AppUsageRowView.Status.NONE, null);
                row.setUsage(getString(R.string.screen_time_row_of, used, limit),
                        app.percentOfLimit());
            }
        }
        row.setLocked(app.enforceAtLimit);
        row.setOnLockChangeListener((r, locked) -> onLockToggled(r, app, locked));
        row.setOnRowClickListener(r -> openDetail(app.packageName));
        row.setOnSetLimitClickListener(r -> openDetail(app.packageName));
    }

    @Nullable
    private static Drawable iconFor(PackageManager pm, String packageName) {
        try {
            return pm.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- lock (enforceAtLimit)

    private void onLockToggled(AppUsageRowView row, ScreenTimeState.AppRow app, boolean locked) {
        if (!locked) {
            viewModel.setEnforceAtLimit(app.packageName, false);
            return;
        }
        if (!app.hasLimit()) {
            // No se puede hacer cumplir lo que no existe: el limite se fija en el detalle.
            row.setLocked(false);
            Toast.makeText(requireContext(), R.string.screen_time_lock_needs_limit,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (AccessibilityAccess.isEnabled(requireContext())) {
            viewModel.setEnforceAtLimit(app.packageName, true);
        } else {
            // Mismo criterio que Progress: sin el servicio no se arma nada hasta que el usuario
            // acepte la divulgacion (onAccessibilityConsentGranted).
            row.setLocked(false);
            pendingEnforcePackage = app.packageName;
            pendingShield = false;
            AccessibilityDisclosureDialog.newInstance().show(getChildFragmentManager(), DISCLOSURE_TAG);
        }
    }

    private void openDetail(String packageName) {
        Bundle args = new Bundle();
        args.putString(AppDetailFragment.ARG_PACKAGE_NAME, packageName);
        NavHostFragment.findNavController(this).navigate(R.id.navigation_app_detail, args);
    }

    // ---------------------------------------------------------------- Focus shield

    // Semantica (app-limits.md): el escudo es la preferencia persistente "cada sesion de
    // concentracion arranca con el modo bloqueo total armado" (BlockingSettings). Activarlo
    // tambien arma la sesion en curso, como el boton de bloqueo de Focus; encenderlo exige el
    // servicio de accesibilidad (misma puerta que Focus).
    private void onShieldToggled(boolean checked) {
        if (!checked) {
            BlockingSettings.setFocusShieldEnabled(requireContext(), false);
            session.setBlockModeArmed(false);
            return;
        }
        if (AccessibilityAccess.isEnabled(requireContext())) {
            BlockingSettings.setFocusShieldEnabled(requireContext(), true);
            session.setBlockModeArmed(true);
        } else {
            setShieldSilently(false);
            pendingShield = true;
            pendingEnforcePackage = null;
            AccessibilityDisclosureDialog.newInstance().show(getChildFragmentManager(), DISCLOSURE_TAG);
        }
    }

    @Override
    public void onAccessibilityConsentGranted() {
        if (pendingShield) {
            BlockingSettings.setFocusShieldEnabled(requireContext(), true);
            session.setBlockModeArmed(true);
            setShieldSilently(true);
        } else if (pendingEnforcePackage != null) {
            viewModel.setEnforceAtLimit(pendingEnforcePackage, true);
        }
        pendingShield = false;
        pendingEnforcePackage = null;
    }

    private void renderShield() {
        setShieldSilently(BlockingSettings.isFocusShieldEnabled(requireContext()));
    }

    private void setShieldSilently(boolean on) {
        bindingShield = true;
        shieldSwitch.setChecked(on);
        bindingShield = false;
    }

    // ---------------------------------------------------------------- footer

    private void renderFooter() {
        if (footer == null) return;
        boolean usage = UsageAccess.hasUsageAccess(requireContext());
        boolean a11y = AccessibilityAccess.isEnabled(requireContext());
        String text = getString(R.string.screen_time_footer_join,
                getString(usage ? R.string.screen_time_footer_usage_on
                        : R.string.screen_time_footer_usage_off),
                getString(a11y ? R.string.screen_time_footer_a11y_on
                        : R.string.screen_time_footer_a11y_off));
        footerText.setText(text);
        footer.setContentDescription(text);
        boolean ok = usage && a11y;
        footerIcon.setImageResource(ok ? R.drawable.ic_check_24 : R.drawable.ic_info_24);
        ImageViewCompat.setImageTintList(footerIcon, ColorStateList.valueOf(ContextCompat.getColor(
                requireContext(), ok ? R.color.bb_primary_bright : R.color.bb_accent_reward)));
        footer.setClickable(!ok);
    }

    // Falta algo: tocar el pie lleva a concederlo (divulgacion primero si aun no hay consentimiento).
    private void onFooterTapped() {
        if (!UsageAccess.hasUsageAccess(requireContext())) {
            viewModel.resolveUsageAccessCta(needsDisclosure -> {
                if (!isAdded()) return;
                if (needsDisclosure) {
                    new UsageDisclosureDialog().show(getChildFragmentManager(), USAGE_DISCLOSURE_TAG);
                } else {
                    try {
                        startActivity(UsageAccess.usageAccessSettingsIntent());
                    } catch (ActivityNotFoundException e) {
                        // Algunos dispositivos no exponen la pantalla; el pie seguira avisando.
                    }
                }
            });
        } else if (!AccessibilityAccess.isEnabled(requireContext())) {
            pendingShield = false;
            pendingEnforcePackage = null;
            AccessibilityDisclosureDialog.newInstance().show(getChildFragmentManager(), DISCLOSURE_TAG);
        }
    }

    // ---------------------------------------------------------------- helpers

    // "1h 18m" / "1h" / "42m" (sin ceros de relleno: "1h of 1h").
    private static String formatMillis(long millis) {
        long totalMinutes = Math.max(0L, millis) / 60_000L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes == 0) return String.format(Locale.getDefault(), "%dh", hours);
        if (hours > 0) return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        return String.format(Locale.getDefault(), "%dm", minutes);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        chart = null;
        chartHolder = null;
        tileTracked = null;
        tileLimit = null;
        footer = null;
        appRows = null;
    }
}
