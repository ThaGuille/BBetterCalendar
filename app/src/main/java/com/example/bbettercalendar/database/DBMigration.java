package com.example.bbettercalendar.database;

import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.TagSeeds;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.firebase.AppCheckInstaller;
import com.example.bbettercalendar.social.invite.InstallReferrerReader;
import com.example.bbettercalendar.social.push.FriendsNotificationsCleaner;
import com.example.bbettercalendar.social.push.PushTokenRegistrar;
import com.example.bbettercalendar.social.stats.StatsPublisher;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.usage.UsageSnapshotter;
import com.example.bbettercalendar.usage.limits.UsageLimitScheduler;
import com.example.bbettercalendar.usage.limits.WarnedTodayStore;

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class DBMigration extends Application {

    // Inyectados por Hilt (spec di-threading-consolidation): @HiltAndroidApp también soporta
    // inyección de campos en la propia clase Application, disponible antes de onCreate().
    @Inject AppRuleDAO appRuleDao;
    @Inject @IoExecutor ExecutorService ioExecutor;
    // F2-u6: invitación por Install Referrer; solo con FEATURE_SOCIAL y una vez por instalación.
    @Inject InstallReferrerReader installReferrerReader;
    // F3-u4: publicación de stats/ (03 §9). Lazy: sin FEATURE_SOCIAL (release) nunca se crea ni toca Firebase.
    @Inject Lazy<StatsPublisher> statsPublisher;
    // F5-u6: token FCM en users/{uid}.fcmTokens al tener sesión y perfil. Lazy por lo mismo que el anterior.
    @Inject Lazy<PushTokenRegistrar> pushTokenRegistrar;
    // L-u7: quita los avisos de Amigos de la bandeja al perder la sesion.
    @Inject Lazy<FriendsNotificationsCleaner> friendsNotificationsCleaner;
    // mejora-visual u1: histórico diario de uso por app (app_usage_daily) en cada arranque en frío.
    @Inject UsageSnapshotter usageSnapshotter;

    @Override
    public void onCreate() {
        // L-u3: App Check antes de cualquier cliente de Firebase (antes de la inyeccion de Hilt); sin FEATURE_SOCIAL o con emulador no hace nada.
        AppCheckInstaller.installOnce(this);
        super.onCreate();
        // App solo oscura (plan migracion-visual-stitch, u5): los tokens oscuros viven en values/ y
        // Theme.BBetter hereda de DayNight. Forzarlo aquí, antes de crear cualquier actividad, hace
        // que AppCompat aplique el modo noche al adjuntar la actividad (sin recrearla).
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        NotificationChannels.createAll(this);
        armUsageLimitScheduler();
        // Fuera del hilo principal (regla #3): snapshotAsync encola el volcado en el executor de IO.
        usageSnapshotter.snapshotAsync();
        installReferrerReader.readOnce();
        if (SocialFeature.isAccountEnabled()) {
            statsPublisher.get().start();
            pushTokenRegistrar.get().start();
            friendsNotificationsCleaner.get().start();
        }
    }

    // Arma el chequeo periódico de límites en cada arranque en frío. BootReceiver hace lo mismo
    // tras un reinicio del dispositivo. Corre fuera del hilo principal, ya que arm() lee app_rule
    // (regla #3), en el executor de IO compartido (@IoExecutor -- no se cierra: es @Singleton).
    private void armUsageLimitScheduler() {
        ioExecutor.execute(() -> {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            WarnedTodayStore warnedStore = new WarnedTodayStore(this);
            new UsageLimitScheduler(this, alarmManager, appRuleDao, warnedStore).arm();
            com.example.bbettercalendar.notifications.nudge.DailyNudgeScheduler.rescheduleAll(this);
        });
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

    // Phase 3 (spec projects-mvp): tabla `project` + columna `projectId` en calendarEntry —
    // los items de un proyecto son CalendarEntry (type task) normales, no una tabla propia
    // (decisión de datos del roadmap). Aditiva -> el histórico se conserva (regla #6).
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS project (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT, notes TEXT, " +
                    "status INTEGER NOT NULL DEFAULT 0, softDeadlineMillis INTEGER NOT NULL DEFAULT 0, " +
                    "colorIndex INTEGER NOT NULL DEFAULT 0, createdAtMillis INTEGER NOT NULL DEFAULT 0, " +
                    "completedAtMillis INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN projectId INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Phase 4 (spec focus-attribution): objetivo de minutos en la tarea + atribución de la sesión.
    //   calendarEntry.targetMinutes -> objetivo de concentración (0 = sin objetivo)
    //   focus_event.entryId         -> tarea/item a la que se atribuye la sesión (0 = genérica)
    // Aditiva (sólo ADD COLUMN) -> el histórico se conserva (regla #6).
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN targetMinutes INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_event ADD COLUMN entryId INTEGER NOT NULL DEFAULT 0");
        }
    };

    // spec recurrence-calendar-visibility: flag por fila para sacarla de la pantalla de Calendario
    // sin sacarla de las listas de Home. Las filas ya existentes de una serie recurrente (plantillas,
    // sus ocurrencias y las repetitivas legacy aún sin adoptar) se marcan ocultas de golpe: el
    // comportamiento nuevo por defecto se aplica también a lo que ya había, que es justo el
    // calendario saturado que motivó el cambio. Las tareas sueltas y los eventos no se tocan.
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE calendarEntry ADD COLUMN hiddenInCalendar INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE calendarEntry SET hiddenInCalendar = 1 " +
                    "WHERE isTemplate = 1 OR templateId != 0 " +
                    "OR (repetition != 0 AND type = 2)");
        }
    };

    // mejora-visual u1: datos nuevos de la mejora visual. Aditiva (ADD COLUMN ... NOT NULL DEFAULT 0 y
    // CREATE TABLE IF NOT EXISTS) -> no se pierde ninguna fila (regla #6). El SQL de app_usage_daily es
    // el createSql que Room exporta en app/schemas/.../15.json (si no coincide, la validación de Room
    // al abrir la BD falla). Rellenos con el instante de la migración:
    //   app_rule.limitSinceMillis        -> las reglas que ya tienen límite (no se sabe desde cuándo)
    //   calendarEntry.completedAtMillis  -> las filas ya hechas: su startMillis si lo tienen, si no ahora
    // focus_event.elapsedMin / pauseCount se quedan a 0 en el histórico: el dato no existía.
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            migrate14To15(database, System.currentTimeMillis());
        }
    };

    static void migrate14To15(SupportSQLiteDatabase database, long nowMillis) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `app_usage_daily` (`day` TEXT NOT NULL, " +
                "`packageName` TEXT NOT NULL, `minutes` INTEGER NOT NULL, " +
                "PRIMARY KEY(`day`, `packageName`))");
        database.execSQL("ALTER TABLE app_rule ADD COLUMN limitSinceMillis INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE focus_event ADD COLUMN elapsedMin INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE focus_event ADD COLUMN pauseCount INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE calendarEntry ADD COLUMN completedAtMillis INTEGER NOT NULL DEFAULT 0");
        database.execSQL("UPDATE app_rule SET limitSinceMillis = ? WHERE dailyLimitMinutes > 0",
                new Object[]{nowMillis});
        database.execSQL("UPDATE calendarEntry SET completedAtMillis = " +
                        "CASE WHEN startMillis > 0 THEN startMillis ELSE ? END WHERE isDone = 1",
                new Object[]{nowMillis});
    }

    // rediseno-proyectos (DB v16, spec §4): secciones, subtareas, etiquetas y áreas, y campos nuevos de
    // proyecto y de tarea. Aditiva (ADD COLUMN ... NOT NULL DEFAULT y CREATE TABLE IF NOT EXISTS) -> no se
    // pierde ninguna fila (regla #6). Los CREATE son los createSql que Room exporta en
    // app/schemas/.../16.json (si no coinciden, la validación de Room al abrir la BD falla). Rellenos:
    //   project_section -> una sección por proyecto existente, en la posición 0, con el nombre «Tareas»
    //                      en el idioma del móvil en ese momento (decisión del usuario d8)
    //   calendarEntry.sectionId -> todas las filas de ese proyecto (de cualquier tipo, plantillas
    //                      incluidas) apuntan a esa sección; las sueltas (projectId 0) se quedan a 0
    //   tag             -> las 4 etiquetas y las 4 áreas predefinidas (TagSeeds), en el mismo idioma
    // Es una fábrica y no un campo estático porque los nombres sembrados necesitan un Context.
    public static Migration migration15To16(final Context context) {
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        return new Migration(15, 16) {
            @Override
            public void migrate(SupportSQLiteDatabase database) {
                migrate15To16(database, TagSeeds.sectionName(app), TagSeeds.build(app));
            }
        };
    }

    static void migrate15To16(SupportSQLiteDatabase database, String sectionName, List<Tag> seeds) {
        database.execSQL("ALTER TABLE project ADD COLUMN iconKey TEXT NOT NULL DEFAULT '"
                + Project.DEFAULT_ICON_KEY + "'");
        database.execSQL("ALTER TABLE project ADD COLUMN areaId INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE calendarEntry ADD COLUMN priority INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE calendarEntry ADD COLUMN tagId INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE calendarEntry ADD COLUMN sectionId INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE calendarEntry ADD COLUMN blockOnFocus INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE calendarEntry ADD COLUMN completionNote TEXT");
        database.execSQL("CREATE TABLE IF NOT EXISTS `project_section` (`id` INTEGER PRIMARY KEY "
                + "AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `name` TEXT, `position` INTEGER NOT NULL)");
        database.execSQL("CREATE TABLE IF NOT EXISTS `task_subtask` (`id` INTEGER PRIMARY KEY "
                + "AUTOINCREMENT NOT NULL, `entryId` INTEGER NOT NULL, `title` TEXT, `done` INTEGER NOT NULL, "
                + "`position` INTEGER NOT NULL)");
        database.execSQL("CREATE TABLE IF NOT EXISTS `tag` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + "`name` TEXT, `iconKey` TEXT, `colorKey` TEXT, `kind` INTEGER NOT NULL, "
                + "`position` INTEGER NOT NULL)");

        database.execSQL("INSERT INTO project_section (projectId, name, position) "
                + "SELECT id, ?, 0 FROM project ORDER BY id", new Object[]{sectionName});
        database.execSQL("UPDATE calendarEntry SET sectionId = (SELECT s.id FROM project_section s "
                + "WHERE s.projectId = calendarEntry.projectId AND s.position = 0) "
                + "WHERE projectId != 0 AND projectId IN (SELECT projectId FROM project_section)");
        insertTags(database, seeds);
    }

    // Inserta las etiquetas y áreas predefinidas (migración 15 -> 16 y base nueva).
    static void insertTags(SupportSQLiteDatabase database, List<Tag> tags) {
        for (Tag t : tags) {
            database.execSQL("INSERT INTO tag (name, iconKey, colorKey, kind, position) VALUES (?, ?, ?, ?, ?)",
                    new Object[]{t.name, t.iconKey, t.colorKey, t.kind, t.position});
        }
    }

    // rediseno-proyectos (DB v16): una instalación nueva nace con las mismas etiquetas y áreas que deja
    // la migración, con los nombres en el idioma del Context que recibe el builder (nunca uno fijo).
    // onCreate solo corre al crear la base (las tablas ya existen y están vacías).
    public static RoomDatabase.Callback seedCallback(final Context context) {
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        return new RoomDatabase.Callback() {
            @Override
            public void onCreate(SupportSQLiteDatabase db) {
                insertTags(db, TagSeeds.build(app));
            }
        };
    }
}
