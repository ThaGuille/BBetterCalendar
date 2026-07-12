# System — Data model (`stats/` + `database/`)

**Last verified:** 2026-07-12 (DB v12) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The Room persistence layer: `AppDatabase` (8 entities, version 12) plus every entity/DAO under
`stats/`. This is the **contract hub** — sibling system docs (`app-limits.md`,
`progress-screen.md`, `pomodoro-timer.md`, `calendar.md`, `startup-config.md`, `projects.md`)
link into the anchors below instead of re-describing entity shape.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Application class | `.database.DBMigration` (manifest `android:name`, `@HiltAndroidApp`) — creates notification channels + arms the usage-limit alarm on cold start |
| DB file | Room `"eventDB"`, singleton via `AppDatabase.getDatabase(context)` (double-checked locking, no Hilt for the singleton itself — `ConfigurationDatabaseModule` wraps it for injection) |

## Files
| Class | Path | Role |
|---|---|---|
| `AppDatabase` | `database/AppDatabase.java` | Room `@Database`, v12, 8 entities, `fallbackToDestructiveMigration()` + 5 real migrations registered |
| `DBMigration` | `database/DBMigration.java` | `Application` class + migration `Migration` constants (misleading name: it's both) |
| `DBConverter` | `database/DBConverter.java` | `@TypeConverter`s: `Calendar`↔JSON (Gson), `boolean[]`↔JSON (Gson), `Location`→null (stub) |
| `Stats` / `StatsDAO` | `stats/Stats.java`, `StatsDAO.java` | Single-row lifetime + today counters (time studied, tasks, streaks, fails) |
| `DailyStat` / `DailyStatDAO` | `stats/DailyStat.java`, `DailyStatDAO.java` | One row per ISO day — history that feeds Progress charts |
| `FocusEvent` / `FocusEventDAO` | `stats/FocusEvent.java`, `FocusEventDAO.java` | One row per completed session / fail, real timestamp — feeds the by-hour charts |
| `AppRule` / `AppRuleDAO` | `stats/AppRule.java`, `AppRuleDAO.java` | One row per app the user tracks — usage limits + enforcement flags |
| `ConsentRecord` / `ConsentRecordDAO` | `stats/ConsentRecord.java`, `ConsentRecordDAO.java` | Affirmative-consent acknowledgement (usage access, accessibility blocking) |
| `Configuration` / `ConfigurationDAO` | `configuration/Configuration.java`, `ConfigurationDAO.java` | Timer/rest/cycle settings + notification-permission ask tracking (owned by `startup-config.md`, listed here since it's a DB entity) |
| `CalendarEntry` / `CalendarEntryDAO` | `calendarEntries/CalendarEntry.java`, `CalendarEntryDAO.java` | Events/tasks/reminders (owned by `calendar.md`, listed here since it's a DB entity) |
| `Project` / `ProjectDAO` | `projects/Project.java`, `ProjectDAO.java` | Project grouping over `CalendarEntry` items via `projectId` (owned by `projects.md`, listed here since it's a DB entity) |

## Schema history (v6 → v12)

| Version | Migration | What changed |
|---|---|---|
| 6 → 7 | `MIGRATION_6_7` | Adds `startMillis`/`endMillis` long mirrors to `calendarEntry` (pre-existing rows default 0; `EventBuilder` populates both going forward) |
| 7 → 8 | `MIGRATION_7_8` | Adds `notificationPermissionAskCount` / `notificationPermissionLastAskedMillis` to `configuration` |
| 8 → 9 | *(none — destructive)* | `DailyStat` + `FocusEvent` tables introduced; no migration was written, so upgraders on v8 lost data here |
| 9 → 10 | `MIGRATION_9_10` | Adds `app_rule` + `consent_record` tables (additive `CREATE TABLE IF NOT EXISTS` only — v9 history preserved) |
| 10 → 11 | `MIGRATION_10_11` | Adds 6 additive columns to `calendarEntry` for recurring tasks: `isTemplate`, `templateId`, `repetitionInterval`, `repetitionDays`, `materializedUntilMillis`, `isDismissed` (all `NOT NULL DEFAULT`, `ALTER TABLE ADD COLUMN` — no data loss) |
| 11 → 12 | `MIGRATION_11_12` | Adds `project` table (`CREATE TABLE IF NOT EXISTS`) + additive `projectId` column on `calendarEntry` (`NOT NULL DEFAULT 0` — no data loss) |

`fallbackToDestructiveMigration()` is still active for any version gap without a registered
`Migration` — see CLAUDE.md rule #6 before bumping `@Database(version)`.

## Per-entity readers/writers (contract table)

| Entity | Written by | Read by |
|---|---|---|
| `Stats` | `HomeViewModel` (`addFails`, `completeTimer`), `SplashActivity`/`InitialConfiguration` (`resetDailyStats`, streak update) | `HomeViewModel`, `SplashActivity`/`InitialConfiguration`, `ProgressViewModel` (today merged as trailing chart point) |
| `DailyStat` | `SplashActivity`/`InitialConfiguration` (`persistDailyStat`, upsert before daily reset) | `ProgressViewModel` (chart history) |
| `FocusEvent` | `HomeViewModel.logFocusEvent()` (`TYPE_FOCUS` on `completeTimer`, `TYPE_FAIL` on `addFails`; `TYPE_TASK` reserved, never emitted) | `ProgressViewModel` (by-hour buckets) |
| `AppRule` | `ProgressViewModel` (`setTracked`, `setDailyLimit`, `setEnforceAtLimit` — see `app-limits.md`) | `ProgressViewModel`, `UsageLimitChecker`/`UsageLimitScheduler` (`getLimited`), `BlockDecisionEngine` (`observeEnforced`), `DBMigration` (arms scheduler on cold start) |
| `ConsentRecord` | `UsageDisclosureDialog`, `AccessibilityDisclosureDialog` | `ProgressViewModel`, `ProgressFragment`/blocking flow (gate before Settings deep-link) |
| `Configuration` | `ConfigurationManager.updateConfiguration()`, `PermissionGate` (ask-count/timestamp) | `ConfigurationManager` (cached in memory), `HomeViewModel`/`HomeFragment` (timer/rest/cycle values) |
| `CalendarEntry` | `AddEventActivity` (via `EventBuilder.build()`), `HomeViewModel` (`quickAddTask` via `EventBuilder`; `setTaskDone` re-reads via `getEventById` + `update`), `RecurrenceMaterializer` (occurrence rows via `EventBuilder.build()`, template `materializedUntilMillis` updates, `dismissSeriesBefore`), `ProjectDetailViewModel` (`addItem` via `EventBuilder` with `projectId` set, `setItemDone` re-read + `update`, `deleteItemsByProject` cascade) | `CalendarFragmentMonth`/`Week` via `CalendarViewModel`, `HomeViewModel` (today's `TYPE_TASK` via `getEventsBetween` + overdue via `getUndoneTasksBefore`, both `isTemplate = 0`), `EventReminderScheduler`, `BootReceiver`, `RecurrenceMaterializer` (`getTemplates`, `getLegacyRepeatingRows`), `ProjectDetailViewModel`/`ProjectsViewModel` (`observeItemsByProject`, `ProjectDAO`'s cross-table done/total counts) |
| `Project` | `ProjectsViewModel` (`createProject`), `ProjectDetailViewModel` (`updateHeader`, `updateDeadline`, `completeProject`, `deleteProject`) | `ProjectsViewModel` (`observeAll()` + per-project counts), `ProjectDetailViewModel` (`observeById`) — see `projects.md` |

**Cross-system coupling to know:** `UsageLimitChecker` (alarm poll, warn-only) and
`BlockDecisionEngine` (live per-foreground-event, enforcement) each independently read `AppRule` +
`UsageStatsRepository` and compute "is this app over its limit" — they do not share a cache. See
`app-limits.md` for the two pipelines.

## Invariants & gotchas

- **Destructive fallback is live.** Any `@Database(version)` bump without a matching `Migration` wipes every table, not just the one you touched (rule #6). The v8→v9 gap above already did this once.
- **`DBMigration` is the Application class**, not just a migration holder — its `onCreate()` runs `NotificationChannels.createAll()` and arms the usage-limit alarm scheduler off the main thread before any DAO is safe to touch on the UI thread.
- **`AppRule.enforceAtLimit` is stored in the physical column `instantBlock`** (`@ColumnInfo(name = "instantBlock")`) — the instant-block feature was dropped and the column repurposed to avoid a schema bump. Don't be misled by the column name in raw SQL/dumps.
- **`ConfigurationManager.getConfiguration()` can return null** if the async load from `ConfigurationDAO` hasn't completed yet — callers on `ui/home` handle this defensively.
- **`Stats` is a true singleton row** (no keyed lookup) — `getStats()` assumes exactly one row exists, which `SplashActivity`/`InitialConfiguration` guarantee by inserting one if missing.

## History

| Date | Change | Spec |
|---|---|---|
| 2026-06-28 | `DailyStat` + `FocusEvent` added (DB v8→v9, no migration written) | `.claude/specs/archive/progress-charts-mvp/proposal.md` |
| 2026-06-29 | `AppRule` + `ConsentRecord` added (DB v9→v10, `MIGRATION_9_10`) | `.claude/specs/archive/progress-phase2-usage/proposal.md` |
| 2026-07-07 | Home surfaces today's `TYPE_TASK` entries — additive `getUndoneTasksBefore` `@Query` (overdue undone tasks), `HomeViewModel` now a `CalendarEntry` reader/writer; no schema change (still v10) | `.claude/specs/archive/tasks-home-today/proposal.md` |
| 2026-07-12 | Recurring tasks: `calendarEntry` +6 columns (DB v10→v11, `MIGRATION_10_11`), new `RecurrenceMaterializer` reader/writer | `.claude/specs/archive/tasks-recurrence/proposal.md` |
| 2026-07-12 | Projects MVP: new `Project` entity (DB v11→v12, `MIGRATION_11_12`), `calendarEntry` +`projectId` column | `.claude/specs/archive/projects-mvp/proposal.md` |
