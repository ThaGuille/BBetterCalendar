package com.example.bbettercalendar.ui.screentime;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.blocking.AccessibilityAccess;
import com.example.bbettercalendar.blocking.AccessibilityDisclosureDialog;
import com.example.bbettercalendar.blocking.BlockingSettings;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.example.bbettercalendar.ui.common.chart.ChartFormat;
import com.example.bbettercalendar.ui.common.chart.ChartFormatter;
import com.example.bbettercalendar.ui.common.chart.DumbbellRowView;
import com.example.bbettercalendar.ui.common.chart.LimitBarView;
import com.example.bbettercalendar.ui.common.chart.StackedBarsView;
import com.example.bbettercalendar.ui.common.widget.AppUsageRowView;
import com.example.bbettercalendar.ui.common.widget.SegmentedControlView;
import com.example.bbettercalendar.ui.focus.FocusSessionViewModel;
import com.example.bbettercalendar.ui.progress.UsageDisclosureDialog;
import com.example.bbettercalendar.ui.progress.apppicker.AppPickerActivity;
import com.example.bbettercalendar.usage.UsageAccess;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Screen time ({@code navigation_screen_time}, mockups 20 and 21), in this order: focus shield (with the
 * "N apps always allowed" row), Week / Month, usage per app ({@link AppUsageRowView} rows; tap = app detail,
 * "Limit another app" = picker), day by day ({@link StackedBarsView}), before and after the limit
 * ({@link DumbbellRowView}), always allowed (content in the allow-list step) and the permissions footer.
 * Top bar with back; no bottom nav (MainActivity hides it).
 */
