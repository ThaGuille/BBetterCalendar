package com.example.bbettercalendar.ui.notifications;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationOffsets;
import com.example.bbettercalendar.notifications.NotificationPrefs;
import com.example.bbettercalendar.notifications.PermissionHelper;
import com.example.bbettercalendar.notifications.nudge.DailyNudgeScheduler;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;

/**
 * Pantalla Notificaciones (plan sistema-notificaciones, parte D): estado del permiso, recordatorio por
 * defecto, aviso de fin de fase, avisos diarios (interruptor + hora), resumen semanal y enlaces a los
 * canales del sistema. Todo se guarda al instante en {@link NotificationPrefs}; los cambios de avisos
 * diarios llaman a {@link DailyNudgeScheduler#rescheduleAll}.
 */
public class NotificationSettingsFragment extends Fragment {

    private NotificationPrefs prefs;
    private View statusCard;
    private TextView reminderValue;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) {
                    return;
                }
                if (!granted && !shouldShowRequestPermissionRationale(
                        Manifest.permission.POST_NOTIFICATIONS)) {
                    // Denegado para siempre (o el sistema ya no pregunta): sólo queda Ajustes.
                    openAppNotificationSettings();
                }
                refreshStatus();
            });

    public NotificationSettingsFragment() {
        super(R.layout.fragment_notification_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = NotificationPrefs.from(requireContext());
        TopBarHelper.bind(this, view, R.string.notif_settings_title);
        InsetsHelper.apply(view.findViewById(R.id.notifSettingsScroll), InsetsHelper.BOTTOM);

        statusCard = view.findViewById(R.id.notifStatusCard);
        view.findViewById(R.id.notifTurnOnButton).setOnClickListener(v -> onTurnOn());

        buildReminders((ViewGroup) view.findViewById(R.id.notifRemindersRows));
        buildFocus((ViewGroup) view.findViewById(R.id.notifFocusRows));
        buildDaily((ViewGroup) view.findViewById(R.id.notifDailyRows));
        buildWeekly((ViewGroup) view.findViewById(R.id.notifWeeklyRows));
        buildMore((ViewGroup) view.findViewById(R.id.notifMoreRows));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        statusCard = null;
        reminderValue = null;
    }

    // ---- Permiso ----

    private void refreshStatus() {
        if (statusCard != null) {
            statusCard.setVisibility(
                    PermissionHelper.notificationsGranted(requireContext()) ? View.GONE : View.VISIBLE);
        }
    }

    private void onTurnOn() {
        if (PermissionHelper.requiresRuntimePostNotificationsPermission()) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            openAppNotificationSettings();
        }
    }

    private void openAppNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        startActivity(intent);
    }

    // ---- Secciones ----

    private void buildReminders(ViewGroup parent) {
        View row = newRow(parent);
        setTexts(row, R.string.notif_settings_default_reminder, 0);
        reminderValue = row.findViewById(R.id.notifRowValue);
        reminderValue.setVisibility(View.VISIBLE);
        row.findViewById(R.id.notifRowChevron).setVisibility(View.VISIBLE);
        renderReminderValue();
        row.setOnClickListener(v -> showReminderDialog());
    }

    private void renderReminderValue() {
        int index = prefs.getDefaultReminderOffsetIndex();
        reminderValue.setText(index == NotificationPrefs.NO_DEFAULT_OFFSET
                ? getString(R.string.notif_settings_default_reminder_none)
                : NotificationOffsets.labelFor(requireContext(), index));
    }

    private void showReminderDialog() {
        int count = NotificationOffsets.count();
        String[] labels = new String[count + 1];
        labels[0] = getString(R.string.notif_settings_default_reminder_none);
        for (int i = 0; i < count; i++) {
            labels[i + 1] = NotificationOffsets.labelFor(requireContext(), i);
        }
        int current = prefs.getDefaultReminderOffsetIndex();
        int checked = current == NotificationPrefs.NO_DEFAULT_OFFSET ? 0 : current + 1;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.notif_settings_default_reminder_dialog)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    prefs.setDefaultReminderOffsetIndex(
                            which == 0 ? NotificationPrefs.NO_DEFAULT_OFFSET : which - 1);
                    if (reminderValue != null) {
                        renderReminderValue();
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void buildFocus(ViewGroup parent) {
        View row = newRow(parent);
        setTexts(row, R.string.notif_settings_focus_end, R.string.notif_settings_focus_end_desc);
        SwitchMaterial toggle = row.findViewById(R.id.notifRowSwitch);
        toggle.setVisibility(View.VISIBLE);
        toggle.setContentDescription(getString(R.string.notif_settings_focus_end));
        toggle.setChecked(prefs.isFocusPhaseEndEnabled());
        toggle.setOnCheckedChangeListener((b, checked) -> prefs.setFocusPhaseEndEnabled(checked));
        row.setOnClickListener(v -> toggle.toggle());
    }

    private void buildDaily(ViewGroup parent) {
        buildNudgeRow(parent, NotificationPrefs.NUDGE_MORNING,
                R.string.notif_settings_morning, R.string.notif_settings_morning_desc, false);
        buildNudgeRow(parent, NotificationPrefs.NUDGE_STREAK,
                R.string.notif_settings_streak, R.string.notif_settings_streak_desc, false);
        buildNudgeRow(parent, NotificationPrefs.NUDGE_EVENING_PLAN,
                R.string.notif_settings_evening, R.string.notif_settings_evening_desc, false);
    }

    private void buildWeekly(ViewGroup parent) {
        buildNudgeRow(parent, NotificationPrefs.NUDGE_WEEKLY,
                R.string.notif_settings_weekly, R.string.notif_settings_weekly_desc, true);
    }

    /** Fila con título, subtítulo, hora (toca la fila para cambiarla) e interruptor. */
    private void buildNudgeRow(ViewGroup parent, String key, @StringRes int title,
                               @StringRes int subtitle, boolean weekly) {
        View row = newRow(parent);
        setTexts(row, title, subtitle);
        TextView value = row.findViewById(R.id.notifRowValue);
        SwitchMaterial toggle = row.findViewById(R.id.notifRowSwitch);
        value.setVisibility(View.VISIBLE);
        toggle.setVisibility(View.VISIBLE);
        toggle.setContentDescription(getString(title));

        renderNudgeTime(value, key, weekly);
        value.setAlpha(prefs.isNudgeEnabled(key) ? 1f : DIMMED_ALPHA);
        toggle.setChecked(prefs.isNudgeEnabled(key));
        toggle.setOnCheckedChangeListener((b, checked) -> {
            prefs.setNudgeEnabled(key, checked);
            value.setAlpha(checked ? 1f : DIMMED_ALPHA);
            DailyNudgeScheduler.rescheduleAll(requireContext());
        });
        row.setOnClickListener(v -> showTimePicker(key, value, weekly));
    }

    private static final float DIMMED_ALPHA = 0.38f;

    private void renderNudgeTime(TextView value, String key, boolean weekly) {
        String time = formatTime(prefs.getNudgeMinutes(key));
        value.setText(weekly ? getString(R.string.notif_settings_weekly_time, time) : time);
    }

    private String formatTime(int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, NudgeTimes.hourOf(minutes));
        cal.set(Calendar.MINUTE, NudgeTimes.minuteOf(minutes));
        return DateFormat.getTimeFormat(requireContext()).format(cal.getTime());
    }

    private void showTimePicker(String key, TextView value, boolean weekly) {
        int minutes = NudgeTimes.clamp(prefs.getNudgeMinutes(key));
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(requireContext())
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(NudgeTimes.hourOf(minutes))
                .setMinute(NudgeTimes.minuteOf(minutes))
                .setTitleText(R.string.notif_settings_pick_time)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            int picked = NudgeTimes.toMinutes(picker.getHour(), picker.getMinute());
            int clamped = NudgeTimes.clamp(picked);
            if (clamped != picked && getView() != null) {
                Snackbar.make(getView(), R.string.notif_settings_time_range_hint,
                        Snackbar.LENGTH_SHORT).show();
            }
            prefs.setNudgeMinutes(key, clamped);
            if (isAdded()) {
                renderNudgeTime(value, key, weekly);
                DailyNudgeScheduler.rescheduleAll(requireContext());
            }
        });
        picker.show(getChildFragmentManager(), "notif_time_" + key);
    }

    private void buildMore(ViewGroup parent) {
        buildChannelRow(parent, R.string.notif_settings_channel_usage,
                NotificationChannels.CHANNEL_USAGE_LIMITS);
        buildChannelRow(parent, R.string.notif_settings_channel_friends,
                NotificationChannels.CHANNEL_FRIENDS);
        buildChannelRow(parent, R.string.notif_settings_channel_projects,
                NotificationChannels.CHANNEL_PROJECT_DEADLINES);
    }

    private void buildChannelRow(ViewGroup parent, @StringRes int title, String channelId) {
        View row = newRow(parent);
        setTexts(row, title, R.string.notif_settings_channel_desc);
        row.findViewById(R.id.notifRowChevron).setVisibility(View.VISIBLE);
        row.setOnClickListener(v -> openChannelSettings(channelId));
    }

    private void openChannelSettings(String channelId) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
        } else {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        }
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        startActivity(intent);
    }

    // ---- Filas ----

    private View newRow(ViewGroup parent) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.view_notif_row, parent, false);
        parent.addView(row);
        return row;
    }

    private void setTexts(View row, @StringRes int title, @StringRes int subtitle) {
        ((TextView) row.findViewById(R.id.notifRowTitle)).setText(title);
        if (subtitle != 0) {
            TextView sub = row.findViewById(R.id.notifRowSubtitle);
            sub.setText(subtitle);
            sub.setVisibility(View.VISIBLE);
        }
    }
}
