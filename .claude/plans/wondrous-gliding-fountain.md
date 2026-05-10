# Calendar refactor: month + week views with vendored libraries

## Context

The current calendar in BBetterCalendar is a hand-built skeleton: two fragments ([CalendarFragmentMonth.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentMonth.java), [CalendarFragmentWeek.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentWeek.java)) that draw grid lines as raw `View`s in `LinearLayout`/`RelativeLayout`, with no real way to render hourly event blocks or multi-day spans. The user wants users to set reminders/tasks/events and see them visually, including events that span multiple hours or multiple days. After researching alternatives, the chosen path is:

- **Month view:** `kizitonwose/Calendar` (Maven Central, actively maintained 2026) — perfect for date-cell decoration with custom layouts.
- **Week view:** `Thellmund/Android-Week-View` 5.3.2 (frozen 2022, MIT licensed) — vendor the source into the project, since the calendar isn't the app's main feature and this avoids reinventing hour-grid + overlap layout.
- **Architecture:** thin domain layer (`CalendarItem` POJO + per-view adapters) so library-specific code is isolated and adding future views (day/year/agenda) is easy.

The existing `CalendarEntry` Room entity already models events/tasks/reminders — we keep it as the storage model and map to a domain `CalendarItem` for the views. The toolbar toggle (`OnToolbarCalendarListener.switchFragment()` + Nav graph actions) stays unchanged.

## Stop-and-ask points

The plan calls out specific blockers where execution must halt and the user be consulted before proceeding. Search for **STOP-AND-ASK** in the phases below.

## Build setup changes

**[build.gradle](build.gradle) (root)** — add Kotlin plugin:
```groovy
id 'org.jetbrains.kotlin.android' version '1.8.22' apply false
```

**[app/build.gradle](app/build.gradle)** — apply plugin, bump compileSdk, enable desugaring:
- Add `id 'org.jetbrains.kotlin.android'` to plugins block
- `compileSdk 33` → `compileSdk 34` (kizitonwose 2.10.1 requires 34; targetSdk stays 33)
- Add `kotlinOptions { jvmTarget = '1.8' }`
- Add `compileOptions { coreLibraryDesugaringEnabled true }`
- Dependencies:
  - `coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.0.4'`
  - `implementation 'com.kizitonwose.calendar:view:2.10.1'`
  - (kotlin-stdlib is bundled by the plugin since 1.4 — omit explicit dep)
- **Keep:** Hilt 2.44 with `annotationProcessor` only (no kapt — vendored Kotlin code uses no annotations). Room 2.4.2 stays.

**STOP-AND-ASK R1:** if Gradle sync fails after compileSdk bump (e.g., resource conflicts from material lib), stop. Fallback: pin `com.kizitonwose.calendar:view:2.5.4` which still supports compileSdk 33.

**STOP-AND-ASK R2:** if Kotlin 1.8.22 sync fails with Hilt aggregation errors or "Unsupported class file major version", stop. May need Hilt 2.48+.

## Architecture (layered)

```
┌─────────────────────────────────────────────────────────┐
│  Fragments (one per view type)                          │
│  CalendarFragmentMonth  │  CalendarFragmentWeek         │
│  - hosts library widget │  - hosts vendored WeekView    │
│  - observes ViewModel   │  - observes ViewModel         │
└─────────────────┬───────────────────┬───────────────────┘
                  │                   │
┌─────────────────▼───────────────────▼───────────────────┐
│  Per-view adapters (ONLY library-specific code lives    │
│  here — swap a library = rewrite one adapter)           │
│  MonthDayBinder         │  WeekViewItemAdapter          │
│  (CalendarItem→         │  (CalendarItem→               │
│   kizitonwose binding)  │   Thellmund WeekViewEntity)   │
└─────────────────┬───────────────────┬───────────────────┘
                  │                   │
                  └─────────┬─────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  CalendarViewModel (activity-scoped, shared by both     │
│  fragments to avoid re-querying Room on toggle)         │
│  - LiveData<List<CalendarItem>> for current range       │
│  - mapping CalendarEntry → CalendarItem inline          │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  Domain: CalendarItem POJO + CalendarItemMapper         │
│  + ColorResolver (type→color, no schema migration)      │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  Existing: CalendarEntryDAO + CalendarEntry             │
│  (add range query: getEventsBetween(start, end))        │
└─────────────────────────────────────────────────────────┘
```

