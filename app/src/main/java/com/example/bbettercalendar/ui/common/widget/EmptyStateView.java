package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * Empty state (u9): sage icon on a raised disc, Title, muted message and an optional secondary pill
 * action. Centred; 24dp padding. Use it inside a card or full-screen when a list has no data.
 */
public class EmptyStateView extends LinearLayout {

    private final View iconDisc;
    private final ImageView icon;
    private final TextView title;
    private final TextView message;
    private final Button action;

    public EmptyStateView(@NonNull Context context) {
        this(context, null);
    }

    public EmptyStateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmptyStateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        int p = getResources().getDimensionPixelSize(R.dimen.spacing_lg);
        setPadding(p, p, p, p);
        inflate(context, R.layout.view_empty_state, this);
        iconDisc = findViewById(R.id.emptyStateIconDisc);
        icon = findViewById(R.id.emptyStateIcon);
        title = findViewById(R.id.emptyStateTitle);
        message = findViewById(R.id.emptyStateMessage);
        action = findViewById(R.id.emptyStateAction);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EmptyStateView, defStyleAttr, 0);
        try {
            setIcon(a.getResourceId(R.styleable.EmptyStateView_emptyIcon, 0));
            setTitle(a.getText(R.styleable.EmptyStateView_emptyTitle));
            setMessage(a.getText(R.styleable.EmptyStateView_emptyMessage));
            setAction(a.getText(R.styleable.EmptyStateView_emptyActionText), null);
        } finally {
            a.recycle();
        }
    }

    /** 0 hides the disc. */
    public void setIcon(@DrawableRes int res) {
        if (res == 0) {
            iconDisc.setVisibility(GONE);
        } else {
            icon.setImageResource(res);
            iconDisc.setVisibility(VISIBLE);
        }
    }

    public void setTitle(@Nullable CharSequence text) {
        WidgetUtils.setTextOrGone(title, text);
    }

    public void setMessage(@Nullable CharSequence text) {
        WidgetUtils.setTextOrGone(message, text);
    }

    /** Secondary pill button; null / empty text hides it. The listener may be set later. */
    public void setAction(@Nullable CharSequence text, @Nullable OnClickListener onClick) {
        action.setText(text);
        action.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
        if (onClick != null) {
            action.setOnClickListener(onClick);
        }
    }

    public void setOnActionClickListener(@Nullable OnClickListener onClick) {
        action.setOnClickListener(onClick);
    }
}
