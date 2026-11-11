package com.example.bbettercalendar.ui.common.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

/** The 26-week consistency calendar (mejora-visual u4, mockup 14). */
public class CalendarHeatmapTest {

    /** Wednesday 11 November 2026. */
    private static final LocalDate END = LocalDate.of(2026, 11, 11);

    @Test
    public void lastColumnIsTheWeekOfTheEndAndFutureDaysAreNaN() {
        float[] daily = new float[180];
        daily[179] = 30f; // today
        daily[178] = 45f; // yesterday
        CalendarHeatmap cal = CalendarHeatmap.build(END, daily, 26);
        assertEquals(26, cal.weeks());
        // Monday row 0 ... Wednesday row 2 = today.
        assertEquals(30f, cal.values[2][25], 0f);
        assertEquals(45f, cal.values[1][25], 0f);
        assertEquals(END, cal.dates[2][25]);
        assertTrue(Float.isNaN(cal.values[3][25]));
        assertNull(cal.dates[6][25]);
    }

    @Test
    public void daysBeforeTheDataCountAsZero() {
        CalendarHeatmap cal = CalendarHeatmap.build(END, new float[]{20f}, 26);
        assertEquals(0f, cal.values[0][0], 0f);
        assertEquals(LocalDate.of(2026, 5, 18), cal.dates[0][0]);
        assertEquals(20f, cal.values[2][25], 0f);
    }

    @Test
    public void monthLabelsGoOnTheColumnOfTheFirstDay() {
        CalendarHeatmap cal = CalendarHeatmap.build(END, new float[0], 26);
        // Columns start on Monday 18 May 2026: 1 June is in week 2, 1 July in week 6 ... 1 November in week 23.
        // Column 0 (May) has a label too, since the next one is 2 columns away -> no, too close: skipped.
        assertEquals(2, cal.labelColumns[0]);
        assertEquals(6, cal.labelMonths[0]);
        int last = cal.labelColumns.length - 1;
        assertEquals(23, cal.labelColumns[last]);
        assertEquals(11, cal.labelMonths[last]);
        assertEquals(6, cal.labelColumns.length);
    }

    @Test
    public void firstColumnGetsALabelWhenTheNextMonthIsFar() {
        // End 30 Dec 2026: columns start on Monday 6 July 2026; 1 August falls in week 3.
        CalendarHeatmap cal = CalendarHeatmap.build(LocalDate.of(2026, 12, 30), new float[0], 26);
        assertEquals(0, cal.labelColumns[0]);
        assertEquals(7, cal.labelMonths[0]);
        assertEquals(3, cal.labelColumns[1]);
        assertEquals(8, cal.labelMonths[1]);
    }
}
