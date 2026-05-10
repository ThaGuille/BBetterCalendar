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

    private Map<LocalDate, List<CalendarItem>> itemsByDate = Collections.emptyMap();

    public void setItemsByDate(@NonNull Map<LocalDate, List<CalendarItem>> itemsByDate) {
        this.itemsByDate = itemsByDate;
    }

    @NonNull
    @Override
    public DayContainer create(@NonNull View view) {
        return new DayContainer(view);
    }

    @Override
    public void bind(@NonNull DayContainer container, CalendarDay day) {
        container.bind(day, itemsByDate.get(day.getDate()));
    }

    public static class DayContainer extends ViewContainer {
        private final TextView dayText;
        private final View eventBar;

        public DayContainer(@NonNull View view) {
            super(view);
            this.dayText = view.findViewById(R.id.cellDayText);
            this.eventBar = view.findViewById(R.id.cellEventBar);
        }

        public void bind(CalendarDay day, List<CalendarItem> items) {
            dayText.setText(String.valueOf(day.getDate().getDayOfMonth()));
            dayText.setAlpha(day.getPosition() == DayPosition.MonthDate ? 1.0f : 0.3f);

            if (items != null && !items.isEmpty() && day.getPosition() == DayPosition.MonthDate) {
                eventBar.setVisibility(View.VISIBLE);
                eventBar.setBackgroundColor(items.get(0).getColorArgb());
            } else {
                eventBar.setVisibility(View.GONE);
            }
        }
    }
}
