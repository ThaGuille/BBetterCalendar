package com.example.bbettercalendar.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppUsageDaily;
import com.example.bbettercalendar.stats.DayMinutes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * mejora-visual u1: migración real 14 -> 15 (regla #6). Sin dependencia nueva (no hay room-testing):
 * crea una base v14 con los CREATE exactos que Room exportó en app/schemas/.../14.json, mete filas en
 * TODAS las tablas, y la abre con Room a v15 solo con {@link DBMigration#MIGRATION_14_15} y SIN
 * fallbackToDestructiveMigration: si el SQL de la migración no deja el esquema que Room espera, la
 * validación de Room lanza al abrir; si la migración borrara algo, las filas no estarían.
 */
@RunWith(AndroidJUnit4.class)
public class Migration14To15Test {

    private static final String DB_NAME = "migration-14-15-test.db";

    // Los CREATE de 14.json (createSql con ${TABLE_NAME} sustituido) + la tabla de Room.
    private static final String[] V14_SCHEMA = {
            "CREATE TABLE IF NOT EXISTS `calendarEntry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `description` TEXT, `limitDate` TEXT, `startDayAndHour` TEXT, `endDayAndHour` TEXT, `startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, `notifications` TEXT, `repetition` INTEGER NOT NULL, `repetitionInterval` INTEGER NOT NULL, `repetitionDays` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `targetMinutes` INTEGER NOT NULL, `isDone` INTEGER NOT NULL, `isTemplate` INTEGER NOT NULL, `templateId` INTEGER NOT NULL, `materializedUntilMillis` INTEGER NOT NULL, `isDismissed` INTEGER NOT NULL, `projectId` INTEGER NOT NULL, `hiddenInCalendar` INTEGER NOT NULL, `type` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `stats` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `totalTimeStudied` INTEGER NOT NULL, `todayTimeStudied` INTEGER NOT NULL, `totalTasksDone` INTEGER NOT NULL, `todayTasksDone` INTEGER NOT NULL, `maxStreak` INTEGER NOT NULL, `currentStreak` INTEGER NOT NULL, `lastDayStreak` TEXT, `totalFails` INTEGER NOT NULL, `todayFails` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `configuration` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `homeTimerTime` INTEGER NOT NULL, `homeRestTime` INTEGER NOT NULL, `homeNumberOfCycles` INTEGER NOT NULL, `homeIsRestEnabled` INTEGER NOT NULL, `homeIsInfiniteCycleEnabled` INTEGER NOT NULL, `homeIsAutoCycle` INTEGER NOT NULL, `homeIsPauseEnabled` INTEGER NOT NULL, `homeIsAlarmEnabled` INTEGER NOT NULL, `homeIsVibrationEnabled` INTEGER NOT NULL, `country` TEXT, `notificationPermissionAskCount` INTEGER NOT NULL, `notificationPermissionLastAskedMillis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `daily_stat` (`day` TEXT NOT NULL, `focusMinutes` INTEGER NOT NULL, `fails` INTEGER NOT NULL, `tasksDone` INTEGER NOT NULL, `phoneUsageMinutes` INTEGER NOT NULL, PRIMARY KEY(`day`))",
            "CREATE TABLE IF NOT EXISTS `focus_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `type` INTEGER NOT NULL, `durationMin` INTEGER NOT NULL, `entryId` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `app_rule` (`packageName` TEXT NOT NULL, `tracked` INTEGER NOT NULL, `dailyLimitMinutes` INTEGER NOT NULL, `warnBeforeMinutes` INTEGER NOT NULL, `instantBlock` INTEGER NOT NULL, `blockedToday` INTEGER NOT NULL, `blockStyle` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
            "CREATE TABLE IF NOT EXISTS `consent_record` (`key` TEXT NOT NULL, `acceptedAt` INTEGER NOT NULL, `disclosureVersion` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS `project` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `notes` TEXT, `status` INTEGER NOT NULL, `softDeadlineMillis` INTEGER NOT NULL, `colorIndex` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, `completedAtMillis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '378871ba645de85e0d0250a763c8b5f6')",
    };

    private static final String[] V14_TABLES = {"calendarEntry", "stats", "configuration", "daily_stat",
            "focus_event", "app_rule", "consent_record", "project"};

    private static final long DONE_WITH_DATE_START = 1_760_000_000_000L;
    private static final String NO_NOTIFICATIONS = "[false,false,false,false,false,false,false]";

    private Context context;
    private AppDatabase room;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DB_NAME);
        createV14WithRows();
    }

    @After
    public void tearDown() {
        if (room != null) {
            room.close();
        }
        context.deleteDatabase(DB_NAME);
    }

    // ------------------------------------------------------------------------------- tests

    @Test
    public void roomOpensTheMigratedDatabaseAndItsSchemaValidates() {
        openV15();
        // getWritableDatabase() corre la migración y la validación de esquema de Room: si el SQL no
        // coincide con las entidades lanza IllegalStateException ("Migration didn't properly handle").
        SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
        assertEquals(15, db.getVersion());

        // La tabla nueva funciona por su DAO (clave compuesta + suma por día).
        room.appUsageDailyDao().upsertAll(Arrays.asList(
                new AppUsageDaily("2026-11-09", "com.a", 30),
                new AppUsageDaily("2026-11-09", "com.b", 15),
                new AppUsageDaily("2026-11-10", "com.a", 5)));
        // Upsert idempotente sobre (day, packageName).
        room.appUsageDailyDao().upsertAll(Arrays.asList(new AppUsageDaily("2026-11-09", "com.a", 40)));
        List<DayMinutes> totals = room.appUsageDailyDao().getDayTotals("2026-11-09", "2026-11-10");
        assertEquals(2, totals.size());
        assertEquals("2026-11-09", totals.get(0).day);
        assertEquals(55, totals.get(0).minutes);
        assertEquals(5, totals.get(1).minutes);
        assertEquals(2, room.appUsageDailyDao().getForPackage("com.a", "2026-11-01", "2026-11-30").size());
        assertEquals(3, room.appUsageDailyDao().getBetween("2026-11-09", "2026-11-10").size());
    }

    @Test
    public void everyV14RowSurvives() {
        openV15();
        SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
        assertEquals(4, count(db, "calendarEntry"));
        assertEquals(1, count(db, "stats"));
        assertEquals(1, count(db, "configuration"));
        assertEquals(1, count(db, "daily_stat"));
        assertEquals(2, count(db, "focus_event"));
        assertEquals(2, count(db, "app_rule"));
        assertEquals(1, count(db, "consent_record"));
        assertEquals(1, count(db, "project"));
        assertEquals(0, count(db, "app_usage_daily"));

        // Las columnas viejas conservan su valor, leído a través de las entidades v15.
        assertEquals("Tarea hecha con fecha", room.eventDao().getEventById(1).getTitle());
        assertEquals(DONE_WITH_DATE_START, room.eventDao().getEventById(1).getStartMillis());
        assertEquals(3, room.eventDao().getEventById(1).getProjectId());
        AppRule limited = room.appRuleDao().getByPackage("com.limited");
        assertNotNull(limited);
        assertEquals(45, limited.dailyLimitMinutes);
        assertTrue(limited.enforceAtLimit);
        assertEquals("Tesis", room.projectDao().getById(3).name);
        assertEquals(25, room.focusEventDao().getRange(0L, Long.MAX_VALUE).get(0).durationMin);
        assertEquals(1, room.dailyStatDao().getRange("2026-11-01", "2026-11-30").size());
        assertEquals(1500000, room.configurationDao().getConfiguration().getHomeTimerTime());
    }

    @Test
    public void backfillsUseStartMillisOrTheMigrationInstant() {
        long before = System.currentTimeMillis();
        openV15();
        SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
        long after = System.currentTimeMillis();

        // Hecha con fecha -> su startMillis; hecha sin fecha -> instante de la migración; no hechas -> 0.
        assertEquals(DONE_WITH_DATE_START, longOf(db, "SELECT completedAtMillis FROM calendarEntry WHERE id = 1"));
        long undated = longOf(db, "SELECT completedAtMillis FROM calendarEntry WHERE id = 2");
        assertTrue("undated done row stamped with the migration instant", undated >= before && undated <= after);
        assertEquals(0L, longOf(db, "SELECT completedAtMillis FROM calendarEntry WHERE id = 3"));
        assertEquals(0L, longOf(db, "SELECT completedAtMillis FROM calendarEntry WHERE id = 4"));

        // Regla con límite -> instante de la migración; sin límite -> 0.
        long since = room.appRuleDao().getByPackage("com.limited").limitSinceMillis;
        assertTrue("limited rule stamped with the migration instant", since >= before && since <= after);
        assertEquals(0L, room.appRuleDao().getByPackage("com.free").limitSinceMillis);

        // focus_event: columnas nuevas a 0 en el histórico (dato desconocido).
        assertEquals(0L, longOf(db, "SELECT COUNT(*) FROM focus_event WHERE elapsedMin != 0 OR pauseCount != 0"));
    }

    @Test
    public void tasksDoneAreCountedByCompletionDate() {
        openV15();
        // La fila 1 se completó (backfill) en su startMillis; la 2 ahora; la 3 y la 4 no están hechas.
        assertEquals(1, room.eventDao().countDoneTasksBetween(DONE_WITH_DATE_START - 1, DONE_WITH_DATE_START + 1));
        long now = System.currentTimeMillis();
        assertEquals(1, room.eventDao().countDoneTasksBetween(now - 60_000L, now + 60_000L));
    }

    @Test
    public void setDailyLimitStampsAndResetsLimitSince() {
        openV15();
        room.appRuleDao().setDailyLimit("com.free", 30, 1_000L);
        assertEquals(1_000L, room.appRuleDao().getByPackage("com.free").limitSinceMillis);
        // Cambiar un límite que ya existía no mueve la fecha.
        room.appRuleDao().setDailyLimit("com.free", 60, 2_000L);
        assertEquals(1_000L, room.appRuleDao().getByPackage("com.free").limitSinceMillis);
        assertEquals(60, room.appRuleDao().getByPackage("com.free").dailyLimitMinutes);
        // Quitarlo la vuelve a 0.
        room.appRuleDao().setDailyLimit("com.free", 0, 3_000L);
        assertEquals(0L, room.appRuleDao().getByPackage("com.free").limitSinceMillis);
    }

    // ----------------------------------------------------------------------------- helpers

    private void openV15() {
        // SIN fallbackToDestructiveMigration: una migración que falte o no cuadre lanza en vez de borrar.
        room = Room.databaseBuilder(context, AppDatabase.class, DB_NAME)
                .addMigrations(DBMigration.MIGRATION_14_15)
                .allowMainThreadQueries()
                .build();
    }

    private void createV14WithRows() {
        File file = context.getDatabasePath(DB_NAME);
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            assertTrue(dir.mkdirs());
        }
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            for (String sql : V14_SCHEMA) {
                db.execSQL(sql);
            }
            // calendarEntry: 1 hecha con fecha, 2 hecha sin fecha, 3 no hecha, 4 plantilla no hecha.
            db.insertOrThrow("calendarEntry", null, entry(1, "Tarea hecha con fecha", DONE_WITH_DATE_START, true, 3, 0));
            db.insertOrThrow("calendarEntry", null, entry(2, "Tarea hecha sin fecha", 0L, true, 0, 0));
            db.insertOrThrow("calendarEntry", null, entry(3, "Tarea pendiente", DONE_WITH_DATE_START, false, 0, 0));
            db.insertOrThrow("calendarEntry", null, entry(4, "Plantilla", DONE_WITH_DATE_START, false, 0, 1));

            db.execSQL("INSERT INTO stats (id, totalTimeStudied, todayTimeStudied, totalTasksDone, todayTasksDone, "
                    + "maxStreak, currentStreak, lastDayStreak, totalFails, todayFails) "
                    + "VALUES (1, 9000000, 1500000, 12, 1, 5, 2, '2026-11-10', 3, 0)");
            db.execSQL("INSERT INTO configuration (id, homeTimerTime, homeRestTime, homeNumberOfCycles, "
                    + "homeIsRestEnabled, homeIsInfiniteCycleEnabled, homeIsAutoCycle, homeIsPauseEnabled, "
                    + "homeIsAlarmEnabled, homeIsVibrationEnabled, country, notificationPermissionAskCount, "
                    + "notificationPermissionLastAskedMillis) VALUES (1, 1500000, 300000, 4, 1, 0, 0, 1, 1, 1, 'ES', 1, 0)");
            db.execSQL("INSERT INTO daily_stat (day, focusMinutes, fails, tasksDone, phoneUsageMinutes) "
                    + "VALUES ('2026-11-10', 50, 1, 2, 120)");
            db.execSQL("INSERT INTO focus_event (id, timestamp, type, durationMin, entryId) "
                    + "VALUES (1, 1762700000000, 0, 25, 1)");
            db.execSQL("INSERT INTO focus_event (id, timestamp, type, durationMin, entryId) "
                    + "VALUES (2, 1762703000000, 1, 0, 0)");
            db.execSQL("INSERT INTO app_rule (packageName, tracked, dailyLimitMinutes, warnBeforeMinutes, "
                    + "instantBlock, blockedToday, blockStyle) VALUES ('com.limited', 1, 45, 5, 1, 0, 0)");
            db.execSQL("INSERT INTO app_rule (packageName, tracked, dailyLimitMinutes, warnBeforeMinutes, "
                    + "instantBlock, blockedToday, blockStyle) VALUES ('com.free', 1, 0, 5, 0, 0, 0)");
            db.execSQL("INSERT INTO consent_record (`key`, acceptedAt, disclosureVersion) "
                    + "VALUES ('usage_access', 1762000000000, 1)");
            db.execSQL("INSERT INTO project (id, name, notes, status, softDeadlineMillis, colorIndex, "
                    + "createdAtMillis, completedAtMillis) VALUES (3, 'Tesis', 'notas', 0, 0, 2, 1761000000000, 0)");
            db.setVersion(14);
        } finally {
            db.close();
        }
        // Sanidad: la base de partida tiene las 8 tablas v14 y ninguna columna v15.
        SQLiteDatabase check = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            assertEquals(14, check.getVersion());
            for (String table : V14_TABLES) {
                assertTrue(table + " exists in v14", tableExists(check, table));
            }
        } finally {
            check.close();
        }
    }

    private static ContentValues entry(int id, String title, long startMillis, boolean done, int projectId,
                                       int isTemplate) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("title", title);
        v.put("description", "");
        v.put("startMillis", startMillis);
        v.put("endMillis", startMillis);
        v.put("notifications", NO_NOTIFICATIONS);
        v.put("repetition", 0);
        v.put("repetitionInterval", 1);
        v.put("repetitionDays", 0);
        v.put("duration", 0);
        v.put("targetMinutes", 0);
        v.put("isDone", done ? 1 : 0);
        v.put("isTemplate", isTemplate);
        v.put("templateId", 0);
        v.put("materializedUntilMillis", 0);
        v.put("isDismissed", 0);
        v.put("projectId", projectId);
        v.put("hiddenInCalendar", 0);
        v.put("type", 2);
        return v;
    }

    private static boolean tableExists(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[]{table})) {
            return c.moveToFirst();
        }
    }

    private static int count(SupportSQLiteDatabase db, String table) {
        return (int) longOf(db, "SELECT COUNT(*) FROM `" + table + "`");
    }

    private static long longOf(SupportSQLiteDatabase db, String sql) {
        try (Cursor c = db.query(sql)) {
            assertTrue(sql, c.moveToFirst());
            return c.getLong(0);
        }
    }
}
