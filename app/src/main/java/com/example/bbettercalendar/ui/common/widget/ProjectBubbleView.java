package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FontHelper;
import com.example.bbettercalendar.ui.common.motion.Motion;

/**
 * Project circle of the selector (rediseno-proyectos u3, spec 3.1 item 2, D-P05): a 42dp circle with a
 * progress ring in the project colour, the project icon over a 14 percent tint of that colour, and a badge
 * at the top right with the tasks due today plus the late ones (background bb_primary_bright, bb_danger
 * when any is late, hidden at 0). Selected = a halo in the project colour; {@link #setNewProject} = the
 * dashed circle with a plus that opens the new-project form. The name under the circle belongs to the screen.
 *
 * <p>Drawn by hand like the rest of the chart kit: the shared {@link RingPainter} only knows the amber and
 * sage palettes, and a project ring needs any project colour. The view is 56dp so the halo and the badge
 * fit; the circle is 42dp (see dimens {@code widget_bubble_*}).
 */
public class ProjectBubbleView extends View {

    private static final float TINT_ALPHA = 0.14f;
    private static final float TRACK_ALPHA = 0.30f;
    private static final float HALO_ALPHA = 0.28f;
    private static final float START_ANGLE = -90f;
    /** Largest count the badge writes; more shows "9+". */
    public static final int BADGE_MAX = 9;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float circle;
    private final float stroke;
    private final float halo;
    private final float badgeRadius;
    private final float iconSize;
    @ColorInt private final int badgeNormal;
    @ColorInt private final int badgeLate;
    @ColorInt private final int muted;
    @Nullable private final Drawable plus;

    @ColorInt private int accent;
    @Nullable private Drawable icon;
    private float progress;
    private int count;
    private boolean late;
    private boolean selected;
    private boolean isNew;
    @Nullable private String projectName;

    private float cx;
    private float cy;

    public ProjectBubbleView(@NonNull Context context) {
        this(context, null);
    }

    public ProjectBubbleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProjectBubbleView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        circle = getResources().getDimension(R.dimen.widget_bubble_size);
        stroke = getResources().getDimension(R.dimen.widget_bubble_ring_stroke);
        halo = getResources().getDimension(R.dimen.widget_bubble_halo);
        badgeRadius = getResources().getDimension(R.dimen.widget_bubble_badge) / 2f;
        iconSize = getResources().getDimension(R.dimen.widget_bubble_icon);
        badgeNormal = ContextCompat.getColor(context, R.color.bb_primary_bright);
        badgeLate = ContextCompat.getColor(context, R.color.bb_danger);
        muted = ContextCompat.getColor(context, R.color.bb_on_surface_muted);
        accent = ContextCompat.getColor(context, R.color.bb_project_1);
        Drawable p = ContextCompat.getDrawable(context, R.drawable.ic_add_24);
        plus = p == null ? null : DrawableCompat.wrap(p.mutate());
        if (plus != null) {
            DrawableCompat.setTint(plus, muted);
        }

        fillPaint.setStyle(Paint.Style.FILL);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        haloPaint.setStyle(Paint.Style.FILL);
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(stroke);
        dashPaint.setColor(muted);
        float dp = getResources().getDisplayMetrics().density;
        dashPaint.setPathEffect(new DashPathEffect(new float[]{4f * dp, 3f * dp}, 0f));
        badgePaint.setStyle(Paint.Style.FILL);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        badgeTextPaint.setTypeface(FontHelper.appTypeface(context, 700));
        badgeTextPaint.setTextSize(getResources().getDimension(R.dimen.widget_bubble_badge_text));
        badgeTextPaint.setColor(ContextCompat.getColor(context, R.color.bb_on_primary));
        applyAccent();

