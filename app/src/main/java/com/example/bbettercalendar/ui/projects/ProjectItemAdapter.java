package com.example.bbettercalendar.ui.projects;

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
import com.example.bbettercalendar.feedback.HapticFeedback;

import java.util.ArrayList;
import java.util.List;

// Lista de items (CalendarEntry type task) del detalle de proyecto (spec projects-mvp). Los items
// de proyecto no recurren (decisión #4) así que, a diferencia de TodayTaskAdapter, no hay
// seriesMissedCount ni botón "quitar" — sólo checkbox + título + fecha (o "sin fecha").
public class ProjectItemAdapter extends RecyclerView.Adapter<ProjectItemAdapter.ItemViewHolder> {

    public interface OnItemCheckedListener {
        void onItemChecked(CalendarEntry entry, boolean isDone);
    }

    /** Botón "focus this" (spec focus-attribution): navega a Home y arranca el timer vinculado. */
    public interface OnItemFocusListener {
        void onItemFocus(CalendarEntry entry);
    }

    private final List<CalendarEntry> items = new ArrayList<>();
    private final OnItemCheckedListener listener;
    private OnItemFocusListener focusListener;

    public ProjectItemAdapter(OnItemCheckedListener listener) {
        this.listener = listener;
    }

    public void setOnItemFocusListener(OnItemFocusListener focusListener) {
        this.focusListener = focusListener;
    }

    public void submitList(List<CalendarEntry> newItems) {
        items.clear();
        if (newItems != null) {
            // u11: pendientes primero, hechas al final (orden estable dentro de cada grupo).
            for (CalendarEntry entry : newItems) {
                if (!entry.isDone()) {
                    items.add(entry);
                }
            }
            for (CalendarEntry entry : newItems) {
                if (entry.isDone()) {
                    items.add(entry);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project_item, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        CalendarEntry entry = items.get(position);

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

        // u11: "40 / 120 min" (minutos atribuidos / objetivo) o "Sin objetivo de tiempo".
        int target = entry.getTargetMinutes();
        holder.progress.setText(target > 0
                ? holder.itemView.getContext().getString(R.string.project_subtask_progress,
                        Math.min(entry.getAttributedMinutes(), target), target)
                : holder.itemView.getContext().getString(R.string.project_subtask_no_target));
        // El play enlaza el temporizador a la tarea; solo las pendientes lo llevan (hechas: sin play).
        if (!entry.isDone() && focusListener != null) {
            holder.focus.setVisibility(View.VISIBLE);
            holder.focus.setOnClickListener(v -> focusListener.onItemFocus(entry));
        } else {
            holder.focus.setVisibility(View.GONE);
            holder.focus.setOnClickListener(null);
        }

        holder.checkbox.setOnCheckedChangeListener((button, isChecked) -> {
            if (button.isPressed()) {
                HapticFeedback.lightTap(button);
            }
            if (listener != null) {
                listener.onItemChecked(entry, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final TextView title;
        final TextView progress;
        final View focus;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.projectItemDoneCheckbox);
            title = itemView.findViewById(R.id.projectItemTitleText);
            progress = itemView.findViewById(R.id.projectItemProgressText);
            focus = itemView.findViewById(R.id.projectItemFocusButton);
        }
    }
}
