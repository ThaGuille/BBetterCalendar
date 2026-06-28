# Design notes — progress-charts-mvp

Deeper technical notes for the Phase 1 charts. Read alongside `proposal.md` + `tasks.md`.

## Data sources & the two traps

| Series | Source | Trap |
|---|---|---|
| concent (focus min/day) | `DailyStat.focusMinutes` | only *past* days exist; **today** is in live `Stats` |
| fails/day | `DailyStat.fails` | same — today is live |
| when focus/fail (per-hour) | `FocusEvent` (live, written per event) | already includes today; just bucket by hour |

### Trap 1 — today is not in `daily_stat`
`persistDailyStat` only runs at the daily reset for a *previous* active day
([`InitialConfiguration.java:115`](../../../../app/src/main/java/com/example/bbettercalendar/configuration/InitialConfiguration.java#L115)).
So a "last 7 days" concent/fails chart is missing the current day. The VM must read the live `Stats`
row and append today as the trailing point: `focusMinutes = millisToMinutes(todayTimeStudied)`,
`fails = todayFails`. (The per-hour chart is fine — `FocusEvent`s are written live in `HomeViewModel`.)

### Trap 2 — sparse days
A `DailyStat` row only appears for days where the app saw a reset. Skipped days simply have no row.
The VM iterates the full `[start, end]` date range and emits a zero entry for any missing day, so the
x-axis is continuous and the line doesn't connect across gaps misleadingly.

## Why the ViewModel stays MPAndroidChart-free
MVVM + the project threading rule: the VM produces plain `ChartBundle` data off the `ExecutorService`
and `postValue`s it; the `Fragment`/adapter converts to `Entry`/`BarEntry` on the main thread. This
keeps chart-library types out of the VM (testable, no Android-view deps) and matches how
`HomeViewModel` already does DB work + `postValue`.

## TimeRange / navigator model (resolves open-Q #3)

```
enum Granularity { DAY, WEEK, MONTH }
class TimeRange { LocalDate anchor; Granularity g; }   // derives startDay/endDay
```

- **Day / Week / Month segmented toggle** sets `g` (`MaterialButtonToggleGroup`, single-selection,
  `bb_*`-styled — Material 1.9.0 has it).
- `‹` / `›` → step `anchor` by one unit of `g` (1 day / 1 week / 1 month). **No `«` `»` big arrows.**
- the label between the arrows reflects the current range ("Today", "This week", "June 2026").
- forward (`›`) disabled when the resulting range would cross past `today` (no future data).

### Placement (cosmetic — easy to move)
Default: under the carousel, a segmented Day/Week/Month toggle row, then a centered `‹ label ›`
stepper row. Because one `TimeRange` drives both charts regardless of layout, the toggle can later
move beside the arrows or above the carousel without touching the data path.

`LocalDate` is safe on minSdk 21 because `coreLibraryDesugaringEnabled true` is set
([`app/build.gradle:30`](../../../../app/build.gradle#L30)).

## Carousel choice: ViewPager2
3 heterogeneous cards (2 line, 1 bar). `ViewPager2` gives one-card-at-a-time swipe matching the
sketch's `[chart][chart][chart→]`, plus a `TabLayout` dots indicator via `TabLayoutMediator`
(Material 1.9.0 has it). A horizontal `RecyclerView` + `PagerSnapHelper` is the fallback if ViewPager2
nesting fights the parent scroll.

## Styling (rule #2)
- concent line/fill: `bb_primary`; fails: `bb_danger`; per-hour focus vs fail: `bb_primary` / `bb_danger`.
- axis + legend text: `bb_on_surface_muted`; grid: `bb_divider`.
- `chart.getDescription().setEnabled(false)`; the card's `TextAppearance.BBetter.Label` is the title.
- card = `Widget.BBetter.Card`; radii/spacing from `dimens.xml` (`radius_md`, `spacing_md`).

## Dependency note
JitPack must go in `settings.gradle`'s `dependencyResolutionManagement` (repo mode is
`FAIL_ON_PROJECT_REPOS`, so a repo in `app/build.gradle` would fail the build). MPAndroidChart is
Apache-2.0, datasets are tiny (≤ ~90 points) so its large-dataset slowness is irrelevant.

## Deliberately deferred
Phone-usage "~" card, per-app list, and usage permission are Phase 2 — they need
`UsageStatsManager`, a different data source with its own onboarding/locked state.
