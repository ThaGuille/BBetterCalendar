package com.example.bbettercalendar.ui.projects.form;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.widget.ProjectAccents;

/**
 * The icon grid and the colour row shared by the project form (inline picker) and the tag editor
 * (rediseno-proyectos u10). Each option is a 48dp touch target with its contentDescription and tooltip;
 * the chosen one gets the selected ring. Views are rebuilt from scratch on every call.
 */
public final class FormPickers {

    public interface KeyListener {
        void onKey(@NonNull String key);
    }

    public interface IndexListener {
        void onIndex(int index);
    }

    private static final int GRID_COLUMNS = 4;

    private FormPickers() {}

    /** Name of an icon key for the contentDescription (project and tag icons). */
    @NonNull
    public static String iconName(@NonNull Context context, @Nullable String key) {
        int res;
        switch (key == null ? "" : key) {
            case "cap": res = R.string.projects_form_icon_cap; break;
            case "book": res = R.string.projects_form_icon_book; break;
            case "code": res = R.string.projects_form_icon_code; break;
            case "plane": res = R.string.projects_form_icon_plane; break;
            case "home": res = R.string.projects_form_icon_home; break;
            case "heart": res = R.string.projects_form_icon_heart; break;
            case "image": res = R.string.projects_form_icon_image; break;
            case "pen": res = R.string.projects_form_icon_pen; break;
            case "bag": res = R.string.projects_form_icon_bag; break;
            case "phone": res = R.string.projects_form_icon_phone; break;
            case "bulb": res = R.string.projects_form_icon_bulb; break;
            default: res = R.string.projects_form_icon_box; break;
        }
        return context.getString(res);
    }

    /** Name of a project colour index (bb_project_1..4) for the contentDescription. */
    @NonNull
    public static String projectColorName(@NonNull Context context, int index) {
        int[] names = {R.string.projects_form_color_sage, R.string.projects_form_color_lilac,
                R.string.projects_form_color_dusk, R.string.projects_form_color_seafoam};
        return context.getString(names[Math.floorMod(index, names.length)]);
    }

    /** Name of a tag colour key (primary, secondary, seafoam, muted) for the contentDescription. */
    @NonNull
    public static String tagColorName(@NonNull Context context, @Nullable String key) {
        int res;
        switch (key == null ? "" : key) {
            case "primary": res = R.string.projects_form_color_sage; break;
            case "secondary": res = R.string.projects_form_color_dusk; break;
            case "seafoam": res = R.string.projects_form_color_seafoam; break;
            default: res = R.string.projects_form_color_muted; break;
        }
        return context.getString(res);
    }

    /**
     * Fills {@code grid} with one button per key. {@code tagIcons} picks the tag glyph set instead of the
     * project one; {@code accent} tints the glyphs.
     */
    public static void fillIcons(@NonNull GridLayout grid, @NonNull String[] keys, boolean tagIcons,
                                 @Nullable String selectedKey, @ColorRes int accent,
                                 @NonNull KeyListener listener) {
        Context context = grid.getContext();
        grid.removeAllViews();
        grid.setColumnCount(GRID_COLUMNS);
        int size = context.getResources().getDimensionPixelSize(R.dimen.projects_form_pick);
        int glyph = context.getResources().getDimensionPixelSize(R.dimen.projects_form_pick_icon);
        int pad = (size - glyph) / 2;
        int gap = context.getResources().getDimensionPixelSize(R.dimen.spacing_xs);
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            ImageButton button = new ImageButton(context);
            button.setBackgroundResource(R.drawable.bg_project_form_pick);
            button.setImageResource(tagIcons ? ProjectAccents.tagIconRes(key) : ProjectAccents.projectIconRes(key));
            button.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, accent)));
            button.setScaleType(ImageButton.ScaleType.FIT_CENTER);
            button.setPadding(pad, pad, pad, pad);
            button.setSelected(key.equals(selectedKey));
            String description = context.getString(R.string.projects_form_icon_option, iconName(context, key));
            button.setContentDescription(description);
            TooltipCompat.setTooltipText(button, description);
            button.setOnClickListener(v -> listener.onKey(key));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = size;
            lp.height = size;
            lp.setMargins(0, gap, 0, gap);
            lp.setGravity(Gravity.CENTER);
            grid.addView(button, lp);
        }
    }

    /** Fills {@code row} with one round dot per colour; {@code names} are the contentDescription colour names. */
    public static void fillColors(@NonNull LinearLayout row, @NonNull @ColorRes int[] colors,
                                  @NonNull String[] names, int selectedIndex, @NonNull IndexListener listener) {
        Context context = row.getContext();
        row.removeAllViews();
        int size = context.getResources().getDimensionPixelSize(R.dimen.projects_form_pick);
        int dot = context.getResources().getDimensionPixelSize(R.dimen.projects_form_dot);
        int pad = (size - dot) / 2;
        int gap = context.getResources().getDimensionPixelSize(R.dimen.spacing_md);
        for (int i = 0; i < colors.length; i++) {
            final int index = i;
            ImageButton button = new ImageButton(context);
            button.setBackgroundResource(R.drawable.bg_project_form_ring);
            GradientDrawable fill = new GradientDrawable();
            fill.setShape(GradientDrawable.OVAL);
            fill.setColor(ContextCompat.getColor(context, colors[i]));
            fill.setSize(dot, dot);
            button.setImageDrawable(fill);
            button.setScaleType(ImageButton.ScaleType.FIT_CENTER);
            button.setPadding(pad, pad, pad, pad);
            button.setSelected(i == selectedIndex);
            String description = context.getString(R.string.projects_form_color_option, names[i]);
            button.setContentDescription(description);
            TooltipCompat.setTooltipText(button, description);
            button.setOnClickListener(v -> listener.onIndex(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(gap);
            row.addView(button, lp);
        }
    }
}
