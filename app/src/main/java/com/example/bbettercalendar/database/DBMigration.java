package com.example.bbettercalendar.database;

import android.app.Application;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.bbettercalendar.notifications.NotificationChannels;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class DBMigration extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannels.createAll(this);
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Event ADD COLUMN dayAndHour TEXT");
        }
    };
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS Event");
            database.execSQL("CREATE TABLE IF NOT EXISTS Event (id INTEGER PRIMARY KEY NOT NULL, title TEXT, description TEXT, " +
                    "limitDate TEXT, startDayAndHour TEXT, endDayAndHour TEXT, notifications TEXT)");

        }
    };
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS Stats (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "completedEvents INTEGER NOT NULL, missedEvents INTEGER NOT NULL, totalTimeSpent INTEGER NOT NULL)");

        }
    };

    // Adds long mirror columns for date-range queries. Pre-existing rows get 0 for both
    // (new rows from EventBuilder.build() populate them from the Calendar fields).
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN startMillis INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN endMillis INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE configuration ADD COLUMN notificationPermissionAskCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE configuration ADD COLUMN notificationPermissionLastAskedMillis INTEGER NOT NULL DEFAULT 0");
        }
    };
}