**Why no Repository class:** Plan-agent critique was right — wrapping a single Room DAO with a Repository is overkill. Mapping happens directly in the ViewModel via `Transformations.map`.

**Why activity-scoped ViewModel:** Nav-graph navigation destroys the source fragment on toggle. Activity scope = data loaded once, shared between toggles, no re-query.

## Files to create

**Domain layer** (new package `com.example.bbettercalendar.ui.calendar.domain`):

- `CalendarItem.java` — POJO with: `int id`, `String title`, `long startMillis`, `long endMillis`, `int colorArgb`, `Type type` (enum: EVENT, TASK, REMINDER). Computed methods: `isMultiDay()`, `durationMinutes()`.
- `CalendarItemMapper.java` — static method `toItem(CalendarEntry)` and `toItems(List<CalendarEntry>)`. Handles legacy `limitDate` String + `Calendar` start/end (use `Calendar.getTimeInMillis()`).
- `ColorResolver.java` — static `colorFor(Type type, Context ctx)` returning theme-aware color int. Avoids adding a `color` column to the DB.

**Adapter layer** (new package `com.example.bbettercalendar.ui.calendar.binders`):

- `MonthDayBinder.java` — implements kizitonwose's `MonthDayBinder<DayViewContainer>`. Holds the current `Map<LocalDate, List<CalendarItem>>` and renders dots/bars per day.
- `WeekViewItemAdapter.java` — converts `List<CalendarItem>` into Thellmund's `WeekViewEntity.Event` instances.

**Vendored Thellmund** (new package `com.example.bbettercalendar.ui.calendar.weekview`):

- ~38–42 Kotlin files copied from Thellmund 5.3.2 `core/` + `base/` modules. Preserve MIT license header in each file plus a top-level `LICENSE.txt` in the package directory.
- Skip: `sample/`, `emoji/`, `jodatime/`, `jsr310/`, `threetenabp/`, all test files.

**Layouts**:

- New: `app/src/main/res/layout/cell_month_day.xml` — single day cell for kizitonwose binder (a TextView for day number + small bar/dots view for events).
- Modified (comment current contents, replace root):
  - [fragment_calendar_month.xml](app/src/main/res/layout/fragment_calendar_month.xml) — root becomes `<com.kizitonwose.calendar.view.CalendarView>` + the existing FAB. **Keep file path and root view ID stable** so ViewBinding still generates `FragmentCalendarMonthBinding`.
  - [fragment_calendar_week.xml](app/src/main/res/layout/fragment_calendar_week.xml) — root becomes the vendored WeekView custom view + FAB.

## Files to modify

**[CalendarEntryDAO.java](app/src/main/java/com/example/bbettercalendar/calendarEntries/CalendarEntryDAO.java)** — add a range query. Since `startDayAndHour` is stored as a `Calendar` (converted via `DBConverter`, presumably to a long), the query targets the underlying long column:

```java
@Query("SELECT * FROM CalendarEntry WHERE startDayAndHour BETWEEN :startMillis AND :endMillis")
LiveData<List<CalendarEntry>> getEventsBetween(long startMillis, long endMillis);
```

**STOP-AND-ASK R3:** if `DBConverter` doesn't store `Calendar` as a long (e.g., stores ISO string), the query column type/operator will be wrong. Stop and inspect [DBConverter.java](app/src/main/java/com/example/bbettercalendar/database/DBConverter.java) before writing the query.

**[CalendarViewModel.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarViewModel.java)** — replace boilerplate with:
- Hold `MutableLiveData<DateRange>` for the queried range (each view sets its own range)
- `getItems()` returns `LiveData<List<CalendarItem>>` via `Transformations.switchMap` on the range, calling `dao.getEventsBetween` and mapping with `CalendarItemMapper`.
- Constructor injects `CalendarEntryDAO` (use `@HiltViewModel` if Hilt ViewModel module exists; otherwise plain `ViewModelProvider.Factory`).

