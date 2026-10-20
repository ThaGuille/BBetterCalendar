package com.example.bbettercalendar.ui.social;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;

/**
 * F1-u5: los 8 colores de avatar del perfil. El índice 0-7 es el {@code avatarColor} que guardan
 * {@code profiles/{uid}} y {@code handles/{h}}; los colores son los tokens {@code bb_avatar_0..7}.
 */
public final class AvatarPalette {

    public static final int SIZE = 8;

    @ColorRes
    private static final int[] COLORS = {
            R.color.bb_avatar_0, R.color.bb_avatar_1, R.color.bb_avatar_2, R.color.bb_avatar_3,
            R.color.bb_avatar_4, R.color.bb_avatar_5, R.color.bb_avatar_6, R.color.bb_avatar_7
    };

    private AvatarPalette() { }

    /** Índice recortado a 0-7: un valor raro nunca rompe la pantalla. */
    public static int clamp(int index) {
        return Math.max(0, Math.min(index, SIZE - 1));
    }

    @ColorInt
    public static int color(@NonNull Context context, int index) {
        return ContextCompat.getColor(context, COLORS[clamp(index)]);
    }

    /** Color de las iniciales sobre cualquiera de los 8 (todos son claros). */
    @ColorInt
    public static int onColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.bb_on_reward);
    }
}
