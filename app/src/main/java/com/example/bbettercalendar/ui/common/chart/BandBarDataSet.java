package com.example.bbettercalendar.ui.common.chart;

import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;

import java.util.List;

/**
 * Marker data set for the FOCUS bands of html 14: {@link RoundedBarChartRenderer} merges the
 * contiguous bars of a run into one translucent block with rounded corners (rx 4 in the design)
 * instead of drawing one rect per hour. Created by {@link ChartStyler#focusBandsData}.
 */
public class BandBarDataSet extends BarDataSet {

    public BandBarDataSet(List<BarEntry> yVals, String label) {
        super(yVals, label);
    }
}
