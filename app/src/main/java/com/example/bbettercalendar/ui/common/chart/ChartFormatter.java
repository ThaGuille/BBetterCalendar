package com.example.bbettercalendar.ui.common.chart;

import androidx.annotation.NonNull;

/**
 * Turns a chart value into the text of labels, bubbles and the spoken summary (mejora-visual u4). Time
 * charts pass {@link #MINUTES}; counts pass a lambda with a plural resource.
 */
public interface ChartFormatter {

    /** Minutes as "2 h 30 m". */
    ChartFormatter MINUTES = ChartFormat::hoursMinutes;

    /** Whole numbers ("4"). */
    ChartFormatter COUNT = v -> String.valueOf(Math.round(v));

    @NonNull
    String format(float value);
}
