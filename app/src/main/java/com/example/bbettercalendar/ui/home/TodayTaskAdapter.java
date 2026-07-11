package com.example.bbettercalendar.ui.home;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Lista de tareas (type task) de Home — spec tasks-home-today. Bindea CalendarEntry
 * directamente (a diferencia de DayDetailAdapter/CalendarItem, que no lleva isDone)
 * para que el toggle del checkbox sea un solo salto hasta el DAO.
 */
public class TodayTaskAdapter extends RecyclerView.Adapter<TodayTaskAdapter.TaskViewHolder> {

    public interface OnTaskCheckedListener {
        void onTaskChecked(CalendarEntry entry, boolean isDone);
    }

    private final List<CalendarEntry> tasks = new ArrayList<>();
    // La sección "older uncompleted" muestra la fecha de la tarea; la de hoy, su hora.
    private final boolean showDate;
    private final OnTaskCheckedListener listener;

    public TodayTaskAdapter(boolean showDate, OnTaskCheckedListener listener) {
        this.showDate = showDate;
        this.listener = listener;
    }

    public void submitList(List<CalendarEntry> newTasks) {
        tasks.clear();
        if (newTasks != null) {
            tasks.addAll(newTasks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_today_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        CalendarEntry entry = tasks.get(position);

        // Quitar el listener antes de setChecked: el holder reciclado aún tiene el
        // callback de la fila anterior y dispararía un update fantasma.
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(entry.isDone());

        holder.title.setText(entry.getTitle());
        int flags = holder.title.getPaintFlags();
        if (entry.isDone()) {
            holder.title.setPaintFlags(flags | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setTextColor(ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.bb_on_surface_muted));
        } else {
            holder.title.setPaintFlags(flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setTextColor(ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.bb_on_surface));
        }

        holder.time.setText(formatWhen(entry));

        holder.checkbox.setOnCheckedChangeListener((button, isChecked) -> {
            if (listener != null) {
                listener.onTaskChecked(entry, isChecked);
            }
        });
    }

    private String formatWhen(CalendarEntry entry) {
        long startMillis = entry.getStartMillis();
        if (startMillis <= 0L) {
            return "";
        }
        if (showDate) {
            return new SimpleDateFormat("MMM d", Locale.getDefault())
                    .format(new Date(startMillis));
        }
        // Las tareas sin hora elegida se guardan a las 00:00 — no mostramos esa hora.
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startMillis);
        if (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0) {
            return "";
        }
        return new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(startMillis));
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final TextView title;
        final TextView time;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.taskDoneCheckbox);
            title = itemView.findViewById(R.id.taskTitleText);
            time = itemView.findViewById(R.id.taskTimeText);
        }
    }
}
