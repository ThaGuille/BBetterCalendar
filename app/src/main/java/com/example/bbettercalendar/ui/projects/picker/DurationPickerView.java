package com.example.bbettercalendar.ui.projects.picker;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Duration picker: Free, 25 min, 50 min, 1 h 30, 2 h and Other (a minutes input), plus the switches
 * "Pomodoro: when it ends, it completes" (amber timer icon) and "Soft block when focusing".
 */
public class DurationPickerView extends PickerView {
    private static final int[] PRESETS = {0, 25, 50, 90, 120};
    private static final int MAX_MINUTES = 24 * 60;

    private final Chip[] presetChips = new Chip[PRESETS.length];
    private final Chip other;
    private final EditText input;
    private final SwitchMaterial pomodoro;
    private final SwitchMaterial block;
    private boolean otherOpen;
    private boolean refreshing;

    public DurationPickerView(@NonNull Context context) {
        this(context, null);
    }

    public DurationPickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LinearLayout c = content();
        ChipGroup group = addGroup(c);
        int[] ids = {R.id.pickerDurFree, R.id.pickerDur25, R.id.pickerDur50, R.id.pickerDur90, R.id.pickerDur120};
        for (int i = 0; i < PRESETS.length; i++) {
            final int minutes = PRESETS[i];
            String label = minutes == 0 ? context.getString(R.string.projects_picker_dur_free)
                    : PickerLabels.durationLabel(minutes);
            presetChips[i] = addChip(group, ids[i], label, v -> {
                otherOpen = false;
                if (draft != null) draft.setDurationMinutes(minutes);
                changed();
            });
        }
        other = addChip(group, R.id.pickerDurOther, context.getString(R.string.projects_picker_other), v -> {
            otherOpen = true;
            refresh();
            View field = findViewById(R.id.pickerDurInput);
            if (field != null) field.requestFocus();
        });

        input = (EditText) LayoutInflater.from(context).inflate(R.layout.picker_input, c, false);
        input.setId(R.id.pickerDurInput);
        input.setHint(R.string.projects_picker_dur_minutes_hint);
        input.setContentDescription(context.getString(R.string.projects_picker_dur_minutes_desc));
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(true);
        input.setVisibility(GONE);
        input.setOnEditorActionListener((v, actionId, event) -> {
            commitMinutes();
            return true;
        });
        c.addView(input);

        pomodoro = addSwitch(c, R.id.pickerPomodoroSwitch, R.drawable.ic_timer_24, R.color.bb_sanct_primary,
                R.string.projects_picker_dur_pomodoro, on -> {
                    if (draft != null) draft.setPomodoro(on);
                    changed();
                });
        block = addSwitch(c, R.id.pickerBlockSwitch, R.drawable.ic_lock_24, R.color.bb_on_surface_muted,
                R.string.projects_picker_dur_block, on -> {
                    if (draft != null) draft.setBlockOnFocus(on);
                    changed();
                });
    }

    private interface OnSwitch {
        void onChanged(boolean on);
    }

    private SwitchMaterial addSwitch(LinearLayout parent, @IdRes int switchId, @DrawableRes int icon, int colorRes,
                                     int label, OnSwitch listener) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.picker_switch_row, parent, false);
        ((ImageView) row.findViewById(R.id.pickerSwitchIcon)).setImageDrawable(tintedIcon(icon, colorRes));
        ((TextView) row.findViewById(R.id.pickerSwitchLabel)).setText(label);
        SwitchMaterial sw = row.findViewById(R.id.pickerSwitch);
        sw.setId(switchId);
        sw.setContentDescription(getContext().getString(label));
        sw.setOnCheckedChangeListener((b, on) -> {
            if (!refreshing) listener.onChanged(on);
        });
        parent.addView(row);
        return sw;
    }

    @Override
    protected int headerIconRes() {
        return R.drawable.ic_timer_24;
    }

    @Override
    protected int titleRes() {
        return R.string.projects_picker_title_duration;
    }

    private void commitMinutes() {
        if (draft == null) return;
        String text = input.getText().toString().trim();
        if (!text.isEmpty()) {
            try {
                int minutes = Math.min(MAX_MINUTES, Integer.parseInt(text));
                draft.setDurationMinutes(minutes);
                otherOpen = false;
            } catch (NumberFormatException ignored) {
                // digits only (inputType number) but longer than an int: keep the previous value
            }
        }
        InputMethodManager imm = ContextCompat.getSystemService(getContext(), InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
        changed();
    }

    @Override
    public void refresh() {
        if (draft == null) return;
        int minutes = draft.getDurationMinutes();
        boolean preset = false;
        for (int i = 0; i < PRESETS.length; i++) {
            boolean on = !otherOpen && minutes == PRESETS[i];
            presetChips[i].setChecked(on);
            preset |= minutes == PRESETS[i];
        }
        boolean custom = otherOpen || !preset;
        other.setChecked(custom);
        input.setVisibility(custom ? VISIBLE : GONE);
        if (custom && !input.hasFocus() && minutes > 0) {
            String text = String.valueOf(minutes);
            if (!text.contentEquals(input.getText())) input.setText(text);
        }
        refreshing = true;
        pomodoro.setChecked(draft.isPomodoro());
        block.setChecked(draft.isBlockOnFocus());
        refreshing = false;
    }
}
