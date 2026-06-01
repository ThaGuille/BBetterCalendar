package com.example.bbettercalendar.popups;

import android.content.Context;

import com.example.bbettercalendar.R;

/**
 * Single source of truth for the notification offsets shown across the create-entry flow:
 * the {@link NotificationsPopup} rows and the chip-style rows injected under the
 * notifications row inside the create event / task screen.
 *
 * Index order matches {@code notificationsArray[]} in {@code AddEventActivity}, which is
 * persisted on the entry; don't reorder entries without migrating stored data.
 */
public final class NotificationOffsets {

    private static final int[] LABEL_RES_IDS = {
            R.string.notifications_offset_5min,
            R.string.notifications_offset_10min,
            R.string.notifications_offset_15min,
            R.string.notifications_offset_30min,
            R.string.notifications_offset_1h,
            R.string.notifications_offset_3h,
            R.string.notifications_offset_1d
    };

    /** Offset in milliseconds before the event start, paired by index with LABEL_RES_IDS. */
    public static final long[] OFFSET_MILLIS = {
            5L * 60_000L,
            10L * 60_000L,
            15L * 60_000L,
            30L * 60_000L,
            60L * 60_000L,
            3L * 60L * 60_000L,
            24L * 60L * 60_000L
    };

    public static int count() {
        return LABEL_RES_IDS.length;
    }

    public static int labelResIdFor(int index) {
        if (index < 0 || index >= LABEL_RES_IDS.length) {
            return LABEL_RES_IDS[0];
        }
        return LABEL_RES_IDS[index];
    }

    public static String labelFor(Context context, int index) {
        return context.getString(labelResIdFor(index));
    }

    private NotificationOffsets() {}
}
