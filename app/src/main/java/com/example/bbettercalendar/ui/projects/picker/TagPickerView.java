package com.example.bbettercalendar.ui.projects.picker;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.domain.TagRef;
import com.example.bbettercalendar.ui.common.widget.ProjectAccents;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/**
 * Tag picker: None, each tag with its icon in its colour and its name, and New (host.onNewTag). A long
 * press on a tag calls host.onEditTag. The tag list comes from the host and is rebuilt on every refresh.
 */
public class TagPickerView extends PickerView {
    private static final int[] ITEM_IDS = {
            R.id.pickerTagItem0, R.id.pickerTagItem1, R.id.pickerTagItem2, R.id.pickerTagItem3,
            R.id.pickerTagItem4, R.id.pickerTagItem5, R.id.pickerTagItem6, R.id.pickerTagItem7};

    private final LinearLayout body;

    public TagPickerView(@NonNull Context context) {
        this(context, null);
    }

    public TagPickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        content().addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected int headerIconRes() {
        return R.drawable.ic_tag_24;
    }

    @Override
    protected int titleRes() {
        return R.string.projects_picker_title_tag;
    }

    @Override
    public void refresh() {
        if (draft == null) return;
        body.removeAllViews();
        ChipGroup group = addGroup(body);
        Chip none = addChip(group, R.id.pickerTagNone, getContext().getString(R.string.projects_picker_tag_none), v -> {
            if (draft != null) draft.tagId = 0;
            changed();
        });
        none.setChecked(draft.tagId == 0);

        List<TagRef> tags = host != null ? host.getTags() : java.util.Collections.<TagRef>emptyList();
        for (int i = 0; i < tags.size(); i++) {
            final TagRef tag = tags.get(i);
            int id = i < ITEM_IDS.length ? ITEM_IDS[i] : View.generateViewId();
            Chip chip = addChip(group, id, tag.name, v -> {
                if (draft != null) draft.tagId = tag.id;
                changed();
            });
            setIcon(chip, tintedIcon(ProjectAccents.tagIconRes(tag.iconKey), ProjectAccents.tagColorRes(tag.colorKey)));
            chip.setChecked(draft.tagId == tag.id);
            chip.setOnLongClickListener(v -> {
                if (host != null) host.onEditTag(tag.id);
                return true;
            });
        }

        Chip create = addChip(group, R.id.pickerTagNew, getContext().getString(R.string.projects_picker_tag_new), v -> {
            if (host != null) host.onNewTag();
            refresh();
        });
        create.setCheckable(false);
        setIcon(create, tintedIcon(R.drawable.ic_add_24, R.color.bb_primary));

        TextView hint = new TextView(getContext());
        hint.setText(R.string.projects_picker_tag_hint);
        hint.setTextAppearance(R.style.TextAppearance_BBetter_Caption);
        body.addView(hint, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }
}
