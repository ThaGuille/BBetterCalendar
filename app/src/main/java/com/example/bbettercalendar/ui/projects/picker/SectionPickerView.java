package com.example.bbettercalendar.ui.projects.picker;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.domain.SectionRef;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Collections;
import java.util.List;

/** Section picker: one chip per section of the project (from the host); the choice sets draft.sectionId. */
public class SectionPickerView extends PickerView {
    private static final int[] ITEM_IDS = {
            R.id.pickerSectionItem0, R.id.pickerSectionItem1, R.id.pickerSectionItem2, R.id.pickerSectionItem3,
            R.id.pickerSectionItem4, R.id.pickerSectionItem5, R.id.pickerSectionItem6, R.id.pickerSectionItem7};

    public SectionPickerView(@NonNull Context context) {
        this(context, null);
    }

    public SectionPickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int headerIconRes() {
        return R.drawable.ic_section_24;
    }

    @Override
    protected int titleRes() {
        return R.string.projects_picker_title_section;
    }

    @Override
    public void refresh() {
        if (draft == null) return;
        content().removeAllViews();
        ChipGroup group = addGroup(content());
        List<SectionRef> sections = host != null ? host.getSections() : Collections.<SectionRef>emptyList();
        for (int i = 0; i < sections.size(); i++) {
            final SectionRef section = sections.get(i);
            int id = i < ITEM_IDS.length ? ITEM_IDS[i] : View.generateViewId();
            Chip chip = addChip(group, id, section.name, v -> {
                if (draft != null) draft.sectionId = section.id;
                changed();
            });
            chip.setChecked(draft.sectionId == section.id);
        }
    }
}
