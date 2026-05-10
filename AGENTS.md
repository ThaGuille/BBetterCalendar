# AGENTS.md -- BBetterCalendar

## What this is

Android app (Java 8, single `:app` module) combining a **Pomodoro study timer** with a **calendar for events/tasks**. Tracks streaks, daily study time, and timer failures.

## Stack

- **Language**: Java 8 -- no Kotlin, no Compose
- **DI**: Dagger Hilt 2.44
- **DB**: Room 2.4.2 with Gson-based TypeConverters
- **UI**: Views + ViewBinding + Material Components 1.9.0
- **Navigation**: Navigation Component 2.5.3 (bottom nav, 4 tabs)
- **Build**: AGP 7.2.1, compileSdk 33, minSdk 21, Groovy Gradle

## Package map

| Package | Purpose |
|---------|---------|
| `calendarEntries` | `CalendarEntry` entity (type 1=event, 2=task, 3=reminder), builder, DAO, `AddEventActivity` |
| `configuration` | `Configuration` entity, DAO, `ConfigurationManager` singleton, Hilt modules, `SplashActivity` (entry point) |
| `database` | `AppDatabase` (Room singleton), `DBConverter` (Calendar/boolean[] via Gson), `DBMigration` (Application class + @HiltAndroidApp) |
| `helpers` | `FormatHelper`, `ScreenHelper`, `ToolbarHelper` + listener interfaces |
| `popups` | 6 DialogFragment subclasses (Alert, Message, Description, Notifications, Repetition, Timer) + listener interfaces |
| `stats` | `Stats` entity (streaks, time, fails), `StatsDAO` with granular update queries |
| `ui.home` | `HomeFragment` (timer state machine), `HomeViewModel`, `HomeForegroundService` |
| `ui.calendar` | `CalendarFragmentMonth`, `CalendarFragmentWeek`, `CalendarController`, `CalendarWeekAdapter` |
| `ui.progress` | Stub fragment |
| `ui.projects` | Stub fragment |

## Key conventions

- **CalendarEntry creation**: Use `CalendarEntry.EventBuilder` (builder pattern), call `.build()` to produce the entity.
- **Popup communication**: DialogFragments report results via `OnPopupListener<T>` or `OnNotificationsPopupListener`. The parent fragment/activity implements the interface.
- **DB access**: Always on background threads via `ExecutorService`. Use `LiveData.postValue()` from background, never `setValue()`.
- **Configuration**: Injected via Hilt (`@Inject ConfigurationManager`). Cached in memory, persisted to Room on change.
- **Toolbar**: `ToolbarHelper` (implements `MenuProvider`) dispatches to screen-specific listeners (`OnToolBarListener`, `OnToolbarCalendarListener`, `OnToolbarHomeListener`).

## Gotchas

- `DBMigration` is the **Application class** (manifest `android:name`), not just a migration holder.
- `fallbackToDestructiveMigration()` is active -- schema changes wipe the database.
- `InitialConfiguration` is **legacy** -- extends Activity but used as singleton. Its logic lives in `SplashActivity` now.
- **Do not upgrade** `appcompat` past 1.6.1 or `Room` past 2.4.2 (see comments in `app/build.gradle`).
- `ConfigurationManager.getConfiguration()` can return null before async load completes.

## Architecture reference

See `ARCHITECTURE.md` at repo root for detailed diagrams, per-package class descriptions, flow walkthroughs, and full tech debt inventory.
