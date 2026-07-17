# DI + threading consolidation (refactor tranche T1)

**Slug:** di-threading-consolidation
**Status:** verified
**Created:** 2026-07-17
**Last updated:** 2026-07-17

Tranche T1 of the [architecture refactor roadmap](../../../plans/architecture-refactor-roadmap.md)
(findings F1/F2/F8). Pure infrastructure refactor — **zero user-visible behavior change** is the
acceptance bar.

## Why

Hilt is installed and already provides `AppDatabase` + 3 of 8 DAOs, yet every ViewModel
hand-builds its DAOs via `AppDatabase.getDatabase(application)`
([`HomeViewModel.java:85-89`](../../../../app/src/main/java/com/example/bbettercalendar/ui/home/HomeViewModel.java#L85-L89))
and there are **16 independent `Executors.new*` creation sites** — one per
ViewModel/dialog/receiver, none ever shut down. Two competing idioms mean every new feature
re-decides its plumbing (and usually copies the manual one). Phase 5 and tranches T2–T4 all
build on this being settled.

## What changes (deltas vs current behavior)

- **ADDED — `database/DatabaseModule.java`**: absorbs `provideDatabase` from
  `ConfigurationDatabaseModule` and adds `@Provides` for **all 8 DAOs** (the 3 existing DAO
  providers in `ConfigurationModule`/`NotificationsModule` move here; those modules keep their
  non-DAO providers: `ConfigurationManager`, `AlarmManager`).
- **ADDED — `database/ThreadingModule.java`** + qualifiers: `@IoExecutor` (`@Singleton` fixed
  pool of 4) and `@DbWriteExecutor` (`@Singleton` single thread). **Conservative adoption in
  T1:** every existing executor usage maps 1:1 onto `@IoExecutor` (same or more parallelism
  than today, so no behavior change); migrating write paths onto `@DbWriteExecutor` for global
  write ordering is **T2 material**, the qualifier just ships now.
- **CHANGED — the 5 ViewModels** (`HomeViewModel`, `CalendarViewModel`, `ProgressViewModel`,
  `ProjectsViewModel`, `ProjectDetailViewModel`) become `@HiltViewModel` with constructor-injected
  DAOs + `@IoExecutor` (staying `AndroidViewModel` where they use the `Application`; Hilt
  injects it). Their fragments gain `@AndroidEntryPoint` where missing
  (`CalendarFragmentMonth`, `CalendarFragmentWeek`, `ProgressFragment`, `ProjectsFragment`,
  `ProjectDetailFragment`). Existing `new ViewModelProvider(...)` call sites keep working via
  Hilt's default factory — no fragment logic changes.
- **CHANGED — remaining direct `getDatabase` call sites** switch to injection:
  `SplashActivity` + `InitialConfiguration`, `AddEventActivity` (already `@AndroidEntryPoint`),
  `AppPickerActivity`, `UsageDisclosureDialog`, `AccessibilityDisclosureDialog`,
  `BlockerAccessibilityService` (Hilt supports `@AndroidEntryPoint` services),
  `DBMigration.onCreate`. Receivers (`BootReceiver`, `EventReminderReceiver`,
  `UsageLimitReceiver` — already `@AndroidEntryPoint`) drop their static `IO` executors for
  injected `@IoExecutor`.
- **CHANGED — `AppDatabase.getDatabase` visibility**: callable only from `database/`
  (package-private) so the compiler enforces "Hilt is the only door". Dead commented-out
  duplicate `getDatabase` (`AppDatabase.java:60-74`) deleted.
- **REMOVED — `ConfigurationDatabaseModule`** (folded into `DatabaseModule`).
- **OPPORTUNISTIC (F8, only files already touched):** none beyond the receiver statics —
  `FocusTarget`/`BlockingSettings` etc. stay as-is (T2+).

## Impact

- **Files / packages touched:**
  - NEW `database/DatabaseModule.java`, `database/ThreadingModule.java` (+ 2 qualifier annotations)
  - DELETED `configuration/ConfigurationDatabaseModule.java`
  - `configuration/ConfigurationModule.java`, `notifications/NotificationsModule.java` (DAO providers move out)
  - `database/AppDatabase.java` (visibility + dead code), `database/DBMigration.java` (inject DAO/executor)
  - 5 ViewModels + 5 fragments listed above; `MainActivity` untouched
  - `configuration/SplashActivity.java`, `configuration/InitialConfiguration.java`,
    `calendarEntries/AddEventActivity.java`, `ui/progress/apppicker/AppPickerActivity.java`,
    `ui/progress/UsageDisclosureDialog.java`, `blocking/AccessibilityDisclosureDialog.java`,
    `blocking/BlockerAccessibilityService.java`
  - `notifications/event/BootReceiver.java`, `notifications/event/EventReminderReceiver.java`,
    `usage/limits/UsageLimitReceiver.java`
- **DB schema:** none (rule #6 untouched; `fallbackToDestructiveMigration` unchanged — that's T3).
- **UI tokens:** none (no layout/resource changes).
- **Threading:** rule #3 semantics preserved — same executor-based off-main work, same
  `postValue` paths; only *ownership* of the executors changes.

## Out of scope

- Repository layer / de-duplication (T2).
- Any schema change, indexes, or destructive-fallback removal (T3).
- Reminder scheduler generalization (T4).
- Moving write paths onto `@DbWriteExecutor` (T2 — needs its own on-device verification).
- `HomeFragment` restructuring; `FocusTarget` et al. statics.

## Verify

**Verdict: PASS.** Implemented task-by-task from `tasks.md`; `/check`-equivalent
(`assembleDebug` + `lintDebug` + `test`) all green, and a full on-device pass on
`emulator-5554` found zero `FATAL EXCEPTION` / Hilt `MissingBinding` errors across every
touched screen.

**Completeness.** All `tasks.md` boxes checked (see that file). No half-finished items.

**Correctness vs proposal's Impact section.** Diff matches the declared file list, with two
additions the proposal implied but didn't spell out to the line:
- `app/build.gradle`: added `androidx.hilt:hilt-navigation-fragment:1.2.0` (implementation) +
  `androidx.hilt:hilt-compiler:1.2.0` (annotationProcessor). Required for `@HiltViewModel` to
  compile at all (it wasn't on the classpath before) — an unavoidable consequence of "5
  ViewModels become `@HiltViewModel`", not scope creep.
- `ui/calendar/CalendarFragmentMonth.java`: deleted a dead, never-referenced
  `@Inject Configuration config;` field. It compiled silently before only because the class
  wasn't `@AndroidEntryPoint`; adding the annotation (required by this tranche) turned it into
  a live Dagger `MissingBinding` error (`Configuration` has no `@Provides`/`@Inject`
  constructor). Fixing required removing the field, not adding a new provider — no proposal
  scope change, just an incidental unblock.

**Known, deliberate exceptions to the "zero outside scope" grep check** (both explicitly
out-of-scope per the proposal's own Impact/Out-of-scope sections, confirmed by grep):
- `configuration/ConfigurationManager.java:15` still has its own `Executors.newFixedThreadPool(2)`
  — never listed in Impact's touched-files, and matches "F2's 16th site" from the roadmap
  finding that this tranche doesn't claim to close.
- `ui/calendar/weekview/EventsProcessor.kt:18` has an unrelated `Executors.newSingleThreadExecutor()`
  default parameter — a vendored/adapted Kotlin file outside this tranche's package list.
- Grep otherwise confirms zero `AppDatabase.getDatabase` outside `database/` and zero
  `Executors.new*` outside `database/ThreadingModule.java` (plus the two exceptions above).

**Coherence.** Threading semantics preserved per rule #3 (`postValue` from background,
`observe(getViewLifecycleOwner(), ...)` unchanged). One behavior note: ViewModels/
Activities/receivers that used to call `.shutdown()` on their own per-instance executor in
`onCleared()`/`onDestroy()`/`teardown()` no longer do — correct, since the executor is now a
shared app-wide `@Singleton` that outlives any single consumer; shutting it down would break
every other consumer. `BlockerAccessibilityService`'s `executor == null` guard (used to detect
"not yet connected / already tearing down") was replaced with the pre-existing `destroyed`
flag, since the injected executor is never null.

**Build/lint/test:** `.\gradlew.bat assembleDebug` — BUILD SUCCESSFUL. `.\gradlew.bat
lintDebug` — BUILD SUCCESSFUL, 0 lint errors. `.\gradlew.bat test` — BUILD SUCCESSFUL (JVM
unit tests, incl. `HomeViewModelCollapseTest`, unaffected by the constructor signature
changes since they only call the static `collapseOverdue` helper).

**On-device (emulator-5554, applicationId `io.github.thaguille.bbettercalendar`):** fresh
install, `pm clear` + launch via `SplashActivity` → `MainActivity`. Exercised: Home (timer
start + pause, quick-add task, task appears in today list), Calendar month view (renders,
switch to week view and back), Calendar week view, Progress (charts render with data,
granularity Day/Week/Month switch updates `range_label`, usage band renders LOCKED state
correctly), Projects (list, create project, open detail, add item), `AddEventActivity`
(save an event from the Calendar week FAB). `adb logcat -b crash` and a full-log grep for
`FATAL EXCEPTION`/`MissingBinding`/`dagger.`/`hilt` stayed empty throughout. Test data
cleared afterward (`pm clear`).

**One tooling note (not a code issue):** this worktree didn't have `local.properties` or the
untracked spec/plan files (git worktrees only carry tracked history, not the main
worktree's untracked/uncommitted files) — copied `local.properties` from the main repo and
copied `proposal.md`/`tasks.md`/`architecture-refactor-roadmap.md` into this worktree before
starting, so they could be edited and later moved into `archive/` from here.
