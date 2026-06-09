# Progress Charts MVP — our-data-only stats screen (Phase 1)

**Slug:** progress-charts-mvp
**Status:** applied
**Created:** 2026-06-09
**Last updated:** 2026-06-09

> Roadmap: Phase 1 of [`docs/progress/06-screen-mapping-and-roadmap.md`](../../../../docs/progress/06-screen-mapping-and-roadmap.md).
> Builds on the now-done Phase 0 (see [`capabilities/progress-screen.md`](../../capabilities/progress-screen.md)).
> Data model + chart-lib rationale: [`docs/progress/04-charts-and-data-model.md`](../../../../docs/progress/04-charts-and-data-model.md).

## Why

Phase 0 persists a real time-series (`daily_stat` + `focus_event`), but nothing renders it —
[`ProgressFragment`](../../../../app/src/main/java/com/example/bbettercalendar/ui/progress/ProgressFragment.java)
is still a centered "This is progress fragment" `TextView`. Phase 1 turns that history into the
sketch's top band: a swipeable carousel of charts plus a time-span navigator that drives them. It
ships a genuinely useful stats screen using **only our own data** — zero new permissions, zero
Play-policy exposure, no DB schema change.

## What changes (deltas vs current behavior)

- ADDED: **MPAndroidChart** (`com.github.PhilJay:MPAndroidChart:v3.1.0`) via a new JitPack repo in
  `settings.gradle` + an `implementation` line in `app/build.gradle`.
- ADDED: a **carousel of 3 chart cards** (sketch band 2) in a `ViewPager2` with a dots indicator:
  1. **concent** — `LineChart` of focus minutes/day over the selected span (`DailyStat.focusMinutes`).
  2. **fails** — `LineChart`/`BarChart` of timer fails/day (`DailyStat.fails`).
  3. **when I focus / fail** — grouped `BarChart`, 24 hourly buckets, focus vs fail (`FocusEvent`).
- ADDED: a **time-span navigator** (sketch band 4) — a **Day / Week / Month** segmented toggle plus
  single `‹ <label> ›` arrows that step one unit of the selected granularity (no `«` `»` big arrows).
  One `TimeRange` (anchor day + `Granularity` DAY/WEEK/MONTH) is the single source of truth; both
  charts observe it.
- CHANGED: `ProgressViewModel` `ViewModel` → `AndroidViewModel`. It reads `DailyStatDAO`,
  `FocusEventDAO`, and the live `StatsDAO` off an `ExecutorService` and `postValue`s plain data
  holders (no MPAndroidChart types in the ViewModel). The legacy `getText()` is kept so the
  unrelated `ProjectsFragment` (which currently reuses this VM) keeps compiling.
- CHANGED: `fragment_progress.xml` is rewritten from the single `TextView` into the
  carousel + navigator layout, styled with `bb_*` tokens + `TextAppearance.BBetter.*`.

## Impact

- Files / packages touched:
  - `settings.gradle`, `app/build.gradle` (JitPack repo + dependency)
  - `ui/progress/ProgressViewModel.java` (rewrite to AndroidViewModel + chart data)
  - `ui/progress/ProgressFragment.java` (wire carousel + navigator + observers)
  - `ui/progress/` new: `TimeRange.java`, `Granularity.java`, `ChartBundle.java` (plain data holder),
    `ChartCarouselAdapter.java` (ViewPager2 RecyclerView.Adapter)
  - `res/layout/`: rewrite `fragment_progress.xml`; add `item_chart_card.xml`
  - `res/values/`: possibly add a few `bb_*`-derived chart dimens/strings (no new palette colors)
- DB schema: **none** — read-only over existing `daily_stat` / `focus_event` / `stats`. Rule #6 not
  triggered (no `@Database(version)` bump).
- UI tokens: `bb_*` + `TextAppearance.BBetter.*` only (rule #2). Charts get entry/axis colors from
  `bb_primary` (concent), `bb_danger` (fails), `bb_on_surface_muted` (axes); description label off.
- Threading: all DB reads + bucketing on `ExecutorService`, results via `postValue` (rule #3).
- Dependency pins: Material stays `1.9.0` (MPAndroidChart doesn't touch it). MPAndroidChart pulls a
  transitive Kotlin stdlib; the project already applies `org.jetbrains.kotlin.android`, so no new
  Kotlin *source* is introduced.

## Key data correctness decisions (see design.md)

- **Today is merged live.** `daily_stat` only has rows for *past* days; today's totals still live in
  `Stats.todayTimeStudied/Fails`. The VM appends today as the trailing chart point from the live
  `Stats` row, converting `todayTimeStudied` ms → minutes (mirroring `persistDailyStat`).
- **Gap-fill missing days as zero.** Days the user never opened the app have no `daily_stat` row;
  the VM fills the full requested range with zero entries so the line/x-axis stay continuous.

## Resolves open question

- Roadmap open-Q "navigator granularity: toggle vs stepper" → **explicit toggle + stepper**: a
  Day/Week/Month segmented control sets `Granularity`; single `‹`/`›` arrows step one unit of the
  selected granularity. No `«`/`»` big arrows. Forward controls disable when the range would enter
  the future. Placement is cosmetic and can move freely (one `TimeRange` drives everything).

## Out of scope

- Band (3) per-app usage list, `PACKAGE_USAGE_STATS`, and the phone-usage "~" chart card — all Phase 2.
- Toolbar (band 1) search/calendar icon *actions* — the header title is fine; wiring icons is later.
- `AppUsageDaily` table (Phase 2 nicety).
- Emitting `FocusEvent.TYPE_TASK` rows (Phase 0 left it reserved; tasks still counted in `DailyStat`).
- Fixing `ProjectsFragment`'s incorrect reuse of `ProgressViewModel` (left compiling, untouched).
- Soft blocking (Phase 4) and web (Phase 5).
