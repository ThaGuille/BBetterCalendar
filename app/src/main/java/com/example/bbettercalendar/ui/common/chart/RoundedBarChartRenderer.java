package com.example.bbettercalendar.ui.common.chart;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.buffer.BarBuffer;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.renderer.BarChartRenderer;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.Utils;
import com.github.mikephil.charting.utils.ViewPortHandler;

/**
 * MPAndroidChart 3.1.0 only draws square bars; the Stitch designs round them (html 10
 * {@code rounded-t-lg} = 8dp on the top of the visible segment of each bar, html 14 {@code rx=4}
 * on the FOCUS bands). Same geometry as {@link BarChartRenderer#drawDataSet}, but the rect of
 * each bar (or of the topmost non-empty segment of a stack) is drawn as a path with rounded top
 * corners, and a {@link BandBarDataSet} run as one rounded block. Hooked by {@link ChartStyler}.
 *
 * <p>Bars with gradients / borders / shadows are left to the library's own
 * drawing: no BBetter chart uses them.
 */
public class RoundedBarChartRenderer extends BarChartRenderer {

    /** dp. html 10 rounded-t-lg (0.5rem). */
    static final float BAR_RADIUS_DP = 8f;
    /** dp. html 14 rx="4". */
    static final float BAND_RADIUS_DP = 4f;

    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];

    public RoundedBarChartRenderer(BarDataProvider chart, ChartAnimator animator,
                                   ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);
    }

    @Override
    protected void drawDataSet(Canvas c, IBarDataSet dataSet, int index) {
        boolean library = dataSet.getGradientColor() != null
                || (dataSet.getGradientColors() != null && !dataSet.getGradientColors().isEmpty())
                || dataSet.getBarBorderWidth() > 0f
                || mChart.isDrawBarShadowEnabled();
        if (library) {
            super.drawDataSet(c, dataSet, index);
            return;
        }

        Transformer trans = mChart.getTransformer(dataSet.getAxisDependency());
        float phaseX = mAnimator.getPhaseX();
        float phaseY = mAnimator.getPhaseY();

        BarBuffer buffer = mBarBuffers[index];
        buffer.setPhases(phaseX, phaseY);
        buffer.setDataSet(index);
        buffer.setInverted(mChart.isInverted(dataSet.getAxisDependency()));
        buffer.setBarWidth(mChart.getBarData().getBarWidth());
        buffer.feed(dataSet);
        trans.pointValuesToPixel(buffer.buffer);

        final boolean singleColor = dataSet.getColors().size() == 1;
        if (singleColor) {
            mRenderPaint.setColor(dataSet.getColor());
        }
        float[] b = buffer.buffer;

        if (dataSet instanceof BandBarDataSet) {
            drawBands(c, b, buffer.size());
            return;
        }

        // Rect j/4 belongs to entry i; a stack contributes one rect per value. Round only the
        // topmost segment with a positive value (a zero "over" segment must not hide the cap).
        int entryCount = (int) (dataSet.getEntryCount() * phaseX);
        int j = 0;
        for (int i = 0; i < entryCount && j < buffer.size(); i++) {
            BarEntry e = dataSet.getEntryForIndex(i);
            if (e == null) {
                continue;
            }
            float[] vals = e.getYVals();
            boolean stacked = dataSet.isStacked() && vals != null;
            int segments = stacked ? vals.length : 1;
            int top = segments - 1;
            if (stacked) {
                for (int k = vals.length - 1; k >= 0; k--) {
                    if (vals[k] > 0f) {
                        top = k;
                        break;
                    }
                }
            }
            for (int k = 0; k < segments && j < buffer.size(); k++, j += 4) {
                if (!mViewPortHandler.isInBoundsLeft(b[j + 2])) {
                    continue;
                }
                if (!mViewPortHandler.isInBoundsRight(b[j])) {
                    return;
                }
                if (!singleColor) {
                    mRenderPaint.setColor(dataSet.getColor(j / 4));
                }
                if (b[j + 1] == b[j + 3]) {
                    continue; // zero-height segment: nothing to draw
                }
                if (k == top) {
                    drawRoundedTop(c, b[j], b[j + 1], b[j + 2], b[j + 3]);
                } else {
                    c.drawRect(b[j], b[j + 1], b[j + 2], b[j + 3], mRenderPaint);
                }
            }
        }
    }

    /** Contiguous bars (right edge touches the next left edge) become one rounded block. */
    private void drawBands(Canvas c, float[] b, int size) {
        int j = 0;
        while (j < size) {
            float left = b[j];
            float top = b[j + 1];
            float right = b[j + 2];
            float bottom = b[j + 3];
            int next = j + 4;
            while (next < size && Math.abs(b[next] - right) < 1.5f) {
                right = b[next + 2];
                next += 4;
            }
            if (mViewPortHandler.isInBoundsRight(left) && mViewPortHandler.isInBoundsLeft(right)) {
                rect.set(left, Math.min(top, bottom), right, Math.max(top, bottom));
                float r = Math.min(Utils.convertDpToPixel(BAND_RADIUS_DP),
                        Math.min(rect.width(), rect.height()) / 2f);
                c.drawRoundRect(rect, r, r, mRenderPaint);
            }
            j = next;
        }
    }

    private void drawRoundedTop(Canvas c, float left, float top, float right, float bottom) {
        float t = Math.min(top, bottom);
        float btm = Math.max(top, bottom);
        float l = Math.min(left, right);
        float rr = Math.max(left, right);
        float r = Math.min(Utils.convertDpToPixel(BAR_RADIUS_DP),
                Math.min((rr - l) / 2f, btm - t));
        if (r <= 0f) {
            c.drawRect(l, t, rr, btm, mRenderPaint);
            return;
        }
        rect.set(l, t, rr, btm);
        radii[0] = r;
        radii[1] = r;
        radii[2] = r;
        radii[3] = r;
        radii[4] = 0f;
        radii[5] = 0f;
        radii[6] = 0f;
        radii[7] = 0f;
        path.reset();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        c.drawPath(path, mRenderPaint);
    }
}
