package com.example.bbettercalendar.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationDAO;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.stats.ConsentRecord;
import com.example.bbettercalendar.stats.ConsentRecordDAO;
import com.example.bbettercalendar.stats.DailyStat;
import com.example.bbettercalendar.stats.DailyStatDAO;
import com.example.bbettercalendar.stats.FocusEvent;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDAO;

@Database(entities = {CalendarEntry.class, Stats.class, Configuration.class, DailyStat.class,
        FocusEvent.class, AppRule.class, ConsentRecord.class, Project.class}, version = 13)
@TypeConverters({DBConverter.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    public abstract CalendarEntryDAO eventDao();
    public abstract StatsDAO statsDao();
    public abstract ConfigurationDAO configurationDao();
    public abstract DailyStatDAO dailyStatDao();
    public abstract FocusEventDAO focusEventDao();
    public abstract AppRuleDAO appRuleDao();
    public abstract ConsentRecordDAO consentRecordDao();
    public abstract ProjectDAO projectDao();

    //Esto sirve para que solo haya una instancia de la base de datos -> la borra y la vuelve a crear
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "eventDB")
                            .addMigrations(DBMigration.MIGRATION_6_7, DBMigration.MIGRATION_7_8,
                                    DBMigration.MIGRATION_9_10, DBMigration.MIGRATION_10_11,
                                    DBMigration.MIGRATION_11_12, DBMigration.MIGRATION_12_13)
                            .fallbackToDestructiveMigration() // Si una migración no existe, recrea la BD
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
