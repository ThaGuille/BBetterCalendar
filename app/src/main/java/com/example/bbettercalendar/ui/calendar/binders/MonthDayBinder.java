package com.example.bbettercalendar.ui.calendar.binders;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.view.ViewContainer;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MonthDayBinder implements com.kizitonwose.calendar.view.MonthDayBinder<MonthDayBinder.DayContainer> {

    public interface OnDayClickListener {
        void onDayClick(LocalDate date);
    }

    private Map<LocalDate, List<CalendarItem>> itemsByDate = Collections.emptyMap();
    private LocalDate selectedDate;
    private OnDayClickListener listener;

    public void setItemsByDate(@NonNull Map<LocalDate, List<CalendarItem>> itemsByDate) {
        this.itemsByDate = itemsByDate;
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

        List<CalendarItem> items = inMonth ? itemsByDate.get(day.getDate()) : null;
        for (int i = 0; i < container.eventBars.length; i++) {
            View bar = container.eventBars[i];
            if (items != null && i < items.size()) {
                bar.setVisibility(View.VISIBLE);
                bar.setBackgroundColor(items.get(i).getColorArgb());
            } else {
                bar.setVisibility(View.GONE);
            }
        }

        boolean isSelected = inMonth && selectedDate != null && day.getDate().equals(selectedDate);
        container.getView().setSelected(isSelected);
    }

    public static class DayContainer extends ViewContainer {
        final TextView dayText;
        final View[] eventBars;
        CalendarDay currentDay;

        public DayContainer(@NonNull View view) {
            super(view);
            this.dayText = view.findViewById(R.id.cellDayText);
            this.eventBars = new View[]{
                    view.findViewById(R.id.cellEventBar1),
                    view.findViewById(R.id.cellEventBar2),
                    view.findViewById(R.id.cellEventBar3)
            };
        }
    }
}
