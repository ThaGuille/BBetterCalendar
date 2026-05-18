package com.example.bbettercalendar.ui.calendar;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItemMapper;

import java.util.Collections;
import java.util.List;

public class CalendarViewModel extends AndroidViewModel {

    public static class DateRange {
        public final long startMillis;
        public final long endMillis;
        public DateRange(long startMillis, long endMillis) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }
    }

    private final CalendarEntryDAO dao;
    private final MutableLiveData<DateRange> range = new MutableLiveData<>();
    private final LiveData<List<CalendarItem>> items;

    public CalendarViewModel(@NonNull Application application) {
        super(application);
        this.dao = AppDatabase.getDatabase(application).eventDao();
        this.items = Transformations.switchMap(range, r -> {
            if (r == null) {
                MutableLiveData<List<CalendarItem>> empty = new MutableLiveData<>();
                empty.setValue(Collections.emptyList());
                return empty;
            }
            LiveData<java.util.List<com.example.bbettercalendar.calendarEntries.CalendarEntry>> entries =
                    dao.getEventsBetween(r.startMillis, r.endMillis);
            return Transformations.map(entries,
                    list -> CalendarItemMapper.toItems(list, getApplication()));
        });
    }

    public void setRange(long startMillis, long endMillis) {
        DateRange current = range.getValue();
        if (current != null && current.startMillis == startMillis && current.endMillis == endMillis) {
            return;
        }
        range.setValue(new DateRange(startMillis, endMillis));
    }

    public LiveData<List<CalendarItem>> getItems() {
        return items;
    }

    // Re-emit the current range as a new DateRange instance. switchMap rebuilds the underlying
    // Room LiveData, forcing a fresh query. Use after returning from a screen that inserts
    // entries — Room's InvalidationTracker can lag and miss the first refresh otherwise.
    public void refresh() {
        DateRange current = range.getValue();
        if (current != null) {
            range.setValue(new DateRange(current.startMillis, current.endMillis));
        }
    }
}
