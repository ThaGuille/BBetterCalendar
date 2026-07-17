# Architecture refactor roadmap — data/DI infrastructure consolidation

**Status:** in progress — sequencing approved 2026-07-17: T1 + T4 land as specs before
`project-deadlines-progress`; T3 merges into Phase 5's schema bump if one is needed; T2 after.
**T4 done** (2026-07-17, worktree `agent-acaea12d49f955fa4`) — see
`.claude/specs/archive/reminder-generalization/proposal.md`. T1 in flight in a parallel worktree.
**Created:** 2026-07-17
**Last updated:** 2026-07-17

Whole-app structural audit (112 files, ~11k lines, DB v13) done before proposing Phase 5
(`project-deadlines-progress`). Goal: pay down the integration debt accumulated across the
tasks/recurrence/projects/focus-attribution phases so cross-system features (Phase 5 couples
projects ↔ calendar ↔ notifications) stop hitting the same seams.

## Findings (grounded, with evidence)

### F1 — DI split-brain: Hilt exists but the data layer ignores it
Hilt already provides `AppDatabase`, `ConfigurationDAO`, `CalendarEntryDAO`, `AppRuleDAO`,
`ConfigurationManager`, `AlarmManager` and the notifiers (`ConfigurationDatabaseModule`,
`ConfigurationModule`, `NotificationsModule`). Yet:
- All 5 ViewModels (`HomeViewModel`, `CalendarViewModel`, `ProgressViewModel`,
  `ProjectsViewModel`, `ProjectDetailViewModel`) are plain `AndroidViewModel`s that call
  `AppDatabase.getDatabase(application)` and hand-build their DAOs (e.g. `HomeViewModel.java:85-89`).
- 10+ other call sites (`AddEventActivity:181`, `SplashActivity:40-43`, dialogs, the
  accessibility service) also call `getDatabase` directly.
- Only 3 of 8 DAOs have `@Provides` methods.

Two idioms for the same thing = every new feature asks "which way?" and usually copies the
manual one.

### F2 — Executor proliferation: 16 creation sites, no shared pool
`Executors.newFixedThreadPool(2)` per ViewModel instance, `newSingleThreadExecutor` per
dialog/receiver/service (16 sites total, see grep). Consequences:
- Threads leak per ViewModel recreation (no `onCleared()` shutdown anywhere).
- No global write ordering: two writers on different executors can interleave DB writes
  (works today because SQLite serializes, but "who wins" is scheduler luck).
- Every new class re-decides its threading.

### F3 — The same Room-invalidation workaround exists three times
`CalendarViewModel.refresh()`, `HomeViewModel.refreshToday()` (todayRange re-emit),
`ProjectsViewModel.refresh()` + `onResume()` hooks in 3 fragments — all documented as the same
workaround ("lag del InvalidationTracker tras insertar desde otra pantalla" /
cross-table count staleness). The root cause was never fixed once; the *pattern* was copied.
`ProjectsViewModel` additionally does a manual `Observer` + executor recompute because its
percent needs a second table — a Room `@Query` with a JOIN/subselect would be observed on
**both** tables by the InvalidationTracker and make the manual recompute + refresh() moot.

### F4 — No repository layer; ViewModels duplicate domain logic
- `enrichWithAttributedMinutes` duplicated verbatim (`HomeViewModel.java:306`,
  `ProjectDetailViewModel.java:61`) — already a MED finding in the focus-attribution verify.
- Task filter/sort/collapse logic lives as statics inside `HomeViewModel`.
- Project done/total counting lives inside `ProjectsViewModel`.
Phase 5 makes this worse: "deadline state per project" will be needed by the Projects tab,
the Calendar mapper, and a notification scheduler — three consumers, no shared home.

### F5 — `CalendarEntry` entity debt
- **Dual time storage:** `Calendar startDayAndHour`/`endDayAndHour` persisted as Gson JSON
  strings *plus* `startMillis`/`endMillis` long mirrors (added v6→7). Readers (e.g.
  `CalendarItemMapper.toItem`) still carry a defensive JSON fallback for pre-v7 rows that
  were never backfilled.
