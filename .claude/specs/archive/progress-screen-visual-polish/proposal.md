# Progress screen — fixed header, clearer bands, titled chart selector

**Slug:** progress-screen-visual-polish
**Status:** verified
**Created:** 2026-09-09
**Last updated:** 2026-09-10

## Why
Three usability complaints on the Progress tab (`ProgressFragment` / `fragment_progress.xml`):
1. The Day/Week/Month segmented control and range stepper sit at the very bottom of a long
   scrolling screen — already flagged as a known, unclaimed gap in
   `.claude/docs/systems/progress-screen.md` ("Moving the range navigator to the top is a known,
   unclaimed improvement").
2. The block-toggle icon (🚫, `usage_block_toggle`), the "Add apps" action (`usage_add_apps`) and
   the enforce-master pause/resume text (`usage_enforce_master`) are plain icons/text with no
   button affordance, and the three bands (charts / projects / usage) have no visual separation
   from each other or from the screen background.
3. The chart carousel's page indicator is a row of blind dots (`chart_dots` with `tab_dot`
   background) — there is no way to see what chart a swipe leads to or jump straight to one.

## What changes (deltas vs current behavior)
- ADDED: a fixed (non-scrolling) header directly under the action bar holding the Day/Week/Month
  segmented control + `‹ label ›` range stepper. Only the chart carousel, projects band and usage
  band scroll underneath it (inside a `NestedScrollView`, unchanged nested-scroll-disabled lists).
- CHANGED: `chart_dots` `TabLayout` — from unlabeled dot indicators to fixed-mode tabs showing each
  chart's own title (reusing `ChartCarouselAdapter`'s existing per-position label logic, exposed as
  a public method). Tapping a tab jumps `chart_pager` straight to that page; swiping still updates
  the selected tab (`TabLayoutMediator`, already wired). Labels re-sync automatically when DAY mode
  drops the "by hour" page, same remapping the adapter already does for `item_chart_card` labels.
- CHANGED: `usage_add_apps` — from a bare colored `TextView` to a real pill button (leading `+`
  icon + label, outlined `bb_primary` style).
- CHANGED: `usage_block_toggle` (`item_app_usage_row.xml`) — gets a chip/circle background behind
  the icon so it reads as a tappable control; the existing 3-state tint (muted / `bb_danger` active
  / `bb_accent_reward` pending) now colors the chip as well as the icon, at reduced background
  alpha so the row doesn't turn into a solid coin.
- CHANGED: `usage_enforce_master` — from end-aligned plain text to a pill-styled toggle (background
  + text color reflecting on/off), same on/off strings.
- CHANGED: `projects_band` and `usage_band` each wrapped in a card surface (`bg_card` +
  `elevation_card` + `home_card_padding`, matching `Widget.BBetter.Card`) so the three bands read
  as distinct sections; the chart carousel gets a matching small section header label for the same
  reason (screen title still comes from the action bar — this is a *section* header, not a repeat
  of it).

## Impact
- Files / packages touched:
  - `app/src/main/res/layout/fragment_progress.xml` (structure: fixed header + scroll region, card
    wrapping, section headers)
  - `app/src/main/res/layout/item_app_usage_row.xml` (block-toggle chip background)
  - `app/src/main/java/com/example/bbettercalendar/ui/progress/ProgressFragment.java`
    (`TabLayoutMediator` callback sets tab text; no other behavior change)
  - `app/src/main/java/com/example/bbettercalendar/ui/progress/ChartCarouselAdapter.java`
    (expose the existing private `labelFor` as a package/public accessor for the tab callback)
  - `app/src/main/java/com/example/bbettercalendar/ui/progress/AppUsageAdapter.java`
    (tint the new chip background alongside the icon per state)
  - New drawables under `app/src/main/res/drawable/` for the pill/chip backgrounds (outline pill
    for "Add apps", chip circle for the block toggle, pill for the enforce-master toggle) — bb_*
    tokens only
  - `.claude/docs/systems/progress-screen.md` — resolve the "range navigator sits at bottom"
    gotcha, add a `## History` row
- DB schema: none
- UI tokens: bb_* + `TextAppearance.BBetter.*` only (rule #2)

## Out of scope
- `AppLimitDialog`, `AppPickerActivity`, `UsageDisclosureDialog` / `AccessibilityDisclosureDialog`
  visuals — untouched.
- Chart *content* or chart types (MPAndroidChart building logic in `ChartCarouselAdapter`) — only
  how you navigate between chart pages changes, not what they draw.
- The usage-limit measure/warn/enforce pipeline (`app-limits.md`) — only the presentation of
  existing toggles changes, no logic/threshold changes.
- Renaming chart title strings (e.g. `progress_chart_when`) — kept as-is unless the emulator check
  shows real clipping in the new fixed-mode tabs, in which case a minimal copy tweak is folded in
  during `apply`/`verify`, not treated as scope creep.

## Verify
**Verdict: pass.**

- **Completeness** — all `tasks.md` boxes done.
- **Correctness** — touched files match this proposal's "Impact" list exactly (plus
  `view_usage_locked.xml`, a small necessary follow-on: it lost its own `bg_card`/elevation since
  it now nests inside the newly-carded `usage_band`, and `strings.xml` for the new
  `progress_charts_header` string — both in scope, not undisclosed creep).
- **Coherence** — `code-reviewer` subagent pass: no correctness bugs, rule #2 (bb_* tokens) and
  rule #3 (threading) both clean. One Medium note flagged for visual follow-up: `chart_dots` in
  fixed+fill mode with 4 tabs could truncate the two longer chart titles ("when I focus / fail",
  "time per project") on a narrow screen.
- **Runtime** — `ui-tester` subagent drove the emulator end to end: fixed header confirmed
  non-scrolling (bounds identical before/after scroll), tab-title selector confirmed tap-to-jump +
  swipe-sync in both WEEK (4 tabs) and DAY (3 tabs) modes, "Add apps" pill opens the picker, the
  block-toggle chip and enforce-master pill render and toggle correctly, no `FATAL EXCEPTION` at
  any point. The Medium note above was checked directly against the `progress-scrolled-fails.png`
  screenshot: both long labels wrap cleanly to two lines with no clipping/ellipsis — no copy change
  needed, out-of-scope item stays out of scope.
- `/check` (assembleDebug + lintDebug): clean. One lint fix folded in during apply —
  `android:drawableTint`/`android:drawableStart` on the new "Add apps" pill aren't backed on
  minSdk 21; switched to `app:drawableTint`/`app:drawableStartCompat`.
