package com.example.bbettercalendar.helpers;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.example.bbettercalendar.R;

/**
 * The app font as a {@link Typeface}, for views that draw text themselves (WeekView, charts)
 * and therefore cannot take a {@code TextAppearance}. It is resolved from the theme's
 * {@code fontFamily} (Theme.BBetter -> {@code @font/bb_font}), so Java never names a font
 * resource: changing the font stays a change of the {@code res/font/bb_font*.xml} files.
 *
 * <p>Weights (u9b): XML text gets its weight from the TextAppearance's family
 * ({@code bb_font_medium} / {@code bb_font_semibold}); Java asks for one with
 * {@link #appTypeface(Context, int)} (e.g. 500 for chart axes = the Caption weight).
 */
public final class FontHelper {

    private FontHelper() {
    }

    /** Typeface of the theme's {@code fontFamily}, or {@code null} if the theme has none. */
    @Nullable
    public static Typeface appTypeface(@NonNull Context context) {
        int fontRes = resolveFontRes(context, R.attr.fontFamily);
        if (fontRes == 0) {
            fontRes = resolveFontRes(context, android.R.attr.fontFamily);
        }
        if (fontRes == 0) {
            return null;
        }
        try {
            return ResourcesCompat.getFont(context, fontRes);
        } catch (RuntimeException e) {
            // Resources.NotFoundException si el atributo apunta a una familia de sistema.
            return null;
        }
    }

    /** {@link #appTypeface} or {@link Typeface#DEFAULT}. */
    @NonNull
    public static Typeface appTypefaceOrDefault(@NonNull Context context) {
        Typeface typeface = appTypeface(context);
        return typeface != null ? typeface : Typeface.DEFAULT;
    }

    /**
     * The app font at a CSS-style {@code weight} (400 regular, 500 medium, 600 semibold,
     * 700 bold, 800 extrabold). API 28+: the exact face of {@code bb_font} (it bundles all five).
     * API 21-27: the nearest of regular / bold ({@code weight >= 600} = bold). Never {@code null}.
     */
    @NonNull
    public static Typeface appTypeface(@NonNull Context context, int weight) {
        Typeface base = appTypefaceOrDefault(context);
        int clamped = Math.max(1, Math.min(1000, weight));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(base, clamped, false);
        }
        return Typeface.create(base, clamped >= 600 ? Typeface.BOLD : Typeface.NORMAL);
    }

    private static int resolveFontRes(@NonNull Context context, int attr) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            return 0;
        }
        return value.resourceId;
    }
}
