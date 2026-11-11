package com.example.bbettercalendar.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.stats.StreakCalculator;

import java.util.Collections;
import java.util.List;

/**
 * The 7-day strip of Home's streak card (mejora-visual u6, M/40-home.png): one pill per day, Monday
 * first. Done = sage pill, today still pending = sage outline, rest and upcoming days = raised pill.
 * Pure drawing; the card around it carries the text and the content description.
 */
public class WeekPillStripView extends View {

    private static final int DAYS = 7;

    private final Paint donePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint restPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float gap;
    private final int defaultHeight;

    @NonNull private List<StreakCalculator.WeekDay> week = Collections.emptyList();

    public WeekPillStripView(@NonNull Context context) {
        this(context, null);
    }

    public WeekPillStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gap = getResources().getDimension(R.dimen.home_streak_strip_gap);
        defaultHeight = getResources().getDimensionPixelSize(R.dimen.home_streak_strip_height);
        donePaint.setStyle(Paint.Style.FILL);
        donePaint.setColor(ContextCompat.getColor(context, R.color.bb_primary));
        todayPaint.setStyle(Paint.Style.STROKE);
        todayPaint.setStrokeWidth(getResources().getDimension(R.dimen.home_streak_strip_stroke));
        todayPaint.setColor(ContextCompat.getColor(context, R.color.bb_primary));
        restPaint.setStyle(Paint.Style.FILL);
        restPaint.setColor(ContextCompat.getColor(context, R.color.bb_surface_raised));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The week of {@link StreakCalculator.Result#week}; null or not 7 days = seven empty pills. */
    public void setWeek(@Nullable List<StreakCalculator.WeekDay> days) {
        week = days != null && days.size() == DAYS ? days : Collections.<StreakCalculator.WeekDay>emptyList();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(defaultHeight + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float pill = (width - gap * (DAYS - 1)) / DAYS;
        if (pill <= 0f || height <= 0f) {
            return;
        }
        float radius = height / 2f;
        float inset = todayPaint.getStrokeWidth() / 2f;
        for (int i = 0; i < DAYS; i++) {
            float x = left + i * (pill + gap);
            rect.set(x, top, x + pill, top + height);
            StreakCalculator.DayState state = week.isEmpty()
                    ? StreakCalculator.DayState.FUTURE : week.get(i).state;
            if (state == StreakCalculator.DayState.DONE) {
                canvas.drawRoundRect(rect, radius, radius, donePaint);
            } else if (state == StreakCalculator.DayState.PENDING) {
                rect.inset(inset, inset);
                canvas.drawRoundRect(rect, radius - inset, radius - inset, todayPaint);
            } else {
                canvas.drawRoundRect(rect, radius, radius, restPaint);
            }
        }
    }
}
