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
import com.example.bbettercalendar.stats.AppUsageDaily;
import com.example.bbettercalendar.stats.AppUsageDailyDAO;
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
import com.example.bbettercalendar.projects.ProjectSection;
import com.example.bbettercalendar.projects.ProjectSectionDAO;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.TagDAO;
import com.example.bbettercalendar.projects.TaskSubtask;
import com.example.bbettercalendar.projects.TaskSubtaskDAO;

@Database(entities = {CalendarEntry.class, Stats.class, Configuration.class, DailyStat.class,
        FocusEvent.class, AppRule.class, ConsentRecord.class, Project.class, AppUsageDaily.class,
        ProjectSection.class, TaskSubtask.class, Tag.class},
        version = 16)
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
    public abstract AppUsageDailyDAO appUsageDailyDao();
    // rediseno-proyectos (DB v16)
    public abstract ProjectSectionDAO projectSectionDao();
    public abstract TaskSubtaskDAO taskSubtaskDao();
    public abstract TagDAO tagDao();

    // Esto sirve para que solo haya una instancia de la base de datos -> la borra y la vuelve a crear.
    // Package-private (spec di-threading-consolidation): la única puerta de entrada es
    // database/DatabaseModule.java (Hilt) -- el compilador impide que cualquier otra clase la llame
    // directamente y se salte la inyección.
    static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = builder(context, "eventDB")
                            .fallbackToDestructiveMigration() // Si una migración no existe, recrea la BD
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // Migraciones reales + siembra de una base nueva, con el contexto de la app (rediseno-proyectos, DB
    // v16: la migración 15 -> 16 y el onCreate siembran nombres en el idioma del móvil, así que los dos
    // necesitan un Context). Package-private: getDatabase() y los tests de database/ la usan.
    static RoomDatabase.Builder<AppDatabase> builder(final Context context, String name) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        return Room.databaseBuilder(app, AppDatabase.class, name)
                .addMigrations(DBMigration.MIGRATION_6_7, DBMigration.MIGRATION_7_8,
                        DBMigration.MIGRATION_9_10, DBMigration.MIGRATION_10_11,
                        DBMigration.MIGRATION_11_12, DBMigration.MIGRATION_12_13,
                        DBMigration.MIGRATION_13_14, DBMigration.MIGRATION_14_15,
                        DBMigration.migration15To16(app))
                .addCallback(DBMigration.seedCallback(app));
    }
}
