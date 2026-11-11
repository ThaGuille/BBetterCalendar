package com.example.bbettercalendar.ui.common.chart;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Histogram buckets, labels and small calculations of the chart kit (mejora-visual u4). */
public class HistogramAndFormatTest {

    @Test
    public void binPutsSamplesInTheirBucketAndOverflowInTheLast() {
        int[] edges = {12, 17, 22, 27, 35};
        int[] counts = Histogram.bin(new int[]{10, 12, 13, 25, 25, 26, 30, 50, 90}, edges);
        assertArrayEquals(new int[]{2, 1, 0, 3, 1, 2}, counts);
        assertArrayEquals(new int[]{0, 0}, Histogram.bin(null, new int[]{5}));
    }

    @Test
    public void modalIsTheFirstTallestOrNoneWhenEmpty() {
        assertEquals(3, Histogram.modalIndex(new int[]{2, 5, 9, 14, 6, 2}));
        assertEquals(1, Histogram.modalIndex(new int[]{1, 4, 4}));
        assertEquals(-1, Histogram.modalIndex(new int[]{0, 0}));
        assertEquals(-1, Histogram.modalIndex(null));
    }

    @Test
    public void hoursMinutesReadsLikeTheMockups() {
        assertEquals("1 h 36 m", ChartFormat.hoursMinutes(96f));
        assertEquals("45 m", ChartFormat.hoursMinutes(45f));
        assertEquals("2 h", ChartFormat.hoursMinutes(120f));
        assertEquals("0 m", ChartFormat.hoursMinutes(-4f));
        assertEquals("1 m", ChartFormat.hoursMinutes(0.6f));
    }

    @Test
    public void percentChangeAndSign() {
        Integer change = ChartFormat.percentChange(91f, 52f);
        assertEquals(Integer.valueOf(-43), change);
        assertEquals(ChartFormat.MINUS + "43 %", ChartFormat.signedPercent(change));
        assertEquals("+12 %", ChartFormat.signedPercent(12));
        assertEquals("0 %", ChartFormat.signedPercent(0));
        assertNull(ChartFormat.percentChange(0f, 30f));
        assertEquals(41, ChartFormat.share(310f, 760f));
        assertEquals(0, ChartFormat.share(5f, 0f));
    }

    @Test
    public void limitStateSeparatesNoneWithinAndOver() {
        assertEquals(ChartFormat.LIMIT_NONE, ChartFormat.limitState(44f, 0f));
        assertEquals(ChartFormat.LIMIT_WITHIN, ChartFormat.limitState(52f, 60f));
        assertEquals(ChartFormat.LIMIT_WITHIN, ChartFormat.limitState(60f, 60f));
        assertEquals(ChartFormat.LIMIT_OVER, ChartFormat.limitState(48f, 45f));
    }
}
