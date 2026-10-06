package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FontHelper;

import java.util.Locale;

/**
 * Friend / user avatar (u9; DESIGN §4 "Avatars", html 01 "Studying now" grid, html 04 header).
 * Drawn on a canvas (no layout), default 40dp ({@code app:avatarSize}).
 *
 * <ul>
 *   <li>{@link Status#NONE}: Forest disc with a hairline, Mist initials (user avatar, html 04).</li>
 *   <li>{@link Status#STUDYING}: 2dp Sage ring, 2dp Forest gap, raised disc, Mist initials.</li>
 *   <li>{@link Status#AWAY}: hairline outline on a dark disc, muted initials, whole view at 40 %.</li>
 * </ul>
 * Optional presence dot (sage, bottom-end, cut out with {@code app:avatarCutoutColor} = the
 * background behind the avatar) and a honey badge with an icon (top-end, html 01 crown).
 * Initials: {@link #setName} derives them (first letters of the first two words), or
 * {@link #setInitials} sets them explicitly.
 */
public class AvatarView extends View {

    public enum Status { NONE, STUDYING, AWAY }

    private static final float AWAY_ALPHA = 0.4f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface regular;
    private final Typeface bold;

    private final int defaultSize;
    private final float ring;
    private final float hairline;
    @ColorInt private int cutoutColor;

    @Nullable private CharSequence name;
    @NonNull private String initials = "";
    @NonNull private Status status = Status.NONE;
    private boolean presenceDot;
    @Nullable private Drawable badgeIcon;

    public AvatarView(@NonNull Context context) {
        this(context, null);
    }

