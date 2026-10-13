package com.example.bbettercalendar.ui.common.chart;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.renderer.BarChartRenderer;
import com.github.mikephil.charting.renderer.CombinedChartRenderer;
import com.github.mikephil.charting.renderer.DataRenderer;
import com.github.mikephil.charting.utils.ViewPortHandler;

/**
 * {@link CombinedChartRenderer} whose bar layer is a {@link RoundedBarChartRenderer} (the FOCUS
 * bands of html 14). The library rebuilds its sub-renderers on every {@code setData}, so the swap
 * lives in {@link #createRenderers()}.
 */
public class RoundedCombinedChartRenderer extends CombinedChartRenderer {

    public RoundedCombinedChartRenderer(CombinedChart chart, ChartAnimator animator,
                                        ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);
    }

    @Override
    public void createRenderers() {
        super.createRenderers();
        for (int i = 0; i < mRenderers.size(); i++) {
            DataRenderer r = mRenderers.get(i);
            if (r instanceof BarChartRenderer && !(r instanceof RoundedBarChartRenderer)) {
                CombinedChart chart = (CombinedChart) mChart.get();
                if (chart != null) {
                    mRenderers.set(i, new RoundedBarChartRenderer(chart, mAnimator, mViewPortHandler));
                }
            }
        }
    }
}
