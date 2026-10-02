# Frontend structure cleanup (pre-styling)

**Slug:** frontend-structure-cleanup
**Status:** applied
**Created:** 2026-10-02
**Last updated:** 2026-10-02

## Why
Before giving the app a concrete visual style, its structure needs cleaning up: top bars that
barely do anything, duplicated titles, controls duplicated across screens, unclear states, and
UI text mixed between English and Spanish. The user approved applying every recommendation from
the 2026-10-02 review (screenshots of the 4 tabs), with these adjustments: drop "Add for this
day", keep the **square** as the "today" marker, remove **all** top bars and page titles (top
bar, in-page, and bottom-nav labels), and have Spanish text via the standard Android
`values-<lang>/strings.xml` pattern.

## What changes (deltas vs current behavior)

### Global
- REMOVED: Activity ActionBar on every screen (MainActivity → `ThemeChatGPTBlue_NoActionBar`,
  no `setupActionBarWithNavController`). `home_toolbar.xml` / `toolbar.xml` menus are no longer
  inflated by the fragments.
- CHANGED: bottom nav `labelVisibilityMode` labeled → **unlabeled** (icons only; the titles stay
  in the menu as accessibility labels).
- ADDED: `res/values-es/strings.xml` with a full Spanish translation. `values/` stays English
  (the default fallback); a phone set to Spanish shows Spanish.
- CHANGED: hardcoded UI literals → string resources (`-- Concentration --`, `This week`,
  `(untitled)`, the end-of-cycle message, the foreground-service notification, popup `OK`/`Description`/`Temporizador fallido`).

### Home
- CHANGED: streak (🔥 N) moves from the top bar into the header, on the greeting line; tapping
  it opens `FocusStreakPopup` as before.
- CHANGED: timer configuration (old hourglass icon) → settings icon in the timer card's corner
  (hidden in focus mode).
- REMOVED: settings gear (had no handler) and the progress icon (duplicated the tab).
- CHANGED: the "00:40" stat gets a label: "%s today" / "%s hoy".
- CHANGED: mode chip without dashes ("Concentration" / "Rest").
- CHANGED: block-mode button shows its state as text (off / on / needs permission) and the off
  state no longer looks disabled (alpha 0.35 → 0.7).
- CHANGED: each task row in its own frame (`bg_task_item`); the "focus" column is reserved in the
  today section (INVISIBLE instead of GONE) so the times line up.
- CHANGED: the play button loses its `elevation`. Its shadow came out clipped into a square at the
  view's own edges; neither an oval outline nor `clipChildren=false` + padding on the row fixed it
  (verified pixel by pixel). The final shadow will be designed in the styling pass.

### Progress
- REMOVED: "Charts" header and the title inside each chart card (duplicated the tab).
- CHANGED: chart tabs move **above** the carousel, with short labels ("Focus", "Fails",
  "Hours", "Projects").

### Calendar
- REMOVED: "Add for this day" button; the FAB now creates the entry on the **selected day**.
- REMOVED: "today" circle. Today = **square outline** (always); selected day = soft fill.
- ADDED: empty state "No events for this day" (string already existed, unused).
- ADDED: Month/Week segmented control (`bg_segmented_*`) in month and week views, replacing the
  top-bar icon.

### Projects
- REMOVED: "Projects" in-page header. The `+` becomes a FAB (same as Calendar) instead of a lone
  `+` with no title next to it.
- FIXED: a completed project (status COMPLETED or every item done) no longer shows "Overdue" /
  "Due soon" — in the list, in the detail, and in the Progress band.
- ADDED: deadline date in the project-card subtitle ("1/4 done · Due Sep 1").
- CHANGED: project detail gets an in-content back button (the ActionBar's Up arrow no longer
  exists).

## Impact
- Files / packages touched: `MainActivity`, `ui/home/*` (HomeFragment, HomeViewModel,
  TodayTaskAdapter, HomeForegroundService), `ui/calendar/*` (Month, Week, MonthDayBinder,
  DayDetailAdapter), `ui/progress/*` (ProgressFragment, ChartCarouselAdapter, TimeRange,
  ProgressViewModel), `ui/projects/*` (ProjectListAdapter, ProjectDetailFragment),
  `projects/ProjectDeadlineState`, layouts (`activity_main`, `fragment_home`, `item_today_task`,
  `fragment_calendar_month`, `fragment_calendar_week`, `cell_month_day`, `fragment_progress`,
  `item_chart_card`, `fragment_projects`, `fragment_project_detail`, popups), drawables
  (`bg_task_item`, `bg_calendar_day_today`, `bg_calendar_day_selected`), `navigation/mobile_navigation.xml`,
  `menu/home_toolbar.xml`, `values/strings.xml`, new `values-es/strings.xml`.
- DB schema: none.
- UI tokens: bb_* only (rule #2).

## Follow-ups
- `menu/home_toolbar.xml`, `menu/toolbar.xml` and `layout/action_streak_counter.xml` are no longer
  inflated by anything (only `ToolbarHelper` references their ids). Delete them along with the
  streak/calendar branches of `ToolbarHelper` in a cleanup pass.

## Out of scope
- Final visual style (palette, typography, motion) — that comes in the frontend overhaul.
- Deleting the `ToolbarHelper` helper (AddEventActivity still uses it for its close/save bar).
- Catalan translation or other languages.

## Verify
<filled in by `/spec verify`>
