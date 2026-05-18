package com.example.bbettercalendar.feedback;

import android.view.HapticFeedbackConstants;
import android.view.View;

public final class HapticFeedback {

    private HapticFeedback() {}

    public static void lightTap(View view) {
        if (view == null) return;
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
    }

    public static void confirm(View view) {
        if (view == null) return;
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    public static void error(View view) {
        if (view == null) return;
        view.performHapticFeedback(HapticFeedbackConstants.REJECT);
    }
}
