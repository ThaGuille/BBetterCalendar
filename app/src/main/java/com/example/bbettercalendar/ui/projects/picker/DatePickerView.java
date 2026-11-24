package com.example.bbettercalendar.ui.projects.picker;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.domain.DateChoices;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.ZoneId;
import java.util.Locale;

/**
 * Date picker (Today, Tomorrow, next Saturday, No date, Other = MaterialDatePicker) with its TIME row
 * (No time, 9:00, 17:00, 20:00, Other = MaterialTimePicker).
 */
public class DatePickerView extends PickerView {
    private static final int[] TIME_PRESETS = {9 * 60, 17 * 60, 20 * 60};

    private final Chip today;
    private final Chip tomorrow;
    private final Chip saturday;
    private final Chip none;
    private final Chip otherDay;
    private final Chip timeNone;
    private final Chip[] timeChips = new Chip[3];
    private final Chip timeOther;

    public DatePickerView(@NonNull Context context) {
        this(context, null);
    }

    public DatePickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LinearLayout c = content();
        ChipGroup days = addGroup(c);
        today = addChip(days, R.id.pickerDateToday, context.getString(R.string.projects_picker_date_today),
                v -> setDay(DateChoices.startOfToday(now(), zone())));
        tomorrow = addChip(days, R.id.pickerDateTomorrow, context.getString(R.string.projects_picker_date_tomorrow),
                v -> setDay(DateChoices.tomorrow(now(), zone())));
        saturday = addChip(days, R.id.pickerDateSat, "",
                v -> setDay(DateChoices.nextSaturday(now(), zone())));
        none = addChip(days, R.id.pickerDateNone, context.getString(R.string.projects_picker_date_none), v -> {
            if (draft != null) draft.setDay(null);
            changed();
        });
        otherDay = addChip(days, R.id.pickerDateOther, context.getString(R.string.projects_picker_other),
                v -> pickDay());

        addLabel(c, R.string.projects_picker_time_label);
        ChipGroup times = addGroup(c);
        timeNone = addChip(times, R.id.pickerTimeNone, context.getString(R.string.projects_picker_time_none), v -> {
            if (draft != null) draft.setTime(null);
            changed();
        });
        int[] ids = {R.id.pickerTime0900, R.id.pickerTime1700, R.id.pickerTime2000};
        for (int i = 0; i < TIME_PRESETS.length; i++) {
            final int minutes = TIME_PRESETS[i];
            timeChips[i] = addChip(times, ids[i], PickerLabels.timeLabel(minutes), v -> {
                if (draft != null) draft.setTime(minutes);
                changed();
            });
        }
        timeOther = addChip(times, R.id.pickerTimeOther, context.getString(R.string.projects_picker_other),
                v -> pickTime());
    }

    @Override
    protected int headerIconRes() {
        return R.drawable.ic_calendar_24;
    }

    @Override
    protected int titleRes() {
        return R.string.projects_picker_title_date;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    private void setDay(long dayStart) {
        if (draft != null) draft.setDay(dayStart);
        changed();
    }

    @Override
    public void refresh() {
        if (draft == null) return;
        Locale locale = Locale.getDefault();
        long now = now();
        ZoneId zone = zone();
        long todayStart = DateChoices.startOfToday(now, zone);
        long tomorrowStart = DateChoices.tomorrow(now, zone);
        long satStart = DateChoices.nextSaturday(now, zone);
        // When tomorrow already is the Saturday, the two chips would be the same day: show only Tomorrow.
        boolean satShown = satStart != tomorrowStart;
        saturday.setVisibility(satShown ? View.VISIBLE : View.GONE);
        saturday.setText(PickerLabels.nextSaturdayLabel(now, zone, locale));

        Long day = draft.getDayStartMillis();
        today.setChecked(day != null && day == todayStart);
        tomorrow.setChecked(day != null && day == tomorrowStart);
        saturday.setChecked(satShown && day != null && day == satStart);
        none.setChecked(day == null);
        boolean customDay = day != null && day != todayStart && day != tomorrowStart && !(satShown && day == satStart);
        otherDay.setChecked(customDay);
        otherDay.setText(customDay ? PickerLabels.dayLabel(day, zone, locale)
                : getContext().getString(R.string.projects_picker_other));

        Integer time = draft.getTimeOfDayMinutes();
        timeNone.setChecked(time == null);
        boolean preset = false;
        for (int i = 0; i < TIME_PRESETS.length; i++) {
            boolean on = time != null && time == TIME_PRESETS[i];
            timeChips[i].setChecked(on);
            preset |= on;
        }
        boolean customTime = time != null && !preset;
        timeOther.setChecked(customTime);
        timeOther.setText(customTime ? PickerLabels.timeLabel(time)
                : getContext().getString(R.string.projects_picker_other));
    }

    private void pickDay() {
        if (draft == null || host == null) return;
        long base = draft.getDayStartMillis() != null ? draft.getDayStartMillis()
                : DateChoices.startOfToday(now(), zone());
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.projects_picker_date_dialog_title)
                .setSelection(PickerLabels.dayToPickerSelection(base, zone()))
                .build();
        picker.addOnPositiveButtonClickListener(selection ->
                setDay(PickerLabels.pickerSelectionToDay(selection, zone())));
        picker.show(host.getPickerFragmentManager(), "projects_picker_date");
        refresh(); // the Other chip toggled itself on the tap: restore it until a day is chosen
    }

    private void pickTime() {
        if (draft == null || host == null) return;
        int minutes = draft.getTimeOfDayMinutes() != null ? draft.getTimeOfDayMinutes() : 9 * 60;
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(getContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(minutes / 60)
                .setMinute(minutes % 60)
                .setTitleText(R.string.projects_picker_time_dialog_title)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            if (draft != null) draft.setTime(picker.getHour() * 60 + picker.getMinute());
            changed();
        });
        picker.show(host.getPickerFragmentManager(), "projects_picker_time");
        refresh();
    }
}