- Legacy fields: `limitDate` (string), `duration` (ms, effectively write-only), a block of
  commented-out dead fields.
- No indexes: `getEventsBetween(startMillis, endMillis)` range-scans, and the new
  `focus_event.entryId` group-by (attribution enrichment) full-scans on every list emission
  (the MED perf follow-up from the focus-attribution verify).
- The entity is becoming a god-row: event + task + reminder + recurrence template +
  occurrence + project item all in one table. That's a *deliberate* decision (items ride the
  task pipelines for free) — keep it — but the unused-legacy columns and missing indexes are
  pure debt.

### F6 — Destructive fallback is still armed (rule #6 standing hazard)
`AppDatabase` registers 6 real migrations but keeps `fallbackToDestructiveMigration()`. One
forgotten migration = silent wipe of all user data (already happened once at v8→v9). Now that
migration discipline exists, the fallback's only remaining "benefit" is masking mistakes.

### F7 — Reminder pipeline is entry-shaped; Phase 5 needs it project-shaped
`EventReminderScheduler` schedules off `CalendarEntry.notifications` (`boolean[7]` Gson
column) against the fixed `NotificationOffsets.OFFSET_MILLIS` table, with
`requestCode = entryId * 10 + offsetIndex`:
- The `* 10` namespace caps offsets at 9 ever (7 used) — silent PendingIntent collisions past that.
- A project deadline reminder can't reuse these request codes: `Project.id` and
  `CalendarEntry.id` are different id spaces that would collide in the same
  `PendingIntent.getBroadcast` namespace.
- `NotificationOffsets` (domain data) lives in `popups/` (a UI package) — anything
  notification-side importing from `popups/` is a dependency smell.

### F8 — Process-wide mutable statics
`FocusTarget` (flagged in its own spec follow-up), `BlockingSettings`, `FocusBlockState`,
`WarnedTodayStore`, receiver-static executors. Each is individually defended, but they are
un-injectable, untestable, and invisible to Hilt's object graph.

### F9 — Small clarity items
- `DBMigration` is both the `Application` class and the migrations holder — actively
  misleading name (docs already apologize for it).
- Dead code: commented-out second `getDatabase` in `AppDatabase.java:60-74`, dead field block
  in `CalendarEntry.java:71-83`.
- `HomeFragment` is 782 lines: timer state machine + today/overdue lists + focus banner +
  block-mode arming. Splitting the `CountDownTimer` state machine into a plain
  `PomodoroTimerController` would be the single biggest readability win, but it is also the
  riskiest refactor in the app (restore-ordering is documented as fragile-by-design).

## Proposals — four tranches, ordered by (leverage / risk)

### T1 — DI + threading consolidation *(do first; mechanical, unlocks the rest)*
1. `DatabaseModule` (rename/extend `ConfigurationDatabaseModule`): `@Provides` for **all 8 DAOs**.
2. Two Hilt-provided executors: `@IoExecutor` (fixed pool, reads/parallel work) and
   `@DbWriteExecutor` (single thread — global write ordering for free). Delete the 16 ad-hoc
   creation sites; receivers get theirs via `@AndroidEntryPoint` injection.
3. Convert the 5 ViewModels to `@HiltViewModel` + constructor injection (fragments already
   use `ViewModelProvider`; swap is mechanical).
4. `AppDatabase.getDatabase` stays (needed by the module) but becomes package-private —
   compile-enforced "Hilt is the only door".
- **Schema:** none. **Risk:** low. **Effort:** ~1 session.

### T2 — Repository layer + kill the invalidation workaround at the root
1. `FocusAttributionRepository`: owns `enrichWithAttributedMinutes`, the sum queries, and
   `maybeAutoComplete` (de-dups the two ViewModels; natural home for the short-circuit perf
   fix from the verify follow-ups).
