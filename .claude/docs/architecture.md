# BBetterCalendar — Architecture

Single-module Android app written in **Java 8** that combines a Pomodoro-style study timer with a calendar for managing events, tasks, and reminders. Tracks daily study time, streaks, phone/app usage, and usage limits.

> Version source of truth: [`app/build.gradle`](../../app/build.gradle) and [`build.gradle`](../../build.gradle) at the repo root. If a number here drifts, the gradle file wins.

For per-package class tables, flow walkthroughs, and invariants, see the **system docs** under
[`.claude/docs/systems/`](systems/) — indexed by task in the "System docs" table in
[`CLAUDE.md`](../../CLAUDE.md). This file stays at the "how the pieces fit together" altitude.

---

## Stack and build

| Key | Value |
|-----|-------|
| Language | Java 8 (source/target compatibility 1.8) — Kotlin plugin is on the classpath only for transitive deps + the vendored week-view, no first-party Kotlin source |
| Android Gradle Plugin | 8.13.2 |
| Gradle wrapper | 8.13 |
| compileSdk / targetSdk | 34 |
| minSdk | 21 |
| Module structure | Single `:app` module, Groovy Gradle |
| DI | Dagger Hilt 2.51.1 |
| Database | Room 2.6.1 (v10) |
| Navigation | Navigation Component 2.5.3 |
| Serialization | Gson 2.9.1 |
| UI toolkit | Views + ViewBinding (no Compose) |
| Design system | Material Components 1.9.0 |
| Calendar lib | `com.kizitonwose.calendar:view:2.5.4` |
| Charts | MPAndroidChart v3.1.0 (JitPack) |

> **Do not upgrade `com.google.android.material` past `1.9.0`** — comment in `app/build.gradle` says it breaks. The Kizitonwose calendar is pinned to 2.5.4.

---

## Layer diagram

```mermaid
graph TD
    subgraph presentation [Presentation Layer]
        SplashAct["SplashActivity"]
        MainAct["MainActivity"]
        AddEventAct["AddEventActivity"]
        HomeFrag["HomeFragment"]
        CalMonthFrag["CalendarFragmentMonth"]
        CalWeekFrag["CalendarFragmentWeek"]
        ProgressFrag["ProgressFragment"]
        AppPicker["AppPickerActivity"]
        ProjectsFrag["ProjectsFragment (stub)"]
        Popups["DialogFragments: Alert, Timer, Notifications, Repetition, Description, Message, AppLimit, UsageDisclosure"]
    end

    subgraph viewmodels [ViewModels]
        HomeVM["HomeViewModel"]
        CalVM["CalendarViewModel"]
        ProgVM["ProgressViewModel"]
        ProjVM["ProjectsViewModel (stub)"]
    end

    subgraph domain [Domain / Controllers]
        ConfigMgr["ConfigurationManager (singleton)"]
        ItemMapper["CalendarItemMapper / ColorResolver"]
        UsageRepo["UsageStatsRepository"]
        BlockEngine["BlockDecisionEngine"]
        LimitChecker["UsageLimitChecker / Scheduler"]
    end

    subgraph data [Data Layer -- Room, v10]
        AppDB["AppDatabase (singleton)"]
        EntryDAO["CalendarEntryDAO"]
        StatsDAO["StatsDAO / DailyStatDAO / FocusEventDAO"]
        ConfigDAO["ConfigurationDAO"]
        AppRuleDAO["AppRuleDAO / ConsentRecordDAO"]
    end

    SplashAct --> AppDB
    SplashAct --> MainAct
    MainAct --> HomeFrag
    MainAct --> CalMonthFrag
    MainAct --> ProgressFrag
    MainAct --> ProjectsFrag
    HomeFrag --> HomeVM --> StatsDAO
    HomeVM --> ConfigMgr --> ConfigDAO
    CalMonthFrag --> CalVM --> EntryDAO
    CalWeekFrag --> CalVM
    AddEventAct --> EntryDAO
    ProgressFrag --> ProgVM
    ProgVM --> StatsDAO
    ProgVM --> AppRuleDAO
    ProgVM --> UsageRepo
    AppPicker --> AppRuleDAO
    LimitChecker --> AppRuleDAO
    LimitChecker --> UsageRepo
    BlockEngine --> AppRuleDAO
    BlockEngine --> UsageRepo
    AppDB --> EntryDAO
    AppDB --> StatsDAO
    AppDB --> ConfigDAO
    AppDB --> AppRuleDAO
```

Background/OS-integration components (services, receivers, alarms, notification channels) are
covered in the System topology diagram below and each owning system doc, not duplicated here.

---

## System topology

Seven runtime subsystems (each documented in [`.claude/docs/systems/`](systems/)) share two data
hubs: `AppRule` (tracked-app + limit/enforce state) and `UsageStatsRepository` (raw foreground-time
reads). Both `app-limits.md`'s alarm-poll checker and its accessibility-service block engine read
these **independently** — no shared cache between the two pipelines.

```mermaid
graph LR
    DataModel["data-model.md<br/>(Room v10, 7 entities)"]
    StartupConfig["startup-config.md"]
    Pomodoro["pomodoro-timer.md"]
    Calendar["calendar.md"]
    Progress["progress-screen.md"]
    AppLimits["app-limits.md"]
    Notifications["notifications.md"]

    StartupConfig --> DataModel
    Pomodoro --> DataModel
    Calendar --> DataModel
    Progress --> DataModel
    AppLimits --> DataModel
    Progress -->|"AppRule writes"| AppLimits
    Pomodoro -->|"FocusFailNotifier"| Notifications
    AppLimits -->|"UsageLimitNotifier"| Notifications
    Calendar -->|"EventReminderReceiver"| Notifications
    Calendar -.->|"BootReceiver also re-arms"| AppLimits
```

