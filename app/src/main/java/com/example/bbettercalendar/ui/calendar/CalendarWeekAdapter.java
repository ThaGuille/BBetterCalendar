package com.example.bbettercalendar.ui.calendar;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;

import java.util.List;

public class CalendarWeekAdapter extends RecyclerView.Adapter<CalendarWeekAdapter.ViewHolder>{

    private List<CalendarEntry> calendarEntries;
    private Context context;

    public CalendarWeekAdapter(Context context, List<CalendarEntry> calendarEntries) {
        this.calendarEntries = calendarEntries;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Infla la vista de cada elemento
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recyclerview_week_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // Configura los elementos de la vista
        CalendarEntry item = calendarEntries.get(position);
        // holder.miTextView.setText(item.getTexto()); // Ejemplo
    }

    @Override
    public int getItemCount() {
        return calendarEntries.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        // Define los elementos de la vista
        // TextView miTextView;

        public ViewHolder(View view) {
            super(view);
            TextView eventTitle = view.findViewById(R.id.calendarEventTitle);
            eventTitle.setText("EUREKA");
            // miTextView = view.findViewById(R.id.mi_text_view);
        }
    }

}
