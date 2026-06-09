# Tasks — progress-charts-mvp

## Dependency wiring
- [x] Add `maven { url 'https://jitpack.io' }` to `settings.gradle` →
      `dependencyResolutionManagement.repositories` (NOT app/build.gradle — repo mode is
      `FAIL_ON_PROJECT_REPOS`).
- [x] Add `implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'` to `app/build.gradle`
      (also added `androidx.viewpager2:viewpager2:1.0.0` for the carousel).
- [x] Sync / build once to confirm the dep resolves and Material stays 1.9.0 (assembleDebug OK).

## Data layer (ViewModel)
- [x] Add `Granularity` enum (DAY, WEEK, MONTH) and `TimeRange` (anchor `LocalDate` + granularity;
      derives `startDay`/`endDay`; `LocalDate` is available — desugaring is on).
- [x] Add `ChartBundle` plain holder: `int[]` series for concent + fails over the range, plus
      `int[24] focusByHour` / `int[24] failByHour`. No MPAndroidChart imports here.
- [x] Rewrite `ProgressViewModel` as `AndroidViewModel`: get `dailyStatDao()`, `focusEventDao()`,
      `statsDao()` from `AppDatabase.getDatabase(application)`; own `ExecutorService`.
- [x] `MutableLiveData<TimeRange> selectedRange` + `LiveData<ChartBundle> charts`. On range change,
      recompute on the executor and `postValue` (rule #3, mirror `HomeViewModel`).
- [x] Range query: `DailyStatDAO.getRange(startIso, endIso)` for past days; **gap-fill** every day in
      the range to zero where no row exists.
- [x] **Merge today**: today's point comes from the live `Stats` row (`todayTimeStudied` ms → minutes
      via `FormatHelper.millisToMinutes`, `todayFails`).
- [x] Per-hour buckets: `FocusEventDAO.getRange(startMillis, endMillis)`, bucket by
      `hourOfDay(timestamp)` into `focusByHour`/`failByHour` (type 0 vs 1).
- [x] Keep `getText()` on the VM so `ProjectsFragment` still compiles.
- [x] Navigator intent methods: `setGranularity(Granularity)`, `stepBack()/stepForward()` (one unit
      of the selected granularity); clamp so the range never enters the future. No big-arrow/jump methods.

## UI
- [x] Rewrite `fragment_progress.xml`: `ViewPager2` carousel + dots (TabLayout/`TabLayoutMediator`)
      + navigator = Day/Week/Month segmented toggle (`bg_segmented_track` + `AppCompatButton`s, the
      project idiom) and a centered `‹ label ›` stepper row (no big arrows). `bb_*` tokens only (rule #2).
- [x] `item_chart_card.xml`: a `bg_card`-wrapped chart container with a label.
- [x] `ChartCarouselAdapter` (ViewPager2 `RecyclerView.Adapter`): 3 pages, binds `ChartBundle` →
      MPAndroidChart entries. Entry colors `bb_primary`/`bb_danger`, axis text `bb_on_surface_muted`,
      `description.setEnabled(false)`, card label is the only title.
- [x] `ProgressFragment`: observe `charts` with `getViewLifecycleOwner()`, push to adapter; wire the
      toggle + arrows to the VM; disable/ dim forward arrow per `selectedRange`.
- [x] Null out `binding` in `onDestroyView` (existing pattern).

## Verify
- [x] `/check`: `assembleDebug` BUILD SUCCESSFUL; `lintDebug` only fails on 5 pre-existing errors in
      untouched calendar/notification files (zero new lint issues from Progress).
- [ ] Manual on-device QA (do when running the app): empty install → flat-zero charts no crash;
      a completed session today shows in the trailing point + the right hour bucket; navigator steps
      day/week/month and the forward arrow disables at "today".
