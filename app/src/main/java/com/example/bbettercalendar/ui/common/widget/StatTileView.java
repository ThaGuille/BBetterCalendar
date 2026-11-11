package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.bbettercalendar.R;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Stat tile (u9; html 13 / 14 / 10 / 02): small label above a big number, then an optional bar,
 * delta chip and sub line.
 *
 * <pre>
 *   Focus time          label  (Caption, sentence case; wraps, never ellipsized)  [+ icon start/end]
 *   12h 40m             value  (Stat.Tile 22sp, tabular)        [+ unit, muted]
 *   ▁▁▁▁▁▁              bar    (optional, 0..100)
 *   ▲ 14%               delta  (optional; inset pill or inline)
 *   4.4 / day avg       sub    (optional; tone muted by default)
 * </pre>
 *
 * By default the tile paints its own Pine Card (20dp, {@code bg_card}) with 14dp padding; set
 * {@code app:tileCard="false"} to put several bare tiles inside one card (html 13).
 *
 * <p>mejora-visual u3: nothing is truncated (label wraps to two lines, sub to three, the value never
 * ellipsizes) and labels are no longer forced to uppercase ({@link #setLabelAllCaps} still opts in).
 * {@code app:tileWide="true"} / {@link #setWide} is the "wide cell" of mockup 10: an inset tile with
 * label, value and a wrapping context line (the sub).
 */
public class StatTileView extends LinearLayout {

    /** Direction of the delta arrow. */
    public enum Trend { NONE, UP, DOWN }

    private final ImageView iconStart;
    private final ImageView iconEnd;
    private final TextView label;
    private final TextView value;
    private final TextView unit;
    private final LinearProgressIndicator progress;
    private final TextView delta;
    private final TextView sub;

    private boolean labelAllCaps = false;
    private boolean deltaChip = true;
    private boolean wide;
    @DrawableRes private int iconRes;
    private boolean iconAtEnd;
    @ColorInt private int iconTint;

    public StatTileView(@NonNull Context context) {
        this(context, null);
    }

    public StatTileView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StatTileView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        inflate(context, R.layout.view_stat_tile, this);
        iconStart = findViewById(R.id.statTileIconStart);
        iconEnd = findViewById(R.id.statTileIconEnd);
        label = findViewById(R.id.statTileLabel);
        value = findViewById(R.id.statTileValue);
        unit = findViewById(R.id.statTileUnit);
        progress = findViewById(R.id.statTileProgress);
        delta = findViewById(R.id.statTileDelta);
        sub = findViewById(R.id.statTileSub);
        iconTint = ContextCompat.getColor(context, R.color.bb_on_surface_muted);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StatTileView, defStyleAttr, 0);
        try {
            setCardStyle(a.getBoolean(R.styleable.StatTileView_tileCard, true));
            labelAllCaps = a.getBoolean(R.styleable.StatTileView_tileLabelAllCaps, false);
            setLabel(a.getText(R.styleable.StatTileView_tileLabel));
            setValue(a.getText(R.styleable.StatTileView_tileValue), a.getText(R.styleable.StatTileView_tileUnit));
            setValueTone(WidgetTone.fromAttr(a.getInt(R.styleable.StatTileView_tileValueTone, 0), WidgetTone.NORMAL));
            setSub(a.getText(R.styleable.StatTileView_tileSub));
            setSubTone(WidgetTone.fromAttr(a.getInt(R.styleable.StatTileView_tileSubTone, 1), WidgetTone.MUTED));
            if (a.hasValue(R.styleable.StatTileView_tileIconTint)) {
                iconTint = a.getColor(R.styleable.StatTileView_tileIconTint, iconTint);
            }
            iconAtEnd = a.getInt(R.styleable.StatTileView_tileIconPosition, 0) == 1;
            setIcon(a.getResourceId(R.styleable.StatTileView_tileIcon, 0), iconAtEnd);
            deltaChip = a.getBoolean(R.styleable.StatTileView_tileDeltaChip, true);
            CharSequence deltaText = a.getText(R.styleable.StatTileView_tileDelta);
            int trend = a.getInt(R.styleable.StatTileView_tileDeltaTrend, 0);
            setDelta(deltaText, Trend.values()[Math.max(0, Math.min(trend, 2))],
                    WidgetTone.fromAttr(a.getInt(R.styleable.StatTileView_tileDeltaTone, 2), WidgetTone.PRIMARY));
            setProgress(a.getInt(R.styleable.StatTileView_tileProgress, -1));
            setWide(a.getBoolean(R.styleable.StatTileView_tileWide, false));
        } finally {
            a.recycle();
        }
    }

    // ---- API ----

    /** Small label above the number ("Focus time"). Sentence case unless {@link #setLabelAllCaps} says otherwise. */
    public void setLabel(@Nullable CharSequence text) {
        WidgetUtils.setTextOrGone(label, text);
        applyLabelCaps();
    }

    /** Opt-in uppercase with tracking (off by default since u3). */
    public void setLabelAllCaps(boolean allCaps) {
        labelAllCaps = allCaps;
        applyLabelCaps();
    }

    /** The big number. */
    public void setValue(@Nullable CharSequence text) {
        value.setText(text);
    }

    /** The big number plus a smaller muted unit after it ("42" + "m", "148.5" + "hrs"). */
    public void setValue(@Nullable CharSequence text, @Nullable CharSequence unitText) {
        value.setText(text);
        WidgetUtils.setTextOrGone(unit, unitText);
    }

    public void setValueTone(@NonNull WidgetTone tone) {
        value.setTextColor(tone.color(getContext()));
    }

    /** Line under everything ("4.4 / day avg", "Personal best"); null hides it. */
    public void setSub(@Nullable CharSequence text) {
        WidgetUtils.setTextOrGone(sub, text);
    }

    public void setSubTone(@NonNull WidgetTone tone) {
        sub.setTextColor(tone.color(getContext()));
    }

    /** Icon next to the label (html 10 shield at the end, html 02 icons at the start); 0 hides it. */
    public void setIcon(@DrawableRes int res, boolean atEnd) {
        iconRes = res;
        iconAtEnd = atEnd;
        ImageView shown = atEnd ? iconEnd : iconStart;
        ImageView hidden = atEnd ? iconStart : iconEnd;
        hidden.setVisibility(GONE);
        if (res == 0) {
            shown.setVisibility(GONE);
            return;
        }
        shown.setImageResource(res);
        ImageViewCompat.setImageTintList(shown, ColorStateList.valueOf(iconTint));
        shown.setVisibility(VISIBLE);
    }

    public void setIconTint(@ColorInt int color) {
        iconTint = color;
        setIcon(iconRes, iconAtEnd);
    }

    /**
     * Delta vs the previous period. {@code tone} = whether it is good news ({@link WidgetTone#PRIMARY})
     * or bad ({@link WidgetTone#DANGER}); the arrow follows {@code trend}. Null / empty text hides it.
     */
    public void setDelta(@Nullable CharSequence text, @NonNull Trend trend, @NonNull WidgetTone tone) {
        if (TextUtils.isEmpty(text)) {
            delta.setVisibility(GONE);
            return;
        }
        int color = tone.color(getContext());
        delta.setText(text);
        delta.setTextColor(color);
        int arrowRes = trend == Trend.UP ? R.drawable.ic_arrow_drop_up_24
                : trend == Trend.DOWN ? R.drawable.ic_arrow_drop_down_24 : 0;
        Drawable arrow = WidgetUtils.tinted(getContext(), arrowRes, color,
                getResources().getDimensionPixelSize(R.dimen.widget_delta_icon_size));
        delta.setCompoundDrawablesRelative(arrow, null, null, null);
        applyDeltaChip();
        delta.setVisibility(VISIBLE);
    }

    public void clearDelta() {
        delta.setVisibility(GONE);
    }

    /** true = delta inside an inset pill (html 13 / 10); false = inline bold text (html 14). */
    public void setDeltaChip(boolean chip) {
        deltaChip = chip;
        applyDeltaChip();
    }

    /** Thin sage bar under the value (html 14 "Daily limit"), 0..100; negative hides it. */
    public void setProgress(int percent) {
        if (percent < 0) {
            progress.setVisibility(GONE);
            return;
        }
        progress.setVisibility(VISIBLE);
        progress.setProgressCompat(Math.min(percent, 100), false);
    }

    /** Colour of the bar (default sage); e.g. reward / danger when a limit is close or passed. */
    public void setProgressColor(@ColorInt int color) {
        progress.setIndicatorColor(color);
    }

    /** true (default) = own Pine Card + padding; false = bare content for a shared card. */
    public void setCardStyle(boolean card) {
        if (wide) {
            return;
        }
        if (card) {
            setBackgroundResource(R.drawable.bg_card);
            int p = getResources().getDimensionPixelSize(R.dimen.widget_tile_padding);
            setPadding(p, p, p, p);
        } else {
            setBackground(null);
            setPadding(0, 0, 0, 0);
        }
    }

    /**
     * "Wide cell" (mockup 10, Sesiones / Tareas): an inset tile (own rounded fill, 14dp padding) whose context
     * line (the sub) may wrap to three lines. Replaces the card look; false restores it.
     */
    public void setWide(boolean wide) {
        this.wide = wide;
        if (wide) {
            setBackgroundResource(R.drawable.bg_stat_tile_wide);
            int p = getResources().getDimensionPixelSize(R.dimen.widget_tile_padding);
            setPadding(p, p, p, p);
            sub.setMaxLines(3);
        } else {
            sub.setMaxLines(2);
        }
    }

    public boolean isWide() {
        return wide;
    }

    // ---- internals ----

    private void applyLabelCaps() {
        label.setAllCaps(labelAllCaps);
        label.setLetterSpacing(labelAllCaps ? 0.06f : 0f);
    }

    private void applyDeltaChip() {
        if (deltaChip) {
            delta.setBackgroundResource(R.drawable.bg_widget_inset_pill);
            int h = WidgetUtils.dp(getContext(), 8);
            int v = WidgetUtils.dp(getContext(), 2);
            delta.setPaddingRelative(h - WidgetUtils.dp(getContext(), 3), v, h, v);
        } else {
            delta.setBackground(null);
            delta.setPaddingRelative(0, 0, 0, 0);
        }
    }
}
