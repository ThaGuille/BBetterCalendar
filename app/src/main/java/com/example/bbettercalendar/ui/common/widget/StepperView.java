package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * − value + stepper (u9; html 10 "Daily limit"): round raised buttons on a dark track, big tabular
 * value with an optional muted unit and caption. Integer model with {@code min}/{@code max}/
 * {@code step}; the buttons disable themselves at the bounds. A {@link ValueFormatter} lets a screen
 * show e.g. "1h 15" instead of "75".
 */
public class StepperView extends LinearLayout {

    public interface OnValueChangeListener {
        /** Only for − / + taps that change the value ({@link #setValue(int)} is silent). */
        void onValueChanged(@NonNull StepperView view, int value);
    }

    public interface ValueFormatter {
        @NonNull
        CharSequence format(int value);
    }

    private final ImageButton minus;
    private final ImageButton plus;
    private final TextView valueView;
    private final TextView unitView;
    private final TextView captionView;

    private int min = 0;
    private int max = 100;
    private int step = 1;
    private int value;
    @Nullable private ValueFormatter formatter;
    @Nullable private OnValueChangeListener listener;

    public StepperView(@NonNull Context context) {
        this(context, null);
    }

    public StepperView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StepperView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.bg_stepper_track);
        int p = getResources().getDimensionPixelSize(R.dimen.widget_row_padding);
        setPadding(p, p, p, p);
        inflate(context, R.layout.view_stepper, this);
        minus = findViewById(R.id.stepperMinus);
        plus = findViewById(R.id.stepperPlus);
        valueView = findViewById(R.id.stepperValue);
        unitView = findViewById(R.id.stepperUnit);
        captionView = findViewById(R.id.stepperCaption);
        minus.setOnClickListener(v -> change(value - step));
        plus.setOnClickListener(v -> change(value + step));

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StepperView, defStyleAttr, 0);
        try {
            min = a.getInt(R.styleable.StepperView_stepperMin, min);
            max = Math.max(min, a.getInt(R.styleable.StepperView_stepperMax, max));
            step = Math.max(1, a.getInt(R.styleable.StepperView_stepperStep, step));
            setUnit(a.getText(R.styleable.StepperView_stepperUnit));
            setCaption(a.getText(R.styleable.StepperView_stepperCaption));
            setValue(a.getInt(R.styleable.StepperView_stepperValue, min));
        } finally {
            a.recycle();
        }
    }

    /** Bounds and step (step >= 1). The current value is clamped into the new range. */
    public void setRange(int min, int max, int step) {
        this.min = min;
        this.max = Math.max(min, max);
        this.step = Math.max(1, step);
        setValue(value);
    }

    /** Clamps and shows {@code newValue} without notifying the listener (ViewModel sync). */
    public void setValue(int newValue) {
        value = Math.max(min, Math.min(max, newValue));
        render();
    }

    public int getValue() {
        return value;
    }

    /** Muted text after the number ("min"); null hides it. */
    public void setUnit(@Nullable CharSequence unit) {
        WidgetUtils.setTextOrGone(unitView, unit);
    }

    /** Small line under the number ("Target session limit"); null hides it. */
    public void setCaption(@Nullable CharSequence caption) {
        WidgetUtils.setTextOrGone(captionView, caption);
    }

    public void setValueFormatter(@Nullable ValueFormatter f) {
        formatter = f;
        render();
    }

    public void setOnValueChangeListener(@Nullable OnValueChangeListener l) {
        listener = l;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        render();
    }

    private void change(int newValue) {
        int old = value;
        setValue(newValue);
        if (listener != null && value != old) {
            listener.onValueChanged(this, value);
        }
    }

    private void render() {
        valueView.setText(formatter != null ? formatter.format(value) : String.valueOf(value));
        minus.setEnabled(isEnabled() && value > min);
        plus.setEnabled(isEnabled() && value < max);
    }
}