**[CalendarFragmentMonth.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentMonth.java)** — full rewrite:
- Inflate new layout, get `CalendarView` from binding
- Configure with `cv_dayViewResource = cell_month_day`, `monthScrollListener` to update VM range
- Set `monthDayBinder = new MonthDayBinder()`
- Observe `viewModel.getItems()` → group by `LocalDate` → push to binder → `calendarView.notifyCalendarChanged()`
- **Keep** `switchFragment()` and FAB click logic intact
- **Remove** all the manual line-positioning code (`positionLines`, `positionMonthDays`, `positionHours`)
- Use `requireActivity()` ViewModel scope: `new ViewModelProvider(requireActivity()).get(CalendarViewModel.class)`

**[CalendarFragmentWeek.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentWeek.java)** — full rewrite:
- Inflate new layout, get `WeekView` from binding
- Set `WeekView.Adapter` (or whichever Thellmund 5.3.2 entry point — verify on vendoring) backed by `WeekViewItemAdapter`
- On `onLoadMore` callback, update VM range
- Observe `viewModel.getItems()` → push to adapter → `weekView.notifyDataSetChanged()`
- Same toggle + FAB preservation as month fragment

**[CalendarController.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarController.java)** — most of it dies:
- `handleOnActivityResult` logic that re-queries Room → moves to ViewModel as `refresh()` after add-event returns, or just trust LiveData auto-refresh from Room.
- `positionWeekDaysText` is dead code (the new layouts don't have manual day initials) — delete.
- `printEvents` is debug logging — keep if useful, otherwise delete.
- Result: file probably reduces to nothing; delete it once fragments no longer reference it.

## Implementation phases

Each phase ends with a build + manual smoke test. If any phase fails, stop and consult the user.

**Phase 1 — Build setup**
1. Edit root `build.gradle` and `app/build.gradle` per the build setup section.
2. Bump compileSdk 33→34. **STOP-AND-ASK R1** if sync fails.
3. Add a hello-Kotlin file (`Sanity.kt`) to verify Kotlin compiles. Delete after success.
4. Run `./gradlew assembleDebug`. If fails: **STOP-AND-ASK R2**.

**Phase 2 — kizitonwose dependency**
1. Add `com.kizitonwose.calendar:view:2.10.1`.
2. Run sync + build. If kizitonwose pulls a transitive that breaks Material 1.9.0: stop and report.

**Phase 3 — Domain layer**
1. Inspect [DBConverter.java](app/src/main/java/com/example/bbettercalendar/database/DBConverter.java) for Calendar storage format. **STOP-AND-ASK R3** if not stored as long.
2. Create `CalendarItem`, `CalendarItem.Type`, `CalendarItemMapper`, `ColorResolver`.
3. Add `getEventsBetween` to DAO.
4. Build (no UI yet).

**Phase 4 — ViewModel**
1. Rewrite `CalendarViewModel` to expose `LiveData<List<CalendarItem>>`.
2. Wire DAO injection (plain factory if Hilt ViewModel module isn't already configured).

**Phase 5 — Comment current XMLs, scaffold new roots**
1. In each of `fragment_calendar_month.xml` and `fragment_calendar_week.xml`, wrap the existing content in `<!-- ... -->` (XML comments). Add a placeholder root with the FAB only — no calendar widget yet.
2. Verify both fragments still launch (just an empty screen with FAB and toggle button working). Toggle must work.

**Phase 6 — Vendor Thellmund source**
1. Clone `https://github.com/thellmund/Android-Week-View` at tag `5.3.2` to a temp directory.
   - **STOP-AND-ASK R4:** if the user wants to clone manually vs. me cloning — confirm before running `git clone`.
2. Copy `core/src/main/java/**` and `base/src/main/java/**` (Kotlin files only) into `app/src/main/java/com/example/bbettercalendar/ui/calendar/weekview/`.
3. Update package declarations to `com.example.bbettercalendar.ui.calendar.weekview.*` (preserving sub-package structure).
4. Add MIT license header to each file (or `LICENSE.txt` at the package root).
5. Build. **STOP-AND-ASK R5:** if compile errors arise from missing AndroidX deps or test-only references, stop and report.

**Phase 7 — Month view wired up**
1. Create `cell_month_day.xml` and `MonthDayBinder.java`.
2. Replace `CalendarFragmentMonth` body with kizitonwose wiring.
3. Drop `CalendarController` references.
4. Manual test: launch app, navigate to month view, scroll months, see day numbers + dots/bars when events exist in DB. Toggle to week view (still empty placeholder). FAB still opens AddEventActivity.

**Phase 8 — Week view wired up**
1. Create `WeekViewItemAdapter.java`.
2. Replace `CalendarFragmentWeek` body with vendored WeekView wiring.
3. Manual test: launch, navigate to week, see hour grid + event blocks at correct time positions. Add a multi-hour event via FAB and confirm it renders as a tall block. Add a multi-day event and confirm it spans columns. Toggle back to month, confirm same data renders. FAB works.

**Phase 9 — Cleanup**
1. Delete `CalendarController.java` if no remaining references.
2. Delete `CalendarWeekAdapter.java` (the old RecyclerView adapter, now unused).
3. Delete `recyclerview_week_event.xml` if it was the layout for that adapter.
4. Verify nav graph still references the same fragment FQNs ([mobile_navigation.xml](app/src/main/res/navigation/mobile_navigation.xml) untouched).

## Verification

End-to-end smoke test after Phase 8:

1. **Build:** `./gradlew assembleDebug` succeeds with no errors.
2. **Launch:** app opens to home, bottom nav → calendar shows month view.
3. **Month view:**
   - Day cells render in a 7-column grid with dots/bars on days that have events
   - Scrolling months works
   - FAB opens AddEventActivity
   - Toolbar toggle navigates to week view
4. **Week view:**
   - Hour grid renders (vendored Thellmund styling)
   - Existing events from Room appear as colored blocks at correct time positions
   - A multi-hour event renders as a tall block spanning the right rows
   - A multi-day event spans across day columns
   - Pinch-to-zoom (Thellmund built-in) works
   - FAB opens AddEventActivity
   - Toolbar toggle navigates back to month view
5. **Add an event flow:** create a new event via FAB → returns → both views show the new event without app restart (LiveData propagation).
6. **Toggle persistence:** toggle month↔week 5 times rapidly — no crashes, data stays in sync (activity-scoped VM working).

## Concrete numbers reference

| Item | Value |
|------|-------|
| Kotlin Gradle Plugin | 1.8.22 |
| `jvmTarget` | `'1.8'` |
| `compileSdk` | 33 → **34** |
| `targetSdk` | stays 33 |
| `desugar_jdk_libs` | 2.0.4 |
| `kizitonwose:view` | 2.10.1 (or 2.5.4 fallback) |
| Thellmund version | 5.3.2 (June 2021, MIT) |
| Vendored file count | ~38–42 Kotlin files |
| Hilt | stays 2.44, no kapt |
| Room | stays 2.4.2 |
| ViewModel scope | activity-scoped via `requireActivity()` |

## Critical files referenced

- [build.gradle](build.gradle)
- [app/build.gradle](app/build.gradle)
- [settings.gradle](settings.gradle)
- [CalendarFragmentMonth.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentMonth.java)
- [CalendarFragmentWeek.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentWeek.java)
- [CalendarViewModel.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarViewModel.java)
- [CalendarController.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarController.java)
- [CalendarEntry.java](app/src/main/java/com/example/bbettercalendar/calendarEntries/CalendarEntry.java)
- [CalendarEntryDAO.java](app/src/main/java/com/example/bbettercalendar/calendarEntries/CalendarEntryDAO.java)
- [DBConverter.java](app/src/main/java/com/example/bbettercalendar/database/DBConverter.java)
- [fragment_calendar_month.xml](app/src/main/res/layout/fragment_calendar_month.xml)
- [fragment_calendar_week.xml](app/src/main/res/layout/fragment_calendar_week.xml)
- [mobile_navigation.xml](app/src/main/res/navigation/mobile_navigation.xml) — **do not change IDs**
