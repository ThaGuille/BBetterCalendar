package com.example.bbettercalendar.ui.common.chart;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Scales of the chart kit (mejora-visual u4). */
public class ChartScaleTest {

    private static final float EPS = 1e-4f;

    @Test
    public void maxSkipsNullNaNAndNegatives() {
        assertEquals(150f, ChartScale.max(new float[]{10f, Float.NaN, -5f}, null, new float[]{150f, 90f}), EPS);
        assertEquals(0f, ChartScale.max(new float[]{-1f}, null), EPS);
        assertEquals(0f, ChartScale.max(), EPS);
    }

    @Test
    public void extentIsProportionalAndClamped() {
        assertEquals(50f, ChartScale.extent(30f, 60f, 100f), EPS);
        assertEquals(100f, ChartScale.extent(90f, 60f, 100f), EPS);
        assertEquals(0f, ChartScale.extent(-3f, 60f, 100f), EPS);
        assertEquals(0f, ChartScale.extent(30f, 0f, 100f), EPS);
    }

    @Test
    public void minuteStepGivesHourLinesForAWeekOfFocus() {
        // Mockup 11: tallest bar 2 h 30 m -> grid at 1 h and 2 h.
        float step = ChartScale.niceMinuteStep(150f, 3);
        assertEquals(60f, step, EPS);
        assertArrayEquals(new float[]{60f, 120f}, ChartScale.ticks(150f, step), EPS);
    }

    @Test
    public void minuteStepGrowsForLongTotalsAndShrinksForShortOnes() {
        assertEquals(240f, ChartScale.niceMinuteStep(600f, 3), EPS);
        assertEquals(10f, ChartScale.niceMinuteStep(25f, 3), EPS);
        assertEquals(5f, ChartScale.niceMinuteStep(0f, 3), EPS);
        // Past one day per tick the step keeps whole days.
        assertEquals(2880f, ChartScale.niceMinuteStep(7000f, 3), EPS);
    }

    @Test
    public void niceStepFollowsOneTwoFive() {
        assertEquals(5f, ChartScale.niceStep(14f, 3), EPS);
        assertEquals(20f, ChartScale.niceStep(55f, 3), EPS);
        assertEquals(1f, ChartScale.niceStep(2f, 4), EPS);
    }

    @Test
    public void ticksIncludeTheMaxWhenItIsAMultiple() {
        assertArrayEquals(new float[]{60f, 120f, 180f}, ChartScale.ticks(180f, 60f), EPS);
        assertEquals(0, ChartScale.ticks(0f, 60f).length);
    }

    @Test
    public void meanAndSumIgnoreNaN() {
        assertEquals(20f, ChartScale.mean(new float[]{10f, Float.NaN, 30f}), EPS);
        assertEquals(40f, ChartScale.sum(new float[]{10f, Float.NaN, 30f, -2f}), EPS);
        assertEquals(0f, ChartScale.mean(new float[0]), EPS);
    }
}