Declared exclusions from the system docs (owned elsewhere):
`popups`/`helpers`/`feedback` (see [`architectural_patterns.md`](architectural_patterns.md)).

---

## Navigation graph

```mermaid
graph LR
    BottomNav["Bottom Navigation (top level)"]
    BottomNav --> Home["HomeFragment"]
    BottomNav --> Progress["ProgressFragment"]
    BottomNav --> CalMonth["CalendarFragmentMonth"]
    BottomNav --> Projects["ProjectsFragment"]
    CalMonth -->|"toolbar switch"| CalWeek["CalendarFragmentWeek"]
    CalWeek  -->|"toolbar switch"| CalMonth
    Projects --> ProjectDetail["ProjectDetailFragment (projectId: int)"]
    Home --> Focus["FocusFragment"]
    Focus --> CycleComplete["CycleCompleteFragment"]
    Progress --> AllProgress["AllProgressFragment"]
    Progress --> ScreenTime["ScreenTimeFragment"]
    ScreenTime --> AppDetail["AppDetailFragment (packageName: string)"]
```

Destination ids (`res/navigation/mobile_navigation.xml`): top level `navigation_home`,
`navigation_progress`, `navigation_calendar_month`, `navigation_calendar_week`,
`navigation_projects`; secondary `navigation_project_detail` (global action
`action_global_project_detail`), `navigation_focus`, `navigation_cycle_complete`,
`navigation_all_progress`, `navigation_screen_time`, `navigation_app_detail`. Entry points: Home play
button / timer card → Focus (finishing the cycle navigates to `navigation_cycle_complete`, popping Focus);
Progress "See all your progress" → All progress and "Manage app blocking" → Screen time; Screen time
row / chevron / "Set limit" → App detail (`packageName`); Projects card long-press or "Open project" →
project detail. Done in u13–u20; Back always returns to the previous screen.

**Chrome per destination (u12):** `MainActivity` hides `nav_view` + `nav_divider` from an
`addOnDestinationChangedListener` unless the destination is in `TOP_LEVEL_DESTINATIONS` (the 5
top-level ids above). Fragments never toggle the bottom nav themselves. Secondary screens get
their back arrow + title from `@layout/view_top_bar` (`MaterialToolbar` id `topBar`, style
`Widget.BBetter.Toolbar.Secondary`) bound with `ui/common/TopBarHelper.bind(fragment, view, title)`;
arrow and system back both pop to wherever you came from.

**Debug entry (debug builds only):** `SplashActivity` forwards its extras to `MainActivity`, which
handles `bb_debug_nav` = `focus|cycle_complete|all_progress|screen_time|app_detail`, optional
`bb_debug_from` = `home|progress|calendar|projects` (tab opened first, so back lands there) and
`bb_debug_pkg` (for `app_detail`). See `bbetter-selectors.md`.

## Where the visual layers live (u24)

| Path | What |
|---|---|
| `ui/common/widget/` | Reusable compound views (`StatTileView`, `SectionHeaderView`, `SegmentedControlView`, `PeriodNavigatorView`, `AppUsageRowView`, `ProgressRowView`, `StreakWeekView`, `AvatarView`, `StepperView`, `EmptyStateView`) |
| `ui/common/chart/` | `ChartStyler` + rounded bar renderers (`RoundedBarChartRenderer`, `RoundedCombinedChartRenderer`, `BandBarDataSet`) |
| `ui/common/TopBarHelper`, `SingleEvent` | Secondary-screen top bar; single-use LiveData events |
| `ui/focus/`, `ui/projects/`, `ui/progress/`, `ui/screentime/` | Screens: Focus + Cycle complete, Projects + detail, Progress + All progress, Screen time + App detail |
| `social/` | Social mock behind `BuildConfig.FEATURE_SOCIAL_MOCK` (hidden in release) |
| `res/values/strings_<area>.xml`, `dimens_<area>.xml`, `styles_<area>.xml` | Per-screen resources (EN + `values-es`), see [`style_guide.md`](style_guide.md) §3b |
| `app/src/debug/` | `DebugGalleryActivity` (widget/chart gallery), debug-only |

Start destination: `SplashActivity` (real `LAUNCHER` activity) → `MainActivity` → `HomeFragment`.

---

## Known quirks and tech debt

- **`DBMigration` naming**: It is the `Application` class (registered in manifest as `android:name`) *and* holds Room migration objects. The name implies only the migrations.
- **`InitialConfiguration` is legacy**: extends `AppCompatActivity` but is instantiated as a singleton. Logic has moved to `SplashActivity`. See `startup-config.md`.
- **Single entity for all calendar entry types**: `CalendarEntry` represents events, tasks, and reminders via an int `type` field. See `calendar.md`.
- **Destructive migration is active**: any Room schema version bump without a real `Migration` wipes the database (CLAUDE.md rule #6). See `data-model.md` for the migration history (v6→v10) and the one gap (v8→v9) that already did this once.
- **`material` pinned to 1.9.0** — see [`app/build.gradle`](../../app/build.gradle:47).
- **Mixed-language comments**: Spanish, Catalan, and English coexist throughout. Don't normalize unless asked.
- **Two independent "over usage limit" computations** — see `app-limits.md`'s Invariants section.

See also: [`architectural_patterns.md`](architectural_patterns.md) for the per-pattern playbook, [`common_errors.md`](common_errors.md) for symptom→fix tables, and [`.claude/docs/systems/`](systems/) for per-subsystem detail.
