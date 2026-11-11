package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.chart.LimitBarView;

/**
 * Per-app usage row (mejora-visual u9, mockup 20): {@code [icon] Name [lock | Suggested]  value} and, under
 * the name, a {@link LimitBarView} (bar with a tick at the daily limit). The lock only shows that the app is
 * blocked at its limit; the toggle lives in the app detail. The whole row is the "open detail" gesture.
 */
public class AppUsageRowView extends LinearLayout {

    public interface OnRowClickListener {
        void onRowClick(@NonNull AppUsageRowView row);
    }

    private final ImageView icon;
    private final TextView name;
    private final ImageView lockIcon;
    private final TextView suggested;
    private final TextView value;
    private final LimitBarView bar;

    @Nullable private OnRowClickListener rowListener;

    public AppUsageRowView(@NonNull Context context) {
        this(context, null);
    }

    public AppUsageRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppUsageRowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            setForeground(ContextCompat.getDrawable(context, ripple.resourceId));
        }
        int p = getResources().getDimensionPixelSize(R.dimen.spacing_xs);
        setPadding(0, p, 0, p);
        inflate(context, R.layout.view_app_usage_row, this);
        icon = findViewById(R.id.appUsageIcon);
        name = findViewById(R.id.appUsageName);
        lockIcon = findViewById(R.id.appUsageLockIcon);
        suggested = findViewById(R.id.appUsageSuggested);
        value = findViewById(R.id.appUsageText);
        bar = findViewById(R.id.appUsageBar);
        bar.setTapEnabled(false);
        bar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        setOnClickListener(v -> {
            if (rowListener != null) {
                rowListener.onRowClick(this);
            }
        });

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AppUsageRowView, defStyleAttr, 0);
        try {
            setAppName(a.getText(R.styleable.AppUsageRowView_appName));
            int iconRes = a.getResourceId(R.styleable.AppUsageRowView_appIcon, 0);
            if (iconRes != 0) {
                setAppIcon(ContextCompat.getDrawable(context, iconRes));
            }
            setValue(a.getText(R.styleable.AppUsageRowView_appUsage));
            setLocked(a.getBoolean(R.styleable.AppUsageRowView_appLocked, false));
            setSuggested(a.getBoolean(R.styleable.AppUsageRowView_appSuggested, false));
        } finally {
            a.recycle();
        }
    }

    // ---- API ----

    public void setAppName(@Nullable CharSequence appName) {
        name.setText(appName);
        CharSequence label = appName != null ? appName : "";
        setContentDescription(getContext().getString(R.string.widget_app_open_cd, label));
    }

    /** The launcher icon from {@code PackageManager}. */
    public void setAppIcon(@Nullable Drawable drawable) {
        icon.setImageDrawable(drawable);
    }

    public void setAppIcon(@DrawableRes int res) {
        icon.setImageResource(res);
    }

    /** "52 m / 1 h" or "44 m". */
    public void setValue(@Nullable CharSequence text) {
        value.setText(text);
    }

    /** Bar data (used, limit and the list's shared scale), or null for an empty track. */
    public void setBar(@Nullable LimitBarView.Data data) {
        bar.setData(data);
    }

    /** The app is blocked when it reaches its limit: lock next to the name. */
    public void setLocked(boolean locked) {
        lockIcon.setVisibility(locked ? VISIBLE : GONE);
    }

    public boolean isLocked() {
        return lockIcon.getVisibility() == View.VISIBLE;
    }

    /** "Suggested" pill: a heavily used app with no limit yet. */
    public void setSuggested(boolean on) {
        suggested.setVisibility(on ? VISIBLE : GONE);
    }

    /** Row tap: open the app detail. */
    public void setOnRowClickListener(@Nullable OnRowClickListener l) {
        rowListener = l;
    }
}
