package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * A column of {@link SkeletonView} rows (mejora-visual u3) shown while a list loads. {@code app:skeletonRows}
 * rows of {@code app:skeletonRowHeight} (default 16dp) with decreasing widths. {@link #show()} /
 * {@link #hide()}: hide ends every shine and sets the group GONE once the data is there.
 */
public class SkeletonGroup extends LinearLayout {

    private static final float[] FRACTIONS = {1f, 0.8f, 0.6f};

    private int rows = 3;
    private int rowHeight;

    public SkeletonGroup(@NonNull Context context) {
        this(context, null);
    }

    public SkeletonGroup(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SkeletonGroup(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        rowHeight = getResources().getDimensionPixelSize(R.dimen.skeleton_row_height);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SkeletonGroup, defStyleAttr, 0);
        try {
            rows = a.getInt(R.styleable.SkeletonGroup_skeletonRows, rows);
            rowHeight = a.getDimensionPixelSize(R.styleable.SkeletonGroup_skeletonRowHeight, rowHeight);
        } finally {
            a.recycle();
        }
        setRows(rows);
    }

    public void setRows(int count) {
        rows = Math.max(0, count);
        removeAllViews();
        int gap = getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        for (int i = 0; i < rows; i++) {
            SkeletonView row = new SkeletonView(getContext());
            row.setWidthFraction(FRACTIONS[i % FRACTIONS.length]);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, rowHeight);
            lp.topMargin = i == 0 ? 0 : gap;
            addView(row, lp);
        }
    }

    /** Shows the rows and starts the shine. */
    public void show() {
        setVisibility(VISIBLE);
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof SkeletonView) {
                ((SkeletonView) getChildAt(i)).start();
            }
        }
    }

    /** Data arrived: stops every shine and hides the group. */
    public void hide() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof SkeletonView) {
                ((SkeletonView) getChildAt(i)).stopAndHide();
            }
        }
        setVisibility(GONE);
    }
}
