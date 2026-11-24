package com.example.bbettercalendar.ui.projects.picker;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/** Priority picker: None, Low, Medium, High, each with a dot in its D-P03 colour and the name in words. */
public class PriorityPickerView extends PickerView {
    private static final int[] NAMES = {
            R.string.projects_picker_pri_none, R.string.projects_picker_pri_low,
            R.string.projects_picker_pri_medium, R.string.projects_picker_pri_high};
    private static final int[] IDS = {
            R.id.pickerPriNone, R.id.pickerPriLow, R.id.pickerPriMedium, R.id.pickerPriHigh};
    /** D-P03: high = danger, medium = secondary, low and none = the neutral border colour. */
    private static final int[] COLORS = {
            R.color.bb_checkbox_border, R.color.bb_checkbox_border, R.color.bb_secondary, R.color.bb_danger};

    private final Chip[] chips = new Chip[4];

    public PriorityPickerView(@NonNull Context context) {
        this(context, null);
    }

    public PriorityPickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ChipGroup group = addGroup(content());
        for (int i = 0; i < chips.length; i++) {
            final int priority = i;
            chips[i] = addChip(group, IDS[i], context.getString(NAMES[i]), v -> {
                if (draft != null) draft.priority = priority;
                changed();
            });
            setIcon(chips[i], dot(COLORS[i]));
        }
    }

    @Override
    protected int headerIconRes() {
        return R.drawable.ic_flag_24;
    }

    @Override
    protected int titleRes() {
        return R.string.projects_picker_title_priority;
    }

    @Override
    public void refresh() {
        if (draft == null) return;
        for (int i = 0; i < chips.length; i++) chips[i].setChecked(draft.priority == i);
    }
}
