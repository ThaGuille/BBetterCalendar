package com.example.bbettercalendar.ui.common.chart;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FontHelper;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * Base of the Canvas chart kit (plan mejora-visual u4). Subclasses own an immutable {@code Data} set through
 * {@code setData(...)} and draw it in {@link #onDrawChart}; this class supplies what every chart shares:
 * <ul>
 *   <li><b>Entrance</b>: the first time the view is on screen with data, every mark grows in
 *   {@code bb_motion_chart} ms, {@code bb_motion_chart_stagger} ms after the previous one
 *   ({@link #markProgress(int)}). Later data changes redraw at once. With animations off
 *   ({@link Motion#enabled}) everything is drawn final.</li>
 *   <li><b>Tap</b>: a tap on a mark highlights it (the others dim, {@link #alphaFor(int)}) and shows a bubble
 *   with its value (and the previous one when the chart compares). Tapping the same mark, an empty part
 *   of the chart, anywhere outside the bubble, or scrolling closes it. The bubble lives in a
 *   {@link PopupWindow}, so it is never clipped by the chart or its card.</li>
 *   <li><b>Accessibility</b>: the content description is the textual summary of the data ({@link #summary()}),
 *   since Canvas marks are invisible to screen readers and to uiautomator.</li>
 *   <li><b>Empty state</b>: without data the view draws "No data yet" / "Aún no hay datos".</li>
 * </ul>
 * Colours come only from {@code bb_*} tokens and text sizes from {@code TextAppearance.BBetter.*}
 * ({@link #textPaint(int, int)}).
 */
public abstract class BaseChartView extends View {

    protected static final int NO_MARK = -1;
    /** Alpha of the marks that are not selected while one is. */
    private static final int DIM_ALPHA = 102;
    /** Longest total of stagger delays (see {@link ChartMotion}). */
    private static final float MAX_STAGGER_SPAN_MS = 240f;

    private final Paint emptyPaint;
    private final Rect visibleRect = new Rect();
    private final PointF anchor = new PointF();
    private final int[] location = new int[2];
    private final RectF barRect = new RectF();
    private final float[] radii = new float[8];
    private final Path linePath = new Path();
    private final Interpolator entryCurve;
    private final float durationMs;
    private final float staggerMs;

    private boolean entered;
    private boolean entryDone;
    private float elapsedMs;
    @Nullable private ValueAnimator entry;

    private int selected = NO_MARK;
    private int downSelected = NO_MARK;
    private long dismissedAt = -1L;
    private int dismissedMark = NO_MARK;
    @Nullable private PopupWindow bubble;
    @Nullable private TextView bubbleText;
    private boolean tapEnabled = true;
    @Nullable private OnMarkSelectedListener markListener;

    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::onHostScrolled;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::maybeEnter;

    public BaseChartView(@NonNull Context context) {
        this(context, null);
    }

    public BaseChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseChartView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        durationMs = getResources().getInteger(R.integer.bb_motion_chart);
        staggerMs = getResources().getInteger(R.integer.bb_motion_chart_stagger);
        entryCurve = Motion.interpolator(context, R.interpolator.bb_decelerate);
        emptyPaint = textPaint(R.style.TextAppearance_BBetter_BodyMuted, 400);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        // Subclass state does not exist yet: the summary is set by the first onDataChanged().
        setContentDescription(context.getString(R.string.chart_empty));
    }

    // ------------------------------------------------------------------ subclass contract

    /** True when there is something to draw; otherwise the empty state shows. */
    protected abstract boolean hasData();

    /** Draws the data (only called when {@link #hasData()}). */
    protected abstract void onDrawChart(@NonNull Canvas canvas);

    /** Textual summary of the data, used as content description. */
    @NonNull
    protected abstract CharSequence summary();

    /** Number of entrance groups (bars, columns, slices); mark i starts i x stagger ms after the first. */
    protected int markCount() {
        return 1;
    }

    /** Mark under a tap, or {@link #NO_MARK}. */
    protected int markAt(float x, float y) {
        return NO_MARK;
    }

    /** Point (view coordinates) the bubble of {@code mark} points at, normally the top of the mark. */
    protected void markAnchor(int mark, @NonNull PointF out) {
        out.set(getWidth() / 2f, 0f);
    }

    /** Text of the bubble of {@code mark}; null shows no bubble. */
    @Nullable
    protected CharSequence bubbleText(int mark) {
        return null;
    }

    /** Height for wrap_content at {@code width}. */
    protected int desiredHeight(int width) {
        return Math.round(dimen(R.dimen.chart_height));
    }

    /** Width for an unspecified width spec. */
    protected int desiredWidth() {
        return Math.round(dimen(R.dimen.chart_height));
    }

    /** Subclasses call it from {@code setData}: resets the selection, the summary and redraws. */
    protected final void onDataChanged() {
        clearSelection();
        updateDescription();
        requestLayout();
        invalidate();
        maybeEnter();
    }

    // ------------------------------------------------------------------ public API

    /** Told when a tap highlights a mark or the highlight goes away. */
    public interface OnMarkSelectedListener {
        /**
         * @param mark the highlighted mark, or -1 when nothing is highlighted any more
         * @param text the bubble text of {@code mark} (null when -1 or the mark has no bubble)
         */
        void onMarkSelected(@NonNull BaseChartView chart, int mark, @Nullable CharSequence text);
    }

    /** Listener of the tap highlight (e.g. a header that repeats the tapped value), or null. */
    public void setOnMarkSelectedListener(@Nullable OnMarkSelectedListener listener) {
        markListener = listener;
    }

    /** Turns tap-to-inspect off, e.g. when the chart sits inside a row that opens a screen. */
    public void setTapEnabled(boolean enabled) {
        tapEnabled = enabled;
        if (!enabled) {
            clearSelection();
        }
    }

    /** Index of the highlighted mark, or -1. */
    public int getSelectedMark() {
        return selected;
    }

    /** Closes the bubble and removes the highlight. */
    public final void clearSelection() {
        if (bubble != null && bubble.isShowing()) {
            bubble.dismiss();
        }
        unselect();
    }

    // ------------------------------------------------------------------ helpers for subclasses

    /** 0..1 entrance progress of mark {@code index} (already eased). */
    protected final float markProgress(int index) {
        if (entryDone || !Motion.enabled(getContext())) {
            return 1f;
        }
        if (!entered) {
            return 0f;
        }
        float f = ChartMotion.markFraction(elapsedMs, index, Math.max(1, markCount()), durationMs, staggerMs,
                MAX_STAGGER_SPAN_MS);
        return entryCurve.getInterpolation(f);
    }

    /** Paint alpha of a mark: full, or dimmed while another one is selected. */
    protected final int alphaFor(int mark) {
        return selected == NO_MARK || selected == mark ? 255 : DIM_ALPHA;
    }

    protected final int selectedMark() {
        return selected;
    }

    protected final int color(@ColorRes int res) {
        return ContextCompat.getColor(getContext(), res);
    }

    protected final float dimen(@DimenRes int res) {
        return getResources().getDimension(res);
    }

    protected final Paint fillPaint(@ColorRes int res) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color(res));
        return p;
    }

    /** Solid stroke in {@code res}. */
    protected final Paint strokePaint(@ColorRes int res, @DimenRes int width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color(res));
        p.setStrokeWidth(dimen(width));
        return p;
    }

    /** Dashed stroke in {@code res} (grid dots, average dashes). */
    protected final Paint strokePaint(@ColorRes int res, @DimenRes int width, @DimenRes int dashOn,
                                      @DimenRes int dashOff) {
        Paint p = strokePaint(res, width);
        p.setPathEffect(new DashPathEffect(new float[]{dimen(dashOn), dimen(dashOff)}, 0f));
        return p;
    }

    /**
     * Text paint whose size and colour come from a {@code TextAppearance.BBetter.*} style (no literal sizes),
     * in the app font at {@code weight}, with tabular figures.
     */
    protected final Paint textPaint(@StyleRes int style, int weight) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Attribute ids must be ascending for obtainStyledAttributes: textSize < textColor.
        TypedArray t = getContext().obtainStyledAttributes(style,
                new int[]{android.R.attr.textSize, android.R.attr.textColor});
        try {
            p.setTextSize(t.getDimension(0, dimen(R.dimen.text_micro)));
            p.setColor(t.getColor(1, color(R.color.bb_on_surface_muted)));
        } finally {
            t.recycle();
        }
        p.setTypeface(FontHelper.appTypeface(getContext(), weight));
        p.setFontFeatureSettings("tnum");
        return p;
    }

    protected static float textHeight(@NonNull Paint p) {
        return p.descent() - p.ascent();
    }

    /** Baseline that centres one line of {@code p} on {@code cy}. */
    protected static float baselineAt(@NonNull Paint p, float cy) {
        return cy - (p.descent() + p.ascent()) / 2f;
    }

    /** Bar with only the top (or only the bottom) corners rounded; nothing when it has no height. */
    protected final void drawBar(@NonNull Canvas canvas, float l, float t, float r, float b, float radius,
                                 boolean roundTop, @NonNull Paint paint) {
        if (b - t <= 0f || r - l <= 0f) {
            return;
        }
        float rad = Math.max(0f, Math.min(radius, Math.min((r - l) / 2f, b - t)));
        for (int i = 0; i < 8; i++) {
            boolean topCorner = i < 4;
            radii[i] = topCorner == roundTop ? rad : 0f;
        }
        barRect.set(l, t, r, b);
        linePath.reset();
        linePath.addRoundRect(barRect, radii, Path.Direction.CW);
        canvas.drawPath(linePath, paint);
    }

    /** Straight line through a Path, so dash effects also draw with hardware acceleration. */
    protected final void drawLine(@NonNull Canvas canvas, float x1, float y1, float x2, float y2,
                                  @NonNull Paint paint) {
        linePath.reset();
        linePath.moveTo(x1, y1);
        linePath.lineTo(x2, y2);
        canvas.drawPath(linePath, paint);
    }

    /** {@code labels} resized to {@code n} (missing entries empty). */
    @NonNull
    protected static String[] fit(@Nullable String[] labels, int n) {
        String[] out = new String[Math.max(0, n)];
        for (int i = 0; i < out.length; i++) {
            out[i] = labels != null && i < labels.length && labels[i] != null ? labels[i] : "";
        }
        return out;
    }

    /** {@code values} copied to length {@code n} (missing entries 0); null stays null. */
    @Nullable
    protected static float[] fitValues(@Nullable float[] values, int n) {
        if (values == null) {
            return null;
        }
        float[] out = new float[Math.max(0, n)];
        System.arraycopy(values, 0, out, 0, Math.min(values.length, out.length));
        return out;
    }

    // ------------------------------------------------------------------ View

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? Math.max(getSuggestedMinimumWidth(), desiredWidth())
                : MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.max(getSuggestedMinimumHeight(), desiredHeight(width));
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!hasData()) {
            float cx = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
            float cy = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
            canvas.drawText(getContext().getString(R.string.chart_empty), cx, baselineAt(emptyPaint, cy), emptyPaint);
            return;
        }
        onDrawChart(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewTreeObserver vto = getViewTreeObserver();
        vto.addOnScrollChangedListener(scrollListener);
        vto.addOnGlobalLayoutListener(layoutListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        ViewTreeObserver vto = getViewTreeObserver();
        vto.removeOnScrollChangedListener(scrollListener);
        vto.removeOnGlobalLayoutListener(layoutListener);
        clearSelection();
        if (entry != null) {
            entry.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) {
            maybeEnter();
        } else {
            clearSelection();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!tapEnabled || !hasData() || !isEnabled()) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downSelected = selected;
                return true;
            case MotionEvent.ACTION_UP: {
                int mark = markAt(event.getX(), event.getY());
                // The bubble may already have closed itself for this very tap (outside touch): treat the
                // mark it showed as still open, so tapping the same mark again closes instead of reopening.
                int open = downSelected;
                if (open == NO_MARK && dismissedMark != NO_MARK && dismissedAt >= event.getDownTime()) {
                    open = dismissedMark;
                }
                if (mark == NO_MARK || mark == open) {
                    clearSelection();
                } else {
                    select(mark);
                }
                performClick();
                return true;
            }
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // ------------------------------------------------------------------ internals

    private void updateDescription() {
        setContentDescription(hasData() ? summary() : getContext().getString(R.string.chart_empty));
    }

    private void onHostScrolled() {
        if (bubble != null && bubble.isShowing()) {
            clearSelection();
        }
        maybeEnter();
    }

    /** Starts the entrance the first time the view is on screen with data. */
    private void maybeEnter() {
        if (entered || !hasData() || !isAttachedToWindow() || !isShown() || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        if (!getGlobalVisibleRect(visibleRect)) {
            return;
        }
        entered = true;
        if (!Motion.enabled(getContext())) {
            entryDone = true;
            invalidate();
            return;
        }
        float total = ChartMotion.totalMs(Math.max(1, markCount()), durationMs, staggerMs, MAX_STAGGER_SPAN_MS);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, total);
        animator.setDuration((long) total);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            elapsedMs = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                entryDone = true;
                entry = null;
                invalidate();
            }
        });
        entry = animator;
        elapsedMs = 0f;
        animator.start();
    }

    private void select(int mark) {
        selected = mark;
        invalidate();
        CharSequence text = bubbleText(mark);
        if (text != null) {
            showBubble(mark, text);
        }
        if (markListener != null) {
            markListener.onMarkSelected(this, mark, text);
        }
    }

    /** Drops the highlight (the bubble is already closed or closing) and tells the listener once. */
    private void unselect() {
        if (selected == NO_MARK) {
            return;
        }
        selected = NO_MARK;
        invalidate();
        if (markListener != null) {
            markListener.onMarkSelected(this, NO_MARK, null);
        }
    }

    private void showBubble(int mark, @NonNull CharSequence text) {
        if (!isAttachedToWindow() || getWindowToken() == null) {
            return;
        }
        if (bubble == null) {
            bubbleText = (TextView) LayoutInflater.from(getContext())
                    .inflate(R.layout.view_chart_bubble, null, false);
            bubbleText.setOnClickListener(v -> clearSelection());
            PopupWindow popup = new PopupWindow(bubbleText, ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, false);
            popup.setOutsideTouchable(true);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setAnimationStyle(android.R.style.Animation_Toast);
            popup.setOnDismissListener(() -> {
                dismissedAt = SystemClock.uptimeMillis();
                dismissedMark = selected;
                unselect();
            });
            bubble = popup;
        }
        TextView label = bubbleText;
        if (label == null) {
            return;
        }
        label.setText(text);
        View root = getRootView();
        int rootW = root.getWidth();
        label.measure(MeasureSpec.makeMeasureSpec(rootW, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int w = label.getMeasuredWidth();
        int h = label.getMeasuredHeight();
        markAnchor(mark, anchor);
        getLocationInWindow(location);
        int margin = Math.round(dimen(R.dimen.chart_bubble_margin));
        int topLimit = margin;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        if (insets != null) {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topLimit += bars.top;
        }
        int x = Math.round(location[0] + anchor.x - w / 2f);
        x = Math.max(margin, Math.min(rootW - w - margin, x));
        int anchorY = Math.round(location[1] + anchor.y);
        int y = anchorY - h - margin;
        if (y < topLimit) {
            y = anchorY + margin;
        }
        PopupWindow popup = bubble;
        if (popup.isShowing()) {
            popup.update(x, y, w, h);
        } else {
            popup.showAtLocation(this, Gravity.TOP | Gravity.START, x, y);
        }
    }
}
