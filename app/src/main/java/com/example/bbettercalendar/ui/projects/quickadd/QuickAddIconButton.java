package com.example.bbettercalendar.ui.projects.quickadd;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;

/**
 * One icon of the fixed row of the quick-add box: icon only, with its contentDescription and tooltip.
 * It never moves: a value set paints the icon sage and shows a dot under it; the open one sits on
 * bb_primary_container.
 */
public class QuickAddIconButton extends FrameLayout {

    private final ImageView icon;
    private final View dot;
    private boolean hasValue;
    private boolean open;

    public QuickAddIconButton(@NonNull Context context) {
        this(context, null);
    }

    public QuickAddIconButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
        int touch = getResources().getDimensionPixelSize(R.dimen.projects_quick_icon);
        icon = new ImageView(context);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(icon, new LayoutParams(touch, touch, Gravity.CENTER));
        int dotSize = getResources().getDimensionPixelSize(R.dimen.projects_quick_dot);
        dot = new View(context);
        dot.setBackgroundResource(R.drawable.bg_dot_primary);
        dot.setVisibility(GONE);
        LayoutParams dotParams = new LayoutParams(dotSize, dotSize, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        dotParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.projects_quick_dot);
        addView(dot, dotParams);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.QuickAddIconButton);
            int iconRes = a.getResourceId(R.styleable.QuickAddIconButton_quickIcon, 0);
            int labelRes = a.getResourceId(R.styleable.QuickAddIconButton_quickLabel, 0);
            a.recycle();
            if (iconRes != 0) icon.setImageResource(iconRes);
            if (labelRes != 0) {
                String label = context.getString(labelRes);
                setContentDescription(label);
                TooltipCompat.setTooltipText(this, label);
            }
        }
        render();
    }

    /** Sets whether the draft has a value for this icon and whether its picker is the open one. */
    public void setState(boolean hasValue, boolean open) {
        if (this.hasValue == hasValue && this.open == open) return;
        this.hasValue = hasValue;
        this.open = open;
        render();
    }

    private void render() {
        int tint = open ? R.color.bb_on_primary_container
                : hasValue ? R.color.bb_primary : R.color.bb_on_surface_muted;
        icon.setColorFilter(ContextCompat.getColor(getContext(), tint));
        dot.setVisibility(hasValue ? VISIBLE : GONE);
        setBackgroundResource(open ? R.drawable.bg_quick_add_icon_open : 0);
        setSelected(open);
    }
}
