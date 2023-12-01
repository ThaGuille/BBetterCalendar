package com.example.bbettercalendar.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.bbettercalendar.events.Event;
import com.example.bbettercalendar.events.EventDao;

@Database(entities = {Event.class}, version = 1)
@TypeConverters({DBConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract EventDao eventDao();
}
