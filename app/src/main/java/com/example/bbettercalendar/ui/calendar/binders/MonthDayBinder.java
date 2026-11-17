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
import com.example.bbettercalendar.ui.calendar.domain.DayCellStyle;
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
        boolean isSelected = inMonth && selectedDate != null && day.getDate().equals(selectedDate);
        Integer minutes = inMonth ? focusMinutesByDate.get(day.getDate()) : null;
        int level = CalendarHeatLevel.levelFor(minutes == null ? 0 : minutes);

        // u5: canales separados. Fondo = tinte de enfoque (se mantiene al seleccionar); hoy = circulo
        // relleno detras del numero; seleccion = contorno claro encima. Hoy + seleccionado = ambos.
        DayCellStyle style = DayCellStyle.of(isToday, isSelected, level);
        Context ctx = container.getView().getContext();
        if (style.focusLevel > 0) {
            container.focusBg.setBackgroundResource(FOCUS_BG[style.focusLevel]);
        } else {
            container.focusBg.setBackground(null);
        }
        container.selectedOutline.setVisibility(style.selectionOutline ? View.VISIBLE : View.GONE);
        container.todayIndicator.setVisibility(style.todayCircle ? View.VISIBLE : View.GONE);
        container.dayText.setTextColor(ContextCompat.getColor(ctx,
                style.numberOnCircle ? R.color.bb_cal_on_today : R.color.bb_on_surface));

        // Hasta 3 puntos, uno por tipo distinto presente ese día; siempre en su color (también al
        // seleccionar: la seleccion ya no es un relleno que los tape).
        List<CalendarItem> items = inMonth ? itemsByDate.get(day.getDate()) : null;
        List<CalendarItem.Type> types = dotTypes(items);
        for (int i = 0; i < container.dots.length; i++) {
            View dot = container.dots[i];
            if (i < types.size()) {
                dot.setVisibility(View.VISIBLE);
                int color = ContextCompat.getColor(ctx, colorFor(types.get(i)));
                ViewCompat.setBackgroundTintList(dot, ColorStateList.valueOf(color));
            } else {
                dot.setVisibility(View.GONE);
            }
        }

        // The marks are colour and shape only: say them (TalkBack, and the selector of the walks).
        container.getView().setContentDescription(
                inMonth ? describe(ctx, day.getDate().getDayOfMonth(), style, types) : null);
    }

    /** "20, Selected, Task, Deadline, Focus": day number, then the marks the cell shows. */
    private static String describe(Context ctx, int dayOfMonth, DayCellStyle style,
                                   List<CalendarItem.Type> types) {
        StringBuilder sb = new StringBuilder(String.valueOf(dayOfMonth));
        if (style.todayCircle) sb.append(", ").append(ctx.getString(R.string.calendar_cell_today));
        if (style.selectionOutline) sb.append(", ").append(ctx.getString(R.string.calendar_cell_selected));
        for (CalendarItem.Type t : types) sb.append(", ").append(ctx.getString(labelFor(t)));
        if (style.focusLevel > 0) sb.append(", ").append(ctx.getString(R.string.calendar_legend_focus));
        return sb.toString();
    }

    private static int labelFor(CalendarItem.Type type) {
        switch (type) {
            case TASK: return R.string.calendar_legend_task;
            case DEADLINE: return R.string.calendar_legend_deadline;
            case REMINDER: return R.string.calendar_legend_reminder;
            case EVENT:
            default: return R.string.calendar_legend_event;
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
        final View selectedOutline;
        final View[] dots;
        CalendarDay currentDay;

        public DayContainer(@NonNull View view) {
            super(view);
            this.dayText = view.findViewById(R.id.cellDayText);
            this.todayIndicator = view.findViewById(R.id.cellTodayIndicator);
            this.focusBg = view.findViewById(R.id.cellFocusBg);
            this.selectedOutline = view.findViewById(R.id.cellSelectedOutline);
            this.dots = new View[]{
                    view.findViewById(R.id.cellDot1),
                    view.findViewById(R.id.cellDot2),
                    view.findViewById(R.id.cellDot3)
            };
        }
    }
}
