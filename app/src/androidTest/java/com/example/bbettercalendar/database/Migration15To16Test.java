package com.example.bbettercalendar.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectSection;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.TaskSubtask;
import com.example.bbettercalendar.stats.AppRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * rediseno-proyectos: migración real 15 -> 16 (regla #6). Misma técnica que Migration14To15Test, sin
 * dependencia nueva: crea una base v15 con los CREATE exactos que Room exportó en
 * app/schemas/.../15.json, mete filas en TODAS las tablas, y la abre con Room a v16 solo con
 * {@link DBMigration#migration15To16(Context)} (la misma fábrica que usa la app, con el contexto de la
 * app de pruebas) y SIN fallbackToDestructiveMigration: si el SQL de la migración no deja el esquema
 * que Room espera, la validación de Room lanza al abrir; si la migración borrara algo, las filas no
 * estarían. Los nombres sembrados siguen el idioma del dispositivo: el AVD está en inglés.
 */
@RunWith(AndroidJUnit4.class)
public class Migration15To16Test {

    private static final String DB_NAME = "migration-15-16-test.db";
    private static final String FRESH_DB_NAME = "fresh-16-test.db";

    // Los CREATE de 15.json (createSql con ${TABLE_NAME} sustituido) + la tabla de Room.
    private static final String[] V15_SCHEMA = {
            "CREATE TABLE IF NOT EXISTS `calendarEntry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `description` TEXT, `limitDate` TEXT, `startDayAndHour` TEXT, `endDayAndHour` TEXT, `startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, `notifications` TEXT, `repetition` INTEGER NOT NULL, `repetitionInterval` INTEGER NOT NULL, `repetitionDays` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `targetMinutes` INTEGER NOT NULL, `isDone` INTEGER NOT NULL, `completedAtMillis` INTEGER NOT NULL, `isTemplate` INTEGER NOT NULL, `templateId` INTEGER NOT NULL, `materializedUntilMillis` INTEGER NOT NULL, `isDismissed` INTEGER NOT NULL, `projectId` INTEGER NOT NULL, `hiddenInCalendar` INTEGER NOT NULL, `type` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `stats` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `totalTimeStudied` INTEGER NOT NULL, `todayTimeStudied` INTEGER NOT NULL, `totalTasksDone` INTEGER NOT NULL, `todayTasksDone` INTEGER NOT NULL, `maxStreak` INTEGER NOT NULL, `currentStreak` INTEGER NOT NULL, `lastDayStreak` TEXT, `totalFails` INTEGER NOT NULL, `todayFails` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `configuration` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `homeTimerTime` INTEGER NOT NULL, `homeRestTime` INTEGER NOT NULL, `homeNumberOfCycles` INTEGER NOT NULL, `homeIsRestEnabled` INTEGER NOT NULL, `homeIsInfiniteCycleEnabled` INTEGER NOT NULL, `homeIsAutoCycle` INTEGER NOT NULL, `homeIsPauseEnabled` INTEGER NOT NULL, `homeIsAlarmEnabled` INTEGER NOT NULL, `homeIsVibrationEnabled` INTEGER NOT NULL, `country` TEXT, `notificationPermissionAskCount` INTEGER NOT NULL, `notificationPermissionLastAskedMillis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `daily_stat` (`day` TEXT NOT NULL, `focusMinutes` INTEGER NOT NULL, `fails` INTEGER NOT NULL, `tasksDone` INTEGER NOT NULL, `phoneUsageMinutes` INTEGER NOT NULL, PRIMARY KEY(`day`))",
            "CREATE TABLE IF NOT EXISTS `focus_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `type` INTEGER NOT NULL, `durationMin` INTEGER NOT NULL, `entryId` INTEGER NOT NULL, `elapsedMin` INTEGER NOT NULL, `pauseCount` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `app_rule` (`packageName` TEXT NOT NULL, `tracked` INTEGER NOT NULL, `dailyLimitMinutes` INTEGER NOT NULL, `warnBeforeMinutes` INTEGER NOT NULL, `instantBlock` INTEGER NOT NULL, `blockedToday` INTEGER NOT NULL, `blockStyle` INTEGER NOT NULL, `limitSinceMillis` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
            "CREATE TABLE IF NOT EXISTS `consent_record` (`key` TEXT NOT NULL, `acceptedAt` INTEGER NOT NULL, `disclosureVersion` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS `project` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `notes` TEXT, `status` INTEGER NOT NULL, `softDeadlineMillis` INTEGER NOT NULL, `colorIndex` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, `completedAtMillis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `app_usage_daily` (`day` TEXT NOT NULL, `packageName` TEXT NOT NULL, `minutes` INTEGER NOT NULL, PRIMARY KEY(`day`, `packageName`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e34d7358d0504914f69a9d1e869d880a')",
    };

    private static final String[] V15_TABLES = {"calendarEntry", "stats", "configuration", "daily_stat",
            "focus_event", "app_rule", "consent_record", "project", "app_usage_daily"};

    // Proyectos de la base v15: 2 con tareas y 1 sin ninguna.
    private static final int THESIS = 3;
    private static final int PHYSICS = 5;
    private static final int EMPTY = 8;

    // calendarEntry de la base v15.
    private static final int THESIS_DONE = 1;        // tarea hecha (con completedAtMillis)
    private static final int THESIS_PENDING = 2;     // tarea con meta, recordatorio y nota
    private static final int THESIS_TEMPLATE = 3;    // plantilla de una tarea que se repite cada semana
    private static final int PHYSICS_DISMISSED = 4;  // tarea retirada (isDismissed)
    private static final int PHYSICS_PENDING = 5;
    private static final int LOOSE_TASK = 6;         // tarea suelta (projectId 0)
    private static final int LOOSE_EVENT = 7;        // evento suelto
    private static final int OCCURRENCE = 8;         // ocurrencia suelta de la plantilla

    private static final long START = 1_763_990_000_000L;
    private static final long COMPLETED = 1_763_000_000_000L;
    private static final String NO_NOTIFICATIONS = "[false,false,false,false,false,false,false]";
    private static final String REMINDER_15_MIN = "[false,false,true,false,false,false,false]";

    private Context context;
    private AppDatabase room;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DB_NAME);
        context.deleteDatabase(FRESH_DB_NAME);
        createV15WithRows();
    }

    @After
    public void tearDown() {
        if (room != null) {
            room.close();
        }
        context.deleteDatabase(DB_NAME);
        context.deleteDatabase(FRESH_DB_NAME);
    }

    // ------------------------------------------------------------------------------- tests

    @Test
    public void roomOpensTheMigratedDatabaseAndItsSchemaValidates() {
        openV16();
        // getWritableDatabase() corre la migración y la validación de esquema de Room: si el SQL no
        // coincide con las entidades lanza IllegalStateException ("Migration didn't properly handle").
        SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
        assertEquals(16, db.getVersion());
        for (String table : new String[]{"project_section", "task_subtask", "tag"}) {
            assertTrue(table + " exists in v16", longOf(db,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'") == 1);
        }
    }

    @Test
    public void everyV15RowAndValueSurvives() {
        openV16();
        SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
        assertEquals(8, count(db, "calendarEntry"));
        assertEquals(1, count(db, "stats"));
        assertEquals(1, count(db, "configuration"));
        assertEquals(1, count(db, "daily_stat"));
        assertEquals(3, count(db, "focus_event"));
        assertEquals(2, count(db, "app_rule"));
        assertEquals(1, count(db, "consent_record"));
        assertEquals(3, count(db, "project"));
        assertEquals(2, count(db, "app_usage_daily"));

        // Las columnas viejas conservan su valor, leído a través de las entidades v16.
        CalendarEntry done = room.eventDao().getEventById(THESIS_DONE);
        assertEquals("Capítulo 1", done.getTitle());
        assertTrue(done.isDone());
        assertEquals(COMPLETED, done.getCompletedAtMillis());
        assertEquals(THESIS, done.getProjectId());

        CalendarEntry pending = room.eventDao().getEventById(THESIS_PENDING);
        assertEquals("Capítulo 3", pending.getTitle());
        assertEquals("una nota", pending.getDescription());
        assertEquals(START, pending.getStartMillis());
        assertEquals(START + 3_600_000L, pending.getEndMillis());
        assertEquals(60, pending.getTargetMinutes());
        assertEquals(3_600_000, pending.getDuration());
        assertTrue(Arrays.equals(new boolean[]{false, false, true, false, false, false, false},
                pending.getNotifications()));
        assertFalse(pending.isDone());

        CalendarEntry template = room.eventDao().getEventById(THESIS_TEMPLATE);
        assertTrue(template.isTemplate());
        assertEquals(2, template.getRepetition());
        assertEquals(1 << 2, template.getRepetitionDays());
        assertEquals(START + 1_000L, template.getMaterializedUntilMillis());
        assertTrue(template.isHiddenInCalendar());

        assertTrue(room.eventDao().getEventById(PHYSICS_DISMISSED).isDismissed());
        assertEquals(THESIS_TEMPLATE, room.eventDao().getEventById(OCCURRENCE).getTemplateId());
        assertEquals(1, room.eventDao().getEventById(LOOSE_EVENT).getType());

        Project thesis = room.projectDao().getById(THESIS);
        assertEquals("Tesis", thesis.name);
        assertEquals("notas", thesis.notes);
        assertEquals(Project.STATUS_ACTIVE, thesis.status);
        assertEquals(START + 86_400_000L, thesis.softDeadlineMillis);
        assertEquals(2, thesis.colorIndex);
        assertEquals(Project.STATUS_ARCHIVED, room.projectDao().getById(EMPTY).status);

        AppRule limited = room.appRuleDao().getByPackage("com.limited");
        assertNotNull(limited);
        assertEquals(45, limited.dailyLimitMinutes);
        assertEquals(1_762_000_000_000L, limited.limitSinceMillis);
        assertEquals(25, room.focusEventDao().getRange(0L, Long.MAX_VALUE).get(0).durationMin);
        assertEquals(THESIS_PENDING, room.focusEventDao().getRange(0L, Long.MAX_VALUE).get(0).entryId);
        assertEquals(1, room.dailyStatDao().getRange("2026-11-01", "2026-11-30").size());
        assertEquals(1500000, room.configurationDao().getConfiguration().getHomeTimerTime());
        assertEquals(300000, room.configurationDao().getConfiguration().getHomeRestTime());
        assertEquals(40, longOf(db, "SELECT minutes FROM app_usage_daily WHERE packageName = 'com.a'"));
        assertEquals(12, longOf(db, "SELECT totalTasksDone FROM stats"));
    }

    @Test
    public void eachProjectGetsOneTasksSectionHoldingAllItsEntries() {
        openV16();
        SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
        assertEquals(3, count(db, "project_section"));

        int thesisSection = onlySectionOf(THESIS);
        int physicsSection = onlySectionOf(PHYSICS);
        onlySectionOf(EMPTY);

        // Todas las filas del proyecto, de cualquier tipo y plantillas incluidas, van a su sección.
        assertEquals(thesisSection, room.eventDao().getEventById(THESIS_DONE).getSectionId());
        assertEquals(thesisSection, room.eventDao().getEventById(THESIS_PENDING).getSectionId());
        assertEquals(thesisSection, room.eventDao().getEventById(THESIS_TEMPLATE).getSectionId());
        assertEquals(physicsSection, room.eventDao().getEventById(PHYSICS_DISMISSED).getSectionId());
        assertEquals(physicsSection, room.eventDao().getEventById(PHYSICS_PENDING).getSectionId());
        // Lo que no es de ningún proyecto se queda sin sección.
        assertEquals(0, room.eventDao().getEventById(LOOSE_TASK).getSectionId());
        assertEquals(0, room.eventDao().getEventById(LOOSE_EVENT).getSectionId());
        assertEquals(0, room.eventDao().getEventById(OCCURRENCE).getSectionId());
    }

    @Test
    public void newColumnsHoldTheirDefaults() {
        openV16();
        SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
        for (int id = 1; id <= 8; id++) {
            CalendarEntry e = room.eventDao().getEventById(id);
            assertEquals("priority of " + id, CalendarEntry.PRIORITY_NONE, e.getPriority());
            assertEquals("tagId of " + id, 0, e.getTagId());
            assertFalse("blockOnFocus of " + id, e.isBlockOnFocus());
            assertNull("completionNote of " + id, e.getCompletionNote());
        }
        for (int id : new int[]{THESIS, PHYSICS, EMPTY}) {
            Project p = room.projectDao().getById(id);
            assertEquals("box", p.iconKey);
            assertEquals(0, p.areaId);
        }
        assertEquals(0, count(db, "task_subtask"));
    }

    @Test
    public void theMigrationSeedsFourTagsAndFourAreas() {
        openV16();
        List<Tag> all = room.tagDao().getAll();
        assertEquals(8, all.size());
        List<Tag> tags = room.tagDao().getByKind(Tag.KIND_TAG);
        List<Tag> areas = room.tagDao().getByKind(Tag.KIND_AREA);
        assertEquals(4, tags.size());
        assertEquals(4, areas.size());
        assertEquals(new HashSet<>(Arrays.asList("pen", "book", "bag", "phone")), iconsOf(tags));
        assertEquals(new HashSet<>(Arrays.asList("cap", "code", "home", "heart")), iconsOf(areas));
        // En el idioma del dispositivo (el AVD está en inglés).
        assertEquals("writing", tags.get(0).name);
        assertEquals("Studies", areas.get(0).name);
        assertEquals("primary", tags.get(0).colorKey);
        assertEquals("", areas.get(0).colorKey);
    }

    @Test
    public void aFreshDatabaseIsBornWithTheSameEightSeeds() {
        room = AppDatabase.builder(context, FRESH_DB_NAME).allowMainThreadQueries().build();
        List<Tag> tags = room.tagDao().getByKind(Tag.KIND_TAG);
        List<Tag> areas = room.tagDao().getByKind(Tag.KIND_AREA);
        assertEquals(4, tags.size());
        assertEquals(4, areas.size());
        assertEquals(new HashSet<>(Arrays.asList("pen", "book", "bag", "phone")), iconsOf(tags));
        assertEquals(new HashSet<>(Arrays.asList("cap", "code", "home", "heart")), iconsOf(areas));
        assertEquals("writing", tags.get(0).name);
        assertEquals("Personal", areas.get(3).name);
        assertEquals(0, room.projectSectionDao().countByProject(1));
    }

    @Test
    public void theNewDaosWorkOnTheMigratedDatabase() {
        openV16();
        int firstSection = onlySectionOf(THESIS);

        // Una segunda sección no cambia cuál es la primera.
        long defense = room.projectSectionDao().insert(new ProjectSection(THESIS, "Defensa", 1));
        assertEquals(firstSection, room.projectSectionDao().getFirstOfProject(THESIS).id);
        assertEquals(2, room.projectSectionDao().getByProject(THESIS).size());
        assertEquals("Defensa", room.projectSectionDao().getById((int) defense).name);

        // Subtareas: por tarea y en orden; completar la tarea las marca; borrar la tarea las borra.
        room.taskSubtaskDao().insertAll(Arrays.asList(
                new TaskSubtask(THESIS_PENDING, "Esquema", true, 0),
                new TaskSubtask(THESIS_PENDING, "Borrador", false, 1)));
        List<TaskSubtask> subtasks = room.taskSubtaskDao().getByEntry(THESIS_PENDING);
        assertEquals(Arrays.asList("Esquema", "Borrador"), Arrays.asList(subtasks.get(0).title, subtasks.get(1).title));
        room.taskSubtaskDao().setAllDone(THESIS_PENDING, true);
        assertTrue(room.taskSubtaskDao().getByEntry(THESIS_PENDING).get(1).done);
        room.taskSubtaskDao().deleteByEntry(THESIS_PENDING);
        assertTrue(room.taskSubtaskDao().getByEntry(THESIS_PENDING).isEmpty());

        // Eliminar una etiqueta deja sus tareas sin etiqueta.
        Tag writing = room.tagDao().getByKind(Tag.KIND_TAG).get(0);
        CalendarEntry pending = room.eventDao().getEventById(THESIS_PENDING);
        pending.setTagId(writing.id);
        room.eventDao().update(pending);
        room.tagDao().clearTagFromEntries(writing.id);
        room.tagDao().delete(writing);
        assertEquals(0, room.eventDao().getEventById(THESIS_PENDING).getTagId());
        assertEquals(7, room.tagDao().getAll().size());
    }

    // ----------------------------------------------------------------------------- helpers

    private void openV16() {
        // SIN fallbackToDestructiveMigration: una migración que falte o no cuadre lanza en vez de borrar.
        room = Room.databaseBuilder(context, AppDatabase.class, DB_NAME)
                .addMigrations(DBMigration.migration15To16(context))
                .allowMainThreadQueries()
                .build();
    }

    private int onlySectionOf(int projectId) {
        List<ProjectSection> sections = room.projectSectionDao().getByProject(projectId);
        assertEquals("sections of project " + projectId, 1, sections.size());
        ProjectSection s = sections.get(0);
        assertEquals("Tasks", s.name);
        assertEquals(0, s.position);
        return s.id;
    }

    private static Set<String> iconsOf(List<Tag> tags) {
        Set<String> out = new HashSet<>();
        for (Tag t : tags) {
            out.add(t.iconKey);
        }
        return out;
    }

    private void createV15WithRows() {
        File file = context.getDatabasePath(DB_NAME);
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            assertTrue(dir.mkdirs());
        }
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            for (String sql : V15_SCHEMA) {
                db.execSQL(sql);
            }
            db.execSQL("INSERT INTO project (id, name, notes, status, softDeadlineMillis, colorIndex, "
                    + "createdAtMillis, completedAtMillis) VALUES (" + THESIS + ", 'Tesis', 'notas', 0, "
                    + (START + 86_400_000L) + ", 2, 1761000000000, 0)");
            db.execSQL("INSERT INTO project (id, name, notes, status, softDeadlineMillis, colorIndex, "
                    + "createdAtMillis, completedAtMillis) VALUES (" + PHYSICS + ", 'Física', '', 0, 0, 1, "
                    + "1761500000000, 0)");
            db.execSQL("INSERT INTO project (id, name, notes, status, softDeadlineMillis, colorIndex, "
                    + "createdAtMillis, completedAtMillis) VALUES (" + EMPTY + ", 'Vacío', NULL, 2, 0, 3, "
                    + "1761600000000, 0)");

            ContentValues done = entry(THESIS_DONE, "Capítulo 1", 2, THESIS);
            done.put("isDone", 1);
            done.put("completedAtMillis", COMPLETED);
            db.insertOrThrow("calendarEntry", null, done);

            ContentValues pending = entry(THESIS_PENDING, "Capítulo 3", 2, THESIS);
            pending.put("description", "una nota");
            pending.put("endMillis", START + 3_600_000L);
            pending.put("targetMinutes", 60);
            pending.put("duration", 3_600_000);
            pending.put("notifications", REMINDER_15_MIN);
            db.insertOrThrow("calendarEntry", null, pending);

            ContentValues template = entry(THESIS_TEMPLATE, "Ensayar", 2, THESIS);
            template.put("isTemplate", 1);
            template.put("repetition", 2);
            template.put("repetitionDays", 1 << 2);
            template.put("materializedUntilMillis", START + 1_000L);
            template.put("hiddenInCalendar", 1);
            db.insertOrThrow("calendarEntry", null, template);

            ContentValues dismissed = entry(PHYSICS_DISMISSED, "Tema 2", 2, PHYSICS);
            dismissed.put("isDismissed", 1);
            db.insertOrThrow("calendarEntry", null, dismissed);
            db.insertOrThrow("calendarEntry", null, entry(PHYSICS_PENDING, "Tema 4", 2, PHYSICS));
            db.insertOrThrow("calendarEntry", null, entry(LOOSE_TASK, "Comprar pan", 2, 0));
            db.insertOrThrow("calendarEntry", null, entry(LOOSE_EVENT, "Reunion", 1, 0));
            ContentValues occurrence = entry(OCCURRENCE, "Ensayar", 2, 0);
            occurrence.put("templateId", THESIS_TEMPLATE);
            occurrence.put("hiddenInCalendar", 1);
            db.insertOrThrow("calendarEntry", null, occurrence);

            db.execSQL("INSERT INTO stats (id, totalTimeStudied, todayTimeStudied, totalTasksDone, todayTasksDone, "
                    + "maxStreak, currentStreak, lastDayStreak, totalFails, todayFails) "
                    + "VALUES (1, 9000000, 1500000, 12, 1, 5, 2, '2026-11-23', 3, 0)");
            db.execSQL("INSERT INTO configuration (id, homeTimerTime, homeRestTime, homeNumberOfCycles, "
                    + "homeIsRestEnabled, homeIsInfiniteCycleEnabled, homeIsAutoCycle, homeIsPauseEnabled, "
                    + "homeIsAlarmEnabled, homeIsVibrationEnabled, country, notificationPermissionAskCount, "
                    + "notificationPermissionLastAskedMillis) VALUES (1, 1500000, 300000, 4, 1, 0, 0, 1, 1, 1, 'ES', 1, 0)");
            db.execSQL("INSERT INTO daily_stat (day, focusMinutes, fails, tasksDone, phoneUsageMinutes) "
                    + "VALUES ('2026-11-23', 50, 1, 2, 120)");
            db.execSQL("INSERT INTO focus_event (id, timestamp, type, durationMin, entryId, elapsedMin, pauseCount) "
                    + "VALUES (1, 1763900000000, 0, 25, " + THESIS_PENDING + ", 25, 1)");
            db.execSQL("INSERT INTO focus_event (id, timestamp, type, durationMin, entryId, elapsedMin, pauseCount) "
                    + "VALUES (2, 1763903000000, 1, 0, 0, 7, 0)");
            db.execSQL("INSERT INTO focus_event (id, timestamp, type, durationMin, entryId, elapsedMin, pauseCount) "
                    + "VALUES (3, 1763906000000, 0, 50, " + PHYSICS_PENDING + ", 50, 0)");
            db.execSQL("INSERT INTO app_rule (packageName, tracked, dailyLimitMinutes, warnBeforeMinutes, "
                    + "instantBlock, blockedToday, blockStyle, limitSinceMillis) "
                    + "VALUES ('com.limited', 1, 45, 5, 1, 0, 0, 1762000000000)");
            db.execSQL("INSERT INTO app_rule (packageName, tracked, dailyLimitMinutes, warnBeforeMinutes, "
                    + "instantBlock, blockedToday, blockStyle, limitSinceMillis) "
                    + "VALUES ('com.free', 1, 0, 5, 0, 0, 0, 0)");
            db.execSQL("INSERT INTO consent_record (`key`, acceptedAt, disclosureVersion) "
                    + "VALUES ('usage_access', 1762000000000, 1)");
            db.execSQL("INSERT INTO app_usage_daily (day, packageName, minutes) VALUES ('2026-11-23', 'com.a', 40)");
            db.execSQL("INSERT INTO app_usage_daily (day, packageName, minutes) VALUES ('2026-11-23', 'com.b', 15)");
            db.setVersion(15);
        } finally {
            db.close();
        }
        // Sanidad: la base de partida tiene las 9 tablas v15 y ninguna tabla v16.
        SQLiteDatabase check = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            assertEquals(15, check.getVersion());
            for (String table : V15_TABLES) {
                assertTrue(table + " exists in v15", tableExists(check, table));
            }
            assertFalse(tableExists(check, "project_section"));
            assertFalse(tableExists(check, "tag"));
        } finally {
            check.close();
        }
    }

    private static ContentValues entry(int id, String title, int type, int projectId) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("title", title);
        v.put("description", "");
        v.put("startMillis", START);
        v.put("endMillis", START);
        v.put("notifications", NO_NOTIFICATIONS);
        v.put("repetition", 0);
        v.put("repetitionInterval", 1);
        v.put("repetitionDays", 0);
        v.put("duration", 0);
        v.put("targetMinutes", 0);
        v.put("isDone", 0);
        v.put("completedAtMillis", 0);
        v.put("isTemplate", 0);
        v.put("templateId", 0);
        v.put("materializedUntilMillis", 0);
        v.put("isDismissed", 0);
        v.put("projectId", projectId);
        v.put("hiddenInCalendar", 0);
        v.put("type", type);
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
