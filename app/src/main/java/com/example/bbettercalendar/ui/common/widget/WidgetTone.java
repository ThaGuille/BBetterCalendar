package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;

/**
 * Colour tone of a text / indicator inside the u9 widgets. Each tone is a {@code bb_*} token, so
 * the widgets never name a hex or a primitive. The ordinal matches the {@code *Tone} enums of
 * {@code attrs_widgets.xml} (normal 0, muted 1, primary 2, reward 3, danger 4).
 *
 * <p>Contrast (u4 table): every tone passes AA on {@code bb_surface} and {@code bb_surface_card};
 * on {@code bb_surface_raised} {@link #DANGER} does NOT (4.34:1), so danger text is never placed on
 * a raised fill by these widgets.
 */
public enum WidgetTone {
    NORMAL(R.color.bb_on_surface),
    MUTED(R.color.bb_on_surface_muted),
    PRIMARY(R.color.bb_primary_bright),
    REWARD(R.color.bb_accent_reward),
    DANGER(R.color.bb_danger);

    @ColorRes
    public final int colorRes;

    WidgetTone(@ColorRes int colorRes) {
        this.colorRes = colorRes;
    }

    @ColorInt
    public int color(@NonNull Context context) {
        return ContextCompat.getColor(context, colorRes);
    }

    @NonNull
    static WidgetTone fromAttr(int value, @NonNull WidgetTone fallback) {
        WidgetTone[] all = values();
        return value >= 0 && value < all.length ? all[value] : fallback;
    }
}
