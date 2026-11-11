package com.example.bbettercalendar.ui.common.chart;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Bar slots, heat-map cells and buckets, entrance stagger (mejora-visual u4). */
public class BarAndGridGeometryTest {

    private static final float EPS = 1e-4f;

    @Test
    public void slotsSplitThePlotEvenly() {
        BarSlots slots = new BarSlots(10f, 700f, 7);
        assertEquals(100f, slots.slotWidth(), EPS);
        assertEquals(60f, slots.center(0), EPS);
        assertEquals(660f, slots.center(6), EPS);
    }

    @Test
    public void slotIndexCoversTheWholeColumnAndNothingOutside() {
        BarSlots slots = new BarSlots(10f, 700f, 7);
        assertEquals(0, slots.indexAt(10f));
        assertEquals(3, slots.indexAt(360f));
        assertEquals(6, slots.indexAt(709.9f));
        assertEquals(-1, slots.indexAt(9.9f));
        assertEquals(-1, slots.indexAt(710f));
        assertEquals(-1, new BarSlots(0f, 100f, 0).indexAt(50f));
    }

    @Test
    public void cellsFillTheWidthWithGaps() {
        // 18 hour columns over 400 px with 2 px gaps: (400 - 34) / 18.
        float cell = GridGeometry.cellSize(400f, 18, 2f);
        assertEquals(366f / 18f, cell, EPS);
        assertEquals(400f, GridGeometry.cellStart(18, 0f, cell, 2f) - 2f, EPS);
    }

    @Test
    public void cellIndexAssignsGapsToThePreviousCell() {
        assertEquals(0, GridGeometry.indexAt(5f, 0f, 10f, 2f, 7));
        assertEquals(0, GridGeometry.indexAt(11f, 0f, 10f, 2f, 7));
        assertEquals(1, GridGeometry.indexAt(12f, 0f, 10f, 2f, 7));
        assertEquals(-1, GridGeometry.indexAt(-1f, 0f, 10f, 2f, 7));
        assertEquals(-1, GridGeometry.indexAt(84f, 0f, 10f, 2f, 7));
    }

    @Test
    public void heatBucketsUseQuartersOfTheMaximum() {
        assertEquals(0, GridGeometry.heatBucket(0f, 100f));
        assertEquals(0, GridGeometry.heatBucket(Float.NaN, 100f));
        assertEquals(0, GridGeometry.heatBucket(10f, 0f));
        assertEquals(1, GridGeometry.heatBucket(1f, 100f));
        assertEquals(1, GridGeometry.heatBucket(25f, 100f));
        assertEquals(2, GridGeometry.heatBucket(26f, 100f));
        assertEquals(3, GridGeometry.heatBucket(75f, 100f));
        assertEquals(4, GridGeometry.heatBucket(100f, 100f));
        assertEquals(4, GridGeometry.heatBucket(140f, 100f));
    }

    @Test
    public void staggerKeepsFortyMsForAWeekAndShrinksForManyMarks() {
        assertEquals(40f, ChartMotion.stepMs(7, 40f, 240f), EPS);
        assertEquals(240f / 25f, ChartMotion.stepMs(26, 40f, 240f), EPS);
        assertEquals(0f, ChartMotion.stepMs(1, 40f, 240f), EPS);
        assertEquals(450f + 6 * 40f, ChartMotion.totalMs(7, 450f, 40f, 240f), EPS);
    }

    @Test
    public void markFractionStartsEachMarkAfterItsDelay() {
        assertEquals(0f, ChartMotion.markFraction(0f, 0, 7, 450f, 40f, 240f), EPS);
        assertEquals(0.5f, ChartMotion.markFraction(225f, 0, 7, 450f, 40f, 240f), EPS);
        assertEquals(0f, ChartMotion.markFraction(100f, 3, 7, 450f, 40f, 240f), EPS);
        assertEquals(1f, ChartMotion.markFraction(690f, 6, 7, 450f, 40f, 240f), EPS);
        assertEquals(1f, ChartMotion.markFraction(0f, 2, 7, 0f, 40f, 240f), EPS);
    }
}
