package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * Period navigator (u9; html 13 under the segmented control):
 * {@code This week · Mon – Sun ............ ‹ ›}. The screen owns the period: this view only shows
 * the labels and reports taps. Disable "next" while on the current period (dimmed, not clickable).
 */
public class PeriodNavigatorView extends LinearLayout {

    public interface OnPeriodNavigateListener {
        void onPrevious(@NonNull PeriodNavigatorView view);

        void onNext(@NonNull PeriodNavigatorView view);
    }

    /** Separator drawn before the subtitle (presentational, not translatable). */
    private static final String SUBTITLE_PREFIX = "· ";

    private final TextView title;
    private final TextView subtitle;
    private final ImageButton previous;
    private final ImageButton next;

    @Nullable private OnPeriodNavigateListener listener;

    public PeriodNavigatorView(@NonNull Context context) {
        this(context, null);
    }

    public PeriodNavigatorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PeriodNavigatorView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        inflate(context, R.layout.view_period_navigator, this);
        title = findViewById(R.id.periodNavTitle);
        subtitle = findViewById(R.id.periodNavSubtitle);
        previous = findViewById(R.id.periodNavPrevious);
        next = findViewById(R.id.periodNavNext);
        previous.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPrevious(this);
            }
        });
        next.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNext(this);
            }
        });

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PeriodNavigatorView, defStyleAttr, 0);
        try {
            setTitle(a.getText(R.styleable.PeriodNavigatorView_periodTitle));
            setSubtitle(a.getText(R.styleable.PeriodNavigatorView_periodSubtitle));
            setPreviousEnabled(a.getBoolean(R.styleable.PeriodNavigatorView_periodPreviousEnabled, true));
            setNextEnabled(a.getBoolean(R.styleable.PeriodNavigatorView_periodNextEnabled, true));
        } finally {
            a.recycle();
        }
    }

    /** "This week", "October 2026"… */
    public void setTitle(@Nullable CharSequence text) {
        title.setText(text);
    }

    /** "Mon – Sun", "Oct 5 – 11"…; shown as "· Mon – Sun". Null hides it. */
    public void setSubtitle(@Nullable CharSequence text) {
        subtitle.setText(TextUtils.isEmpty(text) ? null : SUBTITLE_PREFIX + text);
    }

    public void setPreviousEnabled(boolean enabled) {
        previous.setEnabled(enabled);
    }

    /** false while showing the current period (the future has no data). */
    public void setNextEnabled(boolean enabled) {
        next.setEnabled(enabled);
    }

    public void setOnPeriodNavigateListener(@Nullable OnPeriodNavigateListener l) {
        listener = l;
    }
}
