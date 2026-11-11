package com.example.bbettercalendar.ui.common.chart;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Slices and angles of the donut (mejora-visual u4). */
public class DonutGeometryTest {

    private static final float EPS = 1e-3f;

    @Test
    public void foldKeepsFourLargestAndAddsOthers() {
        DonutGeometry.Folded f = DonutGeometry.fold(new float[]{30f, 310f, 0f, 220f, 140f, 90f, 10f}, 4);
        assertArrayEquals(new float[]{310f, 220f, 140f, 90f, 40f}, f.values, EPS);
        assertArrayEquals(new int[]{1, 3, 4, 5, DonutGeometry.OTHERS}, f.source);
    }

    @Test
    public void foldWithFewItemsHasNoOthersAndDropsZeros() {
        DonutGeometry.Folded f = DonutGeometry.fold(new float[]{50f, 0f, 70f}, 4);
        assertArrayEquals(new float[]{70f, 50f}, f.values, EPS);
        assertArrayEquals(new int[]{2, 0}, f.source);
        assertEquals(0, DonutGeometry.fold(null, 4).values.length);
    }

    @Test
    public void arcsStartAtTwelveAndLeaveGaps() {
        float[][] arcs = DonutGeometry.arcs(new float[]{1f, 1f}, 4f);
        // Two halves of (360 - 8) degrees, the first starting half a gap after 12 o'clock.
        assertEquals(-88f, arcs[0][0], EPS);
        assertEquals(176f, arcs[1][0], EPS);
        assertEquals(-88f + 176f + 4f, arcs[0][1], EPS);
        assertEquals(176f, arcs[1][1], EPS);
    }

    @Test
    public void singleSliceIsAFullRing() {
        float[][] arcs = DonutGeometry.arcs(new float[]{5f}, 4f);
        assertEquals(-90f, arcs[0][0], EPS);
        assertEquals(360f, arcs[1][0], EPS);
    }

    @Test
    public void sliceAtFindsTheSliceUnderAnAngle() {
        float[][] arcs = DonutGeometry.arcs(new float[]{3f, 1f}, 4f);
        // Slice 0 covers 12 o'clock clockwise to about 7 o'clock; slice 1 the rest.
        assertEquals(0, DonutGeometry.sliceAt(DonutGeometry.angleOf(1f, 0f), arcs[0], arcs[1], 4f));
        assertEquals(0, DonutGeometry.sliceAt(DonutGeometry.angleOf(0f, 1f), arcs[0], arcs[1], 4f));
        assertEquals(1, DonutGeometry.sliceAt(DonutGeometry.angleOf(-1f, -0.2f), arcs[0], arcs[1], 4f));
        // Exactly at 12 o'clock is in the gap between the last and the first: it goes to a neighbour.
        int atTop = DonutGeometry.sliceAt(-90f, arcs[0], arcs[1], 4f);
        assertEquals(true, atTop == 0 || atTop == 1);
        assertEquals(-1, DonutGeometry.sliceAt(0f, new float[0], new float[0], 4f));
    }

    @Test
    public void angleOfUsesCanvasOrientation() {
        assertEquals(0f, DonutGeometry.angleOf(1f, 0f), EPS);
        assertEquals(90f, DonutGeometry.angleOf(0f, 1f), EPS);
        assertEquals(-90f, DonutGeometry.angleOf(0f, -1f), EPS);
    }
}
