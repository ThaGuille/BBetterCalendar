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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Lista de items (CalendarEntry type task) del detalle de proyecto (spec projects-mvp). Los items
// de proyecto no recurren (decisión #4) así que, a diferencia de TodayTaskAdapter, no hay
// seriesMissedCount ni botón "quitar" — sólo checkbox + título + fecha (o "sin fecha").
public class ProjectItemAdapter extends RecyclerView.Adapter<ProjectItemAdapter.ItemViewHolder> {

    public interface OnItemCheckedListener {
        void onItemChecked(CalendarEntry entry, boolean isDone);
    }

    private final List<CalendarEntry> items = new ArrayList<>();
    private final OnItemCheckedListener listener;

    public ProjectItemAdapter(OnItemCheckedListener listener) {
        this.listener = listener;
    }

    public void submitList(List<CalendarEntry> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
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

        holder.date.setText(entry.getStartMillis() > 0L
                ? new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(entry.getStartMillis()))
                : holder.itemView.getContext().getString(R.string.project_item_no_date));

        holder.checkbox.setOnCheckedChangeListener((button, isChecked) -> {
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
        final TextView date;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.projectItemDoneCheckbox);
            title = itemView.findViewById(R.id.projectItemTitleText);
            date = itemView.findViewById(R.id.projectItemDateText);
        }
    }
}