        setClickable(true);
        setFocusable(true);
        Motion.pressable(this);
        updateDescription();
    }

    // ------------------------------------------------------------------ API

    /** Project colour (a resolved colour, e.g. from {@link ProjectAccents#colorResFor}). */
    public void setAccentColor(@ColorInt int color) {
        accent = color;
        applyAccent();
        applyIconTint();
        invalidate();
    }

    public void setIconRes(@DrawableRes int res) {
        Drawable d = ContextCompat.getDrawable(getContext(), res);
        icon = d == null ? null : DrawableCompat.wrap(d.mutate());
        applyIconTint();
        invalidate();
    }

    /** Done over total tasks, 0..1; the ring in the project colour. */
    public void setProgress(float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        if (f != progress) {
            progress = f;
            invalidate();
        }
    }

    public float getProgress() {
        return progress;
    }

    /** Badge: tasks due today plus the late ones; {@code late} paints it bb_danger. 0 hides it. */
    public void setBadge(int count, boolean late) {
        this.count = Math.max(0, count);
        this.late = late && this.count > 0;
        updateDescription();
        invalidate();
    }

    public int getBadgeCount() {
        return count;
    }

    public boolean isBadgeLate() {
        return late;
    }

    public boolean isBadgeVisible() {
        return count > 0 && !isNew;
    }

    /** Text the badge draws: the count, or "9+" above {@link #BADGE_MAX}. */
    @NonNull
    public static String badgeText(int count) {
        return count > BADGE_MAX ? BADGE_MAX + "+" : String.valueOf(count);
    }

    /** Halo in the project colour around the circle. */
    @Override
    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            invalidate();
        }
        super.setSelected(selected);
    }

    /** The dashed «new project» variant: dashed circle and a plus; no ring, icon or badge. */
    public void setNewProject(boolean isNew) {
        if (this.isNew != isNew) {
            this.isNew = isNew;
            updateDescription();
            invalidate();
        }
    }

    public boolean isNewProject() {
        return isNew;
    }

    /** Name of the project, used by the content description ("Name, 3 tasks due"). */
    public void setProjectName(@Nullable String name) {
        projectName = name;
        updateDescription();
    }

    // ------------------------------------------------------------------ look

    private void applyAccent() {
        fillPaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(255 * TINT_ALPHA)));
        trackPaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(255 * TRACK_ALPHA)));
        arcPaint.setColor(accent);
        haloPaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(255 * HALO_ALPHA)));
    }

    private void applyIconTint() {
        if (icon != null) {
            DrawableCompat.setTint(icon, accent);
        }
    }

    private void updateDescription() {
        Context c = getContext();
        String text;
        if (isNew) {
            text = c.getString(R.string.widget_bubble_new);
        } else {
            String name = projectName == null ? "" : projectName;
            if (count > 0) {
                text = c.getResources().getQuantityString(late
                        ? R.plurals.widget_bubble_desc_late : R.plurals.widget_bubble_desc, count, name, count);
            } else {
                text = name;
            }
        }
        setContentDescription(text);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int side = getResources().getDimensionPixelSize(R.dimen.widget_bubble_view);
        setMeasuredDimension(resolveSize(side + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(side + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = getPaddingLeft() + (w - getPaddingLeft() - getPaddingRight()) / 2f;
        cy = getPaddingTop() + (h - getPaddingTop() - getPaddingBottom()) / 2f;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float r = circle / 2f;
        if (isNew) {
            float inset = stroke / 2f;
            canvas.drawCircle(cx, cy, r - inset, dashPaint);
            if (plus != null) {
                int half = Math.round(iconSize / 2f);
                plus.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
                plus.draw(canvas);
            }
            return;
        }
        if (selected) {
            canvas.drawCircle(cx, cy, r + halo, haloPaint);
        }
        canvas.drawCircle(cx, cy, r, fillPaint);
        float ringR = r - stroke / 2f;
        oval.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        canvas.drawCircle(cx, cy, ringR, trackPaint);
        if (progress > 0f) {
            canvas.drawArc(oval, START_ANGLE, 360f * progress, false, arcPaint);
        }
        if (icon != null) {
            int half = Math.round(iconSize / 2f);
            icon.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
            icon.draw(canvas);
        }
        if (count > 0) {
            float bx = cx + r * 0.72f;
            float by = cy - r * 0.72f;
            badgePaint.setColor(late ? badgeLate : badgeNormal);
            canvas.drawCircle(bx, by, badgeRadius, badgePaint);
            Paint.FontMetrics fm = badgeTextPaint.getFontMetrics();
            canvas.drawText(badgeText(count), bx, by - (fm.ascent + fm.descent) / 2f, badgeTextPaint);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Button.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setSelected(selected);
    }
}
