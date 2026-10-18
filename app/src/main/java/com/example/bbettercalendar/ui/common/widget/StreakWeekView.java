package com.example.bbettercalendar.ui.common.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.stats.StreakCalculator;

import java.util.List;

/**
 * Weekly streak row (u9; html 04 streak card, DESIGN §4 "Streak calendar"; weekly since plan
 * racha-semanal-y-constancia). Paints {@link StreakCalculator.Result} directly:
 * {@link #setStreak(StreakCalculator.Result)}.
 *
 * <pre>
 *   (flame) 6 weeks              3/4 days  ← streakFlame="top" (default, html 04)
 *   (✓)(✓)( )(✓)( )( )( )   [flame/6]      ← streakFlame="end": tall honey pill
 *    M  T  W  T  F  S  S
 * </pre>
 *
 * The count is weeks in a row meeting the weekly goal; the right-hand text is this week's days vs
 * the goal (honey with a check once met). Day states ({@link StreakCalculator.DayState}): DONE =
 * honey disc + check (sage disc when it is today), PENDING = raised disc with a sage ring, REST =
 * quiet raised disc (a day off is not a failure), FUTURE = hairline only. Today's letter is bright
 * and bold. With {@code app:streakShowDates="true"} the not-done discs show the day of the month
 * (use a bigger {@code app:streakDotSize}, e.g. 28dp).
 */
public class StreakWeekView extends LinearLayout {

    public static final int FLAME_TOP = 0;
    public static final int FLAME_END = 1;
    public static final int FLAME_NONE = 2;

    private final TextView label;
    private final View header;
    private final TextView goalText;
    private final LinearLayout daysRow;
    private final View pill;
    private final TextView pillCount;
    private final View[] cells = new View[7];
    private final String[] letters;
    private final String[] dayNames;

    private final Typeface letterTypeface;
    private final Typeface letterTypefaceBold;

    private int flamePosition = FLAME_TOP;
    private boolean showDates;
    private int dotSize;
    private int streakWeeks;
    private int daysThisWeek;
    private int goal;
    @Nullable private List<StreakCalculator.WeekDay> week;

    public StreakWeekView(@NonNull Context context) {
        this(context, null);
    }