@AndroidEntryPoint
public class ScreenTimeFragment extends Fragment
        implements AccessibilityDisclosureDialog.OnConsentGrantedListener,
        InsetsHelper.OwnsBottomInset {

    private static final String DISCLOSURE_TAG = "accessibility_disclosure";
    private static final String USAGE_DISCLOSURE_TAG = "usage_disclosure";
    private static final int[] DAILY_COLORS = {
            R.color.bb_cat_1, R.color.bb_cat_2, R.color.bb_cat_3, R.color.bb_cat_4};

    private ScreenTimeViewModel viewModel;
    private FocusSessionViewModel session;

    private SwitchMaterial shieldSwitch;
    private TextView allowedCount;
    private SegmentedControlView period;
    private TextView usageSubtitle;
    private TextView avgValue;
    private TextView avgDelta;
    private LinearLayout appRows;
    private TextView appsEmpty;
    private StackedBarsView dailyChart;
    private ViewGroup dailyLegend;
    private View dailySentence;
    private TextView dailySentenceText;
    private LinearLayout beforeAfterRows;
    private TextView beforeAfterEmpty;
    private View beforeAfterLegend;
    private View beforeAfterSentence;
    private TextView beforeAfterSentenceText;
    private View footer;
    private ImageView footerIcon;
    private TextView footerText;

    private boolean bindingShield;
    // Consentimiento de accesibilidad en curso para encender el escudo.
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

        shieldSwitch = view.findViewById(R.id.screenTimeShieldSwitch);
        allowedCount = view.findViewById(R.id.screenTimeAllowedCount);
        period = view.findViewById(R.id.screenTimePeriod);
        usageSubtitle = view.findViewById(R.id.screenTimeUsageSubtitle);
        avgValue = view.findViewById(R.id.screenTimeAvgValue);
        avgDelta = view.findViewById(R.id.screenTimeAvgDelta);
        appRows = view.findViewById(R.id.screenTimeAppRows);
        appsEmpty = view.findViewById(R.id.screenTimeAppsEmpty);
        dailyChart = view.findViewById(R.id.screenTimeDailyChart);
        dailyLegend = view.findViewById(R.id.screenTimeDailyLegend);
        dailySentence = view.findViewById(R.id.screenTimeDailySentence);
        dailySentenceText = view.findViewById(R.id.screenTimeDailySentenceText);
        beforeAfterRows = view.findViewById(R.id.screenTimeBeforeAfterRows);
        beforeAfterEmpty = view.findViewById(R.id.screenTimeBeforeAfterEmpty);
        beforeAfterLegend = view.findViewById(R.id.screenTimeBeforeAfterLegend);
        beforeAfterSentence = view.findViewById(R.id.screenTimeBeforeAfterSentence);
        beforeAfterSentenceText = view.findViewById(R.id.screenTimeBeforeAfterSentenceText);
        footer = view.findViewById(R.id.screenTimeFooter);
        footerIcon = view.findViewById(R.id.screenTimeFooterIcon);
        footerText = view.findViewById(R.id.screenTimeFooterText);

        view.findViewById(R.id.screenTimeBlockNew).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AppPickerActivity.class)));
        shieldSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (!bindingShield) onShieldToggled(checked);
        });
        footer.setOnClickListener(v -> onFooterTapped());
        period.setOnSegmentSelectedListener((v, index) -> viewModel.setMonth(index == 1));
        period.setSelectedIndex(viewModel.isMonth() ? 1 : 0);

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
        allowedCount.setText(getResources().getQuantityString(
                R.plurals.screen_time_allowed_count, s.allowedCount, s.allowedCount));
        renderUsage(s);
        renderDaily(s);
        renderBeforeAfter(s);
        renderFooter();
    }

    // ---- 3. Uso por app

    private void renderUsage(ScreenTimeState s) {
        usageSubtitle.setText(s.month ? R.string.screen_time_usage_subtitle_month
                : R.string.screen_time_usage_subtitle_week);
        boolean hasData = !s.apps.isEmpty();
        avgValue.setText(hasData ? spannedDuration(s.avgMinutesPerDay)
                : getString(R.string.screen_time_value_none));
        renderDelta(s, hasData);

        appRows.removeAllViews();
        appsEmpty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        appsEmpty.setText(s.usageAccess ? R.string.screen_time_usage_empty
                : R.string.screen_time_usage_no_access);
        PackageManager pm = requireContext().getPackageManager();
        float scaleMax = 0f;
        for (ScreenTimeState.AppRow app : s.apps) {
            scaleMax = Math.max(scaleMax, Math.max(app.avgMinutes, app.limitMinutes));
        }
        int gap = getResources().getDimensionPixelSize(R.dimen.screen_time_row_gap);
        boolean first = true;
        for (ScreenTimeState.AppRow app : s.apps) {
            AppUsageRowView row = new AppUsageRowView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = first ? 0 : gap;
            first = false;
            appRows.addView(row, lp);
            bindRow(row, app, scaleMax, pm);
        }
    }

    private void renderDelta(ScreenTimeState s, boolean hasData) {
        if (!hasData || !s.hasDelta || s.deltaPercent == 0) {
            avgDelta.setVisibility(View.GONE);
            return;
        }
        boolean down = s.deltaPercent < 0;
        int value = Math.abs(s.deltaPercent);
        avgDelta.setText((down ? "▼ " : "▲ ") + ChartFormat.percent(value));
        avgDelta.setTextColor(ContextCompat.getColor(requireContext(),
                down ? R.color.bb_primary_bright : R.color.bb_danger));
        avgDelta.setContentDescription(getString(down ? R.string.screen_time_delta_less_cd
                : R.string.screen_time_delta_more_cd, value));
        avgDelta.setVisibility(View.VISIBLE);
    }

    private void bindRow(AppUsageRowView row, ScreenTimeState.AppRow app, float scaleMax,
                         PackageManager pm) {
        row.setAppName(app.label);
        row.setAppIcon(iconFor(pm, app.packageName));
        String used = ChartFormat.hoursMinutes(app.avgMinutes);
        row.setValue(app.hasLimit() ? getString(R.string.screen_time_row_value_of, used,
                ChartFormat.hoursMinutes(app.limitMinutes)) : used);
        row.setBar(new LimitBarView.Data(app.avgMinutes, app.limitMinutes, scaleMax,
                ChartFormatter.MINUTES));
        row.setLocked(app.hasLimit() && app.enforceAtLimit);
        row.setSuggested(app.suggested && !app.hasLimit());
        row.setOnRowClickListener(r -> openDetail(app.packageName));
    }

    @Nullable
    private static Drawable iconFor(PackageManager pm, String packageName) {
        try {
            return pm.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    // "3 h 10 m": cifras grandes y las unidades a mitad de tamano.
    private static CharSequence spannedDuration(float minutes) {
        String text = ChartFormat.hoursMinutes(minutes);
        SpannableString out = new SpannableString(text);
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                out.setSpan(new RelativeSizeSpan(0.55f), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return out;
    }

    private void openDetail(String packageName) {
        Bundle args = new Bundle();
        args.putString(AppDetailFragment.ARG_PACKAGE_NAME, packageName);
        NavHostFragment.findNavController(this).navigate(R.id.navigation_app_detail, args);
    }

    // ---- 4. Dia a dia

    private void renderDaily(ScreenTimeState s) {
        String[] names = new String[DAILY_COLORS.length];
        for (int i = 0; i < s.dailySeriesNames.length; i++) {
            names[i] = s.dailySeriesNames[i] == null ? "" : s.dailySeriesNames[i];
        }
        names[names.length - 1] = getString(R.string.screen_time_others);
        int days = s.dailyValues.length;
        String[] labels = new String[days];
        for (int i = 0; i < days; i++) {
            LocalDate d = s.firstDay.plusDays(i);
            if (s.month) {
                labels[i] = (days - 1 - i) % 5 == 0 ? String.valueOf(d.getDayOfMonth()) : "";
            } else {
                labels[i] = WeekdayLabels.initial(d.getDayOfWeek());
            }
        }
        dailyChart.setData(days == 0 ? null : new StackedBarsView.Data(s.dailyValues, DAILY_COLORS,
                names, labels, true, ChartFormatter.MINUTES));

        dailyLegend.removeAllViews();
        if (days > 0) {
            for (int i = 0; i < names.length; i++) {
                if (!names[i].isEmpty()) addLegendItem(dailyLegend, DAILY_COLORS[i], names[i]);
            }
        }
        if (s.peakWeekday >= 0) {
            String day = DayOfWeek.of(s.peakWeekday + 1).getDisplayName(TextStyle.FULL,
                    Locale.getDefault());
            dailySentenceText.setText(getString(R.string.screen_time_daily_peak,
                    day, ChartFormat.hoursMinutes(s.peakWeekdayMinutes)));
            dailySentence.setVisibility(View.VISIBLE);
        } else {
            dailySentence.setVisibility(View.GONE);
        }
    }

    private void addLegendItem(ViewGroup parent, int colorRes, String label) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int dot = getResources().getDimensionPixelSize(R.dimen.screen_time_legend_dot);
        View swatch = new View(requireContext());
        swatch.setBackgroundResource(R.drawable.bg_dot_primary);
        swatch.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), colorRes)));
        item.addView(swatch, new LinearLayout.LayoutParams(dot, dot));
        TextView text = new TextView(requireContext());
        TextViewCompat.setTextAppearance(text, R.style.TextAppearance_BBetter_Micro);
        text.setText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.spacing_xs) + 2);
        item.addView(text, lp);
        parent.addView(item);
    }

    // ---- 5. Antes y despues del limite

    private void renderBeforeAfter(ScreenTimeState s) {
        beforeAfterRows.removeAllViews();
        boolean has = !s.beforeAfter.isEmpty();
        beforeAfterEmpty.setVisibility(has ? View.GONE : View.VISIBLE);
        beforeAfterLegend.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            float scaleMax = 0f;
            for (ScreenTimeState.BeforeAfterRow r : s.beforeAfter) {
                scaleMax = Math.max(scaleMax, Math.max(r.before, r.after));
            }
            String before = getString(R.string.screen_time_ba_before);
            String after = getString(R.string.screen_time_ba_after);
            for (ScreenTimeState.BeforeAfterRow r : s.beforeAfter) {
                DumbbellRowView row = new DumbbellRowView(requireContext());
                row.setData(new DumbbellRowView.Data(r.label, r.before, r.after, scaleMax, before,
                        after, ChartFormatter.MINUTES));
                beforeAfterRows.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        if (has && Math.round(s.recoveredMinutesPerDay) > 0) {
            beforeAfterSentenceText.setText(getString(R.string.screen_time_ba_recovered,
                    ChartFormat.hoursMinutes(s.recoveredMinutesPerDay)));
            beforeAfterSentence.setVisibility(View.VISIBLE);
        } else {
            beforeAfterSentence.setVisibility(View.GONE);
        }
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
            AccessibilityDisclosureDialog.newInstance().show(getChildFragmentManager(), DISCLOSURE_TAG);
        }
    }

    @Override
    public void onAccessibilityConsentGranted() {
        if (pendingShield) {
            BlockingSettings.setFocusShieldEnabled(requireContext(), true);
            session.setBlockModeArmed(true);
            setShieldSilently(true);
        }
        pendingShield = false;
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
            AccessibilityDisclosureDialog.newInstance().show(getChildFragmentManager(), DISCLOSURE_TAG);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        shieldSwitch = null;
        allowedCount = null;
        period = null;
        usageSubtitle = null;
        avgValue = null;
        avgDelta = null;
        appRows = null;
        appsEmpty = null;
        dailyChart = null;
        dailyLegend = null;
        dailySentence = null;
        dailySentenceText = null;
        beforeAfterRows = null;
        beforeAfterEmpty = null;
        beforeAfterLegend = null;
        beforeAfterSentence = null;
        beforeAfterSentenceText = null;
        footer = null;
        footerIcon = null;
        footerText = null;
    }
}
