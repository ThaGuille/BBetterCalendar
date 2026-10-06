package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.widget.TextViewCompat;

import com.example.bbettercalendar.R;

/**
 * Section / card header (u9; html 13 "Past 12 weeks", 14 "Manage your apps", 01 "Studying now").
 *
 * <pre>
 *   [icon] Title [NEW]                      meta  |  Action
 *          subtitle
 * </pre>
 *
 * Every part except the title is optional and hidden while empty. Title = Title 17sp (override with
 * {@code app:headerTitleAppearance}), subtitle = Caption muted, badge = honey pill, meta = muted
 * semibold text or a hairline pill ({@code app:headerMetaPill}), action = sage text button.
 */
public class SectionHeaderView extends LinearLayout {

    /** Click on the trailing action ("See all", "Add"). */
    public interface OnActionClickListener {
        void onActionClick(@NonNull SectionHeaderView header);
    }

    private final ImageView icon;
    private final TextView title;
    private final TextView badge;
    private final TextView subtitle;
    private final TextView meta;
    private final Button action;

    @Nullable private OnActionClickListener actionListener;

    public SectionHeaderView(@NonNull Context context) {
        this(context, null);
    }

    public SectionHeaderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SectionHeaderView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        inflate(context, R.layout.view_section_header, this);
        icon = findViewById(R.id.sectionHeaderIcon);
        title = findViewById(R.id.sectionHeaderTitle);
        badge = findViewById(R.id.sectionHeaderBadge);
        subtitle = findViewById(R.id.sectionHeaderSubtitle);
        meta = findViewById(R.id.sectionHeaderMeta);
        action = findViewById(R.id.sectionHeaderAction);
        action.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onActionClick(this);
            }
        });

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SectionHeaderView, defStyleAttr, 0);
        try {
            int appearance = a.getResourceId(R.styleable.SectionHeaderView_headerTitleAppearance, 0);
            if (appearance != 0) {
                setTitleAppearance(appearance);
            }
            setTitle(a.getText(R.styleable.SectionHeaderView_headerTitle));
            setSubtitle(a.getText(R.styleable.SectionHeaderView_headerSubtitle));
            setIcon(a.getResourceId(R.styleable.SectionHeaderView_headerIcon, 0));
            setBadge(a.getText(R.styleable.SectionHeaderView_headerBadge));
            setMeta(a.getText(R.styleable.SectionHeaderView_headerMeta),
                    a.getBoolean(R.styleable.SectionHeaderView_headerMetaPill, false));
            setActionText(a.getText(R.styleable.SectionHeaderView_headerActionText));
        } finally {
            a.recycle();
        }
    }

    public void setTitle(@Nullable CharSequence text) {
        title.setText(text);
    }

    /** E.g. {@code TextAppearance.BBetter.BodyStrong} for the smaller 15sp "Studying now" header. */
    public void setTitleAppearance(@StyleRes int appearance) {
        TextViewCompat.setTextAppearance(title, appearance);
    }

    public void setSubtitle(@Nullable CharSequence text) {
        WidgetUtils.setTextOrGone(subtitle, text);
    }

    /** Leading 20dp sage icon (html 14 lock); 0 hides it. */
    public void setIcon(@DrawableRes int res) {
        if (res == 0) {
            icon.setVisibility(GONE);
        } else {
            icon.setImageResource(res);
            icon.setVisibility(VISIBLE);
        }
    }

    /** Honey "NEW" pill after the title (uppercased); null hides it. */
    public void setBadge(@Nullable CharSequence text) {
        WidgetUtils.setTextOrGone(badge, text);
    }

    /** Trailing muted text ("8h 15m logged"); {@code pill} = inside a hairline pill ("5 / 8"). */
    public void setMeta(@Nullable CharSequence text, boolean pill) {
        WidgetUtils.setTextOrGone(meta, text);
        if (pill) {
            meta.setBackgroundResource(R.drawable.bg_meta_pill);
            int h = WidgetUtils.dp(getContext(), 10);
            int v = WidgetUtils.dp(getContext(), 2);
            meta.setPaddingRelative(h, v, h, v);
        } else {
            meta.setBackground(null);
            meta.setPaddingRelative(0, 0, 0, 0);
        }
    }

    public void setMeta(@Nullable CharSequence text) {
        setMeta(text, meta.getBackground() != null);
    }

    /** Trailing sage text button ("See all"); null hides it. Screens use meta OR action. */
    public void setActionText(@Nullable CharSequence text) {
        action.setText(text);
        action.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    public void setOnActionClickListener(@Nullable OnActionClickListener listener) {
        actionListener = listener;
    }
}
