package com.example.bbettercalendar.ui.calendar;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.events.Event;

import java.util.List;

public class CalendarWeekAdapter extends RecyclerView.Adapter<CalendarWeekAdapter.ViewHolder>{

    private List<Event> events;
    private Context context;

    public CalendarWeekAdapter(Context context, List<Event> events) {
        this.events = events;
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
        Event item = events.get(position);
        // holder.miTextView.setText(item.getTexto()); // Ejemplo
    }

    @Override
    public int getItemCount() {
        return events.size();
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
