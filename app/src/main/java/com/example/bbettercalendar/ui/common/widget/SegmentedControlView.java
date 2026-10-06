package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

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
 */
public class SegmentedControlView extends LinearLayout {

    public interface OnSegmentSelectedListener {
        void onSegmentSelected(@NonNull SegmentedControlView view, int index);
    }

    private final int trackHeight;
    private int selected = -1;
    @Nullable private OnSegmentSelectedListener listener;

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
        selected = index;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setSelected(i == index);
        }
    }

    public int getSelectedIndex() {
        return selected;
    }

    /** Disabled items render {@code bb_disabled} and ignore taps (AddEvent "Reminder" case). */
    public void setItemEnabled(int index, boolean enabled) {
        View child = getChildAt(index);
        if (child != null) {
            child.setEnabled(enabled);
        }
    }

    public void setOnSegmentSelectedListener(@Nullable OnSegmentSelectedListener l) {
        listener = l;
    }

    private void selectFromUser(int index) {
        if (index == selected) {
            return;
        }
        setSelectedIndex(index);
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
