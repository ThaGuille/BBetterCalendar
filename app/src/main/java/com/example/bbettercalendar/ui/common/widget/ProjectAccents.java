package com.example.bbettercalendar.ui.common.widget;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * Resource lookups of the Projects redesign (rediseno-proyectos u3): accent colour of a project, project
 * icon, tag icon and tag colour. Plain Java returning {@code R} ids (no Context), so the JVM test can
 * check every key; the views resolve the ids themselves.
 */
public final class ProjectAccents {

    private static final int[] ACCENT_COLORS = {
            R.color.bb_project_1,
            R.color.bb_project_2,
            R.color.bb_project_3,
            R.color.bb_project_4
    };

    /** Project icon keys, in the order the pickers show them. Unknown key = {@link #DEFAULT_PROJECT_ICON}. */
    public static final String[] PROJECT_ICON_KEYS = {
            "cap", "book", "code", "plane", "box", "home", "heart", "image"
    };
    public static final String DEFAULT_PROJECT_ICON = "box";

    /** Tag icon keys, in the order the tag editor shows them. Unknown key = {@link #DEFAULT_TAG_ICON}. */
    public static final String[] TAG_ICON_KEYS = {
            "pen", "book", "bag", "phone", "code", "heart", "home", "bulb"
    };
    public static final String DEFAULT_TAG_ICON = "pen";

    /** Tag colour keys (spec 3.7). Unknown key = {@link #DEFAULT_TAG_COLOR}. */
    public static final String[] TAG_COLOR_KEYS = {"primary", "secondary", "seafoam", "muted"};
    public static final String DEFAULT_TAG_COLOR = "muted";

    private ProjectAccents() {}

    /**
     * Accent colour of a {@code colorIndex}: cycles bb_project_1..4 and tolerates negative indexes
     * ({@code %} in Java does not). Same contract as ProjectListAdapter.accentColorResFor.
     */
    @ColorRes
    public static int colorResFor(int colorIndex) {
        int index = colorIndex % ACCENT_COLORS.length;
        if (index < 0) {
            index += ACCENT_COLORS.length;
        }
        return ACCENT_COLORS[index];
    }

    /** Icon of a project ({@code Project.iconKey}); unknown or null key = box. */
    @DrawableRes
    public static int projectIconRes(@Nullable String iconKey) {
        if (iconKey != null) {
            switch (iconKey) {
                case "cap": return R.drawable.ic_project_cap;
                case "book": return R.drawable.ic_project_book;
                case "code": return R.drawable.ic_project_code;
                case "plane": return R.drawable.ic_project_plane;
                case "home": return R.drawable.ic_project_home;
                case "heart": return R.drawable.ic_project_heart;
                case "image": return R.drawable.ic_project_image;
                default: break;
            }
        }
        return R.drawable.ic_project_box;
    }

    /** Icon of a task tag; book, code, heart and home share the project glyphs. Unknown key = pen. */
    @DrawableRes
    public static int tagIconRes(@Nullable String iconKey) {
        if (iconKey != null) {
            switch (iconKey) {
                case "book": return R.drawable.ic_project_book;
                case "bag": return R.drawable.ic_tag_bag;
                case "phone": return R.drawable.ic_tag_phone;
                case "code": return R.drawable.ic_project_code;
                case "heart": return R.drawable.ic_project_heart;
                case "home": return R.drawable.ic_project_home;
                case "bulb": return R.drawable.ic_tag_bulb;
                default: break;
            }
        }
        return R.drawable.ic_tag_pen;
    }

    /** Colour of a task tag: primary = sage, secondary = dusk, seafoam = bb_project_4, muted = on-surface muted. */
    @ColorRes
    public static int tagColorRes(@Nullable String colorKey) {
        if (colorKey != null) {
            switch (colorKey) {
                case "primary": return R.color.bb_primary;
                case "secondary": return R.color.bb_secondary;
                case "seafoam": return R.color.bb_project_4;
                default: break;
            }
        }
        return R.color.bb_on_surface_muted;
    }
}
