package com.example.bbettercalendar.ui.calendar.binders;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.calendar.domain.CalendarHeatLevel;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.view.ViewContainer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MonthDayBinder implements com.kizitonwose.calendar.view.MonthDayBinder<MonthDayBinder.DayContainer> {

    public interface OnDayClickListener {
        void onDayClick(LocalDate date);
    }

    private Map<LocalDate, List<CalendarItem>> itemsByDate = Collections.emptyMap();
    private Map<LocalDate, Integer> focusMinutesByDate = Collections.emptyMap();
    private LocalDate selectedDate;
    private OnDayClickListener listener;

    public void setItemsByDate(@NonNull Map<LocalDate, List<CalendarItem>> itemsByDate) {
        this.itemsByDate = itemsByDate;
    }

    public void setFocusMinutesByDate(@NonNull Map<LocalDate, Integer> focusMinutesByDate) {
        this.focusMinutesByDate = focusMinutesByDate;
    }

    public void setSelectedDate(LocalDate date) {
        this.selectedDate = date;
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public DayContainer create(@NonNull View view) {
        DayContainer container = new DayContainer(view);
        view.setOnClickListener(v -> {
            CalendarDay day = container.currentDay;
            if (day != null
                    && day.getPosition() == DayPosition.MonthDate
                    && listener != null) {
                listener.onDayClick(day.getDate());
            }
        });
        return container;
    }

    @Override
    public void bind(@NonNull DayContainer container, CalendarDay day) {
        container.currentDay = day;
        container.dayText.setText(String.valueOf(day.getDate().getDayOfMonth()));

        boolean inMonth = day.getPosition() == DayPosition.MonthDate;
        container.dayText.setAlpha(inMonth ? 1.0f : 0.3f);

        boolean isToday = inMonth && day.getDate().equals(LocalDate.now());
        container.todayIndicator.setVisibility(isToday ? View.VISIBLE : View.GONE);

        Context ctx = container.getView().getContext();
        boolean isSelected = inMonth && selectedDate != null && day.getDate().equals(selectedDate);

        // Fondo: relleno salvia si está seleccionado; si no, tinte por minutos de enfoque.
        Integer minutes = inMonth ? focusMinutesByDate.get(day.getDate()) : null;
        int level = CalendarHeatLevel.levelFor(minutes == null ? 0 : minutes);
        if (isSelected) {
            container.focusBg.setBackgroundResource(R.drawable.bg_calendar_day_selected);
        } else if (level > 0) {
            container.focusBg.setBackgroundResource(FOCUS_BG[level]);
        } else {
            container.focusBg.setBackground(null);
        }
        container.dayText.setTextColor(ContextCompat.getColor(ctx,
                isSelected ? R.color.bb_on_primary : R.color.bb_on_surface));

        // Hasta 3 puntos, uno por tipo distinto presente ese día.
        List<CalendarItem> items = inMonth ? itemsByDate.get(day.getDate()) : null;
        List<CalendarItem.Type> types = dotTypes(items);
        for (int i = 0; i < container.dots.length; i++) {
            View dot = container.dots[i];
            if (i < types.size()) {
                dot.setVisibility(View.VISIBLE);
                int color = isSelected
                        ? ContextCompat.getColor(ctx, R.color.bb_on_primary)
                        : ContextCompat.getColor(ctx, colorFor(types.get(i)));
                ViewCompat.setBackgroundTintList(dot, ColorStateList.valueOf(color));
            } else {
                dot.setVisibility(View.GONE);
            }
        }
    }

    private static final int[] FOCUS_BG = {
            0, R.drawable.bg_calendar_focus_1, R.drawable.bg_calendar_focus_2, R.drawable.bg_calendar_focus_3
    };
    static final int MAX_DOTS = 3;

    /** Tipos distintos presentes, en orden fijo tarea, evento, entrega, recordatorio; máx. 3. */
    static List<CalendarItem.Type> dotTypes(List<CalendarItem> items) {
        List<CalendarItem.Type> out = new ArrayList<>();
        if (items == null) return out;
        CalendarItem.Type[] order = {CalendarItem.Type.TASK, CalendarItem.Type.EVENT,
                CalendarItem.Type.DEADLINE, CalendarItem.Type.REMINDER};
        for (CalendarItem.Type t : order) {
            if (out.size() >= MAX_DOTS) break;
            for (CalendarItem item : items) {
                if (item.getType() == t) {
                    out.add(t);
                    break;
                }
            }
        }
        return out;
    }

    private static int colorFor(CalendarItem.Type type) {
        switch (type) {
            case TASK: return R.color.calendar_item_task;
            case DEADLINE: return R.color.calendar_item_deadline;
            case REMINDER: return R.color.calendar_item_reminder;
            case EVENT:
            default: return R.color.calendar_item_event;
        }
    }

    public static class DayContainer extends ViewContainer {
        final TextView dayText;
        final View todayIndicator;
        final View focusBg;
        final View[] dots;
        CalendarDay currentDay;

        public DayContainer(@NonNull View view) {
            super(view);
            this.dayText = view.findViewById(R.id.cellDayText);
            this.todayIndicator = view.findViewById(R.id.cellTodayIndicator);
            this.focusBg = view.findViewById(R.id.cellFocusBg);
            this.dots = new View[]{
                    view.findViewById(R.id.cellDot1),
                    view.findViewById(R.id.cellDot2),
                    view.findViewById(R.id.cellDot3)
            };
        }
    }
}
