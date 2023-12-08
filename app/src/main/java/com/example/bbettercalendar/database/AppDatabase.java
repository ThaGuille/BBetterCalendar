package com.example.bbettercalendar.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.bbettercalendar.events.Event;
import com.example.bbettercalendar.events.EventDao;

@Database(entities = {Event.class}, version = 3)
@TypeConverters({DBConverter.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    public abstract EventDao eventDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "eventDB")
                            .addMigrations(DBMigration.MIGRATION_1_2, DBMigration.MIGRATION_2_3)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
