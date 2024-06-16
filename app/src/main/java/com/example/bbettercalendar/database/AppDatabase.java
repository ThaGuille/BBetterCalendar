package com.example.bbettercalendar.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.bbettercalendar.events.Event;
import com.example.bbettercalendar.events.EventDao;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;

@Database(entities = {Event.class, Stats.class}, version = 1)
@TypeConverters({DBConverter.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    public abstract EventDao eventDao();
    public abstract StatsDAO statsDao();

    //Esto sirve para que solo haya una instancia de la base de datos -> la borra y la vuelve a crear
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "eventDB")
                            .fallbackToDestructiveMigration() // Eliminar todas las migraciones
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    //Y esto es lo que hay que suar para hacer cambios sin perder los datos, actualizando la versión arriba
    /**
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "eventDB")
                            .addMigrations(DBMigration.MIGRATION_1_2, DBMigration.MIGRATION_2_3, DBMigration.MIGRATION_3_4)
                            .build();
                }
            }
        }
        return INSTANCE;
    }*/
}
