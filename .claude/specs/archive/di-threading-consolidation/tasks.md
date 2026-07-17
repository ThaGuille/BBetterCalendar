# Tasks — di-threading-consolidation

## Modules
- [x] `database/DatabaseModule.java`: `provideDatabase` + `@Provides` for all 8 DAOs.
- [x] `database/ThreadingModule.java` + `@IoExecutor` / `@DbWriteExecutor` qualifiers (fixed-4 pool / single thread, both `@Singleton`).
- [x] Move DAO providers out of `ConfigurationModule` / `NotificationsModule`; delete `ConfigurationDatabaseModule`.

## ViewModels → @HiltViewModel (+ fragments → @AndroidEntryPoint)
- [x] `HomeViewModel` (keeps `AndroidViewModel`; inject `StatsDAO`, `FocusEventDAO`, `CalendarEntryDAO`, `ConfigurationManager`, `@IoExecutor`).
- [x] `CalendarViewModel` + `@AndroidEntryPoint` on `CalendarFragmentMonth`, `CalendarFragmentWeek`.
- [x] `ProgressViewModel` + `ProgressFragment`.
- [x] `ProjectsViewModel` + `ProjectsFragment` (dialog shares VM via parent — confirm still resolves).
- [x] `ProjectDetailViewModel` + `ProjectDetailFragment`.

## Remaining direct getDatabase / ad-hoc executor sites → injection
- [x] `SplashActivity` + `InitialConfiguration`.
- [x] `AddEventActivity`.
- [x] `AppPickerActivity`.
- [x] `UsageDisclosureDialog`, `AccessibilityDisclosureDialog`.
- [x] `BlockerAccessibilityService` (`@AndroidEntryPoint` service).
- [x] `DBMigration.onCreate` (inject `AppRuleDAO` + executor).
- [x] Receivers: `BootReceiver`, `EventReminderReceiver`, `UsageLimitReceiver` drop static `IO` executors.

## Lockdown + cleanup
- [x] `AppDatabase.getDatabase` package-private; delete dead commented duplicate (`AppDatabase.java:60-74`).
- [x] Full-tree grep: zero `AppDatabase.getDatabase` outside `database/`, zero `Executors.new` outside `ThreadingModule` (two pre-existing, out-of-scope exceptions confirmed: `configuration/ConfigurationManager.java`, `ui/calendar/weekview/EventsProcessor.kt` — see proposal's Verify section).

## Verify
- [x] `/check` (assembleDebug + lintDebug) — both BUILD SUCCESSFUL; also ran `test` (JVM unit tests) green.
- [x] Emulator pass (rule #7 — every screen's data path is touched): launch → Home timer start/stop, today list + quick-add task, Calendar month/week render, Progress charts + granularity switch, Projects list + create + detail + add item, add event via AddEventActivity. No FATAL EXCEPTION, no Hilt MissingBinding, on `emulator-5554`.
- [x] `/spec verify di-threading-consolidation` — see proposal.md `## Verify` section (verdict: PASS).