    public StreakWeekView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StreakWeekView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        inflate(context, R.layout.view_streak_week, this);
        label = findViewById(R.id.streakWeekLabel);
        header = findViewById(R.id.streakWeekHeader);
        goalText = findViewById(R.id.streakWeekGoal);
        daysRow = findViewById(R.id.streakWeekDays);
        pill = findViewById(R.id.streakWeekPill);
        pillCount = findViewById(R.id.streakWeekPillCount);
        letters = getResources().getStringArray(R.array.widget_streak_letters);
        dayNames = getResources().getStringArray(R.array.weekday_names);

        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < 7; i++) {
            cells[i] = inflater.inflate(R.layout.view_streak_day, daysRow, false);
            daysRow.addView(cells[i]);
        }
        letterTypeface = ((TextView) cells[0].findViewById(R.id.streakDayLetter)).getTypeface();
        letterTypefaceBold = Typeface.create(letterTypeface, Typeface.BOLD);

        dotSize = getResources().getDimensionPixelSize(R.dimen.widget_streak_dot_size);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StreakWeekView, defStyleAttr, 0);
        try {
            flamePosition = a.getInt(R.styleable.StreakWeekView_streakFlame, FLAME_TOP);
            showDates = a.getBoolean(R.styleable.StreakWeekView_streakShowDates, false);
            dotSize = a.getDimensionPixelSize(R.styleable.StreakWeekView_streakDotSize, dotSize);
        } finally {
            a.recycle();
        }
        render();
    }

    // ---- API ----

    /**
     * Count = {@code result.weekStreak}, goal text = {@code daysThisWeek/goal}, circles =
     * {@code result.week}. Null = empty week, 0, no goal text.
     */
    public void setStreak(@Nullable StreakCalculator.Result result) {
        streakWeeks = result != null ? result.weekStreak : 0;
        daysThisWeek = result != null ? result.daysThisWeek : 0;
        goal = result != null ? result.goal : 0;
        week = result != null ? result.week : null;
        render();
    }

    /** The 7 days, Monday first (u3 {@code Result.week} / {@code weekRow}). Null = all upcoming. */
    public void setWeek(@Nullable List<StreakCalculator.WeekDay> days) {
        week = days;
        render();
    }

    /** Weeks shown in the flame label / pill. */
    public void setStreakCount(int weeks) {
        streakWeeks = Math.max(0, weeks);
        render();
    }

    /** {@link #FLAME_TOP}, {@link #FLAME_END} or {@link #FLAME_NONE}. */
    public void setFlamePosition(int position) {
        flamePosition = position;
        render();
    }

    public void setShowDates(boolean show) {
        showDates = show;
        render();
    }

    public void setDotSize(int px) {
        dotSize = px;
        render();
    }

    // ---- rendering ----

    private void render() {
        String weeksText = getResources().getQuantityString(R.plurals.streak_weeks, streakWeeks, streakWeeks);
        label.setText(streakWeeks > 0 ? weeksText : getContext().getString(R.string.streak_label_none));
        header.setVisibility(flamePosition == FLAME_TOP ? VISIBLE : GONE);
        pill.setVisibility(flamePosition == FLAME_END ? VISIBLE : GONE);
        pillCount.setText(String.valueOf(streakWeeks));
        pill.setContentDescription(weeksText);
        renderGoal();

        boolean valid = week != null && week.size() == 7;
        for (int i = 0; i < 7; i++) {
            StreakCalculator.WeekDay day = valid ? week.get(i) : null;
            bindCell(cells[i], i, day);
        }
    }

    private void renderGoal() {
        if (goal <= 0) {
            goalText.setVisibility(GONE);
            return;
        }
        boolean met = daysThisWeek >= goal;
        goalText.setVisibility(VISIBLE);
        goalText.setText(met
                ? getContext().getString(R.string.streak_week_goal_met, daysThisWeek, goal)
                : getResources().getQuantityString(R.plurals.streak_week_goal, goal, daysThisWeek, goal));
        goalText.setTextColor(ContextCompat.getColor(getContext(),
                met ? R.color.bb_accent_reward : R.color.bb_on_surface_muted));
        goalText.setContentDescription(getContext().getString(R.string.streak_week_goal_desc,
                daysThisWeek, goal));
    }

    private void bindCell(@NonNull View cell, int index, @Nullable StreakCalculator.WeekDay day) {
        Context ctx = getContext();
        View dot = cell.findViewById(R.id.streakDayDot);
        ImageView check = cell.findViewById(R.id.streakDayCheck);
        TextView date = cell.findViewById(R.id.streakDayDate);
        TextView letter = cell.findViewById(R.id.streakDayLetter);

        ViewGroup.LayoutParams lp = dot.getLayoutParams();
        if (lp.width != dotSize) {
            lp.width = dotSize;
            lp.height = dotSize;
            dot.setLayoutParams(lp);
        }
        int pad = Math.round(dotSize * 0.18f);
        check.setPadding(pad, pad, pad, pad);

        StreakCalculator.DayState state = day != null ? day.state : StreakCalculator.DayState.FUTURE;
        boolean today = day != null && day.isToday;
        int bg;
        int stateText;
        switch (state) {
            case DONE:
                bg = today ? R.drawable.bg_streak_dot_done_today : R.drawable.bg_streak_dot_done;
                stateText = R.string.widget_streak_day_done;
                break;
            case PENDING:
                bg = R.drawable.bg_streak_dot_pending;
                stateText = R.string.widget_streak_day_pending;
                break;
            case REST:
                bg = R.drawable.bg_streak_dot_rest;
                stateText = R.string.streak_day_rest;
                break;
            case FUTURE:
            default:
                bg = R.drawable.bg_streak_dot_future;
                stateText = R.string.widget_streak_day_future;
                break;
        }
        dot.setBackgroundResource(bg);

        boolean done = state == StreakCalculator.DayState.DONE;
        check.setVisibility(done ? VISIBLE : GONE);
        // Text on honey / sage = bb_on_reward / bb_on_primary (both pine_950).
        ImageViewCompat.setImageTintList(check, ColorStateList.valueOf(ContextCompat.getColor(ctx,
                today ? R.color.bb_on_primary : R.color.bb_on_reward)));

        boolean dateShown = showDates && !done && day != null;
        date.setVisibility(dateShown ? VISIBLE : GONE);
        if (dateShown) {
            date.setText(String.valueOf(day.date.getDayOfMonth()));
            date.setTextColor(ContextCompat.getColor(ctx, today ? R.color.bb_on_surface : R.color.bb_on_surface_muted));
        }

        letter.setText(letters[index]);
        letter.setTextColor(ContextCompat.getColor(ctx, today ? R.color.bb_on_surface : R.color.bb_on_surface_muted));
        letter.setTypeface(today ? letterTypefaceBold : letterTypeface);

        cell.setContentDescription(ctx.getString(stateText, dayNames[index]));
    }
}
