package com.example.bbettercalendar.database;

import android.app.Application;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DBMigration extends Application {


    @Override
    public void onCreate() {
        super.onCreate();
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
}