    public AvatarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AvatarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ring = getResources().getDimension(R.dimen.widget_avatar_ring);
        hairline = getResources().getDimension(R.dimen.widget_hairline);
        regular = FontHelper.appTypefaceOrDefault(context);
        bold = Typeface.create(regular, Typeface.BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        cutoutColor = ContextCompat.getColor(context, R.color.bb_surface_card);

        int size = getResources().getDimensionPixelSize(R.dimen.widget_avatar_size);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AvatarView, defStyleAttr, 0);
        try {
            size = a.getDimensionPixelSize(R.styleable.AvatarView_avatarSize, size);
            cutoutColor = a.getColor(R.styleable.AvatarView_avatarCutoutColor, cutoutColor);
            presenceDot = a.getBoolean(R.styleable.AvatarView_avatarPresenceDot, false);
            CharSequence n = a.getText(R.styleable.AvatarView_avatarName);
            if (n != null) {
                setName(n);
            }
            CharSequence i = a.getText(R.styleable.AvatarView_avatarInitials);
            if (i != null) {
                setInitials(i.toString());
            }
            int s = a.getInt(R.styleable.AvatarView_avatarStatus, 0);
            setStatus(Status.values()[Math.max(0, Math.min(s, 2))]);
            setBadgeIcon(a.getResourceId(R.styleable.AvatarView_avatarBadgeIcon, 0));
        } finally {
            a.recycle();
        }
        defaultSize = size;
    }

    // ---- API ----

    /** Display name: derives the initials and the content description ("Laia, studying now"). */
    public void setName(@Nullable CharSequence displayName) {
        name = displayName;
        initials = initialsOf(displayName);
        updateDescription();
        invalidate();
    }

    /** Explicit initials (1–2 letters shown, uppercased). */
    public void setInitials(@Nullable String value) {
        initials = value == null ? "" : value.trim().toUpperCase(Locale.getDefault());
        if (initials.length() > 2) {
            initials = initials.substring(0, 2);
        }
        invalidate();
    }

    public void setStatus(@NonNull Status newStatus) {
        status = newStatus;
        setAlpha(newStatus == Status.AWAY ? AWAY_ALPHA : 1f);
        updateDescription();
        invalidate();
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    /** Small sage "online" dot at the bottom-end (html 04 header avatar). */
    public void setShowPresenceDot(boolean show) {
        presenceDot = show;
        invalidate();
    }

    /** Honey badge at the top-end with this icon (html 01 "top earner" crown); 0 removes it. */
    public void setBadgeIcon(@DrawableRes int res) {
        badgeIcon = res == 0 ? null : WidgetUtils.tinted(getContext(), res,
                ContextCompat.getColor(getContext(), R.color.bb_on_reward), 1);
        invalidate();
    }

    /** Colour of the background the avatar sits on (cut-out around the dot / badge). */
    public void setCutoutColor(@ColorInt int color) {
        cutoutColor = color;
        invalidate();
    }

    // ---- drawing ----

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(defaultSize + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
        int h = resolveSize(defaultSize + getPaddingTop() + getPaddingBottom(), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float contentW = getWidth() - getPaddingLeft() - getPaddingRight();
        float contentH = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(contentW, contentH);
        float cx = getPaddingLeft() + contentW / 2f;
        float cy = getPaddingTop() + contentH / 2f;
        float r = size / 2f;
        Context ctx = getContext();

        @ColorInt int initialsColor;
        switch (status) {
            case STUDYING:
                stroke.setStrokeWidth(ring);
                stroke.setColor(ContextCompat.getColor(ctx, R.color.bb_primary));
                fill.setColor(ContextCompat.getColor(ctx, R.color.bb_primary_container));
                canvas.drawCircle(cx, cy, r - ring, fill);
                canvas.drawCircle(cx, cy, r - ring / 2f, stroke);
                fill.setColor(ContextCompat.getColor(ctx, R.color.bb_surface_raised));
                canvas.drawCircle(cx, cy, r - ring * 2f, fill);
                initialsColor = ContextCompat.getColor(ctx, R.color.bb_on_surface);
                text.setTypeface(bold);
                break;
            case AWAY:
                fill.setColor(ContextCompat.getColor(ctx, R.color.bb_surface));
                canvas.drawCircle(cx, cy, r - hairline, fill);
                stroke.setStrokeWidth(hairline);
                stroke.setColor(ContextCompat.getColor(ctx, R.color.bb_outline));
                canvas.drawCircle(cx, cy, r - hairline / 2f, stroke);
                initialsColor = ContextCompat.getColor(ctx, R.color.bb_on_surface_muted);
                text.setTypeface(regular);
                break;
            case NONE:
            default:
                fill.setColor(ContextCompat.getColor(ctx, R.color.bb_primary_container));
                canvas.drawCircle(cx, cy, r - hairline, fill);
                stroke.setStrokeWidth(hairline);
                stroke.setColor(ContextCompat.getColor(ctx, R.color.bb_outline));
                canvas.drawCircle(cx, cy, r - hairline / 2f, stroke);
                initialsColor = ContextCompat.getColor(ctx, R.color.bb_on_primary_container);
                text.setTypeface(bold);
                break;
        }

        if (!initials.isEmpty()) {
            text.setColor(initialsColor);
            text.setTextSize(size * (initials.length() > 1 ? 0.28f : 0.36f));
            float baseline = cy - (text.descent() + text.ascent()) / 2f;
            canvas.drawText(initials, cx, baseline, text);
        }

        if (presenceDot) {
            float dotR = size * 0.125f;
            // html: absolute bottom-0 right-0 -> inside the bounding square's corner.
            float dx = cx + r - dotR - hairline * 1.5f;
            float dy = cy + r - dotR - hairline * 1.5f;
            fill.setColor(cutoutColor);
            canvas.drawCircle(dx, dy, dotR + hairline * 1.5f, fill);
            fill.setColor(ContextCompat.getColor(ctx, R.color.bb_primary));
            canvas.drawCircle(dx, dy, dotR, fill);
        }

        if (badgeIcon != null) {
            float badgeR = size * 0.2f;
            float bx = cx + r - badgeR - hairline * 1.5f;
            float by = cy - r + badgeR + hairline * 1.5f;
            fill.setColor(cutoutColor);
            canvas.drawCircle(bx, by, badgeR + hairline * 1.5f, fill);
            fill.setColor(ContextCompat.getColor(ctx, R.color.bb_accent_reward));
            canvas.drawCircle(bx, by, badgeR, fill);
            int half = Math.round(badgeR * 0.65f);
            badgeIcon.setBounds(Math.round(bx) - half, Math.round(by) - half,
                    Math.round(bx) + half, Math.round(by) + half);
            badgeIcon.draw(canvas);
        }
    }

    // ---- helpers ----

    private void updateDescription() {
        if (TextUtils.isEmpty(name)) {
            setContentDescription(null);
            return;
        }
        switch (status) {
            case STUDYING:
                setContentDescription(getContext().getString(R.string.widget_avatar_studying, name));
                break;
            case AWAY:
                setContentDescription(getContext().getString(R.string.widget_avatar_away, name));
                break;
            default:
                setContentDescription(name);
                break;
        }
    }

    @NonNull
    static String initialsOf(@Nullable CharSequence displayName) {
        if (displayName == null) {
            return "";
        }
        String[] words = displayName.toString().trim().split("\\s+");
        StringBuilder sb = new StringBuilder(2);
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.appendCodePoint(w.codePointAt(0));
                if (sb.length() >= 2) {
                    break;
                }
            }
        }
        return sb.toString().toUpperCase(Locale.getDefault());
    }
}