2. `ProjectsRepository`: replace `ProjectsViewModel`'s manual Observer/recompute with a
   single observed `@Query` joining `project` × `calendarEntry` counts → InvalidationTracker
   watches both tables → delete `ProjectsViewModel.refresh()` + `onResume` hook.
3. Root-cause the remaining `refresh()` twins (Home/Calendar) once: reproduce the
   "insert from another screen doesn't retrigger" case post-T1 (single DB instance,
   single write executor) and either delete the workaround or document the *real* cause in
   `data-model.md`. Don't copy the pattern a fourth time.
- **Schema:** none. **Risk:** medium (behavioral parity to verify on-device). **Effort:** 1–2 sessions.

### T3 — Schema v13→v14 hygiene bundle *(one real migration, one bump — rule #6)*
1. `@Index` on `calendarEntry(startMillis)` and `focus_event(entryId)`.
2. Backfill migration: rows with `startMillis = 0` but non-null `startDayAndHour` JSON —
   parse with Gson inside `migrate()` (Java-side cursor walk), write the long mirrors. Then
   delete the in-memory fallbacks (`CalendarItemMapper.toItem` and friends).
3. Mark `limitDate`, `startDayAndHour`, `endDayAndHour`, `duration` as
   `@Deprecated` write-only legacy (column *drop* deferred — SQLite table-rebuild on
   minSdk 21 isn't worth it yet).
4. **Remove `fallbackToDestructiveMigration()`** → keep only
   `fallbackToDestructiveMigrationOnDowngrade()`. A missing future migration then crashes
   loudly in dev instead of wiping user data in prod. Update CLAUDE.md rule #6 wording after.
- **Schema:** v14 + real `MIGRATION_13_14`. **Risk:** medium (migration correctness; test the
  v13-seeded upgrade path like focus-attribution did). **Effort:** ~1 session.

### T4 — Reminder generalization *(the direct Phase 5 enabler)* — DONE (2026-07-17)
1. Extract the alarm mechanics of `EventReminderScheduler` into a shared core parameterized
   by *(receiver class, id-namespace, anchor millis, offsets)*; entry reminders and project
   deadline reminders become two thin clients with **disjoint PendingIntent request-code
   namespaces** (fixes the `*10` cap and the Project-vs-Entry id collision before it exists).
2. Move `NotificationOffsets` from `popups/` to `notifications/` (popups keep a UI wrapper);
   let the deadline flavor define its own offset table (5d/3d/1d/day-of/1h) without touching
   the entry one (whose indexes are persisted in `boolean[]` and must not shift).
- **Schema:** none (deadline offsets storage is decided in the Phase 5 spec, not here).
- **Risk:** low-medium (alarm re-scheduling parity). **Effort:** ~1 session.

### Explicitly deferred (flagged, not scheduled)
- `HomeFragment` timer-state-machine extraction (F9) — highest effort/risk, do only with a
  dedicated spec + heavy on-device verification.
- `CalendarEntry` column drops / table split (F5) — revisit if the entity grows again.
- Migrating statics (F8) to injected singletons — fold opportunistically into T1/T2 files
  that are already being touched (`FocusTarget` first; it has an open spec follow-up).
- Kotlin/coroutines/Flow migration — out: project is deliberately Java 8 (CLAUDE.md).

## Sequencing vs Phase 5
**T1 and T4 before the Phase 5 spec** (T1 is prerequisite hygiene; T4 *is* Phase 5's
foundation — doing it inside the Phase 5 spec would double that spec's blast radius).
T2 can land in parallel or right after. T3 is independent; bundle it whenever the next
schema bump is needed anyway (Phase 5 may need one for deadline reminder offsets — if so,
merge T3 into that same bump to keep one migration).

## Verify
- Each tranche: `/check` + `ui-tester` emulator pass (T1/T2 touch every screen's data path).
- T3: seeded-upgrade test (v13 data → v14 install → data intact + backfill correct).
- The three `refresh()` workarounds are the canary: T2 succeeds when they're deleted and the
  screens still update after cross-screen inserts.
