package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

/** Small helpers shared by the u9 widgets (package-private). */
final class WidgetUtils {

    private WidgetUtils() {
    }

    /** Sets the text and hides the view when it is empty. */
    static void setTextOrGone(@NonNull TextView view, @Nullable CharSequence text) {
        view.setText(text);
        view.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    /** A mutated, tinted copy of a drawable resource, sized to {@code sizePx} (for compound use). */
    @Nullable
    static Drawable tinted(@NonNull Context context, @DrawableRes int res, @ColorInt int color,
                           int sizePx) {
        if (res == 0) {
            return null;
        }
        Drawable d = AppCompatResources.getDrawable(context, res);
        if (d == null) {
            return null;
        }
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, color);
        d.setBounds(0, 0, sizePx, sizePx);
        return d;
    }

    static int dp(@NonNull Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
