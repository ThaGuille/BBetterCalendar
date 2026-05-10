package com.example.bbettercalendar.ui.calendar.binders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DayDetailAdapter extends RecyclerView.Adapter<DayDetailAdapter.VH> {

    public interface OnItemClickListener {
        void onItemClick(CalendarItem item);
    }

    private List<CalendarItem> items = Collections.emptyList();
    private OnItemClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<CalendarItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_day_event, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CalendarItem item = items.get(position);
        holder.title.setText(item.getTitle() != null && !item.getTitle().isEmpty()
                ? item.getTitle()
                : "(untitled)");
        holder.time.setText(timeFormat.format(new Date(item.getStartMillis())));
        holder.swatch.setBackgroundColor(item.getColorArgb());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final View swatch;
        final TextView title;
        final TextView time;

        VH(@NonNull View itemView) {
            super(itemView);
            swatch = itemView.findViewById(R.id.eventColorSwatch);
            title = itemView.findViewById(R.id.eventTitle);
            time = itemView.findViewById(R.id.eventTime);
        }
    }
}
