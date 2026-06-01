package com.example.bbettercalendar.popups;

import android.content.Context;

import com.example.bbettercalendar.R;

/**
 * Single source of truth for the repetition options shown across the create-entry flow:
 * the {@link RepetitionPopup} rows and the row label inside the create event / task screen.
 *
 * To rename or reorder a label, edit the string resource it points to (or this array).
 * Index order is part of the contract — it doubles as the persisted value passed via
 * {@link com.example.bbettercalendar.popups.OnPopupListener}, so don't shuffle entries
 * without migrating stored data.
 */
public final class RepetitionOptions {

    public static final int NONE = 0;
    public static final int DAILY = 1;
    public static final int WEEKLY = 2;
    public static final int MONTHLY = 3;

    private static final int[] LABEL_RES_IDS = {
            R.string.repetition_option_none,
            R.string.repetition_option_daily,
            R.string.repetition_option_weekly,
            R.string.repetition_option_monthly
    };

    public static int count() {
        return LABEL_RES_IDS.length;
    }

    public static int labelResIdFor(int repetition) {
        if (repetition < 0 || repetition >= LABEL_RES_IDS.length) {
            return LABEL_RES_IDS[NONE];
        }
        return LABEL_RES_IDS[repetition];
    }

    public static String labelFor(Context context, int repetition) {
        return context.getString(labelResIdFor(repetition));
    }

    private RepetitionOptions() {}
}
