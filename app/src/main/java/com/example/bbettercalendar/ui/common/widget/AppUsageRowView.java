package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Per-app usage row (u9; html 14 "Manage your apps"): raised 16dp row with
 * {@code [icon] Name [status chip] / "42m of 45m" + bar  ·  lock switch  ·  chevron}.
 *
 * <ul>
 *   <li>{@link Status#NONE}: sage bar. {@link Status#WARNING}: honey chip "Almost at limit" + honey
 *   bar. {@link Status#BLOCKED}: ember chip "Blocked" (with lock) + ember bar. Chip text can be
 *   overridden ("Blocked · 00:00").</li>
 *   <li>No limit ({@link #setNoLimit}): no bar, a sage "Set limit" action instead.</li>
 *   <li>The lock switch reports user toggles only ({@link #setLocked} is silent); the row / chevron
 *   click is the "open detail" gesture.</li>
 * </ul>
 * Contrast: the usage line stays muted even when blocked (ember on the raised row is 4.34:1, below
 * AA). u13b: the chip is a forest pill ({@code bb_primary_container}) with a tone hairline, as the
 * status pills of the design; ember / honey text on forest is below AA (see notes u13b).
 */
public class AppUsageRowView extends LinearLayout {

    public enum Status { NONE, WARNING, BLOCKED }

    public interface OnLockChangeListener {
        void onLockChanged(@NonNull AppUsageRowView row, boolean locked);
    }

    public interface OnRowClickListener {
        void onRowClick(@NonNull AppUsageRowView row);
    }

    public interface OnSetLimitClickListener {
        void onSetLimitClick(@NonNull AppUsageRowView row);
    }

    private final ImageView icon;
    private final TextView name;
    private final TextView status;
    private final TextView usage;
    private final LinearProgressIndicator bar;
    private final TextView setLimit;
    private final SwitchMaterial lock;

    private Status currentStatus = Status.NONE;
    private boolean noLimit;
    private boolean bindingLock;
    @Nullable private OnLockChangeListener lockListener;
    @Nullable private OnRowClickListener rowListener;
    @Nullable private OnSetLimitClickListener setLimitListener;

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
        setBackgroundResource(R.drawable.bg_app_row);
        int p = getResources().getDimensionPixelSize(R.dimen.widget_row_padding);
        setPadding(p, p, p, p);
        inflate(context, R.layout.view_app_usage_row, this);
        icon = findViewById(R.id.appUsageIcon);
        name = findViewById(R.id.appUsageName);
        status = findViewById(R.id.appUsageStatus);
        usage = findViewById(R.id.appUsageText);
        bar = findViewById(R.id.appUsageBar);
        setLimit = findViewById(R.id.appUsageSetLimit);
        lock = findViewById(R.id.appUsageLock);

        lock.setOnCheckedChangeListener((CompoundButton b, boolean checked) -> {
            if (!bindingLock && lockListener != null) {
                lockListener.onLockChanged(this, checked);
            }
        });
        setOnClickListener(v -> {
            if (rowListener != null) {
                rowListener.onRowClick(this);
            }
        });
        setLimit.setOnClickListener(v -> {
            if (setLimitListener != null) {
                setLimitListener.onSetLimitClick(this);
            }
        });

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AppUsageRowView, defStyleAttr, 0);
        try {
            setAppName(a.getText(R.styleable.AppUsageRowView_appName));
            int iconRes = a.getResourceId(R.styleable.AppUsageRowView_appIcon, 0);
            if (iconRes != 0) {
                setAppIcon(ContextCompat.getDrawable(context, iconRes));
            }
            setUsage(a.getText(R.styleable.AppUsageRowView_appUsage),
                    a.getInt(R.styleable.AppUsageRowView_appProgress, 0));
            int s = a.getInt(R.styleable.AppUsageRowView_appStatus, 0);
            setStatus(Status.values()[Math.max(0, Math.min(s, 2))], null);
            setNoLimit(a.getBoolean(R.styleable.AppUsageRowView_appNoLimit, false));
            setLocked(a.getBoolean(R.styleable.AppUsageRowView_appLocked, false));
        } finally {
            a.recycle();
        }
    }

    // ---- API ----

    public void setAppName(@Nullable CharSequence appName) {
        name.setText(appName);
        CharSequence label = appName != null ? appName : "";
        lock.setContentDescription(getContext().getString(R.string.widget_app_lock_cd, label));
        setContentDescription(getContext().getString(R.string.widget_app_open_cd, label));
    }

    /** The launcher icon from {@code PackageManager} (44dp). */
    public void setAppIcon(@Nullable Drawable drawable) {
        icon.setImageDrawable(drawable);
    }

    public void setAppIcon(@DrawableRes int res) {
        icon.setImageResource(res);
    }

    /** "42m of 45m" and the bar fill 0..100. */
    public void setUsage(@Nullable CharSequence text, int percent) {
        usage.setText(text);
        bar.setProgressCompat(Math.max(0, Math.min(percent, 100)), false);
    }

    /** Status chip + bar colour. {@code label} null = the default "Almost at limit" / "Blocked". */
    public void setStatus(@NonNull Status newStatus, @Nullable CharSequence label) {
        currentStatus = newStatus;
        Context ctx = getContext();
        WidgetTone tone = newStatus == Status.BLOCKED ? WidgetTone.DANGER
                : newStatus == Status.WARNING ? WidgetTone.REWARD : WidgetTone.PRIMARY;
        int color = tone.color(ctx);
        bar.setIndicatorColor(color);

        if (newStatus == Status.NONE) {
            status.setVisibility(GONE);
            return;
        }
        CharSequence text = !TextUtils.isEmpty(label) ? label : ctx.getString(
                newStatus == Status.BLOCKED ? R.string.widget_app_status_blocked : R.string.widget_app_status_warning);
        status.setText(text);
        status.setTextColor(color);
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(ContextCompat.getColor(ctx, R.color.bb_primary_container));
        chip.setStroke(getResources().getDimensionPixelSize(R.dimen.widget_hairline), color);
        chip.setCornerRadius(getResources().getDimension(R.dimen.radius_pill));
        status.setBackground(chip);
        Drawable lockIcon = newStatus == Status.BLOCKED
                ? WidgetUtils.tinted(ctx, R.drawable.ic_lock_24, color, WidgetUtils.dp(ctx, 11))
                : null;
        status.setCompoundDrawablesRelative(lockIcon, null, null, null);
        status.setVisibility(VISIBLE);
    }

    @NonNull
    public Status getStatus() {
        return currentStatus;
    }

    /** No daily limit yet: hide the bar and offer "Set limit" (html 14 Discord row). */
    public void setNoLimit(boolean none) {
        noLimit = none;
        bar.setVisibility(none ? GONE : VISIBLE);
        setLimit.setVisibility(none ? VISIBLE : GONE);
    }

    public boolean isNoLimit() {
        return noLimit;
    }

    /** Lock switch state, silent (does not call the listener). */
    public void setLocked(boolean locked) {
        bindingLock = true;
        lock.setChecked(locked);
        bindingLock = false;
    }

    public boolean isLocked() {
        return lock.isChecked();
    }

    /** Hide the lock switch (e.g. accessibility service off and the screen wants no toggle). */
    public void setLockVisible(boolean visible) {
        lock.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setOnLockChangeListener(@Nullable OnLockChangeListener l) {
        lockListener = l;
    }

    /** Row tap (= chevron): open the app detail. */
    public void setOnRowClickListener(@Nullable OnRowClickListener l) {
        rowListener = l;
    }

    public void setOnSetLimitClickListener(@Nullable OnSetLimitClickListener l) {
        setLimitListener = l;
    }
}
