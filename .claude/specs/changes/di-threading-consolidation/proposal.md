# DI + threading consolidation (refactor tranche T1)

**Slug:** di-threading-consolidation
**Status:** proposed
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

<filled in by `/spec verify` — verdict + any issues found and how resolved>
