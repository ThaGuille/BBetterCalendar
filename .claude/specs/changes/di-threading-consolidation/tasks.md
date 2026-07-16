# Tasks — di-threading-consolidation

## Modules
- [ ] `database/DatabaseModule.java`: `provideDatabase` + `@Provides` for all 8 DAOs.
- [ ] `database/ThreadingModule.java` + `@IoExecutor` / `@DbWriteExecutor` qualifiers (fixed-4 pool / single thread, both `@Singleton`).
- [ ] Move DAO providers out of `ConfigurationModule` / `NotificationsModule`; delete `ConfigurationDatabaseModule`.

## ViewModels → @HiltViewModel (+ fragments → @AndroidEntryPoint)
- [ ] `HomeViewModel` (keeps `AndroidViewModel`; inject `StatsDAO`, `FocusEventDAO`, `CalendarEntryDAO`, `ConfigurationManager`, `@IoExecutor`).
- [ ] `CalendarViewModel` + `@AndroidEntryPoint` on `CalendarFragmentMonth`, `CalendarFragmentWeek`.
- [ ] `ProgressViewModel` + `ProgressFragment`.
- [ ] `ProjectsViewModel` + `ProjectsFragment` (dialog shares VM via parent — confirm still resolves).
- [ ] `ProjectDetailViewModel` + `ProjectDetailFragment`.

## Remaining direct getDatabase / ad-hoc executor sites → injection
- [ ] `SplashActivity` + `InitialConfiguration`.
- [ ] `AddEventActivity`.
- [ ] `AppPickerActivity`.
- [ ] `UsageDisclosureDialog`, `AccessibilityDisclosureDialog`.
- [ ] `BlockerAccessibilityService` (`@AndroidEntryPoint` service).
- [ ] `DBMigration.onCreate` (inject `AppRuleDAO` + executor).
- [ ] Receivers: `BootReceiver`, `EventReminderReceiver`, `UsageLimitReceiver` drop static `IO` executors.

## Lockdown + cleanup
- [ ] `AppDatabase.getDatabase` package-private; delete dead commented duplicate (`AppDatabase.java:60-74`).
- [ ] Full-tree grep: zero `AppDatabase.getDatabase` outside `database/`, zero `Executors.new` outside `ThreadingModule`.

## Verify
- [ ] `/check` (assembleDebug + lintDebug).
- [ ] `ui-tester` emulator pass (rule #7 — every screen's data path is touched): launch → Home timer start/stop, today list, Calendar month/week render, Progress charts, Projects list + detail, add event, quick-add task. No FATAL EXCEPTION.
- [ ] `/spec verify di-threading-consolidation`.
