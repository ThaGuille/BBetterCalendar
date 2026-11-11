package com.example.bbettercalendar.ui.calendar.binders;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
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
                : holder.itemView.getContext().getString(R.string.calendar_untitled));
        holder.meta.setText(metaFor(holder.itemView.getContext(), item));
        holder.time.setText(item.getType() == CalendarItem.Type.DEADLINE
                ? "" : timeFormat.format(new Date(item.getStartMillis())));
        ViewCompat.setBackgroundTintList(holder.swatch, ColorStateList.valueOf(item.getColorArgb()));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** "Evento · 2 h": tipo + duración (sin duración, sólo el tipo). */
    static String metaFor(Context ctx, CalendarItem item) {
        String type = typeLabel(ctx, item.getType());
        long minutes = item.durationMinutes();
        if (minutes <= 0) return type;
        return ctx.getString(R.string.calendar_agenda_meta, type, formatDuration(ctx, minutes));
    }

    static String typeLabel(Context ctx, CalendarItem.Type type) {
        switch (type) {
            case TASK: return ctx.getString(R.string.calendar_legend_task);
            case DEADLINE: return ctx.getString(R.string.calendar_legend_deadline);
            case REMINDER: return ctx.getString(R.string.calendar_type_reminder);
            case EVENT:
            default: return ctx.getString(R.string.calendar_legend_event);
        }
    }

    /** "30 min" / "2 h" / "1 h 30 m". */
    public static String formatDuration(Context ctx, long minutes) {
        long h = minutes / 60;
        long m = minutes % 60;
        if (h == 0) return ctx.getString(R.string.calendar_duration_min, (int) m);
        if (m == 0) return ctx.getString(R.string.calendar_duration_h, (int) h);
        return ctx.getString(R.string.calendar_duration_h_min, (int) h, (int) m);
    }

    static class VH extends RecyclerView.ViewHolder {
        final View swatch;
        final TextView title;
        final TextView meta;
        final TextView time;

        VH(@NonNull View itemView) {
            super(itemView);
            swatch = itemView.findViewById(R.id.eventColorSwatch);
            title = itemView.findViewById(R.id.eventTitle);
            meta = itemView.findViewById(R.id.eventMeta);
            time = itemView.findViewById(R.id.eventTime);
        }
    }
}
