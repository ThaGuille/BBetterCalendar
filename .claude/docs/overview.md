# BBetterCalendar — Overview & Conventions

## What this is

Android app (Java 8, single `:app` module) combining a **Pomodoro study timer** with a **calendar for events / tasks / reminders**. Tracks streaks, daily study time, and timer failures.

## Stack at a glance

- **Language**: Java 8 — no Kotlin sources, no Compose
- **DI**: Dagger Hilt 2.51.1
- **DB**: Room 2.6.1 with Gson-based TypeConverters
- **UI**: Views + ViewBinding + Material Components 1.9.0
- **Navigation**: Navigation Component 2.5.3 (bottom nav, 4 tabs)
- **Build**: AGP 8.13.2, compileSdk 34, minSdk 21, Groovy Gradle

For the full table see [`architecture.md`](architecture.md).

## Package map

| Package | Purpose |
|---------|---------|
| `calendarEntries` | `CalendarEntry` entity (type 1=event, 2=task, 3=reminder), builder, DAO, `AddEventActivity` |
| `configuration`   | `Configuration` entity, DAO, `ConfigurationManager` singleton, Hilt modules, `SplashActivity` (entry point) |
| `database`        | `AppDatabase` (Room singleton), `DBConverter` (Calendar/boolean[] via Gson), `DBMigration` (Application class + `@HiltAndroidApp`) |
| `helpers`         | `FormatHelper`, `ScreenHelper`, `ToolbarHelper` + listener interfaces |
| `popups`          | 6 DialogFragment subclasses (Alert, Message, Description, Notifications, Repetition, Timer) + listener interfaces |
| `stats`           | `Stats` entity (streaks, time, fails), `StatsDAO` with granular update queries |
| `ui.home`         | `HomeFragment` (timer state machine), `HomeViewModel`, `HomeForegroundService` |
| `ui.calendar`     | `CalendarFragmentMonth`, `CalendarFragmentWeek`, domain mappers, binders, vendored week-view |
| `ui.progress`     | Stub fragment |
| `ui.projects`     | Stub fragment |
| `feedback`        | `HapticFeedback`, `SoundFeedback` |

## Key conventions

- **CalendarEntry creation**: Use `CalendarEntry.EventBuilder` (builder pattern), call `.build()` to produce the entity.
- **Popup communication**: DialogFragments report results via `OnPopupListener<T>` or `OnNotificationsPopupListener`. The parent fragment/activity implements the interface.
- **DB access**: Always on background threads via `ExecutorService`. Use `LiveData.postValue()` from background, never `setValue()`.
- **Configuration**: Injected via Hilt (`@Inject ConfigurationManager`). Cached in memory, persisted to Room on change.
- **Toolbar**: `ToolbarHelper` (implements `MenuProvider`) dispatches to screen-specific listeners (`OnToolBarListener`, `OnToolbarCalendarListener`, `OnToolbarHomeListener`).
- **Design tokens**: Always use the `bb_*` semantic palette and `TextAppearance.BBetter.*` typography styles. Legacy color names (`azul`, `verde`, etc.) exist only so old layouts keep rendering — do not introduce new references. See [`style_guide.md`](style_guide.md).
- **Comments**: Don't normalize the mixed Spanish/Catalan/English comments unless the user asks.

## Gotchas (quick list)

Full list with reproduction and fixes lives in [`common_errors.md`](common_errors.md). Headline items:

- `DBMigration` is the **Application class** (manifest `android:name`), not just a migration holder.
- `fallbackToDestructiveMigration()` is active — schema changes wipe the database.
- `InitialConfiguration` is **legacy** — extends Activity but used as a singleton. Logic lives in `SplashActivity` now.
- **Do not upgrade** `material` past `1.9.0` (see comment in `app/build.gradle:47`).
- `ConfigurationManager.getConfiguration()` can return null before the async load completes.

## Where to look next

- [`architecture.md`](architecture.md) — diagrams, package-by-package class table, flow walkthroughs.
- [`architectural_patterns.md`](architectural_patterns.md) — threading model, DI wiring, DAO conventions, LiveData flow, popup creation.
- [`style_guide.md`](style_guide.md) — palette, typography, dimens, drawables, theme rules.
- [`workflows.md`](workflows.md) — recipes for "add a popup / entity / colour / build the APK".
- [`common_errors.md`](common_errors.md) — symptom → fix table for the failure modes that show up repeatedly.
- [`windows_commands.md`](windows_commands.md) — PowerShell / `gradlew.bat` reference for this machine.
- [`android_studio.md`](android_studio.md) — IDE-specific tips (sync, AVD, logcat).
