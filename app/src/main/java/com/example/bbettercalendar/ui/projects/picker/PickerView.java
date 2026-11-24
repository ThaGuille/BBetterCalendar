package com.example.bbettercalendar.ui.projects.picker;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * Base of the task pickers (rediseno-proyectos u6). A picker is bound to a {@link TaskDraft} and a
 * {@link PickerHost} with {@link #bind}; it re-renders itself from the draft after every change, and
 * every change goes through the TaskDraft setters (the rules between fields live in projects.domain).
 * Optional header (icon, name, Done) for the quick-add box; the content scrolls vertically inside the
 * height the host gives, and option rows wrap (never horizontal scroll).
 */
public abstract class PickerView extends LinearLayout {

    @Nullable protected TaskDraft draft;
    @Nullable protected PickerHost host;
    private final LinearLayout content;
    private final View header;

    protected PickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_picker, this, true);
        content = findViewById(R.id.pickerContent);
        header = findViewById(R.id.pickerHeader);
        ((ImageView) findViewById(R.id.pickerHeaderIcon)).setImageResource(headerIconRes());
        ((TextView) findViewById(R.id.pickerHeaderTitle)).setText(titleRes());
        findViewById(R.id.pickerHeaderDone).setOnClickListener(v -> {
            if (host != null) host.onDone();
        });
    }

    @DrawableRes
    protected abstract int headerIconRes();

    @StringRes
    protected abstract int titleRes();

    /** Re-renders every control from the draft; must not call the host. */
    public abstract void refresh();

    /** Binds the draft and the host and renders. */
    public void bind(@NonNull TaskDraft draft, @NonNull PickerHost host) {
        this.draft = draft;
        this.host = host;
        refresh();
    }

    /** Shows the header (icon, name and Done) used by the quick-add box. */
    public void setHeaderVisible(boolean visible) {
        header.setVisibility(visible ? VISIBLE : GONE);
    }

    protected final LinearLayout content() {
        return content;
    }

    /** A picker changed the draft: tell the host and render again. */
    protected final void changed() {
        if (host != null) host.onDraftChanged();
        refresh();
    }

    protected final TextView addLabel(LinearLayout parent, @StringRes int text) {
        TextView label = (TextView) LayoutInflater.from(getContext())
                .inflate(R.layout.picker_label, parent, false);
        label.setText(text);
        parent.addView(label);
        return label;
    }

    protected final ChipGroup addGroup(LinearLayout parent) {
        ChipGroup group = (ChipGroup) LayoutInflater.from(getContext())
                .inflate(R.layout.picker_option_group, parent, false);
        parent.addView(group);
        return group;
    }

    /** A checkable option chip; the click listener sets the draft, {@link #refresh()} sets the checked state. */
    protected final Chip addChip(ChipGroup group, @IdRes int id, CharSequence label, @Nullable OnClickListener click) {
        Chip chip = (Chip) LayoutInflater.from(getContext()).inflate(R.layout.picker_chip, group, false);
        if (id != 0) chip.setId(id);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setOnClickListener(click);
        group.addView(chip);
        return chip;
    }

    protected final Drawable tintedIcon(@DrawableRes int drawableRes, @ColorRes int colorRes) {
        Drawable d = ContextCompat.getDrawable(getContext(), drawableRes).mutate();
        d.setTint(ContextCompat.getColor(getContext(), colorRes));
        return d;
    }

    /** Round dot of a priority colour. */
    protected final Drawable dot(@ColorRes int colorRes) {
        int size = getResources().getDimensionPixelSize(R.dimen.projects_picker_dot);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ContextCompat.getColor(getContext(), colorRes));
        d.setSize(size, size);
        return d;
    }

    protected final void setIcon(Chip chip, @Nullable Drawable icon) {
        chip.setChipIcon(icon);
        chip.setChipIconVisible(icon != null);
    }
}
