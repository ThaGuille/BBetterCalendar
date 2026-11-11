package com.example.bbettercalendar.ui.common.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.motion.Motion;

import java.util.Arrays;
import java.util.List;

/**
 * Pill segmented control (u9; html 13 Week/Month/Year, html 10 Week/Month). Wraps the u7 approach:
 * NOT a {@code MaterialButtonToggleGroup} (MDC 1.9.0 squares the inner corners and rejects custom
 * item backgrounds) but a pill track ({@code Widget.BBetter.SegmentedControl}) holding
 * {@code Widget.BBetter.SegmentedControl.Item} children whose selection is {@code setSelected}.
 *
 * <p>Items come from {@code app:segmentEntries} (string-array) or {@link #setItems}. Exactly one item
 * is selected; {@link OnSegmentSelectedListener} fires only on user taps that change it. Height is
 * fixed at {@code @dimen/segmented_track_height} unless the layout gives an exact height.
 *
 * <p>mejora-visual u3: the selected pill is drawn by the track itself and slides to the new item
 * (260 ms, {@code bb_standard}) while the two labels cross-fade; with animations off it jumps.
 */
public class SegmentedControlView extends LinearLayout {

    public interface OnSegmentSelectedListener {
        void onSegmentSelected(@NonNull SegmentedControlView view, int index);
    }

    private final int trackHeight;
    private int selected = -1;
    @Nullable private OnSegmentSelectedListener listener;

    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final int colorOn;
    private final int colorMuted;
    private final int colorDisabled;
    @Nullable private ValueAnimator slide;
    /** Previous item while a slide runs (its label fades back to muted). */
    private int leaving = -1;
    private float slideProgress = 1f;

    public SegmentedControlView(@NonNull Context context) {
        this(context, null);
    }

    public SegmentedControlView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SegmentedControlView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        // Track look (pill background + 4dp padding + horizontal) from the u7 style.
        super(context, attrs, defStyleAttr, R.style.Widget_BBetter_SegmentedControl);
        setOrientation(HORIZONTAL);
        trackHeight = getResources().getDimensionPixelSize(R.dimen.segmented_track_height);
        setWillNotDraw(false);
        colorOn = ContextCompat.getColor(context, R.color.bb_on_primary);
        colorMuted = ContextCompat.getColor(context, R.color.bb_on_surface_muted);
        colorDisabled = ContextCompat.getColor(context, R.color.bb_disabled);
        pillPaint.setColor(ContextCompat.getColor(context, R.color.bb_primary));

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SegmentedControlView, defStyleAttr, 0);
        try {
            int entries = a.getResourceId(R.styleable.SegmentedControlView_segmentEntries, 0);
            if (entries != 0) {
                setItems(entries);
            }
            int initial = a.getInt(R.styleable.SegmentedControlView_segmentSelected, 0);
            if (getChildCount() > 0) {
                setSelectedIndex(initial);
            }
        } finally {
            a.recycle();
        }
    }

    /** Replaces the items (keeps the selected index when still valid, else selects 0). */
    public void setItems(@NonNull List<? extends CharSequence> labels) {
        removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < labels.size(); i++) {
            TextView item = (TextView) inflater.inflate(R.layout.view_segmented_item, this, false);
            item.setText(labels.get(i));
            final int index = i;
            item.setOnClickListener(v -> selectFromUser(index));
            addView(item);
        }
        int keep = selected >= 0 && selected < labels.size() ? selected : 0;
        selected = -1;
        if (!labels.isEmpty()) {
            setSelectedIndex(keep);
        }
    }

    public void setItems(@NonNull CharSequence... labels) {
        setItems(Arrays.asList(labels));
    }

    public void setItems(@ArrayRes int arrayRes) {
        setItems(getResources().getTextArray(arrayRes));
    }

    /** Selects without notifying the listener (state restore, ViewModel sync). */
    public void setSelectedIndex(int index) {
        if (index < 0 || index >= getChildCount()) {
            return;
        }
        select(index, false);
    }

    /** Selects {@code index}; {@code animate} slides the pill (only once laid out and with animations on). */
    private void select(int index, boolean animate) {
        int previous = selected;
        selected = index;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setSelected(i == index);
        }
        cancelSlide();
        View target = getChildAt(index);
        boolean canSlide = animate && previous >= 0 && previous != index && isLaidOut()
                && target.getWidth() > 0 && Motion.enabled(getContext());
        if (!canSlide) {
            leaving = -1;
            slideProgress = 1f;
            placePill(target);
            applyLabelColors();
            invalidate();
            return;
        }
        final float fromL = pill.left;
        final float fromR = pill.right;
        final float toL = target.getLeft();
        final float toR = target.getRight();
        leaving = previous;
        slideProgress = 0f;
        slide = ValueAnimator.ofFloat(0f, 1f);
        slide.setDuration(Motion.duration(getContext(), R.integer.bb_motion_slide));
        slide.setInterpolator(Motion.interpolator(getContext(), R.interpolator.bb_standard));
        slide.addUpdateListener(a -> {
            slideProgress = (float) a.getAnimatedValue();
            pill.left = fromL + (toL - fromL) * slideProgress;
            pill.right = fromR + (toR - fromR) * slideProgress;
            applyLabelColors();
            invalidate();
        });
        slide.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                leaving = -1;
                slideProgress = 1f;
                slide = null;
                applyLabelColors();
            }
        });
        applyLabelColors();
        slide.start();
    }

    private void cancelSlide() {
        if (slide != null) {
            ValueAnimator running = slide;
            slide = null;
            running.removeAllListeners();
            running.cancel();
        }
    }

    private void placePill(@Nullable View item) {
        if (item == null) {
            return;
        }
        pill.set(item.getLeft(), item.getTop(), item.getRight(), item.getBottom());
    }

    /** Label colours by hand (a ColorStateList cannot cross-fade): on-primary on the pill, muted elsewhere. */
    private void applyLabelColors() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof TextView)) {
                continue;
            }
            int color;
            if (!child.isEnabled()) {
                color = colorDisabled;
            } else if (i == selected) {
                color = leaving >= 0 ? (Integer) argb.evaluate(slideProgress, colorMuted, colorOn) : colorOn;
            } else if (i == leaving) {
                color = (Integer) argb.evaluate(slideProgress, colorOn, colorMuted);
            } else {
                color = colorMuted;
            }
            ((TextView) child).setTextColor(color);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (slide == null && selected >= 0 && selected < getChildCount()) {
            placePill(getChildAt(selected));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (selected >= 0 && pill.right > pill.left) {
            float radius = pill.height() / 2f;
            canvas.drawRoundRect(pill, radius, radius, pillPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelSlide();
        super.onDetachedFromWindow();
    }

    public int getSelectedIndex() {
        return selected;
    }

    /** Disabled items render {@code bb_disabled} and ignore taps (AddEvent "Reminder" case). */
    public void setItemEnabled(int index, boolean enabled) {
        View child = getChildAt(index);
        if (child != null) {
            child.setEnabled(enabled);
            applyLabelColors();
        }
    }

    public void setOnSegmentSelectedListener(@Nullable OnSegmentSelectedListener l) {
        listener = l;
    }

    private void selectFromUser(int index) {
        if (index == selected) {
            return;
        }
        select(index, true);
        if (listener != null) {
            listener.onSegmentSelected(this, index);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(trackHeight, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
