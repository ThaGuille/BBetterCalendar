# System — Data model (`stats/` + `database/`)

**Last verified:** 2026-09-09 (DB v14) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The Room persistence layer: `AppDatabase` (8 entities, version 14) plus every entity/DAO under
`stats/`. This is the **contract hub** — sibling system docs (`app-limits.md`,
`progress-screen.md`, `pomodoro-timer.md`, `calendar.md`, `startup-config.md`, `projects.md`)
link into the anchors below instead of re-describing entity shape.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Application class | `.database.DBMigration` (manifest `android:name`, `@HiltAndroidApp`) — creates notification channels + arms the usage-limit alarm on cold start |
| DB file | Room `"eventDB"`, singleton via package-private `AppDatabase.getDatabase(context)` (double-checked locking; callable only from `database/` — `database/DatabaseModule.java` is the sole Hilt door, also providing all 8 DAOs) |

## Files
| Class | Path | Role |
|---|---|---|
| `AppDatabase` | `database/AppDatabase.java` | Room `@Database`, v14, 8 entities, `fallbackToDestructiveMigration()` + 7 real migrations registered |
| `DBMigration` | `database/DBMigration.java` | `Application` class + migration `Migration` constants (misleading name: it's both) |
| `DBConverter` | `database/DBConverter.java` | `@TypeConverter`s: `Calendar`↔JSON (Gson), `boolean[]`↔JSON (Gson), `Location`→null (stub) |
| `Stats` / `StatsDAO` | `stats/Stats.java`, `StatsDAO.java` | Single-row lifetime + today counters (time studied, tasks, streaks, fails) |
| `DailyStat` / `DailyStatDAO` | `stats/DailyStat.java`, `DailyStatDAO.java` | One row per ISO day — history that feeds Progress charts |
| `FocusEvent` / `FocusEventDAO` | `stats/FocusEvent.java`, `FocusEventDAO.java` | One row per completed session / fail, real timestamp — feeds the by-hour charts. Carries `entryId` (0 = unattributed) linking a session to the `CalendarEntry` it was focused on; `sumAttributedMinutes(entryId)` (scalar, auto-complete check) + `observeAttributedMinutesByEntry()` (`LiveData`, grouped, → `AttributedMinutes` POJO) feed task/item time-progress, both consumed by `FocusAttributionRepository`. `getMinutesByProject(start, end)` (→ `ProjectMinutes` POJO) joins `focus_event ⨝ calendarEntry ⨝ project`, `type = 0 AND entryId != 0`, grouped by project — note it is **time-bounded**, unlike the deliberately lifetime-scoped `observeAttributedMinutesByEntry()`. **Racha de Home**: `observeActiveDaysSince` / `getActiveDaysSince` / `countActiveDaysBetween` / `countFocusBetween` agregan por fecha **civil local** — `date(timestamp/1000, 'unixepoch', 'localtime')`; sin el modificador `'localtime'` una sesión de las 00:30 caería en el día anterior en cualquier TZ al este de UTC |
| `FocusAttributionRepository` | `stats/FocusAttributionRepository.java` | `@Singleton`: `enrich(LiveData<List<CalendarEntry>>)` merges an entry list with `observeAttributedMinutesByEntry()` via `MediatorLiveData` (both `HomeViewModel.todayTasksEnriched` and `ProjectDetailViewModel.itemsEnriched` call this — was copy-pasted before repository-layer-consolidation); also owns `maybeAutoComplete(entryId)` |
| `AppRule` / `AppRuleDAO` | `stats/AppRule.java`, `AppRuleDAO.java` | One row per app the user tracks — usage limits + enforcement flags |
| `ConsentRecord` / `ConsentRecordDAO` | `stats/ConsentRecord.java`, `ConsentRecordDAO.java` | Affirmative-consent acknowledgement (usage access, accessibility blocking) |
| `Configuration` / `ConfigurationDAO` | `configuration/Configuration.java`, `ConfigurationDAO.java` | Timer/rest/cycle settings + notification-permission ask tracking (owned by `startup-config.md`, listed here since it's a DB entity) |
| `CalendarEntry` / `CalendarEntryDAO` | `calendarEntries/CalendarEntry.java`, `CalendarEntryDAO.java` | Events/tasks/reminders (owned by `calendar.md`, listed here since it's a DB entity) |
| `Project` / `ProjectDAO` | `projects/Project.java`, `ProjectDAO.java` | Project grouping over `CalendarEntry` items via `projectId` (owned by `projects.md`, listed here since it's a DB entity). `observeAllWithCounts()` (→ `ProjectWithCounts` POJO, `@Embedded Project` + `doneCount`/`totalCount`) joins `project` × `calendarEntry` in one query so `InvalidationTracker` watches both tables. `observeDeadlinesBetween(start, end)` and `getActiveWithDeadlineAfter(now)` (both `STATUS_ACTIVE` only) feed the Calendar markers and the boot re-arm |
| `ProjectsRepository` | `projects/ProjectsRepository.java` | `@Singleton`: owns `observeAllWithCounts()` + `createProject()` (on `@DbWriteExecutor`) |

## Schema history (v6 → v14)

| Version | Migration | What changed |
|---|---|---|
| 6 → 7 | `MIGRATION_6_7` | Adds `startMillis`/`endMillis` long mirrors to `calendarEntry` (pre-existing rows default 0; `EventBuilder` populates both going forward) |
| 7 → 8 | `MIGRATION_7_8` | Adds `notificationPermissionAskCount` / `notificationPermissionLastAskedMillis` to `configuration` |
| 8 → 9 | *(none — destructive)* | `DailyStat` + `FocusEvent` tables introduced; no migration was written, so upgraders on v8 lost data here |
| 9 → 10 | `MIGRATION_9_10` | Adds `app_rule` + `consent_record` tables (additive `CREATE TABLE IF NOT EXISTS` only — v9 history preserved) |
| 10 → 11 | `MIGRATION_10_11` | Adds 6 additive columns to `calendarEntry` for recurring tasks: `isTemplate`, `templateId`, `repetitionInterval`, `repetitionDays`, `materializedUntilMillis`, `isDismissed` (all `NOT NULL DEFAULT`, `ALTER TABLE ADD COLUMN` — no data loss) |
| 11 → 12 | `MIGRATION_11_12` | Adds `project` table (`CREATE TABLE IF NOT EXISTS`) + additive `projectId` column on `calendarEntry` (`NOT NULL DEFAULT 0` — no data loss) |
| 12 → 13 | `MIGRATION_12_13` | Two additive columns: `calendarEntry.targetMinutes` + `focus_event.entryId` (both `NOT NULL DEFAULT 0`, `ALTER TABLE ADD COLUMN` — no data loss). `CalendarEntry.attributedMinutes` is `@Ignore` (transient row-progress, not a column) |

| 13 → 14 | `MIGRATION_13_14` | Additive `calendarEntry.hiddenInCalendar` (`NOT NULL DEFAULT 0`) **plus a one-off backfill**: every row already belonging to a recurring series (`isTemplate = 1 OR templateId != 0 OR (repetition != 0 AND type = 2)`) is set to `1`. The only migration so far that rewrites existing rows rather than just adding a column — deliberate, so the new "repeats ⇒ hidden by default" rule also applies to series created before it (user decision). Standalone tasks and events are untouched |

`fallbackToDestructiveMigration()` is still active for any version gap without a registered
`Migration` — see CLAUDE.md rule #6 before bumping `@Database(version)`.

## Per-entity readers/writers (contract table)

| Entity | Written by | Read by |
|---|---|---|
| `Stats` | `HomeViewModel` (`addFails`, `completeTimer`), `SplashActivity`/`InitialConfiguration` (`resetDailyStats`, streak update) | `HomeViewModel`, `SplashActivity`/`InitialConfiguration`, `ProgressViewModel` (today merged as trailing chart point) |
| `DailyStat` | `SplashActivity`/`InitialConfiguration` (`persistDailyStat`, upsert before daily reset) | `ProgressViewModel` (chart history) |
| `FocusEvent` | `HomeViewModel.logFocusEvent()` (`TYPE_FOCUS` on `completeTimer`, `TYPE_FAIL` on `addFails`; both now carry the bound `entryId` from `FocusTarget`, 0 when unbound; `TYPE_TASK` reserved, never emitted) | `ProgressViewModel` (by-hour buckets), `FocusAttributionRepository` (`observeAttributedMinutesByEntry()` for `HomeViewModel`/`ProjectDetailViewModel`'s task/item time-progress via `enrich()`; `sumAttributedMinutes()` for `maybeAutoComplete()`), `ProgressViewModel` (`getMinutesByProject()` — one call feeds both the time-per-project chart page and the project band) |
| `AppRule` | `ProgressViewModel` (`setTracked`, `setDailyLimit`, `setEnforceAtLimit` — see `app-limits.md`) | `ProgressViewModel`, `UsageLimitChecker`/`UsageLimitScheduler` (`getLimited`), `BlockDecisionEngine` (`observeEnforced`), `DBMigration` (arms scheduler on cold start) |
| `ConsentRecord` | `UsageDisclosureDialog`, `AccessibilityDisclosureDialog` | `ProgressViewModel`, `ProgressFragment`/blocking flow (gate before Settings deep-link) |
| `Configuration` | `ConfigurationManager.updateConfiguration()`, `PermissionGate` (ask-count/timestamp) | `ConfigurationManager` (cached in memory), `HomeViewModel`/`HomeFragment` (timer/rest/cycle values) |
| `CalendarEntry` | `AddEventActivity` (via `EventBuilder.build()`), `HomeViewModel` (`quickAddTask` via `EventBuilder`; `setTaskDone` re-reads via `getEventById` + `update`), `RecurrenceMaterializer` (occurrence rows via `EventBuilder.build()`, template `materializedUntilMillis` updates, `dismissSeriesBefore`), `ProjectDetailViewModel` (`addItem` via `EventBuilder` with `projectId` set, `setItemDone` re-read + `update`, `deleteItemsByProject` cascade), `FocusAttributionRepository.maybeAutoComplete()` (re-read + `update` on target reached) | `CalendarFragmentMonth`/`Week` via `CalendarViewModel`, `HomeViewModel` (today's `TYPE_TASK` via `getEventsBetween` + overdue via `getUndoneTasksBefore`, both `isTemplate = 0`), `EventReminderScheduler`, `BootReceiver`, `RecurrenceMaterializer` (`getTemplates`, `getLegacyRepeatingRows`), `ProjectDetailViewModel` (`observeItemsByProject`), `ProjectDAO.observeAllWithCounts()` (joined, see `Project` row) |
| `Project` | `ProjectsRepository.createProject()`, `ProjectDetailViewModel` (`updateHeader`, `updateDeadline`, `completeProject`, `deleteProject`) — every writer touching `status`/`softDeadlineMillis` must also cancel/reschedule the deadline alarms, see `projects.md` | `ProjectsRepository.observeAllWithCounts()` (joined with `calendarEntry` in one query — see the Room invalidation invariant below), `ProjectDetailViewModel` (`observeById`), `CalendarViewModel` (`observeDeadlinesBetween`, read-only markers), `ProgressViewModel` (project band), `ProjectDeadlineReceiver` (`getById`, stale-alarm re-read), `BootReceiver` (`getActiveWithDeadlineAfter`) — see `projects.md` |

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
- **Room invalidation & cross-table observation** (repository-layer-consolidation, T2 — the durable
  answer after three `onResume()`/`ActivityResultCallback` "requery" workarounds were audited and
  found to have three *different* causes, not one bug copied three times):
  - A Room `@Query` returning `LiveData<...>` **does** auto-invalidate on any write to the table(s)
    it references — including tables referenced only inside a correlated subquery, as
    `ProjectDAO.observeAllWithCounts()` proves (its subqueries read `calendarEntry`; a
    `calendarEntry` write re-fires it even though the outer `FROM` is `project`). If a query only
    reads ONE table, its `LiveData` will never re-fire from a write to a DIFFERENT table — that
    was `ProjectsViewModel`'s real bug (`observeAll()` was `project`-only while the % needed
    `calendarEntry` too): the fix is a query that references both, not a manual poll.
  - A Room `LiveData` sitting behind a `Transformations.switchMap` trigger (a plain
    `MutableLiveData` the ViewModel controls, e.g. `CalendarViewModel.range`,
    `HomeViewModel.todayRange`) invalidates and re-emits **on its own** the moment the underlying
    table changes — `switchMap` doesn't need to be "poked" for that. Re-emitting the trigger with
    an unchanged value only forces `switchMap` to rebuild the *chain object*; it does nothing Room
    wasn't already doing. `CalendarViewModel.refresh()` existed as exactly this needless poke and
    was deleted outright (proven on-device: an `AddEventActivity` insert appears on return with no
    call to it). Don't reintroduce this pattern to "fix" a perceived invalidation lag — check
    whether the underlying `@Query` already covers the write's table first.
  - A `MediatorLiveData` merging two independent sources must never forward a value read from
    ONE source before the OTHER has ever emitted (e.g. `entries.getValue()` can be `null` if a
    second, independently-firing source's callback runs first) — the merge must wait rather than
    propagate a `null`/stale snapshot downstream, since observers in this codebase assume
    `LiveData<List<T>>` never emits `null` (`FocusAttributionRepository.enrich()` hit exactly this
    as a real crash during T2's on-device verification, fixed by returning early instead of
    forwarding the unset snapshot).

## History

| Date | Change | Spec |
|---|---|---|
| 2026-06-28 | `DailyStat` + `FocusEvent` added (DB v8→v9, no migration written) | `.claude/specs/archive/progress-charts-mvp/proposal.md` |
| 2026-06-29 | `AppRule` + `ConsentRecord` added (DB v9→v10, `MIGRATION_9_10`) | `.claude/specs/archive/progress-phase2-usage/proposal.md` |
| 2026-07-07 | Home surfaces today's `TYPE_TASK` entries — additive `getUndoneTasksBefore` `@Query` (overdue undone tasks), `HomeViewModel` now a `CalendarEntry` reader/writer; no schema change (still v10) | `.claude/specs/archive/tasks-home-today/proposal.md` |
| 2026-07-12 | Recurring tasks: `calendarEntry` +6 columns (DB v10→v11, `MIGRATION_10_11`), new `RecurrenceMaterializer` reader/writer | `.claude/specs/archive/tasks-recurrence/proposal.md` |
| 2026-07-12 | Projects MVP: new `Project` entity (DB v11→v12, `MIGRATION_11_12`), `calendarEntry` +`projectId` column | `.claude/specs/archive/projects-mvp/proposal.md` |
| 2026-07-17 | Time targets + focus attribution: `calendarEntry` +`targetMinutes`, `focus_event` +`entryId` (DB v12→v13, `MIGRATION_12_13`); `FocusEvent` now attributed to a `CalendarEntry`, new `FocusEventDAO` sum queries + `AttributedMinutes` POJO | `.claude/specs/archive/focus-attribution/proposal.md` |
| 2026-07-17 | DI + threading consolidation: `database/DatabaseModule.java` now the single `@Provides` source for `AppDatabase` + all 8 DAOs (absorbed `ConfigurationDatabaseModule`, deleted); `AppDatabase.getDatabase()` made package-private; `database/ThreadingModule.java` adds shared `@IoExecutor`/`@DbWriteExecutor` singletons replacing ~15 ad-hoc `Executors.new*` sites. No schema change. | `.claude/specs/archive/di-threading-consolidation/proposal.md` |
| 2026-08-29 | Repository layer: new `FocusAttributionRepository` (de-dupes `enrichWithAttributedMinutes`, owns `maybeAutoComplete`) and `ProjectsRepository` + `ProjectDAO.observeAllWithCounts()` (cross-table join, replaces `ProjectsViewModel`'s manual Observer/recompute). All three `refresh()`-on-`onResume()` workarounds removed or narrowed — see the Room invalidation invariant above for the three different root causes found. `@DbWriteExecutor` (provisioned but unused since T1) now carries every DB write in the touched ViewModels. No schema change. | `.claude/specs/archive/repository-layer-consolidation/proposal.md` |
| 2026-09-01 | Recurring tasks hidden from the Calendar screen: `calendarEntry` +`hiddenInCalendar` (DB v13→v14, `MIGRATION_13_14`, retroactive backfill for existing series). New `CalendarEntryDAO.getVisibleEventsBetween` — the Calendar screen's query; Home keeps `getEventsBetween` so a hidden task stays actionable in its list | `.claude/specs/changes/recurrence-calendar-visibility/proposal.md` |
| 2026-09-09 | Racha "días con pomodoro": sin cambio de esquema (sigue v14) — sólo cuatro `@Query` de agregación sobre `focus_event` por fecha civil local. `Stats.currentStreak`/`maxStreak` siguen escribiéndose en `SplashActivity` pero **dejan de tener lector de UI**: la racha que se muestra ahora se deriva de `focus_event`. | `.claude/specs/archive/focus-mode-and-streak/proposal.md` |
| 2026-09-01 | Project deadlines & Progress integration: no schema change (stays v13) — only new `@Query` methods (`ProjectDAO.observeDeadlinesBetween`/`getActiveWithDeadlineAfter`, `FocusEventDAO.getMinutesByProject`) and one projection POJO (`ProjectMinutes`). `Project` gains four new readers across Calendar, Progress and the deadline alarm path. | `.claude/specs/archive/project-deadlines-progress/proposal.md` |
