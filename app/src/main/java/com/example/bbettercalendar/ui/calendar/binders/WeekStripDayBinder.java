package com.example.bbettercalendar.ui.calendar.binders;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.calendar.domain.CalendarHeatLevel;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;
import com.kizitonwose.calendar.core.WeekDay;
import com.kizitonwose.calendar.view.ViewContainer;
import com.kizitonwose.calendar.view.WeekDayBinder;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Día de la tira semanal (vista Semana, rediseno-calendario-color): letra, número (hoy = círculo
 * relleno), barrita de enfoque y marca. La barrita solo se ve con la capa de enfoque encendida y su
 * alto se mide contra una cifra fija ({@link CalendarHeatLevel#LEVEL_3_MIN} minutos = llena), no
 * contra el mejor día: un día muy largo no encoge a los demás.
 */
public class WeekStripDayBinder implements WeekDayBinder<WeekStripDayBinder.DayContainer> {

    private Map<LocalDate, List<CalendarItem>> itemsByDate = Collections.emptyMap();
    private Map<LocalDate, Integer> focusMinutesByDate = Collections.emptyMap();
    private boolean focusLayerVisible;
    private LocalDate selectedDate;
    private MonthDayBinder.OnDayClickListener listener;

    public void setItemsByDate(@NonNull Map<LocalDate, List<CalendarItem>> itemsByDate) {
        this.itemsByDate = itemsByDate;
    }

    public void setFocusMinutesByDate(@NonNull Map<LocalDate, Integer> focusMinutesByDate) {
        this.focusMinutesByDate = focusMinutesByDate;
    }

    public void setFocusLayerVisible(boolean visible) {
        this.focusLayerVisible = visible;
    }

    public void setSelectedDate(LocalDate date) {
        this.selectedDate = date;
    }

    public void setOnDayClickListener(MonthDayBinder.OnDayClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public DayContainer create(@NonNull View view) {
        DayContainer container = new DayContainer(view);
        view.setOnClickListener(v -> {
            if (container.date != null && listener != null) listener.onDayClick(container.date);
        });
        return container;
    }

    @Override
    public void bind(@NonNull DayContainer container, WeekDay day) {
        LocalDate date = day.getDate();
        container.date = date;
        Context ctx = container.getView().getContext();
        boolean isToday = date.equals(LocalDate.now());
        boolean isSelected = date.equals(selectedDate);

        container.letter.setText(date.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.getDefault()));
        container.dayText.setText(String.valueOf(date.getDayOfMonth()));
        container.todayIndicator.setVisibility(isToday ? View.VISIBLE : View.GONE);
        container.dayText.setTextColor(ContextCompat.getColor(ctx,
                isToday ? R.color.bb_cal_on_today : R.color.bb_on_surface));
        if (isSelected) {
            container.getView().setBackgroundResource(R.drawable.bg_calendar_week_day_selected);
        } else {
            container.getView().setBackground(null);
        }

        Integer minutes = focusMinutesByDate.get(date);
        int focus = minutes == null ? 0 : minutes;
        container.barBox.setVisibility(focusLayerVisible ? View.VISIBLE : View.GONE);
        container.bar.setVisibility(focus > 0 ? View.VISIBLE : View.INVISIBLE);
        ViewGroup.LayoutParams lp = container.bar.getLayoutParams();
        lp.height = barHeightPx(ctx, focus);
        container.bar.setLayoutParams(lp);

        List<CalendarItem> items = itemsByDate.get(date);
        boolean deadline = MonthDayBinder.hasDeadline(items);
        int others = MonthDayBinder.dotCount(items);
        container.flag.setVisibility(deadline ? View.VISIBLE : View.GONE);
        container.dot.setVisibility(others > 0 ? View.VISIBLE : View.GONE);

        container.getView().setContentDescription(describe(ctx, date, isToday, isSelected, items,
                focusLayerVisible ? focus : 0));
    }

    private static int barHeightPx(Context ctx, int focusMinutes) {
        int min = ctx.getResources().getDimensionPixelSize(R.dimen.calendar_week_bar_min);
        int max = ctx.getResources().getDimensionPixelSize(R.dimen.calendar_week_bar_box);
        return min + Math.round((max - min) * barFraction(focusMinutes));
    }

    /** 0..1: fracción de la barrita para esos minutos; llena a partir de LEVEL_3_MIN. */
    static float barFraction(int focusMinutes) {
        if (focusMinutes <= 0) return 0f;
        return Math.min(1f, focusMinutes / (float) CalendarHeatLevel.LEVEL_3_MIN);
    }

    private static String describe(Context ctx, LocalDate date, boolean today, boolean selected,
                                   List<CalendarItem> items, int focusMinutes) {
        StringBuilder sb = new StringBuilder(
                date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()));
        sb.append(' ').append(date.getDayOfMonth());
        if (today) sb.append(", ").append(ctx.getString(R.string.calendar_cell_today));
        if (selected) sb.append(", ").append(ctx.getString(R.string.calendar_cell_selected));
        int count = items == null ? 0 : items.size();
        if (count > 0) {
            sb.append(", ").append(ctx.getResources()
                    .getQuantityString(R.plurals.calendar_agenda_count, count, count));
        }
        if (MonthDayBinder.hasDeadline(items)) {
            sb.append(", ").append(ctx.getString(R.string.calendar_legend_deadline));
        }
        if (focusMinutes > 0) {
            sb.append(", ").append(ctx.getString(R.string.calendar_week_focus,
                    DayDetailAdapter.formatDuration(ctx, focusMinutes)));
        }
        return sb.toString();
    }

    public static class DayContainer extends ViewContainer {
        final TextView letter;
        final TextView dayText;
        final View todayIndicator;
        final View barBox;
        final View bar;
        final View flag;
        final View dot;
        LocalDate date;

        public DayContainer(@NonNull View view) {
            super(view);
            letter = view.findViewById(R.id.weekCellLetter);
            dayText = view.findViewById(R.id.weekCellDay);
            todayIndicator = view.findViewById(R.id.weekCellToday);
            barBox = view.findViewById(R.id.weekCellBarBox);
            bar = view.findViewById(R.id.weekCellBar);
            flag = view.findViewById(R.id.weekCellFlag);
            dot = view.findViewById(R.id.weekCellDot);
        }
    }
}
