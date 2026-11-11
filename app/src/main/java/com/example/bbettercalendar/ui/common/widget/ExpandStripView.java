package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * Expandable strip (mejora-visual u3, mockup 04 "Desplegar"): a {@code bb_surface_raised} header with the
 * title, a "Ver / Ocultar" action and a chevron that rotates 180 degrees; the content (children declared in
 * XML, or {@link #addContentView}) unfolds with {@code TransitionManager.beginDelayedTransition} (250 ms,
 * {@code bb_standard}) and pushes what is below. With animations off it just switches.
 */
public class ExpandStripView extends LinearLayout {

    private final View header;
    private final ImageView chevron;
    private final TextView title;
    private final TextView action;
    private final LinearLayout content;
    private boolean expanded;
    @Nullable private OnExpandedChangeListener expandedListener;

    /** Called when the user (not {@link #setExpanded(boolean)} from code) toggles the strip. */
    public interface OnExpandedChangeListener {
        void onExpandedChanged(boolean expanded);
    }

    public ExpandStripView(@NonNull Context context) {
        this(context, null);
    }

    public ExpandStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExpandStripView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_expand_strip);
        inflate(context, R.layout.view_expand_strip, this);
        header = findViewById(R.id.expandStripHeader);
        chevron = findViewById(R.id.expandStripChevron);
        title = findViewById(R.id.expandStripTitle);
        action = findViewById(R.id.expandStripAction);
        content = findViewById(R.id.expandStripContent);
        header.setOnClickListener(v -> {
            setExpanded(!expanded, true);
            if (expandedListener != null) {
                expandedListener.onExpandedChanged(expanded);
            }
        });

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ExpandStripView, defStyleAttr, 0);
        try {
            setTitle(a.getText(R.styleable.ExpandStripView_stripTitle));
            setExpanded(a.getBoolean(R.styleable.ExpandStripView_stripExpanded, false), false);
        } finally {
            a.recycle();
        }
    }

    public void setOnExpandedChangeListener(@Nullable OnExpandedChangeListener listener) {
        expandedListener = listener;
    }

    /** Removes every view added to the part that unfolds. */
    public void clearContent() {
        content.removeAllViews();
    }

    public void setTitle(@Nullable CharSequence text) {
        title.setText(text);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expand) {
        setExpanded(expand, false);
    }

    /** Expands or collapses; {@code animate} unfolds with a delayed transition when animations are on. */
    public void setExpanded(boolean expand, boolean animate) {
        expanded = expand;
        boolean run = animate && Motion.enabled(getContext()) && isLaidOut();
        if (run) {
            ViewGroup scene = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : this;
            AutoTransition transition = new AutoTransition();
            transition.setDuration(Motion.duration(getContext(), R.integer.bb_motion_expand));
            transition.setInterpolator(Motion.interpolator(getContext(), R.interpolator.bb_standard));
            TransitionManager.beginDelayedTransition(scene, transition);
        }
        content.setVisibility(expand ? VISIBLE : GONE);
        action.setText(expand ? R.string.widget_strip_hide : R.string.widget_strip_show);
        float target = expand ? 180f : 0f;
        chevron.animate().cancel();
        if (run) {
            chevron.animate().rotation(target)
                    .setDuration(Motion.duration(getContext(), R.integer.bb_motion_expand))
                    .setInterpolator(Motion.interpolator(getContext(), R.interpolator.bb_standard))
                    .start();
        } else {
            chevron.setRotation(target);
        }
        header.setContentDescription(null);
        header.setSelected(expand);
    }

    /** Adds a view to the part that unfolds. */
    public void addContentView(@NonNull View child) {
        content.addView(child);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        // Our own header/content are inflated before `content` is set; everything after is user content.
        if (content != null && child != header && child != content) {
            content.addView(child, index, params);
        } else {
            super.addView(child, index, params);
        }
    }
}
