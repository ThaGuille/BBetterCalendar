package com.example.bbettercalendar.ui.calendar.binders;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.calendar.CalendarTitleFormatter;
import com.example.bbettercalendar.ui.calendar.domain.WeekAgenda;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agenda de la vista Semana (rediseno-calendario-color): los siete días seguidos, cada uno con su
 * cabecera y sus filas ({@link WeekAgenda}). Las filas de elemento son las de la agenda del día
 * ({@link DayDetailAdapter.VH}).
 */
public class WeekAgendaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;
    private static final int TYPE_EMPTY = 2;

    private List<WeekAgenda.Row> rows = Collections.emptyList();
    private Map<LocalDate, Integer> focusMinutesByDate = Collections.emptyMap();
    private boolean focusLayerVisible;
    private DayDetailAdapter.OnItemClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public void setOnItemClickListener(DayDetailAdapter.OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<WeekAgenda.Row> rows,
                       @NonNull Map<LocalDate, Integer> focusMinutesByDate,
                       boolean focusLayerVisible) {
        this.rows = rows;
        this.focusMinutesByDate = focusMinutesByDate;
        this.focusLayerVisible = focusLayerVisible;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        switch (rows.get(position).kind) {
            case HEADER: return TYPE_HEADER;
            case ITEM: return TYPE_ITEM;
            case EMPTY:
            default: return TYPE_EMPTY;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inflater.inflate(R.layout.item_week_agenda_header, parent, false));
        }
        if (viewType == TYPE_ITEM) {
            return DayDetailAdapter.VH.create(parent);
        }
        return new EmptyVH(inflater.inflate(R.layout.item_week_agenda_empty, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        WeekAgenda.Row row = rows.get(position);
        if (holder instanceof HeaderVH) {
            bindHeader((HeaderVH) holder, row.date);
        } else if (holder instanceof DayDetailAdapter.VH && row.item != null) {
            ((DayDetailAdapter.VH) holder).bind(row.item, timeFormat, listener);
        }
    }

    private void bindHeader(HeaderVH holder, LocalDate date) {
        Context ctx = holder.itemView.getContext();
        boolean today = date.equals(LocalDate.now());
        String weekday = CalendarTitleFormatter.capitalize(
                date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()));
        holder.day.setText(today
                ? ctx.getString(R.string.calendar_cell_today)
                : ctx.getString(R.string.calendar_agenda_title, weekday, date.getDayOfMonth()));
        holder.day.setTextColor(ContextCompat.getColor(ctx,
                today ? R.color.bb_primary_bright : R.color.bb_on_surface));
        Integer minutes = focusLayerVisible ? focusMinutesByDate.get(date) : null;
        if (minutes != null && minutes > 0) {
            holder.focus.setText(ctx.getString(R.string.calendar_week_focus,
                    DayDetailAdapter.formatDuration(ctx, minutes)));
            holder.focus.setVisibility(View.VISIBLE);
        } else {
            holder.focus.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView day;
        final TextView focus;

        HeaderVH(@NonNull View itemView) {
            super(itemView);
            day = itemView.findViewById(R.id.weekAgendaDay);
            focus = itemView.findViewById(R.id.weekAgendaFocus);
        }
    }

    static class EmptyVH extends RecyclerView.ViewHolder {
        EmptyVH(@NonNull View itemView) {
            super(itemView);
        }
    }
}
