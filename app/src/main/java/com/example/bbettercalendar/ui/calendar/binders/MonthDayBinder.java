package com.example.bbettercalendar.ui.calendar.binders;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

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
    private boolean focusLayerVisible;
    private LocalDate selectedDate;
    private OnDayClickListener listener;

    public void setItemsByDate(@NonNull Map<LocalDate, List<CalendarItem>> itemsByDate) {
        this.itemsByDate = itemsByDate;
    }

    public void setFocusMinutesByDate(@NonNull Map<LocalDate, Integer> focusMinutesByDate) {
        this.focusMinutesByDate = focusMinutesByDate;
    }

    /** The focus wash is an optional layer (the «Enfoque» button); off, no cell is tinted. */
    public void setFocusLayerVisible(boolean visible) {
        this.focusLayerVisible = visible;
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
        Integer minutes = inMonth && focusLayerVisible ? focusMinutesByDate.get(day.getDate()) : null;
        int level = CalendarHeatLevel.levelFor(minutes == null ? 0 : minutes);

        // Canales separados. Fondo = veladura de enfoque (solo con la capa encendida; se mantiene al
        // seleccionar); hoy = circulo relleno detras del numero; seleccion = contorno claro encima.
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

        // Marcas sin color por tipo: banderita si hay una entrega; un punto neutro por cada otro
        // elemento (tarea, evento o recordatorio), hasta MAX_DOTS. Los puntos dicen cuanta carga hay.
        List<CalendarItem> items = inMonth ? itemsByDate.get(day.getDate()) : null;
        container.deadlineFlag.setVisibility(hasDeadline(items) ? View.VISIBLE : View.GONE);
        int dots = dotCount(items);
        for (int i = 0; i < container.dots.length; i++) {
            container.dots[i].setVisibility(i < dots ? View.VISIBLE : View.GONE);
        }

        // The marks are shape only: say them (TalkBack, and the selector of the walks).
        container.getView().setContentDescription(
                inMonth ? describe(ctx, day.getDate().getDayOfMonth(), style, presentTypes(items)) : null);
    }

    /** "20, Selected, Task, Deadline, Focus": day number, then what the cell holds. */
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

    static boolean hasDeadline(List<CalendarItem> items) {
        if (items == null) return false;
        for (CalendarItem item : items) {
            if (item.getType() == CalendarItem.Type.DEADLINE) return true;
        }
        return false;
    }

    /** Elementos que no son entrega, con tope en {@link #MAX_DOTS}. */
    static int dotCount(List<CalendarItem> items) {
        if (items == null) return 0;
        int count = 0;
        for (CalendarItem item : items) {
            if (item.getType() != CalendarItem.Type.DEADLINE) count++;
        }
        return Math.min(count, MAX_DOTS);
    }

    /** Tipos distintos presentes, en orden fijo tarea, evento, entrega, recordatorio (para la descripcion). */
    static List<CalendarItem.Type> presentTypes(List<CalendarItem> items) {
        List<CalendarItem.Type> out = new ArrayList<>();
        if (items == null) return out;
        CalendarItem.Type[] order = {CalendarItem.Type.TASK, CalendarItem.Type.EVENT,
                CalendarItem.Type.DEADLINE, CalendarItem.Type.REMINDER};
        for (CalendarItem.Type t : order) {
            for (CalendarItem item : items) {
                if (item.getType() == t) {
                    out.add(t);
                    break;
                }
            }
        }
        return out;
    }

    public static class DayContainer extends ViewContainer {
        final TextView dayText;
        final View todayIndicator;
        final View focusBg;
        final View selectedOutline;
        final View deadlineFlag;
        final View[] dots;
        CalendarDay currentDay;

        public DayContainer(@NonNull View view) {
            super(view);
            this.dayText = view.findViewById(R.id.cellDayText);
            this.todayIndicator = view.findViewById(R.id.cellTodayIndicator);
            this.focusBg = view.findViewById(R.id.cellFocusBg);
            this.selectedOutline = view.findViewById(R.id.cellSelectedOutline);
            this.deadlineFlag = view.findViewById(R.id.cellDeadlineFlag);
            this.dots = new View[]{
                    view.findViewById(R.id.cellDot1),
                    view.findViewById(R.id.cellDot2),
                    view.findViewById(R.id.cellDot3)
            };
        }
    }
}
