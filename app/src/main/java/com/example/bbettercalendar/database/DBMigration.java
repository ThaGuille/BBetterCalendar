package com.example.bbettercalendar.database;

import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.usage.limits.UsageLimitScheduler;
import com.example.bbettercalendar.usage.limits.WarnedTodayStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class DBMigration extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannels.createAll(this);
        armUsageLimitScheduler();
    }

    // Arma el chequeo periódico de límites en cada arranque en frío. BootReceiver hace lo mismo
    // tras un reinicio del dispositivo. Construido a mano (no vía Hilt: Application no es un
    // target de @AndroidEntryPoint) fuera del hilo principal, ya que arm() lee app_rule (regla #3).
    private void armUsageLimitScheduler() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            AppRuleDAO appRuleDao = AppDatabase.getDatabase(this).appRuleDao();
            WarnedTodayStore warnedStore = new WarnedTodayStore(this);
            new UsageLimitScheduler(this, alarmManager, appRuleDao, warnedStore).arm();
        });
        executor.shutdown();
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

    // Phase 2 (uso de apps): añade las tablas app_rule (apps seguidas + campos de límite/bloqueo
    // reservados para Phases 3-4) y consent_record (acuse de la divulgación de Usage Access).
    // Migración aditiva (sólo CREATE TABLE) -> el histórico v9 (daily_stat / focus_event) se conserva.
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS app_rule (" +
                    "packageName TEXT NOT NULL PRIMARY KEY, tracked INTEGER NOT NULL DEFAULT 0, " +
                    "dailyLimitMinutes INTEGER NOT NULL DEFAULT 0, warnBeforeMinutes INTEGER NOT NULL DEFAULT 5, " +
                    "instantBlock INTEGER NOT NULL DEFAULT 0, blockedToday INTEGER NOT NULL DEFAULT 0, " +
                    "blockStyle INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE TABLE IF NOT EXISTS consent_record (" +
                    "`key` TEXT NOT NULL PRIMARY KEY, acceptedAt INTEGER NOT NULL DEFAULT 0, " +
                    "disclosureVersion INTEGER NOT NULL DEFAULT 0)");
        }
    };

    // Phase 2 tareas (spec tasks-recurrence): recurrencia real basada en plantilla + ocurrencias
    // materializadas. Aditiva (sólo ADD COLUMN) -> el histórico de calendarEntry se conserva.
    //   isTemplate/templateId  -> fila-definición de la serie / vínculo ocurrencia->plantilla
    //   repetitionInterval     -> "cada X días" (default 1 para no romper filas legacy diarias)
    //   repetitionDays         -> bitmask de días de la semana (weekly)
    //   materializedUntilMillis-> marca de agua por plantilla (top-up idempotente)
    //   isDismissed            -> tarea retirada de las listas sin borrado (datos para stats)
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN isTemplate INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN templateId INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN repetitionInterval INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN repetitionDays INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN materializedUntilMillis INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN isDismissed INTEGER NOT NULL DEFAULT 0");
        }
    };
}
