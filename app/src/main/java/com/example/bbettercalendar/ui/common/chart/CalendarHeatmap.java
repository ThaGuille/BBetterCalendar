package com.example.bbettercalendar.ui.common.chart;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays daily values out as the "Constancia" calendar (mejora-visual u4, mockup 14): {@code weeks} columns
 * of 7 rows (Monday on top), the last column being the week of {@code end}. Days after {@code end} are NaN
 * (not drawn); days before the data start count as 0. A month label goes on the column holding the 1st of
 * that month. Pure Java (java.time is desugared on API &lt; 26).
 */
public final class CalendarHeatmap {

    public static final int DAYS = 7;

    /** [row = day of week, Monday 0][column = week]. */
    public final float[][] values;
    /** Same shape; null where the value is NaN. */
    public final LocalDate[][] dates;
    /** Columns that get a month label, ascending. */
    public final int[] labelColumns;
    /** Month (1..12) of each label column. */
    public final int[] labelMonths;

    private CalendarHeatmap(float[][] values, LocalDate[][] dates, int[] labelColumns, int[] labelMonths) {
        this.values = values;
        this.dates = dates;
        this.labelColumns = labelColumns;
        this.labelMonths = labelMonths;
    }

    public int weeks() {
        return values.length == 0 ? 0 : values[0].length;
    }

    /**
     * @param end   last day shown (today)
     * @param daily one value per day, the last one being {@code end}
     * @param weeks columns (26 for half a year)
     */
    public static CalendarHeatmap build(LocalDate end, float[] daily, int weeks) {
        int cols = Math.max(1, weeks);
        float[][] values = new float[DAYS][cols];
        LocalDate[][] dates = new LocalDate[DAYS][cols];
        LocalDate lastMonday = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstMonday = lastMonday.minusWeeks(cols - 1);
        int n = daily == null ? 0 : daily.length;
        List<Integer> labelCols = new ArrayList<>();
        List<Integer> labelMonths = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < DAYS; r++) {
                LocalDate d = firstMonday.plusWeeks(c).plusDays(r);
                if (d.isAfter(end)) {
                    values[r][c] = Float.NaN;
                    continue;
                }
                dates[r][c] = d;
                int back = (int) ChronoUnit.DAYS.between(d, end);
                int i = n - 1 - back;
                values[r][c] = i >= 0 && i < n && daily[i] > 0f ? daily[i] : 0f;
                if (d.getDayOfMonth() == 1) {
                    labelCols.add(c);
                    labelMonths.add(d.getMonthValue());
                }
            }
        }
        // First column: label its month too unless the next label is too close to fit (< 3 columns).
        if (labelCols.isEmpty() || labelCols.get(0) >= 3) {
            labelCols.add(0, 0);
            labelMonths.add(0, firstMonday.plusDays(DAYS - 1).getMonthValue());
        }
        int[] lc = new int[labelCols.size()];
        int[] lm = new int[labelMonths.size()];
        for (int i = 0; i < lc.length; i++) {
            lc[i] = labelCols.get(i);
            lm[i] = labelMonths.get(i);
        }
        return new CalendarHeatmap(values, dates, lc, lm);
    }
}
