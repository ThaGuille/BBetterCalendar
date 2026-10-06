package com.example.bbettercalendar.helpers;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.example.bbettercalendar.R;

/**
 * The app font as a {@link Typeface}, for views that draw text themselves (WeekView, charts)
 * and therefore cannot take a {@code TextAppearance}. It is resolved from the theme's
 * {@code fontFamily} (Theme.BBetter -> {@code @font/bb_font}), so Java never names a font
 * resource: changing the font stays a one-file change in {@code res/font/bb_font.xml}.
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

    private static int resolveFontRes(@NonNull Context context, int attr) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            return 0;
        }
        return value.resourceId;
    }
}
