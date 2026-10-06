package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Labelled progress row (u9; html 13 "Projects this week"): title on the left, bold value on the
 * right, 6dp bar underneath. Bar colour defaults to sage; projects pass their accent
 * ({@code bb_project_N}) with {@link #setIndicatorColorRes(int)}.
 */
public class ProgressRowView extends LinearLayout {

    private final TextView title;
    private final TextView value;
    private final LinearProgressIndicator bar;

    public ProgressRowView(@NonNull Context context) {
        this(context, null);
    }

    public ProgressRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgressRowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        inflate(context, R.layout.view_progress_row, this);
        title = findViewById(R.id.progressRowTitle);
        value = findViewById(R.id.progressRowValue);
        bar = findViewById(R.id.progressRowBar);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ProgressRowView, defStyleAttr, 0);
        try {
            setTitle(a.getText(R.styleable.ProgressRowView_rowTitle));
            setValue(a.getText(R.styleable.ProgressRowView_rowValue));
            setProgress(a.getInt(R.styleable.ProgressRowView_rowProgress, 0));
            if (a.hasValue(R.styleable.ProgressRowView_rowIndicatorColor)) {
                setIndicatorColor(a.getColor(R.styleable.ProgressRowView_rowIndicatorColor, 0));
            }
        } finally {
            a.recycle();
        }
    }

    public void setTitle(@Nullable CharSequence text) {
        title.setText(text);
    }

    public void setValue(@Nullable CharSequence text) {
        value.setText(text);
    }

    /** 0..100 (clamped). */
    public void setProgress(int percent) {
        bar.setProgressCompat(Math.max(0, Math.min(percent, 100)), false);
    }

    /** Animated variant for live updates. */
    public void setProgress(int percent, boolean animate) {
        bar.setProgressCompat(Math.max(0, Math.min(percent, 100)), animate);
    }

    public void setIndicatorColor(@ColorInt int color) {
        bar.setIndicatorColor(color);
    }

    public void setIndicatorColorRes(@ColorRes int colorRes) {
        bar.setIndicatorColor(ContextCompat.getColor(getContext(), colorRes));
    }
}
